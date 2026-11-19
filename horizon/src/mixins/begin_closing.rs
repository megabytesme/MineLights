fn begin_closing(game: &Game<'_>, quit: bool) {
    if quit && game.options.is_client_start() {
        minelights_horizon::request_shutdown();
    }
}
