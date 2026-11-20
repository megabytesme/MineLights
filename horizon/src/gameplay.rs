use std::cell::RefCell;

use mod_api::ClientHost;

use super::minelights::{Color, Snapshot, Waypoint};
use super::minelights_config::Settings;
use super::minelights_effects::normalize_yaw;

const OVERWORLD: &str = "minecraft:overworld";

/// An item with a lodestone component or a player's recorded death block.
#[derive(Clone)]
pub struct CompassTarget {
    pub dimension: String,
    pub block: [i32; 3],
}

/// Exact client-only inputs that the pinned SDK's typed host cannot read.
pub struct FrameReads {
    pub biome: String,
    pub biome_precipitation: bool,
    pub biome_snowing: bool,
    pub eye_y: f64,
    pub packed_light: u8,
    pub rain_override: Option<f32>,
    pub world_spawn: Option<[f64; 3]>,
    pub compass_candidates: Vec<(u8, Option<CompassTarget>)>,
    pub movement_bindings: Vec<String>,
    pub chat_binding: String,
    pub hud_bar: u8,
    pub chat_received: u64,
    pub taking_damage: bool,
    // On this SDK the public body reads use the logical server's resolver and
    // return None for the client player. Keep the original client observations.
    pub air: i32,
    pub on_fire: bool,
    pub in_water: bool,
    pub lightning: bool,
    pub end_flash: f32,
}

#[derive(PartialEq, Eq)]
pub enum LocatorId {
    Uuid(u128),
    Name(String),
}

#[derive(Clone, Copy)]
pub enum LocatorTarget {
    Empty,
    Block([i32; 3]),
    Chunk([i32; 2]),
    Azimuth(f32),
}

struct Locator {
    id: LocatorId,
    target: LocatorTarget,
    color: u32,
}

thread_local! {
    static LANGUAGE: RefCell<String> = const { RefCell::new(String::new()) };
    static NATIVE_FRAME: RefCell<Option<Option<FrameReads>>> = const { RefCell::new(None) };
    static LOCATORS: RefCell<Vec<Locator>> = const { RefCell::new(Vec::new()) };
}

/// Called immediately before the SDK frame hook on the client thread.
pub fn observe_frame(frame: Option<FrameReads>) {
    NATIVE_FRAME.with(|current| *current.borrow_mut() = Some(frame));
}

/// Prevent secondary clients from submitting a frame or restoring P1's devices.
pub fn discard_frame() {
    NATIVE_FRAME.with(|current| *current.borrow_mut() = None);
}

fn take_frame() -> Option<Option<FrameReads>> {
    NATIVE_FRAME.with(|current| current.take())
}

/// The pinned client session service gives no language while the title is open.
pub fn observe_language(language: &str) {
    LANGUAGE.with(|current| {
        let mut current = current.borrow_mut();
        if current.as_str() != language {
            language.clone_into(&mut current);
        }
    });
}

pub(super) fn client_language() -> String {
    LANGUAGE.with(|current| {
        let current = current.borrow();
        if current.is_empty() {
            "en_us".to_owned()
        } else {
            current.clone()
        }
    })
}

pub fn clear_locators() {
    LOCATORS.with(|locators| locators.borrow_mut().clear());
}

/// Preserve Horizon's replacement, ordering and update semantics for tracked waypoints.
pub fn observe_locator(operation: i32, id: LocatorId, target: LocatorTarget, color: Option<i32>) {
    LOCATORS.with(|locators| {
        let mut locators = locators.borrow_mut();
        let at = locators.iter().position(|locator| locator.id == id);
        match (operation, at) {
            (0, at) => {
                let locator = Locator {
                    color: color.map_or_else(|| default_locator_color(&id), |color| color as u32),
                    id,
                    target,
                };
                if let Some(at) = at {
                    locators[at] = locator;
                } else {
                    locators.push(locator);
                }
            }
            (1, Some(at)) => {
                locators.remove(at);
            }
            (_, Some(at)) => {
                let old = &mut locators[at].target;
                if !matches!(target, LocatorTarget::Empty)
                    && std::mem::discriminant(old) == std::mem::discriminant(&target)
                {
                    *old = target;
                }
            }
            _ => {}
        }
    });
}

fn default_locator_color(id: &LocatorId) -> u32 {
    let hash = match id {
        LocatorId::Uuid(uuid) => {
            let halves = (*uuid >> 64) as u64 ^ *uuid as u64;
            (halves >> 32) as u32 ^ halves as u32
        }
        LocatorId::Name(name) => name.chars().fold(0u32, |hash, ch| {
            hash.wrapping_mul(31).wrapping_add(ch as u32)
        }),
    };
    let r = 96 + ((hash >> 16) & 0x5f);
    let g = 96 + ((hash >> 8) & 0x5f);
    let b = 96 + (hash & 0x5f);
    (r << 16) | (g << 8) | b
}

fn friendly_key_name(binding: &str) -> String {
    let Some(raw) = binding.strip_prefix("key.keyboard.") else {
        return "T".into();
    };
    let name = raw.to_ascii_uppercase().replace('.', "_").replace('-', "_");
    match name.as_str() {
        "LEFT_SHIFT" => "LSHIFT".into(),
        "RIGHT_SHIFT" => "RSHIFT".into(),
        "LEFT_CONTROL" => "LCTRL".into(),
        "RIGHT_CONTROL" => "RCTRL".into(),
        "LEFT_ALT" => "LALT".into(),
        "RIGHT_ALT" => "RALT".into(),
        "ESC" => "ESCAPE".into(),
        "RETURN" => "ENTER".into(),
        "BACKQUOTE" => "BACKTICK".into(),
        "BRACKET_LEFT" => "LEFT_BRACKET".into(),
        "BRACKET_RIGHT" => "RIGHT_BRACKET".into(),
        "ARROW_UP" => "UP".into(),
        "ARROW_DOWN" => "DOWN".into(),
        "ARROW_LEFT" => "LEFT".into(),
        "ARROW_RIGHT" => "RIGHT".into(),
        "SPACE" => "SPACE".into(),
        "NUMPAD_0" => "NUMPAD0".into(),
        "NUMPAD_1" => "NUMPAD1".into(),
        "NUMPAD_2" => "NUMPAD2".into(),
        "NUMPAD_3" => "NUMPAD3".into(),
        "NUMPAD_4" => "NUMPAD4".into(),
        "NUMPAD_5" => "NUMPAD5".into(),
        "NUMPAD_6" => "NUMPAD6".into(),
        "NUMPAD_7" => "NUMPAD7".into(),
        "NUMPAD_8" => "NUMPAD8".into(),
        "NUMPAD_9" => "NUMPAD9".into(),
        _ => name,
    }
}

pub(super) fn compass(
    settings: &Settings,
    dimension: &str,
    position: [f64; 3],
    yaw: f32,
    world_spawn: Option<[f64; 3]>,
    candidates: &[(u8, Option<CompassTarget>)],
    last_death: Option<CompassTarget>,
) -> (u8, bool, f32, f32) {
    let carried = (!settings.ignore_inventory_compass)
        .then(|| {
            candidates
                .iter()
                .find(|(kind, _)| *kind == settings.compass_priority)
                .or_else(|| candidates.first())
        })
        .flatten();
    let spawn = world_spawn.filter(|_| dimension == OVERWORLD);
    let target_center = |target: Option<&CompassTarget>| {
        target
            .filter(|target| target.dimension == dimension)
            .map(|target| target.block.map(|coordinate| f64::from(coordinate) + 0.5))
    };
    let (kind, target) = match carried {
        Some((1, _)) => (1, spawn),
        Some((2, target)) => (2, target_center(target.as_ref())),
        Some((3, _)) => (3, target_center(last_death.as_ref())),
        _ if settings.always_show_compass || settings.ignore_inventory_compass => {
            // Player Spawn remains a world-spawn fallback, as in the legacy Horizon mod.
            let target = if settings.compass_fallback_target == 1 {
                Some([0.0; 3])
            } else {
                spawn
            };
            (1, target)
        }
        _ => (0, None),
    };
    if let Some(target) = target {
        let dx = target[0] - position[0];
        let dz = target[2] - position[2];
        let distance_squared = dx * dx + dz * dz;
        if distance_squared >= 1.0e-5
            && let Some(heading) = normalize_yaw((-dx).atan2(dz).to_degrees() - f64::from(yaw))
        {
            return (kind, true, heading as f32, distance_squared.sqrt() as f32);
        }
    }
    let kind = if settings.spin_compass_without_target {
        kind
    } else {
        0
    };
    (kind, false, 0.0, 0.0)
}

fn waypoint(locator: &Locator, position: [f64; 3], eye_y: f64, yaw: f32) -> Option<Waypoint> {
    let [x, _, z] = position;
    let (dx, dy, dz) = match locator.target {
        LocatorTarget::Empty => return None,
        LocatorTarget::Block([tx, ty, tz]) => (
            f64::from(tx) + 0.5 - x,
            f64::from(ty) + 0.5 - eye_y,
            f64::from(tz) + 0.5 - z,
        ),
        LocatorTarget::Chunk([cx, cz]) => (
            f64::from(cx) * 16.0 + 8.5 - x,
            0.0,
            f64::from(cz) * 16.0 + 8.5 - z,
        ),
        LocatorTarget::Azimuth(_) => (0.0, 0.0, 0.0),
    };
    let angle = match locator.target {
        LocatorTarget::Azimuth(angle) => f64::from(angle).to_degrees(),
        _ => (-dx).atan2(dz).to_degrees(),
    };
    let relative_yaw = normalize_yaw(angle - f64::from(yaw))?;
    let [_, r, g, b] = locator.color.to_be_bytes();
    Some(Waypoint {
        yaw_degrees: relative_yaw.round() as i16,
        color: Color(r, g, b),
        pitch: if dy > 1.0 {
            1
        } else if dy < -1.0 {
            -1
        } else {
            0
        },
        distance: (dx * dx + dy * dy + dz * dz).sqrt() as f32,
    })
}

fn light_levels(packed: u8, sky_darken: i32) -> (f32, f32) {
    let sky = (packed >> 4).saturating_sub(sky_darken.clamp(0, 15) as u8);
    let local = (packed & 15).max(sky);
    (f32::from(sky) / 15.0, f32::from(local) / 15.0)
}

pub(super) fn snapshot(host: &dyn ClientHost, settings: &Settings) -> Option<Snapshot> {
    // An observed primary-client menu frame and a skipped secondary frame have
    // different meanings: only the first may release the devices.
    let raw = take_frame()?;
    let (Some(raw), Some(player)) = (raw, host.local_player()) else {
        return Some(Snapshot::default());
    };
    let entity = player.entity();
    let Some(level) = entity.level(host) else {
        return Some(Snapshot::default());
    };
    let (Some(position), Some([yaw, _]), Some(living), Some(food), Some(experience)) = (
        entity.position(host),
        entity.rotation(host),
        host.living(entity),
        player.food(host),
        player.experience(host),
    ) else {
        return Some(Snapshot::default());
    };
    let [x, y, z] = position.map(|axis| axis.floor() as i32);
    let pos = [x, y, z];
    let dimension = level.dimension_id();
    let block = |pos| {
        level
            .block(host, pos)
            .map_or_else(|| "minecraft:air".to_owned(), |key| key.id(host))
    };
    let rain = if dimension == OVERWORLD {
        raw.rain_override
            .or_else(|| level.weather(host).map(|(rain, _)| rain))
            .unwrap_or(0.0)
    } else {
        0.0
    };
    let (sky_light, local_light) =
        light_levels(raw.packed_light, level.get_sky_darken(host).unwrap_or(0));
    let last_death = player.last_death_location(host).map(|target| {
        let pos = target.pos();
        CompassTarget {
            dimension: target.dimension().to_string(),
            block: [pos.x, pos.y, pos.z],
        }
    });
    let (compass_type, compass_pointing, compass_yaw, compass_distance) = compass(
        settings,
        dimension,
        position,
        yaw,
        raw.world_spawn,
        &raw.compass_candidates,
        last_death,
    );
    let waypoints = LOCATORS.with(|locators| {
        locators
            .borrow()
            .iter()
            .filter_map(|locator| waypoint(locator, position, raw.eye_y, yaw))
            .collect()
    });
    let affected = |name| {
        host.effect_key(name)
            .is_some_and(|effect| living.potion_affected(host, effect))
    };
    Some(Snapshot {
        in_game: true,
        dimension: dimension.to_owned(),
        biome: raw.biome,
        feet: block(pos),
        below: block([x, y.saturating_sub(1), z]),
        head: block([x, raw.eye_y.floor() as i32, z]),
        health: living.health(host).unwrap_or_default(),
        absorption: living.absorption(host).unwrap_or_default(),
        hunger: food.level,
        saturation: food.saturation,
        air: raw.air,
        experience: experience.progress,
        hud_bar: raw.hud_bar,
        rain,
        biome_precipitation: raw.biome_precipitation,
        biome_snowing: raw.biome_snowing,
        sky_light,
        local_light,
        end_flash: raw.end_flash,
        compass_type,
        compass_pointing,
        compass_yaw,
        compass_distance,
        waypoints,
        chat_received: raw.chat_received,
        chat_key: friendly_key_name(&raw.chat_binding),
        on_fire: raw.on_fire,
        poisoned: affected("minecraft:poison"),
        withering: affected("minecraft:wither"),
        taking_damage: raw.taking_damage,
        in_water: raw.in_water,
        lightning: raw.lightning,
        movement_keys: raw
            .movement_bindings
            .iter()
            .filter(|binding| binding.starts_with("key.keyboard."))
            .map(|binding| friendly_key_name(binding))
            .collect(),
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn locator_tracks_replaces_updates_and_clears_in_game_order() {
        clear_locators();
        let a = || LocatorId::Name("alpha".to_owned());
        let b = || LocatorId::Uuid(0x1234abcdef9876543210fedcba4321);
        let colors = || {
            LOCATORS.with(|locators| {
                locators
                    .borrow()
                    .iter()
                    .map(|locator| locator.color)
                    .collect::<Vec<_>>()
            })
        };
        // An update received before a track does not create a dot.
        observe_locator(2, a(), LocatorTarget::Azimuth(0.0), None);
        assert!(colors().is_empty());
        observe_locator(0, a(), LocatorTarget::Block([0, 5, 1]), Some(0x00112233));
        observe_locator(0, b(), LocatorTarget::Chunk([1, 0]), None);
        assert_eq!(colors(), [0x00112233, default_locator_color(&b())]);

        // Updating a dot moves it, retaining its color and slot. Changing
        // its target kind requires a new track, which replaces that slot.
        observe_locator(2, a(), LocatorTarget::Azimuth(0.0), None);
        observe_locator(2, a(), LocatorTarget::Block([0, -2, 1]), Some(0));
        LOCATORS.with(|locators| {
            let locators = locators.borrow();
            let dot = waypoint(&locators[0], [0.5, 0.0, 0.5], 1.5, 0.0).unwrap();
            assert_eq!(dot.yaw_degrees, 0);
            assert_eq!(dot.pitch, -1);
            assert_eq!(dot.color, Color(0x11, 0x22, 0x33));
            assert!((dot.distance - 10f32.sqrt()).abs() < 0.0001);
        });
        observe_locator(0, a(), LocatorTarget::Azimuth(0.0), Some(0x00445566));
        assert_eq!(colors(), [0x00445566, default_locator_color(&b())]);

        observe_locator(1, a(), LocatorTarget::Empty, None);
        assert_eq!(colors(), [default_locator_color(&b())]);
        observe_frame(None);
        clear_locators();
        assert!(colors().is_empty());
        LOCATORS.with(|locators| assert!(locators.borrow().is_empty()));
    }

    #[test]
    fn waypoint_yaw_ignores_nonfinite_values_and_wraps_finite_values() {
        let collect = |azimuth, camera_yaw| {
            let locator = Locator {
                id: LocatorId::Uuid(1),
                target: LocatorTarget::Azimuth(azimuth),
                color: 0x00112233,
            };
            waypoint(&locator, [0.0; 3], 1.5, camera_yaw).map(|dot| dot.yaw_degrees)
        };
        for invalid in [f32::NAN, f32::INFINITY, f32::NEG_INFINITY] {
            assert_eq!(collect(invalid, 0.0), None);
            assert_eq!(collect(0.0, invalid), None);
        }
        assert_eq!(collect(std::f32::consts::FRAC_PI_2, 30.0), Some(60));
        assert!((-180..=180).contains(&collect(f32::MAX, 0.0).unwrap()));
        assert_eq!(normalize_yaw(-180.0), Some(180.0));
        assert_eq!(normalize_yaw(360.0), Some(0.0));
    }

    #[test]
    fn a_primary_menu_frame_is_observed_once_and_a_secondary_frame_is_skipped() {
        discard_frame();
        assert!(take_frame().is_none());
        observe_frame(None);
        assert!(matches!(take_frame(), Some(None)));
        assert!(take_frame().is_none());
        observe_frame(None);
        discard_frame();
        assert!(take_frame().is_none());
    }

    #[test]
    fn packed_client_light_preserves_stored_sky_and_block_brightness() {
        assert_eq!(light_levels(0xf1, 0), (1.0, 1.0));
        assert_eq!(light_levels(0xf1, 15), (0.0, 1.0 / 15.0));
        assert_eq!(light_levels(0x18, 5), (0.0, 8.0 / 15.0));
        assert_eq!(light_levels(0x00, 100), (0.0, 0.0));
    }

    #[test]
    fn keyboard_names_retain_device_mapping_and_mouse_chat_fallback() {
        assert_eq!(friendly_key_name("key.keyboard.left.shift"), "LSHIFT");
        assert_eq!(friendly_key_name("key.keyboard.arrow-right"), "RIGHT");
        assert_eq!(friendly_key_name("key.keyboard.numpad.6"), "NUMPAD6");
        assert_eq!(friendly_key_name("key.mouse.middle"), "T");
    }
}
