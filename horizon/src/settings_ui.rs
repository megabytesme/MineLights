use std::{
    fs::{self, File},
    io::Read,
    path::{Path, PathBuf},
    sync::{
        Mutex, OnceLock,
        atomic::{AtomicUsize, Ordering},
    },
};

use mod_api::{
    ClientHost, ConfigFileError, ConfigFiles, ConfigPath, Flow, Registrar, ScreenClosingEvent,
    ScreenKeyPressedPreEvent, ScreenMouseButtonPressedPreEvent, ScreenMouseScrolledPreEvent,
    ScreenRenderPostEvent, ScreenView, SettingChangedEvent, SettingSpec, SettingValue,
};

use super::{
    MOD_ID,
    minelights_bridge::{ACTION_CHECK_UPDATE, ACTION_REFRESH, BRIDGE, bridge_for},
    minelights_config::{Settings, device_disabled, on_off, set_device_enabled},
    minelights_server::SERVER_VERSION,
};

const LEGACY_PATH: [&str; 4] = [
    "horizon-mods",
    ".horizon-mod-data",
    "minelights",
    "settings.json",
];
const SERVER_AUTOSTART: &str = "server-autostart";
const CONTROL_ACTION: &str = "device-and-server-actions";
const MAX_SETTINGS_BYTES: u64 = 4 * 1024 * 1024;

static SETTINGS: OnceLock<Mutex<SettingsState>> = OnceLock::new();
static BINDINGS: OnceLock<Vec<Binding>> = OnceLock::new();
static PANEL: Mutex<Option<Panel>> = Mutex::new(None);
static VISIBLE_ACTIONS: Mutex<Vec<PanelAction>> = Mutex::new(Vec::new());
static FOCUSED_ROW: AtomicUsize = AtomicUsize::new(0);

struct SettingsState {
    current: Settings,
    retry_write: bool,
}

#[derive(Clone, Copy)]
enum Panel {
    Devices { offset: usize },
    Consent,
}

struct Binding {
    spec: SettingSpec,
    get: fn(&Settings) -> SettingValue,
    set: fn(&mut Settings, SettingValue),
}

fn bindings() -> &'static [Binding] {
    BINDINGS.get_or_init(|| {
        let defaults = Settings::default();
        macro_rules! binding {
            ($key:literal, $label:literal, $field:ident, bool) => {
                Binding {
                    spec: SettingSpec::toggle($key, $label, defaults.$field),
                    get: |s| SettingValue::Toggle(s.$field),
                    set: |s, v| if let SettingValue::Toggle(v) = v { s.$field = v },
                }
            };
            ($key:literal, $label:literal, $field:ident, range $min:literal, $max:literal, $step:literal) => {
                Binding {
                    spec: SettingSpec::range($key, $label, $min, $max, $step, f64::from(defaults.$field)),
                    get: |s| SettingValue::Range(f64::from(s.$field)),
                    set: |s, v| if let SettingValue::Range(v) = v { s.$field = v as f32 },
                }
            };
            ($key:literal, $label:literal, $field:ident, color) => {
                Binding {
                    spec: SettingSpec::color($key, $label, defaults.$field),
                    get: |s| SettingValue::Color(s.$field),
                    set: |s, v| if let SettingValue::Color(v) = v { s.$field = v },
                }
            };
            ($key:literal, $label:literal, $field:ident, choice [$($option:literal),+]) => {
                Binding {
                    spec: SettingSpec::choice($key, $label, [$($option),+], usize::from(defaults.$field)),
                    get: |s| SettingValue::Choice(usize::from(s.$field)),
                    set: |s, v| if let SettingValue::Choice(v) = v { s.$field = v as u8 },
                }
            };
        }
        const MODES: [&str; 4] = ["auto", "experience", "locator", "both"];
        vec![
            binding!("enabled", "minelights.horizon.setting.0", enabled, bool),
            binding!("experience-bar", "minelights.horizon.setting.1", experience_bar, bool),
            binding!("health-bar", "minelights.horizon.setting.2", health_bar, bool),
            binding!("hunger-bar", "minelights.horizon.setting.3", hunger_bar, bool),
            binding!("saturation-air-bar", "minelights.horizon.setting.4", saturation_air_bar, bool),
            binding!("compass-effect", "minelights.horizon.setting.5", compass_effect, bool),
            binding!("biome-lighting", "minelights.horizon.setting.6", biome_lighting, bool),
            binding!("dimming-mode", "minelights.horizon.setting.7", dimming_mode, choice [
                "minelights.horizon.setting.40", "minelights.horizon.setting.41", "minelights.horizon.setting.42"
            ]),
            binding!("min-brightness", "Minimum brightness", min_brightness, range 0.0, 1.0, 0.05),
            binding!("weather-effects", "minelights.horizon.setting.9", weather_effects, bool),
            binding!("mirror-ram-rain-direction", "minelights.horizon.setting.10", mirror_ram_rain_direction, bool),
            binding!("end-flash-effect", "minelights.horizon.setting.11", end_flash_effect, bool),
            binding!("fire-effect", "minelights.horizon.setting.12", fire_effect, bool),
            binding!("water-effect", "minelights.horizon.setting.13", water_effect, bool),
            binding!("portal-effects", "minelights.horizon.setting.14", portal_effects, bool),
            binding!("low-health-warning", "minelights.horizon.setting.15", low_health_warning, bool),
            binding!("movement-highlight", "minelights.horizon.setting.16", movement_highlight, bool),
            binding!("chat-pulse", "minelights.horizon.setting.17", chat_pulse, bool),
            binding!("always-show-compass", "minelights.horizon.setting.18", always_show_compass, bool),
            binding!("locator-bar", "minelights.horizon.setting.19", locator_bar, bool),
            binding!("compass-priority", "minelights.horizon.setting.21", compass_priority, choice [
                "minelights.horizon.setting.43", "minelights.horizon.setting.44",
                "minelights.horizon.setting.45", "minelights.horizon.setting.46"
            ]),
            binding!("compass-fallback-target", "minelights.horizon.setting.57", compass_fallback_target, choice [
                "minelights.horizon.setting.58", "minelights.horizon.setting.59", "minelights.horizon.setting.60"
            ]),
            binding!("ignore-inventory-compass", "minelights.horizon.setting.55", ignore_inventory_compass, bool),
            binding!("spin-compass-without-target", "minelights.horizon.setting.56", spin_compass_without_target, bool),
            binding!("lodestone-shimmer", "minelights.horizon.setting.61", lodestone_compass_shimmer, bool),
            binding!("corsair", "minelights.horizon.setting.22", enable_corsair, bool),
            binding!("asus", "minelights.horizon.setting.23", enable_asus, bool),
            binding!("logitech", "minelights.horizon.setting.24", enable_logitech, bool),
            binding!("razer", "minelights.horizon.setting.25", enable_razer, bool),
            binding!("wooting", "minelights.horizon.setting.26", enable_wooting, bool),
            binding!("steelseries", "minelights.horizon.setting.27", enable_steelseries, bool),
            binding!("msi", "minelights.horizon.setting.28", enable_msi, bool),
            binding!("novation", "minelights.horizon.setting.29", enable_novation, bool),
            binding!("picopi", "minelights.horizon.setting.30", enable_picopi, bool),
            binding!("openrgb", "OpenRGB direct (no MineLights auth/TLS)", enable_openrgb, bool),
            binding!("yeelight", "minelights.horizon.setting.32", enable_yeelight, bool),
            binding!("notify-missing-server", "Notify when MineLights Server is missing", notify_missing_server, bool),
            binding!("check-server-updates", "Check for MineLights Server updates", check_server_updates, bool),
            binding!("notify-server-download-progress", "minelights.horizon.setting.54", notify_server_download_progress, bool),
            binding!("absorption-overlay", "minelights.horizon.setting.47", enable_absorption_overlay, bool),
            binding!("merge-saturation-into-hunger", "minelights.horizon.setting.48", merge_saturation_into_hunger, bool),
            binding!("restore-lighting-outside-world", "minelights.horizon.setting.49", restore_lighting_outside_world, bool),
            binding!("low-health-threshold", "minelights.horizon.setting.50", low_health_threshold, range 1.0, 20.0, 1.0),
            binding!("saturation-tint-strength", "minelights.horizon.setting.51", saturation_tint_strength, range 0.0, 1.0, 0.1),
            binding!("locator-dot-size", "minelights.horizon.setting.53", locator_dot_size, range 0.5, 5.0, 0.5),
            Binding {
                spec: SettingSpec::choice("experience-locator-mode", "minelights.horizon.setting.52", ["Auto", "Experience", "Locator", "Both"], 0),
                get: |s| SettingValue::Choice(MODES.iter().position(|m| *m == s.experience_locator_mode).unwrap_or(0)),
                set: |s, v| if let SettingValue::Choice(v) = v {
                    s.experience_locator_mode = MODES[v].into();
                },
            },
            binding!("health-color", "Health color", health_color, color),
            binding!("health-dim-color", "Empty health color", health_dim_color, color),
            binding!("hunger-color", "Hunger color", hunger_color, color),
            binding!("hunger-dim-color", "Empty hunger color", hunger_dim_color, color),
            binding!("saturation-color", "Saturation color", saturation_color, color),
            binding!("saturation-dim-color", "Empty saturation color", saturation_dim_color, color),
            binding!("air-color", "Air color", air_color, color),
            binding!("air-dim-color", "Empty air color", air_dim_color, color),
            binding!("experience-color", "Experience color", experience_color, color),
            binding!("experience-dim-color", "Empty experience color", experience_dim_color, color),
            binding!("heartbeat-color", "Heartbeat color", heartbeat_color, color),
            binding!("absorption-color", "Absorption color", absorption_color, color),
            binding!("locator-bar-color", "Locator background color", locator_bar_color, color),
        ]
    })
}

pub(super) fn register(registrar: &mut dyn Registrar) {
    let reviewed_server = SettingSpec::toggle(
        SERVER_AUTOSTART, format!("Download and run reviewed Server {SERVER_VERSION}"), false,
    ).tooltip("MineLights will ask before enabling download and execution. Revoke permission here at any time.");
    let controls = SettingSpec::toggle(CONTROL_ACTION, "Open device and server controls", false)
        .tooltip(
            "View and disable detected devices, refresh connections, and check for server updates.",
        );
    for spec in bindings()
        .iter()
        .map(|binding| binding.spec.clone())
        .chain([reviewed_server, controls])
    {
        registrar
            .register_setting(spec)
            .expect("valid MineLights setting");
    }
}

pub(super) fn config_dir(host: &dyn ClientHost) -> Option<PathBuf> {
    let instance = host.instance_dir();
    (!instance.as_os_str().is_empty()).then(|| instance.join("config"))
}

fn settings_path() -> ConfigPath {
    ConfigPath::new("minelights/settings.json").expect("valid MineLights config path")
}

pub(super) fn write_settings(files: &dyn ConfigFiles, settings: &Settings) -> bool {
    serde_json::to_vec_pretty(settings)
        .is_ok_and(|json| files.write(&settings_path(), &json).is_ok())
}

pub(super) fn load_initial_settings(files: &dyn ConfigFiles, instance: &Path) -> Settings {
    match files.read(&settings_path()) {
        Ok(json) => decode_settings(&json).unwrap_or_default(),
        Err(ConfigFileError::NotFound) => {
            let Some(mut settings) = read_legacy(instance).and_then(|json| decode_settings(&json))
            else {
                return Settings::default();
            };
            // A failed migration is retried on the next launch. The old file is never modified.
            if !write_settings(files, &settings) {
                settings.allow_reviewed_server = false;
            }
            settings
        }
        Err(_) => Settings::default(),
    }
}

fn is_link(metadata: &fs::Metadata) -> bool {
    #[cfg(windows)]
    {
        use std::os::windows::fs::MetadataExt;
        // Windows directory junctions need not report themselves as symbolic links.
        metadata.file_attributes() & 0x400 != 0
    }
    #[cfg(not(windows))]
    {
        metadata.file_type().is_symlink()
    }
}

fn read_legacy(instance: &Path) -> Option<Vec<u8>> {
    if instance.as_os_str().is_empty() {
        return None;
    }
    let mut path = instance.to_owned();
    for part in LEGACY_PATH {
        path.push(part);
        if is_link(&fs::symlink_metadata(&path).ok()?) {
            return None;
        }
    }
    let file = File::open(path).ok()?;
    let metadata = file.metadata().ok()?;
    if !metadata.is_file() || metadata.len() > MAX_SETTINGS_BYTES {
        return None;
    }
    let mut bytes = Vec::new();
    file.take(MAX_SETTINGS_BYTES + 1)
        .read_to_end(&mut bytes)
        .ok()?;
    (bytes.len() as u64 <= MAX_SETTINGS_BYTES).then_some(bytes)
}

fn decode_settings(bytes: &[u8]) -> Option<Settings> {
    use serde_json::Value;

    let input = serde_json::from_slice::<Value>(bytes).ok()?;
    let fields = input.as_object()?;
    if let Ok(settings) = serde_json::from_value(input.clone()) {
        return Some(settings);
    }
    // Keep usable preferences from a partially malformed object. Require fresh approval
    // when the file is damaged, and never interpret an old autostart flag as approval.
    let defaults = serde_json::to_value(Settings::default()).ok()?;
    let mut recovered = serde_json::Map::new();
    for (key, value) in fields {
        if key == "allow_reviewed_server" || !defaults.as_object()?.contains_key(key) {
            continue;
        }
        let field = Value::Object([(key.clone(), value.clone())].into_iter().collect());
        if serde_json::from_value::<Settings>(field).is_ok() {
            recovered.insert(key.clone(), value.clone());
        }
    }
    serde_json::from_value(Value::Object(recovered)).ok()
}

pub(super) fn settings_for(host: &dyn ClientHost) -> Settings {
    SETTINGS
        .get_or_init(|| {
            let files = host.config_files();
            let mut settings = load_initial_settings(files, &host.instance_dir());
            // The SDK stores a UI change before notifying us. If the game
            // exited or the config write failed in between, a recorded revoke
            // still takes precedence. The widget can never grant permission.
            let revoked = apply_saved_revocation(
                &mut settings,
                host.setting(MOD_ID, SERVER_AUTOSTART).as_deref(),
            );
            let retry_write = revoked && !write_settings(files, &settings);
            sync_widgets(host, &settings);
            Mutex::new(SettingsState {
                current: settings,
                retry_write,
            })
        })
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner())
        .current
        .clone()
}

pub(super) fn apply_saved_revocation(settings: &mut Settings, stored: Option<&str>) -> bool {
    if settings.allow_reviewed_server && stored == Some("false") {
        settings.revoke_reviewed_server();
        return true;
    }
    false
}

fn sync_widgets(host: &dyn ClientHost, settings: &Settings) {
    for binding in bindings() {
        if let Some(value) = binding.spec.store((binding.get)(settings)) {
            let default = binding.spec.store(binding.spec.default_value());
            sync_widget(host, &binding.spec.key, &value, default.as_deref());
        }
    }
    sync_widget(
        host,
        SERVER_AUTOSTART,
        if settings.allow_reviewed_server {
            "true"
        } else {
            "false"
        },
        Some("false"),
    );
}

fn sync_widget(host: &dyn ClientHost, key: &str, value: &str, default: Option<&str>) {
    if host.setting(MOD_ID, key).as_deref().or(default) != Some(value) {
        host.set_setting(MOD_ID, key, value);
    }
}

pub(super) fn apply_widget_setting(settings: &mut Settings, key: &str, raw: Option<&str>) -> bool {
    let Some(binding) = bindings().iter().find(|binding| binding.spec.key == key) else {
        return false;
    };
    (binding.set)(settings, binding.spec.read(raw));
    settings.server_confirmation_pending = false;
    true
}

#[cfg(test)]
pub(super) fn widget_spec(key: &str) -> Option<&'static SettingSpec> {
    bindings()
        .iter()
        .find(|binding| binding.spec.key == key)
        .map(|binding| &binding.spec)
}

pub(super) fn persist_settings(host: &dyn ClientHost, settings: Settings) -> bool {
    let Some(state) = SETTINGS.get() else {
        return false;
    };
    let mut state = state
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner());
    let mut saved = settings.clone();
    saved.server_confirmation_pending = state.current.server_confirmation_pending;
    if (saved != state.current || state.retry_write)
        && !write_settings(host.config_files(), &settings)
    {
        // Revocation takes effect immediately even if a write fails. The caller
        // reports that the on-disk settings still need attention.
        if state.current.allow_reviewed_server && !settings.allow_reviewed_server {
            if let Some(bridge) = BRIDGE.get() {
                bridge
                    .auto_start
                    .store(false, std::sync::atomic::Ordering::Release);
                if let Ok(mut active_settings) = bridge.settings.write() {
                    active_settings.allow_reviewed_server = false;
                }
            }
            state.current.allow_reviewed_server = false;
            state.retry_write = true;
            host.set_setting(MOD_ID, SERVER_AUTOSTART, "false");
        }
        sync_widgets(host, &state.current);
        return false;
    }
    state.retry_write = false;
    sync_widgets(host, &settings);
    state.current = settings;
    if BRIDGE.get().is_some() {
        bridge_for(config_dir(host), &state.current);
    }
    true
}

pub(super) fn changed(host: &mut dyn ClientHost, event: &SettingChangedEvent<'_>) {
    if event.mod_id != MOD_ID || !host.on(MOD_ID) {
        return;
    }
    // Read the event value first so a first-use migration cannot overwrite the user's edit.
    let stored = host.setting(MOD_ID, event.key);
    let mut settings = settings_for(host);
    if event.key == CONTROL_ACTION {
        sync_widget(host, CONTROL_ACTION, "false", Some("false"));
        open_panel(Panel::Devices { offset: 0 }, 0);
        return;
    }
    if event.key == SERVER_AUTOSTART {
        if stored.as_deref() == Some("true") {
            if settings.request_reviewed_server() {
                open_panel(Panel::Consent, 1);
            }
        } else {
            settings.revoke_reviewed_server();
        }
    } else if !apply_widget_setting(&mut settings, event.key, stored.as_deref()) {
        return;
    }
    if !persist_settings(host, settings) {
        let _ = host.chat_line(mod_api::Text::literal(
            "[MineLights] Could not save settings.",
        ));
    }
}

struct PanelRow {
    title: String,
    action: PanelAction,
}

#[derive(Clone)]
enum PanelAction {
    Close,
    Refresh,
    CheckUpdate,
    Restart,
    ElevatedRestart,
    ClearDisabled,
    ToggleDevice(String, Option<String>),
    CancelConsent,
    ConfirmConsent,
}

fn open_panel(panel: Panel, focus: usize) {
    VISIBLE_ACTIONS
        .lock()
        .unwrap_or_else(|p| p.into_inner())
        .clear();
    FOCUSED_ROW.store(focus, Ordering::Relaxed);
    *PANEL.lock().unwrap_or_else(|p| p.into_inner()) = Some(panel);
}

fn panel_title(host: &dyn ClientHost, index: usize, fallback: &str) -> String {
    let language = host
        .client_session()
        .client_language()
        .unwrap_or_else(super::minelights_player_data::client_language);
    super::minelights_lang::setting_label(&language, index)
        .unwrap_or(fallback)
        .to_owned()
}

fn rows(host: &dyn ClientHost, panel: Panel) -> Vec<PanelRow> {
    use PanelAction::*;

    match panel {
        Panel::Consent => vec![
            PanelRow {
                title: "Confirm download and execution".into(),
                action: ConfirmConsent,
            },
            PanelRow {
                title: "Cancel".into(),
                action: CancelConsent,
            },
        ],
        Panel::Devices { .. } => {
            let settings = settings_for(host);
            let layouts = BRIDGE
                .get()
                .and_then(|bridge| bridge.layouts.read().ok().map(|layouts| layouts.clone()))
                .unwrap_or_default();
            let mut rows = vec![
                PanelRow {
                    title: panel_title(host, 33, "Refresh devices"),
                    action: Refresh,
                },
                PanelRow {
                    title: "Check for MineLights Server updates".into(),
                    action: CheckUpdate,
                },
                PanelRow {
                    title: "MineLights Server tray menu: restart".into(),
                    action: Restart,
                },
                PanelRow {
                    title: "MineLights Server tray menu: elevated restart".into(),
                    action: ElevatedRestart,
                },
                PanelRow {
                    title: panel_title(host, 37, "Clear disabled devices"),
                    action: ClearDisabled,
                },
            ];
            rows.extend(layouts.into_iter().map(|layout| {
                let disabled =
                    device_disabled(&settings, &layout.unique_id, layout.legacy_id.as_deref());
                PanelRow {
                    title: format!(
                        "{} ({}) · {}",
                        layout.display_name,
                        layout.integration,
                        on_off(!disabled)
                    ),
                    action: ToggleDevice(layout.unique_id, layout.legacy_id),
                }
            }));
            rows
        }
    }
}

fn shown_panel(screen: &ScreenView<'_>) -> Option<Panel> {
    (screen.id == "options")
        .then(|| {
            *PANEL
                .lock()
                .unwrap_or_else(|poisoned| poisoned.into_inner())
        })
        .flatten()
}

const ROW_HEIGHT: f32 = 19.0;

fn panel_geometry(screen: &ScreenView<'_>, panel: Panel) -> ([f32; 4], f32, usize) {
    let [x, y, width, height] = screen.rect;
    let width_inner = (width - 12.0).clamp(80.0, 360.0);
    let height_inner = (height - 12.0).clamp(75.0, 340.0);
    let left = x + (width - width_inner) / 2.0;
    let top = y + (height - height_inner) / 2.0;
    let row_start = match panel {
        Panel::Consent => 70.0,
        Panel::Devices { .. } => 38.0,
    };
    let count = if matches!(panel, Panel::Consent) {
        2
    } else {
        ((height_inner - row_start - 28.0) / ROW_HEIGHT)
            .floor()
            .max(1.0) as usize
    };
    ([left, top, width_inner, height_inner], row_start, count)
}

fn scroll_offset(panel: Panel, total: usize, count: usize) -> usize {
    match panel {
        Panel::Consent => 0,
        Panel::Devices { offset } => offset.min(total.saturating_sub(count)),
    }
}

pub(super) fn screen_render(host: &dyn ClientHost, event: &mut ScreenRenderPostEvent<'_>) {
    let Some(panel) = shown_panel(event.screen) else {
        return;
    };
    let rows = rows(host, panel);
    let ([x, y, w, h], start, count) = panel_geometry(event.screen, panel);
    let offset = scroll_offset(panel, rows.len(), count);
    let draw = &mut *event.draw;
    draw.fill(event.screen.rect, 0xBC000000);
    draw.fill([x, y, w, h], 0xFF20242A);
    match panel {
        Panel::Devices { .. } => {
            draw.text(
                "MineLights devices and server",
                [x + 10.0, y + 9.0],
                0xFFFFFFFF,
                true,
            );
            draw.text(
                "Configure keys and overrides with /minelights set",
                [x + 10.0, y + 23.0],
                0xFFD1D5DB,
                false,
            );
        }
        Panel::Consent => {
            draw.text(
                "Download and run the reviewed server?",
                [x + 10.0, y + 9.0],
                0xFFFFFFFF,
                true,
            );
            draw.text(
                &format!("Version {SERVER_VERSION} is downloaded from GitHub."),
                [x + 10.0, y + 25.0],
                0xFFD1D5DB,
                false,
            );
            draw.text(
                "MineLights checks the pinned SHA-256 before execution.",
                [x + 10.0, y + 38.0],
                0xFFD1D5DB,
                false,
            );
            draw.text(
                "The executable runs with your account permissions.",
                [x + 10.0, y + 51.0],
                0xFFD1D5DB,
                false,
            );
        }
    }
    let _ = draw.push_clip([x + 5.0, y + start, w - 10.0, count as f32 * ROW_HEIGHT]);
    let visible = rows.iter().skip(offset).take(count);
    *VISIBLE_ACTIONS.lock().unwrap_or_else(|p| p.into_inner()) =
        visible.clone().map(|row| row.action.clone()).collect();
    for (index, row) in visible.enumerate() {
        let rect = [
            x + 7.0,
            y + start + index as f32 * ROW_HEIGHT,
            w - 14.0,
            ROW_HEIGHT - 2.0,
        ];
        draw.fill(
            rect,
            if index == FOCUSED_ROW.load(Ordering::Relaxed) || inside(rect, event.screen.mouse) {
                0xFF526173
            } else {
                0xFF343D48
            },
        );
        draw.text(
            &row.title,
            [rect[0] + 5.0, rect[1] + 4.0],
            0xFFFFFFFF,
            false,
        );
    }
    let _ = draw.pop_clip();
    draw.text("Back", [x + 10.0, y + h - 16.0], 0xFFFFFFFF, true);
}

fn inside([x, y, w, h]: [f32; 4], [mx, my]: [f32; 2]) -> bool {
    mx >= x && mx < x + w && my >= y && my < y + h
}

pub(super) fn screen_click(
    host: &dyn ClientHost,
    event: &mut ScreenMouseButtonPressedPreEvent<'_>,
) -> Flow {
    let Some(panel) = shown_panel(event.screen) else {
        return Flow::Continue;
    };
    if event.button != 0 {
        return Flow::Cancel;
    }
    let ([x, y, w, h], start, count) = panel_geometry(event.screen, panel);
    let [mx, my] = event.screen.mouse;
    if inside([x + 5.0, y + h - 22.0, w - 10.0, 20.0], [mx, my]) {
        let action = if matches!(panel, Panel::Consent) {
            PanelAction::CancelConsent
        } else {
            PanelAction::Close
        };
        activate(host, action);
    } else if inside(
        [x + 7.0, y + start, w - 14.0, count as f32 * ROW_HEIGHT],
        [mx, my],
    ) {
        let index = ((my - y - start) / ROW_HEIGHT) as usize;
        if let Some(action) = visible_action(index) {
            activate(host, action);
        }
    }
    Flow::Cancel
}

pub(super) fn screen_scroll(
    host: &dyn ClientHost,
    event: &mut ScreenMouseScrolledPreEvent<'_>,
) -> Flow {
    let Some(panel) = shown_panel(event.screen) else {
        return Flow::Continue;
    };
    if let Panel::Devices { .. } = panel {
        let total = rows(host, panel).len();
        let (_, _, count) = panel_geometry(event.screen, panel);
        let current = scroll_offset(panel, total, count);
        let offset = if event.scroll[1] > 0.0 {
            current.saturating_sub(1)
        } else if event.scroll[1] < 0.0 {
            current.saturating_add(1).min(total.saturating_sub(count))
        } else {
            current
        };
        *PANEL
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner()) = Some(Panel::Devices { offset });
    }
    Flow::Cancel
}

pub(super) fn screen_closing(host: &dyn ClientHost, event: &mut ScreenClosingEvent<'_>) {
    if let Some(panel) = shown_panel(event.screen) {
        let action = if matches!(panel, Panel::Consent) {
            PanelAction::CancelConsent
        } else {
            PanelAction::Close
        };
        activate(host, action);
    }
}

fn visible_action(index: usize) -> Option<PanelAction> {
    VISIBLE_ACTIONS
        .lock()
        .unwrap_or_else(|p| p.into_inner())
        .get(index)
        .cloned()
}

pub(super) fn screen_key(host: &dyn ClientHost, event: &mut ScreenKeyPressedPreEvent<'_>) -> Flow {
    let Some(panel) = shown_panel(event.screen) else {
        return Flow::Continue;
    };
    let focus = FOCUSED_ROW.load(Ordering::Relaxed);
    match event.key {
        "key.keyboard.escape" => activate(
            host,
            if matches!(panel, Panel::Consent) {
                PanelAction::CancelConsent
            } else {
                PanelAction::Close
            },
        ),
        "key.keyboard.enter" | "key.keyboard.keypad.enter" | "key.keyboard.space"
            if !event.repeat =>
        {
            if let Some(action) = visible_action(focus) {
                activate(host, action);
            }
        }
        "key.keyboard.up" | "key.keyboard.down" | "key.keyboard.tab" => {
            let previous = event.key == "key.keyboard.up"
                || event.key == "key.keyboard.tab" && event.modifiers & 1 != 0;
            let rows = rows(host, panel).len();
            if rows != 0 {
                let (_, _, count) = panel_geometry(event.screen, panel);
                let offset = scroll_offset(panel, rows, count);
                let index = offset.saturating_add(focus).min(rows - 1);
                let next = if previous {
                    index.checked_sub(1).unwrap_or(rows - 1)
                } else {
                    (index + 1) % rows
                };
                let new_offset = if next < offset {
                    next
                } else {
                    offset.max((next + 1).saturating_sub(count))
                };
                if matches!(panel, Panel::Devices { .. }) {
                    *PANEL.lock().unwrap_or_else(|p| p.into_inner()) =
                        Some(Panel::Devices { offset: new_offset });
                }
                FOCUSED_ROW.store(next - new_offset, Ordering::Relaxed);
            }
        }
        _ => {}
    }
    Flow::Cancel
}

fn activate(host: &dyn ClientHost, action: PanelAction) {
    use super::minelights_bridge::{ACTION_RESTART, ACTION_RESTART_ADMIN};
    use PanelAction::*;

    let mut settings = settings_for(host);
    match action {
        Close => {}
        CancelConsent => {
            settings.server_confirmation_pending = false;
            persist_settings(host, settings);
        }
        ConfirmConsent => {
            if settings.confirm_reviewed_server() {
                if !persist_settings(host, settings) {
                    return;
                }
            }
        }
        ToggleDevice(id, legacy) => {
            let disabled = device_disabled(&settings, &id, legacy.as_deref());
            set_device_enabled(&mut settings, &id, legacy.as_deref(), disabled);
            persist_settings(host, settings);
            return;
        }
        ClearDisabled => {
            settings.disabled_devices.clear();
            settings.enabled_legacy_devices.clear();
            if persist_settings(host, settings.clone()) {
                bridge_for(config_dir(host), &settings)
                    .actions
                    .fetch_or(ACTION_REFRESH, Ordering::Release);
            }
            return;
        }
        Refresh | CheckUpdate | Restart | ElevatedRestart => {
            let requested = match action {
                Refresh => ACTION_REFRESH,
                CheckUpdate => ACTION_CHECK_UPDATE,
                Restart => ACTION_RESTART,
                ElevatedRestart => ACTION_RESTART_ADMIN,
                _ => unreachable!(),
            };
            bridge_for(config_dir(host), &settings)
                .actions
                .fetch_or(requested, Ordering::Release);
            return;
        }
    }
    *PANEL
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner()) = None;
}
