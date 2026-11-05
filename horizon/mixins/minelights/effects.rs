use std::{
    collections::HashMap,
    time::{Duration, Instant, SystemTime, UNIX_EPOCH},
};

use super::minelights::{Color, DeviceLayout, Frame, Snapshot, trailing_number};
use super::minelights_config::Settings;

#[derive(Clone, Copy)]
struct RainDrop {
    column: usize,
    row: usize,
    vertical: bool,
    last_fall: Instant,
    color: Color,
}

struct RamDrop {
    position: isize,
    last_fall: Instant,
    color: Color,
}

struct RamRainState {
    drops: Vec<RamDrop>,
    last_spawn: Instant,
    next_delay: Duration,
}

const KEYBOARD_ROWS: [&[&str]; 6] = [
    &[
        "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12",
    ],
    &[
        "BACKTICK", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "MINUS", "EQUALS",
    ],
    &[
        "Q",
        "W",
        "E",
        "R",
        "T",
        "Y",
        "U",
        "I",
        "O",
        "P",
        "LEFT_BRACKET",
        "RIGHT_BRACKET",
    ],
    &[
        "A",
        "S",
        "D",
        "F",
        "G",
        "H",
        "J",
        "K",
        "L",
        "SEMICOLON",
        "APOSTROPHE",
        "HASH",
    ],
    &[
        "ISO_BACKSLASH",
        "Z",
        "X",
        "C",
        "V",
        "B",
        "N",
        "M",
        "COMMA",
        "PERIOD",
        "SLASH",
    ],
    &[
        "LCTRL", "LWIN", "LALT", "SPACE", "RALT", "FN", "MENU", "RCTRL",
    ],
];
const KEYBOARD_COLUMNS: [&[&str]; 8] = [
    &["G1", "G2", "G3", "G4", "G5", "G6"],
    &["PRINTSCREEN", "DELETE", "LEFT"],
    &["SCROLLLOCK", "END", "DOWN"],
    &["PAUSE", "PAGE_DOWN", "RIGHT"],
    &["NUMLOCK", "NUMPAD7", "NUMPAD4", "NUMPAD1"],
    &["NUMPAD_DIVIDE", "NUMPAD8", "NUMPAD5", "NUMPAD2", "NUMPAD0"],
    &[
        "NUMPAD_MULTIPLY",
        "NUMPAD9",
        "NUMPAD6",
        "NUMPAD3",
        "NUMPAD_DECIMAL",
    ],
    &["NUMPAD_SUBTRACT", "NUMPAD_ADD", "NUMPAD_ENTER"],
];

pub(super) struct Painter {
    pub(super) last_frame: Option<Frame>,
    pub(super) last_frame_sent: Instant,
    pub(super) last_paint: Instant,
    current_biome: String,
    biome_from: Color,
    biome_to: Color,
    biome_started: Instant,
    brightness: f32,
    damage_started: Option<Instant>,
    was_damaged: bool,
    was_low_health: bool,
    heartbeat: f32,
    heartbeat_rising: bool,
    heartbeat_step: Instant,
    spin_index: usize,
    last_spin: Instant,
    sheen_index: usize,
    last_sheen: Instant,
    rain_phase: bool,
    last_rain_phase: Instant,
    last_chat_received: u64,
    chat_pulse_started: Option<Instant>,
    raindrops: Vec<RainDrop>,
    last_raindrop_spawn: Option<Instant>,
    ram_rain: HashMap<String, RamRainState>,
    fire_crackle: Vec<u32>,
    portal_twinkle: Vec<u32>,
    end_flash_crackle: Vec<u32>,
    last_fire_crackle: Instant,
    last_portal_twinkle: Instant,
    last_end_flash_crackle: Instant,
}

impl Painter {
    pub(super) fn new() -> Self {
        let now = Instant::now();
        Self {
            last_frame: None,
            last_frame_sent: now,
            last_paint: now - Duration::from_millis(33),
            current_biome: String::new(),
            biome_from: Color::BLACK,
            biome_to: Color::BLACK,
            biome_started: now,
            brightness: 1.0,
            damage_started: None,
            was_damaged: false,
            was_low_health: false,
            heartbeat: 0.1,
            heartbeat_rising: true,
            heartbeat_step: now,
            spin_index: 0,
            last_spin: now,
            sheen_index: 0,
            last_sheen: now,
            rain_phase: false,
            last_rain_phase: now,
            last_chat_received: 0,
            chat_pulse_started: None,
            raindrops: Vec::new(),
            last_raindrop_spawn: None,
            ram_rain: HashMap::new(),
            fire_crackle: Vec::new(),
            portal_twinkle: Vec::new(),
            end_flash_crackle: Vec::new(),
            last_fire_crackle: now - Duration::from_secs(1),
            last_portal_twinkle: now - Duration::from_secs(1),
            last_end_flash_crackle: now - Duration::from_secs(1),
        }
    }

    pub(super) fn render(
        &mut self,
        player: Snapshot,
        layouts: &[DeviceLayout],
        settings: Settings,
        now: Instant,
    ) -> Frame {
        let mut frame = Frame::default();
        let all_leds: Vec<u32> = layouts
            .iter()
            .flat_map(|layout| layout.leds.iter().copied())
            .collect();
        if !settings.enabled {
            for led in all_leds {
                frame.keys.insert(led, Color::BLACK);
            }
            return frame;
        }
        if !player.in_game {
            for led in all_leds {
                frame.keys.insert(led, Color(255, 0, 0));
            }
            return frame;
        }
        for led in &all_leds {
            frame.keys.insert(*led, Color::BLACK);
        }

        let biome_name = player.biome.to_ascii_uppercase();
        if settings.biome_lighting {
            if biome_name != self.current_biome && !biome_name.is_empty() {
                let previous_progress =
                    (now.duration_since(self.biome_started).as_secs_f32() / 0.75).clamp(0.0, 1.0);
                self.biome_from = self.biome_from.lerp(self.biome_to, previous_progress);
                self.biome_to = biome_color(&biome_name);
                self.current_biome = biome_name.clone();
                self.biome_started = now;
            }
        }
        let transition =
            (now.duration_since(self.biome_started).as_secs_f32() / 0.75).clamp(0.0, 1.0);
        let mut base = if settings.biome_lighting {
            self.biome_from.lerp(self.biome_to, transition)
        } else {
            Color::BLACK
        };
        let blocks = [
            player.feet.as_str(),
            player.below.as_str(),
            player.head.as_str(),
        ];
        let soul_fire = player.on_fire
            && blocks.iter().any(|b| {
                b.contains("soul_fire") || b.contains("soul_sand") || b.contains("soul_soil")
            });
        let in_regular_fire = blocks
            .iter()
            .any(|b| b.contains("minecraft:fire") || b.contains("minecraft:lava"));
        let nether_portal = blocks.iter().any(|b| b.contains("minecraft:nether_portal"));
        let end_portal = blocks.iter().any(|b| b.contains("minecraft:end_portal"));
        if settings.fire_effect && (player.on_fire || in_regular_fire || soul_fire) {
            base = if soul_fire {
                Color(0, 100, 255)
            } else {
                Color(255, 69, 0)
            };
        } else if settings.portal_effects && nether_portal {
            base = Color(128, 0, 128);
        } else if settings.portal_effects && end_portal {
            base = Color(0, 0, 50);
        } else if settings.dimming_mode < 2
            && !player.dimension.ends_with("the_nether")
            && !player.dimension.ends_with("the_end")
        {
            let light = if settings.dimming_mode == 1 {
                player.sky_light
            } else {
                player.local_light
            };
            let target = light.clamp(settings.min_brightness, 1.0);
            self.brightness += (target - self.brightness) * 0.1;
            base = base.scaled(self.brightness);
        } else {
            self.brightness = 1.0;
        }
        for led in &all_leds {
            frame.keys.insert(*led, base);
        }

        if player.lightning {
            for led in &all_leds {
                frame.keys.insert(*led, Color::WHITE);
            }
        } else {
            self.paint_weather(&mut frame, &player, layouts, &settings, now);
        }
        if settings.end_flash_effect
            && player.end_flash > 0.0
            && player.dimension.ends_with("the_end")
        {
            let tint = Color(150, 80, 255);
            for led in &all_leds {
                let color = frame.keys.get(led).copied().unwrap_or(Color::BLACK);
                frame
                    .keys
                    .insert(*led, color.blend(tint, player.end_flash * 0.8));
            }
            if now.duration_since(self.last_end_flash_crackle) > Duration::from_millis(120) {
                self.end_flash_crackle = random_leds(&all_leds, 0.15);
                self.last_end_flash_crackle = now;
            }
            for led in &self.end_flash_crackle {
                if let Some(color) = frame.keys.get_mut(led) {
                    *color = color.blend(Color(220, 180, 255), player.end_flash);
                }
            }
        }

        if !player.lightning {
            if settings.fire_effect && (player.on_fire || in_regular_fire || soul_fire) {
                if now.duration_since(self.last_fire_crackle) > Duration::from_millis(100) {
                    self.fire_crackle = random_leds(&all_leds, 0.2);
                    self.last_fire_crackle = now;
                }
                for led in &self.fire_crackle {
                    if let Some(color) = frame.keys.get_mut(led) {
                        *color = if soul_fire {
                            Color(173, 216, 230)
                        } else {
                            Color(200, 0, 0)
                        };
                    }
                }
            } else if settings.portal_effects && (nether_portal || end_portal) {
                if now.duration_since(self.last_portal_twinkle) > Duration::from_millis(500) {
                    self.portal_twinkle = random_leds(&all_leds, 0.2);
                    self.last_portal_twinkle = now;
                }
                for led in &self.portal_twinkle {
                    if let Some(color) = frame.keys.get_mut(led) {
                        *color = if nether_portal {
                            Color(50, 0, 100)
                        } else {
                            Color(50, 50, 50)
                        };
                    }
                }
            }
        }
        self.apply_bars(&mut frame, &player, layouts, &settings, now);
        if settings.compass_effect {
            self.paint_compass(&mut frame, &player, layouts, &settings, now);
        }

        let effect_color = if settings.water_effect
            && (player.in_water || blocks.iter().any(|b| b.contains("minecraft:water")))
        {
            Some(Color(0, 100, 255))
        } else if settings.fire_effect
            && (in_regular_fire
                || soul_fire
                || blocks.iter().any(|b| {
                    b.contains("soul_fire") || b.contains("soul_sand") || b.contains("soul_soil")
                }))
        {
            Some(if soul_fire {
                Color(0, 100, 255)
            } else {
                Color(255, 0, 0)
            })
        } else if settings.movement_highlight {
            Some(Color::WHITE)
        } else {
            None
        };
        if let Some(color) = effect_color {
            for key in &player.movement_keys {
                paint_key(&mut frame, layouts, key, color);
            }
        }
        if player.chat_received > self.last_chat_received && self.chat_pulse_started.is_none() {
            self.chat_pulse_started = Some(now);
        }
        self.last_chat_received = player.chat_received;
        if settings.chat_pulse {
            if let Some(started) = self.chat_pulse_started {
                let elapsed = now.duration_since(started).as_secs_f32();
                if elapsed <= 1.8 {
                    let t = ((elapsed / 0.6 * std::f32::consts::TAU).sin() + 1.0) * 0.5;
                    paint_key(
                        &mut frame,
                        layouts,
                        &player.chat_key,
                        base.lerp(Color::WHITE, t),
                    );
                } else {
                    self.chat_pulse_started = None;
                }
            }
        }
        frame
    }

    fn apply_bars(
        &mut self,
        frame: &mut Frame,
        player: &Snapshot,
        layouts: &[DeviceLayout],
        settings: &Settings,
        now: Instant,
    ) {
        let mode = settings.experience_locator_mode.as_str();
        let locator = settings.locator_bar
            && (mode == "locator" || mode == "both" || (mode == "auto" && player.hud_bar == 1));
        let experience = settings.experience_bar
            && (mode == "experience"
                || mode == "both"
                || (mode == "auto"
                    && (player.hud_bar == 0 || (player.hud_bar == 1 && !settings.locator_bar))));
        if experience {
            let filled = player.experience * settings.experience_bar_keys.len() as f32;
            for (i, key) in settings.experience_bar_keys.iter().enumerate() {
                paint_key(
                    frame,
                    layouts,
                    key,
                    packed(settings.experience_dim_color)
                        .lerp(packed(settings.experience_color), filled - i as f32),
                );
            }
        }
        if locator {
            let keys = &settings.locator_bar_keys;
            if mode != "both" {
                for key in keys {
                    paint_key(frame, layouts, key, packed(settings.locator_bar_color));
                }
            }
            let mut points = player.waypoints.clone();
            points.sort_by(|a, b| b.distance.total_cmp(&a.distance));
            for waypoint in points {
                let yaw = f32::from(waypoint.yaw_degrees);
                if keys.is_empty() || yaw <= -60.0 || yaw > 60.0 {
                    continue;
                }
                let position = (yaw + 60.0) / 120.0 * (keys.len() - 1) as f32;
                let step = if waypoint.distance <= 128.0 {
                    0
                } else if waypoint.distance >= 332.0 {
                    3
                } else {
                    (((waypoint.distance - 128.0) / 204.0 * 4.0) as usize).min(3)
                };
                let radius = settings.locator_dot_size.max(0.5) * [1.0, 0.65, 0.35, 0.0][step];
                let max = waypoint.color.0.max(waypoint.color.1).max(waypoint.color.2);
                let mut dot = if max == 0 {
                    waypoint.color
                } else {
                    waypoint.color.scaled(255.0 / f32::from(max))
                };
                dot = match waypoint.pitch {
                    1 => dot.lerp(Color::WHITE, 0.35),
                    -1 => dot.lerp(Color::BLACK, 0.35),
                    _ => dot,
                };
                let weights: Vec<f32> = (0..keys.len())
                    .map(|i| (1.0 - ((i as f32 - position).abs() - radius)).clamp(0.0, 1.0))
                    .collect();
                let peak = weights.iter().copied().fold(0.0_f32, f32::max);
                if peak <= 0.0 {
                    continue;
                }
                for (key, weight) in keys.iter().zip(weights) {
                    if weight <= 0.0 {
                        continue;
                    }
                    for layout in layouts {
                        if let Some(ids) = layout.keys.get(key) {
                            for id in ids {
                                overlay_led(frame, *id, dot, weight / peak);
                            }
                        }
                    }
                }
            }
        }
        if settings.health_bar {
            let damage_active = if player.taking_damage && !self.was_damaged {
                self.damage_started = Some(now);
                true
            } else {
                self.damage_started
                    .map(|at| now.duration_since(at) < Duration::from_millis(120))
                    .unwrap_or(false)
            };
            self.was_damaged = player.taking_damage;
            let chunk = 20.0 / settings.health_bar_keys.len().max(1) as f32;
            for (i, key) in settings.health_bar_keys.iter().enumerate() {
                let mut color = if damage_active {
                    Color::WHITE
                } else if player.withering {
                    Color(43, 43, 43)
                } else if player.poisoned {
                    Color(148, 120, 24)
                } else {
                    packed(settings.health_dim_color).lerp(
                        packed(settings.health_color),
                        (player.health - i as f32 * chunk) / chunk,
                    )
                };
                if !damage_active && settings.enable_absorption_overlay && player.absorption > 0.0 {
                    color = color.lerp(
                        packed(settings.absorption_color),
                        (player.absorption - i as f32 * chunk) / chunk,
                    );
                }
                paint_key(frame, layouts, key, color);
            }
        }
        let air_bar = player.in_water || player.air < 300;
        let merged = settings.merge_saturation_into_hunger && settings.saturation_air_bar;
        if settings.hunger_bar {
            let chunk = 20.0 / settings.hunger_bar_keys.len().max(1) as f32;
            for (i, key) in settings.hunger_bar_keys.iter().enumerate() {
                let color = if merged && air_bar {
                    let air_chunk = 300.0 / settings.hunger_bar_keys.len().max(1) as f32;
                    packed(settings.air_dim_color).lerp(
                        packed(settings.air_color),
                        (player.air as f32 - i as f32 * air_chunk) / air_chunk,
                    )
                } else {
                    let mut c = packed(settings.hunger_dim_color).lerp(
                        packed(settings.hunger_color),
                        (player.hunger as f32 - i as f32 * chunk) / chunk,
                    );
                    if merged {
                        c = c.lerp(
                            packed(settings.saturation_color),
                            settings.saturation_tint_strength.clamp(0.0, 1.0)
                                * ((player.saturation - i as f32 * chunk) / chunk).clamp(0.0, 1.0),
                        );
                    }
                    c
                };
                paint_key(frame, layouts, key, color);
            }
        }
        if settings.saturation_air_bar && !(merged && settings.hunger_bar) {
            let (value, maximum, full, dim) = if air_bar {
                (
                    player.air as f32,
                    300.0,
                    packed(settings.air_color),
                    packed(settings.air_dim_color),
                )
            } else {
                (
                    player.saturation,
                    20.0,
                    packed(settings.saturation_color),
                    packed(settings.saturation_dim_color),
                )
            };
            let chunk = maximum / settings.saturation_bar_keys.len().max(1) as f32;
            for (i, key) in settings.saturation_bar_keys.iter().enumerate() {
                paint_key(
                    frame,
                    layouts,
                    key,
                    dim.lerp(full, (value - i as f32 * chunk) / chunk),
                );
            }
        }
        if settings.low_health_warning {
            let low = player.health < settings.low_health_threshold;
            if low && !self.was_low_health {
                self.heartbeat = 0.1;
                self.heartbeat_rising = true;
                self.heartbeat_step = now;
            }
            self.was_low_health = low;
            if low {
                while now.duration_since(self.heartbeat_step) > Duration::from_millis(40) {
                    self.heartbeat += if self.heartbeat_rising { 0.1 } else { -0.1 };
                    if self.heartbeat >= 1.0 {
                        self.heartbeat = 1.0;
                        self.heartbeat_rising = false;
                    }
                    if self.heartbeat <= 0.1 {
                        self.heartbeat = 0.1;
                        self.heartbeat_rising = true;
                    }
                    self.heartbeat_step += Duration::from_millis(40);
                }
                let pulse = packed(settings.heartbeat_color).scaled(self.heartbeat);
                let white = Color::WHITE.scaled(self.heartbeat);
                for key in &settings.heartbeat_red_keys {
                    paint_key(frame, layouts, key, pulse);
                }
                for key in &settings.heartbeat_white_keys {
                    paint_key(frame, layouts, key, white);
                }
            }
        }
    }

    fn paint_weather(
        &mut self,
        frame: &mut Frame,
        player: &Snapshot,
        layouts: &[DeviceLayout],
        settings: &Settings,
        now: Instant,
    ) {
        if !settings.weather_effects || player.rain <= 0.01 || !player.biome_precipitation {
            self.raindrops.clear();
            return;
        }
        if now.duration_since(self.last_rain_phase) > Duration::from_millis(500) {
            self.rain_phase = !self.rain_phase;
            self.last_rain_phase = now;
        }
        let rain_color = if player.biome_snowing {
            Color(200, 200, 255)
        } else {
            Color(0, 50, 200)
        };
        let millis = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis() as u64;
        for layout in layouts {
            let keyboard = layout.keys.contains_key("Q");
            if keyboard {
                let underglow = sorted_keys(layout, "UNDERGLOW_");
                if !underglow.is_empty() {
                    let head = ((millis / 40) as usize) % underglow.len();
                    for offset in 0..8.min(underglow.len()) {
                        let ids = &layout.keys[&underglow[(head + offset) % underglow.len()]];
                        let color = rain_color.scaled(1.0 - offset as f32 / 8.0);
                        for id in ids {
                            overlay_led(frame, *id, color, 0.8);
                        }
                    }
                }
            } else if layout.keys.keys().any(|key| key.starts_with("RAM_")) {
                let keys = sorted_keys(layout, "RAM_");
                if !keys.is_empty() {
                    let state = self
                        .ram_rain
                        .entry(layout.unique_id.clone())
                        .or_insert_with(|| RamRainState {
                            drops: Vec::new(),
                            last_spawn: now - Duration::from_secs(1),
                            next_delay: Duration::from_millis(500),
                        });
                    if now.duration_since(state.last_spawn) > state.next_delay {
                        state.drops.push(RamDrop {
                            position: -2,
                            last_fall: now,
                            color: rain_color,
                        });
                        state.last_spawn = now;
                        state.next_delay = Duration::from_millis(
                            200 + (millis.wrapping_mul(6364136223846793005) % 800),
                        );
                    }
                    let mut colors = HashMap::new();
                    for drop in &mut state.drops {
                        if now.duration_since(drop.last_fall) > Duration::from_millis(90) {
                            drop.position += 1;
                            drop.last_fall = now;
                        }
                        let direction = if settings.mirror_ram_rain_direction {
                            -1
                        } else {
                            1
                        };
                        let head = if direction < 0 {
                            keys.len() as isize - 1 - drop.position
                        } else {
                            drop.position
                        };
                        for (tail, color) in [
                            (0isize, drop.color),
                            (1, drop.color.scaled(0.5)),
                            (2, drop.color.scaled(0.2)),
                        ] {
                            let position = head - tail * direction;
                            if position >= 0 && (position as usize) < keys.len() {
                                if tail == 0 {
                                    colors.insert(position as usize, color);
                                } else {
                                    colors.entry(position as usize).or_insert(color);
                                }
                            }
                        }
                    }
                    state
                        .drops
                        .retain(|drop| drop.position < keys.len() as isize + 2);
                    for (index, color) in colors {
                        paint_key_blend(frame, layout, &keys[index], color, 0.9);
                    }
                }
            } else if layout.keys.keys().any(|key| key.starts_with("PLAYER_")) {
                let keys = sorted_keys(layout, "PLAYER_");
                if !keys.is_empty() {
                    let head = ((millis / 150) % (keys.len() as u64 + 3)) as isize;
                    for (index, key) in keys.iter().enumerate() {
                        let color = if index as isize == head {
                            rain_color
                        } else if index as isize == head - 1 {
                            rain_color.scaled(0.5)
                        } else if index as isize == head - 2 {
                            rain_color.scaled(0.2)
                        } else {
                            Color::BLACK
                        };
                        paint_key_blend(frame, layout, key, color, 0.9);
                    }
                }
            } else {
                let mut leds = layout.leds.clone();
                leds.sort_unstable();
                for (i, id) in leds.iter().enumerate() {
                    if i % 2 == usize::from(!self.rain_phase) {
                        overlay_led(frame, *id, rain_color, 0.7);
                    }
                }
            }
        }

        if self.last_raindrop_spawn.map_or(true, |at| {
            now.duration_since(at) > Duration::from_millis(120)
        }) {
            let seed = millis.rotate_left(13) ^ now.elapsed().as_nanos() as u64;
            let horizontal = seed % 100 < 75;
            if horizontal {
                let first_row = (seed >> 8) as usize % 2;
                let columns = KEYBOARD_ROWS[first_row].len();
                self.raindrops.push(RainDrop {
                    column: (seed >> 16) as usize % columns,
                    row: 0,
                    vertical: false,
                    last_fall: now,
                    color: rain_color,
                });
            } else {
                let column = (seed >> 24) as usize % KEYBOARD_COLUMNS.len();
                self.raindrops.push(RainDrop {
                    column,
                    row: 0,
                    vertical: true,
                    last_fall: now,
                    color: rain_color,
                });
            }
            self.last_raindrop_spawn = Some(now);
        }
        for drop in &mut self.raindrops {
            let rain_color = drop.color;
            if now.duration_since(drop.last_fall) > Duration::from_millis(90) {
                drop.row += 1;
                drop.last_fall = now;
            }
            let path_length = if drop.vertical {
                KEYBOARD_COLUMNS[drop.column].len()
            } else {
                KEYBOARD_ROWS.len()
            };
            if drop.vertical {
                for (offset, color) in [
                    (0isize, rain_color),
                    (-1, rain_color.scaled(0.5)),
                    (-2, rain_color.scaled(0.2)),
                ] {
                    let row = drop.row as isize + offset;
                    if row >= 0 && (row as usize) < KEYBOARD_COLUMNS[drop.column].len() {
                        paint_mapped_blend(
                            frame,
                            layouts,
                            KEYBOARD_COLUMNS[drop.column][row as usize],
                            color,
                            0.9,
                        );
                    }
                }
            } else {
                for (offset, color) in [
                    (0isize, rain_color),
                    (-1, rain_color.scaled(0.5)),
                    (-2, rain_color.scaled(0.2)),
                ] {
                    let row = drop.row as isize + offset;
                    if row >= 0
                        && (row as usize) < KEYBOARD_ROWS.len()
                        && drop.column < KEYBOARD_ROWS[row as usize].len()
                    {
                        paint_mapped_blend(
                            frame,
                            layouts,
                            KEYBOARD_ROWS[row as usize][drop.column],
                            color,
                            0.9,
                        );
                    }
                }
            }
            if drop.row >= path_length + 2 {
                drop.column = usize::MAX;
            }
        }
        self.raindrops.retain(|drop| drop.column != usize::MAX);
    }

    fn paint_compass(
        &mut self,
        frame: &mut Frame,
        player: &Snapshot,
        layouts: &[DeviceLayout],
        settings: &Settings,
        now: Instant,
    ) {
        if player.compass_type == 0 || settings.compass_keys.is_empty() {
            return;
        }
        let (color, background) = match player.compass_type {
            3 => (Color(0, 191, 255), Color(0, 20, 35)),
            2 => (Color(238, 130, 238), Color(25, 0, 25)),
            _ => (Color(255, 0, 0), Color(35, 0, 0)),
        };
        let directions = &settings.compass_keys;
        for key in directions {
            paint_key(frame, layouts, key, background);
        }
        paint_key(frame, layouts, &settings.compass_center_key, color);
        if !player.compass_pointing {
            if now.duration_since(self.last_spin) > Duration::from_millis(75) {
                self.spin_index = (self.spin_index + 1) % directions.len();
                self.last_spin = now;
            }
            paint_key(
                frame,
                layouts,
                &directions[self.spin_index % directions.len()],
                color,
            );
        } else if player.compass_distance < 8.0 {
            let wave = (SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap_or_default()
                .as_millis() as f64
                / 2000.0)
                .sin() as f32;
            paint_key(
                frame,
                layouts,
                &settings.compass_center_key,
                color.scaled(0.8 + (wave + 1.0) * 0.1),
            );
        } else {
            let yaw = normalize_yaw(player.compass_yaw);
            let sector = 360.0 / directions.len() as f32;
            let index = ((yaw + sector / 2.0).rem_euclid(360.0) / sector) as usize;
            paint_key(frame, layouts, &directions[index % directions.len()], color);
        }
        if player.compass_type == 2 {
            if now.duration_since(self.last_sheen) > Duration::from_millis(60) {
                self.sheen_index = (self.sheen_index + 1) % directions.len();
                self.last_sheen = now;
            }
            for (offset, opacity) in [0.6, 0.4, 0.2].into_iter().enumerate() {
                let index = (self.sheen_index % directions.len() + directions.len()
                    - offset % directions.len())
                    % directions.len();
                let key = &directions[index];
                for layout in layouts {
                    if let Some(ids) = layout.keys.get(key) {
                        for id in ids {
                            overlay_led(frame, *id, Color(120, 0, 255), opacity);
                        }
                    }
                }
            }
        }
    }
}

pub(super) fn normalize_yaw(mut yaw: f32) -> f32 {
    while yaw <= -180.0 {
        yaw += 360.0;
    }
    while yaw > 180.0 {
        yaw -= 360.0;
    }
    yaw
}

fn packed(color: u32) -> Color {
    Color((color >> 16) as u8, (color >> 8) as u8, color as u8)
}

fn paint_key(frame: &mut Frame, layouts: &[DeviceLayout], key: &str, color: Color) {
    for layout in layouts {
        if let Some(ids) = layout.keys.get(key) {
            for id in ids {
                frame.keys.insert(*id, color);
            }
        }
    }
}

fn sorted_keys(layout: &DeviceLayout, prefix: &str) -> Vec<String> {
    let mut keys: Vec<String> = layout
        .keys
        .keys()
        .filter(|key| key.starts_with(prefix))
        .cloned()
        .collect();
    keys.sort_by_key(|key| trailing_number(key).parse::<u32>().unwrap_or(u32::MAX));
    keys
}

fn paint_key_blend(frame: &mut Frame, layout: &DeviceLayout, key: &str, color: Color, alpha: f32) {
    if let Some(ids) = layout.keys.get(key) {
        for id in ids {
            overlay_led(frame, *id, color, alpha);
        }
    }
}

fn paint_mapped_blend(
    frame: &mut Frame,
    layouts: &[DeviceLayout],
    key: &str,
    color: Color,
    alpha: f32,
) {
    for layout in layouts {
        paint_key_blend(frame, layout, key, color, alpha);
    }
}

fn overlay_led(frame: &mut Frame, id: u32, color: Color, alpha: f32) {
    let base = frame.keys.get(&id).copied().unwrap_or(Color::BLACK);
    frame.keys.insert(id, base.blend(color, alpha));
}

fn random_leds(leds: &[u32], density: f32) -> Vec<u32> {
    if leds.is_empty() {
        return Vec::new();
    }
    let mut seed = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_nanos() as u64;
    let count = (leds.len() as f32 * density) as usize;
    (0..count)
        .map(|_| {
            seed = seed.wrapping_mul(6364136223846793005).wrapping_add(1);
            leds[(seed >> 32) as usize % leds.len()]
        })
        .collect()
}

pub(super) fn biome_color(name: &str) -> Color {
    let key = name
        .rsplit(':')
        .next()
        .unwrap_or(name)
        .trim()
        .to_ascii_uppercase();
    match key.as_str() {
        "BADLANDS" => Color(204, 85, 0),
        "BADLANDS_PLATEAU" => Color(204, 85, 0),
        "BAMBOO_JUNGLE" => Color(44, 122, 29),
        "BAMBOO_JUNGLE_HILLS" => Color(44, 122, 29),
        "BASALT_DELTAS" => Color(64, 54, 54),
        "BEACH" => Color(200, 200, 122),
        "BIRCH_FOREST" => Color(78, 170, 96),
        "BIRCH_FOREST_HILLS" => Color(78, 170, 96),
        "CHERRY_GROVE" => Color(255, 112, 216),
        "COLD_OCEAN" => Color(48, 94, 128),
        "CRIMSON_FOREST" => Color(221, 8, 8),
        "DAPPLED_FOREST" => Color(218, 126, 52),
        "DARK_FOREST" => Color(42, 67, 36),
        "DARK_FOREST_HILLS" => Color(42, 67, 36),
        "DEEP_COLD_OCEAN" => Color(5, 46, 73),
        "DEEP_DARK" => Color(10, 10, 20),
        "DEEP_FROZEN_OCEAN" => Color(116, 150, 182),
        "DEEP_LUKEWARM_OCEAN" => Color(17, 32, 61),
        "DEEP_OCEAN" => Color(10, 19, 36),
        "DEEP_WARM_OCEAN" => Color(22, 41, 79),
        "DEFAULT" => Color(255, 0, 255),
        "DESERT" => Color(255, 204, 132),
        "DESERT_HILLS" => Color(255, 204, 132),
        "DESERT_LAKES" => Color(255, 204, 132),
        "DRIPSTONE_CAVES" => Color(152, 106, 69),
        "END_BARRENS" => Color(244, 244, 178),
        "END_HIGHLANDS" => Color(177, 149, 200),
        "END_MIDLANDS" => Color(192, 166, 211),
        "ERODED_BADLANDS" => Color(204, 85, 0),
        "FLOWER_FOREST" => Color(34, 139, 34),
        "FOREST" => Color(34, 139, 34),
        "FROZEN_OCEAN" => Color(169, 208, 245),
        "FROZEN_PEAKS" => Color(213, 226, 231),
        "FROZEN_RIVER" => Color(176, 224, 230),
        "GIANT_SPRUCE_TAIGA" => Color(74, 68, 33),
        "GIANT_SPRUCE_TAIGA_HILLS" => Color(74, 68, 33),
        "GIANT_TREE_TAIGA" => Color(74, 68, 33),
        "GIANT_TREE_TAIGA_HILLS" => Color(74, 68, 33),
        "GRAVELLY_MOUNTAINS" => Color(134, 134, 134),
        "GROVE" => Color(196, 196, 196),
        "ICE_SPIKES" => Color(173, 216, 230),
        "JAGGED_PEAKS" => Color(221, 221, 221),
        "JUNGLE" => Color(72, 156, 44),
        "JUNGLE_EDGE" => Color(86, 157, 50),
        "JUNGLE_HILLS" => Color(72, 156, 44),
        "LUKEWARM_OCEAN" => Color(43, 65, 109),
        "LUSH_CAVES" => Color(76, 122, 62),
        "MANGROVE_SWAMP" => Color(89, 103, 72),
        "MEADOW" => Color(65, 134, 101),
        "MODIFIED_BADLANDS_PLATEAU" => Color(204, 85, 0),
        "MODIFIED_GRAVELLY_MOUNTAINS" => Color(134, 134, 134),
        "MODIFIED_JUNGLE" => Color(27, 153, 27),
        "MODIFIED_JUNGLE_EDGE" => Color(27, 153, 27),
        "MODIFIED_WOODED_BADLANDS_PLATEAU" => Color(204, 85, 0),
        "MOUNTAIN_EDGE" => Color(87, 115, 107),
        "MOUNTAINS" => Color(34, 139, 34),
        "MUSHROOM_FIELD_SHORE" => Color(117, 81, 128),
        "MUSHROOM_FIELDS" => Color(156, 110, 170),
        "NETHER" => Color(191, 59, 59),
        "NETHER_WASTES" => Color(191, 59, 59),
        "OCEAN" => Color(36, 53, 91),
        "OLD_GROWTH_BIRCH_FOREST" => Color(136, 255, 136),
        "OLD_GROWTH_PINE_TAIGA" => Color(74, 68, 33),
        "OLD_GROWTH_SPRUCE_TAIGA" => Color(74, 68, 33),
        "PALE_GARDEN" => Color(170, 170, 170),
        "PLAINS" => Color(130, 159, 66),
        "RIVER" => Color(79, 148, 205),
        "SAVANNA" => Color(130, 145, 63),
        "SAVANNA_PLATEAU" => Color(130, 145, 63),
        "SHATTERED_SAVANNA" => Color(130, 145, 63),
        "SHATTERED_SAVANNA_PLATEAU" => Color(130, 145, 63),
        "SMALL_END_ISLANDS" => Color(128, 0, 128),
        "SNOWY_BEACH" => Color(200, 200, 122),
        "SNOWY_MOUNTAINS" => Color(224, 224, 224),
        "SNOWY_PLAINS" => Color(238, 238, 238),
        "SNOWY_SLOPES" => Color(238, 238, 238),
        "SNOWY_TAIGA" => Color(224, 224, 224),
        "SNOWY_TAIGA_HILLS" => Color(255, 255, 255),
        "SNOWY_TAIGA_MOUNTAINS" => Color(255, 255, 255),
        "SNOWY_TUNDRA" => Color(224, 224, 224),
        "SOUL_SAND_VALLEY" => Color(77, 58, 48),
        "SPARSE_JUNGLE" => Color(86, 157, 50),
        "STONE_SHORE" => Color(134, 134, 134),
        "STONY_PEAKS" => Color(134, 134, 134),
        "STONY_SHORE" => Color(134, 134, 134),
        "SUNFLOWER_PLAINS" => Color(130, 159, 66),
        "SWAMP" => Color(89, 103, 72),
        "SWAMP_HILLS" => Color(85, 102, 68),
        "TAIGA" => Color(11, 106, 95),
        "TAIGA_HILLS" => Color(11, 106, 95),
        "TAIGA_MOUNTAINS" => Color(11, 106, 95),
        "TALL_BIRCH_FOREST" => Color(78, 170, 96),
        "TALL_BIRCH_HILLS" => Color(78, 170, 96),
        "THE_END" => Color(128, 0, 128),
        "THE_VOID" => Color(0, 0, 0),
        "WARM_OCEAN" => Color(59, 88, 146),
        "WARPED_FOREST" => Color(73, 144, 123),
        "WINDSWEPT_FOREST" => Color(71, 97, 89),
        "WINDSWEPT_GRAVELLY_HILLS" => Color(134, 134, 134),
        "WINDSWEPT_HILLS" => Color(87, 115, 107),
        "WINDSWEPT_SAVANNA" => Color(130, 145, 63),
        "WOODED_BADLANDS" => Color(204, 85, 0),
        "WOODED_BADLANDS_PLATEAU" => Color(204, 85, 0),
        "WOODED_HILLS" => Color(34, 139, 34),
        "WOODED_MOUNTAINS" => Color(34, 139, 34),
        _ => Color(124, 252, 0),
    }
}
