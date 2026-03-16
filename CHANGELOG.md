# MineLights 2.3.5

This is an update which improves OpenRGB device support on zone-based devices.
Previous server version compatible (as of 2.3.1)!

## Key Features & Major Changes

- Added a fallback for zone-based RGB devices to prevent LEDs turning black.

## Installation / Upgrade Instructions

- DELETE your old mine-lights-\*.jar file completely.
- Download the from this release.
- Place the new mine-lights-2.3.5.jar into your mods folder.
- Run Minecraft

**For the best experience, also install Mod Menu and Cloth Config. Fabric API is required.**

## Full Changelog & Technical Details

- Add a fallback for zone-based RGB devices to prevent LEDs turning black [(PR #19 - SaturninTheAlien)](https://github.com/megabytesme/MineLights/pull/19)
  1. Added a fallback to prevent some RGB devices, particularly zone-based keyboards (e.g. SteelSeries Apex 3), from turning black when LED indices fall outside the device's supported range.
  2. The fallback distributes the available colors cyclically across all LEDs so that zone-based devices still receive a valid color update.
  3. For some devices, the calculated value of localLedId falls outside the valid LED index range. This can also occur when multiple RGB devices are present.
  4. The fallback ensures that byte[][] colorArray is always initialized, which prevents LEDs from being set to (0,0,0).

## New Contributors ⭐

- [SaturninTheAlien](https://github.com/SaturninTheAlien): [PR #19](https://github.com/megabytesme/MineLights/pull/19)
