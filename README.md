# MineLights
![_bfb50031-5cf5-4366-aa3d-ec005d0aa4bf](https://github.com/user-attachments/assets/d2a078e4-f7f1-43cf-b03e-b1b6142b6fd8)

MineLights is a Minecraft Fabric mod that brings RGB lighting integrations to Minecraft Java Edition (targeting latest, 1.21). Currently, it only supports Corsair iCue however due to the modular design, other RGB softwares could (and will) be easily added.

## Features

- **iCue Integration**: Sync your in-game events with your iCue-compatible RGB devices.
- **Biome-specific colours**: Each biome you traverse in-game will have its own specific colours!
- **Weather integration**: The weather in the current biome will show on your devices!
- **Block effects**: When standing in certain blocks (such as lava, fire and portals), effects will show on the keyboard!

## Supported RGB software
- **Corsair iCue** - full device support upto [iCue SDK 3.0.464](https://github.com/CorsairOfficial/cue-sdk/releases/tag/v3.0.464).

## 🛠️ Installation

1.  **Download the LATEST MineLights Release Package:**
    *   Go to the [MineLights GitHub Releases page](https://github.com/megabytesme/MineLights/releases).
    *   Download the latest release `.zip` file that supports your Minecraft version (e.g., `MineLights-v1.1.2.zip`). **This is a complete package, not just a mod JAR!**

2.  **Install MineLights (CRITICAL STEPS - Read Carefully!):**

    a.  Locate the `MineLights-vX.X.X.zip` file you just downloaded.

    b.  **Extract the contents** of this `.zip` file to a temporary location (e.g., your Desktop).

    c.  After extracting, you will have a folder (e.g., `MineLights-v1.1.2`). **Open this folder.**

    d.  Inside, you should see the mod's `.jar` file (e.g., `mine-lights-1.1.2.jar`), and a `MineLights` folder (which contains other files essential for the mod to work).
   
    e.  Select **BOTH** of these items (the `mine-lights-x.x.x.jar` file AND the `MineLights` folder) from inside the extracted folder.
    
    f.  Copy these selected items and **paste them directly into your Minecraft `mods` folder.**

    **Your `mods` folder should now look something like this (along with other mods you may have):**
    ```
    .minecraft/
    └── mods/
        ├── mine-lights-1.1.2.jar   <-- The JAR from the extracted MineLights package
        ├── MineLights/             <-- The 'MineLights' folder from the extracted MineLights package
        └── (other mods...)
    ```

    ⚠️ **IMPORTANT!**
    *   **DO NOT** just place the downloaded `MineLights-vX.X.X.zip` file into your `mods` folder.
    *   **DO NOT** just extract only the `mine-lights-x.x.x.jar` from the zip and place it alone into the `mods` folder.
    *   **The `MineLights` folder MUST be present in your `mods` folder alongside the `mine-lights-x.x.x.jar` for the mod to function correctly.**

## Usage

1. **Launch Minecraft** with the Fabric profile.
2. **If requested** allow "MineLights" and "Java" access to networks.
3. **Ensure third-party RGB integrations are enabled** in whichever software you use!
4. **Enjoy synchronized RGB lighting** while you play!

## Roadmap

- **Support Windows Dynamic Lighting**: Integration with Windows Dynamic Lighting is in development and will be available in future updates (hopefully this becomes the final standard someday 😂).
- **Update Corsair iCue SDK from V3 to V4**: Currently using [iCue SDK 3.0.464](https://github.com/CorsairOfficial/cue-sdk/releases/tag/v3.0.464) (latest v3 release) since V4 is not as fully documented as I'd like it to be. Also, V3 supports everything currently.

## Contributing

Contributions are welcome! Please fork the repository and submit a pull request.

## Contact

For any questions or suggestions, feel free to open an issue.
