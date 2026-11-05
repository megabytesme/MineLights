package megabytesme.minelights.config;

import java.util.ArrayList;
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
    /** Permit unauthenticated loopback V1 only when a discovered V2 endpoint is unavailable. */
    public boolean allowLegacyV1Fallback = false;

    /** Credentials are stored in the protected MineLights configuration file and never logged. */
    public String v2ClientId;
    public String v2ClientSecret;
    public List<RemoteServerProfile> remoteServers = new java.util.concurrent.CopyOnWriteArrayList<>();
    /** Native OpenRGB endpoints; these do not use MineLights V2 authentication or TLS. */
    public List<OpenRGBEndpoint> openRgbEndpoints = new java.util.concurrent.CopyOnWriteArrayList<>(java.util.Collections.singletonList(
            new OpenRGBEndpoint("127.0.0.1", 6742)));

    public boolean enableCorsair = true;
    public boolean enableAsus = true;
    public boolean enableLogitech = true;
    public boolean enableRazer = true;
    public boolean enableWooting = true;
    public boolean enableSteelSeries = true;
    public boolean enableMsi = true;
    public boolean enableNovation = true;
    public boolean enablePicoPi = true;
    public boolean enableOpenRgb = true;
    public boolean enableYeelight = true;
    public List<String> disabledDevices = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Use stable iterators when UI and network workers read or save settings concurrently. */
    public void normalizeCollections() {
        remoteServers = new java.util.concurrent.CopyOnWriteArrayList<>(remoteServers == null
                ? java.util.Collections.<RemoteServerProfile>emptyList() : remoteServers);
        openRgbEndpoints = new java.util.concurrent.CopyOnWriteArrayList<>(openRgbEndpoints == null
                ? java.util.Collections.singletonList(new OpenRGBEndpoint("127.0.0.1", 6742)) : openRgbEndpoints);
        disabledDevices = new java.util.concurrent.CopyOnWriteArrayList<>(disabledDevices == null
                ? java.util.Collections.<String>emptyList() : disabledDevices);
    }

    public boolean enableHealthBar = true;
    public boolean enableHungerBar = true;
    public boolean enableSaturationBar = true;
    public boolean enableExperienceBar = true;
    public boolean enableLocatorBar = true;
    public boolean enableCompassEffect = true;
    public boolean alwaysShowCompass = false;
    public CompassPriority compassPriority = CompassPriority.PRIORITY;
    public boolean enableLowHealthWarning = true;
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
}
