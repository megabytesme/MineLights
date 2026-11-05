use std::{
    collections::{HashMap, HashSet, VecDeque},
    io::{Read, Write},
    net::{TcpStream, ToSocketAddrs, UdpSocket},
    path::PathBuf,
    sync::{
        Arc, Mutex, OnceLock, RwLock,
        atomic::{AtomicBool, AtomicU32, Ordering},
        mpsc::{self, SyncSender},
    },
    thread,
    time::{Duration, Instant},
};

// A failure to authenticate an advertised endpoint must not silently enable
// unauthenticated V1. A user may explicitly opt into local-only fallback.
pub(super) fn allow_v1_fallback(explicit: bool, endpoint_present: bool, reviewed_binary: bool) -> bool {
    explicit || (!endpoint_present && !reviewed_binary)
}

use super::minelights::{DeviceLayout, Frame, trailing_number};
use super::minelights_config::Settings;
use super::minelights_direct::DirectOutputs;
use super::minelights_protocol::{self, V2Connection};
use super::minelights_server;

const FRAME_PORT: u16 = 63212;

#[derive(Clone, Copy, Debug, PartialEq, Eq, serde::Serialize)]
struct LedColor {
    id: u32,
    r: u8,
    g: u8,
    b: u8,
}

pub(super) const ACTION_REFRESH: u32 = 1;
pub(super) const ACTION_RESTART: u32 = 2;
pub(super) const ACTION_RESTART_ADMIN: u32 = 4;
pub(super) const ACTION_CHECK_UPDATE: u32 = 8;

pub(super) enum Notice {
    MissingServer,
    Update(Vec<String>),
    Manual(Vec<String>),
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
    if !settings.allow_reviewed_server { MANAGED_SERVER_STOPPED.store(false, Ordering::Release); }
    bridge
        .auto_start
        .store(settings.allow_reviewed_server && !MANAGED_SERVER_STOPPED.load(Ordering::Acquire), Ordering::Relaxed);
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
    let udp = UdpSocket::bind("127.0.0.1:0").ok();
    let mut direct = DirectOutputs::default();
    let mut local_v2: Option<V2Connection> = None;
    let mut remote_v2: HashMap<String, V2Connection> = HashMap::new();
    let mut v2_layouts: HashMap<String, Vec<DeviceLayout>> = HashMap::new();
    let mut legacy_devices = Vec::new();
    let mut direct_layouts = Vec::new();
    let mut next_handshake = Instant::now();
    let mut next_yeelight_discovery = Instant::now();
    let mut last_launch_attempt = Instant::now() - Duration::from_secs(600);
    let mut was_in_world = false;
    let mut missing_reported = false;
    let mut update_checked = false;
    let mut legacy_led_ids = HashSet::new();
    let mut was_active = true;
    loop {
        let is_active = active.load(Ordering::Acquire);
        if SHUTTING_DOWN.load(Ordering::Acquire) && MANAGED_SERVER_STARTED.load(Ordering::Acquire) {
            let launch_id = MANAGED_SERVER_LAUNCH_ID.lock().ok().and_then(|id| id.clone());
            if let Some(expected) = launch_id.as_deref() {
                if let Some(connection) = local_v2.as_mut() {
                    if connection.matches_launch_id(expected) { let _ = connection.request_server_shutdown(); }
                } else if let Some(dir) = config_dir.as_deref() {
                    let deadline = Instant::now() + Duration::from_secs(2);
                    loop {
                        match minelights_protocol::connect_local_control(dir) {
                            Ok(Some((mut connection, _))) => {
                                if connection.matches_launch_id(expected) { let _ = connection.request_server_shutdown(); }
                                break;
                            }
                            Ok(None) if Instant::now() < deadline => thread::sleep(Duration::from_millis(100)),
                            _ => break,
                        }
                    }
                }
            }
        }
        if was_active && !is_active {
            // Dropping each stream releases its V2 leases immediately. The
            // shared server remains alive under tray ownership.
            let legacy_black = legacy_led_ids
                .iter()
                .map(|id| LedColor {
                    id: *id,
                    r: 0,
                    g: 0,
                    b: 0,
                })
                .collect::<Vec<_>>();
            if !legacy_black.is_empty() {
                if let (Some(socket), Ok(bytes)) = (
                    udp.as_ref(),
                    serde_json::to_vec(&serde_json::json!({"led_colors": legacy_black})),
                ) {
                    let _ = socket.send_to(&bytes, ("127.0.0.1", FRAME_PORT));
                }
            }
            let mut direct_black = HashMap::new();
            let current_direct_layouts = direct.layouts();
            for led in current_direct_layouts
                .iter()
                .flat_map(|layout| layout.leds.iter())
            {
                direct_black.insert(*led, [0, 0, 0]);
            }
            direct.update(&direct_black);
            direct.disconnect_openrgb();
            direct.disconnect_yeelights();
            direct_layouts.clear();
            local_v2 = None;
            remote_v2.clear();
            v2_layouts.clear();
            legacy_devices.clear();
            legacy_led_ids.clear();
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
        let stop_target = stop_request.lock().ok().and_then(|mut request| request.take());
        if let Some(target) = stop_target {
            let connection = if target.is_empty() { local_v2.as_mut() } else { remote_v2.get_mut(&target).or_else(|| local_v2.as_mut().filter(|local| local.server_id() == target)) };
            let mut result = connection.map(|c| c.request_server_shutdown()).unwrap_or("unavailable");
            if result == "unavailable" {
                if let Some(dir) = config_dir.as_deref() {
                    let control = if target.is_empty() {
                        minelights_protocol::connect_local_control(dir).ok().flatten().map(|(c, _)| c)
                    } else {
                        minelights_protocol::load_remote_servers(dir).ok()
                            .and_then(|servers| servers.into_iter().find(|server| server.endpoint.server_id == target))
                            .and_then(|server| minelights_protocol::connect_remote_control(&server).ok().map(|(c, _)| c))
                    };
                    if let Some(mut connection) = control { result = connection.request_server_shutdown(); }
                }
            }
            if target.is_empty() && matches!(result, "accepted" | "pending") {
                MANAGED_SERVER_STOPPED.store(true, Ordering::Release);
                auto_start.store(false, Ordering::Release);
            }
            let text = match result {
                "accepted" => "Server accepted the stop request.",
                "pending" => "Other clients are connected; the server will stop if they disconnect shortly.",
                "other_clients" => "Server remains running because other clients are connected.",
                _ => "Server stop request unavailable or unsupported.",
            };
            if let Ok(mut pending) = notices.lock() { pending.push_back(Notice::Manual(vec![format!("[MineLights] {text}")])); }
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
                            Arc::clone(&notices),
                        );
                    }
                }
                let endpoint_result = minelights_protocol::local_endpoint();
                let endpoint_file_exists = match &endpoint_result {
                    Ok(Some(_)) => true,
                    Ok(None) => false,
                    // Keep malformed metadata fail-closed rather than enabling V1 fallback.
                    Err(_) => minelights_protocol::local_endpoint_path()
                        .is_some_and(|path| path.exists()),
                };
                let endpoint = endpoint_result.ok().flatten();
                if let Some(endpoint) = endpoint {
                    if local_v2
                        .as_ref()
                        .is_none_or(|connection| !connection.matches_endpoint(&endpoint))
                    {
                        local_v2 = None;
                        if let Some(config_dir) = config_dir.as_deref() {
                            if let Ok(Some((connection, _))) =
                                minelights_protocol::connect_local(config_dir)
                            {
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
                let reviewed_binary = server_path
                    .as_deref()
                    .is_some_and(minelights_server::is_reviewed_binary);
                if local_v2.is_some() {
                    legacy_devices.clear();
                } else if allow_v1_fallback(
                    config.allow_legacy_v1_fallback,
                    endpoint_file_exists,
                    reviewed_binary,
                ) {
                    legacy_devices = perform_handshake(&config).unwrap_or_default();
                } else {
                    legacy_devices.clear();
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
                        if needs_connect {
                            remote_v2.remove(&remote.endpoint.server_id);
                            v2_layouts.remove(&remote.endpoint.server_id);
                            if let Ok((connection, devices)) =
                                minelights_protocol::connect_remote(&remote)
                            {
                                v2_layouts.insert(
                                    remote.endpoint.server_id.clone(),
                                    devices.into_iter().map(|device| device.layout).collect(),
                                );
                                remote_v2.insert(remote.endpoint.server_id.clone(), connection);
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
                legacy_led_ids = legacy_devices
                    .iter()
                    .flat_map(|device| device.leds.iter().copied())
                    .collect();
                direct_layouts = direct
                    .layouts()
                    .into_iter()
                    .map(|device| DeviceLayout {
                        keys: device.keys,
                        leds: device.leds,
                        unique_id: device.unique_id,
                        display_name: device.display_name,
                        integration: device.integration,
                    })
                    .collect();
                publish_layouts(&layouts, &legacy_devices, &v2_layouts, &direct_layouts);
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
            publish_layouts(&layouts, &legacy_devices, &v2_layouts, &direct_layouts);
        }

        match receiver.recv_timeout(Duration::from_millis(25)) {
            Ok(frame) => {
                let mut led_colors: Vec<LedColor> = frame
                    .keys
                    .iter()
                    .filter_map(|(id, color)| {
                        legacy_led_ids.contains(id).then_some(LedColor {
                            id: *id,
                            r: color.0,
                            g: color.1,
                            b: color.2,
                        })
                    })
                    .collect();
                led_colors.sort_by_key(|led| led.id);
                if !led_colors.is_empty() {
                    if let (Some(socket), Ok(bytes)) = (
                        udp.as_ref(),
                        serde_json::to_vec(&serde_json::json!({"led_colors": led_colors})),
                    ) {
                        let _ = socket.send_to(&bytes, ("127.0.0.1", FRAME_PORT));
                    }
                }
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
                direct.update(&direct_frame);
            }
            Err(mpsc::RecvTimeoutError::Timeout) => {}
            Err(mpsc::RecvTimeoutError::Disconnected) => break,
        }
    }
    WORKER_FINISHED.store(true, Ordering::Release);
}

fn start_reviewed_server_async(
    path: PathBuf,
    permission: Arc<AtomicBool>,
    notices: Arc<Mutex<VecDeque<Notice>>>,
) {
    if MANAGED_SERVER_BUSY
        .compare_exchange(false, true, Ordering::AcqRel, Ordering::Acquire)
        .is_err()
    {
        return;
    }
    if thread::Builder::new().name("MineLights-Reviewed-Server".into()).spawn(move || {
        struct BusyGuard;
        impl Drop for BusyGuard {
            fn drop(&mut self) { MANAGED_SERVER_BUSY.store(false, Ordering::Release); }
        }
        let _guard = BusyGuard;
        if !path.exists() {
            let _ = minelights_server::download_pinned(&path, permission.load(Ordering::Acquire));
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
    }).is_err() {
        MANAGED_SERVER_BUSY.store(false, Ordering::Release);
    }
}

fn check_updates_async(notices: Arc<Mutex<VecDeque<Notice>>>, manual: bool) {
    let _ = thread::Builder::new().name("MineLights-Server-Update-Check".into()).spawn(move || {
        let messages = match minelights_server::check_for_update() {
            Ok(Some(update)) => minelights_server::update_messages(&update),
            Ok(None) if manual => vec![format!("[MineLights] No server release newer than the reviewed {} was found.", minelights_server::SERVER_VERSION)],
            Err(_) if manual => vec!["[MineLights] Could not check for server updates. See https://github.com/megabytesme/MineLights-Server/releases".into()],
            _ => return,
        };
        if let Ok(mut pending) = notices.lock() {
            pending.push_back(if manual { Notice::Manual(messages) } else { Notice::Update(messages) });
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
    if server_is_running() {
        return false;
    }
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        let mut random = [0u8; 16];
        if getrandom::fill(&mut random).is_err() { return false; }
        random[6] = (random[6] & 0x0f) | 0x40;
        random[8] = (random[8] & 0x3f) | 0x80;
        let hex = hex::encode(random);
        let launch_id = format!("{}-{}-{}-{}-{}", &hex[..8], &hex[8..12], &hex[12..16], &hex[16..20], &hex[20..]);
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
            if let Ok(mut id) = MANAGED_SERVER_LAUNCH_ID.lock() { *id = Some(launch_id); }
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

fn server_is_running() -> bool {
    if let Ok(Some(endpoint)) = minelights_protocol::local_endpoint() {
        return (endpoint.host.as_str(), endpoint.port)
            .to_socket_addrs()
            .ok()
            .into_iter()
            .flatten()
            .any(|address| {
                TcpStream::connect_timeout(&address, Duration::from_millis(150)).is_ok()
            });
    }
    [63211, 63213].into_iter().any(|port| {
        let address = ("127.0.0.1", port)
            .to_socket_addrs()
            .ok()
            .and_then(|mut addresses| addresses.next());
        address.is_some_and(|address| {
            TcpStream::connect_timeout(&address, Duration::from_millis(150)).is_ok()
        })
    })
}

pub(super) fn minelights_shutdown() {
    if let Ok(_lifecycle) = SERVER_LIFECYCLE.lock() {
        SHUTTING_DOWN.store(true, Ordering::Release);
    } else {
        SHUTTING_DOWN.store(true, Ordering::Release);
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
    legacy: &[DeviceLayout],
    v2: &HashMap<String, Vec<DeviceLayout>>,
    direct: &[DeviceLayout],
) {
    let mut all = Vec::new();
    all.extend_from_slice(legacy);
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

fn perform_handshake(settings: &Settings) -> Result<Vec<DeviceLayout>, String> {
    let mut stream = TcpStream::connect_timeout(
        &"127.0.0.1:63211"
            .parse()
            .map_err(|error| format!("bad address: {error}"))?,
        Duration::from_secs(2),
    )
    .map_err(|error| error.to_string())?;
    let _ = stream.set_read_timeout(Some(Duration::from_secs(3)));
    let integrations = [
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
    .into_iter()
    .filter_map(|(name, enabled)| enabled.then_some(name))
    .collect::<Vec<_>>();
    let request = serde_json::json!({
        "enabled_integrations": integrations,
        "disabled_devices": []
    });
    let bytes = serde_json::to_vec(&request).map_err(|error| error.to_string())?;
    stream
        .write_all(&(bytes.len() as u32).to_be_bytes())
        .map_err(|error| error.to_string())?;
    stream
        .write_all(&bytes)
        .map_err(|error| error.to_string())?;
    stream.flush().map_err(|error| error.to_string())?;

    let mut length = [0u8; 4];
    stream
        .read_exact(&mut length)
        .map_err(|error| error.to_string())?;
    let length = u32::from_be_bytes(length) as usize;
    if length == 0 || length > 16 * 1024 * 1024 {
        return Err(format!(
            "invalid MineLights Server response length: {length}"
        ));
    }
    let mut response = vec![0; length];
    stream
        .read_exact(&mut response)
        .map_err(|error| error.to_string())?;
    let value: serde_json::Value =
        serde_json::from_slice(&response).map_err(|error| error.to_string())?;
    let mut result = Vec::new();
    if let Some(devices) = value.get("devices").and_then(serde_json::Value::as_array) {
        for device in devices {
            let Some(map) = device.get("key_map").and_then(serde_json::Value::as_object) else {
                continue;
            };
            let mut layout = DeviceLayout::default();
            layout.display_name = device
                .get("name")
                .and_then(serde_json::Value::as_str)
                .unwrap_or("Unknown device")
                .to_owned();
            layout.integration = device
                .get("sdk")
                .and_then(serde_json::Value::as_str)
                .unwrap_or("Unknown")
                .to_owned();
            layout.unique_id = format!("{}|{}", layout.integration, layout.display_name);
            for (key, led) in map {
                let Some(id) = led.as_u64().and_then(|id| u32::try_from(id).ok()) else {
                    continue;
                };
                let key = standardize_key(key);
                layout.keys.entry(key).or_default().push(id);
                if !layout.leds.contains(&id) {
                    layout.leds.push(id);
                }
            }
            if !layout.leds.is_empty() {
                result.push(layout);
            }
        }
    }
    Ok(result)
}

pub(super) fn standardize_key(key: &str) -> String {
    let mut name = key
        .trim()
        .to_ascii_uppercase()
        .replace("CONTROL", "CTRL")
        .replace("WINDOWS", "WIN");
    if let Some(stripped) = name.strip_prefix("KEY: ") {
        name = stripped.to_owned();
    }
    if name.contains("PROGRAMMABLE") {
        return format!("G{}", trailing_number(&name));
    }
    if name.contains("DRAM") || name.contains("CORSAIR PRO LED") || name.contains("RAM") {
        let number = trailing_number(&name);
        if name.contains("CORSAIR PRO LED") {
            if let Ok(index) = number.parse::<u32>() {
                return format!("RAM_{}", index.saturating_add(1));
            }
        }
        return if number.is_empty() {
            "RAM".into()
        } else {
            format!("RAM_{number}")
        };
    }
    if name.contains("LEDSTRIPE") || name.contains("UNDERGLOW") {
        let number = trailing_number(&name);
        return if number.is_empty() {
            "UNDERGLOW".into()
        } else {
            format!("UNDERGLOW_{number}")
        };
    }
    if name.contains("CUSTOM") || name.contains("WHEEL") {
        let number = trailing_number(&name);
        return if number.is_empty() {
            "WHEEL".into()
        } else {
            format!("WHEEL_{number}")
        };
    }
    if name.contains("PLAYER") {
        let number = trailing_number(&name);
        return if number.is_empty() {
            "PLAYER".into()
        } else {
            format!("PLAYER_{number}")
        };
    }
    name.split_whitespace()
        .collect::<Vec<_>>()
        .join("_")
        .chars()
        .filter(|c| c.is_ascii_alphanumeric() || *c == '_')
        .collect()
}
