use crate::minelights_config::Settings;
use crate::minelights_player_data::{CompassTarget, compass};

const OVERWORLD: &str = "minecraft:overworld";
const NETHER: &str = "minecraft:the_nether";
const PLAYER: [f64; 3] = [10.5, 60.0, 9.5];
const SPAWN: [f64; 3] = [10.5, 120.0, 29.5];

fn target(dimension: &str, block: [i32; 3]) -> CompassTarget {
    CompassTarget {
        dimension: dimension.into(),
        block,
    }
}

#[test]
fn carried_compass_priority_uses_slot_order_then_preferred_kind() {
    let candidates = [
        (2, Some(target(OVERWORLD, [30, 56, 9]))),
        (3, None),
        (1, None),
        (2, Some(target(OVERWORLD, [60, 56, 9]))),
    ];
    let last_death = target(OVERWORLD, [-10, 99, 9]);
    for (compass_priority, expected) in [
        (0, (2, true, -90.0, 20.0)),
        (1, (1, true, 0.0, 20.0)),
        (2, (2, true, -90.0, 20.0)),
        (3, (3, true, 90.0, 20.0)),
    ] {
        let settings = Settings {
            compass_priority,
            ..Default::default()
        };
        assert_eq!(
            compass(
                &settings,
                OVERWORLD,
                PLAYER,
                0.0,
                Some(SPAWN),
                &candidates,
                Some(last_death.clone())
            ),
            expected
        );
    }
    let settings = Settings {
        compass_priority: 1,
        ..Default::default()
    };
    assert_eq!(
        compass(
            &settings,
            OVERWORLD,
            PLAYER,
            270.0,
            Some(SPAWN),
            &candidates[..2],
            None
        ),
        (2, true, 0.0, 20.0)
    );
}

#[test]
fn cross_dimension_or_missing_target_wobbles_or_hides() {
    for (kind, lodestone, death) in [
        (1, None, None),
        (2, Some(target(OVERWORLD, [30, 56, 9])), None),
        (2, None, None),
        (3, None, Some(target(OVERWORLD, [-10, 99, 9]))),
        (3, None, None),
    ] {
        let carried = [(kind, lodestone)];
        let mut settings = Settings::default();
        assert_eq!(
            compass(
                &settings,
                NETHER,
                PLAYER,
                0.0,
                Some(SPAWN),
                &carried,
                death.clone()
            ),
            (kind, false, 0.0, 0.0)
        );
        settings.spin_compass_without_target = false;
        assert_eq!(
            compass(&settings, NETHER, PLAYER, 0.0, Some(SPAWN), &carried, death),
            (0, false, 0.0, 0.0)
        );
    }
}

#[test]
fn fallback_obeys_legacy_spawn_origin_and_ignore_carried_policy() {
    let settings = Settings::default();
    assert_eq!(
        compass(&settings, OVERWORLD, PLAYER, 0.0, Some(SPAWN), &[], None),
        (0, false, 0.0, 0.0)
    );

    let carried = [(2, Some(target(NETHER, [30, 56, 9])))];
    let mut settings = Settings {
        ignore_inventory_compass: true,
        ..settings
    };
    for fallback in [0, 2] {
        settings.compass_fallback_target = fallback;
        assert_eq!(
            compass(
                &settings,
                OVERWORLD,
                PLAYER,
                45.0,
                Some(SPAWN),
                &carried,
                None
            ),
            (1, true, -45.0, 20.0)
        );
        assert_eq!(
            compass(&settings, NETHER, PLAYER, 45.0, Some(SPAWN), &carried, None),
            (1, false, 0.0, 0.0)
        );
    }
    settings.compass_fallback_target = 1;
    assert_eq!(
        compass(
            &settings,
            NETHER,
            [0.0, 64.0, 12.0],
            0.0,
            None,
            &carried,
            None
        ),
        (1, true, 180.0, 12.0)
    );
    assert_eq!(
        compass(
            &settings,
            NETHER,
            [0.0, 64.0, 0.0],
            0.0,
            None,
            &carried,
            None
        ),
        (1, false, 0.0, 0.0)
    );

    settings.ignore_inventory_compass = false;
    settings.always_show_compass = true;
    assert_eq!(
        compass(&settings, NETHER, PLAYER, 0.0, None, &carried, None),
        (2, true, -90.0, 20.0)
    );
    assert_eq!(
        compass(&settings, NETHER, [0.0, 64.0, -8.0], 0.0, None, &[], None),
        (1, true, 0.0, 8.0)
    );
}
