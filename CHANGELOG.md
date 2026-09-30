# MineLights 2.3.8.3

This release adds native Forge support and expands version support across the loaders. It also fixes compatibility with OpenRGB 1.0.

Previous server version compatible (as of 2.3.1)!

## Key Features & Major Changes

- Added native Forge support from Minecraft 1.17.1 to 26.3 for every Minecraft version with an official Forge release.
- Expanded Minecraft version support for Fabric, Quilt, and NeoForge.
- Fixed OpenRGB 1.0 compatibility so MineLights discovers devices.

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

- Updated the loader target matrix to Fabric 1.14.3-26.3, Quilt 1.14.4-26.3, Forge 1.17.1-26.3, and NeoForge 1.20.2-26.3. Forge targets skip Minecraft versions without an official Forge release.
- Added a dedicated Forge module with per-version build configuration, a Forge client entry point, configuration screen integration, and player-data compatibility overrides.
- Updated shared client, effect, and network mixin code for the mappings and APIs used by the Forge versions.
- Added a dedicated 1.21.11 target for Fabric, Quilt, and NeoForge, plus NeoForge targets for 1.21.10 and 1.21.11.
- Updated Cloth Config and Mod Menu dependency resolution for Fabric and Quilt 1.21.11 and 26.3 builds. Quilt 26.1 and newer now declares the required Quilt Loader version range.
- Updated OpenRGB protocol handling to read controller IDs from protocol 6 responses, use those IDs for device requests and lighting updates, and negotiate protocol 5 for compatibility.
- Set the Fabric lighting thread as a daemon so it does not keep the client running during shutdown.
