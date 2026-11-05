#[allow(dead_code)]
#[path = "../mixins/minelights/server.rs"]
mod server;

#[test]
fn pinned_build_metadata_compares_stable_release_precedence() {
    assert_eq!(server::SERVER_VERSION, "v2.4.0+build.2");
    let equal = r#"[
      {"tag_name":"v2.4.0","draft":false,"prerelease":false},
      {"tag_name":"v2.4.0+different.1","draft":false,"prerelease":false},
      {"tag_name":"v2.4.1-rc.1","draft":false,"prerelease":false},
      {"tag_name":"v2.5.0","draft":false,"prerelease":true}
    ]"#;
    assert!(server::parse_update_notice(equal).is_none());

    let newer = r#"[
      {"tag_name":"v2.4.1+build.9","draft":false,"prerelease":false,"body":"Fix"},
      {"tag_name":"v999.0.0+bad..metadata","draft":false,"prerelease":false}
    ]"#;
    let notice = server::parse_update_notice(newer).unwrap();
    assert_eq!(notice.version, "v2.4.1+build.9");
    assert_eq!(notice.changelog, ["Fix"]);
}

#[test]
fn reports_new_stable_releases_and_their_notes() {
    let json = r###"[
      {"tag_name":"v2.3.3-test-3","draft":false,"prerelease":false,"body":"test"},
      {"tag_name":"v999.0.0","draft":false,"prerelease":false,"body":"## Changes\n- New devices"},
      {"tag_name":"v9.0.0","draft":true,"prerelease":false,"body":"draft"}
    ]"###;
    let notice = server::parse_update_notice(json).unwrap();
    assert_eq!(notice.version, "v999.0.0");
    let lines = server::update_messages(&notice);
    assert!(lines.iter().any(|line| line.contains("New devices")));
    assert!(lines[0].contains(&format!(
        "newer than the reviewed {}",
        server::SERVER_VERSION
    )));
    assert!(lines[0].contains("/releases"));
}

#[test]
fn ignores_test_versions_old_versions_and_malformed_metadata() {
    let json = r#"[
      {"tag_name":"v2.3.3-test-3","draft":false,"prerelease":false},
      {"tag_name":"v2.3.3","draft":false,"prerelease":false},
      {"tag_name":"v3.0.0","draft":false,"prerelease":true},
      {"tag_name":"v4.0.0/evil","draft":false,"prerelease":false}
    ]"#;
    assert!(server::parse_update_notice(json).is_none());
    assert!(server::parse_update_notice("not json").is_none());
}

#[test]
fn bounds_untrusted_release_notes() {
    let body = (0..10)
        .map(|i| format!("line {i}"))
        .collect::<Vec<_>>()
        .join("\n");
    let json = serde_json::json!([{"tag_name":"v999.0.0", "draft":false, "prerelease":false, "body":body}]);
    let notice = server::parse_update_notice(&json.to_string()).unwrap();
    assert_eq!(notice.changelog.len(), 8);
    assert!(notice.truncated);
    assert!(
        server::update_messages(&notice)
            .last()
            .unwrap()
            .contains("More release notes")
    );
}

#[test]
fn download_never_runs_without_permission_or_overwrites_an_existing_file() {
    let dir = std::env::temp_dir().join(format!("minelights-consent-{}", std::process::id()));
    std::fs::create_dir_all(&dir).unwrap();
    let path = dir.join("MineLights.exe");
    assert!(!server::download_pinned(&path, false));
    assert!(!path.exists());
    std::fs::write(&path, b"user-installed server").unwrap();
    assert!(!server::is_reviewed_binary(&path));
    assert!(!server::download_pinned(&path, true));
    assert_eq!(std::fs::read(&path).unwrap(), b"user-installed server");
    std::fs::remove_file(&path).unwrap();
    std::fs::remove_dir(&dir).unwrap();
}
