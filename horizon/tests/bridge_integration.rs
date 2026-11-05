#![allow(dead_code)]
use std::marker::PhantomData;

struct Game<'a> {
    settings: GameSettings,
    _lifetime: PhantomData<&'a ()>,
}
struct GameSettings {
    language: String,
}
impl Game<'_> {
    fn native_config_dir() -> std::io::Result<std::path::PathBuf> {
        Ok(std::env::var_os("MINELIGHTS_TEST_CONFIG_DIR")
            .map(std::path::PathBuf::from)
            .unwrap_or_else(std::env::temp_dir))
    }
    fn native_setting(&self, _: &str, _: &str) -> Option<String> {
        None
    }
    fn native_set_setting(&self, _: &str, _: &str, _: &str) {}
    fn native_on(&self, _: &str) -> bool {
        true
    }
    fn native_chat(&mut self, _: &str) {}
}
mod minelights_lang {
    pub fn setting_label(_: &str, _: usize) -> Option<&'static str> {
        None
    }
}
mod minelights_player_data {
    pub fn snapshot(
        _: &super::Game<'_>,
        _: &super::minelights_config::Settings,
    ) -> super::minelights::Snapshot {
        super::minelights::Snapshot::default()
    }
}
mod minelights_effects {
    use super::minelights::{Color, DeviceLayout, Frame, Snapshot};
    use super::minelights_config::Settings;
    use std::time::Instant;
    pub struct Painter {
        pub last_frame: Option<Frame>,
        pub last_frame_sent: Instant,
        pub last_paint: Instant,
    }
    impl Painter {
        pub fn new() -> Self {
            Self {
                last_frame: None,
                last_frame_sent: Instant::now(),
                last_paint: Instant::now(),
            }
        }
        pub fn render(
            &mut self,
            _: Snapshot,
            _: &[DeviceLayout],
            _: Settings,
            _: Instant,
        ) -> Frame {
            Frame::default()
        }
    }
    pub fn biome_color(_: &str) -> Color {
        Color(130, 159, 66)
    }
}
#[path = "../mixins/minelights/runtime.rs"]
mod minelights;
#[path = "../mixins/minelights/bridge.rs"]
mod minelights_bridge;
#[path = "../mixins/minelights/config.rs"]
mod minelights_config;
#[path = "../mixins/minelights/commands.rs"]
mod minelights_commands;
#[path = "../mixins/minelights/direct.rs"]
mod minelights_direct;
#[path = "../mixins/minelights/server.rs"]
mod minelights_server;
#[path = "../mixins/minelights/protocol.rs"]
mod minelights_protocol;

#[test]
fn bridge_uses_a_disabled_server_by_default() {
    assert!(!minelights_config::Settings::default().allow_reviewed_server);
    let _shutdown_hook: fn() = minelights::minelights_shutdown;
}

#[test]
fn advertised_endpoint_requires_explicit_legacy_fallback() {
    assert!(!minelights_config::Settings::default().allow_legacy_v1_fallback);
    assert!(!minelights_bridge::allow_v1_fallback(false, true, false));
    assert!(!minelights_bridge::allow_v1_fallback(false, false, true));
    assert!(minelights_bridge::allow_v1_fallback(false, false, false));
    assert!(minelights_bridge::allow_v1_fallback(true, true, true));
}

// This isolated live test binds the actual legacy loopback port. Run explicitly
// with LOCALAPPDATA and MINELIGHTS_TEST_CONFIG_DIR set to disposable directories.
#[test]
#[ignore = "binds the real V1 port; run separately with isolated environment"]
fn live_legacy_fallback_requires_explicit_opt_in() {
    use std::io::{Read, Write};
    use std::net::TcpListener;
    use std::sync::{Arc, atomic::{AtomicBool, AtomicUsize, Ordering}};
    use std::time::{Duration, Instant};

    let data_dir = std::path::PathBuf::from(std::env::var_os("LOCALAPPDATA").expect("isolated LOCALAPPDATA required"));
    let config_dir = std::path::PathBuf::from(std::env::var_os("MINELIGHTS_TEST_CONFIG_DIR").expect("isolated config required"));
    let temp = std::env::temp_dir();
    assert!(data_dir.starts_with(&temp) && config_dir.starts_with(&temp), "test paths must be under system temp");
    let endpoint_dir = data_dir.join("MineLights").join("v2");
    std::fs::create_dir_all(&endpoint_dir).unwrap();
    let unrelated = TcpListener::bind("127.0.0.1:0").unwrap();
    unrelated.set_nonblocking(true).unwrap();
    let unrelated_port = unrelated.local_addr().unwrap().port();
    let v1 = TcpListener::bind("127.0.0.1:63211").expect("V1 port must be free for isolated test");
    v1.set_nonblocking(true).unwrap();
    let document = serde_json::json!({
        "schema_version":1, "server_id":"11111111-1111-4111-8111-111111111111",
        "server_name":"stale test", "host":"127.0.0.1", "port":unrelated_port,
        "tls_certificate_sha256":"00".repeat(32), "protocol_major":2, "protocol_minor":0
    });
    std::fs::write(endpoint_dir.join("endpoint.json"), document.to_string()).unwrap();
    let stop = Arc::new(AtomicBool::new(false));
    let v1_connections = Arc::new(AtomicUsize::new(0));
    let unrelated_connections = Arc::new(AtomicUsize::new(0));
    let mock_stop = Arc::clone(&stop);
    let mock_v1 = Arc::clone(&v1_connections);
    let mock_unrelated = Arc::clone(&unrelated_connections);
    let mock = std::thread::spawn(move || {
        while !mock_stop.load(Ordering::Acquire) {
            if let Ok((stream, _)) = unrelated.accept() {
                mock_unrelated.fetch_add(1, Ordering::Relaxed);
                drop(stream);
            }
            if let Ok((mut stream, _)) = v1.accept() {
                mock_v1.fetch_add(1, Ordering::Relaxed);
                let _ = stream.set_read_timeout(Some(Duration::from_secs(1)));
                let mut len = [0u8; 4];
                if stream.read_exact(&mut len).is_ok() {
                    let request_len = u32::from_be_bytes(len) as usize;
                    if request_len > 16 * 1024 { continue; }
                    let mut request = vec![0; request_len];
                    let _ = stream.read_exact(&mut request);
                    let body = br#"{"devices":[{"name":"Mock device","sdk":"Mock","key_map":{"A":1}}]}"#;
                    let _ = stream.write_all(&(body.len() as u32).to_be_bytes());
                    let _ = stream.write_all(body);
                }
            }
            std::thread::sleep(Duration::from_millis(5));
        }
    });
    let mut settings = minelights_config::Settings::default();
    settings.enable_openrgb = false;
    settings.enable_yeelight = false;
    settings.check_server_updates = false;
    settings.notify_missing_server = false;
    let game = Game { settings: GameSettings { language: "en_us".into() }, _lifetime: PhantomData };
    let bridge = minelights_bridge::bridge_for(&game, &settings);
    let deadline = Instant::now() + Duration::from_secs(8);
    while minelights_bridge::TEST_COMPLETED_HANDSHAKES.load(Ordering::Acquire) == 0 && Instant::now() < deadline {
        std::thread::sleep(Duration::from_millis(20));
    }
    assert!(minelights_bridge::TEST_COMPLETED_HANDSHAKES.load(Ordering::Acquire) > 0, "worker did not complete its first handshake cycle");
    assert!(unrelated_connections.load(Ordering::Relaxed) > 0, "worker did not probe unrelated listener");
    assert_eq!(v1_connections.load(Ordering::Relaxed), 0, "default unexpectedly contacted V1");
    settings.allow_legacy_v1_fallback = true;
    let _ = minelights_bridge::bridge_for(&game, &settings);
    bridge.actions.fetch_or(minelights_bridge::ACTION_REFRESH, Ordering::Release);
    let deadline = Instant::now() + Duration::from_secs(8);
    while bridge.layouts.read().unwrap().is_empty() && Instant::now() < deadline {
        std::thread::sleep(Duration::from_millis(20));
    }
    assert!(v1_connections.load(Ordering::Relaxed) > 0, "opt-in did not contact V1");
    assert_eq!(bridge.layouts.read().unwrap()[0].display_name, "Mock device");
    minelights_bridge::minelights_shutdown();
    stop.store(true, Ordering::Release);
    mock.join().unwrap();
}
