use crate::minelights_config::Settings;
use crate::minelights_settings::{apply_saved_revocation, load_initial_settings, write_settings};
use mod_api::{ConfigEntry, ConfigFileError, ConfigFiles, ConfigPath, ConfigStat};
use std::cell::{Cell, RefCell};
use std::path::PathBuf;
use std::sync::atomic::{AtomicUsize, Ordering};

#[derive(Default)]
struct Config {
    bytes: RefCell<Option<Vec<u8>>>,
    fail_writes: Cell<bool>,
    writes: Cell<usize>,
}

impl ConfigFiles for Config {
    fn read(&self, path: &ConfigPath) -> Result<Vec<u8>, ConfigFileError> {
        assert_eq!(path.as_str(), "minelights/settings.json");
        self.bytes.borrow().clone().ok_or(ConfigFileError::NotFound)
    }

    fn write(&self, path: &ConfigPath, bytes: &[u8]) -> Result<(), ConfigFileError> {
        assert_eq!(path.as_str(), "minelights/settings.json");
        if self.fail_writes.get() {
            return Err(ConfigFileError::Io("fixture rejected the write".into()));
        }
        self.bytes.replace(Some(bytes.to_vec()));
        self.writes.set(self.writes.get() + 1);
        Ok(())
    }

    fn list(
        &self,
        _: &ConfigPath,
        _: Option<&str>,
        _: u32,
    ) -> Result<Vec<ConfigEntry>, ConfigFileError> {
        Ok(Vec::new())
    }

    fn stat(&self, _: &ConfigPath) -> Result<Option<ConfigStat>, ConfigFileError> {
        Ok(None)
    }
}

struct Instance {
    root: PathBuf,
    legacy: PathBuf,
}

impl Instance {
    fn new() -> Self {
        static NEXT_ID: AtomicUsize = AtomicUsize::new(0);
        let root = std::env::temp_dir().join(format!(
            "minelights-settings-test-{}-{}",
            std::process::id(),
            NEXT_ID.fetch_add(1, Ordering::Relaxed),
        ));
        let legacy = root.join("horizon-mods/.horizon-mod-data/minelights/settings.json");
        std::fs::create_dir(&root).unwrap();
        std::fs::create_dir_all(legacy.parent().unwrap()).unwrap();
        Self { root, legacy }
    }
}

impl Drop for Instance {
    fn drop(&mut self) {
        let _ = std::fs::remove_file(&self.legacy);
        for path in self.legacy.ancestors().skip(1).take(4) {
            let _ = std::fs::remove_dir(path);
        }
    }
}

#[test]
fn first_launch_uses_defaults_without_granting_permissions() {
    let instance = Instance::new();
    let files = Config::default();
    let settings = load_initial_settings(&files, &instance.root);
    assert_eq!(settings, Settings::default());
    assert!(!instance.legacy.exists());
}

#[test]
fn migration_preserves_selected_values_and_runs_once() {
    let instance = Instance::new();
    let previous = br#"{"enabled":false,"low_health_threshold":7.5,"health_color":65535,
        "health_bar_keys":["F8","F9"],"led_overrides":["Example|2=F8"],
        "openrgb_endpoints":[{"host":"192.0.2.10","port":6743}],
        "disabled_devices":["device-id"],"enabled_legacy_devices":["old-device-id"],
        "compass_fallback_target":2,"server_confirmation_pending":true,
        "auto_start_server":true,"allow_legacy_v1_fallback":true}"#;
    std::fs::write(&instance.legacy, previous).unwrap();
    let files = Config::default();
    let settings = load_initial_settings(&files, &instance.root);
    assert!(!settings.enabled);
    assert_eq!(settings.low_health_threshold, 7.5);
    assert_eq!(settings.health_color, 65_535);
    assert_eq!(settings.health_bar_keys, ["F8", "F9"]);
    assert_eq!(settings.led_overrides, ["Example|2=F8"]);
    assert_eq!(settings.openrgb_endpoints[0].host, "192.0.2.10");
    assert_eq!(settings.openrgb_endpoints[0].port, 6743);
    assert_eq!(settings.disabled_devices, ["device-id"]);
    assert_eq!(settings.enabled_legacy_devices, ["old-device-id"]);
    assert_eq!(settings.compass_fallback_target, 2);
    assert!(settings.notify_server_download_progress);
    assert!(!settings.allow_reviewed_server);
    assert!(!settings.server_confirmation_pending);
    assert_eq!(files.writes.get(), 1);
    let persisted = files.bytes.borrow().clone().unwrap();
    let value: serde_json::Value = serde_json::from_slice(&persisted).unwrap();
    assert!(value.get("auto_start_server").is_none());
    assert!(value.get("allow_legacy_v1_fallback").is_none());
    assert!(value.get("server_confirmation_pending").is_none());
    assert_eq!(std::fs::read(&instance.legacy).unwrap(), previous);

    let repeated = load_initial_settings(&files, &instance.root);
    assert_eq!(repeated, settings);
    assert_eq!(files.writes.get(), 1);
    assert_eq!(*files.bytes.borrow(), Some(persisted));
}

#[test]
fn new_preferences_take_precedence_after_restart() {
    let instance = Instance::new();
    std::fs::write(&instance.legacy, br#"{"enabled":false}"#).unwrap();
    let files = Config::default();
    let mut settings = load_initial_settings(&files, &instance.root);
    assert!(!settings.enabled);
    settings.enabled = true;
    settings.allow_reviewed_server = true;
    assert!(write_settings(&files, &settings));
    let restored = load_initial_settings(&files, &instance.root);
    assert!(restored.enabled);
    assert!(restored.allow_reviewed_server);
    assert_eq!(files.writes.get(), 2);
    assert_eq!(
        std::fs::read(&instance.legacy).unwrap(),
        br#"{"enabled":false}"#
    );
}

#[test]
fn malformed_legacy_data_keeps_defaults_and_the_original_file() {
    let instance = Instance::new();
    let previous = b"{ malformed legacy JSON";
    std::fs::write(&instance.legacy, previous).unwrap();
    let files = Config::default();
    let settings = load_initial_settings(&files, &instance.root);
    assert!(settings.enabled);
    assert!(!settings.allow_reviewed_server);
    assert_eq!(files.writes.get(), 0);
    assert_eq!(std::fs::read(&instance.legacy).unwrap(), previous);
    assert!(write_settings(&files, &settings));
    assert_eq!(files.writes.get(), 1);
}

#[test]
fn partially_malformed_preferences_are_recovered_without_granting_consent() {
    let instance = Instance::new();
    let previous = br#"{"enabled":false,"health_color":65280,
        "min_brightness":"invalid","allow_reviewed_server":true}"#;
    std::fs::write(&instance.legacy, previous).unwrap();
    let files = Config::default();
    let repaired = load_initial_settings(&files, &instance.root);
    assert!(!repaired.enabled);
    assert_eq!(repaired.health_color, 65_280);
    assert_eq!(repaired.min_brightness, Settings::default().min_brightness);
    assert!(!repaired.allow_reviewed_server);
    assert_eq!(files.writes.get(), 1);
    assert_eq!(std::fs::read(&instance.legacy).unwrap(), previous);
    assert!(!load_initial_settings(&files, &instance.root).allow_reviewed_server);
}

#[test]
fn malformed_newer_data_does_not_reactivate_stale_legacy_consent() {
    let instance = Instance::new();
    let previous = br#"{"enabled":false,"allow_reviewed_server":true}"#;
    std::fs::write(&instance.legacy, previous).unwrap();
    let broken = b"{ malformed SDK JSON".to_vec();
    let files = Config::default();
    files.bytes.replace(Some(broken.clone()));
    let settings = load_initial_settings(&files, &instance.root);
    assert!(settings.enabled);
    assert!(!settings.allow_reviewed_server);
    assert_eq!(*files.bytes.borrow(), Some(broken));
    assert_eq!(files.writes.get(), 0);
    assert_eq!(std::fs::read(&instance.legacy).unwrap(), previous);
}

#[test]
fn interrupted_migration_uses_legacy_preferences_and_retries() {
    let instance = Instance::new();
    let previous = br#"{"enabled":false,"allow_reviewed_server":true}"#;
    std::fs::write(&instance.legacy, previous).unwrap();
    let files = Config::default();
    files.fail_writes.set(true);
    assert!(!load_initial_settings(&files, &instance.root).enabled);
    assert_eq!(files.writes.get(), 0);
    assert_eq!(std::fs::read(&instance.legacy).unwrap(), previous);
    files.fail_writes.set(false);
    let recovered = load_initial_settings(&files, &instance.root);
    assert!(!recovered.enabled);
    assert!(recovered.allow_reviewed_server);
    assert_eq!(files.writes.get(), 1);
}
#[test]
fn interrupted_revocation_takes_priority_and_widget_cannot_grant_consent() {
    let mut settings = Settings::default();
    assert!(!apply_saved_revocation(&mut settings, Some("true")));
    assert!(!settings.allow_reviewed_server);
    assert!(settings.request_reviewed_server());
    assert!(settings.confirm_reviewed_server());
    assert!(!apply_saved_revocation(&mut settings, None));
    assert!(settings.allow_reviewed_server);
    assert!(apply_saved_revocation(&mut settings, Some("false")));
    assert!(!settings.allow_reviewed_server);
    assert!(!settings.server_confirmation_pending);
}
