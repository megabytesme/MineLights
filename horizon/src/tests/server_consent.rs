use crate::minelights_config::Settings;
use crate::minelights_settings::{apply_widget_setting, widget_spec};
use mod_api::{SettingKind, SettingValue};

fn change_widget(settings: &mut Settings, key: &str, value: SettingValue) {
    let stored = widget_spec(key).unwrap().store(value).unwrap();
    assert!(apply_widget_setting(settings, key, Some(&stored)));
}

#[test]
fn old_default_on_setting_cannot_grant_permission() {
    let settings: Settings = serde_json::from_str(r#"{"auto_start_server":true}"#).unwrap();
    assert!(!settings.allow_reviewed_server);
    assert!(!settings.server_confirmation_pending);
}

#[test]
fn permission_requires_an_explicit_confirmation_and_can_be_revoked() {
    let mut settings = Settings::default();
    assert!(!settings.allow_reviewed_server);
    assert!(!settings.confirm_reviewed_server());
    assert!(!apply_widget_setting(
        &mut settings,
        "server-autostart",
        Some("true")
    ));
    assert!(settings.request_reviewed_server());
    assert!(settings.request_reviewed_server());
    assert!(!settings.allow_reviewed_server);
    assert!(settings.server_confirmation_pending);
    assert!(settings.confirm_reviewed_server());
    assert!(settings.allow_reviewed_server);
    assert!(!settings.server_confirmation_pending);
    assert!(!settings.request_reviewed_server());
    settings.revoke_reviewed_server();
    assert!(!settings.allow_reviewed_server);
    assert!(!settings.confirm_reviewed_server());
}

#[test]
fn confirmation_is_not_persisted_and_can_be_cancelled() {
    let mut settings = Settings::default();
    assert!(settings.request_reviewed_server());
    let json = serde_json::to_string(&settings).unwrap();
    let mut restored: Settings = serde_json::from_str(&json).unwrap();
    assert!(!restored.server_confirmation_pending);
    assert!(!restored.allow_reviewed_server);
    assert!(!restored.confirm_reviewed_server());
    change_widget(
        &mut settings,
        "check-server-updates",
        SettingValue::Toggle(false),
    );
    assert!(!settings.server_confirmation_pending);
    assert!(!settings.check_server_updates);
    assert!(!settings.confirm_reviewed_server());
}

#[test]
fn server_download_notifications_are_enabled_by_default_and_can_be_toggled() {
    let mut settings = Settings::default();
    let key = "notify-server-download-progress";
    assert!(settings.notify_server_download_progress);
    assert_eq!(
        widget_spec(key).unwrap().default_value(),
        SettingValue::Toggle(true)
    );
    change_widget(&mut settings, key, SettingValue::Toggle(false));
    assert!(!settings.notify_server_download_progress);
    change_widget(&mut settings, key, SettingValue::Toggle(true));
    assert!(settings.notify_server_download_progress);
}

#[test]
fn compass_settings_are_available_in_the_settings_screen_and_toggle() {
    let mut settings = Settings::default();
    assert!(!settings.ignore_inventory_compass);
    assert!(settings.spin_compass_without_target);
    assert_eq!(settings.compass_fallback_target, 0);
    assert!(settings.lodestone_compass_shimmer);

    let labels: serde_json::Value =
        serde_json::from_str(include_str!("../../assets/minelights/lang/en_us.json")).unwrap();
    for (key, label, default) in [
        (
            "ignore-inventory-compass",
            "Ignore Carried Compasses",
            SettingValue::Toggle(false),
        ),
        (
            "compass-fallback-target",
            "Compass Fallback Target",
            SettingValue::Choice(0),
        ),
        (
            "spin-compass-without-target",
            "Spin Compass Without a Target",
            SettingValue::Toggle(true),
        ),
        (
            "lodestone-shimmer",
            "Lodestone Compass Shimmer",
            SettingValue::Toggle(true),
        ),
    ] {
        let spec = widget_spec(key).unwrap();
        assert_eq!(labels[&spec.label], label);
        assert_eq!(spec.default_value(), default);
    }

    change_widget(
        &mut settings,
        "ignore-inventory-compass",
        SettingValue::Toggle(true),
    );
    change_widget(
        &mut settings,
        "spin-compass-without-target",
        SettingValue::Toggle(false),
    );
    change_widget(
        &mut settings,
        "compass-fallback-target",
        SettingValue::Choice(1),
    );
    change_widget(
        &mut settings,
        "lodestone-shimmer",
        SettingValue::Toggle(false),
    );
    assert!(settings.ignore_inventory_compass);
    assert_eq!(settings.compass_fallback_target, 1);
    assert!(!settings.spin_compass_without_target);
    assert!(!settings.lodestone_compass_shimmer);
}

#[test]
fn compass_fallback_setting_cycles_all_choices() {
    let mut settings = Settings::default();
    let key = "compass-fallback-target";
    let spec = widget_spec(key).unwrap();
    let SettingKind::Choice { options, .. } = &spec.kind else {
        panic!("compass fallback must be a choice list");
    };
    assert_eq!(
        options,
        &[
            "minelights.horizon.setting.58",
            "minelights.horizon.setting.59",
            "minelights.horizon.setting.60",
        ]
    );
    for expected in [1, 2, 0] {
        change_widget(&mut settings, key, SettingValue::Choice(expected));
        assert_eq!(usize::from(settings.compass_fallback_target), expected);
    }
}
