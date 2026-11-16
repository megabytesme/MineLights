#![allow(dead_code)]
use std::marker::PhantomData;

struct Game<'a>(PhantomData<&'a ()>);
impl Game<'_> {
    fn native_setting(&self, _: &str, _: &str) -> Option<String> {
        None
    }
    fn native_set_setting(&self, _: &str, _: &str, _: &str) {}
}
mod minelights_lang {
    pub fn setting_label(_: &str, index: usize) -> Option<&'static str> {
        match index {
            55 => Some("Ignore Carried Compasses"),
            56 => Some("Spin Compass Without a Target"),
            57 => Some("Compass Fallback Target"),
            58 => Some("World Spawn"),
            59 => Some("0, 0"),
            60 => Some("Player Spawn"),
            61 => Some("Lodestone Compass Shimmer"),
            _ => None,
        }
    }
    pub fn setting_label_or_english(language: &str, index: usize) -> Option<&'static str> {
        setting_label(language, index)
    }
}
mod minelights_server {
    pub const SERVER_VERSION: &str = "v2.4.1";
}
#[path = "../mixins/minelights/config.rs"]
mod config;

#[test]
fn old_default_on_setting_cannot_grant_permission() {
    let settings: config::Settings = serde_json::from_str(r#"{"auto_start_server":true}"#).unwrap();
    assert!(!settings.allow_reviewed_server);
    assert!(!settings.server_confirmation_pending);
}

#[test]
fn permission_requires_two_clicks_and_can_be_revoked() {
    let mut settings = config::Settings::default();
    assert!(!settings.allow_reviewed_server);
    config::toggle_setting(&mut settings, 20);
    assert!(!settings.allow_reviewed_server);
    assert!(settings.server_confirmation_pending);
    config::toggle_setting(&mut settings, 20);
    assert!(settings.allow_reviewed_server);
    assert!(!settings.server_confirmation_pending);
    config::toggle_setting(&mut settings, 20);
    assert!(!settings.allow_reviewed_server);
}

#[test]
fn confirmation_is_not_persisted_and_can_be_cancelled() {
    let mut settings = config::Settings::default();
    config::toggle_setting(&mut settings, 20);
    let json = serde_json::to_string(&settings).unwrap();
    let restored: config::Settings = serde_json::from_str(&json).unwrap();
    assert!(!restored.server_confirmation_pending);
    assert!(!restored.allow_reviewed_server);
    config::toggle_setting(&mut settings, 39);
    assert!(!settings.server_confirmation_pending);
    assert!(!settings.check_server_updates);
}

#[test]
fn server_download_notifications_are_enabled_by_default_and_can_be_toggled() {
    let mut settings = config::Settings::default();
    assert!(settings.notify_server_download_progress);
    config::toggle_setting(&mut settings, 47);
    assert!(!settings.notify_server_download_progress);
    config::toggle_setting(&mut settings, 47);
    assert!(settings.notify_server_download_progress);
}

#[test]
fn compass_settings_are_available_in_the_settings_screen_and_toggle() {
    let mut settings = config::Settings::default();
    assert!(!settings.ignore_inventory_compass);
    assert!(settings.spin_compass_without_target);
    assert_eq!(settings.compass_fallback_target, 0);
    assert!(settings.lodestone_compass_shimmer);

    let labels = config::settings_labels(settings.clone(), "en_us");
    assert_eq!(labels.len(), config::STATIC_SETTING_COUNT);
    assert!(labels[49].starts_with("Ignore Carried Compasses: Off"));
    assert!(labels[50].starts_with("Compass Fallback Target: World Spawn"));
    assert!(labels[51].starts_with("Spin Compass Without a Target: On"));
    assert!(labels[52].starts_with("Lodestone Compass Shimmer: On"));

    config::toggle_setting(&mut settings, 49);
    config::toggle_setting(&mut settings, 51);
    config::toggle_setting(&mut settings, 50);
    config::toggle_setting(&mut settings, 52);
    assert!(settings.ignore_inventory_compass);
    assert_eq!(settings.compass_fallback_target, 1);
    assert!(!settings.spin_compass_without_target);
    assert!(!settings.lodestone_compass_shimmer);
}

#[test]
fn compass_fallback_setting_cycles_all_choices() {
    let mut settings = config::Settings::default();
    for expected in [1, 2, 0] {
        config::toggle_setting(&mut settings, 50);
        assert_eq!(settings.compass_fallback_target, expected);
    }
}
