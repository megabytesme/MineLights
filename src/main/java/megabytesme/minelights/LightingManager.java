package megabytesme.minelights;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import megabytesme.minelights.effects.DeviceLayout;
import megabytesme.minelights.effects.EffectPainter;
import megabytesme.minelights.effects.FrameStateDto;
import megabytesme.minelights.effects.KeyColorDto;
import megabytesme.minelights.effects.KeyNameStandardizer;
import megabytesme.minelights.effects.RGBColorDto;
import megabytesme.minelights.rgb.OpenRGBController;
import megabytesme.minelights.rgb.YeelightController;
import net.minecraft.client.MinecraftClient;
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

public class LightingManager implements Runnable {
    public static final Logger LOGGER = LogManager.getLogger("MineLights-LightingManager");

    private EffectPainter effectPainter;
    private final Gson gson = new Gson();
    private static final int FRAME_DURATION_MS = 33;
    private volatile boolean isInitialized = false;
    private FrameStateDto lastSentFrame = null;

    private final OpenRGBController openRgbController = new OpenRGBController();
    private final Map<Integer, Integer> openRgbLedToDeviceMap = new HashMap<>();

    private final YeelightController yeelightController = new YeelightController();
    private final Map<Integer, Integer> yeelightLedToDeviceMap = new HashMap<>();

    private final List<DeviceLayout> deviceLayouts = Collections.synchronizedList(new ArrayList<>());

    public LightingManager() {
    }

    private void performHandshakes() {
        final JsonObject[] serverDataContainer = new JsonObject[1];

        Thread serverHandshakeThread = new Thread(() -> {
            try (Socket clientSocket = new Socket()) {
                clientSocket.connect(new InetSocketAddress("127.0.0.1", 63211), 2000);
                MineLightsClient.isProxyConnected = true;

                DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream());
                DataInputStream dis = new DataInputStream(clientSocket.getInputStream());

                JsonObject configPayload = new JsonObject();
                JsonArray enabledIntegrations = new JsonArray();
                if (MineLightsClient.CONFIG.enableCorsair)
                    enabledIntegrations.add("Corsair");
                if (MineLightsClient.CONFIG.enableAsus)
                    enabledIntegrations.add("Asus");
                if (MineLightsClient.CONFIG.enableLogitech)
                    enabledIntegrations.add("Logitech");
                if (MineLightsClient.CONFIG.enableRazer)
                    enabledIntegrations.add("Razer");
                if (MineLightsClient.CONFIG.enableWooting)
                    enabledIntegrations.add("Wooting");
                if (MineLightsClient.CONFIG.enableSteelSeries)
                    enabledIntegrations.add("SteelSeries");
                if (MineLightsClient.CONFIG.enableMsi)
                    enabledIntegrations.add("Msi");
                if (MineLightsClient.CONFIG.enableNovation)
                    enabledIntegrations.add("Novation");
                if (MineLightsClient.CONFIG.enablePicoPi)
                    enabledIntegrations.add("PicoPi");

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
            if (!MineLightsClient.CONFIG.enableOpenRgb)
                return;
            openRgbController.connect();
        });
        openRgbThread.setName("MineLights-OpenRGB-Handshake");

        Thread yeelightThread = new Thread(() -> {
            if (!MineLightsClient.CONFIG.enableYeelight)
                return;
            if (yeelightController.discover()) {
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
            serverHandshakeThread.start();
            openRgbThread.start();
            yeelightThread.start();

            serverHandshakeThread.join(5000);
            openRgbThread.join(5000);
            yeelightThread.join(5000);

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
        for (OpenRGBController.OpenRGBDevice device : openRgbController.getDevices()) {
            String uniqueId = "OpenRGB|" + device.name;
            MineLightsClient.discoveredDevices.add(uniqueId);

            DeviceLayout layout = new DeviceLayout(device.name, "OPENRGB");
            Map<String, Integer> openRgbMap = device.keyMap;

            int openRgbLedOffset = 2000 + (openRgbDeviceIndex * 1000);

            for (Map.Entry<String, Integer> entry : openRgbMap.entrySet()) {
                String standardizedName = KeyNameStandardizer.standardize(entry.getKey());
                int globalLedId = openRgbLedOffset + entry.getValue();
                layout.addMapping(standardizedName, globalLedId);

                openRgbLedToDeviceMap.put(globalLedId, openRgbDeviceIndex);
            }
            deviceLayouts.add(layout);
            LOGGER.info("> Mapped device [OpenRGB]: {} ({} LEDs)", device.name, layout.getAllLeds().size());
            openRgbDeviceIndex++;
        }
    }

    @Override
    public void run() {
        performHandshakes();

        while (!isInitialized) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        try {
            while (!Thread.currentThread().isInterrupted()) {
                long frameStart = System.currentTimeMillis();

                MinecraftClient client = MinecraftClient.getInstance();
                PlayerDto playerState;
                if (client.player == null || !MineLightsClient.CONFIG.enableMod) {
                    playerState = new PlayerDto();
                    playerState.setInGame(false);
                } else {
                    playerState = PlayerDataCollector.getCurrentState(client);
                }

                FrameStateDto frameState = effectPainter.paint(playerState);

                if (frameState.equals(lastSentFrame)) {
                    long frameEnd = System.currentTimeMillis();
                    long sleepTime = FRAME_DURATION_MS - (frameEnd - frameStart);
                    if (sleepTime > 0)
                        Thread.sleep(sleepTime);
                    continue;
                }
                lastSentFrame = frameState;

                List<KeyColorDto> proxyUpdateList = new ArrayList<>();
                Map<Integer, List<KeyColorDto>> openRgbUpdates = new HashMap<>();

                for (Map.Entry<Integer, RGBColorDto> entry : frameState.keys.entrySet()) {
                    int globalLedId = entry.getKey();
                    RGBColorDto color = entry.getValue();

                    if (openRgbLedToDeviceMap.containsKey(globalLedId)) {
                        int deviceIndex = openRgbLedToDeviceMap.get(globalLedId);
                        openRgbUpdates.computeIfAbsent(deviceIndex, k -> new ArrayList<>())
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

                if (!openRgbUpdates.isEmpty()) {
                    for (Map.Entry<Integer, List<KeyColorDto>> deviceUpdate : openRgbUpdates.entrySet()) {
                        openRgbController.updateLeds(deviceUpdate.getKey(), deviceUpdate.getValue());
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
            openRgbController.disconnect();
            yeelightController.disconnect();
        }
    }
}