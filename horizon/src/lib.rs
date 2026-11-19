use mod_api::{
    ClientHost, ClientStoppingEvent, CommandEvent, Flow, Hooks, RegisterClientCommandsEvent,
    Registrar, RegistrationContext, RenderFramePostEvent, ScreenClosingEvent,
    ScreenKeyPressedPreEvent, ScreenMouseButtonPressedPreEvent, ScreenMouseScrolledPreEvent,
    ScreenRenderPostEvent, SettingChangedEvent,
};

const MOD_ID: &str = "minelights";

#[path = "runtime.rs"]
mod minelights;
#[path = "bridge.rs"]
mod minelights_bridge;
#[path = "commands.rs"]
mod minelights_commands;
#[path = "config.rs"]
mod minelights_config;
#[path = "direct.rs"]
mod minelights_direct;
#[path = "effects.rs"]
mod minelights_effects;
#[path = "translations.rs"]
mod minelights_lang;
#[path = "gameplay.rs"]
mod minelights_player_data;
#[path = "protocol.rs"]
mod minelights_protocol;
#[path = "server.rs"]
mod minelights_server;
#[path = "settings_ui.rs"]
mod minelights_settings;

#[cfg(test)]
#[path = "native/pinned_tls.rs"]
mod pinned_tls;

#[cfg(test)]
mod tests;

// The launcher builds the pinned native-route helpers in a separate patch crate.
// They exchange owned values with this crate and never let game borrows escape.
#[doc(hidden)]
pub use minelights_player_data::{
    CompassTarget, FrameReads, LocatorId, LocatorTarget, clear_locators, discard_frame,
    observe_frame, observe_language, observe_locator,
};

#[doc(hidden)]
pub use minelights_engine::{PinnedTlsStream, install_pinned_tls};

#[doc(hidden)]
pub fn observe_disabled_frame() {
    minelights::minelights_disabled_frame();
}

#[doc(hidden)]
pub fn request_shutdown() {
    minelights_bridge::request_shutdown();
}

#[doc(hidden)]
pub fn finish_shutdown() {
    minelights::minelights_shutdown();
}

#[derive(Default)]
struct MineLights;

#[mod_api::horizon_mod]
impl Hooks for MineLights {
    fn register(&self, registrar: &mut dyn Registrar, _: &RegistrationContext) {
        minelights_settings::register(registrar);
    }

    fn register_client_commands(
        &self,
        _: &mut dyn ClientHost,
        event: &mut RegisterClientCommandsEvent<'_>,
    ) {
        minelights_commands::register(event.commands);
    }

    fn command(&self, host: &mut dyn ClientHost, event: &mut CommandEvent<'_>) -> Flow {
        if minelights_commands::minelights_command(host, event.command) {
            Flow::Cancel
        } else {
            Flow::Continue
        }
    }

    fn render_frame_post(&self, host: &mut dyn ClientHost, _: &mut RenderFramePostEvent) {
        let settings = minelights_settings::settings_for(host);
        if let Some(snapshot) = minelights_player_data::snapshot(host, &settings) {
            minelights::minelights_frame(host, snapshot, settings);
        }
    }

    fn client_stopping(&self, _: &mut dyn ClientHost, _: &mut ClientStoppingEvent) {
        finish_shutdown();
    }

    fn setting_changed(&self, host: &mut dyn ClientHost, event: &mut SettingChangedEvent<'_>) {
        minelights_settings::changed(host, event);
    }

    fn screen_render_post(&self, host: &dyn ClientHost, event: &mut ScreenRenderPostEvent<'_>) {
        minelights_settings::screen_render(host, event);
    }

    fn screen_mouse_button_pressed_pre(
        &self,
        host: &dyn ClientHost,
        event: &mut ScreenMouseButtonPressedPreEvent<'_>,
    ) -> Flow {
        minelights_settings::screen_click(host, event)
    }

    fn screen_mouse_scrolled_pre(
        &self,
        host: &dyn ClientHost,
        event: &mut ScreenMouseScrolledPreEvent<'_>,
    ) -> Flow {
        minelights_settings::screen_scroll(host, event)
    }

    fn screen_closing(&self, host: &dyn ClientHost, event: &mut ScreenClosingEvent<'_>) {
        minelights_settings::screen_closing(host, event);
    }

    fn screen_key_pressed_pre(
        &self,
        host: &dyn ClientHost,
        event: &mut ScreenKeyPressedPreEvent<'_>,
    ) -> Flow {
        minelights_settings::screen_key(host, event)
    }
}
