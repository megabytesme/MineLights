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
        Ok(std::env::temp_dir())
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
#[path = "../mixins/minelights/direct.rs"]
mod minelights_direct;
#[path = "../mixins/minelights/server.rs"]
mod minelights_server;

#[test]
fn bridge_uses_a_disabled_server_by_default() {
    assert!(!minelights_config::Settings::default().allow_reviewed_server);
    let _shutdown_hook: fn() = minelights::minelights_shutdown;
}
