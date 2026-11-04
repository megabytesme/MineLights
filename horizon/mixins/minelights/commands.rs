use super::minelights_bridge::{
    ACTION_CHECK_UPDATE, ACTION_REFRESH, ACTION_RESTART, ACTION_RESTART_ADMIN, BRIDGE, bridge_for,
};
use super::minelights_config::{SETTINGS, Settings, on_off, persist_settings, settings_for};

const SETTING_NAMES: &[&str] = &[
    "enabled",
    "experience-bar",
    "health-bar",
    "hunger-bar",
    "saturation-air-bar",
    "compass-effect",
    "biome-lighting",
    "dimming-mode",
    "min-brightness",
    "weather-effects",
    "mirror-ram-rain-direction",
    "end-flash-effect",
    "fire-effect",
    "water-effect",
    "portal-effects",
    "low-health-warning",
    "movement-highlight",
    "chat-pulse",
    "always-show-compass",
    "locator-bar",
    "server-autostart",
    "compass-priority",
    "corsair",
    "asus",
    "logitech",
    "razer",
    "wooting",
    "steelseries",
    "msi",
    "novation",
    "picopi",
    "openrgb",
    "yeelight",
    "notify-missing-server",
    "check-server-updates",
];

pub(super) fn minelights_command(game: &mut super::Game<'_>, command: &str, ran: &mut bool) {
    let mut parts = command.split_whitespace();
    let Some(first) = parts.next() else {
        return;
    };
    if !first.eq_ignore_ascii_case("minelights") {
        return;
    }
    *ran = true;
    let Some(subcommand) = parts.next() else {
        show_help(game);
        return;
    };

    let subcommand = subcommand.to_ascii_lowercase();
    match subcommand.as_str() {
        "help" => show_help(game),
        "list" | "settings" => list_settings(game),
        "get" => {
            let Some(name) = parts.next() else {
                chat(game, "Usage: /minelights get <setting|all>");
                return;
            };
            get_setting(game, name);
        }
        "set" => {
            let (Some(name), Some(value)) = (parts.next(), parts.next()) else {
                chat(game, "Usage: /minelights set <setting> <value>");
                return;
            };
            set_command(game, name, value);
        }
        "toggle" => {
            let Some(name) = parts.next() else {
                chat(game, "Usage: /minelights toggle <setting>");
                return;
            };
            toggle_command(game, name);
        }
        "confirm" => {
            let Some(name) = parts.next() else {
                chat(game, "Usage: /minelights confirm server-autostart");
                return;
            };
            confirm_command(game, name);
        }
        "devices" => match parts.next().map(str::to_ascii_lowercase).as_deref() {
            Some("clear") => clear_disabled_devices(game),
            _ => list_devices(game),
        },
        "device" => {
            let (Some(index), Some(value)) = (parts.next(), parts.next()) else {
                chat(game, "Usage: /minelights device <number> <on|off>");
                return;
            };
            set_device(game, index, value);
        }
        "refresh" => request_action(game, ACTION_REFRESH, "Refreshing lighting devices."),
        "clear-disabled" | "clear-disabled-devices" => clear_disabled_devices(game),
        "server" => match parts.next().map(str::to_ascii_lowercase).as_deref() {
            Some("restart") => {
                request_action(game, ACTION_RESTART, "Restarting MineLights Server.")
            }
            Some("restart-admin") | Some("restart-as-admin") => request_action(
                game,
                ACTION_RESTART_ADMIN,
                "Restarting MineLights Server as administrator.",
            ),
            Some("check-update") | Some("check-updates") => request_action(
                game,
                ACTION_CHECK_UPDATE,
                "Checking for MineLights Server updates.",
            ),
            _ => chat(
                game,
                "Usage: /minelights server <restart|restart-admin|check-update>",
            ),
        },
        _ => {
            if matches!(
                subcommand.as_str(),
                "restart" | "restart-admin" | "restart-as-admin"
            ) {
                let (action, message) = if subcommand == "restart" {
                    (ACTION_RESTART, "Restarting MineLights Server.")
                } else {
                    (
                        ACTION_RESTART_ADMIN,
                        "Restarting MineLights Server as administrator.",
                    )
                };
                request_action(game, action, message);
            } else if matches!(subcommand.as_str(), "check-update" | "check-updates") {
                request_action(
                    game,
                    ACTION_CHECK_UPDATE,
                    "Checking for MineLights Server updates.",
                );
            } else {
                chat(game, "Unknown command. Use /minelights help.");
            }
        }
    }
}

fn show_help(game: &mut super::Game<'_>) {
    chat(
        game,
        "Commands: /minelights list | get <setting> | set <setting> <value> | toggle <setting>",
    );
    chat(
        game,
        "Also: confirm server-autostart, devices [clear], device <number> <on|off>, refresh, server <restart|restart-admin|check-update>.",
    );
    chat(
        game,
        "Boolean values: on/off. Dimming: local/sky/off. Brightness: 0-100. Compass priority: inventory/standard/lodestone/recovery.",
    );
}

fn list_settings(game: &mut super::Game<'_>) {
    let settings = settings_for(game);
    for group in SETTING_NAMES.chunks(5) {
        let values = group
            .iter()
            .map(|name| {
                format!(
                    "{name}={}",
                    setting_value(&settings, name).unwrap_or("?".into())
                )
            })
            .collect::<Vec<_>>()
            .join(", ");
        chat(game, &values);
    }
}

fn get_setting(game: &mut super::Game<'_>, name: &str) {
    let name = normalize_name(name);
    if name == "all" {
        list_settings(game);
        return;
    }
    let settings = settings_for(game);
    match setting_value(&settings, &name) {
        Some(value) => chat(game, &format!("{name} = {value}")),
        None => chat(game, "Unknown setting. Use /minelights list."),
    }
}

fn set_command(game: &mut super::Game<'_>, name: &str, value: &str) {
    let name = normalize_name(name);
    let Some(state) = SETTINGS.get() else {
        let _ = settings_for(game);
        set_command(game, &name, value);
        return;
    };
    let mut settings = state
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner());
    match set_setting(&mut settings, &name, value) {
        Ok(message) => {
            persist_settings(game, settings.clone());
            let snapshot = settings.clone();
            drop(settings);
            let _ = bridge_for(game, &snapshot);
            if name == "server-autostart" && snapshot.server_confirmation_pending {
                super::minelights::PENDING_SERVER_CONFIRMATION_REMINDER_SENT
                    .store(true, std::sync::atomic::Ordering::Release);
            }
            chat(game, message);
        }
        Err(message) => chat(game, message),
    }
}

fn toggle_command(game: &mut super::Game<'_>, name: &str) {
    let name = normalize_name(name);
    if name == "server-autostart" {
        let Some(state) = SETTINGS.get() else {
            let _ = settings_for(game);
            toggle_command(game, &name);
            return;
        };
        let mut settings = state
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner());
        let message = if settings.allow_reviewed_server {
            settings.allow_reviewed_server = false;
            settings.server_confirmation_pending = false;
            "Reviewed server autostart is off."
        } else if settings.server_confirmation_pending {
            settings.allow_reviewed_server = true;
            settings.server_confirmation_pending = false;
            "Reviewed server autostart confirmed and enabled."
        } else {
            settings.server_confirmation_pending = true;
            "Autostart is still off. Confirm with /minelights confirm server-autostart, or cancel with /minelights set server-autostart off."
        };
        persist_settings(game, settings.clone());
        let snapshot = settings.clone();
        drop(settings);
        let _ = bridge_for(game, &snapshot);
        if snapshot.server_confirmation_pending {
            super::minelights::PENDING_SERVER_CONFIRMATION_REMINDER_SENT
                .store(true, std::sync::atomic::Ordering::Release);
        }
        chat(game, message);
        return;
    }

    let settings = settings_for(game);
    if let Some(value) = boolean_setting(&settings, &name) {
        set_command(game, &name, if value { "off" } else { "on" });
    } else if setting_value(&settings, &name).is_some() {
        chat(
            game,
            "This setting uses a value. Use /minelights set <setting> <value>.",
        );
    } else {
        chat(game, "Unknown setting. Use /minelights list.");
    }
}

fn confirm_command(game: &mut super::Game<'_>, name: &str) {
    if normalize_name(name) != "server-autostart" {
        chat(game, "Only server-autostart requires confirmation.");
        return;
    }
    let Some(state) = SETTINGS.get() else {
        let _ = settings_for(game);
        confirm_command(game, name);
        return;
    };
    let mut settings = state
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner());
    if !settings.server_confirmation_pending {
        chat(
            game,
            "No server autostart confirmation is pending. Request it with /minelights set server-autostart on.",
        );
        return;
    }
    settings.server_confirmation_pending = false;
    settings.allow_reviewed_server = true;
    persist_settings(game, settings.clone());
    let snapshot = settings.clone();
    drop(settings);
    let _ = bridge_for(game, &snapshot);
    chat(
        game,
        &format!(
            "Reviewed MineLights Server {} autostart is confirmed. It will only launch on Windows.",
            super::minelights_server::SERVER_VERSION
        ),
    );
}

fn list_devices(game: &mut super::Game<'_>) {
    let settings = settings_for(game);
    let bridge = bridge_for(game, &settings);
    let layouts = bridge
        .layouts
        .read()
        .map(|items| items.clone())
        .unwrap_or_default();
    if layouts.is_empty() {
        chat(game, "No MineLights devices are currently detected.");
        return;
    }
    for (index, layout) in layouts.iter().enumerate() {
        let enabled = !settings.disabled_devices.contains(&layout.unique_id);
        chat(
            game,
            &format!(
                "Device {}: {} ({}) — {}. Use /minelights device {} <on|off>.",
                index + 1,
                layout.display_name,
                layout.integration,
                on_off(enabled),
                index + 1
            ),
        );
    }
}

fn set_device(game: &mut super::Game<'_>, index: &str, value: &str) {
    let Ok(index) = index.parse::<usize>() else {
        chat(
            game,
            "Device number must be a positive number. Use /minelights devices.",
        );
        return;
    };
    if index == 0 {
        chat(game, "Device numbers start at 1. Use /minelights devices.");
        return;
    }
    let Some(enabled) = parse_bool(value) else {
        chat(game, "Device state must be on or off.");
        return;
    };
    let current = settings_for(game);
    let bridge = bridge_for(game, &current);
    let layouts = bridge
        .layouts
        .read()
        .map(|items| items.clone())
        .unwrap_or_default();
    let Some(layout) = layouts.get(index - 1) else {
        chat(game, "No device has that number. Use /minelights devices.");
        return;
    };
    let unique_id = layout.unique_id.clone();
    let display_name = layout.display_name.clone();
    let Some(state) = SETTINGS.get() else {
        return;
    };
    let mut settings = state
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner());
    if enabled {
        settings.disabled_devices.retain(|id| id != &unique_id);
    } else if !settings.disabled_devices.contains(&unique_id) {
        settings.disabled_devices.push(unique_id);
    }
    persist_settings(game, settings.clone());
    drop(settings);
    bridge
        .actions
        .fetch_or(ACTION_REFRESH, std::sync::atomic::Ordering::Release);
    chat(
        game,
        &format!("Device {display_name} is now {}.", on_off(enabled)),
    );
}

fn clear_disabled_devices(game: &mut super::Game<'_>) {
    let _ = settings_for(game);
    let Some(state) = SETTINGS.get() else {
        return;
    };
    let mut settings = state
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner());
    let count = settings.disabled_devices.len();
    settings.disabled_devices.clear();
    persist_settings(game, settings.clone());
    drop(settings);
    if let Some(bridge) = BRIDGE.get() {
        bridge
            .actions
            .fetch_or(ACTION_REFRESH, std::sync::atomic::Ordering::Release);
    }
    chat(game, &format!("Re-enabled {count} disabled device(s)."));
}

fn request_action(game: &mut super::Game<'_>, action: u32, message: &str) {
    let settings = settings_for(game);
    let bridge = bridge_for(game, &settings);
    bridge
        .actions
        .fetch_or(action, std::sync::atomic::Ordering::Release);
    chat(game, message);
}

fn set_setting(
    settings: &mut Settings,
    name: &str,
    value: &str,
) -> Result<&'static str, &'static str> {
    if name == "server-autostart" {
        let Some(enabled) = parse_bool(value) else {
            return Err("server-autostart value must be on or off.");
        };
        if !enabled {
            settings.allow_reviewed_server = false;
            settings.server_confirmation_pending = false;
            return Ok("Reviewed server autostart is off.");
        }
        if settings.allow_reviewed_server {
            return Ok("Reviewed server autostart is already confirmed and enabled.");
        }
        settings.server_confirmation_pending = true;
        return Ok(
            "Autostart is still off. Confirm with /minelights confirm server-autostart, or cancel with /minelights set server-autostart off.",
        );
    }
    if name == "dimming-mode" {
        settings.dimming_mode = match value.to_ascii_lowercase().as_str() {
            "local" | "local-light" | "local_light" => 0,
            "sky" | "sky-light" | "sky_light" => 1,
            "off" | "none" => 2,
            _ => return Err("Dimming mode must be local, sky, or off."),
        };
        settings.server_confirmation_pending = false;
        return Ok("Dimming mode updated.");
    }
    if name == "compass-priority" {
        settings.compass_priority = match value.to_ascii_lowercase().as_str() {
            "inventory" | "priority" => 0,
            "standard" | "standard-first" | "standard_first" => 1,
            "lodestone" | "lodestone-first" | "lodestone_first" => 2,
            "recovery" | "recovery-first" | "recovery_first" => 3,
            _ => {
                return Err(
                    "Compass priority must be inventory, standard, lodestone, or recovery.",
                );
            }
        };
        settings.server_confirmation_pending = false;
        return Ok("Compass priority updated.");
    }
    if name == "min-brightness" {
        let Ok(percent) = value.parse::<u8>() else {
            return Err("Minimum brightness must be a number from 0 to 100.");
        };
        if percent > 100 {
            return Err("Minimum brightness must be a number from 0 to 100.");
        }
        settings.min_brightness = f32::from(percent) / 100.0;
        settings.server_confirmation_pending = false;
        return Ok("Minimum brightness updated.");
    }

    let Some(enabled) = parse_bool(value) else {
        return Err("This setting needs on or off. Use /minelights help for value options.");
    };
    let field = match name {
        "enabled" => &mut settings.enabled,
        "experience-bar" => &mut settings.experience_bar,
        "health-bar" => &mut settings.health_bar,
        "hunger-bar" => &mut settings.hunger_bar,
        "saturation-air-bar" => &mut settings.saturation_air_bar,
        "compass-effect" => &mut settings.compass_effect,
        "biome-lighting" => &mut settings.biome_lighting,
        "weather-effects" => &mut settings.weather_effects,
        "mirror-ram-rain-direction" => &mut settings.mirror_ram_rain_direction,
        "end-flash-effect" => &mut settings.end_flash_effect,
        "fire-effect" => &mut settings.fire_effect,
        "water-effect" => &mut settings.water_effect,
        "portal-effects" => &mut settings.portal_effects,
        "low-health-warning" => &mut settings.low_health_warning,
        "movement-highlight" => &mut settings.movement_highlight,
        "chat-pulse" => &mut settings.chat_pulse,
        "always-show-compass" => &mut settings.always_show_compass,
        "locator-bar" => &mut settings.locator_bar,
        "corsair" => &mut settings.enable_corsair,
        "asus" => &mut settings.enable_asus,
        "logitech" => &mut settings.enable_logitech,
        "razer" => &mut settings.enable_razer,
        "wooting" => &mut settings.enable_wooting,
        "steelseries" => &mut settings.enable_steelseries,
        "msi" => &mut settings.enable_msi,
        "novation" => &mut settings.enable_novation,
        "picopi" => &mut settings.enable_picopi,
        "openrgb" => &mut settings.enable_openrgb,
        "yeelight" => &mut settings.enable_yeelight,
        "notify-missing-server" => &mut settings.notify_missing_server,
        "check-server-updates" => &mut settings.check_server_updates,
        _ => return Err("Unknown setting. Use /minelights list."),
    };
    *field = enabled;
    settings.server_confirmation_pending = false;
    Ok("Setting updated.")
}

fn setting_value(settings: &Settings, name: &str) -> Option<String> {
    let value = match name {
        "enabled" => on_off(settings.enabled).into(),
        "experience-bar" => on_off(settings.experience_bar).into(),
        "health-bar" => on_off(settings.health_bar).into(),
        "hunger-bar" => on_off(settings.hunger_bar).into(),
        "saturation-air-bar" => on_off(settings.saturation_air_bar).into(),
        "compass-effect" => on_off(settings.compass_effect).into(),
        "biome-lighting" => on_off(settings.biome_lighting).into(),
        "dimming-mode" => ["local", "sky", "off"][usize::from(settings.dimming_mode.min(2))].into(),
        "min-brightness" => format!("{}%", (settings.min_brightness * 100.0).round() as u8),
        "weather-effects" => on_off(settings.weather_effects).into(),
        "mirror-ram-rain-direction" => on_off(settings.mirror_ram_rain_direction).into(),
        "end-flash-effect" => on_off(settings.end_flash_effect).into(),
        "fire-effect" => on_off(settings.fire_effect).into(),
        "water-effect" => on_off(settings.water_effect).into(),
        "portal-effects" => on_off(settings.portal_effects).into(),
        "low-health-warning" => on_off(settings.low_health_warning).into(),
        "movement-highlight" => on_off(settings.movement_highlight).into(),
        "chat-pulse" => on_off(settings.chat_pulse).into(),
        "always-show-compass" => on_off(settings.always_show_compass).into(),
        "locator-bar" => on_off(settings.locator_bar).into(),
        "server-autostart" if settings.server_confirmation_pending => "pending confirmation".into(),
        "server-autostart" => on_off(settings.allow_reviewed_server).into(),
        "compass-priority" => ["inventory", "standard", "lodestone", "recovery"]
            [usize::from(settings.compass_priority.min(3))]
        .into(),
        "corsair" => on_off(settings.enable_corsair).into(),
        "asus" => on_off(settings.enable_asus).into(),
        "logitech" => on_off(settings.enable_logitech).into(),
        "razer" => on_off(settings.enable_razer).into(),
        "wooting" => on_off(settings.enable_wooting).into(),
        "steelseries" => on_off(settings.enable_steelseries).into(),
        "msi" => on_off(settings.enable_msi).into(),
        "novation" => on_off(settings.enable_novation).into(),
        "picopi" => on_off(settings.enable_picopi).into(),
        "openrgb" => on_off(settings.enable_openrgb).into(),
        "yeelight" => on_off(settings.enable_yeelight).into(),
        "notify-missing-server" => on_off(settings.notify_missing_server).into(),
        "check-server-updates" => on_off(settings.check_server_updates).into(),
        _ => return None,
    };
    Some(value)
}

fn boolean_setting(settings: &Settings, name: &str) -> Option<bool> {
    Some(match name {
        "enabled" => settings.enabled,
        "experience-bar" => settings.experience_bar,
        "health-bar" => settings.health_bar,
        "hunger-bar" => settings.hunger_bar,
        "saturation-air-bar" => settings.saturation_air_bar,
        "compass-effect" => settings.compass_effect,
        "biome-lighting" => settings.biome_lighting,
        "weather-effects" => settings.weather_effects,
        "mirror-ram-rain-direction" => settings.mirror_ram_rain_direction,
        "end-flash-effect" => settings.end_flash_effect,
        "fire-effect" => settings.fire_effect,
        "water-effect" => settings.water_effect,
        "portal-effects" => settings.portal_effects,
        "low-health-warning" => settings.low_health_warning,
        "movement-highlight" => settings.movement_highlight,
        "chat-pulse" => settings.chat_pulse,
        "always-show-compass" => settings.always_show_compass,
        "locator-bar" => settings.locator_bar,
        "corsair" => settings.enable_corsair,
        "asus" => settings.enable_asus,
        "logitech" => settings.enable_logitech,
        "razer" => settings.enable_razer,
        "wooting" => settings.enable_wooting,
        "steelseries" => settings.enable_steelseries,
        "msi" => settings.enable_msi,
        "novation" => settings.enable_novation,
        "picopi" => settings.enable_picopi,
        "openrgb" => settings.enable_openrgb,
        "yeelight" => settings.enable_yeelight,
        "notify-missing-server" => settings.notify_missing_server,
        "check-server-updates" => settings.check_server_updates,
        _ => return None,
    })
}

fn normalize_name(name: &str) -> String {
    let normalized = name.to_ascii_lowercase().replace('_', "-");
    match normalized.as_str() {
        "output" | "lighting-output" => "enabled".into(),
        "experience" => "experience-bar".into(),
        "health" => "health-bar".into(),
        "hunger" => "hunger-bar".into(),
        "saturation" | "saturation-bar" | "saturation-air" => "saturation-air-bar".into(),
        "biome" | "biome-effects" => "biome-lighting".into(),
        "dimming" => "dimming-mode".into(),
        "brightness" => "min-brightness".into(),
        "mirror-ram-rain" => "mirror-ram-rain-direction".into(),
        "end-flash" => "end-flash-effect".into(),
        "fire" => "fire-effect".into(),
        "water" => "water-effect".into(),
        "portal" => "portal-effects".into(),
        "movement-keys" => "movement-highlight".into(),
        "chat-key-pulse" => "chat-pulse".into(),
        "always-compass" => "always-show-compass".into(),
        "auto-start"
        | "auto-start-server"
        | "autostart"
        | "enable-server-autostart"
        | "server-autostart-confirmation" => "server-autostart".into(),
        "check-updates" => "check-server-updates".into(),
        "missing-server-notice" => "notify-missing-server".into(),
        _ => normalized,
    }
}

fn parse_bool(value: &str) -> Option<bool> {
    match value.to_ascii_lowercase().as_str() {
        "on" | "true" | "yes" | "enabled" | "1" => Some(true),
        "off" | "false" | "no" | "disabled" | "0" => Some(false),
        _ => None,
    }
}

fn chat(game: &mut super::Game<'_>, message: &str) {
    game.native_chat(&format!("[MineLights] {message}"));
}
