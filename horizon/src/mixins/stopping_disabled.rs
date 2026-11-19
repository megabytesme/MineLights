fn stopping_disabled(game: &Game<'_>) {
    if game.options.is_client_start() && !game.on("minelights") {
        minelights_horizon::finish_shutdown();
    }
}
