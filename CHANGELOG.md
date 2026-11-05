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

- Added configurable key layouts and colors for player status effects, with `/minelights` commands across loaders and Horizon. Fabric and Quilt 1.16+ support choosing keys by pressing them; Forge and NeoForge configuration screens provide effect controls.
- Fixed OpenRGB keyboard mapping for repeated LED names and added LED-name overrides for incorrectly reported keys.
- Restore OpenRGB devices' previous lighting outside a world, with an optional OpenRGB profile, while respecting disabled devices.
- Added smoothly filled experience lighting, configurable low-health thresholds, absorption overlays, and an option to merge saturation and air into hunger.
- Added configurable locator lighting for Minecraft 1.21.6 and later, including waypoint colors and a mode that follows the active experience or locator HUD bar.

## Installation / Upgrade

- With manual server management enabled, download MineLights Server from the [release page](https://github.com/megabytesme/MineLights-Server/releases) on Windows if your devices need it.
- Place `MineLights.exe` in the game's `mods/MineLights` folder for Fabric, Forge, and NeoForge, or the game's `MineLights` folder for Quilt.
- Java clients can start an installed server automatically. By default, they download a missing server and check for updates.
- OpenRGB and Yeelight can connect directly and do not need MineLights Server.
