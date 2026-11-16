# MineLights 2.4.1
## Changes
- Choose a fallback target for the keyboard compass: world spawn (default), 0, 0, or player spawn. Player spawn uses your bed or respawn anchor in singleplayer on Fabric and Quilt 1.16+, Forge 1.17+, and NeoForge. Multiplayer, Horizon and older versions use world spawn when it is available. A known player spawn in another dimension has no target.
- Enable **Ignore Carried Compasses** to use the fallback target even when you carry a compass, lodestone compass or recovery compass. It is off by default.
- **Spin Compass Without a Target** is on by default. When there is no target, compass keys wobble like Minecraft's needle; turn it off to show biome colors instead.
- **Lodestone Compass Shimmer** is on by default. Turn it off to keep the lodestone colors without the purple sweep.
- Use the settings screens or `/minelights set` commands to change these options. Horizon also has controls for the new compass settings.
- Grouped the Player Status, Key Layout and Colors settings under section titles on Fabric and Quilt 1.16+. The Key Layout page explains when Saturation / Air keys are unused because those bars are merged into the hunger bar.

## Compatibility and fixes

- Added translated labels for the compass settings to Forge's built-in settings screens, including older Forge language files.
- Fixed the settings-menu entry on older Fabric and Quilt versions. Quilt 1.14.4 uses ModMenu 1.7.18 or later; Fabric 26.3 requires Fabric Loader 0.20.1 or later.
- Made singleplayer spawn lookup safe across world and player changes, and clarified the fallback behavior in the settings descriptions.
- Compass fallback, carried-compass options, settings sections and lodestone shimmer: [obaba-samich](https://github.com/obaba-samich), in [PR #40](https://github.com/megabytesme/MineLights/pull/40) and [PR #41](https://github.com/megabytesme/MineLights/pull/41).
