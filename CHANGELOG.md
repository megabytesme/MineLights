# MineLights 2.3.8.2

This release adds native Quilt support and removes loader-specific wording from the MineLights descriptions.

Previous server version compatible (as of 2.3.1)!

## Key Features & Major Changes

- Updated the About description across all translations to describe MineLights as a Minecraft mod.
- Updated Fabric, Quilt, and NeoForge metadata descriptions to use the same loader-neutral wording.
- Added native Quilt builds from Minecraft 1.14.4 through 26.3 without a Fabric API or Quilt API library dependency.

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

- Replaced loader-specific About descriptions in all language files.
- Made Fabric and NeoForge metadata descriptions loader-neutral, matching Quilt.
- Added Minecraft 26.3 target metadata and a loader-independent client tick hook for Quilt builds from Minecraft 1.14.4 onward.
- Updated Quilt dependencies and supported-version documentation.
