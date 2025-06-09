# MineLights
![_bfb50031-5cf5-4366-aa3d-ec005d0aa4bf](https://github.com/user-attachments/assets/d2a078e4-f7f1-43cf-b03e-b1b6142b6fd8)

MineLights is a Minecraft Fabric mod that brings your world to life with dynamic RGB lighting effects for your peripherals. It targets the latest version of Minecraft (1.21+) and uses a modular design to support a wide range of hardware.

## Features

- **Multi-SDK Support**: Integrates with OpenRGB (Windows, Linux and MacOS), Corsair iCUE (Windows), and MSI Mystic Light (Windows) simultaneously.
- **Dynamic Environmental Effects**: Your lighting changes in real-time based on your in-game surroundings.
  - **Biome Colors**: Your keyboard's background color smoothly transitions to match the biome you're in.
  - **Weather Effects**: Experience rain and dramatic lightning flashes during in-game thunderstorms.
  - **Status Effects**: Your lighting will react when you are on fire, poisoned, or withering.
  - **Block Effects**: Standing in lava, fire, or portals will trigger unique lighting themes.
- **In-Game Status Bars**: Use your keyboard's function keys as real-time status bars for health, hunger, and experience.
- **Highly Configurable**: An in-game configuration screen (via Mod Menu) lets you enable/disable every feature, integration, and even individual devices.

## Supported RGB Software
- **OpenRGB**: Control any hardware supported by the OpenRGB server (requires OpenRGB - Cross-platform support on 64-bit devices! Windows, Linux and MacOS).
- **Corsair iCUE**: Full device support via the iCUE SDK (requires Corsair iCue - Windows).
- **MSI Mystic Light**: Control for motherboards, GPUs, and other devices via the Mystic Light SDK (requires MSI Center - Windows).

## 🛠️ Installation

### Prerequisites
- **Minecraft Fabric**: You must have the Fabric Loader installed.
- **Mod Menu**: Required to access the in-game configuration screen.
- **(Optional) OpenRGB**: If you want to use OpenRGB devices, make sure the OpenRGB server is running before you launch Minecraft.
- **(Optional) iCUE / MSI Center**: If you have Corsair or MSI hardware, ensure their official software is installed and running.

### Standard Installation
1.  **Download the LATEST MineLights Release Package:**
    *   Go to the [MineLights GitHub Releases page](https://github.com/megabytesme/MineLights/releases).
    *   Download the latest release `.zip` file that supports your Minecraft version (e.g., `MineLights-v2.0.0.zip`). **This is a complete package, not just a mod JAR!**

2.  **Install MineLights (CRITICAL STEPS - Read Carefully!):**

    a.  Locate the `MineLights-vX.X.X.zip` file you just downloaded.

    b.  **Extract the contents** of this `.zip` file to a temporary location (e.g., your Desktop).

    c.  After extracting, you will have a folder (e.g., `MineLights-v2.0.0`). **Open this folder.**

    d.  Inside, you should see the mod's `.jar` file (e.g., `mine-lights-2.0.0.jar`), and a `MineLights` folder (which contains other files essential for the mod to work).
   
    e.  Select **BOTH** of these items (the `mine-lights-x.x.x.jar` file AND the `MineLights` folder) from inside the extracted folder.
    
    f.  Copy these selected items and **paste them directly into your Minecraft `mods` folder.**

    **Your `mods` folder should now look something like this (along with other mods you may have):**
    ```
    .minecraft/
    └── mods/
        ├── mine-lights-2.0.0.jar   <-- The JAR from the extracted MineLights package
        ├── MineLights/             <-- The 'MineLights' folder from the extracted MineLights package
        └── (other mods...)
    ```

    ⚠️ **IMPORTANT!**
    *   **DO NOT** just place the downloaded `MineLights-vX.X.X.zip` file into your `mods` folder.
    *   **DO NOT** just extract only the `mine-lights-x.x.x.jar` from the zip and place it alone into the `mods` folder.
    *   **The `MineLights` folder MUST be present in your `mods` folder alongside the `mine-lights-x.x.x.jar` for the mod to function correctly with iCue and Mystic Light. For OpenRGB, this is optional!**

### For iCUE & Mystic Light Support (Windows Only)
The `mine-lights-x.x.x.jar` file does not contain the proprietary SDK files from Corsair or MSI. To enable support for these, you must use the external `MineLights.exe` helper along with the libraries in the zip.
1.  **Run the Helper:**
    -   Double-click `MineLights.exe` in your .minecraft/mods/MineLights/ folder to run it.
    -   For **MSI Mystic Light** support, you **must** right-click `MineLights.exe` and select **"Run as administrator"**.
    -   A new icon will appear in your Windows System Tray to show that it's running.

## Usage

1. **Run the Helper (if needed):** If you are on Windows and want iCUE/MSI support, install and run `MineLights.exe` *before* you launch Minecraft. Remember to run it as an administrator for MSI hardware.
2. **Launch Minecraft:** Start the game with your Fabric profile.
3. **Configure:**
   - In the main menu, go to **Mods > MineLights > Config** (the gear icon).
   - Enable the integrations you want to use (OpenRGB, iCUE Proxy, etc.).
   - Save the config. The mod will automatically detect your devices.
   - You can re-enter the config to disable specific devices if you wish.
4. **Enjoy!** Your lighting will now sync with your game.

## Roadmap

- **Windows Dynamic Lighting**: Integration with Windows Dynamic Lighting is planned for the future.
- **Improve Device Compatibility**: Continuously working to improve support for more devices across all SDKs.

## Contributing

Contributions are welcome! Please fork the repository and submit a pull request.

## Contact

For any questions, suggestions, or bug reports, please [open an issue](https://github.com/megabytesme/MineLights/issues) on the GitHub repository.
