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
    // A new field deliberately does not inherit the old, default-on setting.
    pub(super) allow_reviewed_server: bool,
    pub(super) notify_missing_server: bool,
    pub(super) check_server_updates: bool,
    pub(super) allow_legacy_v1_fallback: bool,
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
            allow_reviewed_server: false,
            notify_missing_server: true,
            check_server_updates: true,
            allow_legacy_v1_fallback: false,
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
        }
    }
}

fn default_openrgb_endpoints() -> Vec<OpenRgbEndpoint> {
    vec![OpenRgbEndpoint::default()]
}

pub(super) const STATIC_SETTING_COUNT: usize = 41;

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
        format!("Experience bar: {}", on_off(settings.experience_bar)),
        format!("Health bar: {}", on_off(settings.health_bar)),
        format!("Hunger bar: {}", on_off(settings.hunger_bar)),
        format!(
            "Saturation and air bar: {}",
            on_off(settings.saturation_air_bar)
        ),
        format!("Compass effect: {}", on_off(settings.compass_effect)),
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
        format!("Waypoint locator bar: {}", on_off(settings.locator_bar)),
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
            "Allow unauthenticated local V1 fallback: {}",
            on_off(settings.allow_legacy_v1_fallback)
        ),
    ];
    for (index, label) in labels.iter_mut().enumerate() {
        // The old translations describe different, less restrictive behaviour.
        if index == 20 || index == 31 || (34..=36).contains(&index) || index >= 38 {
            continue;
        }
        if let Some(translated) = super::minelights_lang::setting_label(language, index) {
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
        40 => &mut settings.allow_legacy_v1_fallback,
        _ => return,
    };
    *value = !*value;
}
