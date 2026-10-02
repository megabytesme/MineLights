# MineLights 2.3.8.4

This release adds Forge configuration menus, refined settings across Minecraft versions, improved reliability of the mod update checker, and fixes issues with the config screen on NeoForge.

Previous server version compatible (as of 2.3.1)!

## Key Features & Major Changes

- Added Forge configuration menu support, including a built-in settings screen for older Forge versions.
- Fixed MineLights settings failing to open on NeoForge 1.21.1 with Mod Menu ([#30](https://github.com/megabytesme/MineLights/issues/30)).
- Updated settings menus to show features available in the current Minecraft version.
- Fixed update notices being dropped when the Modrinth check finished before a world was joined.

## Installation / Upgrade Instructions

- DELETE your old mine-lights-\*.jar file completely.
- Download the jar from this release.
- Place the new minelights-\*.jar into your mods folder.
- Install Cloth Config, here: [https://modrinth.com/mod/cloth-config](https://modrinth.com/mod/cloth-config)
- **OpenRGB (optional):** The latest OpenRGB version is recommended. After installing or updating OpenRGB, restart your computer before using MineLights. Start OpenRGB, open the **SDK Server** tab, and click **Start Server**. Keep OpenRGB running while Minecraft is open. MineLights connects to the local server at `127.0.0.1:6742` (the default SDK server address and port).
- Run Minecraft.
  - Using anything which is not OpenRGB?
    1. Start Minecraft.
    2. Wait for MineLights.exe to finish downloading in the background.
    3. If all goes well, you will see a dialog asking you if you want to grant `MineLights.exe` permission to access the internet.
    4. After accepting, your supported devices will show red.

## Full Changelog & Technical Details

- Added Forge config screen integration across supported releases, with a native screen on older versions that do not use Cloth Config.
- Fixed the NeoForge config screen factory for the API signature used by NeoForge 1.21.1, resolving the `AbstractMethodError` reported in [issue #30](https://github.com/megabytesme/MineLights/issues/30).
- Reworked legacy Forge settings with separate category tabs, page navigation that stays within the selected tab, and a dedicated About section.
- Hid Locator Bar settings before Minecraft 26.1 and End Flash settings before Minecraft 1.21.9 in applicable configuration menus.
- Queued Modrinth update notices until a player is available, across Fabric, Quilt, Forge, and NeoForge.
- Updated older Fabric version detection and tick callback registration, including the Modrinth update check's Minecraft version lookup.
- Corrected Fabric API and Java 8 build settings for Minecraft 1.14.3, and hardened Forge and NeoForge shutdown handling.
