use super::minelights_bridge::{ACTION_CHECK_UPDATE, ACTION_REFRESH, BRIDGE, bridge_for};
use super::minelights_config::{Settings, device_disabled, on_off, set_device_enabled};
use super::minelights_protocol;
use super::minelights_settings::{config_dir, persist_settings, settings_for};
use mod_api::{ClientHost, Command, Text};
use std::path::{Path, PathBuf};

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
    "lodestone-shimmer",
    "low-health-warning",
    "movement-highlight",
    "chat-pulse",
    "always-show-compass",
    "ignore-inventory-compass",
    "spin-compass-without-target",
    "locator-bar",
    "server-autostart",
    "compass-priority",
    "compass-fallback-target",
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
    "notify-server-download-progress",
    "health-bar-keys",
    "hunger-bar-keys",
    "saturation-bar-keys",
    "experience-bar-keys",
    "locator-bar-keys",
    "heartbeat-red-keys",
    "heartbeat-white-keys",
    "compass-keys",
    "compass-center-key",
    "low-health-threshold",
    "saturation-tint-strength",
    "locator-dot-size",
    "experience-locator-mode",
    "absorption-overlay",
    "merge-saturation-into-hunger",
    "restore-lighting-outside-world",
    "outside-world-profile",
    "led-overrides",
    "health-color",
    "health-dim-color",
    "hunger-color",
    "hunger-dim-color",
    "saturation-color",
    "saturation-dim-color",
    "air-color",
    "air-dim-color",
    "experience-color",
    "experience-dim-color",
    "heartbeat-color",
    "absorption-color",
    "locator-bar-color",
];

pub(super) fn register(commands: &mut Vec<Command>) {
    const USAGES: &[&str] = &[
        "minelights",
        "minelights help",
        "minelights list",
        "minelights settings",
        "minelights get <setting:word>",
        "minelights set <setting:word> <value:text>",
        "minelights toggle <setting:word>",
        "minelights confirm server-autostart",
        "minelights devices",
        "minelights devices clear",
        "minelights device <number:integer> <state:word>",
        "minelights refresh",
        "minelights clear-disabled",
        "minelights clear-disabled-devices",
        "minelights openrgb",
        "minelights openrgb list",
        "minelights openrgb add <host:word> <port:integer>",
        "minelights openrgb remove <number:integer>",
        "minelights server import <path:text>",
        "minelights server list",
        "minelights server remove <server-id:word>",
        "minelights server forget <server-id:word>",
        "minelights server status",
        "minelights server stop [server-id:word]",
        "minelights server restart",
        "minelights server restart-admin",
        "minelights server restart-as-admin",
        "minelights server check-update",
        "minelights server check-updates",
        "minelights restart",
        "minelights restart-admin",
        "minelights restart-as-admin",
        "minelights check-update",
        "minelights check-updates",
    ];
    commands.extend(USAGES.iter().map(|usage| Command::new(usage)));
}

pub(super) fn minelights_command(client: &mut dyn ClientHost, command: &str) -> bool {
    if !command
        .split_whitespace()
        .next()
        .is_some_and(|root| root.eq_ignore_ascii_case("minelights"))
    {
        return false;
    }
    run_command(client, command);
    true
}

fn run_command(client: &mut dyn ClientHost, command: &str) {
    let mut parts = command.split_whitespace();
    parts.next();
    let Some(subcommand) = parts.next() else {
        show_help(client);
        return;
    };

    let subcommand = subcommand.to_ascii_lowercase();
    match subcommand.as_str() {
        "help" => show_help(client),
        "list" | "settings" => list_settings(client),
        "get" => {
            let Some(name) = parts.next() else {
                chat(client, "Usage: /minelights get <setting|all>");
                return;
            };
            get_setting(client, name);
        }
        "set" => {
            let Some(name) = parts.next() else {
                chat(client, "Usage: /minelights set <setting> <value>");
                return;
            };
            let value = parts.collect::<Vec<_>>().join(" ");
            if value.is_empty() {
                chat(client, "Usage: /minelights set <setting> <value>");
                return;
            }
            set_command(client, name, &value);
        }
        "toggle" => {
            let Some(name) = parts.next() else {
                chat(client, "Usage: /minelights toggle <setting>");
                return;
            };
            toggle_command(client, name);
        }
        "confirm" => {
            let Some(name) = parts.next() else {
                chat(client, "Usage: /minelights confirm server-autostart");
                return;
            };
            confirm_command(client, name);
        }
        "devices" => match parts.next().map(str::to_ascii_lowercase).as_deref() {
            Some("clear") => clear_disabled_devices(client),
            _ => list_devices(client),
        },
        "device" => {
            let (Some(index), Some(value)) = (parts.next(), parts.next()) else {
                chat(client, "Usage: /minelights device <number> <on|off>");
                return;
            };
            set_device(client, index, value);
        }
        "refresh" => request_action(client, ACTION_REFRESH, "Refreshing lighting devices."),
        "clear-disabled" | "clear-disabled-devices" => clear_disabled_devices(client),
        "openrgb" => {
            let action = parts.next().unwrap_or("list").to_ascii_lowercase();
            match action.as_str() {
                "list" => list_openrgb_endpoints(client),
                "add" => {
                    let (Some(host), Some(port)) = (parts.next(), parts.next()) else {
                        chat(client, "Usage: /minelights openrgb add <host> <port>");
                        return;
                    };
                    add_openrgb_endpoint(client, host, port);
                }
                "remove" => {
                    let Some(index) = parts.next() else {
                        chat(client, "Usage: /minelights openrgb remove <number>");
                        return;
                    };
                    remove_openrgb_endpoint(client, index);
                }
                _ => chat(
                    client,
                    "Usage: /minelights openrgb <list|add <host> <port>|remove <number>>",
                ),
            }
        }
        "server" => match parts.next().map(str::to_ascii_lowercase).as_deref() {
            Some("import") => {
                let Some(path) = server_import_path(command) else {
                    chat(client, "Usage: /minelights server import <path-to-.mlpair>");
                    return;
                };
                request_server_task(client, ServerTask::Import(path));
            }
            Some("list") => request_server_task(client, ServerTask::List),
            Some("remove") | Some("forget") => {
                let Some(server_id) = parts.next() else {
                    chat(client, "Usage: /minelights server remove <server-id>");
                    return;
                };
                request_server_task(client, ServerTask::Remove(server_id.to_owned()));
            }
            Some("status") => request_server_task(client, ServerTask::Status),
            Some("stop") => {
                let target = parts.next().unwrap_or("").to_owned();
                let settings = settings_for(client);
                let bridge = bridge_for(config_dir(client), &settings);
                if let Ok(mut request) = bridge.stop_request.lock() {
                    *request = Some(target);
                }
                chat(client, "Requesting MineLights Server shutdown.");
            }
            Some("restart") | Some("restart-admin") | Some("restart-as-admin") => {
                chat(
                    client,
                    "MineLights Server lifecycle is managed from its tray menu.",
                );
            }
            Some("check-update") | Some("check-updates") => request_action(
                client,
                ACTION_CHECK_UPDATE,
                "Checking for MineLights Server updates.",
            ),
            _ => chat(
                client,
                "Usage: /minelights server <status|list|import <path>|remove <server-id>|stop [server-id]|check-update>",
            ),
        },
        _ => {
            if matches!(
                subcommand.as_str(),
                "restart" | "restart-admin" | "restart-as-admin"
            ) {
                chat(
                    client,
                    "MineLights Server lifecycle is managed from its tray menu.",
                );
            } else if matches!(subcommand.as_str(), "check-update" | "check-updates") {
                request_action(
                    client,
                    ACTION_CHECK_UPDATE,
                    "Checking for MineLights Server updates.",
                );
            } else {
                chat(client, "Unknown command. Use /minelights help.");
            }
        }
    }
}

fn show_help(client: &mut dyn ClientHost) {
    chat(
        client,
        "Commands: /minelights list | get <setting> | set <setting> <value> | toggle <setting>",
    );
    chat(
        client,
        "Also: confirm server-autostart, devices [clear], device <number> <on|off>, refresh, server <status|list|import <path>|remove <server-id>|stop [server-id]|check-update>.",
    );
    chat(
        client,
        "OpenRGB endpoints: /minelights openrgb list | add <host> <port> | remove <number>.",
    );
    chat(
        client,
        "Boolean values: on/off. Dimming: local/sky/off. Brightness: 0-100. Compass priority: inventory/standard/lodestone/recovery. Compass fallback target: world-spawn/origin/player-spawn.",
    );
    chat(
        client,
        "OpenRGB endpoints use the native OpenRGB protocol without MineLights V2 auth/TLS; only add trusted servers.",
    );
}

fn list_settings(client: &mut dyn ClientHost) {
    let settings = settings_for(client);
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
        chat(client, &values);
    }
}

fn get_setting(client: &mut dyn ClientHost, name: &str) {
    let name = normalize_name(name);
    if name == "all" {
        list_settings(client);
        return;
    }
    let settings = settings_for(client);
    match setting_value(&settings, &name) {
        Some(value) => chat(client, &format!("{name} = {value}")),
        None => chat(client, "Unknown setting. Use /minelights list."),
    }
}

fn set_command(client: &mut dyn ClientHost, name: &str, value: &str) {
    let name = normalize_name(name);
    let mut settings = settings_for(client);
    match set_setting(&mut settings, &name, value) {
        Ok(message) => {
            if !save_settings(client, &settings) {
                return;
            }
            let _ = bridge_for(config_dir(client), &settings);
            if name == "led-overrides" {
                if let Some(bridge) = BRIDGE.get() {
                    bridge
                        .actions
                        .fetch_or(ACTION_REFRESH, std::sync::atomic::Ordering::Release);
                }
            }
            if name == "server-autostart" && settings.server_confirmation_pending {
                super::minelights::PENDING_SERVER_CONFIRMATION_REMINDER_SENT
                    .store(true, std::sync::atomic::Ordering::Release);
            }
            chat(client, message);
        }
        Err(message) => chat(client, message),
    }
}

fn toggle_command(client: &mut dyn ClientHost, name: &str) {
    let name = normalize_name(name);
    if name == "server-autostart" {
        let mut settings = settings_for(client);
        let message = if settings.allow_reviewed_server {
            settings.revoke_reviewed_server();
            "Reviewed server autostart is off."
        } else if settings.confirm_reviewed_server() {
            "Reviewed server autostart confirmed and enabled."
        } else {
            settings.request_reviewed_server();
            "Autostart is still off. Confirm with /minelights confirm server-autostart, or cancel with /minelights set server-autostart off."
        };
        if !save_settings(client, &settings) {
            return;
        }
        let _ = bridge_for(config_dir(client), &settings);
        if settings.server_confirmation_pending {
            super::minelights::PENDING_SERVER_CONFIRMATION_REMINDER_SENT
                .store(true, std::sync::atomic::Ordering::Release);
        }
        chat(client, message);
        return;
    }

    let settings = settings_for(client);
    if let Some(value) = boolean_setting(&settings, &name) {
        set_command(client, &name, if value { "off" } else { "on" });
    } else if setting_value(&settings, &name).is_some() {
        chat(
            client,
            "This setting uses a value. Use /minelights set <setting> <value>.",
        );
    } else {
        chat(client, "Unknown setting. Use /minelights list.");
    }
}

fn confirm_command(client: &mut dyn ClientHost, name: &str) {
    if normalize_name(name) != "server-autostart" {
        chat(client, "Only server-autostart requires confirmation.");
        return;
    }
    let mut settings = settings_for(client);
    if !settings.confirm_reviewed_server() {
        chat(
            client,
            "No server autostart confirmation is pending. Request it with /minelights set server-autostart on.",
        );
        return;
    }
    if !save_settings(client, &settings) {
        return;
    }
    let _ = bridge_for(config_dir(client), &settings);
    chat(
        client,
        &format!(
            "Reviewed MineLights Server {} autostart is confirmed. It will only launch on Windows.",
            super::minelights_server::SERVER_VERSION
        ),
    );
}

fn list_devices(client: &mut dyn ClientHost) {
    let settings = settings_for(client);
    let bridge = bridge_for(config_dir(client), &settings);
    let layouts = bridge
        .layouts
        .read()
        .map(|items| items.clone())
        .unwrap_or_default();
    if layouts.is_empty() {
        chat(client, "No MineLights devices are currently detected.");
        return;
    }
    for (index, layout) in layouts.iter().enumerate() {
        let enabled = !device_disabled(&settings, &layout.unique_id, layout.legacy_id.as_deref());
        chat(
            client,
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

fn set_device(client: &mut dyn ClientHost, index: &str, value: &str) {
    let Ok(index) = index.parse::<usize>() else {
        chat(
            client,
            "Device number must be a positive number. Use /minelights devices.",
        );
        return;
    };
    if index == 0 {
        chat(
            client,
            "Device numbers start at 1. Use /minelights devices.",
        );
        return;
    }
    let Some(enabled) = parse_bool(value) else {
        chat(client, "Device state must be on or off.");
        return;
    };
    let current = settings_for(client);
    let bridge = bridge_for(config_dir(client), &current);
    let layouts = bridge
        .layouts
        .read()
        .map(|items| items.clone())
        .unwrap_or_default();
    let Some(layout) = layouts.get(index - 1) else {
        chat(
            client,
            "No device has that number. Use /minelights devices.",
        );
        return;
    };
    let unique_id = layout.unique_id.clone();
    let display_name = layout.display_name.clone();
    let mut settings = settings_for(client);
    set_device_enabled(
        &mut settings,
        &unique_id,
        layout.legacy_id.as_deref(),
        enabled,
    );
    if !save_settings(client, &settings) {
        return;
    }
    bridge
        .actions
        .fetch_or(ACTION_REFRESH, std::sync::atomic::Ordering::Release);
    chat(
        client,
        &format!("Device {display_name} is now {}.", on_off(enabled)),
    );
}

fn clear_disabled_devices(client: &mut dyn ClientHost) {
    let mut settings = settings_for(client);
    let count = settings.disabled_devices.len();
    settings.disabled_devices.clear();
    settings.enabled_legacy_devices.clear();
    if !save_settings(client, &settings) {
        return;
    }
    if let Some(bridge) = BRIDGE.get() {
        bridge
            .actions
            .fetch_or(ACTION_REFRESH, std::sync::atomic::Ordering::Release);
    }
    chat(client, &format!("Re-enabled {count} disabled device(s)."));
}

fn request_action(client: &mut dyn ClientHost, action: u32, message: &str) {
    let settings = settings_for(client);
    let bridge = bridge_for(config_dir(client), &settings);
    bridge
        .actions
        .fetch_or(action, std::sync::atomic::Ordering::Release);
    chat(client, message);
}

fn list_openrgb_endpoints(client: &mut dyn ClientHost) {
    let settings = settings_for(client);
    if settings.openrgb_endpoints.is_empty() {
        chat(client, "No OpenRGB endpoints are configured.");
    } else {
        for (index, endpoint) in settings.openrgb_endpoints.iter().enumerate() {
            chat(
                client,
                &format!(
                    "OpenRGB endpoint {}: {}:{}",
                    index + 1,
                    endpoint.host,
                    endpoint.port
                ),
            );
        }
    }
    chat(
        client,
        "These use the native OpenRGB protocol without MineLights V2 authentication or TLS. Only add trusted endpoints.",
    );
}

fn add_openrgb_endpoint(client: &mut dyn ClientHost, host: &str, port: &str) {
    let Ok(port) = port.parse::<u16>() else {
        chat(client, "OpenRGB port must be between 1 and 65535.");
        return;
    };
    if port == 0 || host.is_empty() || host.len() > 253 || host.chars().any(char::is_whitespace) {
        chat(client, "OpenRGB host or port is invalid.");
        return;
    }
    let mut settings = settings_for(client);
    if settings
        .openrgb_endpoints
        .iter()
        .any(|endpoint| endpoint.host.eq_ignore_ascii_case(host) && endpoint.port == port)
    {
        chat(client, "That OpenRGB endpoint is already configured.");
        return;
    }
    if settings.openrgb_endpoints.len() >= 112 {
        chat(client, "The OpenRGB endpoint limit has been reached.");
        return;
    }
    settings
        .openrgb_endpoints
        .push(super::minelights_config::OpenRgbEndpoint {
            host: host.to_owned(),
            port,
        });
    if !save_settings(client, &settings) {
        return;
    }
    let bridge = bridge_for(config_dir(client), &settings);
    bridge
        .actions
        .fetch_or(ACTION_REFRESH, std::sync::atomic::Ordering::Release);
    chat(
        client,
        "OpenRGB endpoint added. It uses OpenRGB's native protocol without MineLights authentication or TLS; only add trusted endpoints.",
    );
}

fn remove_openrgb_endpoint(client: &mut dyn ClientHost, number: &str) {
    let Ok(number) = number.parse::<usize>() else {
        chat(
            client,
            "OpenRGB endpoint number must be a positive number. Use /minelights openrgb list.",
        );
        return;
    };
    if number == 0 {
        chat(client, "OpenRGB endpoint numbers start at 1.");
        return;
    }
    let mut settings = settings_for(client);
    if number > settings.openrgb_endpoints.len() {
        chat(
            client,
            "No OpenRGB endpoint has that number. Use /minelights openrgb list.",
        );
        return;
    }
    let endpoint = settings.openrgb_endpoints.remove(number - 1);
    if !save_settings(client, &settings) {
        return;
    }
    let bridge = bridge_for(config_dir(client), &settings);
    bridge
        .actions
        .fetch_or(ACTION_REFRESH, std::sync::atomic::Ordering::Release);
    chat(
        client,
        &format!(
            "Removed OpenRGB endpoint {}:{}.",
            endpoint.host, endpoint.port
        ),
    );
}

fn server_import_path(command: &str) -> Option<PathBuf> {
    let mut remainder = command.trim().trim_start_matches('/');
    for expected in ["minelights", "server", "import"] {
        let (word, tail) = remainder.split_once(char::is_whitespace)?;
        if !word.eq_ignore_ascii_case(expected) {
            return None;
        }
        remainder = tail.trim_start();
    }
    let path = remainder;
    let path = path
        .strip_prefix('"')
        .and_then(|value| value.strip_suffix('"'))
        .unwrap_or(path);
    (!path.is_empty()).then(|| PathBuf::from(path))
}

pub(super) enum ServerTask {
    List,
    Status,
    Import(PathBuf),
    Remove(String),
}

fn request_server_task(client: &mut dyn ClientHost, task: ServerTask) {
    let config_dir = config_dir(client);
    let importing = matches!(task, ServerTask::Import(_));
    if config_dir.is_none() && !matches!(task, ServerTask::Status) {
        chat(client, "MineLights client storage is unavailable.");
        return;
    }
    let settings = settings_for(client);
    let bridge = bridge_for(config_dir, &settings);
    if !bridge.submit_server_task(task) {
        chat(
            client,
            "MineLights Server requests are busy or unavailable. Try again shortly.",
        );
    } else if importing {
        chat(
            client,
            "Importing the .mlpair file. Its contents and pairing code will not be shown.",
        );
    }
}

/// Runs on the MineLights bridge worker, alongside connection-state changes.
pub(super) fn run_server_task(task: ServerTask, config_dir: Option<&Path>) -> (Vec<String>, bool) {
    let mut lines = Vec::new();
    let mut refresh = false;
    match task {
        ServerTask::Status => {
            lines.push(match minelights_protocol::local_endpoint() {
                Ok(Some(endpoint)) => format!(
                    "Local MineLights Server {} is advertised at {}:{}.",
                    endpoint.server_name, endpoint.host, endpoint.port
                ),
                Ok(None) => "No local V2 MineLights Server endpoint is advertised.".to_owned(),
                Err(_) => "The local V2 endpoint document is invalid.".to_owned(),
            });
            lines.extend(remote_server_lines(config_dir));
        }
        ServerTask::List => lines.extend(remote_server_lines(config_dir)),
        ServerTask::Import(path) => {
            let result = config_dir
                .ok_or_else(|| "MineLights client storage is unavailable.".to_owned())
                .and_then(|dir| minelights_protocol::import_remote_pairing(dir, &path));
            refresh = result.is_ok();
            lines.push(match result {
                Ok(_) => "Remote server pairing imported. Its name and address are visible with /minelights server list; pairing credentials were not displayed.".to_owned(),
                Err(error) => match error.as_str() {
                    "the server pairing file has expired" => "The server pairing file has expired.".to_owned(),
                    "unsupported or invalid .mlpair file" => "The .mlpair file is unsupported or invalid.".to_owned(),
                    "MineLights client storage is unavailable." => error,
                    _ => "Could not import the remote server pairing. Check the .mlpair file and server availability.".to_owned(),
                },
            });
        }
        ServerTask::Remove(server_id) => {
            let result =
                config_dir.map(|dir| minelights_protocol::remove_remote_server(dir, &server_id));
            refresh = matches!(result, Some(Ok(true)));
            lines.push(match result {
                Some(Ok(true)) => "Remote server pairing removed from this client. Revoke its client credential in the server tray to disable access.".to_owned(),
                Some(Ok(false)) => "That remote server is not paired on this client.".to_owned(),
                Some(Err(_)) => "Could not remove the remote server pairing.".to_owned(),
                None => "MineLights client storage is unavailable.".to_owned(),
            });
        }
    }
    let lines = lines
        .into_iter()
        .map(|line| format!("[MineLights] {line}"))
        .collect();
    (lines, refresh)
}

fn remote_server_lines(config_dir: Option<&Path>) -> Vec<String> {
    let Some(config_dir) = config_dir else {
        return vec!["MineLights client storage is unavailable.".to_owned()];
    };
    match minelights_protocol::load_remote_servers(config_dir) {
        Ok(remotes) if remotes.is_empty() => {
            vec!["No remote MineLights Servers are paired.".to_owned()]
        }
        Ok(remotes) => remotes
            .into_iter()
            .map(|remote| {
                format!(
                    "Remote MineLights Server {} at {}:{}",
                    remote.endpoint.server_id, remote.endpoint.host, remote.endpoint.port
                )
            })
            .collect(),
        Err(_) => vec!["Could not read paired MineLights Server profiles.".to_owned()],
    }
}

fn effect_field(name: &str) -> Option<String> {
    if !SETTING_NAMES.contains(&name) {
        return None;
    }
    if name == "absorption-overlay" {
        return Some("enable_absorption_overlay".into());
    }
    if name == "merge-saturation-into-hunger" {
        return Some("merge_saturation_into_hunger".into());
    }
    if matches!(
        name,
        "restore-lighting-outside-world" | "outside-world-profile" | "led-overrides"
    ) {
        return Some(name.replace('-', "_"));
    }
    if name.ends_with("-keys")
        || name.ends_with("-color")
        || matches!(
            name,
            "compass-center-key"
                | "low-health-threshold"
                | "saturation-tint-strength"
                | "locator-dot-size"
                | "experience-locator-mode"
        )
    {
        return Some(name.replace('-', "_"));
    }
    None
}

#[cfg(test)]
mod effect_setting_tests {
    use super::*;

    #[test]
    fn validates_and_round_trips_effect_commands() {
        let mut settings = Settings::default();
        set_setting(&mut settings, "health-bar-keys", "Q,W").unwrap();
        set_setting(&mut settings, "health-color", "#123456").unwrap();
        set_setting(&mut settings, "absorption-overlay", "off").unwrap();
        assert_eq!(
            setting_value(&settings, "health-bar-keys").as_deref(),
            Some("Q,W")
        );
        assert_eq!(
            setting_value(&settings, "health-color").as_deref(),
            Some("#123456")
        );
        assert!(!settings.enable_absorption_overlay);
        assert!(set_setting(&mut settings, "health-color", "not-a-color").is_err());
        assert!(set_setting(&mut settings, "saturation-tint-strength", "NaN").is_err());
    }

    #[test]
    fn server_download_notifications_can_be_read_written_and_toggled() {
        let mut settings = Settings::default();
        assert!(settings.notify_server_download_progress);
        assert_eq!(
            setting_value(&settings, "notify-server-download-progress").as_deref(),
            Some("On")
        );
        set_setting(&mut settings, "notify-server-download-progress", "off").unwrap();
        assert!(!settings.notify_server_download_progress);
        assert_eq!(
            setting_value(&settings, "notify-server-download-progress").as_deref(),
            Some("Off")
        );
        assert_eq!(
            normalize_name("server-download-notifications"),
            "notify-server-download-progress"
        );
    }

    #[test]
    fn compass_fallback_aliases_match_java_commands() {
        let mut settings = Settings::default();
        for alias in ["compass-fallback", "compass-target"] {
            let name = normalize_name(alias);
            assert_eq!(name, "compass-fallback-target");
            set_setting(&mut settings, &name, "player-spawn").unwrap();
            assert_eq!(
                setting_value(&settings, &name).as_deref(),
                Some("player-spawn")
            );
        }
    }
}

fn effect_value(settings: &Settings, name: &str) -> Option<String> {
    let data = serde_json::to_value(settings).ok()?;
    let field = effect_field(name)?;
    let value = data.get(field.as_str())?;
    if name.ends_with("-color") {
        return Some(format!("#{:06X}", value.as_u64()?));
    }
    if let Some(list) = value.as_array() {
        return Some(
            list.iter()
                .filter_map(|v| v.as_str())
                .collect::<Vec<_>>()
                .join(if name == "led-overrides" { "; " } else { "," }),
        );
    }
    Some(match value {
        serde_json::Value::String(text) => text.clone(),
        serde_json::Value::Bool(value) => on_off(*value).into(),
        _ => value.to_string(),
    })
}

fn set_effect_setting(
    settings: &mut Settings,
    name: &str,
    raw: &str,
) -> Result<&'static str, &'static str> {
    let field = effect_field(name).ok_or("Unknown effect setting.")?;
    let value = raw.trim();
    let parsed = if name.ends_with("-color") {
        let hex = value.strip_prefix('#').unwrap_or(value);
        if hex.len() != 6 {
            return Err("Colors require six hexadecimal digits, e.g. #FF0080.");
        }
        serde_json::json!(u32::from_str_radix(hex, 16).map_err(|_| "Invalid RGB color.")?)
    } else if name == "led-overrides" {
        let mut list = Vec::new();
        if !value.eq_ignore_ascii_case("none") {
            for item in value.split(';') {
                let Some((device, rest)) = item.trim().rsplit_once('|') else {
                    return Err("Use Device name|index=KEY for LED overrides.");
                };
                let Some((index, key)) = rest.split_once('=') else {
                    return Err("Use Device name|index=KEY for LED overrides.");
                };
                if device.trim().is_empty()
                    || index.trim().parse::<usize>().is_err()
                    || key.trim().is_empty()
                {
                    return Err("Invalid LED override.");
                }
                list.push(item.trim().to_owned());
            }
        }
        serde_json::json!(list)
    } else if name.ends_with("-keys") {
        let mut list = Vec::new();
        if !value.eq_ignore_ascii_case("none") {
            for key in value.split(',') {
                let key = minelights_engine::standardize_key(key.trim());
                if key.is_empty() {
                    return Err("Key names cannot be empty.");
                }
                list.push(key);
            }
        }
        serde_json::json!(list)
    } else if name == "compass-center-key" {
        serde_json::json!(if value.eq_ignore_ascii_case("none") {
            String::new()
        } else {
            minelights_engine::standardize_key(value)
        })
    } else if name == "experience-locator-mode" {
        let mode = value.to_ascii_lowercase();
        if !["auto", "experience", "locator", "both"].contains(&mode.as_str()) {
            return Err("Mode must be auto, experience, locator, or both.");
        }
        serde_json::json!(mode)
    } else if name == "outside-world-profile" {
        serde_json::json!(if value.eq_ignore_ascii_case("none") {
            ""
        } else {
            value
        })
    } else if name == "absorption-overlay"
        || name == "merge-saturation-into-hunger"
        || name == "restore-lighting-outside-world"
    {
        serde_json::json!(parse_bool(value).ok_or("Use on or off.")?)
    } else {
        let number: f32 = value.parse().map_err(|_| "Expected a number.")?;
        let (min, max) = match name {
            "locator-dot-size" => (0.5, 5.0),
            "saturation-tint-strength" => (0.0, 1.0),
            _ => (1.0, 20.0),
        };
        if !number.is_finite() || number < min || number > max {
            return Err("Number is outside the supported range.");
        }
        serde_json::json!(number)
    };
    let mut data = serde_json::to_value(&*settings).map_err(|_| "Could not read settings.")?;
    data.as_object_mut()
        .ok_or("Could not read settings.")?
        .insert(field, parsed);
    let pending = settings.server_confirmation_pending;
    *settings = serde_json::from_value(data).map_err(|_| "Invalid setting value.")?;
    settings.server_confirmation_pending = pending;
    Ok("Setting updated.")
}

fn set_setting(
    settings: &mut Settings,
    name: &str,
    value: &str,
) -> Result<&'static str, &'static str> {
    if effect_field(name).is_some() {
        return set_effect_setting(settings, name, value);
    }
    if name == "server-autostart" {
        let Some(enabled) = parse_bool(value) else {
            return Err("server-autostart value must be on or off.");
        };
        if !enabled {
            settings.revoke_reviewed_server();
            return Ok("Reviewed server autostart is off.");
        }
        if !settings.request_reviewed_server() {
            return Ok("Reviewed server autostart is already confirmed and enabled.");
        }
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
    if name == "compass-fallback-target" {
        settings.compass_fallback_target = match value.to_ascii_lowercase().as_str() {
            "world-spawn" | "world_spawn" | "spawn" => 0,
            "origin" | "0,0" | "zero" => 1,
            "player-spawn" | "player_spawn" | "bed" | "respawn" => 2,
            _ => {
                return Err(
                    "Compass fallback target must be world-spawn, origin, or player-spawn.",
                );
            }
        };
        settings.server_confirmation_pending = false;
        return Ok("Compass fallback target updated.");
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
        "lodestone-shimmer" => &mut settings.lodestone_compass_shimmer,
        "low-health-warning" => &mut settings.low_health_warning,
        "movement-highlight" => &mut settings.movement_highlight,
        "chat-pulse" => &mut settings.chat_pulse,
        "always-show-compass" => &mut settings.always_show_compass,
        "ignore-inventory-compass" => &mut settings.ignore_inventory_compass,
        "spin-compass-without-target" => &mut settings.spin_compass_without_target,
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
        "notify-server-download-progress" => &mut settings.notify_server_download_progress,
        _ => return Err("Unknown setting. Use /minelights list."),
    };
    *field = enabled;
    settings.server_confirmation_pending = false;
    Ok("Setting updated.")
}

fn setting_value(settings: &Settings, name: &str) -> Option<String> {
    if effect_field(name).is_some() {
        return effect_value(settings, name);
    }
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
        "lodestone-shimmer" => on_off(settings.lodestone_compass_shimmer).into(),
        "low-health-warning" => on_off(settings.low_health_warning).into(),
        "movement-highlight" => on_off(settings.movement_highlight).into(),
        "chat-pulse" => on_off(settings.chat_pulse).into(),
        "always-show-compass" => on_off(settings.always_show_compass).into(),
        "ignore-inventory-compass" => on_off(settings.ignore_inventory_compass).into(),
        "spin-compass-without-target" => on_off(settings.spin_compass_without_target).into(),
        "locator-bar" => on_off(settings.locator_bar).into(),
        "server-autostart" if settings.server_confirmation_pending => "pending confirmation".into(),
        "server-autostart" => on_off(settings.allow_reviewed_server).into(),
        "compass-priority" => ["inventory", "standard", "lodestone", "recovery"]
            [usize::from(settings.compass_priority.min(3))]
        .into(),
        "compass-fallback-target" => ["world-spawn", "origin", "player-spawn"]
            [usize::from(settings.compass_fallback_target.min(2))]
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
        "notify-server-download-progress" => {
            on_off(settings.notify_server_download_progress).into()
        }
        _ => return None,
    };
    Some(value)
}

fn boolean_setting(settings: &Settings, name: &str) -> Option<bool> {
    Some(match name {
        "absorption-overlay" => settings.enable_absorption_overlay,
        "merge-saturation-into-hunger" => settings.merge_saturation_into_hunger,
        "restore-lighting-outside-world" => settings.restore_lighting_outside_world,
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
        "lodestone-shimmer" => settings.lodestone_compass_shimmer,
        "low-health-warning" => settings.low_health_warning,
        "movement-highlight" => settings.movement_highlight,
        "chat-pulse" => settings.chat_pulse,
        "always-show-compass" => settings.always_show_compass,
        "ignore-inventory-compass" => settings.ignore_inventory_compass,
        "spin-compass-without-target" => settings.spin_compass_without_target,
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
        "notify-server-download-progress" => settings.notify_server_download_progress,
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
        "lodestone-glint" | "lodestone-compass-shimmer" => "lodestone-shimmer".into(),
        "movement-keys" => "movement-highlight".into(),
        "chat-key-pulse" => "chat-pulse".into(),
        "always-compass" => "always-show-compass".into(),
        "compass-fallback" | "compass-target" => "compass-fallback-target".into(),
        "compass-spin" | "spin-compass" => "spin-compass-without-target".into(),
        "auto-start"
        | "auto-start-server"
        | "autostart"
        | "enable-server-autostart"
        | "server-autostart-confirmation" => "server-autostart".into(),
        "check-updates" => "check-server-updates".into(),
        "missing-server-notice" => "notify-missing-server".into(),
        "server-download-notifications" | "notify-server-downloads" => {
            "notify-server-download-progress".into()
        }
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

fn chat(client: &mut dyn ClientHost, message: &str) {
    let _ = client.chat_line(Text::literal(format!("[MineLights] {message}")));
}

fn save_settings(client: &mut dyn ClientHost, settings: &Settings) -> bool {
    if persist_settings(client, settings.clone()) {
        true
    } else {
        chat(client, "MineLights could not persist the setting.");
        false
    }
}

#[cfg(test)]
mod server_task_tests {
    use super::*;

    #[test]
    fn import_path_keeps_internal_spaces_and_accepts_command_whitespace() {
        let expected = PathBuf::from(r"C:\MineLights Pairing\server.mlpair");
        assert_eq!(
            server_import_path(
                r#"/MineLights   server import "C:\MineLights Pairing\server.mlpair""#
            ),
            Some(expected.clone())
        );
        assert_eq!(
            server_import_path(r"minelights server import C:\MineLights Pairing\server.mlpair"),
            Some(expected)
        );
        assert!(server_import_path(r#"minelights server import """#).is_none());
    }

    #[test]
    fn import_errors_do_not_echo_pairing_bytes_or_paths() {
        let directory = std::env::temp_dir().join(format!(
            "minelights-pairing-feedback-{}",
            std::process::id()
        ));
        std::fs::create_dir_all(&directory).unwrap();
        let pair_path = directory.join("pairing-path-marker.mlpair");
        std::fs::write(&pair_path, b"\"pairing-secret-marker\"").unwrap();

        let (notices, refresh) =
            run_server_task(ServerTask::Import(pair_path.clone()), Some(&directory));
        assert!(!refresh);
        assert_eq!(notices.len(), 1);
        assert!(!notices[0].contains("pairing-secret-marker"));
        assert!(!notices[0].contains("pairing-path-marker"));
        assert!(
            minelights_protocol::load_remote_servers(&directory)
                .unwrap()
                .is_empty()
        );
        std::fs::remove_file(pair_path).unwrap();
        std::fs::remove_dir(directory).unwrap();
    }
}
