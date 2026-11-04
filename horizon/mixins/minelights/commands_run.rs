fn minelights_commands_run(game: &mut crate::game::Game<'_>, command: &str, ran: &mut bool) {
    crate::game::minelights_commands::minelights_command(game, command, ran);
}
