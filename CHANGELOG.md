# MineLights 2.3.1

This is a large update which focuses on new visual effects, expanded biome and lighting logic, and per‑device mapping — alongside multiple new Minecraft version targets (1.14.3 - 1.21.9) and optimisations to the server management workflow.
Previous server version NOT compatible!

## Key Features & Major Changes

- New Chat Pulse Effect:
- Keys bound to chat will now smoothly pulse white three times when a player message is received.
- Config option added to enable/disable this effect.
- Environmental & Weather Enhancements:
- Added dimming mode based on local light level or sky light level.
- Lightning flash effect now uses isLightningFlashing for accurate timing.
- Biome Colour Improvements:
- Comprehensive biome list added for 1.14.4 – 1.21.9 based on Yarn mappings, with adjusted colours to match map colours.
- Per‑Device LED Mapping:
- Standardised mapped key names before usage - Fixes OpenRGB integration on keyboards.
- Rain Effect Upgrade:
- Improved rain animation on RAM sticks and per‑key‑mapped devices.
- Server Management Optimisations:
- Faster update logic — triggers download, sends shutdown, and sets flags before starting server again.
- Updated URL for latest MineLights Server release.

## Installation / Upgrade Instructions

- DELETE your old mine-lights-\*.jar file completely.
- Download the from this release.
- Place the new mine-lights-2.3.1.jar into your mods folder.
- Run Minecraft

**For the best experience, also install Mod Menu and Cloth Config. Fabric API is required.**

## Full Changelog & Technical Details

- Added chat pulse effect when receiving a player message.
- Added config option for chat pulse effect.
- Added DTO and mixin/accessor for isChatMessageReceived.
- Added dimming mode based on local/sky light level; DTO and mixins for light level access.
- Added isLightningFlashing boolean and mixin for lightning properties.
- Added comprehensive biome list with official name resolution.
- Per‑device mapping refactor; standardised mapped key names.
- Improved rain effect on RAM sticks and per‑key devices.
- Integrated OpenRGB key mapping; expanded keymap.
- Logging improvements — total mapped keyboard keys.
- Updated server restart handling and release URL.
- Added new Minecraft targets: 1.21.8 (max 1.21.9), 1.21.6 (max 1.21.7), 1.21.2 (max 1.21.5), 1.20.5 (max 1.21.1), 1.20 (max 1.20.4), 1.19 (max 1.19.4), 1.17 (max 1.18.2), 1.16.2 (max 1.16.5), 1.16 (max 1.16.1), 1.15 (max 1.16), 1.14.4, 1.14.3.
- Minor fixes: removed unused imports, corrected name/version setting, fixed typos.
