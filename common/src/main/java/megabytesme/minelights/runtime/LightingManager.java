package megabytesme.minelights.runtime;

import megabytesme.minelights.MineLightsClient;
import megabytesme.minelights.effects.DeviceLayout;
import megabytesme.minelights.effects.EffectPainter;
import megabytesme.minelights.effects.FrameStateDto;
import megabytesme.minelights.effects.KeyColorDto;
import megabytesme.minelights.effects.KeyNameStandardizer;
import megabytesme.minelights.effects.RGBColorDto;
import megabytesme.minelights.model.PlayerDto;
import megabytesme.minelights.network.RemoteServerProfile;
import megabytesme.minelights.network.V2ClientConnection;
import megabytesme.minelights.network.V2DevicePolicy;
import megabytesme.minelights.network.V2Endpoint;
import megabytesme.minelights.rgb.OpenRGBController;
import megabytesme.minelights.rgb.OpenRGBEndpoint;
import megabytesme.minelights.rgb.YeelightController;
//? if loader_neoforge || >=26.1 {
import net.minecraft.client.Minecraft;
//?} else {
/* import net.minecraft.client.MinecraftClient;
*///?}
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class LightingManager implements Runnable {
    public static final Logger LOGGER = LogManager.getLogger("MineLights-LightingManager");

    private EffectPainter effectPainter;
    private static final int FRAME_DURATION_MS = 33;
    private volatile boolean isInitialized = false;
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private final AtomicBoolean openRgbDataChanged = new AtomicBoolean(false);
    private FrameStateDto lastSentFrame = null;
    private int initializedOpenRgbControllerCount;

    private final List<OpenRGBController> openRgbControllers = Collections.synchronizedList(new ArrayList<OpenRGBController>());
    private final Map<Integer, OpenRGBDeviceRef> openRgbLedToDeviceMap = new HashMap<>();
    private final List<V2ClientConnection> v2Connections = Collections.synchronizedList(new ArrayList<V2ClientConnection>());
    private final Map<Integer, V2ElementTarget> v2ElementTargets = new HashMap<Integer, V2ElementTarget>();
    private final AtomicBoolean refreshAfterDisconnect = new AtomicBoolean(false);
    private long lastV2KeepaliveFrame;
    private long nextLocalV2Retry;
    private Set<String> lastPolicy;
    private static int nextV2InternalId = Integer.MIN_VALUE;
    private static final AtomicBoolean LEGACY_REMINDER_SENT = new AtomicBoolean(false);
    private Boolean devicesReleased;

    private final YeelightController yeelightController = new YeelightController();
    private final Map<Integer, Integer> yeelightLedToDeviceMap = new HashMap<>();

    private final List<DeviceLayout> deviceLayouts = Collections.synchronizedList(new ArrayList<>());

    public LightingManager() {
    }

    public void requestStop() {
        stopping.set(true);
    }

    private boolean isStopping() {
        return stopping.get() || !MineLightsClient.CONFIG.enableMod;
    }

    public String requestServerShutdown(String serverId) {
        return requestServerShutdown(serverId, null);
    }

    public String requestServerShutdown(String serverId, String expectedLaunchId) {
        try {
            V2Endpoint local = serverId == null ? V2Endpoint.readLocal() : null;
            if (serverId == null && local == null) return "unavailable";
            String selectedId = serverId == null ? local.serverId : serverId;
            synchronized (v2Connections) {
                for (V2ClientConnection connection : v2Connections) {
                    if (selectedId.equals(connection.serverId) && connection.isConnected()) {
                        if (expectedLaunchId != null && !connection.matchesLaunchId(expectedLaunchId)) return "not_owned";
                        return connection.requestServerShutdown();
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return "unavailable";
    }

    private void performHandshakes() {
        if (isStopping()) return;
        initializeV2Connections();

        Thread openRgbThread = new Thread(() -> {
            if (isStopping() || !MineLightsClient.CONFIG.enableOpenRgb)
                return;
            List<OpenRGBEndpoint> endpoints = MineLightsClient.CONFIG.openRgbEndpoints;
            if (endpoints == null) {
                endpoints = new java.util.concurrent.CopyOnWriteArrayList<OpenRGBEndpoint>();
                endpoints.add(new OpenRGBEndpoint("127.0.0.1", 6742));
                MineLightsClient.CONFIG.openRgbEndpoints = endpoints;
                MineLightsClient.saveConfig();
            }
            for (OpenRGBEndpoint endpoint : new ArrayList<OpenRGBEndpoint>(endpoints)) {
                if (isStopping()) break;
                if (endpoint == null || !endpoint.isValid()) {
                    LOGGER.warn("Ignoring invalid OpenRGB endpoint in MineLights configuration.");
                    continue;
                }
                OpenRGBController controller = new OpenRGBController();
                if (controller.connect(endpoint.host, endpoint.port)) {
                    synchronized (openRgbControllers) {
                        if (isStopping()) controller.disconnect();
                        else {
                            openRgbControllers.add(controller);
                            openRgbDataChanged.set(true);
                        }
                    }
                }
            }
        });
        openRgbThread.setName("MineLights-OpenRGB-Handshake");

        Thread yeelightThread = new Thread(() -> {
            if (isStopping() || !MineLightsClient.CONFIG.enableYeelight)
                return;
            if (yeelightController.discover(() -> !isStopping())) {
                if (isStopping()) { yeelightController.disconnect(); return; }
                List<YeelightController.YeelightDevice> devices = yeelightController.getDevices();
                for (int i = 0; i < devices.size(); i++) {
                    YeelightController.YeelightDevice device = devices.get(i);
                    String uniqueId = "Yeelight|"
                            + (device.name != null && !device.name.isEmpty() ? device.name : device.id);
                    if (MineLightsClient.CONFIG.disabledDevices.contains(uniqueId))
                        continue;
                    MineLightsClient.discoveredDevices.add(uniqueId);
                    int globalLedId = -1 - i;

                    DeviceLayout layout = new DeviceLayout("Yeelight " + i, "YEELIGHT");
                    layout.addMapping("YEELIGHT_" + i, globalLedId);
                    deviceLayouts.add(layout);
                    yeelightLedToDeviceMap.put(globalLedId, i);
                }
            }
        });
        yeelightThread.setName("MineLights-Yeelight-Discovery");

        try {
            openRgbThread.setDaemon(true);
            yeelightThread.setDaemon(true);
            openRgbThread.start();
            yeelightThread.start();

            openRgbThread.join(5000);
            yeelightThread.join(5000);

            if (isStopping()) return;

            synchronized (openRgbControllers) {
                parseOpenRGBData();
                this.effectPainter = new EffectPainter(deviceLayouts);
                initializedOpenRgbControllerCount = openRgbControllers.size();
                openRgbDataChanged.set(false);
                this.isInitialized = true;
            }

            long totalLeds = deviceLayouts.stream().mapToLong(d -> d.getAllLeds().size()).sum();
            LOGGER.info("Initialization complete. Found {} devices with a total of {} LEDs.", deviceLayouts.size(),
                    totalLeds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean initializeV2Connections() {
        if (isStopping()) return false;
        final String clientId;
        final List<RemoteServerProfile> remoteProfiles;
        synchronized (megabytesme.minelights.config.SimpleJsonConfig.configLock()) {
            if (MineLightsClient.CONFIG.v2ClientId == null || MineLightsClient.CONFIG.v2ClientId.trim().isEmpty()) {
                MineLightsClient.CONFIG.v2ClientId = V2ClientConnection.newClientId();
                MineLightsClient.saveConfig();
            }
            if (MineLightsClient.CONFIG.remoteServers == null) MineLightsClient.CONFIG.remoteServers = new java.util.concurrent.CopyOnWriteArrayList<RemoteServerProfile>();
            if (MineLightsClient.CONFIG.disabledDevices == null) MineLightsClient.CONFIG.disabledDevices = new java.util.concurrent.CopyOnWriteArrayList<String>();
            clientId = MineLightsClient.CONFIG.v2ClientId;
            remoteProfiles = new ArrayList<RemoteServerProfile>(MineLightsClient.CONFIG.remoteServers);
        }
        Set<String> disabled = V2DevicePolicy.exclusions(MineLightsClient.CONFIG);
        lastPolicy = disabled;
        boolean localConnected = false;
        try {
            V2Endpoint localEndpoint = V2Endpoint.readLocal();
            if (localEndpoint != null) {
                final String localSecret;
                synchronized (megabytesme.minelights.config.SimpleJsonConfig.configLock()) {
                    new megabytesme.minelights.config.SimpleJsonConfig("minelights").ensureCredentialStorageAvailable();
                    if (MineLightsClient.CONFIG.v2ClientSecret == null || MineLightsClient.CONFIG.v2ClientSecret.isEmpty()) {
                        MineLightsClient.CONFIG.v2ClientSecret = V2ClientConnection.randomSecret();
                        MineLightsClient.saveConfig();
                    }
                    localSecret = MineLightsClient.CONFIG.v2ClientSecret;
                }
                byte[] enrollmentKey = V2Endpoint.readLocalEnrollmentKey();
                V2ClientConnection connection = V2ClientConnection.connectLocal(localEndpoint,
                        clientId, localSecret,
                        enrollmentKey, "minelights-java", MineLightsClient.getModVersion(), disabled);
                if (isStopping()) { connection.close(); return false; }
                addV2Connection(connection);
                localConnected = true;
                MineLightsClient.isProxyConnected = true;
                LOGGER.info("Connected to local MineLights Server '{}' with Protocol V2.", connection.serverName);
                if (!LEGACY_REMINDER_SENT.get() && connection.legacyLocalAccessEnabled()
                        && LEGACY_REMINDER_SENT.compareAndSet(false, true)) {
                    MineLightsClient.sendChatMessage("[MineLights] Legacy local access remains enabled in MineLights Server. If older clients no longer need it, turn off Allow legacy local clients in the server tray menu.");
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Could not connect to the local MineLights V2 endpoint: {}", e.getMessage());
        }

        boolean configChanged = false;
        for (RemoteServerProfile profile : remoteProfiles) {
            if (isStopping()) break;
            if (profile == null) {
                continue;
            }
            try {
                V2ClientConnection connection = V2ClientConnection.connectRemote(profile,
                        clientId, "minelights-java",
                        MineLightsClient.getModVersion(), disabled);
                if (isStopping()) { connection.close(); break; }
                addV2Connection(connection);
                configChanged = true;
                LOGGER.info("Connected to remote MineLights V2 server '{}'.", connection.serverName);
            } catch (IOException e) {
                // Persist a successful one-time enrollment even if the subsequent reconnect failed.
                if (profile.clientSecret != null && !profile.clientSecret.isEmpty()) {
                    configChanged = true;
                }
                LOGGER.warn("Could not connect to remote MineLights server '{}': {}",
                        profile.displayName(), e.getMessage());
            }
        }
        if (configChanged) {
            MineLightsClient.saveConfig();
        }
        return localConnected;
    }

    private void addV2Connection(final V2ClientConnection connection) {
        v2Connections.add(connection);
        connection.setDeviceChangeListener(new V2ClientConnection.DeviceChangeListener() {
            @Override
            public void onDevicesChanged(V2ClientConnection changedConnection,
                    List<V2ClientConnection.DeviceDescriptor> updatedDevices) {
                MineLightsClient.refreshLightingManager();
            }
        });
        for (V2ClientConnection.DeviceDescriptor device : connection.getDevices()) {
            String deviceKey = "MineLights V2|" + connection.serverId + "|" + device.deviceId;
            MineLightsClient.discoveredDeviceNames.put(deviceKey, connection.serverName + " / " + device.name);
            MineLightsClient.discoveredLegacyDeviceKeys.put(deviceKey, V2DevicePolicy.legacyKey(device));
            MineLightsClient.discoveredDevices.add(deviceKey);
            if (!V2DevicePolicy.allows(V2DevicePolicy.exclusions(MineLightsClient.CONFIG), connection.serverId, device)) {
                continue;
            }
            DeviceLayout layout = new DeviceLayout(connection.serverName + " / " + device.name, "SERVER");
            for (V2ClientConnection.ElementDescriptor element : device.elements) {
                int internalId = nextV2InternalId++;
                String key = KeyNameStandardizer.standardize(element.keyName);
                if (key == null || key.isEmpty()) {
                    key = "UNKNOWN";
                }
                layout.addMapping(key, internalId);
                v2ElementTargets.put(internalId,
                        new V2ElementTarget(connection, device.deviceId, element.elementId));
            }
            deviceLayouts.add(layout);
        }
    }

    private void parseOpenRGBData() {
        LOGGER.info("[OpenRGB] Parsing data from OpenRGB...");
        long nextOpenRgbLedId = 0;
        for (OpenRGBController controller : openRgbControllers) {
            List<OpenRGBController.OpenRGBDevice> devices = controller.getDevices();
            for (int localDeviceIndex = 0; localDeviceIndex < devices.size(); localDeviceIndex++) {
                OpenRGBController.OpenRGBDevice device = devices.get(localDeviceIndex);
                String endpointAddress = device.endpointAddress == null ? "127.0.0.1:6742" : device.endpointAddress;
                String uniqueId = "127.0.0.1:6742".equals(endpointAddress)
                        ? "OpenRGB|" + device.name : "OpenRGB|" + endpointAddress + "|" + device.name;
                MineLightsClient.discoveredDevices.add(uniqueId);
                long lastLedId = nextOpenRgbLedId + Math.max(0, device.ledNames.size() - 1);
                if (lastLedId > Integer.MAX_VALUE) {
                    LOGGER.warn("Skipping OpenRGB device {} because its LED IDs exceed the supported range.", device.name);
                    continue;
                }
                int openRgbLedOffset = (int) nextOpenRgbLedId;
                nextOpenRgbLedId += device.ledNames.size();
                if (MineLightsClient.CONFIG.disabledDevices.contains(uniqueId)) {
                    continue;
                }

                DeviceLayout layout = new DeviceLayout(device.name + " (" + endpointAddress + ")", "OPENRGB");
                Map<Integer, String> overrides = getLedOverrides(device.name);

                for (int i = 0; i < device.ledNames.size(); i++) {
                    String keyName = device.ledNames.get(i);
                    if (overrides.containsKey(i)) {
                        LOGGER.info("  LED {} \"{}\" overridden as \"{}\"", i, keyName, overrides.get(i));
                        keyName = overrides.get(i);
                    }
                    String standardizedName = KeyNameStandardizer.standardize(keyName);
                    int globalLedId = openRgbLedOffset + i;
                    layout.addMapping(standardizedName, globalLedId);
                    openRgbLedToDeviceMap.put(globalLedId,
                            new OpenRGBDeviceRef(controller, localDeviceIndex, openRgbLedOffset));
                }
                deviceLayouts.add(layout);
                LOGGER.info("> Mapped device [OpenRGB]: {} ({} LEDs)", device.name, layout.getAllLeds().size());
            }
        }
    }

    private Map<Integer, String> getLedOverrides(String deviceName) {
        Map<Integer, String> overrides = new HashMap<>();
        if (MineLightsClient.CONFIG.ledOverrides == null) {
            return overrides;
        }
        for (String entry : MineLightsClient.CONFIG.ledOverrides) {
            int separator = entry.lastIndexOf('|');
            int equals = entry.indexOf('=', separator + 1);
            if (separator < 0 || equals < 0) {
                LOGGER.warn("Ignoring LED override \"{}\": expected \"Device name|LED index=KEY\"", entry);
                continue;
            }
            String device = entry.substring(0, separator).trim().toLowerCase(java.util.Locale.ROOT);
            if (!deviceName.toLowerCase(java.util.Locale.ROOT).contains(device)) {
                continue;
            }
            try {
                int ledIndex = Integer.parseInt(entry.substring(separator + 1, equals).trim());
                overrides.put(ledIndex, entry.substring(equals + 1).trim());
            } catch (NumberFormatException e) {
                LOGGER.warn("Ignoring LED override \"{}\": LED index is not a number", entry);
            }
        }
        return overrides;
    }

    private void releaseOpenRgbDevices() {
        synchronized (openRgbControllers) {
            for (OpenRGBController controller : openRgbControllers) {
                releaseOpenRgbController(controller);
            }
        }
    }

    private void releaseOpenRgbController(OpenRGBController controller) {
        Set<Integer> enabled = enabledOpenRgbDevices(controller);
        controller.restoreOriginalModes(enabled);
        String profile = MineLightsClient.CONFIG.outsideWorldProfile;
        if (profile != null && !profile.trim().isEmpty()
                && enabled.size() == controller.getDevices().size()) {
            controller.loadProfile(profile.trim());
        }
    }

    private void takeOverOpenRgbDevices() {
        synchronized (openRgbControllers) {
            for (OpenRGBController controller : openRgbControllers) {
                controller.enterCustomMode(enabledOpenRgbDevices(controller));
            }
        }
    }

    private Set<Integer> enabledOpenRgbDevices(OpenRGBController controller) {
        Set<Integer> enabled = new HashSet<>();
        List<OpenRGBController.OpenRGBDevice> devices = controller.getDevices();
        for (int i = 0; i < devices.size(); i++) {
            OpenRGBController.OpenRGBDevice device = devices.get(i);
            String id = "127.0.0.1:6742".equals(device.endpointAddress)
                    ? "OpenRGB|" + device.name
                    : "OpenRGB|" + device.endpointAddress + "|" + device.name;
            if (!MineLightsClient.CONFIG.disabledDevices.contains(id)) enabled.add(i);
        }
        return enabled;
    }

    @Override
    public void run() {
        try {
            performHandshakes();
            while (!isInitialized && !isStopping()) {
                Thread.sleep(100);
            }
            while (!Thread.currentThread().isInterrupted() && !isStopping()) {
                if (openRgbDataChanged.get()) {
                    List<OpenRGBController> addedControllers = new ArrayList<OpenRGBController>();
                    synchronized (openRgbControllers) {
                        if (openRgbDataChanged.getAndSet(false)) {
                            if (initializedOpenRgbControllerCount < openRgbControllers.size()) {
                                addedControllers.addAll(openRgbControllers.subList(
                                        initializedOpenRgbControllerCount, openRgbControllers.size()));
                                initializedOpenRgbControllerCount = openRgbControllers.size();
                            }
                            deviceLayouts.removeIf(layout -> "OPENRGB".equals(layout.deviceType));
                            openRgbLedToDeviceMap.clear();
                            parseOpenRGBData();
                            effectPainter = new EffectPainter(deviceLayouts);
                            lastSentFrame = null;
                        }
                    }
                    if (devicesReleased != null) {
                        for (OpenRGBController controller : addedControllers) {
                            if (devicesReleased.booleanValue()) releaseOpenRgbController(controller);
                            else controller.enterCustomMode(enabledOpenRgbDevices(controller));
                        }
                    }
                }
                long frameStart = System.currentTimeMillis();

                //? if loader_neoforge || >=26.1 {
                Minecraft client = MineLightsClient.getMinecraft();
                //?} else {
                /* MinecraftClient client = MinecraftClient.getInstance();
                *///?}
                PlayerDto playerState;
                //? if loader_forge && <=1.9.4 {
                if (client == null || client.thePlayer == null || !MineLightsClient.CONFIG.enableMod) {
                //?} else {
                /* if (client == null || client.player == null || !MineLightsClient.CONFIG.enableMod) {
                *///?}
                    playerState = new PlayerDto();
                    playerState.setInGame(false);
                } else {
                    playerState = PlayerDataCollector.getCurrentState(client);
                }

                boolean releaseOpenRgb = !playerState.getInGame() && MineLightsClient.CONFIG.restoreLightingOutsideWorld;
                if (devicesReleased == null || releaseOpenRgb != devicesReleased.booleanValue()) {
                    if (releaseOpenRgb) {
                        releaseOpenRgbDevices();
                    } else {
                        takeOverOpenRgbDevices();
                    }
                    devicesReleased = releaseOpenRgb;
                    lastSentFrame = null;
                }

                FrameStateDto frameState = effectPainter.paint(playerState);
                scheduleV2ReconnectIfNeeded();

                long now = System.currentTimeMillis();
                if (frameState.equals(lastSentFrame)) {
                    if (now - lastV2KeepaliveFrame >= 500) {
                        sendV2Frames(frameState);
                        lastV2KeepaliveFrame = now;
                    }
                    long frameEnd = System.currentTimeMillis();
                    long sleepTime = FRAME_DURATION_MS - (frameEnd - frameStart);
                    if (sleepTime > 0)
                        Thread.sleep(sleepTime);
                    continue;
                }
                lastSentFrame = frameState;

                Map<OpenRGBDeviceRef, List<KeyColorDto>> openRgbUpdates = new HashMap<OpenRGBDeviceRef, List<KeyColorDto>>();

                for (Map.Entry<Integer, RGBColorDto> entry : frameState.keys.entrySet()) {
                    int globalLedId = entry.getKey();
                    RGBColorDto color = entry.getValue();

                    if (v2ElementTargets.containsKey(globalLedId)) {
                        continue;
                    } else if (openRgbLedToDeviceMap.containsKey(globalLedId)) {
                        OpenRGBDeviceRef deviceRef = openRgbLedToDeviceMap.get(globalLedId);
                        openRgbUpdates.computeIfAbsent(deviceRef, k -> new ArrayList<KeyColorDto>())
                                .add(new KeyColorDto(globalLedId, color));
                    } else if (yeelightLedToDeviceMap.containsKey(globalLedId)) {
                        int deviceIndex = yeelightLedToDeviceMap.get(globalLedId);
                        yeelightController.updateLed(deviceIndex, color);
                    }
                }

                sendV2Frames(frameState);
                lastV2KeepaliveFrame = System.currentTimeMillis();

                if (!Boolean.TRUE.equals(devicesReleased) && !openRgbUpdates.isEmpty()) {
                    for (Map.Entry<OpenRGBDeviceRef, List<KeyColorDto>> deviceUpdate : openRgbUpdates.entrySet()) {
                        OpenRGBDeviceRef deviceRef = deviceUpdate.getKey();
                        deviceRef.controller.updateLeds(deviceRef.deviceIndex, deviceUpdate.getValue(),
                                deviceRef.globalLedOffset);
                    }
                }

                long frameEnd = System.currentTimeMillis();
                long sleepTime = FRAME_DURATION_MS - (frameEnd - frameStart);
                if (sleepTime > 0)
                    Thread.sleep(sleepTime);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOGGER.error("Error in lighting loop", e);
        } finally {
            stopping.set(true);
            if (MineLightsClient.CONFIG.restoreLightingOutsideWorld) {
                releaseOpenRgbDevices();
            }
            synchronized (openRgbControllers) {
                for (OpenRGBController controller : openRgbControllers) controller.disconnect();
            }
            synchronized (v2Connections) {
                for (V2ClientConnection connection : v2Connections) connection.close();
            }
            yeelightController.disconnect();
        }
    }

    private void sendV2Frames(FrameStateDto frameState) {
        Map<V2ClientConnection, Map<String, Map<Long, RGBColorDto>>> updates =
                new HashMap<V2ClientConnection, Map<String, Map<Long, RGBColorDto>>>();
        for (Map.Entry<Integer, RGBColorDto> entry : frameState.keys.entrySet()) {
            V2ElementTarget target = v2ElementTargets.get(entry.getKey());
            if (target == null || !target.connection.hasLease(target.deviceId)) {
                continue;
            }
            Map<String, Map<Long, RGBColorDto>> devices = updates.get(target.connection);
            if (devices == null) {
                devices = new HashMap<String, Map<Long, RGBColorDto>>();
                updates.put(target.connection, devices);
            }
            Map<Long, RGBColorDto> colors = devices.get(target.deviceId);
            if (colors == null) {
                colors = new HashMap<Long, RGBColorDto>();
                devices.put(target.deviceId, colors);
            }
            colors.put(target.elementId, entry.getValue());
        }
        for (Map.Entry<V2ClientConnection, Map<String, Map<Long, RGBColorDto>>> update : updates.entrySet()) {
            try {
                update.getKey().sendFrame(update.getValue());
            } catch (IOException e) {
                LOGGER.warn("Could not send a lighting frame to MineLights Server '{}': {}",
                        update.getKey().serverName, e.getMessage());
            }
        }
    }

    private void scheduleV2ReconnectIfNeeded() {
        Set<String> currentPolicy = V2DevicePolicy.exclusions(MineLightsClient.CONFIG);
        if (lastPolicy != null && !lastPolicy.equals(currentPolicy)) {
            scheduleRefresh(0, currentPolicy);
            return;
        }
        long now = System.currentTimeMillis();
        if (!MineLightsClient.isProxyConnected && now >= nextLocalV2Retry) {
            nextLocalV2Retry = now + 5000;
            if (V2Endpoint.localEndpointDocumentExists()) {
                scheduleRefresh(5000, null);
                return;
            }
        }
        for (V2ClientConnection connection : v2Connections) {
            if (connection.isConnected()) {
                continue;
            }
            scheduleRefresh(5000, null);
            return;
        }
    }

    private void scheduleRefresh(final long delayMillis, final Set<String> changedPolicy) {
        if (refreshAfterDisconnect.compareAndSet(false, true)) {
            Thread refreshThread = new Thread(() -> {
                try {
                    if (delayMillis > 0) Thread.sleep(delayMillis);
                    if (changedPolicy != null) {
                        List<V2ClientConnection> connections;
                        synchronized (v2Connections) { connections = new ArrayList<V2ClientConnection>(v2Connections); }
                        for (V2ClientConnection connection : connections) {
                            if (isStopping()) return;
                            connection.setRequestedIntegrations(changedPolicy);
                        }
                    }
                    if (!isStopping()) MineLightsClient.refreshLightingManager();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    refreshAfterDisconnect.set(false);
                }
            }, "MineLights-V2-Reconnect");
            refreshThread.setDaemon(true);
            refreshThread.start();
        }
    }

    private static final class V2ElementTarget {
        final V2ClientConnection connection;
        final String deviceId;
        final long elementId;

        V2ElementTarget(V2ClientConnection connection, String deviceId, long elementId) {
            this.connection = connection;
            this.deviceId = deviceId;
            this.elementId = elementId;
        }
    }

    private static final class OpenRGBDeviceRef {
        final OpenRGBController controller;
        final int deviceIndex;
        final int globalLedOffset;

        OpenRGBDeviceRef(OpenRGBController controller, int deviceIndex, int globalLedOffset) {
            this.controller = controller;
            this.deviceIndex = deviceIndex;
            this.globalLedOffset = globalLedOffset;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof OpenRGBDeviceRef)) return false;
            OpenRGBDeviceRef that = (OpenRGBDeviceRef) other;
            return controller == that.controller && deviceIndex == that.deviceIndex;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(controller) + deviceIndex;
        }
    }
}
