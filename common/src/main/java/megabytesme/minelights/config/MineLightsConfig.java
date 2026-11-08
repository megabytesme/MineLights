package megabytesme.minelights.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import megabytesme.minelights.network.RemoteServerProfile;
import megabytesme.minelights.rgb.OpenRGBEndpoint;

public class MineLightsConfig {
    public transient boolean refreshDevices = false;
    public transient boolean restartProxy = false;
    public transient boolean restartProxyAsAdmin = false;
    public transient boolean clearDisabledDevices = false;
    public transient boolean requestServerUpdateCheck = false;

    public volatile boolean enableMod = true;
    public volatile boolean autoStartServer = true;
    public volatile boolean manualServerManagement = false;

    /** A manually managed installed server can still be started automatically. */
    public boolean mayAutomaticallyStartServer(boolean shuttingDown) {
        return enableMod && autoStartServer && !shuttingDown;
    }

    /** Downloading or replacing a server additionally requires automatic management. */
    public boolean mayAutomaticallyInstallServer(boolean shuttingDown, boolean explicitlyStopped) {
        return mayAutomaticallyStartServer(shuttingDown) && !manualServerManagement && !explicitlyStopped;
    }
    public boolean notifyMissingServer = true;
    public boolean checkServerUpdates = true;
    public volatile boolean notifyServerDownloadProgress = true;

    /** Credentials are stored in the protected MineLights configuration file and never logged. */
    public String v2ClientId;
    public String v2ClientSecret;
    public List<RemoteServerProfile> remoteServers = new java.util.concurrent.CopyOnWriteArrayList<>();
    /** Native OpenRGB endpoints; these do not use MineLights V2 authentication or TLS. */
    public List<OpenRGBEndpoint> openRgbEndpoints = new java.util.concurrent.CopyOnWriteArrayList<>(java.util.Collections.singletonList(
            new OpenRGBEndpoint("127.0.0.1", 6742)));

    public volatile boolean enableCorsair = true;
    public volatile boolean enableAsus = true;
    public volatile boolean enableLogitech = true;
    public volatile boolean enableRazer = true;
    public volatile boolean enableWooting = true;
    public volatile boolean enableSteelSeries = true;
    public volatile boolean enableMsi = true;
    public volatile boolean enableNovation = true;
    public volatile boolean enablePicoPi = true;
    public volatile boolean enableOpenRgb = true;
    public volatile boolean enableYeelight = true;
    public List<String> disabledDevices = new java.util.concurrent.CopyOnWriteArrayList<>();
    public List<String> enabledLegacyDevices = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Use stable iterators when UI and network workers read or save settings concurrently. */
    public void normalizeCollections() {
        remoteServers = new java.util.concurrent.CopyOnWriteArrayList<>(remoteServers == null
                ? java.util.Collections.<RemoteServerProfile>emptyList() : remoteServers);
        openRgbEndpoints = new java.util.concurrent.CopyOnWriteArrayList<>(openRgbEndpoints == null
                ? java.util.Collections.singletonList(new OpenRGBEndpoint("127.0.0.1", 6742)) : openRgbEndpoints);
        disabledDevices = new java.util.concurrent.CopyOnWriteArrayList<>(disabledDevices == null
                ? java.util.Collections.<String>emptyList() : disabledDevices);
        enabledLegacyDevices = new java.util.concurrent.CopyOnWriteArrayList<>(enabledLegacyDevices == null
                ? java.util.Collections.<String>emptyList() : enabledLegacyDevices);
    }

    public boolean enableHealthBar = true;
    public boolean enableHungerBar = true;
    public boolean enableSaturationBar = true;
    public boolean enableExperienceBar = true;
    public boolean enableLocatorBar = true;
    public boolean enableCompassEffect = true;
    public boolean alwaysShowCompass = false;
    public boolean ignoreInventoryCompass = false;
    public boolean spinCompassWithoutTarget = true;
    public CompassFallbackTarget compassFallbackTarget = CompassFallbackTarget.WORLD_SPAWN;
    public CompassPriority compassPriority = CompassPriority.PRIORITY;
    public boolean enableLowHealthWarning = true;
    public float lowHealthThreshold = 10.0F;
    public boolean enableAbsorptionOverlay = true;
    public ExperienceLocatorMode experienceLocatorMode = ExperienceLocatorMode.AUTO;
    public boolean mergeSaturationIntoHunger = false;
    public float saturationTintStrength = 0.25F;
    public boolean enableBiomeEffects = true;
    public DimmingMode dimmingMode = DimmingMode.LOCAL_LIGHT;
    public float minBrightness = 0.2F;
    public boolean enableWeatherEffects = true;
    public boolean mirrorRamRainDirection = false;
    public boolean enableEndFlashEffect = true;
    public boolean enableOnFireEffect = true;
    public boolean enableInWaterEffect = true;
    public boolean enablePortalEffects = true;
    public boolean highlightMovementKeys = true;
    public boolean pulseChatKey = true;

    public List<String> ledOverrides = new ArrayList<>();
    public boolean restoreLightingOutsideWorld = true;
    public String outsideWorldProfile = "";

    public List<String> healthBarKeys = new ArrayList<>(Arrays.asList("F1", "F2", "F3", "F4"));
    public List<String> hungerBarKeys = new ArrayList<>(Arrays.asList("F5", "F6", "F7", "F8"));
    public List<String> saturationBarKeys = new ArrayList<>(Arrays.asList("F9", "F10", "F11", "F12"));
    public List<String> experienceBarKeys = new ArrayList<>(
            Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"));
    public List<String> locatorBarKeys = new ArrayList<>(
            Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"));
    public float locatorDotSize = 1.5F;
    public List<String> heartbeatRedKeys = new ArrayList<>(
            Arrays.asList("4", "5", "6", "7", "E", "T", "U", "D", "F", "H", "V", "B", "SPACE"));
    public List<String> heartbeatWhiteKeys = new ArrayList<>(Arrays.asList("R", "Y", "G"));
    public List<String> compassKeys = new ArrayList<>(
            Arrays.asList("NUMPAD8", "NUMPAD9", "NUMPAD6", "NUMPAD3", "NUMPAD2", "NUMPAD1", "NUMPAD4", "NUMPAD7"));
    public String compassCenterKey = "NUMPAD5";

    public int healthColor = 0xFF0000;
    public int healthDimColor = 0x1E0000;
    public int hungerColor = 0xFFA500;
    public int hungerDimColor = 0x1E0F00;
    public int saturationColor = 0xC8FF00;
    public int saturationDimColor = 0x283200;
    public int airColor = 0xADD8E6;
    public int airDimColor = 0x000032;
    public int experienceColor = 0x00FF00;
    public int experienceDimColor = 0x0A1E0A;
    public int heartbeatColor = 0xFF0000;
    public int absorptionColor = 0xFFD700;
    public int locatorBarColor = 0x0A0A0A;
}
