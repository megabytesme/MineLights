# MineLights 2.3.8

This release adds support for Minecraft 26.2 on Fabric and NeoForge.

Previous server version compatible (as of 2.3.1)!

## Key Features & Major Changes

- Added Fabric and NeoForge targets for Minecraft 26.2.
- Updated Fabric API, Fabric Loader, Mod Menu, Cloth Config, and NeoForge dependency coordinates for 26.2.
- Fixed MineLights Server autostart sometimes stalling because the mod no longer drained the server process output stream.

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

- Added Minecraft 26.2 target metadata for Fabric and NeoForge.
- Updated supported-version documentation to include Minecraft 26.2.
- Restored MineLights.exe output draining after launch while keeping the in-game live server log UI removed.
