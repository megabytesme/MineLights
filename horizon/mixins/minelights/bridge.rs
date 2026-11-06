use std::{
    collections::{HashMap, HashSet, VecDeque},
    net::{TcpStream, ToSocketAddrs},
    path::PathBuf,
    sync::{
        Arc, Mutex, OnceLock, RwLock,
        atomic::{AtomicBool, AtomicU32, Ordering},
        mpsc::{self, SyncSender},
    },
    thread,
    time::{Duration, Instant},
};

use super::minelights::{DeviceLayout, Frame};
use super::minelights_config::Settings;
use super::minelights_direct::DirectOutputs;
use super::minelights_protocol::{self, V2Connection};
use super::minelights_server;

pub(super) const ACTION_REFRESH: u32 = 1;
pub(super) const ACTION_RESTART: u32 = 2;
pub(super) const ACTION_RESTART_ADMIN: u32 = 4;
pub(super) const ACTION_CHECK_UPDATE: u32 = 8;

pub(super) enum Notice {
    MissingServer,
    Update(Vec<String>),
    Manual(Vec<String>),
    ServerDownloadStarted {
        began_in_world: bool,
    },
    ServerDownloadFinished {
        result: Result<(), String>,
        server_was_running_before: bool,
        server_running_after: bool,
        server_started: bool,
        server_readiness_failed: bool,
    },
    ServerStartFailed,
    UpdateCheckFailed {
        manual: bool,
    },
}

#[derive(Clone)]
pub(super) struct Bridge {
    pub(super) layouts: Arc<RwLock<Vec<DeviceLayout>>>,
    pub(super) active: Arc<AtomicBool>,
    pub(super) auto_start: Arc<AtomicBool>,
    pub(super) in_world: Arc<AtomicBool>,
    pub(super) notices: Arc<Mutex<VecDeque<Notice>>>,
    pub(super) settings: Arc<RwLock<Settings>>,
    pub(super) actions: Arc<AtomicU32>,
    pub(super) stop_request: Arc<Mutex<Option<String>>>,
    pub(super) sender: SyncSender<Frame>,
}

pub(super) static BRIDGE: OnceLock<Bridge> = OnceLock::new();
static SHUTTING_DOWN: AtomicBool = AtomicBool::new(false);
static MANAGED_SERVER_BUSY: AtomicBool = AtomicBool::new(false);
pub(super) static SERVER_DOWNLOAD_IN_PROGRESS: AtomicBool = AtomicBool::new(false);
static MANAGED_SERVER_STARTED: AtomicBool = AtomicBool::new(false);
static MANAGED_SERVER_LAUNCH_ID: Mutex<Option<String>> = Mutex::new(None);
static MANAGED_SERVER_STOPPED: AtomicBool = AtomicBool::new(false);
static WORKER_FINISHED: AtomicBool = AtomicBool::new(false);
#[cfg(test)]
pub(super) static TEST_COMPLETED_HANDSHAKES: std::sync::atomic::AtomicUsize =
    std::sync::atomic::AtomicUsize::new(0);
static SERVER_LIFECYCLE: Mutex<()> = Mutex::new(());

pub(super) fn bridge_for(game: &super::Game<'_>, settings: &Settings) -> &'static Bridge {
    let config_dir = super::Game::native_config_dir().ok();
    let server_path = config_dir.as_deref().and_then(|config| {
        config
            .parent()
            .map(|root| root.join("mods").join("MineLights").join("MineLights.exe"))
    });
    let bridge = BRIDGE.get_or_init(|| {
        let layouts = Arc::new(RwLock::new(Vec::new()));
        let active = Arc::new(AtomicBool::new(true));
        let worker_auto_start = Arc::new(AtomicBool::new(settings.allow_reviewed_server));
        let in_world = Arc::new(AtomicBool::new(false));
        let notices = Arc::new(Mutex::new(VecDeque::new()));
        let worker_settings = Arc::new(RwLock::new(settings.clone()));
        let actions = Arc::new(AtomicU32::new(0));
        let stop_request = Arc::new(Mutex::new(None));
        let worker_stop_request = Arc::clone(&stop_request);
        let (sender, receiver) = mpsc::sync_channel::<Frame>(1);
        let worker_layouts = Arc::clone(&layouts);
        let worker_active = Arc::clone(&active);
        let worker_auto = Arc::clone(&worker_auto_start);
        let worker_in_world = Arc::clone(&in_world);
        let worker_notices = Arc::clone(&notices);
        let worker_config = Arc::clone(&worker_settings);
        let worker_actions = Arc::clone(&actions);
        thread::Builder::new()
            .name("MineLights-Horizon-Bridge".into())
            .spawn(move || {
                bridge_worker(
                    receiver,
                    worker_layouts,
                    worker_active,
                    worker_auto,
                    worker_in_world,
                    worker_notices,
                    worker_config,
                    worker_actions,
                    worker_stop_request,
                    server_path,
                    config_dir,
                )
            })
            .ok();
        Bridge {
            layouts,
            active,
            auto_start: worker_auto_start,
            in_world,
            notices,
            settings: worker_settings,
            actions,
            stop_request,
            sender,
        }
    });
    if !settings.allow_reviewed_server {
        MANAGED_SERVER_STOPPED.store(false, Ordering::Release);
    }
    bridge.auto_start.store(
        settings.allow_reviewed_server && !MANAGED_SERVER_STOPPED.load(Ordering::Acquire),
        Ordering::Relaxed,
    );
    bridge.active.store(true, Ordering::Release);
    if let Ok(mut current) = bridge.settings.write() {
        *current = settings.clone();
    }
    let _ = game;
    bridge
}

fn bridge_worker(
    receiver: mpsc::Receiver<Frame>,
    layouts: Arc<RwLock<Vec<DeviceLayout>>>,
    active: Arc<AtomicBool>,
    auto_start: Arc<AtomicBool>,
    in_world: Arc<AtomicBool>,
    notices: Arc<Mutex<VecDeque<Notice>>>,
    settings: Arc<RwLock<Settings>>,
    actions: Arc<AtomicU32>,
    stop_request: Arc<Mutex<Option<String>>>,
    server_path: Option<PathBuf>,
    config_dir: Option<PathBuf>,
) {
    let mut direct = DirectOutputs::default();
    let mut local_v2: Option<V2Connection> = None;
    let mut remote_v2: HashMap<String, V2Connection> = HashMap::new();
    let (remote_connect_tx, remote_connect_rx) = mpsc::channel::<(
        minelights_protocol::RemoteServer,
        Result<(V2Connection, Vec<minelights_protocol::V2Device>), String>,
    )>();
    let mut remote_connecting = HashSet::<String>::new();
    let mut v2_layouts: HashMap<String, Vec<DeviceLayout>> = HashMap::new();
    let mut direct_layouts = Vec::new();
    let mut next_handshake = Instant::now();
    let mut next_yeelight_discovery = Instant::now();
    let mut last_launch_attempt = Instant::now() - Duration::from_secs(600);
    let mut was_in_world = false;
    let mut missing_reported = false;
    let mut update_checked = false;
    let mut was_active = true;
    let mut legacy_reminder_sent = false;
    let mut old_server_notice_sent = false;
    let mut old_server_since: Option<Instant> = None;
    let mut previous_direct: Option<(bool, bool, Vec<super::minelights_config::OpenRgbEndpoint>)> =
        None;
    loop {
        let is_active = active.load(Ordering::Acquire);
        if SHUTTING_DOWN.load(Ordering::Acquire) && MANAGED_SERVER_STARTED.load(Ordering::Acquire) {
            let launch_id = MANAGED_SERVER_LAUNCH_ID
                .lock()
                .ok()
                .and_then(|id| id.clone());
            if let Some(expected) = launch_id.as_deref() {
                if let Some(connection) = local_v2.as_mut() {
                    if connection.matches_launch_id(expected) {
                        let _ = connection.request_server_shutdown();
                    }
                } else if let Some(dir) = config_dir.as_deref() {
                    let deadline = Instant::now() + Duration::from_secs(2);
                    loop {
                        match minelights_protocol::connect_local_control(dir) {
                            Ok(Some((mut connection, _))) => {
                                if connection.matches_launch_id(expected) {
                                    let _ = connection.request_server_shutdown();
                                }
                                break;
                            }
                            Ok(None) if Instant::now() < deadline => {
                                thread::sleep(Duration::from_millis(100))
                            }
                            _ => break,
                        }
                    }
                }
            }
        }
        if was_active && !is_active {
            // Dropping each stream releases its V2 leases immediately. The
            // shared server remains alive under tray ownership.
            let mut direct_black = HashMap::new();
            let current_direct_layouts = direct.layouts();
            for led in current_direct_layouts
                .iter()
                .flat_map(|layout| layout.leds.iter())
            {
                direct_black.insert(*led, [0, 0, 0]);
            }
            let disabled = settings
                .read()
                .map(|config| config.disabled_devices.clone())
                .unwrap_or_default();
            direct.update(&direct_black, &disabled);
            direct.disconnect_openrgb();
            direct.disconnect_yeelights();
            direct_layouts.clear();
            local_v2 = None;
            remote_v2.clear();
            v2_layouts.clear();
            if let Ok(mut current) = layouts.write() {
                *current = direct_layouts.clone();
            }
        } else if !was_active && is_active {
            next_handshake = Instant::now();
        }
        was_active = is_active;
        if SHUTTING_DOWN.load(Ordering::Acquire) {
            break;
        }
        let stop_target = stop_request
            .lock()
            .ok()
            .and_then(|mut request| request.take());
        if let Some(target) = stop_target {
            let connection = if target.is_empty() {
                local_v2.as_mut()
            } else {
                remote_v2.get_mut(&target).or_else(|| {
                    local_v2
                        .as_mut()
                        .filter(|local| local.server_id() == target)
                })
            };
            let mut result = connection
                .map(|c| c.request_server_shutdown())
                .unwrap_or("unavailable");
            if result == "unavailable" {
                if let Some(dir) = config_dir.as_deref() {
                    let control = if target.is_empty() {
                        minelights_protocol::connect_local_control(dir)
                            .ok()
                            .flatten()
                            .map(|(c, _)| c)
                    } else {
                        minelights_protocol::load_remote_servers(dir)
                            .ok()
                            .and_then(|servers| {
                                servers
                                    .into_iter()
                                    .find(|server| server.endpoint.server_id == target)
                            })
                            .and_then(|server| {
                                minelights_protocol::connect_remote_control(&server)
                                    .ok()
                                    .map(|(c, _)| c)
                            })
                    };
                    if let Some(mut connection) = control {
                        result = connection.request_server_shutdown();
                    }
                }
            }
            if target.is_empty() && matches!(result, "accepted" | "pending") {
                MANAGED_SERVER_STOPPED.store(true, Ordering::Release);
                auto_start.store(false, Ordering::Release);
            }
            let text = match result {
                "accepted" => "Server accepted the stop request.",
                "pending" => {
                    "Other clients are connected; the server will stop if they disconnect shortly."
                }
                "legacy_enabled" => {
                    "Server remains running while legacy local access is enabled; disable it or exit via the server tray."
                }
                "other_clients" => "Server remains running because other clients are connected.",
                _ => "Server stop request unavailable or unsupported.",
            };
            if let Ok(mut pending) = notices.lock() {
                pending.push_back(Notice::Manual(vec![format!("[MineLights] {text}")]));
            }
        }
        while let Ok((remote, result)) = remote_connect_rx.try_recv() {
            let remote: minelights_protocol::RemoteServer = remote;
            remote_connecting.remove(&remote.endpoint.server_id);
            let still_configured = config_dir
                .as_deref()
                .and_then(|dir| minelights_protocol::load_remote_servers(dir).ok())
                .is_some_and(|servers| {
                    servers.iter().any(|saved| {
                        saved.endpoint == remote.endpoint
                            && saved.client_id == remote.client_id
                            && saved.client_secret == remote.client_secret
                    })
                });
            if is_active && still_configured {
                if let Ok((connection, devices)) = result {
                    v2_layouts.insert(
                        remote.endpoint.server_id.clone(),
                        devices.into_iter().map(|device| device.layout).collect(),
                    );
                    remote_v2.insert(remote.endpoint.server_id, connection);
                    next_handshake = Instant::now();
                }
            }
        }
        if active.load(Ordering::Acquire) {
            let requested = actions.swap(0, Ordering::AcqRel);
            if requested & ACTION_REFRESH != 0 {
                direct.disconnect_openrgb();
                direct.disconnect_yeelights();
                next_yeelight_discovery = Instant::now();
                next_handshake = Instant::now();
            }
            if requested & ACTION_RESTART != 0 {
                if let Ok(mut pending) = notices.lock() {
                    pending.push_back(Notice::Manual(vec![
                        "[MineLights] Restart MineLights Server from its tray menu.".into(),
                    ]));
                }
            }
            if requested & ACTION_RESTART_ADMIN != 0 {
                if let Ok(mut pending) = notices.lock() {
                    pending.push_back(Notice::Manual(vec!["[MineLights] Server lifecycle is managed from the MineLights Server tray menu.".into()]));
                }
            }
            if requested & ACTION_CHECK_UPDATE != 0 && cfg!(windows) {
                check_updates_async(Arc::clone(&notices), true);
            }
            if Instant::now() >= next_handshake {
                let config = settings
                    .read()
                    .map(|current| current.clone())
                    .unwrap_or_default();
                let now_in_world = in_world.load(Ordering::Acquire);
                if now_in_world && !was_in_world {
                    missing_reported = false;
                }
                was_in_world = now_in_world;
                if cfg!(windows) && now_in_world && config.check_server_updates && !update_checked {
                    update_checked = true;
                    check_updates_async(Arc::clone(&notices), false);
                }
                let running = server_is_running();
                if cfg!(windows)
                    && now_in_world
                    && config.notify_missing_server
                    && !running
                    && !server_path.as_deref().is_some_and(|path| path.is_file())
                    && !missing_reported
                {
                    missing_reported = true;
                    if let Ok(mut pending) = notices.lock() {
                        pending.push_back(Notice::MissingServer);
                    }
                }
                if auto_start.load(Ordering::Relaxed)
                    && now_in_world
                    && cfg!(windows)
                    && Instant::now().duration_since(last_launch_attempt)
                        >= Duration::from_secs(600)
                    && !running
                {
                    last_launch_attempt = Instant::now();
                    if let Some(path) = server_path.clone() {
                        start_reviewed_server_async(
                            path,
                            Arc::clone(&auto_start),
                            Arc::clone(&in_world),
                            Arc::clone(&notices),
                        );
                    }
                }
                let endpoint_result = minelights_protocol::local_endpoint();
                let endpoint = endpoint_result.ok().flatten();
                if endpoint.is_some() || !running {
                    old_server_since = None;
                } else if !old_server_notice_sent {
                    let since = *old_server_since.get_or_insert_with(Instant::now);
                    if since.elapsed() >= Duration::from_secs(30) {
                        if let Ok(mut pending) = notices.lock() {
                            pending.push_back(Notice::Manual(vec!["[MineLights] A MineLights Server process may be running without a V2 endpoint. If it is an older server, exit it from its tray and update to MineLights Server 2.4.0 or newer.".into()]));
                            old_server_notice_sent = true;
                        }
                    }
                }
                if let Some(endpoint) = endpoint {
                    if local_v2
                        .as_ref()
                        .is_none_or(|connection| !connection.matches_endpoint(&endpoint))
                    {
                        local_v2 = None;
                        if let Some(config_dir) = config_dir.as_deref() {
                            if let Ok(Some((connection, _))) =
                                minelights_protocol::connect_local_with_integrations(
                                    config_dir,
                                    &requested_providers(&config),
                                )
                            {
                                let mut connection = connection;
                                if !legacy_reminder_sent && connection.legacy_local_access_enabled()
                                {
                                    if let Ok(mut pending) = notices.lock() {
                                        pending.push_back(Notice::Manual(vec!["[MineLights] Legacy local access remains enabled in MineLights Server. If older clients no longer need it, turn off Allow legacy local clients in the server tray menu.".into()]));
                                        legacy_reminder_sent = true;
                                    }
                                }
                                local_v2 = Some(connection);
                            }
                        }
                    }
                    if let Some(connection) = local_v2.as_mut() {
                        if let Ok(Some(changed)) = connection.poll() {
                            v2_layouts.insert(connection.server_id().to_owned(), changed);
                        }
                    }
                } else {
                    local_v2 = None;
                }
                if let Some(config_dir) = config_dir.as_deref() {
                    let configured_remotes =
                        minelights_protocol::load_remote_servers(config_dir).unwrap_or_default();
                    let keep = configured_remotes
                        .iter()
                        .map(|remote| remote.endpoint.server_id.as_str())
                        .collect::<HashSet<_>>();
                    let local_server_id = local_v2
                        .as_ref()
                        .map(|connection| connection.server_id().to_owned());
                    remote_v2.retain(|server_id, _| {
                        keep.contains(server_id.as_str())
                            && local_server_id.as_deref() != Some(server_id.as_str())
                    });
                    v2_layouts.retain(|server_id, _| {
                        local_v2
                            .as_ref()
                            .is_some_and(|connection| connection.server_id() == server_id)
                            || keep.contains(server_id.as_str())
                    });
                    for remote in configured_remotes.into_iter().take(1022) {
                        if local_v2.as_ref().is_some_and(|connection| {
                            connection.server_id() == remote.endpoint.server_id
                        }) {
                            continue;
                        }
                        let needs_connect = remote_v2
                            .get(&remote.endpoint.server_id)
                            .is_none_or(|connection| !connection.matches_remote(&remote));
                        if needs_connect
                            && !remote_connecting.contains(&remote.endpoint.server_id)
                            && remote_connecting.len() < 4
                        {
                            remote_v2.remove(&remote.endpoint.server_id);
                            v2_layouts.remove(&remote.endpoint.server_id);
                            let id = remote.endpoint.server_id.clone();
                            remote_connecting.insert(id.clone());
                            let send = remote_connect_tx.clone();
                            let providers = requested_providers(&config);
                            if thread::Builder::new()
                                .name("MineLights-Remote-Connect".into())
                                .spawn(move || {
                                    let result =
                                        minelights_protocol::connect_remote_with_integrations(
                                            &remote, &providers,
                                        );
                                    let _ = send.send((remote, result));
                                })
                                .is_err()
                            {
                                remote_connecting.remove(&id);
                            }
                        }
                    }
                }

                // Assign disjoint renderer IDs while retaining (server, device,
                // element) identity on each session's frame router.
                if let Some(connection) = local_v2.as_mut() {
                    let assigned = connection.assign_output_ids(0);
                    v2_layouts.insert(connection.server_id().to_owned(), assigned);
                }
                let mut remote_ids = remote_v2.keys().cloned().collect::<Vec<_>>();
                remote_ids.sort();
                for (index, server_id) in remote_ids.iter().enumerate() {
                    if let Some(connection) = remote_v2.get_mut(server_id) {
                        let assigned = connection.assign_output_ids((index as u32) + 1);
                        v2_layouts.insert(server_id.clone(), assigned);
                    }
                }

                if config.enable_openrgb {
                    let openrgb_endpoints = config
                        .openrgb_endpoints
                        .iter()
                        .map(|endpoint| (endpoint.host.clone(), endpoint.port))
                        .collect::<Vec<_>>();
                    direct.configure_openrgb(&openrgb_endpoints);
                } else {
                    direct.disconnect_openrgb();
                }
                if config.enable_yeelight {
                    if Instant::now() >= next_yeelight_discovery {
                        direct.discover_yeelights();
                        next_yeelight_discovery = Instant::now() + Duration::from_secs(60);
                    }
                } else {
                    direct.disconnect_yeelights();
                    next_yeelight_discovery = Instant::now() + Duration::from_secs(5);
                }
                direct_layouts = direct
                    .layouts_with_overrides(&config.led_overrides)
                    .into_iter()
                    .map(|device| DeviceLayout {
                        keys: device.keys,
                        leds: device.leds,
                        unique_id: device.unique_id,
                        legacy_id: None,
                        display_name: device.display_name,
                        integration: device.integration,
                    })
                    .collect();
                publish_layouts(&layouts, &v2_layouts, &direct_layouts);
                #[cfg(test)]
                TEST_COMPLETED_HANDSHAKES.fetch_add(1, Ordering::Release);
                next_handshake = Instant::now() + Duration::from_secs(5);
            }
        }

        let mut changed_layouts = false;
        let mut local_failed = false;
        if let Some(connection) = local_v2.as_mut() {
            match connection.poll() {
                Ok(Some(changed)) => {
                    v2_layouts.insert(connection.server_id().to_owned(), changed);
                    changed_layouts = true;
                }
                Err(_) => {
                    v2_layouts.remove(connection.server_id());
                    local_failed = true;
                    next_handshake = Instant::now();
                    changed_layouts = true;
                }
                _ => {}
            }
        }
        if local_failed {
            local_v2 = None;
        }
        let mut failed_remotes = Vec::new();
        for (server_id, connection) in remote_v2.iter_mut() {
            match connection.poll() {
                Ok(Some(changed)) => {
                    v2_layouts.insert(server_id.clone(), changed);
                    changed_layouts = true;
                }
                Err(_) => failed_remotes.push(server_id.clone()),
                _ => {}
            }
        }
        for server_id in failed_remotes {
            remote_v2.remove(&server_id);
            v2_layouts.remove(&server_id);
            next_handshake = Instant::now();
            changed_layouts = true;
        }
        if changed_layouts {
            publish_layouts(&layouts, &v2_layouts, &direct_layouts);
        }

        // Reconcile each session against this client's current provider/device settings.
        // Control connections start with no leases, so excluded devices are never acquired.
        let current_policy = settings
            .read()
            .map(|config| config.clone())
            .unwrap_or_default();
        let direct_policy = (
            current_policy.enable_openrgb,
            current_policy.enable_yeelight,
            current_policy.openrgb_endpoints.clone(),
        );
        if previous_direct.as_ref() != Some(&direct_policy) {
            next_handshake = Instant::now();
            next_yeelight_discovery = Instant::now();
            previous_direct = Some(direct_policy);
        }
        let local_policy_failed = local_v2
            .as_mut()
            .is_some_and(|connection| apply_v2_policy(connection, &current_policy).is_err());
        if local_policy_failed {
            if let Some(connection) = local_v2.take() {
                v2_layouts.remove(connection.server_id());
            }
            next_handshake = Instant::now() + Duration::from_secs(5);
            publish_layouts(&layouts, &v2_layouts, &direct_layouts);
        }
        let failed_policy_remotes = remote_v2
            .iter_mut()
            .filter_map(|(id, connection)| {
                apply_v2_policy(connection, &current_policy)
                    .err()
                    .map(|_| id.clone())
            })
            .collect::<Vec<_>>();
        for id in failed_policy_remotes {
            remote_v2.remove(&id);
            v2_layouts.remove(&id);
            next_handshake = Instant::now() + Duration::from_secs(5);
            publish_layouts(&layouts, &v2_layouts, &direct_layouts);
        }

        if is_active {
            if let Some(config) = settings.read().ok().map(|value| value.clone()) {
                direct.set_openrgb_state(
                    in_world.load(Ordering::Acquire),
                    config.restore_lighting_outside_world,
                    &config.disabled_devices,
                    &config.outside_world_profile,
                );
            }
        }
        match receiver.recv_timeout(Duration::from_millis(25)) {
            Ok(frame) => {
                if let Some(connection) = local_v2.as_mut() {
                    let _ = connection.send_frame(
                        &frame
                            .keys
                            .iter()
                            .map(|(id, color)| (*id, [color.0, color.1, color.2]))
                            .collect(),
                    );
                }
                for connection in remote_v2.values_mut() {
                    let _ = connection.send_frame(
                        &frame
                            .keys
                            .iter()
                            .map(|(id, color)| (*id, [color.0, color.1, color.2]))
                            .collect(),
                    );
                }
                let direct_frame = frame
                    .keys
                    .iter()
                    .map(|(id, color)| (*id, [color.0, color.1, color.2]))
                    .collect();
                direct.update(&direct_frame, &current_policy.disabled_devices);
            }
            Err(mpsc::RecvTimeoutError::Timeout) => {}
            Err(mpsc::RecvTimeoutError::Disconnected) => break,
        }
    }
    WORKER_FINISHED.store(true, Ordering::Release);
}

fn requested_providers(settings: &Settings) -> Vec<String> {
    [
        ("Corsair", settings.enable_corsair),
        ("Asus", settings.enable_asus),
        ("Logitech", settings.enable_logitech),
        ("Razer", settings.enable_razer),
        ("Wooting", settings.enable_wooting),
        ("SteelSeries", settings.enable_steelseries),
        ("Msi", settings.enable_msi),
        ("Novation", settings.enable_novation),
        ("PicoPi", settings.enable_picopi),
    ]
    .iter()
    .filter_map(|(name, enabled)| enabled.then(|| (*name).to_owned()))
    .collect()
}

fn v2_device_allowed(
    config: &Settings,
    identity: &str,
    legacy_key: Option<&str>,
    provider: Option<&str>,
    integration: &str,
) -> bool {
    if super::minelights_config::device_disabled(config, identity, legacy_key) {
        return false;
    }
    let source = provider
        .filter(|p| !p.trim().is_empty())
        .unwrap_or(integration)
        .to_ascii_lowercase()
        .chars()
        .filter(|c| c.is_ascii_alphanumeric())
        .collect::<String>();
    if source.contains("corsair") {
        config.enable_corsair
    } else if source.contains("asus") || source.contains("asustek") {
        config.enable_asus
    } else if source.contains("logitech") {
        config.enable_logitech
    } else if source.contains("razer") {
        config.enable_razer
    } else if source.contains("wooting") {
        config.enable_wooting
    } else if source.contains("steelseries") {
        config.enable_steelseries
    } else if source.contains("msi") || source.contains("microstar") {
        config.enable_msi
    } else if source.contains("novation") {
        config.enable_novation
    } else if source.contains("picopi") {
        config.enable_picopi
    } else {
        true
    }
}

fn apply_v2_policy(connection: &mut V2Connection, config: &Settings) -> Result<(), String> {
    let allowed = connection
        .devices
        .iter()
        .filter(|device| {
            v2_device_allowed(
                config,
                &device.layout.unique_id,
                device.legacy_device_key.as_deref(),
                device.provider.as_deref(),
                &device.layout.integration,
            )
        })
        .map(|device| device.device_id.clone())
        .collect();
    connection.set_allowed_devices(allowed);
    connection.set_requested_integrations(&requested_providers(config))
}

#[cfg(test)]
mod policy_tests {
    use super::*;
    #[test]
    fn provider_toggle_and_legacy_device_exclusions_are_client_local() {
        let mut config = Settings::default();
        config.enable_corsair = false;
        assert!(!requested_providers(&config).contains(&"Corsair".to_string()));
        assert!(requested_providers(&config).contains(&"Asus".to_string()));
        assert!(!v2_device_allowed(
            &config,
            "server|1",
            Some("Corsair|Keyboard"),
            Some("Corsair"),
            "Other vendor"
        ));
        assert!(v2_device_allowed(
            &config,
            "server|2",
            Some("Asus|Mouse"),
            Some("Asus"),
            "ASUSTeK"
        ));
        config.enable_corsair = true;
        config.disabled_devices.push("Corsair|Keyboard".into());
        assert!(!v2_device_allowed(
            &config,
            "server|1",
            Some("Corsair|Keyboard"),
            Some("Corsair"),
            "Corsair"
        ));
        assert!(v2_device_allowed(
            &config,
            "server|2",
            Some("Asus|Mouse"),
            Some("Asus"),
            "Asus"
        ));
        // An explicit enable affects only this stable device, not another sharing its old model key.
        super::super::minelights_config::set_device_enabled(
            &mut config,
            "server|1",
            Some("Corsair|Keyboard"),
            true,
        );
        assert!(v2_device_allowed(
            &config,
            "server|1",
            Some("Corsair|Keyboard"),
            Some("Corsair"),
            "Corsair"
        ));
        assert!(!v2_device_allowed(
            &config,
            "server|3",
            Some("Corsair|Keyboard"),
            Some("Corsair"),
            "Corsair"
        ));
        super::super::minelights_config::set_device_enabled(
            &mut config,
            "server|1",
            Some("Corsair|Keyboard"),
            false,
        );
        assert!(!v2_device_allowed(
            &config,
            "server|1",
            Some("Corsair|Keyboard"),
            Some("Corsair"),
            "Corsair"
        ));
        config.disabled_devices.clear();
        config.enable_asus = false;
        assert!(!v2_device_allowed(
            &config,
            "server|2",
            None,
            None,
            "ASUSTeK COMPUTER INC."
        ));
    }
}

#[cfg(test)]
mod server_readiness_tests {
    use super::{ReadinessWait, wait_for_server_readiness};
    use std::time::Duration;

    #[test]
    fn waits_until_the_endpoint_is_ready_while_the_child_is_alive() {
        let mut endpoint_checks = 0;
        let result = wait_for_server_readiness(
            || {
                endpoint_checks += 1;
                endpoint_checks == 3
            },
            || false,
            || false,
            Duration::from_millis(100),
            Duration::from_millis(1),
        );
        assert_eq!(result, ReadinessWait::Ready);
        assert_eq!(endpoint_checks, 3);
    }

    #[test]
    fn reports_a_child_that_exits_before_readiness() {
        let result = wait_for_server_readiness(
            || false,
            || true,
            || false,
            Duration::from_millis(100),
            Duration::from_millis(1),
        );
        assert_eq!(result, ReadinessWait::ProcessExited);
    }

    #[test]
    fn permission_or_shutdown_cancellation_interrupts_the_wait() {
        let result = wait_for_server_readiness(
            || false,
            || false,
            || true,
            Duration::from_secs(30),
            Duration::from_millis(1),
        );
        assert_eq!(result, ReadinessWait::Cancelled);
    }

    #[test]
    fn bounds_the_wait_when_the_endpoint_never_appears() {
        let result = wait_for_server_readiness(
            || false,
            || false,
            || false,
            Duration::ZERO,
            Duration::from_millis(1),
        );
        assert_eq!(result, ReadinessWait::TimedOut);
    }
}

fn start_reviewed_server_async(
    path: PathBuf,
    permission: Arc<AtomicBool>,
    in_world: Arc<AtomicBool>,
    notices: Arc<Mutex<VecDeque<Notice>>>,
) {
    if MANAGED_SERVER_BUSY
        .compare_exchange(false, true, Ordering::AcqRel, Ordering::Acquire)
        .is_err()
    {
        return;
    }
    let failed_notices = Arc::clone(&notices);
    if let Err(error) = thread::Builder::new().name("MineLights-Reviewed-Server".into()).spawn(move || {
        struct BusyGuard;
        impl Drop for BusyGuard {
            fn drop(&mut self) { MANAGED_SERVER_BUSY.store(false, Ordering::Release); }
        }
        let _guard = BusyGuard;
        let _operation_lock = match minelights_server::ServerOperationLock::try_acquire() {
            Ok(Some(lock)) => lock,
            Ok(None) => return,
            Err(error) => {
                log_server_diagnostic("MineLights Server operation lock", &error.to_string());
                return;
            }
        };
        if !path.exists() {
            if !permission.load(Ordering::Acquire) {
                return;
            }
            let server_was_running_before = server_is_running();
            SERVER_DOWNLOAD_IN_PROGRESS.store(false, Ordering::Release);
            struct DownloadGuard;
            impl Drop for DownloadGuard {
                fn drop(&mut self) {
                    SERVER_DOWNLOAD_IN_PROGRESS.store(false, Ordering::Release);
                }
            }
            let download_guard = DownloadGuard;
            let result = minelights_server::download_pinned_detailed_with_started(
                &path,
                permission.load(Ordering::Acquire),
                || {
                    let began_in_world = in_world.load(Ordering::Acquire);
                    SERVER_DOWNLOAD_IN_PROGRESS.store(true, Ordering::Release);
                    if let Ok(mut pending) = notices.lock() {
                        pending.push_back(Notice::ServerDownloadStarted { began_in_world });
                    }
                },
            );
            if let Err(reason) = &result {
                log_server_diagnostic("MineLights Server download", reason);
            }
            drop(download_guard);
            let server_start = if result.is_ok()
                && permission.load(Ordering::Acquire)
                && !SHUTTING_DOWN.load(Ordering::Acquire)
                && path.is_file()
                && minelights_server::is_reviewed_binary(&path)
            {
                if server_is_running_fresh() {
                    DownloadedServerStart::AlreadyRunning
                } else {
                    start_downloaded_server_and_wait_ready(&path, &permission)
                }
            } else {
                DownloadedServerStart::Failed
            };
            let server_started = matches!(server_start, DownloadedServerStart::Ready);
            let server_readiness_failed = matches!(server_start, DownloadedServerStart::ReadinessFailed);
            let server_running_after = !server_started && server_is_running_fresh();
            if let Ok(mut pending) = notices.lock() {
                pending.push_back(Notice::ServerDownloadFinished {
                    result,
                    server_was_running_before,
                    server_running_after,
                    server_started,
                    server_readiness_failed,
                });
            }
            return;
        }
        if !permission.load(Ordering::Acquire) || SHUTTING_DOWN.load(Ordering::Acquire) { return; }
        if path.is_file() && minelights_server::is_reviewed_binary(&path) {
            let _ = start_installed_server(Some(&path), &permission);
        } else if let Ok(mut pending) = notices.lock() {
            let message = if path.is_file() {
                "[MineLights] The installed server does not match the reviewed hash. Start it manually, or remove that file to allow a verified download."
            } else {
                "[MineLights] The reviewed server could not be downloaded and verified. Install and start it yourself: https://github.com/megabytesme/MineLights-Server/releases"
            };
            pending.push_back(Notice::Manual(vec![message.into()]));
        }
    }) {
        MANAGED_SERVER_BUSY.store(false, Ordering::Release);
        log_server_diagnostic(
            "MineLights Server startup",
            &format!("could not create the worker thread ({:?})", error.kind()),
        );
        if let Ok(mut pending) = failed_notices.lock() {
            pending.push_back(Notice::ServerStartFailed);
        }
    }
}

fn log_server_diagnostic(context: &str, detail: &str) {
    use std::io::Write;
    let safe_detail: String = detail
        .chars()
        .filter(|character| !character.is_control())
        .take(180)
        .collect();
    let _ = writeln!(
        std::io::stderr().lock(),
        "[MineLights] {context}: {safe_detail}"
    );
}

fn check_updates_async(notices: Arc<Mutex<VecDeque<Notice>>>, manual: bool) {
    let _ = thread::Builder::new()
        .name("MineLights-Server-Update-Check".into())
        .spawn(move || {
            let notice = match minelights_server::check_for_update() {
                Ok(Some(update)) => Notice::Update(minelights_server::update_messages(&update)),
                Ok(None) if manual => Notice::Manual(vec![format!(
                    "[MineLights] No server release newer than the reviewed {} was found.",
                    minelights_server::SERVER_VERSION
                )]),
                Err(reason) if manual => {
                    log_server_diagnostic("MineLights Server update check", &reason);
                    Notice::UpdateCheckFailed { manual }
                }
                _ => return,
            };
            if let Ok(mut pending) = notices.lock() {
                pending.push_back(notice);
            }
        });
}

fn start_installed_server(path: Option<&std::path::Path>, permission: &AtomicBool) -> bool {
    let Ok(_lifecycle) = SERVER_LIFECYCLE.lock() else {
        return false;
    };
    if SHUTTING_DOWN.load(Ordering::Acquire) || !permission.load(Ordering::Acquire) {
        return false;
    }
    let Some(path) = path.filter(|path| path.is_file()) else {
        return false;
    };
    if !minelights_server::is_reviewed_binary(path) || !permission.load(Ordering::Acquire) {
        return false;
    }
    if server_is_running_fresh() {
        return false;
    }
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        let Ok(launch_id) = minelights_engine::new_launch_id() else {
            return false;
        };
        let mut command = std::process::Command::new(path);
        command
            .arg("--client-started")
            .env("MINELIGHTS_LAUNCH_ID", &launch_id)
            .current_dir(path.parent().unwrap_or_else(|| std::path::Path::new(".")))
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::null())
            .creation_flags(0x0800_0000);
        let started = command.spawn().is_ok();
        if started {
            if let Ok(mut id) = MANAGED_SERVER_LAUNCH_ID.lock() {
                *id = Some(launch_id);
            }
            MANAGED_SERVER_STARTED.store(true, Ordering::Release);
        }
        return started;
    }
    #[cfg(not(windows))]
    {
        let _ = path;
        false
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum DownloadedServerStart {
    Ready,
    AlreadyRunning,
    Failed,
    ReadinessFailed,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum ReadinessWait {
    Ready,
    ProcessExited,
    Cancelled,
    TimedOut,
}

fn wait_for_server_readiness(
    mut endpoint_ready: impl FnMut() -> bool,
    mut process_exited: impl FnMut() -> bool,
    mut cancelled: impl FnMut() -> bool,
    timeout: Duration,
    poll_interval: Duration,
) -> ReadinessWait {
    let deadline = Instant::now() + timeout;
    loop {
        if cancelled() {
            return ReadinessWait::Cancelled;
        }
        if process_exited() {
            return ReadinessWait::ProcessExited;
        }
        if endpoint_ready() {
            if cancelled() {
                return ReadinessWait::Cancelled;
            }
            if process_exited() {
                return ReadinessWait::ProcessExited;
            }
            return ReadinessWait::Ready;
        }
        let now = Instant::now();
        if now >= deadline {
            return ReadinessWait::TimedOut;
        }
        thread::sleep(poll_interval.min(deadline.saturating_duration_since(now)));
    }
}

fn start_downloaded_server_and_wait_ready(
    path: &std::path::Path,
    permission: &AtomicBool,
) -> DownloadedServerStart {
    let Ok(_lifecycle) = SERVER_LIFECYCLE.lock() else {
        return DownloadedServerStart::Failed;
    };
    if SHUTTING_DOWN.load(Ordering::Acquire) || !permission.load(Ordering::Acquire) {
        return DownloadedServerStart::Failed;
    }
    if !path.is_file() || !minelights_server::is_reviewed_binary(path) {
        return DownloadedServerStart::Failed;
    }
    if server_is_running_fresh() {
        return DownloadedServerStart::AlreadyRunning;
    }

    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        let Ok(launch_id) = minelights_engine::new_launch_id() else {
            return DownloadedServerStart::Failed;
        };
        let mut command = std::process::Command::new(path);
        command
            .arg("--client-started")
            .env("MINELIGHTS_LAUNCH_ID", &launch_id)
            .current_dir(path.parent().unwrap_or_else(|| std::path::Path::new(".")))
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::null())
            .creation_flags(0x0800_0000);
        let Ok(mut child) = command.spawn() else {
            return DownloadedServerStart::Failed;
        };

        let readiness = wait_for_server_readiness(
            || {
                minelights_protocol::local_endpoint()
                    .ok()
                    .flatten()
                    .is_some()
            },
            || {
                child
                    .try_wait()
                    .map(|status| status.is_some())
                    .unwrap_or(true)
            },
            || SHUTTING_DOWN.load(Ordering::Acquire) || !permission.load(Ordering::Acquire),
            Duration::from_secs(30),
            Duration::from_millis(200),
        );
        let readiness = if readiness == ReadinessWait::Ready
            && (SHUTTING_DOWN.load(Ordering::Acquire) || !permission.load(Ordering::Acquire))
        {
            ReadinessWait::Cancelled
        } else {
            readiness
        };
        match readiness {
            ReadinessWait::Ready => {
                if let Ok(mut id) = MANAGED_SERVER_LAUNCH_ID.lock() {
                    *id = Some(launch_id);
                }
                MANAGED_SERVER_STARTED.store(true, Ordering::Release);
                DownloadedServerStart::Ready
            }
            reason => {
                log_server_diagnostic(
                    "MineLights Server readiness",
                    match reason {
                        ReadinessWait::ProcessExited => {
                            "process exited before its V2 endpoint became reachable"
                        }
                        ReadinessWait::Cancelled => {
                            "startup was cancelled before its V2 endpoint became reachable"
                        }
                        ReadinessWait::TimedOut => {
                            "V2 endpoint did not become reachable within 30 seconds"
                        }
                        ReadinessWait::Ready => unreachable!(),
                    },
                );
                match child.try_wait() {
                    Ok(Some(_)) => {}
                    Ok(None) | Err(_) => {
                        let _ = child.kill();
                        let _ = child.wait();
                    }
                }
                DownloadedServerStart::ReadinessFailed
            }
        }
    }
    #[cfg(not(windows))]
    {
        let _ = path;
        DownloadedServerStart::Failed
    }
}

fn server_is_running() -> bool {
    if let Ok(Some(endpoint)) = minelights_protocol::local_endpoint() {
        let reachable = (endpoint.host.as_str(), endpoint.port)
            .to_socket_addrs()
            .ok()
            .into_iter()
            .flatten()
            .any(|address| {
                TcpStream::connect_timeout(&address, Duration::from_millis(150)).is_ok()
            });
        if reachable {
            return true;
        }
    }
    cached_server_process_running()
}

/// Bypasses the UI polling cache at file replacement and process launch boundaries.
fn server_is_running_fresh() -> bool {
    if let Ok(Some(endpoint)) = minelights_protocol::local_endpoint() {
        let reachable = (endpoint.host.as_str(), endpoint.port)
            .to_socket_addrs()
            .ok()
            .into_iter()
            .flatten()
            .any(|address| {
                TcpStream::connect_timeout(&address, Duration::from_millis(150)).is_ok()
            });
        if reachable {
            return true;
        }
    }
    #[cfg(windows)]
    {
        return legacy_server_process_running();
    }
    #[cfg(not(windows))]
    {
        false
    }
}

#[cfg(windows)]
fn cached_server_process_running() -> bool {
    static LAST: Mutex<Option<Instant>> = Mutex::new(None);
    static RUNNING: AtomicBool = AtomicBool::new(true);
    static BUSY: AtomicBool = AtomicBool::new(false);
    let due = LAST
        .lock()
        .map(|mut last| {
            if last.is_some_and(|at| at.elapsed() < Duration::from_secs(3)) {
                false
            } else {
                *last = Some(Instant::now());
                true
            }
        })
        .unwrap_or(false);
    if due
        && BUSY
            .compare_exchange(false, true, Ordering::AcqRel, Ordering::Acquire)
            .is_ok()
    {
        if thread::Builder::new()
            .name("MineLights-Process-Check".into())
            .spawn(|| {
                RUNNING.store(legacy_server_process_running(), Ordering::Release);
                BUSY.store(false, Ordering::Release);
            })
            .is_err()
        {
            BUSY.store(false, Ordering::Release);
        }
    }
    RUNNING.load(Ordering::Acquire)
}

#[cfg(not(windows))]
fn cached_server_process_running() -> bool {
    false
}

#[cfg(windows)]
fn legacy_server_process_running() -> bool {
    use std::os::windows::process::CommandExt;
    let Some(system_root) = std::env::var_os("SystemRoot") else {
        return true;
    };
    let root = std::path::PathBuf::from(system_root);
    if !root.is_absolute() {
        return true;
    }
    let system_dir = if std::env::var_os("PROCESSOR_ARCHITEW6432").is_some_and(|v| !v.is_empty()) {
        "Sysnative"
    } else {
        "System32"
    };
    let executable = root.join(system_dir).join("tasklist.exe");
    if !executable.is_file() {
        return true;
    }
    let process = std::process::Command::new(executable)
        .args(["/FI", "IMAGENAME eq MineLights.exe", "/FO", "CSV", "/NH"])
        .creation_flags(0x0800_0000)
        .stdout(std::process::Stdio::piped())
        .stderr(std::process::Stdio::null())
        .spawn();
    let Ok(mut process) = process else {
        return true;
    };
    let deadline = Instant::now() + Duration::from_secs(3);
    loop {
        match process.try_wait() {
            Ok(Some(status)) if status.success() => {
                use std::io::Read;
                let mut output = String::new();
                if process
                    .stdout
                    .take()
                    .is_none_or(|mut stdout| stdout.read_to_string(&mut output).is_err())
                {
                    return true;
                }
                return output
                    .lines()
                    .any(|line| line.to_ascii_lowercase().starts_with("\"minelights.exe\","));
            }
            Ok(Some(_)) | Err(_) => return true,
            Ok(None) if Instant::now() >= deadline => {
                let _ = process.kill();
                let _ = process.wait();
                return true;
            }
            Ok(None) => thread::sleep(Duration::from_millis(25)),
        }
    }
}

#[cfg(not(windows))]
fn legacy_server_process_running() -> bool {
    false
}

pub(super) fn minelights_shutdown() {
    // Set this before waiting for the lifecycle lock so a startup readiness
    // wait holding that lock can observe shutdown and terminate promptly.
    SHUTTING_DOWN.store(true, Ordering::Release);
    if let Ok(_lifecycle) = SERVER_LIFECYCLE.lock() {
        // Acquiring the lock still serializes shutdown with process creation.
    }
    if let Some(bridge) = BRIDGE.get() {
        bridge.active.store(false, Ordering::Release);
        bridge.auto_start.store(false, Ordering::Release);
        bridge.in_world.store(false, Ordering::Release);
        let deadline = Instant::now() + Duration::from_secs(4);
        while !WORKER_FINISHED.load(Ordering::Acquire) && Instant::now() < deadline {
            thread::sleep(Duration::from_millis(20));
        }
    }
}

fn publish_layouts(
    target: &RwLock<Vec<DeviceLayout>>,
    v2: &HashMap<String, Vec<DeviceLayout>>,
    direct: &[DeviceLayout],
) {
    let mut all = Vec::new();
    let mut servers = v2.keys().cloned().collect::<Vec<_>>();
    servers.sort();
    for server in servers {
        if let Some(layouts) = v2.get(&server) {
            all.extend_from_slice(layouts);
        }
    }
    all.extend_from_slice(direct);
    if let Ok(mut current) = target.write() {
        *current = all;
    }
}
