use crate::minelights::{Color, DeviceLayout, Snapshot, Waypoint};
use crate::minelights_config::Settings;
use crate::minelights_effects::{EffectClock, Painter};
use std::time::{Duration, Instant};

fn layout(id: &str, first: u32, keys: &[&str]) -> DeviceLayout {
    let pairs = keys
        .iter()
        .enumerate()
        .map(|(index, key)| ((*key).into(), vec![first + index as u32]));
    DeviceLayout {
        keys: pairs.collect(),
        leds: (first..first + keys.len() as u32).collect(),
        unique_id: id.into(),
        ..Default::default()
    }
}

fn observing() -> Snapshot {
    Snapshot {
        in_game: true,
        dimension: "minecraft:overworld".into(),
        health: 20.0,
        hunger: 20,
        air: 300,
        sky_light: 1.0,
        local_light: 1.0,
        ..Default::default()
    }
}

fn plain_settings() -> Settings {
    Settings {
        biome_lighting: false,
        dimming_mode: 2,
        health_bar: false,
        hunger_bar: false,
        saturation_air_bar: false,
        experience_bar: false,
        locator_bar: false,
        compass_effect: false,
        low_health_warning: false,
        ..Default::default()
    }
}

struct Probe<'a> {
    start: Instant,
    painter: Painter,
    layouts: &'a [DeviceLayout],
    frames: &'a mut Vec<String>,
}

impl<'a> Probe<'a> {
    fn new(layouts: &'a [DeviceLayout], frames: &'a mut Vec<String>) -> Self {
        let start = Instant::now();
        Self {
            start,
            painter: Painter::starting_at(start),
            layouts,
            frames,
        }
    }

    fn frame(&mut self, label: &str, ms: u64, snapshot: &Snapshot, settings: &Settings) {
        let progress = Duration::from_millis(ms);
        let frame = self.painter.render_with_clock(
            snapshot.clone(),
            self.layouts,
            settings.clone(),
            self.start + progress,
            EffectClock::Fixed {
                since_epoch: Duration::from_millis(1_700_000_000_000) + progress,
                since_frame_started: Duration::from_nanos(650),
            },
        );
        let mut row = label.to_owned();
        for id in self.layouts.iter().flat_map(|layout| &layout.leds) {
            let Color(red, green, blue) = frame.keys[id];
            use std::fmt::Write;
            write!(&mut row, " {red:02x}{green:02x}{blue:02x}").unwrap();
        }
        self.frames.push(row);
    }
}

fn status_and_chat(frames: &mut Vec<String>) {
    let keyboard = [layout(
        "keyboard",
        1,
        &[
            "F1", "F2", "F5", "F6", "F9", "F10", "1", "2", "3", "W", "Y", "T",
        ],
    )];
    let mut probe = Probe::new(&keyboard, frames);
    let mut settings = Settings {
        health_bar_keys: vec!["F1".into(), "F2".into()],
        hunger_bar_keys: vec!["F5".into(), "F6".into()],
        saturation_bar_keys: vec!["F9".into(), "F10".into()],
        experience_bar_keys: vec!["1".into(), "2".into(), "3".into()],
        locator_bar_keys: vec!["1".into(), "2".into(), "3".into()],
        heartbeat_red_keys: vec!["W".into()],
        heartbeat_white_keys: vec!["Y".into()],
        absorption_color: 0x12_34_56,
        locator_dot_size: 0.5,
        movement_highlight: false,
        dimming_mode: 2,
        biome_lighting: false,
        ..Default::default()
    };
    let mut player = Snapshot {
        health: 15.0,
        absorption: 5.0,
        hunger: 13,
        saturation: 7.0,
        experience: 0.55,
        chat_key: "T".into(),
        movement_keys: vec!["W".into()],
        ..observing()
    };
    probe.frame("bars", 0, &player, &settings);
    player.taking_damage = true;
    player.chat_received = 1;
    probe.frame("damage-chat", 100, &player, &settings);
    player.taking_damage = false;
    player.poisoned = true;
    player.absorption = 0.0;
    player.health = 6.0;
    probe.frame("poison-damage-linger-heartbeat", 200, &player, &settings);
    probe.frame("poison-after-damage", 250, &player, &settings);
    player.withering = true;
    player.in_water = true;
    player.air = 125;
    settings.merge_saturation_into_hunger = true;
    probe.frame("wither-water-merged-oxygen", 300, &player, &settings);
    player.in_water = false;
    player.air = 300;
    settings.experience_locator_mode = "both".into();
    player.waypoints = vec![
        Waypoint {
            yaw_degrees: 0,
            color: Color(30, 150, 0),
            pitch: -1,
            distance: 50.0,
        },
        Waypoint {
            yaw_degrees: -24,
            color: Color(80, 0, 150),
            pitch: 1,
            distance: 200.0,
        },
        Waypoint {
            yaw_degrees: 61,
            color: Color(255, 0, 0),
            pitch: 0,
            distance: 10.0,
        },
    ];
    probe.frame(
        "experience-locator-merged-saturation",
        580,
        &player,
        &settings,
    );
    settings.experience_locator_mode = "auto".into();
    player.hud_bar = 1;
    probe.frame("contextual-locator", 2_100, &player, &settings);
    player.hud_bar = 2;
    probe.frame("contextual-mount", 2_150, &player, &settings);
    probe.frame("world-unloaded", 2_200, &Snapshot::default(), &settings);
    settings.enabled = false;
    probe.frame("lighting-disabled", 2_250, &player, &settings);
}

fn dimension_and_hazards(frames: &mut Vec<String>) {
    let hardware = [layout(
        "keyboard",
        1,
        &["Q", "W", "A", "S", "D", "SPACE", "F1", "F2"],
    )];
    let mut probe = Probe::new(&hardware, frames);
    let mut settings = Settings {
        biome_lighting: true,
        dimming_mode: 0,
        ..plain_settings()
    };
    let mut player = Snapshot {
        biome: "minecraft:plains".into(),
        local_light: 0.1,
        movement_keys: vec!["W".into(), "SPACE".into()],
        ..observing()
    };
    probe.frame("plains-transition-start", 0, &player, &settings);
    probe.frame("plains-dimmed", 750, &player, &settings);
    settings.dimming_mode = 1;
    player.sky_light = 0.4;
    player.biome = "minecraft:cherry_grove".into();
    probe.frame("cherry-grove-sky-dimming", 1_200, &player, &settings);
    probe.frame("cherry-grove-transition", 1_575, &player, &settings);
    player.dimension = "minecraft:the_nether".into();
    player.biome = "minecraft:crimson_forest".into();
    player.feet = "minecraft:nether_portal".into();
    probe.frame("nether-portal", 2_000, &player, &settings);
    player.below = "minecraft:lava".into();
    probe.frame("lava-priority", 2_250, &player, &settings);
    player.feet.clear();
    player.below = "minecraft:soul_sand".into();
    player.on_fire = true;
    probe.frame("soul-fire", 2_500, &player, &settings);
    player.on_fire = false;
    player.below.clear();
    player.feet = "minecraft:end_portal".into();
    player.dimension = "minecraft:the_end".into();
    player.biome = "minecraft:end_highlands".into();
    player.end_flash = 0.7;
    probe.frame("end-portal-and-flash", 2_750, &player, &settings);
    player.feet = "minecraft:water".into();
    player.dimension = "minecraft:overworld".into();
    player.lightning = true;
    probe.frame("lightning-and-water", 3_000, &player, &settings);
}

fn weather(frames: &mut Vec<String>) {
    let hardware = [
        layout(
            "keyboard",
            1,
            &[
                "Q",
                "ESCAPE",
                "F1",
                "UNDERGLOW_1",
                "UNDERGLOW_2",
                "UNDERGLOW_3",
            ],
        ),
        layout("ram", 20, &["RAM_1", "RAM_2", "RAM_3", "RAM_4"]),
        layout(
            "controller",
            30,
            &["PLAYER_1", "PLAYER_2", "PLAYER_3", "PLAYER_4"],
        ),
        layout("generic", 40, &["AUX_1", "AUX_2", "AUX_3", "AUX_4"]),
    ];
    let mut probe = Probe::new(&hardware, frames);
    let mut settings = plain_settings();
    let mut player = Snapshot {
        rain: 0.9,
        biome_precipitation: true,
        ..observing()
    };
    probe.frame("rain-spawn", 0, &player, &settings);
    probe.frame("rain-fall", 95, &player, &settings);
    probe.frame("rain-ram-first", 190, &player, &settings);
    settings.mirror_ram_rain_direction = true;
    probe.frame("rain-ram-mirrored", 285, &player, &settings);
    player.biome_snowing = true;
    probe.frame("snow", 550, &player, &settings);
    player.biome_precipitation = false;
    probe.frame("no-precipitation-biome", 650, &player, &settings);
}

fn compass(frames: &mut Vec<String>) {
    let hardware = [layout(
        "keypad",
        1,
        &[
            "NUMPAD8", "NUMPAD9", "NUMPAD6", "NUMPAD3", "NUMPAD2", "NUMPAD1", "NUMPAD4", "NUMPAD7",
            "NUMPAD5",
        ],
    )];
    let mut probe = Probe::new(&hardware, frames);
    let settings = Settings {
        compass_effect: true,
        ..plain_settings()
    };
    let mut player = Snapshot {
        compass_type: 1,
        compass_pointing: true,
        compass_yaw: -90.0,
        compass_distance: 60.0,
        ..observing()
    };
    probe.frame("ordinary-compass", 0, &player, &settings);
    player.compass_distance = 7.9;
    probe.frame("ordinary-compass-near-target", 50, &player, &settings);
    player.compass_type = 2;
    player.compass_yaw = 135.0;
    player.compass_distance = 34.0;
    probe.frame("lodestone-with-shimmer", 110, &player, &settings);
    probe.frame("lodestone-shimmer-advances", 230, &player, &settings);
    player.compass_type = 3;
    player.compass_yaw = 450.0;
    probe.frame("recovery-compass", 290, &player, &settings);
    player.compass_pointing = false;
    for ms in (350..=800).step_by(50) {
        probe.frame(&format!("targetless-wobble-{ms}"), ms, &player, &settings);
    }
    player.compass_type = 0;
    probe.frame("targetless-hidden", 900, &player, &settings);
}

#[test]
fn representative_rgb_frames_match_legacy_2_4_1() {
    let mut frames = Vec::new();
    status_and_chat(&mut frames);
    dimension_and_hazards(&mut frames);
    weather(&mut frames);
    compass(&mut frames);
    // Exact bytes captured from commit be4ea332 with fixed clock reads and the same snapshots.
    assert_eq!(frames.len(), EXPECTED.len());
    for (actual, expected) in frames.iter().zip(EXPECTED) {
        assert_eq!(actual, expected);
    }
}

const EXPECTED: &[&str] = &[
    "bars 881a2b 8e0000 ffa500 613c00 98c100 283200 00ff00 03b003 0a1e0a 000000 000000 000000",
    "damage-chat ffffff ffffff ffa500 613c00 98c100 283200 00ff00 03b003 0a1e0a 000000 000000 7f7f7f",
    "poison-damage-linger-heartbeat ffffff ffffff ffa500 613c00 98c100 283200 00ff00 03b003 0a1e0a 190000 191919 ededed",
    "poison-after-damage 947818 947818 ffa500 613c00 98c100 283200 00ff00 03b003 0a1e0a 330000 333333 ffffff",
    "wither-water-merged-oxygen 2b2b2b 2b2b2b 90b4c7 000032 000000 000000 00ff00 03b003 0a1e0a 0064ff 4c4c4c ededed",
    "experience-locator-merged-saturation 2b2b2b 2b2b2b f5b400 613c00 000000 000000 559063 21a500 156105 ff0000 ffffff 060606",
    "contextual-locator 2b2b2b 2b2b2b f5b400 613c00 000000 000000 567665 21a500 155705 cb0000 cbcbcb 000000",
    "contextual-mount 2b2b2b 2b2b2b f5b400 613c00 000000 000000 000000 000000 000000 b20000 b2b2b2 000000",
    "world-unloaded ff0000 ff0000 ff0000 ff0000 ff0000 ff0000 ff0000 ff0000 ff0000 ff0000 ff0000 ff0000",
    "lighting-disabled 000000 000000 000000 000000 000000 000000 000000 000000 000000 000000 000000 000000",
    "plains-transition-start 000000 ffffff 000000 000000 000000 ffffff 000000 000000",
    "plains-dimmed 6e8637 ffffff 6e8637 6e8637 6e8637 ffffff 6e8637 6e8637",
    "cherry-grove-sky-dimming 687f35 ffffff 687f35 687f35 687f35 ffffff 687f35 687f35",
    "cherry-grove-transition 92666b ffffff 92666b 92666b 92666b ffffff 92666b 92666b",
    "nether-portal 320064 ffffff 800080 800080 800080 ffffff 800080 800080",
    "lava-priority ff4500 ff0000 c80000 ff4500 ff4500 ff0000 ff4500 ff4500",
    "soul-fire 0064ff 0064ff 0064ff 0064ff 0064ff 0064ff 0064ff 0064ff",
    "end-portal-and-flash 323232 ffffff 542ca4 542ca4 542ca4 ffffff 542ca4 542ca4",
    "lightning-and-water ffffff 0064ff ffffff ffffff ffffff 0064ff ffffff ffffff",
    "rain-spawn 000000 000000 000000 00228c 001d78 0028a0 000000 000000 000000 000000 002db4 000000 000000 000000 000000 00238c 000000 00238c",
    "rain-fall 000000 000000 000000 001d78 0028a0 00228c 000000 000000 000000 000000 002db4 000000 000000 000000 000000 00238c 000000 00238c",
    "rain-ram-first 000000 000000 000000 0028a0 00228c 001d78 002db4 000000 000000 000000 00165a 002db4 000000 000000 000000 00238c 000000 00238c",
    "rain-ram-mirrored 000000 000000 000000 0028a0 00228c 001d78 000000 000000 002db4 00165a 000924 00165a 002db4 000000 000000 00238c 000000 00238c",
    "snow 000000 000000 b4b4e5 a0a0cc 8c8cb2 787898 000000 002db4 00165a 000924 000000 000000 24242d 5a5a72 8c8cb2 000000 8c8cb2 000000",
    "no-precipitation-biome 000000 000000 000000 000000 000000 000000 000000 000000 000000 000000 000000 000000 000000 000000 000000 000000 000000 000000",
    "ordinary-compass 230000 230000 230000 230000 230000 230000 ff0000 230000 ff0000",
    "ordinary-compass-near-target 230000 230000 230000 230000 230000 230000 230000 230000 d20000",
    "lodestone-with-shimmer 3f0075 5200a3 190019 ee82ee 190019 190019 190019 2c0047 ee82ee",
    "lodestone-shimmer-advances 2c0047 3f0075 5200a3 ee82ee 190019 190019 190019 190019 ee82ee",
    "recovery-compass 001423 001423 00bfff 001423 001423 001423 001423 001423 00bfff",
    "targetless-wobble-350 00bfff 001423 001423 001423 001423 001423 001423 001423 00bfff",
    "targetless-wobble-400 00bfff 001423 001423 001423 001423 001423 001423 001423 00bfff",
    "targetless-wobble-450 001423 00bfff 001423 001423 001423 001423 001423 001423 00bfff",
    "targetless-wobble-500 001423 00bfff 001423 001423 001423 001423 001423 001423 00bfff",
    "targetless-wobble-550 001423 00bfff 001423 001423 001423 001423 001423 001423 00bfff",
    "targetless-wobble-600 001423 00bfff 001423 001423 001423 001423 001423 001423 00bfff",
    "targetless-wobble-650 001423 00bfff 001423 001423 001423 001423 001423 001423 00bfff",
    "targetless-wobble-700 00bfff 001423 001423 001423 001423 001423 001423 001423 00bfff",
    "targetless-wobble-750 00bfff 001423 001423 001423 001423 001423 001423 001423 00bfff",
    "targetless-wobble-800 001423 001423 001423 001423 001423 001423 001423 00bfff 00bfff",
    "targetless-hidden 000000 000000 000000 000000 000000 000000 000000 000000 000000",
];
