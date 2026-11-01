fn minelights_begin_shutdown(quit: bool) {
    if quit {
        crate::game::minelights::minelights_shutdown();
    }
}
