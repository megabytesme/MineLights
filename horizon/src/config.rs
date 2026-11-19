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

#[derive(Clone, Debug, PartialEq, serde::Serialize, serde::Deserialize)]
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
    pub(super) lodestone_compass_shimmer: bool,
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
    pub(super) compass_fallback_target: u8,
    pub(super) ignore_inventory_compass: bool,
    pub(super) spin_compass_without_target: bool,
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
            lodestone_compass_shimmer: true,
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
            compass_fallback_target: 0,
            ignore_inventory_compass: false,
            spin_compass_without_target: true,
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

impl Settings {
    pub(super) fn request_reviewed_server(&mut self) -> bool {
        self.server_confirmation_pending = !self.allow_reviewed_server;
        self.server_confirmation_pending
    }

    pub(super) fn confirm_reviewed_server(&mut self) -> bool {
        if !self.server_confirmation_pending {
            return false;
        }
        self.server_confirmation_pending = false;
        self.allow_reviewed_server = true;
        true
    }

    pub(super) fn revoke_reviewed_server(&mut self) {
        self.server_confirmation_pending = false;
        self.allow_reviewed_server = false;
    }
}

fn default_openrgb_endpoints() -> Vec<OpenRgbEndpoint> {
    vec![OpenRgbEndpoint::default()]
}

fn keys(values: &[&str]) -> Vec<String> {
    values.iter().map(|value| (*value).to_owned()).collect()
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
