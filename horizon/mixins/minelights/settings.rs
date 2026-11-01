fn minelights_settings(
    game: &mut crate::game::Game<'_>,
    mod_id: &str,
    buttons: &mut Vec<String>,
) {
    crate::game::minelights::minelights_settings(game, mod_id, buttons);
}
