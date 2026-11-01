#[path = "../mixins/minelights/translations.rs"]
mod translations;

#[test]
fn settings_labels_follow_the_selected_language() {
    assert_eq!(translations::setting_label("en_us", 0), Some("Enable Mod"));
    assert_ne!(
        translations::setting_label("de_de", 0),
        translations::setting_label("en_us", 0)
    );
    assert!(translations::setting_label("missing_language", 0).is_none());
}

#[test]
fn generated_labels_match_every_java_language_file() {
    let languages = std::path::Path::new(env!("CARGO_MANIFEST_DIR"))
        .join("../common/src/main/resources/assets/minelights/lang");
    let labels = [
        (0, "option.mine-lights.enableMod"),
        (10, "option.mine-lights.mirrorRamRainDirection"),
        (20, "option.mine-lights.autoStartServer"),
        (33, "option.mine-lights.refresh_devices.label"),
        (36, "option.mine-lights.force_update.label"),
        (40, "enum.mine-lights.dimmingMode.local_light"),
        (46, "enum.mine-lights.compassPriority.recovery_first"),
    ];
    let mut count = 0;
    for entry in std::fs::read_dir(languages).unwrap() {
        let path = entry.unwrap().path();
        if path.extension().is_none_or(|ext| ext != "json") {
            continue;
        }
        let language = path.file_stem().unwrap().to_str().unwrap();
        let text = std::fs::read_to_string(&path).unwrap();
        let java: serde_json::Value =
            serde_json::from_str(text.trim_start_matches('\u{feff}')).unwrap();
        for (index, key) in labels {
            assert_eq!(
                translations::setting_label(language, index),
                java.get(key).and_then(serde_json::Value::as_str),
                "{language}: {key}"
            );
        }
        count += 1;
    }
    assert!(count >= 38, "expected the full set of Java translations");
}
