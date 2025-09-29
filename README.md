# MineLights

| Old logo |   | New logo | Video |
|----------|---|----------|-------|
| <img src="https://github.com/user-attachments/assets/d2a078e4-f7f1-43cf-b03e-b1b6142b6fd8" alt="MineLights Icon" height="200"> | ➡️ | <img src="https://github.com/user-attachments/assets/c0d50adb-0242-4603-89a5-074b40903606" alt="MineLights Icon" height="200"> | ![output](https://github.com/user-attachments/assets/fd0adbbf-eaf4-46c1-bcfc-e56b5df13e32) |
| AI Placeholder Image (during dev) | Now... | Made by me! (I love MS Paint) | <small><em>MineLights running on an Asus laptop. Video courtesy of Nukepatrol99.</em></small> |

<a href="https://modrinth.com/mod/minelights" aria-label="MineLights on Modrinth">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/gabrielvicenteYT/modrinth-icons/refs/heads/main/Branding/Badge/badge-dark.svg">
    <source media="(prefers-color-scheme: light)" srcset="https://raw.githubusercontent.com/gabrielvicenteYT/modrinth-icons/refs/heads/main/Branding/Badge/badge-light.svg">
    <img alt="MineLights on Modrinth" src="https://raw.githubusercontent.com/gabrielvicenteYT/modrinth-icons/refs/heads/main/Branding/Badge/badge-dark.svg">
  </picture>
</a>

MineLights is a Minecraft Fabric mod that brings your world to life with dynamic RGB lighting effects for your peripherals. It targets the latest version of Minecraft (1.21+) and uses a modular design to support a wide range of hardware.

## Features

- **Extensive Multi-SDK Support**: Integrates with a wide array of RGB SDKs simultaneously, including OpenRGB, Corsair iCUE, Logitech G HUB, Razer Chroma, MSI Mystic Light, ASUS Aura, SteelSeries GameSense, and Wooting.
- **Dynamic Environmental Effects**: Your lighting changes in real-time based on your in-game surroundings.
  - **Biome Colors**: Your keyboard's background color smoothly transitions to match the biome you're in.
  - **Weather Effects**: Experience rain and dramatic lightning flashes during in-game thunderstorms.
  - **Status Effects**: Your lighting will react when you are on fire, poisoned, or withering.
  - **Block Effects**: Standing in lava, fire, or portals will trigger unique lighting themes.
- **In-Game Status Bars**: Use your keyboard's function keys as real-time status bars for health, hunger, and experience, alongside tracking other players or waypoints!
- **Compass**: A compass on your numpad (Extended keyboards only) will appear, if you have either a compass or recovery compass in your inventory! Alternatively it can always be displayed via a toggle in settings.
- **Highly Configurable**: An in-game configuration screen (via Mod Menu) lets you enable/disable every feature, integration, and even individual devices.
- **Multiple Languages**: Support for English, Anglish, German and Chinese (Simplified) has been added (Along with some joke languages)

## Supported RGB Software

- **OpenRGB** Full device support (DIRECT mode!). Requires OpenRGB - Windows, Linux and MacOS.
- **Corsair iCUE**: Full device support for keyboards, mice, headsets, and more via the iCUE SDK (requires Corsair iCUE - Windows).
- **Logitech G HUB / Lightsync**: Controls Logitech G keyboards, mice, headsets, and other Lightsync-enabled gear (requires Logitech G HUB - Windows).
- **Razer Chroma**: Extensive support for all Razer Chroma-enabled peripherals like keyboards, mice, and mousepads (requires Razer Synapse - Windows).
- **MSI Mystic Light**: Control for motherboards, GPUs, and other devices via the Mystic Light SDK (requires MSI Center - Windows).
- **ASUS Aura Sync**: Control for motherboards, GPUs, and other devices via the ASUS Aura SDK (requires Armoury Crate - Windows).
- **SteelSeries GameSense**: Integration with SteelSeries peripherals like keyboards, mice, and headsets (requires SteelSeries GG - Windows).
- **Wooting**: Direct, low-latency control for Wooting analog keyboards (requires Wootility software to be running - Windows).
- **Novation**: Support for Novation MIDI controllers like the Launchpad, enabling unique grid-based effects (Windows).
- **Raspberry Pi Pico**: Directly control custom DIY lighting projects powered by a Raspberry Pi Pico (Windows).
- **Yeelights** Directly control your Yeelights smarthome bulbs (Universal).

### This project uses the RGB.Net Nuget package.

## 🛠️ Installation

### Prerequisites

- **Minecraft Fabric**: You must have the Fabric Loader installed.
- **Mod Menu**: Required to access the in-game configuration screen.
- **Cloth Config API**: Required to access the in-game configuration screen.
- **(Optional) OpenRGB**: If you want to use OpenRGB devices, make sure the OpenRGB server is running before you launch Minecraft.
- **(Optional) All other RGB software**: If you have any other hardware, ensure their official software is installed and running.

### Installation
- Simply place the mod in your mods folder, then start Minecraft. Follow the on-screen instructions.

**Installing MineLights version 2.1 or below**: Follow the guide in the release changelog.

## Usage

1. **Launch Minecraft:** Start the game with your Fabric profile.
2. **Configure:**
   - In the main menu, go to **Mods > MineLights > Config** (the gear icon).
   - Enable the integrations you want to use (OpenRGB, Corsair iCUE, etc.).
   - Save the config.
   - Refresh devices to show newly added devices.
   - You can re-enter the config to disable specific devices if you wish.
   - ⚠️: MSI Mystic Light integration requires you to restart the MineLights server as administrator to work!
3. **Enjoy!** Your lighting will now sync with your game.

## Roadmap

- **Windows Dynamic Lighting**: Integration with Windows Dynamic Lighting is planned for the future.
- **Improve Device Compatibility**: Continuously working to improve support for more devices across all SDKs.
- **Community Testing**: Ideally have testers, well test!

## Contributing

Contributions are welcome! Please fork the repository and submit a pull request.

## Contact

For any questions, suggestions, or bug reports, please [open an issue](https://github.com/megabytesme/MineLights/issues) on the GitHub repository.
