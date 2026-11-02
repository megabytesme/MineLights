use std::{
    collections::{HashSet, VecDeque},
    io::{Read, Write},
    net::{TcpStream, UdpSocket},
    path::PathBuf,
    sync::{
        Arc, Mutex, OnceLock, RwLock,
        atomic::{AtomicBool, AtomicU32, Ordering},
        mpsc::{self, SyncSender},
    },
    thread,
    time::{Duration, Instant},
};

use super::minelights::{DeviceLayout, Frame, trailing_number};
use super::minelights_config::Settings;
use super::minelights_direct::DirectOutputs;
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
    pub(super) sender: SyncSender<Frame>,
}

pub(super) static BRIDGE: OnceLock<Bridge> = OnceLock::new();
static STARTED_SERVER: AtomicBool = AtomicBool::new(false);
static SHUTTING_DOWN: AtomicBool = AtomicBool::new(false);
static MANAGED_SERVER_BUSY: AtomicBool = AtomicBool::new(false);
static SERVER_LIFECYCLE: Mutex<()> = Mutex::new(());

pub(super) fn bridge_for(game: &super::Game<'_>, settings: &Settings) -> &'static Bridge {
    let server_path = super::Game::native_config_dir().ok().and_then(|config| {
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
                    server_path,
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
            sender,
        }
    });
    bridge
        .auto_start
        .store(settings.allow_reviewed_server, Ordering::Relaxed);
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
    server_path: Option<PathBuf>,
) {
    let udp = UdpSocket::bind("127.0.0.1:0").ok();
    let mut direct = DirectOutputs::default();
    let mut next_handshake = Instant::now();
    let mut next_yeelight_discovery = Instant::now();
    let mut last_launch_attempt = Instant::now() - Duration::from_secs(600);
    let mut was_in_world = false;
    let mut missing_reported = false;
    let mut update_checked = false;
    let mut server_led_ids = HashSet::new();
    loop {
        if active.load(Ordering::Acquire) {
            let requested = actions.swap(0, Ordering::AcqRel);
            if requested & ACTION_REFRESH != 0 {
                direct.disconnect_openrgb();
                direct.disconnect_yeelights();
                next_yeelight_discovery = Instant::now();
                next_handshake = Instant::now();
            }
            if requested & ACTION_RESTART != 0 {
                let _ = send_server_command("restart");
            }
            if requested & ACTION_RESTART_ADMIN != 0 {
                let _ = send_server_command("restart_admin");
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
                let mut devices = perform_handshake(&config).unwrap_or_default();
                if config.enable_openrgb {
                    if !direct.has_openrgb() {
                        let _ = direct.connect_openrgb();
                    }
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
                server_led_ids = devices
                    .iter()
                    .flat_map(|device| device.leds.iter().copied())
                    .collect();
                let direct_layouts = direct.layouts();
                for device in direct_layouts {
                    devices.push(DeviceLayout {
                        keys: device.keys,
                        leds: device.leds,
                        unique_id: device.unique_id,
                        display_name: device.display_name,
                        integration: device.integration,
                    });
                }
                if let Ok(mut current) = layouts.write() {
                    *current = devices;
                }
                next_handshake = Instant::now() + Duration::from_secs(5);
            }
        }

        match receiver.recv_timeout(Duration::from_millis(25)) {
            Ok(frame) => {
                let mut led_colors: Vec<LedColor> = frame
                    .keys
                    .iter()
                    .filter_map(|(id, color)| {
                        server_led_ids.contains(id).then_some(LedColor {
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
        let mut command = std::process::Command::new(path);
        command
            .current_dir(path.parent().unwrap_or_else(|| std::path::Path::new(".")))
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::null())
            .creation_flags(0x0800_0000);
        let started = command.spawn().is_ok();
        if started {
            STARTED_SERVER.store(true, Ordering::Release);
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
    "127.0.0.1:63213".parse().ok().is_some_and(|address| {
        TcpStream::connect_timeout(&address, Duration::from_millis(150)).is_ok()
    })
}

fn send_server_command(command: &str) -> bool {
    let Ok(address) = "127.0.0.1:63213".parse() else {
        return false;
    };
    TcpStream::connect_timeout(&address, Duration::from_secs(1))
        .and_then(|mut stream| stream.write_all(command.as_bytes()))
        .is_ok()
}

pub(super) fn minelights_shutdown() {
    SHUTTING_DOWN.store(true, Ordering::Release);
    let Ok(_lifecycle) = SERVER_LIFECYCLE.lock() else {
        return;
    };
    if STARTED_SERVER.swap(false, Ordering::AcqRel) {
        let _ = send_server_command("shutdown");
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
