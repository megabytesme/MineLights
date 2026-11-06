// The reviewed MineLights Server release and informational update checks.
// The release checker never chooses executable bytes. Changing the executable
// URL or digest requires a new, reviewed native-mod release.

pub(super) const SERVER_VERSION: &str = "v2.4.0";
#[cfg(windows)]
pub(super) const SERVER_SHA256: &str =
    "65679ebd02206b459e8dc6db9e5f9d934492802957757f80087cb38ace4210f7";
#[cfg(windows)]
const SERVER_URL: &str =
    "https://github.com/megabytesme/MineLights-Server/releases/download/v2.4.0/MineLights.exe";
const RELEASES_URL: &str = "https://github.com/megabytesme/MineLights-Server/releases";

#[derive(Debug, PartialEq, Eq)]
pub(super) struct UpdateNotice {
    pub(super) version: String,
    pub(super) changelog: Vec<String>,
    pub(super) truncated: bool,
}

#[cfg(any(windows, test))]
fn stable_version(tag: &str) -> Option<[u64; 3]> {
    let tag = tag.strip_prefix('v')?;
    if tag.len() > 128 {
        return None;
    }
    let (core, build) = match tag.split_once('+') {
        Some((core, build)) => (core, Some(build)),
        None => (tag, None),
    };
    // Build metadata does not affect SemVer precedence. Reject malformed
    // metadata as well as prereleases instead of accepting arbitrary tags.
    if let Some(build) = build {
        if build.split('.').any(|identifier| {
            identifier.is_empty()
                || !identifier
                    .bytes()
                    .all(|c| c.is_ascii_alphanumeric() || c == b'-')
        }) {
            return None;
        }
    }
    let mut parts = core.split('.');
    let parse_part = |part: &str| -> Option<u64> {
        if part.is_empty()
            || (part.len() > 1 && part.starts_with('0'))
            || !part.bytes().all(|c| c.is_ascii_digit())
        {
            return None;
        }
        part.parse().ok()
    };
    let numbers = [
        parse_part(parts.next()?)?,
        parse_part(parts.next()?)?,
        parse_part(parts.next()?)?,
    ];
    parts.next().is_none().then_some(numbers)
}

#[cfg(test)]
mod stable_version_tests {
    use super::stable_version;

    #[test]
    fn accepts_valid_build_metadata_without_changing_precedence() {
        assert_eq!(stable_version("v2.4.0+build.2"), Some([2, 4, 0]));
        assert_eq!(stable_version("v2.4.0+00.sha-ABC"), Some([2, 4, 0]));
        assert_eq!(stable_version("v2.4.0"), Some([2, 4, 0]));
        assert_eq!(stable_version("v2.4.1"), Some([2, 4, 1]));
    }

    #[test]
    fn rejects_prereleases_and_malformed_semver() {
        for tag in [
            "v2.4.1-rc.1",
            "v2.4.1-rc.1+build.2",
            "v2.4.0+",
            "v2.4.0+.a",
            "v2.4.0+a..b",
            "v2.4.0+a.",
            "v2.4.0+a+b",
            "v2.4.0+a/b",
            "v2.04.0",
            "v2.4",
            "v2.4.0.1",
            "2.4.0",
            "v18446744073709551616.0.0",
        ] {
            assert_eq!(stable_version(tag), None, "accepted {tag}");
        }
    }
}

/// Parse GitHub release metadata as text only; ignore drafts and test releases.
#[cfg(any(windows, test))]
pub(super) fn parse_update_notice(json: &str) -> Option<UpdateNotice> {
    let releases: serde_json::Value = serde_json::from_str(json).ok()?;
    let current = stable_version(SERVER_VERSION)?;
    let release = releases
        .as_array()?
        .iter()
        .filter_map(|release| {
            if release.get("draft")?.as_bool()? || release.get("prerelease")?.as_bool()? {
                return None;
            }
            let tag = release.get("tag_name")?.as_str()?;
            let version = stable_version(tag)?;
            (version > current).then_some((version, tag, release))
        })
        .max_by_key(|(version, _, _)| *version)?;
    let body = release
        .2
        .get("body")
        .and_then(serde_json::Value::as_str)
        .unwrap_or("");
    let lines: Vec<String> = body
        .lines()
        .map(|line| {
            line.chars()
                .filter(|c| !c.is_control())
                .take(180)
                .collect::<String>()
        })
        .filter(|line| !line.trim().is_empty())
        .collect();
    Some(UpdateNotice {
        version: release.1.to_owned(),
        changelog: lines.iter().take(8).cloned().collect(),
        truncated: lines.len() > 8 || body.lines().any(|line| line.chars().count() > 180),
    })
}

pub(super) fn update_messages(update: &UpdateNotice) -> Vec<String> {
    let mut messages = vec![format!(
        "[MineLights] Server {} is newer than the reviewed {SERVER_VERSION}. Install it yourself: {RELEASES_URL}",
        update.version
    )];
    if update.changelog.is_empty() {
        messages.push("[MineLights] No release notes were provided.".into());
    } else {
        messages.extend(
            update
                .changelog
                .iter()
                .map(|line| format!("[MineLights] {line}")),
        );
        if update.truncated {
            messages.push(format!("[MineLights] More release notes: {RELEASES_URL}"));
        }
    }
    messages
}

#[cfg(windows)]
fn hidden_command(program: &str) -> std::process::Command {
    use std::os::windows::process::CommandExt;
    let mut command = std::process::Command::new(program);
    command.creation_flags(0x0800_0000);
    command
}

/// Check release metadata only. This cannot download or launch an executable.
#[cfg(windows)]
pub(super) fn check_for_update() -> Result<Option<UpdateNotice>, String> {
    let output = hidden_command("curl.exe")
        .args([
            "--fail",
            "--silent",
            "--show-error",
            "--location",
            "--proto",
            "=https",
            "--proto-redir",
            "=https",
            "--connect-timeout",
            "5",
            "--max-time",
            "15",
            "--max-filesize",
            "131072",
            "--user-agent",
            "MineLights-Horizon",
            "https://api.github.com/repos/megabytesme/MineLights-Server/releases?per_page=30",
        ])
        .output()
        .map_err(|error| error.to_string())?;
    if !output.status.success() || output.stdout.len() > 131072 {
        return Err("could not fetch the release list".into());
    }
    let text = std::str::from_utf8(&output.stdout).map_err(|error| error.to_string())?;
    let value: serde_json::Value = serde_json::from_str(text).map_err(|error| error.to_string())?;
    if !value.is_array() {
        return Err("invalid release list".into());
    }
    Ok(parse_update_notice(text))
}

#[cfg(not(windows))]
pub(super) fn check_for_update() -> Result<Option<UpdateNotice>, String> {
    Ok(None)
}

#[cfg(windows)]
pub(super) fn is_reviewed_binary(path: &std::path::Path) -> bool {
    let output = hidden_command("certutil.exe")
        .arg("-hashfile")
        .arg(path)
        .arg("SHA256")
        .output();
    output
        .ok()
        .filter(|output| output.status.success())
        .and_then(|output| String::from_utf8(output.stdout).ok())
        .is_some_and(|output| {
            output
                .lines()
                .map(str::trim)
                .any(|line| line.eq_ignore_ascii_case(SERVER_SHA256))
        })
}

#[cfg(not(windows))]
pub(super) fn is_reviewed_binary(_path: &std::path::Path) -> bool {
    false
}

/// Download only the pinned binary and never overwrite a user-installed file.
#[cfg(windows)]
pub(super) fn download_pinned_detailed(
    path: &std::path::Path,
    allowed: bool,
) -> Result<(), String> {
    download_pinned_detailed_with_started(path, allowed, || {})
}

/// Like `download_pinned_detailed`, but calls `on_started` only after the
/// downloader process was successfully created, after all local preflight.
#[cfg(windows)]
pub(super) fn download_pinned_detailed_with_started(
    path: &std::path::Path,
    allowed: bool,
    on_started: impl FnOnce(),
) -> Result<(), String> {
    if !allowed || path.exists() {
        return Err(if allowed {
            "the destination already exists".into()
        } else {
            "download permission is off".into()
        });
    }
    let Some(parent) = path.parent() else {
        return Err("the destination folder is unavailable".into());
    };
    std::fs::create_dir_all(parent).map_err(|error| {
        format!(
            "could not create the destination folder ({:?})",
            error.kind()
        )
    })?;
    let file_name = path
        .file_name()
        .unwrap_or_else(|| std::ffi::OsStr::new("MineLights.exe"));
    let temporary = path.with_file_name(format!(
        "{}.minelights-download-{}",
        file_name.to_string_lossy(),
        std::process::id()
    ));
    if temporary.exists() {
        return Err("a previous temporary download file already exists".into());
    }
    let mut command = hidden_command("curl.exe");
    command
        .args([
            "--fail",
            "--silent",
            "--show-error",
            "--location",
            "--proto",
            "=https",
            "--proto-redir",
            "=https",
            "--connect-timeout",
            "10",
            "--max-time",
            "300",
            "--max-filesize",
            "268435456",
            "--output",
        ])
        .arg(&temporary)
        .arg(SERVER_URL)
        .stdout(std::process::Stdio::null())
        .stderr(std::process::Stdio::null());
    let mut downloader = command
        .spawn()
        .map_err(|error| format!("could not start the HTTPS downloader ({:?})", error.kind()))?;
    on_started();
    let status = match downloader.wait() {
        Ok(status) => status,
        Err(error) => {
            let _ = std::fs::remove_file(&temporary);
            return Err(format!(
                "could not wait for the HTTPS downloader ({:?})",
                error.kind()
            ));
        }
    };
    if !status.success() {
        let code = status.code();
        let _ = std::fs::remove_file(&temporary);
        return Err(match code {
            Some(6) => "HTTPS download failed: DNS lookup failed (curl 6)".into(),
            Some(7) => "HTTPS download failed: the server could not be reached (curl 7)".into(),
            Some(22) => "HTTPS download failed: GitHub returned an HTTP error (curl 22)".into(),
            Some(23) => {
                "HTTPS download failed: the temporary file could not be written (curl 23)".into()
            }
            Some(28) => "HTTPS download failed: the connection timed out (curl 28)".into(),
            Some(60) => {
                "HTTPS download failed: the server certificate could not be verified (curl 60)"
                    .into()
            }
            Some(63) => {
                "HTTPS download failed: the response exceeded the 256 MiB safety limit (curl 63)"
                    .into()
            }
            Some(code) => format!("HTTPS download failed (curl exit code {code})"),
            None => "HTTPS download failed because the downloader was terminated".into(),
        });
    }
    let actual = match sha256_file(&temporary) {
        Ok(actual) => actual,
        Err(error) => {
            let _ = std::fs::remove_file(&temporary);
            return Err(error);
        }
    };
    if !actual.eq_ignore_ascii_case(SERVER_SHA256) {
        let _ = std::fs::remove_file(&temporary);
        return Err(format!(
            "SHA-256 did not match the reviewed binary (received {actual})"
        ));
    }
    if path.exists() {
        let _ = std::fs::remove_file(&temporary);
        return Err("the destination appeared before installation; it was preserved".into());
    }
    std::fs::rename(&temporary, path).map_err(|error| {
        let _ = std::fs::remove_file(&temporary);
        format!(
            "the verified download could not be installed ({:?})",
            error.kind()
        )
    })?;
    Ok(())
}

#[cfg(windows)]
fn sha256_file(path: &std::path::Path) -> Result<String, String> {
    let output = hidden_command("certutil.exe")
        .arg("-hashfile")
        .arg(path)
        .arg("SHA256")
        .output()
        .map_err(|error| format!("could not start SHA-256 verification ({:?})", error.kind()))?;
    if !output.status.success() {
        return Err("SHA-256 verification could not read the downloaded file".into());
    }
    let text = String::from_utf8_lossy(&output.stdout);
    text.lines()
        .map(str::trim)
        .find(|line| line.len() == 64 && line.bytes().all(|byte| byte.is_ascii_hexdigit()))
        .map(str::to_ascii_lowercase)
        .ok_or_else(|| "SHA-256 verification returned no digest".into())
}

#[cfg(windows)]
pub(super) fn download_pinned(path: &std::path::Path, allowed: bool) -> bool {
    download_pinned_detailed(path, allowed).is_ok()
}

#[cfg(not(windows))]
pub(super) fn download_pinned(_path: &std::path::Path, _allowed: bool) -> bool {
    false
}

#[cfg(not(windows))]
pub(super) fn download_pinned_detailed(
    _path: &std::path::Path,
    _allowed: bool,
) -> Result<(), String> {
    Err("automatic server installation is available on Windows only".into())
}

#[cfg(not(windows))]
pub(super) fn download_pinned_detailed_with_started(
    _path: &std::path::Path,
    _allowed: bool,
    _on_started: impl FnOnce(),
) -> Result<(), String> {
    Err("automatic server installation is available on Windows only".into())
}
