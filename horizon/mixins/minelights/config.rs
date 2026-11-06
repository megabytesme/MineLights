use std::sync::{Mutex, OnceLock};

const MOD_ID: &str = "minelights";

#[derive(Clone, Debug, serde::Serialize, serde::Deserialize, PartialEq, Eq)]
pub(super) struct OpenRgbEndpoint {
    pub(super) host: String,
    pub(super) port: u16,
}

impl Default for OpenRgbEndpoint {
    fn default() -> Self {
        Self {
            host: "127.0.0.1".into(),
            port: 6742,
        }
    }
}

#[derive(Clone, Debug, serde::Serialize, serde::Deserialize)]
#[serde(default)]
pub(super) struct Settings {
    pub(super) enabled: bool,
    pub(super) experience_bar: bool,
    pub(super) health_bar: bool,
    pub(super) hunger_bar: bool,
    pub(super) saturation_air_bar: bool,
    pub(super) compass_effect: bool,
    pub(super) biome_lighting: bool,
    pub(super) dimming_mode: u8,
    pub(super) min_brightness: f32,
    pub(super) weather_effects: bool,
    pub(super) mirror_ram_rain_direction: bool,
    pub(super) end_flash_effect: bool,
    pub(super) fire_effect: bool,
    pub(super) water_effect: bool,
    pub(super) portal_effects: bool,
    pub(super) low_health_warning: bool,
    pub(super) movement_highlight: bool,
    pub(super) chat_pulse: bool,
    pub(super) always_show_compass: bool,
    pub(super) locator_bar: bool,
    pub(super) experience_locator_mode: String,
    pub(super) low_health_threshold: f32,
    pub(super) enable_absorption_overlay: bool,
    pub(super) merge_saturation_into_hunger: bool,
    pub(super) saturation_tint_strength: f32,
    pub(super) locator_dot_size: f32,
    pub(super) restore_lighting_outside_world: bool,
    pub(super) outside_world_profile: String,
    pub(super) led_overrides: Vec<String>,
    pub(super) health_bar_keys: Vec<String>,
    pub(super) hunger_bar_keys: Vec<String>,
    pub(super) saturation_bar_keys: Vec<String>,
    pub(super) experience_bar_keys: Vec<String>,
    pub(super) locator_bar_keys: Vec<String>,
    pub(super) heartbeat_red_keys: Vec<String>,
    pub(super) heartbeat_white_keys: Vec<String>,
    pub(super) compass_keys: Vec<String>,
    pub(super) compass_center_key: String,
    pub(super) health_color: u32,
    pub(super) health_dim_color: u32,
    pub(super) hunger_color: u32,
    pub(super) hunger_dim_color: u32,
    pub(super) saturation_color: u32,
    pub(super) saturation_dim_color: u32,
    pub(super) air_color: u32,
    pub(super) air_dim_color: u32,
    pub(super) experience_color: u32,
    pub(super) experience_dim_color: u32,
    pub(super) heartbeat_color: u32,
    pub(super) absorption_color: u32,
    pub(super) locator_bar_color: u32,
    // A new field deliberately does not inherit the old, default-on setting.
    pub(super) allow_reviewed_server: bool,
    pub(super) notify_missing_server: bool,
    pub(super) check_server_updates: bool,
    pub(super) notify_server_download_progress: bool,
    #[serde(skip)]
    pub(super) server_confirmation_pending: bool,
    pub(super) compass_priority: u8,
    pub(super) enable_corsair: bool,
    pub(super) enable_asus: bool,
    pub(super) enable_logitech: bool,
    pub(super) enable_razer: bool,
    pub(super) enable_wooting: bool,
    pub(super) enable_steelseries: bool,
    pub(super) enable_msi: bool,
    pub(super) enable_novation: bool,
    pub(super) enable_picopi: bool,
    pub(super) enable_openrgb: bool,
    #[serde(default = "default_openrgb_endpoints")]
    pub(super) openrgb_endpoints: Vec<OpenRgbEndpoint>,
    pub(super) enable_yeelight: bool,
    pub(super) disabled_devices: Vec<String>,
    pub(super) enabled_legacy_devices: Vec<String>,
}

impl Default for Settings {
    fn default() -> Self {
        Self {
            enabled: true,
            experience_bar: true,
            health_bar: true,
            hunger_bar: true,
            saturation_air_bar: true,
            compass_effect: true,
            biome_lighting: true,
            dimming_mode: 0,
            min_brightness: 0.2,
            weather_effects: true,
            mirror_ram_rain_direction: false,
            end_flash_effect: true,
            fire_effect: true,
            water_effect: true,
            portal_effects: true,
            low_health_warning: true,
            movement_highlight: true,
            chat_pulse: true,
            always_show_compass: false,
            locator_bar: true,
            experience_locator_mode: "auto".into(),
            low_health_threshold: 10.0,
            enable_absorption_overlay: true,
            merge_saturation_into_hunger: false,
            saturation_tint_strength: 0.25,
            locator_dot_size: 1.5,
            restore_lighting_outside_world: true,
            outside_world_profile: String::new(),
            led_overrides: Vec::new(),
            health_bar_keys: keys(&["F1", "F2", "F3", "F4"]),
            hunger_bar_keys: keys(&["F5", "F6", "F7", "F8"]),
            saturation_bar_keys: keys(&["F9", "F10", "F11", "F12"]),
            experience_bar_keys: keys(&["1", "2", "3", "4", "5", "6", "7", "8", "9", "0"]),
            locator_bar_keys: keys(&["1", "2", "3", "4", "5", "6", "7", "8", "9", "0"]),
            heartbeat_red_keys: keys(&[
                "4", "5", "6", "7", "E", "T", "U", "D", "F", "H", "V", "B", "SPACE",
            ]),
            heartbeat_white_keys: keys(&["R", "Y", "G"]),
            compass_keys: keys(&[
                "NUMPAD8", "NUMPAD9", "NUMPAD6", "NUMPAD3", "NUMPAD2", "NUMPAD1", "NUMPAD4",
                "NUMPAD7",
            ]),
            compass_center_key: "NUMPAD5".into(),
            health_color: 0xff0000,
            health_dim_color: 0x1e0000,
            hunger_color: 0xffa500,
            hunger_dim_color: 0x1e0f00,
            saturation_color: 0xc8ff00,
            saturation_dim_color: 0x283200,
            air_color: 0xadd8e6,
            air_dim_color: 0x000032,
            experience_color: 0x00ff00,
            experience_dim_color: 0x0a1e0a,
            heartbeat_color: 0xff0000,
            absorption_color: 0xffd700,
            locator_bar_color: 0x0a0a0a,
            allow_reviewed_server: false,
            notify_missing_server: true,
            check_server_updates: true,
            notify_server_download_progress: true,
            server_confirmation_pending: false,
            compass_priority: 0,
            enable_corsair: true,
            enable_asus: true,
            enable_logitech: true,
            enable_razer: true,
            enable_wooting: true,
            enable_steelseries: true,
            enable_msi: true,
            enable_novation: true,
            enable_picopi: true,
            enable_openrgb: true,
            openrgb_endpoints: default_openrgb_endpoints(),
            enable_yeelight: true,
            disabled_devices: Vec::new(),
            enabled_legacy_devices: Vec::new(),
        }
    }
}

fn default_openrgb_endpoints() -> Vec<OpenRgbEndpoint> {
    vec![OpenRgbEndpoint::default()]
}

fn keys(values: &[&str]) -> Vec<String> {
    values.iter().map(|value| (*value).to_owned()).collect()
}

pub(super) const STATIC_SETTING_COUNT: usize = 49;

pub(super) static SETTINGS: OnceLock<Mutex<Settings>> = OnceLock::new();

pub(super) fn settings_for(game: &super::Game<'_>) -> Settings {
    SETTINGS
        .get_or_init(|| {
            let loaded = game
                .native_setting(MOD_ID, "settings.json")
                .and_then(|text| serde_json::from_str::<Settings>(&text).ok())
                .unwrap_or_default();
            Mutex::new(loaded)
        })
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner())
        .clone()
}

pub(super) fn persist_settings(game: &super::Game<'_>, settings: Settings) {
    let json = serde_json::to_string(&settings).unwrap_or_else(|_| "{}".into());
    game.native_set_setting(MOD_ID, "settings.json", &json);
}

pub(super) fn settings_labels(settings: Settings, language: &str) -> Vec<String> {
    let server_version = super::minelights_server::SERVER_VERSION;
    let mut labels = vec![
        format!("Lighting output: {}", on_off(settings.enabled)),
        format!(
            "Experience bar ({}): {}",
            settings.experience_bar_keys.join(" "),
            on_off(settings.experience_bar)
        ),
        format!(
            "Health bar ({}): {}",
            settings.health_bar_keys.join(" "),
            on_off(settings.health_bar)
        ),
        format!(
            "Hunger bar ({}): {}",
            settings.hunger_bar_keys.join(" "),
            on_off(settings.hunger_bar)
        ),
        format!(
            "Saturation and air bar ({}): {}",
            settings.saturation_bar_keys.join(" "),
            on_off(settings.saturation_air_bar)
        ),
        format!(
            "Compass effect ({}): {}",
            settings.compass_keys.join(" "),
            on_off(settings.compass_effect)
        ),
        format!("Biome lighting: {}", on_off(settings.biome_lighting)),
        format!(
            "Dimming mode: {}",
            ["Local light", "Sky light", "Off"][usize::from(settings.dimming_mode.min(2))]
        ),
        format!(
            "Minimum brightness: {}%",
            (settings.min_brightness * 100.0).round() as u8
        ),
        format!("Weather effects: {}", on_off(settings.weather_effects)),
        format!(
            "Mirror RAM rain direction: {}",
            on_off(settings.mirror_ram_rain_direction)
        ),
        format!("End flash effect: {}", on_off(settings.end_flash_effect)),
        format!("Fire effect: {}", on_off(settings.fire_effect)),
        format!("Water effect: {}", on_off(settings.water_effect)),
        format!("Portal effects: {}", on_off(settings.portal_effects)),
        format!(
            "Low health warning: {}",
            on_off(settings.low_health_warning)
        ),
        format!("Movement keys: {}", on_off(settings.movement_highlight)),
        format!("Chat key pulse: {}", on_off(settings.chat_pulse)),
        format!(
            "Always show spawn compass: {}",
            on_off(settings.always_show_compass)
        ),
        format!(
            "Waypoint locator bar ({}): {}",
            settings.locator_bar_keys.join(" "),
            on_off(settings.locator_bar)
        ),
        if settings.server_confirmation_pending {
            format!("CONFIRM: download and run verified MineLights Server {server_version}")
        } else {
            format!(
                "Download and run reviewed Server {server_version}: {} (click for details)",
                on_off(settings.allow_reviewed_server)
            )
        },
        format!(
            "Compass priority: {}",
            [
                "Inventory order",
                "Standard first",
                "Lodestone first",
                "Recovery first"
            ][usize::from(settings.compass_priority.min(3))]
        ),
        format!("Corsair integration: {}", on_off(settings.enable_corsair)),
        format!("ASUS integration: {}", on_off(settings.enable_asus)),
        format!("Logitech integration: {}", on_off(settings.enable_logitech)),
        format!("Razer integration: {}", on_off(settings.enable_razer)),
        format!("Wooting integration: {}", on_off(settings.enable_wooting)),
        format!(
            "SteelSeries integration: {}",
            on_off(settings.enable_steelseries)
        ),
        format!("MSI integration: {}", on_off(settings.enable_msi)),
        format!("Novation integration: {}", on_off(settings.enable_novation)),
        format!("PicoPi integration: {}", on_off(settings.enable_picopi)),
        format!(
            "OpenRGB native protocol (no MineLights auth/TLS): {}",
            on_off(settings.enable_openrgb)
        ),
        format!("Yeelight integration: {}", on_off(settings.enable_yeelight)),
        "Refresh devices".into(),
        "MineLights Server tray menu: restart".into(),
        "MineLights Server tray menu: elevated restart".into(),
        "Check for MineLights Server updates".into(),
        "Clear disabled devices".into(),
        format!(
            "Notify when MineLights Server is missing: {}",
            on_off(settings.notify_missing_server)
        ),
        format!(
            "Check for MineLights Server updates: {}",
            on_off(settings.check_server_updates)
        ),
        format!(
            "Absorption overlay: {}",
            on_off(settings.enable_absorption_overlay)
        ),
        format!(
            "Merge saturation into hunger: {}",
            on_off(settings.merge_saturation_into_hunger)
        ),
        format!(
            "Restore OpenRGB outside worlds: {}",
            on_off(settings.restore_lighting_outside_world)
        ),
        format!("Low health threshold: {:.0}", settings.low_health_threshold),
        format!(
            "Saturation tint strength: {:.2}",
            settings.saturation_tint_strength
        ),
        format!(
            "Experience / locator mode: {}",
            settings.experience_locator_mode
        ),
        format!("Locator dot size: {:.1}", settings.locator_dot_size),
        format!(
            "Notify me about MineLights Server downloads: {}",
            on_off(settings.notify_server_download_progress)
        ),
        "Configure keys and colors with /minelights set".into(),
    ];
    for (index, label) in labels.iter_mut().enumerate() {
        if (40..=46).contains(&index) {
            if let Some(translated) = super::minelights_lang::setting_label(language, index + 7) {
                if let Some((_, value)) = label.rsplit_once(": ") {
                    *label = format!("{translated}: {value}");
                }
            }
            continue;
        }
        if index == 47 {
            if let Some(translated) = super::minelights_lang::setting_label_or_english(language, 54)
            {
                if let Some((_, value)) = label.rsplit_once(": ") {
                    *label = format!("{translated}: {value}");
                }
            }
            continue;
        }
        // The old translations describe different, less restrictive behaviour.
        if index == 20 || index == 31 || (34..=36).contains(&index) || index >= 38 {
            continue;
        }
        if let Some(translated) = super::minelights_lang::setting_label(language, index) {
            let keys = match index {
                1 => Some(&settings.experience_bar_keys),
                2 => Some(&settings.health_bar_keys),
                3 => Some(&settings.hunger_bar_keys),
                4 => Some(&settings.saturation_bar_keys),
                5 => Some(&settings.compass_keys),
                19 => Some(&settings.locator_bar_keys),
                _ => None,
            };
            let translated = keys
                .map(|keys| translated.replace("%s", &keys.join(" ")))
                .unwrap_or_else(|| translated.to_owned());
            if index < 33 {
                if let Some((_, value)) = label.rsplit_once(": ") {
                    let value_index = match index {
                        7 => Some(40 + usize::from(settings.dimming_mode.min(2))),
                        21 => Some(43 + usize::from(settings.compass_priority.min(3))),
                        _ => None,
                    };
                    let translated_value = value_index
                        .and_then(|i| super::minelights_lang::setting_label(language, i))
                        .unwrap_or(value);
                    *label = format!("{translated}: {translated_value}");
                }
            } else {
                *label = translated.to_owned();
            }
        }
    }
    labels
}

/// Resolve pre-V2 manufacturer|model exclusions without enabling same-model siblings.
pub(super) fn device_disabled(settings: &Settings, id: &str, legacy: Option<&str>) -> bool {
    settings.disabled_devices.iter().any(|v| v == id)
        || (legacy.is_some_and(|old| settings.disabled_devices.iter().any(|v| v == old))
            && !settings.enabled_legacy_devices.iter().any(|v| v == id))
}

pub(super) fn set_device_enabled(
    settings: &mut Settings,
    id: &str,
    legacy: Option<&str>,
    enabled: bool,
) {
    settings.disabled_devices.retain(|v| v != id);
    settings.enabled_legacy_devices.retain(|v| v != id);
    if enabled {
        if legacy.is_some_and(|old| settings.disabled_devices.iter().any(|v| v == old)) {
            settings.enabled_legacy_devices.push(id.to_owned());
        }
    } else {
        settings.disabled_devices.push(id.to_owned());
    }
}

pub(super) fn on_off(value: bool) -> &'static str {
    if value { "On" } else { "Off" }
}

pub(super) fn toggle_setting(settings: &mut Settings, index: usize) {
    if index != 20 {
        settings.server_confirmation_pending = false;
    }
    match index {
        20 => {
            if settings.allow_reviewed_server {
                settings.allow_reviewed_server = false;
                settings.server_confirmation_pending = false;
            } else if settings.server_confirmation_pending {
                settings.allow_reviewed_server = true;
                settings.server_confirmation_pending = false;
            } else {
                settings.server_confirmation_pending = true;
            }
            return;
        }
        7 => {
            settings.dimming_mode = (settings.dimming_mode + 1) % 3;
            return;
        }
        8 => {
            settings.min_brightness = if settings.min_brightness >= 0.99 {
                0.0
            } else {
                (settings.min_brightness + 0.05).min(1.0)
            };
            return;
        }
        21 => {
            settings.compass_priority = (settings.compass_priority + 1) % 4;
            return;
        }
        43 => {
            settings.low_health_threshold = if settings.low_health_threshold >= 20.0 {
                1.0
            } else {
                (settings.low_health_threshold + 1.0).min(20.0)
            };
            return;
        }
        44 => {
            settings.saturation_tint_strength = if settings.saturation_tint_strength >= 1.0 {
                0.0
            } else {
                (settings.saturation_tint_strength + 0.1).min(1.0)
            };
            return;
        }
        45 => {
            settings.experience_locator_mode = match settings.experience_locator_mode.as_str() {
                "auto" => "experience",
                "experience" => "locator",
                "locator" => "both",
                _ => "auto",
            }
            .into();
            return;
        }
        46 => {
            settings.locator_dot_size = if settings.locator_dot_size >= 5.0 {
                0.5
            } else {
                (settings.locator_dot_size + 0.5).min(5.0)
            };
            return;
        }
        _ => {}
    }
    let value = match index {
        0 => &mut settings.enabled,
        1 => &mut settings.experience_bar,
        2 => &mut settings.health_bar,
        3 => &mut settings.hunger_bar,
        4 => &mut settings.saturation_air_bar,
        5 => &mut settings.compass_effect,
        6 => &mut settings.biome_lighting,
        9 => &mut settings.weather_effects,
        10 => &mut settings.mirror_ram_rain_direction,
        11 => &mut settings.end_flash_effect,
        12 => &mut settings.fire_effect,
        13 => &mut settings.water_effect,
        14 => &mut settings.portal_effects,
        15 => &mut settings.low_health_warning,
        16 => &mut settings.movement_highlight,
        17 => &mut settings.chat_pulse,
        18 => &mut settings.always_show_compass,
        19 => &mut settings.locator_bar,
        22 => &mut settings.enable_corsair,
        23 => &mut settings.enable_asus,
        24 => &mut settings.enable_logitech,
        25 => &mut settings.enable_razer,
        26 => &mut settings.enable_wooting,
        27 => &mut settings.enable_steelseries,
        28 => &mut settings.enable_msi,
        29 => &mut settings.enable_novation,
        30 => &mut settings.enable_picopi,
        31 => &mut settings.enable_openrgb,
        32 => &mut settings.enable_yeelight,
        38 => &mut settings.notify_missing_server,
        39 => &mut settings.check_server_updates,
        40 => &mut settings.enable_absorption_overlay,
        41 => &mut settings.merge_saturation_into_hunger,
        42 => &mut settings.restore_lighting_outside_world,
        47 => &mut settings.notify_server_download_progress,
        _ => return,
    };
    *value = !*value;
}
