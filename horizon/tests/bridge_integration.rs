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
    pub fn setting_label_or_english(_: &str, _: usize) -> Option<&'static str> {
        None
    }
    pub fn server_message(_: &str, _: usize) -> Option<&'static str> {
        None
    }
    pub fn server_message_or_english(_: &str, _: usize) -> Option<&'static str> {
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
#[path = "../mixins/minelights/runtime.rs"]
mod minelights;
#[path = "../mixins/minelights/bridge.rs"]
mod minelights_bridge;
#[path = "../mixins/minelights/commands.rs"]
mod minelights_commands;
#[path = "../mixins/minelights/config.rs"]
mod minelights_config;
#[path = "../mixins/minelights/direct.rs"]
mod minelights_direct;
#[path = "../mixins/minelights/effects.rs"]
mod minelights_effects;
#[path = "../mixins/minelights/protocol.rs"]
mod minelights_protocol;
#[path = "../mixins/minelights/server.rs"]
mod minelights_server;

#[test]
fn bridge_uses_a_disabled_server_by_default() {
    assert!(!minelights_config::Settings::default().allow_reviewed_server);
    let _shutdown_hook: fn() = minelights::minelights_shutdown;
}

#[test]
fn configurable_bars_use_the_selected_keys_and_absorption_color() {
    use std::collections::HashMap;
    use std::time::Instant;
    let mut settings = minelights_config::Settings::default();
    settings.biome_lighting = false;
    settings.dimming_mode = 2;
    settings.compass_effect = false;
    settings.low_health_warning = false;
    settings.health_bar_keys = vec!["Q".into()];
    settings.absorption_color = 0x123456;
    settings.experience_bar = false;
    settings.hunger_bar = false;
    settings.saturation_air_bar = false;
    let player = minelights::Snapshot {
        in_game: true,
        health: 20.0,
        absorption: 20.0,
        ..Default::default()
    };
    let layout = minelights::DeviceLayout {
        keys: HashMap::from([("Q".into(), vec![7])]),
        leds: vec![7],
        ..Default::default()
    };
    let frame =
        minelights_effects::Painter::new().render(player, &[layout], settings, Instant::now());
    assert_eq!(
        frame.keys.get(&7),
        Some(&minelights::Color(0x12, 0x34, 0x56))
    );
}

// Binds legacy ports and therefore runs only under an isolated explicit test run.
#[test]
#[ignore = "binds legacy ports; requires an isolated test environment"]
fn new_client_never_contacts_legacy_server() {
    use std::net::{TcpListener, UdpSocket};
    use std::sync::atomic::Ordering;
    use std::time::{Duration, Instant};

    let data =
        std::path::PathBuf::from(std::env::var_os("LOCALAPPDATA").expect("isolated LOCALAPPDATA"));
    let config = std::path::PathBuf::from(
        std::env::var_os("MINELIGHTS_TEST_CONFIG_DIR").expect("isolated config"),
    );
    assert!(data.starts_with(std::env::temp_dir()) && config.starts_with(std::env::temp_dir()));
    let tcp = TcpListener::bind("127.0.0.1:63211").expect("legacy handshake port must be free");
    let udp = UdpSocket::bind("127.0.0.1:63212").expect("legacy lighting port must be free");
    tcp.set_nonblocking(true).unwrap();
    udp.set_nonblocking(true).unwrap();
    // An old stored opt-in must not restore the removed implementation.
    let mut settings: minelights_config::Settings =
        serde_json::from_str(r#"{"allow_legacy_v1_fallback":true}"#).unwrap();
    settings.enable_openrgb = false;
    settings.enable_yeelight = false;
    settings.check_server_updates = false;
    settings.notify_missing_server = false;
    let game = Game {
        settings: GameSettings {
            language: "en_us".into(),
        },
        _lifetime: PhantomData,
    };
    let _bridge = minelights_bridge::bridge_for(&game, &settings);
    let deadline = Instant::now() + Duration::from_secs(10);
    while minelights_bridge::TEST_COMPLETED_HANDSHAKES.load(Ordering::Acquire) == 0
        && Instant::now() < deadline
    {
        std::thread::sleep(Duration::from_millis(20));
    }
    assert!(minelights_bridge::TEST_COMPLETED_HANDSHAKES.load(Ordering::Acquire) > 0);
    assert!(
        tcp.accept().is_err(),
        "new client contacted V1 TCP handshake"
    );
    assert!(
        udp.recv_from(&mut [0u8; 1024]).is_err(),
        "new client sent a V1 lighting frame"
    );
    minelights_bridge::minelights_shutdown();
}
