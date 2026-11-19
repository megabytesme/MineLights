fn clear_waypoints(game: &Game<'_>) {
    if game.options.is_client_start() {
        minelights_horizon::clear_locators();
    }
}
