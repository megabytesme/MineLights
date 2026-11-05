package megabytesme.minelights.runtime;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import megabytesme.minelights.MineLightsClient;
import megabytesme.minelights.effects.DeviceLayout;
import megabytesme.minelights.effects.EffectPainter;
import megabytesme.minelights.effects.FrameStateDto;
import megabytesme.minelights.effects.KeyColorDto;
import megabytesme.minelights.effects.KeyNameStandardizer;
import megabytesme.minelights.effects.RGBColorDto;
import megabytesme.minelights.model.PlayerDto;
import megabytesme.minelights.network.UDPClient;
import megabytesme.minelights.network.RemoteServerProfile;
import megabytesme.minelights.network.V2ClientConnection;
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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
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
    private final Gson gson = new Gson();
    private static final int FRAME_DURATION_MS = 33;
    private volatile boolean isInitialized = false;
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private FrameStateDto lastSentFrame = null;

    private final List<OpenRGBController> openRgbControllers = Collections.synchronizedList(new ArrayList<OpenRGBController>());
    private final Map<Integer, OpenRGBDeviceRef> openRgbLedToDeviceMap = new HashMap<>();
    private final List<V2ClientConnection> v2Connections = Collections.synchronizedList(new ArrayList<V2ClientConnection>());
    private final Map<Integer, V2ElementTarget> v2ElementTargets = new HashMap<Integer, V2ElementTarget>();
    private final AtomicBoolean refreshAfterDisconnect = new AtomicBoolean(false);
    private long lastV2KeepaliveFrame;
    private static int nextV2InternalId = Integer.MIN_VALUE;

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
        final JsonObject[] serverDataContainer = new JsonObject[1];

        final boolean localV2EndpointPresent = V2Endpoint.localEndpointDocumentExists();
        final boolean allowV1Fallback = !localV2EndpointPresent || MineLightsClient.CONFIG.allowLegacyV1Fallback;
        boolean v2LocalConnected = initializeV2Connections();

        Thread serverHandshakeThread = new Thread(() -> {
            if (isStopping() || v2LocalConnected || !allowV1Fallback) {
                return;
            }
            if (localV2EndpointPresent) {
                MineLightsClient.sendChatMessage("[MineLights] V2 connection failed; using explicitly enabled legacy V1 fallback. V1 is unauthenticated and loopback-only.");
            }
            try (Socket clientSocket = new Socket()) {
                clientSocket.connect(new InetSocketAddress("127.0.0.1", 63211), 2000);
                clientSocket.setSoTimeout(3000);
                if (isStopping()) return;
                MineLightsClient.isProxyConnected = true;

                DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream());
                DataInputStream dis = new DataInputStream(clientSocket.getInputStream());

                JsonObject configPayload = new JsonObject();
                JsonArray enabledIntegrations = new JsonArray();
                if (MineLightsClient.CONFIG.enableCorsair)
                    enabledIntegrations.add(new com.google.gson.JsonPrimitive("Corsair"));
                if (MineLightsClient.CONFIG.enableAsus)
                    enabledIntegrations.add(new com.google.gson.JsonPrimitive("Asus"));
                if (MineLightsClient.CONFIG.enableLogitech)
                    enabledIntegrations.add(new com.google.gson.JsonPrimitive("Logitech"));
                if (MineLightsClient.CONFIG.enableRazer)
                    enabledIntegrations.add(new com.google.gson.JsonPrimitive("Razer"));
                if (MineLightsClient.CONFIG.enableWooting)
                    enabledIntegrations.add(new com.google.gson.JsonPrimitive("Wooting"));
                if (MineLightsClient.CONFIG.enableSteelSeries)
                    enabledIntegrations.add(new com.google.gson.JsonPrimitive("SteelSeries"));
                if (MineLightsClient.CONFIG.enableMsi)
                    enabledIntegrations.add(new com.google.gson.JsonPrimitive("Msi"));
                if (MineLightsClient.CONFIG.enableNovation)
                    enabledIntegrations.add(new com.google.gson.JsonPrimitive("Novation"));
                if (MineLightsClient.CONFIG.enablePicoPi)
                    enabledIntegrations.add(new com.google.gson.JsonPrimitive("PicoPi"));

                configPayload.add("enabled_integrations", enabledIntegrations);
                configPayload.add("disabled_devices", new Gson().toJsonTree(MineLightsClient.CONFIG.disabledDevices));

                byte[] configBytes = configPayload.toString().getBytes(StandardCharsets.UTF_8);
                dos.writeInt(configBytes.length);
                dos.write(configBytes);
                dos.flush();

                int length = dis.readInt();
                if (length > 0) {
                    byte[] jsonBytes = new byte[length];
                    dis.readFully(jsonBytes);
                    String jsonString = new String(jsonBytes, StandardCharsets.UTF_8);
                    serverDataContainer[0] = new JsonParser().parse(jsonString).getAsJsonObject();
                }
            } catch (Exception e) {
                LOGGER.error("Failed during handshake with MineLights Server. Is it running? Details: {}",
                        e.getMessage());
                MineLightsClient.isProxyConnected = false;
            }
        });
        serverHandshakeThread.setName("MineLights-Server-Handshake");

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
                        else openRgbControllers.add(controller);
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
                int yeelightLedOffset = 3000;
                for (int i = 0; i < devices.size(); i++) {
                    YeelightController.YeelightDevice device = devices.get(i);
                    String uniqueId = "Yeelight|"
                            + (device.name != null && !device.name.isEmpty() ? device.name : device.id);
                    if (MineLightsClient.CONFIG.disabledDevices.contains(uniqueId))
                        continue;
                    MineLightsClient.discoveredDevices.add(uniqueId);
                    int globalLedId = yeelightLedOffset + i;

                    DeviceLayout layout = new DeviceLayout("Yeelight " + i, "YEELIGHT");
                    layout.addMapping("YEELIGHT_" + i, globalLedId);
                    deviceLayouts.add(layout);
                    yeelightLedToDeviceMap.put(globalLedId, i);
                }
            }
        });
        yeelightThread.setName("MineLights-Yeelight-Discovery");

        try {
            serverHandshakeThread.setDaemon(true);
            openRgbThread.setDaemon(true);
            yeelightThread.setDaemon(true);
            serverHandshakeThread.start();
            openRgbThread.start();
            yeelightThread.start();

            serverHandshakeThread.join(5000);
            openRgbThread.join(5000);
            yeelightThread.join(5000);

            if (isStopping()) return;

            if (serverDataContainer[0] != null) {
                parseServerHandshakeData(serverDataContainer[0]);
            }

            parseOpenRGBData();

            this.effectPainter = new EffectPainter(deviceLayouts);
            this.isInitialized = true;

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
        Set<String> disabled = new HashSet<String>(MineLightsClient.CONFIG.disabledDevices);
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
            MineLightsClient.discoveredDevices.add(deviceKey);
            if (MineLightsClient.CONFIG.disabledDevices.contains(deviceKey)
                    || MineLightsClient.CONFIG.disabledDevices.contains(connection.serverId + "|" + device.deviceId)) {
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

    private void parseServerHandshakeData(JsonObject handshakeData) {
        LOGGER.info("--- Parsing Handshake Data from MineLights Server ---");
        if (handshakeData.has("devices")) {
            for (JsonElement deviceElement : handshakeData.getAsJsonArray("devices")) {
                JsonObject deviceObject = deviceElement.getAsJsonObject();
                String name = deviceObject.get("name").getAsString();
                String sdk = deviceObject.get("sdk").getAsString();
                String uniqueId = sdk + "|" + name;

                MineLightsClient.discoveredDevices.add(uniqueId);
                DeviceLayout layout = new DeviceLayout(name, "SERVER");

                if (deviceObject.has("key_map")) {
                    JsonObject mapObject = deviceObject.getAsJsonObject("key_map");
                    for (Map.Entry<String, JsonElement> entry : mapObject.entrySet()) {
                        String standardizedName = KeyNameStandardizer.standardize(entry.getKey());
                        int ledId = entry.getValue().getAsInt();
                        layout.addMapping(standardizedName, ledId);
                    }
                }
                deviceLayouts.add(layout);
                LOGGER.info("> Mapped device [{}]: {} ({} LEDs)", sdk, name, layout.getAllLeds().size());
            }
        }
    }

    private void parseOpenRGBData() {
        LOGGER.info("[OpenRGB] Parsing data from OpenRGB...");
        int openRgbDeviceIndex = 0;
        for (OpenRGBController controller : openRgbControllers) {
            List<OpenRGBController.OpenRGBDevice> devices = controller.getDevices();
            for (int localDeviceIndex = 0; localDeviceIndex < devices.size(); localDeviceIndex++) {
                OpenRGBController.OpenRGBDevice device = devices.get(localDeviceIndex);
                String endpointAddress = device.endpointAddress == null ? "127.0.0.1:6742" : device.endpointAddress;
                String uniqueId = "127.0.0.1:6742".equals(endpointAddress)
                        ? "OpenRGB|" + device.name : "OpenRGB|" + endpointAddress + "|" + device.name;
                MineLightsClient.discoveredDevices.add(uniqueId);
                if (MineLightsClient.CONFIG.disabledDevices.contains(uniqueId)) {
                    openRgbDeviceIndex++;
                    continue;
                }

                DeviceLayout layout = new DeviceLayout(device.name + " (" + endpointAddress + ")", "OPENRGB");
                Map<String, Integer> openRgbMap = device.keyMap;
                int openRgbLedOffset = 2000 + (openRgbDeviceIndex * 1000);

                for (Map.Entry<String, Integer> entry : openRgbMap.entrySet()) {
                    String standardizedName = KeyNameStandardizer.standardize(entry.getKey());
                    int globalLedId = openRgbLedOffset + entry.getValue();
                    layout.addMapping(standardizedName, globalLedId);
                    openRgbLedToDeviceMap.put(globalLedId,
                            new OpenRGBDeviceRef(controller, localDeviceIndex, openRgbLedOffset));
                }
                deviceLayouts.add(layout);
                LOGGER.info("> Mapped device [OpenRGB]: {} ({} LEDs)", device.name, layout.getAllLeds().size());
                openRgbDeviceIndex++;
            }
        }
    }

    @Override
    public void run() {
        try {
            performHandshakes();
            while (!isInitialized && !isStopping()) {
                Thread.sleep(100);
            }
            while (!Thread.currentThread().isInterrupted() && !isStopping()) {
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

                List<KeyColorDto> proxyUpdateList = new ArrayList<>();
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
                    } else {
                        proxyUpdateList.add(new KeyColorDto(globalLedId, color));
                    }
                }

                if (!proxyUpdateList.isEmpty()) {
                    JsonObject proxyPayload = new JsonObject();
                    proxyPayload.add("led_colors", gson.toJsonTree(proxyUpdateList));
                    UDPClient.sendFrameData(gson.toJson(proxyPayload));
                }

                sendV2Frames(frameState);
                lastV2KeepaliveFrame = System.currentTimeMillis();

                if (!openRgbUpdates.isEmpty()) {
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
        for (V2ClientConnection connection : v2Connections) {
            if (connection.isConnected()) {
                continue;
            }
            if (refreshAfterDisconnect.compareAndSet(false, true)) {
                Thread refreshThread = new Thread(() -> {
                    try {
                        Thread.sleep(5000);
                        MineLightsClient.refreshLightingManager();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        refreshAfterDisconnect.set(false);
                    }
                }, "MineLights-V2-Reconnect");
                refreshThread.setDaemon(true);
                refreshThread.start();
            }
            return;
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
