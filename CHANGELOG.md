# Unreleased

This release makes the effect keys configurable, fixes OpenRGB keyboards showing jumbled effects, and adds a locator bar and other player status options.

## Changes

- Fixed OpenRGB keyboards showing effects on the wrong keys. LEDs are now mapped by index, so LEDs that share a name are no longer dropped, and symbol, modifier, arrow and numpad key names are standardized.
- Added LED overrides (`Device name|LED index=KEY`) for devices that report the wrong name for a key, for example media names on the F-row.
- Added a Key Layout settings page (Fabric and Quilt, 1.16 and newer) to choose the keys for every effect by pressing them: health, hunger, saturation/air, experience, locator bar, low health heartbeat and compass.
- Added colour settings for every bar and the heartbeat.
- The experience bar now fills each key gradually and reaches the last key.
- Added a locator bar (1.21.6 and newer) that shows waypoints as glowing dots in their own colours. Dots shrink with distance in the same steps as the vanilla bar.
- Added an experience/locator bar mode: merged (switches at the same moments as the HUD), experience only, locator only, or both.
- Added golden heart (absorption) overlays on the health keys.
- Added an option to show saturation and air on the hunger keys, with an adjustable saturation tint.
- Added a low health threshold setting for the heartbeat.
- OpenRGB devices now go back to their own lighting outside of a world, or load a chosen OpenRGB profile, instead of staying red.
- Player status labels now show the keys each bar is assigned to.

---

# MineLights 2.4.0

This release adds Horizon support for Minecraft 26.3 and aligns all clients with the authenticated MineLights Server V2 protocol.

## Changes

- Added V2 server communication with per-install credentials, automatic local enrollment, and optional remote pairing.
- Use V2 exclusively in the updated clients. Older V1 clients can still connect after explicit approval in MineLights Server.
- Automatically stop a client-started server when it is safe; keep it running while other clients are connected or legacy access is enabled.
- Added stable device identities, remote server profiles, per-client device leases and provider interests, and expanded server controls.
- Preserve legacy device exclusions and remind users when legacy server access remains enabled after connecting over V2.
- Added `/minelights` settings and control commands across Fabric, Quilt, Forge, NeoForge, and Horizon.
- Added a manual server management option for Java clients on Windows. It disables automatic downloads and replacement and offers configurable installation and update notices, including release notes. Automatic server downloads and updates remain enabled by default.
- Kept server downloads opt-in on Horizon and added chat guidance while autostart confirmation is pending.
- Bumped the Java mods, Horizon mod, and MineLights Server to version 2.4.0.

## Installation / Upgrade

- With manual server management enabled, download MineLights Server from the [release page](https://github.com/megabytesme/MineLights-Server/releases) on Windows if your devices need it.
- Place `MineLights.exe` in the game's `mods/MineLights` folder for Fabric, Forge, and NeoForge, or the game's `MineLights` folder for Quilt.
- Java clients can start an installed server automatically. By default, they download a missing server and check for updates.
- OpenRGB and Yeelight can connect directly and do not need MineLights Server.
