# MineLights 2.3.8.1

This release adds support for Minecraft 26.3 on Fabric and NeoForge.

Previous server version compatible (as of 2.3.1)!

## Key Features & Major Changes

- Added Fabric and NeoForge targets for Minecraft 26.3.
- Added a dedicated warm autumn lighting color for the Dappled Forest biome.
- Fixed lighting initialization when Minecraft's client is not ready during startup.
- Added the NeoForge Mods screen icon metadata.
- Updated Fabric API, Fabric Loader, Mod Menu, Cloth Config, and NeoForge dependency coordinates for 26.3.

## Installation / Upgrade Instructions

- DELETE your old mine-lights-\*.jar file completely.
- Download the jar from this release.
- Place the new minelights-\*.jar into your mods folder.
- Install Cloth Config, here: [https://modrinth.com/mod/cloth-config](https://modrinth.com/mod/cloth-config)
- Run Minecraft.
  - Using anything which is not OpenRGB?
    1. Start Minecraft.
    2. Wait for MineLights.exe to finish downloading in the background.
    3. If all goes well, you will see a dialog asking you if you want to grant `MineLights.exe` permission to access the internet.
    4. After accepting, your supported devices will show red.

## Full Changelog & Technical Details

- Added Minecraft 26.3 target metadata for Fabric and NeoForge.
- Updated supported-version documentation to include Minecraft 26.3.
- Added rain and biome-color data for Dappled Forest lighting.
- Fixed the startup lighting loop to tolerate the client not being initialized yet.
- Updated About to show the resolved mod version and 2026 copyright.
