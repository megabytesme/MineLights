use super::minelights::{Color, Snapshot, Waypoint};
use super::minelights_config::Settings;
use super::minelights_effects::normalize_yaw;

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

fn compass_type(stack: &horizon_client::items::ItemStack) -> Option<u8> {
    match stack.item.as_ref() {
        "minecraft:recovery_compass" => Some(3),
        "minecraft:compass" => Some(
            if stack
                .extra
                .as_ref()
                .and_then(|data| data.other("minecraft:lodestone_tracker"))
                .and_then(|tracker| tracker.get("target"))
                .is_some()
            {
                2
            } else {
                1
            },
        ),
        _ => None,
    }
}

fn compass_target(
    stack: &horizon_client::items::ItemStack,
    game: &super::Game<'_>,
    dimension: &str,
) -> Option<[f64; 3]> {
    match compass_type(stack)? {
        1 if dimension == "minecraft:overworld" => {
            game.spawn.map(|spawn| [spawn.x, spawn.y, spawn.z])
        }
        2 => {
            let tracker = stack.extra.as_ref()?.other("minecraft:lodestone_tracker")?;
            let target = tracker.get("target")?;
            let target_dimension = match target.get("dimension")? {
                horizon_world::nbt::Tag::String(value) => value.to_string_lossy(),
                _ => return None,
            };
            if target_dimension != dimension {
                return None;
            }
            match target.get("pos")? {
                horizon_world::nbt::Tag::IntArray(position) if position.len() >= 3 => Some([
                    f64::from(position[0]) + 0.5,
                    f64::from(position[1]) + 0.5,
                    f64::from(position[2]) + 0.5,
                ]),
                _ => None,
            }
        }
        3 => game
            .level
            .last_death
            .as_ref()
            .and_then(|(death_dimension, position)| {
                (death_dimension == dimension).then_some([
                    f64::from(position[0]) + 0.5,
                    f64::from(position[1]) + 0.5,
                    f64::from(position[2]) + 0.5,
                ])
            }),
        _ => None,
    }
}

pub(super) fn snapshot(game: &super::Game<'_>, settings: &Settings) -> Snapshot {
    let Some(player) = game.player.as_ref() else {
        return Snapshot::default();
    };
    let x = player.position.x.floor() as i32;
    let y = player.position.y.floor() as i32;
    let z = player.position.z.floor() as i32;
    let head_y = (player.position.y + game.eye as f64).floor() as i32;
    let block_name = |bx: i32, by: i32, bz: i32| {
        game.res
            .facts()
            .get(game.world.block(bx, by, bz))
            .name
            .to_string()
    };
    let biome_id = game
        .world
        .chunks
        .get(&(x >> 4, z >> 4))
        .map(|chunk| chunk.biome(x, y, z))
        .unwrap_or_else(|| game.res.biomes().id("minecraft:plains"));
    let biome_info = game.res.biomes().get(biome_id);
    let biome = biome_info.name.clone();
    let biome_precipitation = biome_info.precipitation;
    let biome_snowing = biome_info.snows_at(x, y, z).unwrap_or(false);
    let dimension = game.dim.id().to_owned();
    let rain = if dimension == "minecraft:overworld" {
        game.world.weather.levels(1.0).0
    } else {
        0.0
    };
    let packed_light = game.world.light(x, y, z);
    let sky_level = packed_light >> 4;
    let adjusted_sky = sky_level.saturating_sub(game.sky_darken().clamp(0, 15) as u8);
    let sky_light = f32::from(adjusted_sky) / 15.0;
    let local_light = f32::from((packed_light & 15).max(adjusted_sky)) / 15.0;
    let movement_keys = [
        "key.forward",
        "key.back",
        "key.left",
        "key.right",
        "key.jump",
        "key.sneak",
        "key.sprint",
    ]
    .into_iter()
    .map(|binding| game.settings.keys.get(binding))
    .filter(|binding| binding.starts_with("key.keyboard."))
    .map(friendly_key_name)
    .collect();
    let mut compass_candidates = Vec::new();
    if let Some(stack) = game.inventory.get(game.selected).and_then(Option::as_ref) {
        if let Some(kind) = compass_type(stack) {
            compass_candidates.push((kind, compass_target(stack, game, &dimension)));
        }
    }
    if let Some(stack) = game.offhand.as_ref() {
        if let Some(kind) = compass_type(stack) {
            compass_candidates.push((kind, compass_target(stack, game, &dimension)));
        }
    }
    for stack in game.inventory.iter().flatten() {
        if let Some(kind) = compass_type(stack) {
            compass_candidates.push((kind, compass_target(stack, game, &dimension)));
        }
    }
    let preferred_kind = match settings.compass_priority {
        1 => Some(1),
        2 => Some(2),
        3 => Some(3),
        _ => None,
    };
    let selected_compass = preferred_kind
        .and_then(|kind| compass_candidates.iter().find(|(found, _)| *found == kind))
        .or_else(|| compass_candidates.first());
    let (compass_type, target) = selected_compass
        .map(|(kind, target)| (*kind, *target))
        .unwrap_or_else(|| {
            if settings.always_show_compass && dimension == "minecraft:overworld" {
                (1, game.spawn.map(|spawn| [spawn.x, spawn.y, spawn.z]))
            } else {
                (0, None)
            }
        });
    let (compass_pointing, compass_yaw, compass_distance) = if let Some(target) = target {
        let dx = target[0] - player.position.x;
        let dz = target[2] - player.position.z;
        if dx * dx + dz * dz < 1.0e-5 {
            (false, 0.0, 0.0)
        } else {
            (
                true,
                normalize_yaw(((-dx).atan2(dz).to_degrees() as f32) - game.yaw),
                (dx * dx + dz * dz).sqrt() as f32,
            )
        }
    } else {
        (false, 0.0, 0.0)
    };
    let waypoints = game
        .waypoints
        .minelights_waypoints()
        .into_iter()
        .filter_map(|(target, packed_color)| {
            use horizon_protocol::packets::play::WaypointTarget;
            let (dx, dy, dz, yaw) = match target {
                WaypointTarget::Empty => return None,
                WaypointTarget::Block([tx, ty, tz]) => {
                    let dx = f64::from(tx) + 0.5 - player.position.x;
                    let dy = f64::from(ty) + 0.5 - (player.position.y + game.eye as f64);
                    let dz = f64::from(tz) + 0.5 - player.position.z;
                    (
                        dx,
                        dy,
                        dz,
                        normalize_yaw((-dx).atan2(dz).to_degrees() as f32 - game.yaw),
                    )
                }
                WaypointTarget::Chunk([cx, cz]) => {
                    let dx = f64::from(cx * 16 + 8) + 0.5 - player.position.x;
                    let dz = f64::from(cz * 16 + 8) + 0.5 - player.position.z;
                    (
                        dx,
                        0.0,
                        dz,
                        normalize_yaw((-dx).atan2(dz).to_degrees() as f32 - game.yaw),
                    )
                }
                WaypointTarget::Azimuth(angle) => {
                    (0.0, 0.0, 0.0, normalize_yaw(angle.to_degrees() - game.yaw))
                }
            };
            let pitch = if dy > 1.0 {
                1
            } else if dy < -1.0 {
                -1
            } else {
                0
            };
            let color = Color(
                ((packed_color >> 16) & 0xFF) as u8,
                ((packed_color >> 8) & 0xFF) as u8,
                (packed_color & 0xFF) as u8,
            );
            let distance = (dx * dx + dy * dy + dz * dz).sqrt() as f32;
            Some(Waypoint {
                yaw_degrees: yaw.round() as i16,
                color,
                pitch,
                distance,
            })
        })
        .collect();
    let chat_key = friendly_key_name(game.settings.keys.get("key.chat"));
    let spectator = game.spectator();
    let contextual = horizon_client::locator_bar::ContextualBar::next(
        game.waypoints.has_waypoints(),
        !spectator && game.jumpable_mount(),
        game.mount_jumping(),
        !game.creative && !spectator,
        game.experience_recent(),
    );
    let hud_bar = match contextual {
        horizon_client::locator_bar::ContextualBar::Experience => 0,
        horizon_client::locator_bar::ContextualBar::Locator => 1,
        _ => 2,
    };
    Snapshot {
        in_game: true,
        dimension,
        biome,
        feet: block_name(x, y, z),
        below: block_name(x, y - 1, z),
        head: block_name(x, head_y, z),
        health: player.health.health(),
        absorption: player.health.absorption(),
        hunger: player.health.food.food,
        saturation: player.health.food.saturation,
        air: player.air,
        experience: game.experience.progress,
        hud_bar,
        rain,
        biome_precipitation,
        biome_snowing,
        sky_light,
        local_light,
        end_flash: game.minelights_end_flash(),
        compass_type,
        compass_pointing,
        compass_yaw,
        compass_distance,
        waypoints,
        chat_received: game
            .remote
            .as_ref()
            .map_or(game.ui.chat.received, |remote| remote.chat_received),
        chat_key,
        on_fire: player.fire_ticks > 0,
        poisoned: game.potions.effects.get("minecraft:poison").is_some(),
        withering: game.potions.effects.get("minecraft:wither").is_some(),
        taking_damage: player.health.hurt.hurt_time > 0,
        in_water: player.fluids.was_touching_water,
        lightning: game.flash > 0,
        movement_keys,
    }
}
