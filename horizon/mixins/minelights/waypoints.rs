use super::{ClientWaypoints, WaypointId, WaypointTarget};

impl ClientWaypoints {
    pub fn minelights_waypoints(&self) -> Vec<(WaypointTarget, u32)> {
        self.waypoints.iter().map(|waypoint| {
            let color = waypoint.color.map(|color| color as u32 & 0x00FF_FFFF).unwrap_or_else(|| {
                let hash = match &waypoint.id {
                    WaypointId::Uuid(uuid) => {
                        let halves = (*uuid >> 64) as u64 ^ *uuid as u64;
                        ((halves >> 32) as i32) ^ halves as i32
                    }
                    WaypointId::Name(name) => name.chars().fold(0i32, |hash, ch| hash.wrapping_mul(31).wrapping_add(ch as i32)),
                };
                let r = 96 + ((hash as u32 >> 16) & 0x5F);
                let g = 96 + ((hash as u32 >> 8) & 0x5F);
                let b = 96 + (hash as u32 & 0x5F);
                (r << 16) | (g << 8) | b
            });
            (waypoint.target, color)
        }).collect()
    }
}
