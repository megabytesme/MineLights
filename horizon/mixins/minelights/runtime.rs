// MineLights' Horizon-native runtime.
//
// The renderer is deliberately independent of Horizon's game structs. The small
// adapter at the end of this file turns the pinned Horizon client state into a
// snapshot; the worker owns all blocking loopback I/O.

use std::{
    collections::HashMap,
    sync::{
        Mutex, OnceLock,
        atomic::{AtomicBool, Ordering},
        mpsc::TrySendError,
    },
    time::{Duration, Instant},
};

const MOD_ID: &str = "minelights";

pub(super) use super::minelights_bridge::minelights_shutdown;
use super::minelights_bridge::{
    ACTION_CHECK_UPDATE, ACTION_REFRESH, ACTION_RESTART, ACTION_RESTART_ADMIN, BRIDGE, Bridge,
    Notice, bridge_for,
};
use super::minelights_config::{
    SETTINGS, STATIC_SETTING_COUNT, device_disabled, set_device_enabled, on_off, persist_settings, settings_for, settings_labels,
    toggle_setting,
};
use super::minelights_effects::Painter;
use super::minelights_player_data::snapshot;

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub(super) struct Color(pub(super) u8, pub(super) u8, pub(super) u8);

impl Color {
    pub(super) const BLACK: Self = Self(0, 0, 0);
    pub(super) const WHITE: Self = Self(255, 255, 255);

    pub(super) fn scaled(self, factor: f32) -> Self {
        Self(
            (self.0 as f32 * factor).clamp(0.0, 255.0) as u8,
            (self.1 as f32 * factor).clamp(0.0, 255.0) as u8,
            (self.2 as f32 * factor).clamp(0.0, 255.0) as u8,
        )
    }

    pub(super) fn blend(self, over: Self, opacity: f32) -> Self {
        let alpha = opacity.clamp(0.0, 1.0);
        let channel = |a: u8, b: u8| (a as f32 * (1.0 - alpha) + b as f32 * alpha) as u8;
        Self(
            channel(self.0, over.0),
            channel(self.1, over.1),
            channel(self.2, over.2),
        )
    }

    pub(super) fn lerp(self, other: Self, t: f32) -> Self {
        self.blend(other, t)
    }
}

pub(super) type DeviceLayout = minelights_engine::DeviceLayout;

#[derive(Clone, Debug, Default, PartialEq)]
pub(super) struct Snapshot {
    pub(super) in_game: bool,
    pub(super) dimension: String,
    pub(super) biome: String,
    pub(super) feet: String,
    pub(super) below: String,
    pub(super) head: String,
    pub(super) health: f32,
    pub(super) hunger: i32,
    pub(super) saturation: f32,
    pub(super) air: i32,
    pub(super) experience: f32,
    pub(super) rain: f32,
    pub(super) biome_precipitation: bool,
    pub(super) biome_snowing: bool,
    pub(super) sky_light: f32,
    pub(super) local_light: f32,
    pub(super) end_flash: f32,
    pub(super) compass_type: u8,
    pub(super) compass_pointing: bool,
    pub(super) compass_yaw: f32,
    pub(super) compass_distance: f32,
    pub(super) waypoints: Vec<Waypoint>,
    pub(super) chat_received: u64,
    pub(super) chat_key: String,
    pub(super) on_fire: bool,
    pub(super) poisoned: bool,
    pub(super) withering: bool,
    pub(super) taking_damage: bool,
    pub(super) in_water: bool,
    pub(super) lightning: bool,
    pub(super) movement_keys: Vec<String>,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(super) struct Waypoint {
    pub(super) yaw_degrees: i16,
    pub(super) color: Color,
    pub(super) pitch: i8,
}

#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(super) struct Frame {
    pub(super) keys: HashMap<u32, Color>,
}

pub(super) fn trailing_number(value: &str) -> String {
    value
        .chars()
        .rev()
        .take_while(|c| c.is_ascii_digit())
        .collect::<String>()
        .chars()
        .rev()
        .collect()
}

static PAINTER: OnceLock<Mutex<Painter>> = OnceLock::new();
pub(super) static PENDING_SERVER_CONFIRMATION_REMINDER_SENT: AtomicBool = AtomicBool::new(false);

/// Called from Horizon's native frame hook. Game state stays on the render
/// thread; only immutable LED frames cross to the background network worker.
pub(super) fn minelights_frame(game: &mut super::Game<'_>) {
    if !game.native_on(MOD_ID) {
        if let Some(bridge) = BRIDGE.get() {
            bridge.active.store(false, Ordering::Release);
            bridge.auto_start.store(false, Ordering::Relaxed);
            bridge.in_world.store(false, Ordering::Release);
            if let Ok(mut notices) = bridge.notices.lock() {
                while let Some(notice) = notices.pop_front() {
                    if let Notice::Manual(lines) = notice {
                        for line in lines { game.native_chat(&line); }
                    }
                }
            }
            let layouts = bridge.layouts.read().map(|v| v.clone()).unwrap_or_default();
            let mut frame = Frame::default();
            for led in layouts.iter().flat_map(|layout| layout.leds.iter()) {
                frame.keys.insert(*led, Color::BLACK);
            }
            let now = Instant::now();
            let mut painter = PAINTER
                .get_or_init(|| Mutex::new(Painter::new()))
                .lock()
                .unwrap_or_else(|poisoned| poisoned.into_inner());
            if painter.last_frame.as_ref() != Some(&frame) {
                send_frame_if_needed(bridge, &mut painter, frame, now);
            }
        }
        return;
    }
    let config = settings_for(game);
    let bridge = bridge_for(game, &config);
    let player = snapshot(game, &config);
    bridge.in_world.store(player.in_game, Ordering::Release);
    if config.server_confirmation_pending {
        if player.in_game && !PENDING_SERVER_CONFIRMATION_REMINDER_SENT.swap(true, Ordering::AcqRel)
        {
            game.native_chat("[MineLights] Reviewed server autostart is still off because confirmation is incomplete. Use /minelights confirm server-autostart to enable it, or /minelights set server-autostart off to cancel.");
        }
    } else {
        PENDING_SERVER_CONFIRMATION_REMINDER_SENT.store(false, Ordering::Release);
    }
    if player.in_game {
        if let Ok(mut notices) = bridge.notices.lock() {
            while let Some(notice) = notices.pop_front() {
                match notice {
                    Notice::MissingServer if config.notify_missing_server => {
                        game.native_chat("[MineLights] Server not detected. Download MineLights.exe from https://github.com/megabytesme/MineLights-Server/releases and run it yourself.");
                        game.native_chat("[MineLights] Alternatively, opt in to the reviewed Server download and launch in Mods settings.");
                    }
                    Notice::Update(lines) if config.check_server_updates => {
                        for line in lines {
                            game.native_chat(&line);
                        }
                    }
                    Notice::Manual(lines) => {
                        for line in lines {
                            game.native_chat(&line);
                        }
                    }
                    _ => {}
                }
            }
        }
    }
    let layouts = bridge.layouts.read().map(|v| v.clone()).unwrap_or_default();
    let now = Instant::now();
    let mut painter = PAINTER
        .get_or_init(|| Mutex::new(Painter::new()))
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner());
    if now.duration_since(painter.last_paint) < Duration::from_millis(33) {
        return;
    }
    painter.last_paint = now;
    let mut frame = painter.render(player, &layouts, config.clone(), now);
    for layout in layouts
        .iter()
        .filter(|layout| device_disabled(&config, &layout.unique_id, layout.legacy_id.as_deref()))
    {
        for led in &layout.leds {
            frame.keys.insert(*led, Color::BLACK);
        }
    }
    send_frame_if_needed(bridge, &mut painter, frame, now);
}

fn send_frame_if_needed(bridge: &Bridge, painter: &mut Painter, frame: Frame, now: Instant) {
    if painter.last_frame.as_ref() != Some(&frame)
        || now.duration_since(painter.last_frame_sent) >= Duration::from_secs(1)
    {
        match bridge.sender.try_send(frame.clone()) {
            Ok(()) => {
                painter.last_frame = Some(frame);
                painter.last_frame_sent = now;
            }
            Err(TrySendError::Full(_)) | Err(TrySendError::Disconnected(_)) => {}
        }
    }
}

pub(super) fn minelights_settings(
    game: &mut super::Game<'_>,
    mod_id: &str,
    buttons: &mut Vec<String>,
) {
    if mod_id != MOD_ID {
        return;
    }
    let settings = settings_for(game);
    buttons.extend(settings_labels(settings.clone(), &game.settings.language));
    if let Some(bridge) = BRIDGE.get() {
        if let Ok(layouts) = bridge.layouts.read() {
            buttons.extend(layouts.iter().map(|layout| {
                let enabled = !device_disabled(&settings, &layout.unique_id, layout.legacy_id.as_deref());
                format!(
                    "Device {} ({}): {}",
                    layout.display_name,
                    layout.integration,
                    on_off(enabled)
                )
            }));
        }
    }
}

pub(super) fn minelights_settings_click(game: &mut super::Game<'_>, mod_id: &str, button: usize) {
    if mod_id != MOD_ID {
        return;
    }
    let Some(state) = SETTINGS.get() else {
        return;
    };
    let mut settings = state
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner());
    if button != 20 {
        settings.server_confirmation_pending = false;
    }
    if button == 37 {
        settings.disabled_devices.clear();
        settings.enabled_legacy_devices.clear();
        if let Some(bridge) = BRIDGE.get() {
            bridge.actions.fetch_or(ACTION_REFRESH, Ordering::Release);
        }
    } else if (33..=36).contains(&button) {
        if let Some(bridge) = BRIDGE.get() {
            let action = [
                ACTION_REFRESH,
                ACTION_RESTART,
                ACTION_RESTART_ADMIN,
                ACTION_CHECK_UPDATE,
            ][button - 33];
            bridge.actions.fetch_or(action, Ordering::Release);
        }
    } else if button < STATIC_SETTING_COUNT {
        toggle_setting(&mut settings, button);
        if button == 20 && settings.server_confirmation_pending {
            game.native_chat(&format!("[MineLights] Reviewed server autostart is still off. Confirm with /minelights confirm server-autostart to download MineLights Server {} from GitHub, verify its pinned SHA-256, and run it with your account permissions; or cancel with /minelights set server-autostart off.", super::minelights_server::SERVER_VERSION));
            if BRIDGE
                .get()
                .is_some_and(|bridge| bridge.in_world.load(Ordering::Acquire))
            {
                PENDING_SERVER_CONFIRMATION_REMINDER_SENT.store(true, Ordering::Release);
            }
        }
    } else if let Some(bridge) = BRIDGE.get() {
        if let Ok(layouts) = bridge.layouts.read() {
            if let Some(layout) = layouts.get(button - STATIC_SETTING_COUNT) {
                let disabled = device_disabled(&settings, &layout.unique_id, layout.legacy_id.as_deref());
                set_device_enabled(&mut settings, &layout.unique_id, layout.legacy_id.as_deref(), disabled);
            }
        }
    }
    persist_settings(game, settings.clone());
}

#[cfg(test)]
mod tests {
    use super::super::minelights_bridge::standardize_key;
    use super::super::minelights_effects::biome_color;
    use super::Color;

    #[test]
    fn standardizes_device_key_names_like_the_java_client() {
        assert_eq!(standardize_key("Left Control"), "LEFT_CTRL");
        assert_eq!(standardize_key("RAM 3"), "RAM_3");
        assert_eq!(standardize_key("Programmable Key 12"), "G12");
    }

    #[test]
    fn maps_biome_colors_and_blends_channels() {
        assert_eq!(biome_color("minecraft:plains"), Color(130, 159, 66));
        assert_eq!(
            Color(0, 0, 0).blend(Color(255, 128, 0), 0.5),
            Color(127, 64, 0)
        );
    }
}
