# Unreleased

- Added a compass fallback target. With Always Show Compass on and no compass in your inventory, the compass effect can point to world spawn (default), 0, 0, or your player spawn. Player spawn uses your bed or respawn anchor in singleplayer, and world spawn on servers, because servers don't send it to the client. A compass in your inventory always takes priority. Available in the settings screens, through `/minelights set compass-fallback-target`, and in Horizon through the command.
- Added Ignore Carried Compasses, which always uses the fallback target even when you carry a compass, lodestone compass or recovery compass (`/minelights set ignore-inventory-compass on`).
- Grouped the Player Status, Key Layout and Colors settings under section titles (Health, Hunger & Saturation, Experience & Locator Bar, Compass, Other Keys, Devices, Outside Worlds) on Fabric and Quilt 1.16+.
- The Key Layout page now says when the Saturation / Air keys aren't used because saturation and air are merged into the hunger bar.
- Added Spin Compass Without a Target (on by default). When the compass has no target, like a compass in the Nether or the End, the compass keys now spin with the same wobble as Minecraft's own needle instead of a fixed loop. Turn it off to show biome colors on those keys instead (`/minelights set spin-compass-without-target off`). With Always Show Compass on, the keyboard compass also spins in dimensions where the fallback target has no position.

# MineLights 2.4.0

This release brings the authenticated MineLights Server V2 protocol to Java and Horizon clients, adds Horizon support for Minecraft 26.3, and introduces configurable lighting controls.

## Key Features & Major Changes

- Added V2 server communication with per-install credentials, automatic local enrollment, and optional remote pairing.
- Updated clients use V2 exclusively. Older V1 clients can still connect after explicit approval in MineLights Server.
- Added stable device identities, per-client device leases and provider interests, remote server profiles, and expanded server controls.
- Stop a client-started server automatically when it is safe; keep it running while other clients are connected or legacy access is enabled.
- Added `/minelights` settings and control commands across Fabric, Quilt, Forge, NeoForge, and Horizon.
- Added a manual server management option for Java clients on Windows, with configurable installation and update notices. Automatic downloads and updates remain enabled by default.
- Kept Horizon server downloads off by default, with confirmation required before a download or launch.
- Bumped the Java mods, Horizon mod, and MineLights Server to version 2.4.0.
- Added configurable status lighting, custom key layouts and colors, OpenRGB keyboard fixes, and locator-bar lighting.

## Installation / Upgrade Instructions

- Install the Java mod build matching your loader and Minecraft version. Java artifacts retain their loader and Minecraft version names.
- V2 Java and Horizon clients require MineLights Server 2.4.0. Older V1 clients can connect only when legacy access is explicitly enabled in the server.
- By default, Java clients download a missing server and check for updates. Enable manual server management to disable automatic downloads and replacement and receive installation or update notices instead.
- Place `MineLights.exe` in the game's `mods/MineLights` folder for Fabric, Forge, and NeoForge, or the game's `MineLights` folder for Quilt.
- Horizon keeps server downloads opt-in and requires confirmation before downloading or starting the reviewed server.
- OpenRGB and Yeelight can connect directly and do not need MineLights Server.

## Full Changelog & Technical Details

### Original contribution in [PR #32](https://github.com/megabytesme/MineLights/pull/32) by [obaba-samich](https://github.com/obaba-samich)

- Mapped OpenRGB LEDs by index so repeated LED names are retained, standardized symbol, modifier, arrow, and numpad key names, and added overrides for devices that report incorrect names.
- Added key-layout and color settings pages for Fabric and Quilt 1.16+, with key selection by pressing the desired keys.
- Filled the experience bar gradually through its final key.
- Added locator-bar lighting for Minecraft 1.21.6+, with colored waypoint dots, vanilla distance steps, and a mode that follows the active experience or locator HUD bar.
- Added golden absorption-heart overlays, an option to show saturation and air on hunger keys, and a configurable low-health threshold.
- Restored OpenRGB devices' own lighting outside a world, with an optional OpenRGB profile.

### Follow-up fixes and parity work

- Ported effect commands and configuration controls to Forge, NeoForge, Horizon, and older supported Fabric and Quilt versions, with version-specific feature gating.
- Fixed player-status labels and locator-bar selection, improved handling for programmable and unnamed OpenRGB keys, made LED overrides consistent, and preserved device exclusions when restoring lighting.
- Added Horizon OpenRGB key-name logging and documentation for configurable lighting. Translation cleanup and completion followed the original contribution.
- The authenticated server protocol, pairing, multi-client lifecycle, Java installation controls, and Horizon server-download safeguards are separate 2.4.0 release work.

### Later 2.4.0 update

- Added an option to enable or disable MineLights Server download chat notices across Java and Horizon clients. Notices report a transfer that starts while the player is in a world, an active earlier transfer after they join one, and whether the server started or needs a restart. If a shared server starts during an update, Fabric and Quilt now retain the verified update for the next restart and report that restart is required.

## New Contributors ⭐

- [obaba-samich](https://github.com/obaba-samich): [PR #32](https://github.com/megabytesme/MineLights/pull/32)
