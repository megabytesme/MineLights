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
    pub fn setting_label(_: &str, _: usize) -> Option<&'static str> {
        None
    }
    pub fn setting_label_or_english(_: &str, _: usize) -> Option<&'static str> {
        None
    }
}
mod minelights_server {
    pub const SERVER_VERSION: &str = "v2.4.0";
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
