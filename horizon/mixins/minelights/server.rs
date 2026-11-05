// The reviewed MineLights Server release and informational update checks.
// The release checker never chooses executable bytes. Changing the executable
// URL or digest requires a new, reviewed native-mod release.

pub(super) const SERVER_VERSION: &str = "v2.4.0";
#[cfg(windows)]
pub(super) const SERVER_SHA256: &str =
    "f88c7c844915bde700337f9e407ee2b15813fa0760653c0a7f2a9a974496071c";
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
    if tag.len() > 32 || !tag.chars().all(|c| c.is_ascii_digit() || c == '.') {
        return None;
    }
    let mut parts = tag.split('.');
    let numbers = [
        parts.next()?.parse().ok()?,
        parts.next()?.parse().ok()?,
        parts.next()?.parse().ok()?,
    ];
    parts.next().is_none().then_some(numbers)
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
pub(super) fn download_pinned(path: &std::path::Path, allowed: bool) -> bool {
    if !allowed || path.exists() {
        return false;
    }
    let Some(parent) = path.parent() else {
        return false;
    };
    if std::fs::create_dir_all(parent).is_err() {
        return false;
    }
    let temporary = path.with_file_name("MineLights.exe.minelights-download");
    let result = hidden_command("curl.exe")
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
        .output();
    let verified =
        result.is_ok_and(|output| output.status.success()) && is_reviewed_binary(&temporary);
    if !verified || path.exists() || std::fs::rename(&temporary, path).is_err() {
        let _ = std::fs::remove_file(&temporary);
        return false;
    }
    true
}

#[cfg(not(windows))]
pub(super) fn download_pinned(_path: &std::path::Path, _allowed: bool) -> bool {
    false
}
