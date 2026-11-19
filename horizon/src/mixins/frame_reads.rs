fn frame_reads(game: &Game<'_>) {
    if !game.options.is_client_start() {
        minelights_horizon::discard_frame();
        return;
    }
    minelights_horizon::observe_language(&game.settings.language);
    if !game.on("minelights") {
        minelights_horizon::discard_frame();
        minelights_horizon::observe_disabled_frame();
        return;
    }
    let raw = game.player.as_ref().map(|player| {
        let x = player.position.x.floor() as i32;
        let y = player.position.y.floor() as i32;
        let z = player.position.z.floor() as i32;
        let biome_id = game.world.chunks.get(&(x >> 4, z >> 4)).map_or_else(
            || game.res.biomes().id("minecraft:plains"),
            |chunk| chunk.biome(x, y, z),
        );
        let biome = game.res.biomes().get(biome_id);
        let compass_candidates = game
            .inventory
            .get(game.selected)
            .and_then(Option::as_ref)
            .into_iter()
            .chain(game.offhand.iter())
            .chain(game.inventory.iter().flatten())
            .filter_map(|stack| match stack.item.as_ref() {
                "minecraft:recovery_compass" => Some((3, None)),
                "minecraft:compass" => {
                    let target = stack
                        .extra
                        .as_ref()
                        .and_then(|components| components.other("minecraft:lodestone_tracker"))
                        .and_then(|tracker| tracker.get("target"));
                    let Some(target) = target else {
                        return Some((1, None));
                    };
                    let lodestone = match (target.get("dimension"), target.get("pos")) {
                        (
                            Some(horizon_core::nbt::Tag::String(dimension)),
                            Some(horizon_core::nbt::Tag::IntArray(pos)),
                        ) if pos.len() >= 3 => Some(minelights_horizon::CompassTarget {
                            dimension: dimension.to_string_lossy(),
                            block: [pos[0], pos[1], pos[2]],
                        }),
                        _ => None,
                    };
                    Some((2, lodestone))
                }
                _ => None,
            })
            .collect();
        let spectator = game.spectator();
        let contextual = horizon_client::locator_bar::ContextualBar::next(
            game.waypoints.has_waypoints(),
            !spectator && game.jumpable_mount(),
            game.mount_jumping(),
            !game.creative && !spectator,
            game.experience_recent(),
        );
        minelights_horizon::FrameReads {
            biome: biome.name.clone(),
            biome_precipitation: biome.precipitation,
            biome_snowing: biome.snows_at(x, y, z).unwrap_or(false),
            eye_y: player.position.y + f64::from(game.eye),
            // LevelRef::brightness uses the server-only resolver in this SDK.
            packed_light: game.world.light(x, y, z),
            // The SDK suppresses weather in an Overworld whose dimension type
            // lacks sky or has a ceiling. The legacy painter used raw rain there.
            rain_override: (game.dim.id() == "minecraft:overworld"
                && (!game.world.shape.sky_light || game.world.shape.has_ceiling))
                .then(|| game.world.weather.levels(1.0).0),
            world_spawn: game.spawn.map(|pos| [pos.x, pos.y, pos.z]),
            compass_candidates,
            movement_bindings: [
                "key.forward",
                "key.back",
                "key.left",
                "key.right",
                "key.jump",
                "key.sneak",
                "key.sprint",
            ]
            .iter()
            .map(|binding| game.settings.keys.get(binding).to_owned())
            .collect(),
            chat_binding: game.settings.keys.get("key.chat").to_owned(),
            hud_bar: match contextual {
                horizon_client::locator_bar::ContextualBar::Experience => 0,
                horizon_client::locator_bar::ContextualBar::Locator => 1,
                _ => 2,
            },
            chat_received: game
                .remote
                .as_ref()
                .map_or(game.ui.chat.received, |remote| remote.chat_received),
            taking_damage: player.health.hurt.hurt_time > 0,
            air: player.air,
            on_fire: player.fire_ticks > 0,
            in_water: player.fluids.was_touching_water,
            lightning: game.flash > 0,
            end_flash: game.end_flash_for_pack(1.0).0,
        }
    });
    minelights_horizon::observe_frame(raw);
}
