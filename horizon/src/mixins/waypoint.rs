fn waypoint(game: &Game<'_>, packet: &TrackedWaypoint) {
    if !game.options.is_client_start() {
        return;
    }
    let id = match &packet.id {
        horizon_protocol::packets::play::WaypointId::Uuid(uuid) => {
            minelights_horizon::LocatorId::Uuid(*uuid)
        }
        horizon_protocol::packets::play::WaypointId::Name(name) => {
            minelights_horizon::LocatorId::Name(name.clone())
        }
    };
    let target = match packet.target {
        horizon_protocol::packets::play::WaypointTarget::Empty => {
            minelights_horizon::LocatorTarget::Empty
        }
        horizon_protocol::packets::play::WaypointTarget::Block(pos) => {
            minelights_horizon::LocatorTarget::Block(pos)
        }
        horizon_protocol::packets::play::WaypointTarget::Chunk(pos) => {
            minelights_horizon::LocatorTarget::Chunk(pos)
        }
        horizon_protocol::packets::play::WaypointTarget::Azimuth(angle) => {
            minelights_horizon::LocatorTarget::Azimuth(angle)
        }
    };
    minelights_horizon::observe_locator(packet.operation, id, target, packet.color);
}
