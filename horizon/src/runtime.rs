// MineLights' Horizon-native runtime.
//
// The renderer takes a gameplay snapshot; the bridge worker owns blocking device and server I/O.

use std::{
    collections::HashMap,
    sync::{
        Mutex, OnceLock,
        atomic::{AtomicBool, Ordering},
        mpsc::{SyncSender, TrySendError},
    },
    time::{Duration, Instant},
};

use mod_api::{ClientHost, Text};

use super::MOD_ID;

pub(super) use super::minelights_bridge::minelights_shutdown;
use super::minelights_bridge::{BRIDGE, Bridge, Notice, SERVER_DOWNLOAD_IN_PROGRESS, bridge_for};
use super::minelights_config::Settings;
use super::minelights_effects::Painter;
use super::minelights_protocol::LIGHTING_FRAME_TTL_MS;
use super::minelights_settings::config_dir;

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub(super) struct Color(pub(super) u8, pub(super) u8, pub(super) u8);

impl Color {
    pub(super) const BLACK: Self = Self(0, 0, 0);
    pub(super) const WHITE: Self = Self(255, 255, 255);

    pub(super) fn scaled(self, factor: f32) -> Self {
        Self(
            (self.0 as f32 * factor).clamp(0.0, 255.0) as u8,
            (self.1 as f32 * factor).clamp(0.0, 255.0) as u8,
            (self.2 as f32 * factor).clamp(0.0, 255.0) as u8,
        )
    }

    pub(super) fn blend(self, over: Self, opacity: f32) -> Self {
        let alpha = opacity.clamp(0.0, 1.0);
        let channel = |a: u8, b: u8| (a as f32 * (1.0 - alpha) + b as f32 * alpha) as u8;
        Self(
            channel(self.0, over.0),
            channel(self.1, over.1),
            channel(self.2, over.2),
        )
    }

    pub(super) fn lerp(self, other: Self, t: f32) -> Self {
        self.blend(other, t)
    }
}

pub(super) type DeviceLayout = minelights_engine::DeviceLayout;

#[derive(Clone, Debug, Default, PartialEq)]
pub(super) struct Snapshot {
    pub(super) in_game: bool,
    pub(super) dimension: String,
    pub(super) biome: String,
    pub(super) feet: String,
    pub(super) below: String,
    pub(super) head: String,
    pub(super) health: f32,
    pub(super) absorption: f32,
    pub(super) hunger: i32,
    pub(super) saturation: f32,
    pub(super) air: i32,
    pub(super) experience: f32,
    pub(super) hud_bar: u8,
    pub(super) rain: f32,
    pub(super) biome_precipitation: bool,
    pub(super) biome_snowing: bool,
    pub(super) sky_light: f32,
    pub(super) local_light: f32,
    pub(super) end_flash: f32,
    pub(super) compass_type: u8,
    pub(super) compass_pointing: bool,
    pub(super) compass_yaw: f32,
    pub(super) compass_distance: f32,
    pub(super) waypoints: Vec<Waypoint>,
    pub(super) chat_received: u64,
    pub(super) chat_key: String,
    pub(super) on_fire: bool,
    pub(super) poisoned: bool,
    pub(super) withering: bool,
    pub(super) taking_damage: bool,
    pub(super) in_water: bool,
    pub(super) lightning: bool,
    pub(super) movement_keys: Vec<String>,
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub(super) struct Waypoint {
    pub(super) yaw_degrees: i16,
    pub(super) color: Color,
    pub(super) pitch: i8,
    pub(super) distance: f32,
}

#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub(super) struct Frame {
    pub(super) keys: HashMap<u32, Color>,
}

pub(super) fn trailing_number(value: &str) -> String {
    value
        .chars()
        .rev()
        .take_while(|c| c.is_ascii_digit())
        .collect::<String>()
        .chars()
        .rev()
        .collect()
}

static PAINTER: OnceLock<Mutex<Painter>> = OnceLock::new();
pub(super) static PENDING_SERVER_CONFIRMATION_REMINDER_SENT: AtomicBool = AtomicBool::new(false);

/// Called from Horizon's native frame hook. Game state stays on the render
/// thread; only immutable LED frames cross to the background network worker.
pub(super) fn minelights_frame(host: &mut dyn ClientHost, player: Snapshot, config: Settings) {
    if !host.on(MOD_ID) {
        minelights_disabled_frame();
        return;
    }
    let bridge = bridge_for(config_dir(host), &config);
    let language = host
        .client_session()
        .client_language()
        .unwrap_or_else(super::minelights_player_data::client_language);
    bridge.in_world.store(player.in_game, Ordering::Release);
    if config.server_confirmation_pending {
        if player.in_game && !PENDING_SERVER_CONFIRMATION_REMINDER_SENT.swap(true, Ordering::AcqRel)
        {
            chat(
                host,
                "[MineLights] Reviewed server autostart is still off because confirmation is incomplete. Use /minelights confirm server-autostart to enable it, or /minelights set server-autostart off to cancel.",
            );
        }
    } else {
        PENDING_SERVER_CONFIRMATION_REMINDER_SENT.store(false, Ordering::Release);
    }
    if player.in_game {
        for notice in take_notices(bridge) {
            match notice {
                Notice::MissingServer if config.notify_missing_server => {
                    chat(
                        host,
                        "[MineLights] Server not detected. Download MineLights.exe from https://github.com/megabytesme/MineLights-Server/releases and run it yourself.",
                    );
                    chat(
                        host,
                        "[MineLights] Alternatively, opt in to the reviewed Server download and launch in Mods settings.",
                    );
                }
                Notice::Update(lines) if config.check_server_updates => {
                    for line in lines {
                        chat(host, &line);
                    }
                }
                Notice::UpdateCheckFailed { manual } if manual || config.check_server_updates => {
                    chat(
                        host,
                        &server_download_message(
                            &language,
                            7,
                            "[MineLights] MineLights Server update check failed. Review updates manually: https://github.com/megabytesme/MineLights-Server/releases",
                        ),
                    );
                }
                Notice::ServerStartFailed if config.notify_server_download_progress => {
                    chat(
                        host,
                        &server_download_message(
                            &language,
                            5,
                            "[MineLights] MineLights Server could not be started automatically. Start it from its tray or install it manually: https://github.com/megabytesme/MineLights-Server/releases",
                        ),
                    );
                }
                Notice::ServerDownloadStarted { began_in_world }
                    if config.notify_server_download_progress =>
                {
                    if began_in_world {
                        chat(
                            host,
                            &server_download_message(
                                &language,
                                0,
                                "[MineLights] Downloading MineLights Server…",
                            ),
                        );
                    } else if SERVER_DOWNLOAD_IN_PROGRESS.load(Ordering::Acquire) {
                        chat(
                            host,
                            &server_download_message(
                                &language,
                                1,
                                "[MineLights] MineLights Server download is still in progress.",
                            ),
                        );
                    }
                }
                Notice::ServerDownloadFinished {
                    result,
                    server_was_running_before,
                    server_running_after,
                    server_started,
                    server_readiness_failed,
                } if config.notify_server_download_progress => {
                    match classify_server_download_completion(
                        result.is_ok(),
                        server_was_running_before,
                        server_running_after,
                        server_started,
                        server_readiness_failed,
                    ) {
                        ServerDownloadCompletion::Failed => {
                            chat(
                                host,
                                &server_download_message(
                                    &language,
                                    4,
                                    "[MineLights] Automatic MineLights Server setup could not be completed. If the server is running, stop it from its tray; otherwise install it manually: https://github.com/megabytesme/MineLights-Server/releases",
                                ),
                            );
                        }
                        ServerDownloadCompletion::Restarted => {
                            chat(
                                host,
                                &server_download_message(
                                    &language,
                                    3,
                                    "[MineLights] MineLights Server update finished and the server has restarted.",
                                ),
                            );
                        }
                        ServerDownloadCompletion::RestartRequired => {
                            chat(
                                host,
                                &server_download_message(
                                    &language,
                                    6,
                                    "[MineLights] The MineLights Server update is verified but not installed yet. If a MineLights Server is running, stop it from its system tray; then restart Minecraft to install and start the update.",
                                ),
                            );
                        }
                        ServerDownloadCompletion::Started => {
                            chat(
                                host,
                                &server_download_message(
                                    &language,
                                    2,
                                    "[MineLights] MineLights Server download finished and the server has started.",
                                ),
                            );
                        }
                        ServerDownloadCompletion::StartFailed => {
                            chat(
                                host,
                                &server_download_message(
                                    &language,
                                    5,
                                    "[MineLights] MineLights Server could not be started automatically. Start it from its tray or install it manually: https://github.com/megabytesme/MineLights-Server/releases",
                                ),
                            );
                        }
                    }
                }
                Notice::Manual(lines) => {
                    for line in lines {
                        chat(host, &line);
                    }
                }
                _ => {}
            }
        }
    }
    let layouts = bridge.layouts.read().map(|v| v.clone()).unwrap_or_default();
    let now = Instant::now();
    let mut painter = PAINTER
        .get_or_init(|| Mutex::new(Painter::new()))
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner());
    if now.duration_since(painter.last_paint) < Duration::from_millis(33) {
        return;
    }
    painter.last_paint = now;
    let frame = painter.render(player, &layouts, config.clone(), now);
    send_frame_if_needed(&bridge.sender, &mut painter, frame, now);
}

fn server_download_message(language: &str, index: usize, fallback: &str) -> String {
    super::minelights_lang::server_message_or_english(language, index)
        .unwrap_or(fallback)
        .to_owned()
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum ServerDownloadCompletion {
    Failed,
    Restarted,
    RestartRequired,
    Started,
    StartFailed,
}

fn classify_server_download_completion(
    download_succeeded: bool,
    server_was_running_before: bool,
    server_running_after: bool,
    server_started: bool,
    server_readiness_failed: bool,
) -> ServerDownloadCompletion {
    if !download_succeeded {
        ServerDownloadCompletion::Failed
    } else if server_started && server_was_running_before {
        ServerDownloadCompletion::Restarted
    } else if server_readiness_failed {
        ServerDownloadCompletion::StartFailed
    } else if server_running_after {
        ServerDownloadCompletion::RestartRequired
    } else if server_started {
        ServerDownloadCompletion::Started
    } else {
        ServerDownloadCompletion::StartFailed
    }
}

#[cfg(test)]
mod server_download_completion_tests {
    use super::{ServerDownloadCompletion as C, classify_server_download_completion as classify};

    #[test]
    fn classifies_completion_and_server_process_races() {
        let cases = [
            // Failed transfer/install is reported independently of process state.
            (false, false, false, false, false, C::Failed),
            // A managed spawn after replacing a previously running server is a restart.
            (true, true, false, true, false, C::Restarted),
            // A server that appeared during transfer and remains up needs a tray restart.
            (true, false, true, false, false, C::RestartRequired),
            (true, true, true, false, false, C::RestartRequired),
            // A prior process that exited during transfer and could not be relaunched failed.
            (true, true, false, false, false, C::StartFailed),
            // Fresh installation successfully spawned the server.
            (true, false, false, true, false, C::Started),
            // A prior process may exit during transfer, then the managed process starts.
            (true, true, false, true, false, C::Restarted),
            // A server that appeared and then exited is not reported as still running.
            (true, false, false, false, false, C::StartFailed),
            // A spawned child that never becomes V2-ready is failure even if
            // process enumeration still sees it during the completion notice.
            (true, false, true, false, true, C::StartFailed),
            (true, true, true, false, true, C::StartFailed),
        ];

        for (succeeded, before, after, started, readiness_failed, expected) in cases {
            assert_eq!(
                classify(succeeded, before, after, started, readiness_failed),
                expected,
                "succeeded={succeeded}, before={before}, after={after}, started={started}, readiness_failed={readiness_failed}"
            );
        }
    }
}

fn send_frame_if_needed(
    sender: &SyncSender<Frame>,
    painter: &mut Painter,
    frame: Frame,
    now: Instant,
) {
    // Leave room for the next render pass and worker dispatch before the server expires this frame.
    const REFRESH_INTERVAL: Duration = Duration::from_millis(LIGHTING_FRAME_TTL_MS / 2);
    if painter.last_frame.as_ref() != Some(&frame)
        || now.duration_since(painter.last_frame_sent) >= REFRESH_INTERVAL
    {
        match sender.try_send(frame.clone()) {
            Ok(()) => {
                painter.last_frame = Some(frame);
                painter.last_frame_sent = now;
            }
            Err(TrySendError::Full(_)) | Err(TrySendError::Disconnected(_)) => {}
        }
    }
}

fn take_notices(bridge: &Bridge) -> std::collections::VecDeque<Notice> {
    bridge
        .notices
        .lock()
        .map(|mut notices| std::mem::take(&mut *notices))
        .unwrap_or_default()
}

fn chat(host: &mut dyn ClientHost, line: &str) {
    let _ = host.chat_line(Text::literal(line));
}

pub(super) fn minelights_disabled_frame() {
    let Some(bridge) = BRIDGE.get() else { return };
    bridge.auto_start.store(false, Ordering::Release);
    bridge.in_world.store(false, Ordering::Release);
    // The worker consumes this transition, sends black, releases V2 leases,
    // and restores the saved OpenRGB state off the render thread.
    bridge.active.store(false, Ordering::Release);
}

#[cfg(test)]
mod tests {
    use super::super::minelights_effects::biome_color;
    use super::{Color, Frame, LIGHTING_FRAME_TTL_MS, Painter, send_frame_if_needed};
    use minelights_engine::standardize_key;
    use std::{sync::mpsc, time::Duration};

    #[test]
    fn unchanged_frame_refreshes_before_the_server_expires_it() {
        let (sender, receiver) = mpsc::sync_channel(1);
        let mut painter = Painter::new();
        let start = painter.last_frame_sent;
        let frame = Frame::default();
        let interval = Duration::from_millis(LIGHTING_FRAME_TTL_MS / 2);

        send_frame_if_needed(&sender, &mut painter, frame.clone(), start);
        assert_eq!(receiver.try_recv().unwrap(), frame);
        send_frame_if_needed(
            &sender,
            &mut painter,
            frame.clone(),
            start + interval - Duration::from_millis(1),
        );
        assert_eq!(receiver.try_recv(), Err(mpsc::TryRecvError::Empty));
        send_frame_if_needed(&sender, &mut painter, frame.clone(), start + interval);
        assert_eq!(receiver.try_recv().unwrap(), frame);
    }

    #[test]
    fn standardizes_device_key_names_like_the_java_client() {
        assert_eq!(standardize_key("Left Control"), "LCTRL");
        assert_eq!(standardize_key("RAM 3"), "RAM_3");
        assert_eq!(standardize_key("Programmable Key 12"), "G12");
    }

    #[test]
    fn maps_biome_colors_and_blends_channels() {
        assert_eq!(biome_color("minecraft:plains"), Color(130, 159, 66));
        assert_eq!(
            Color(0, 0, 0).blend(Color(255, 128, 0), 0.5),
            Color(127, 64, 0)
        );
    }
}
