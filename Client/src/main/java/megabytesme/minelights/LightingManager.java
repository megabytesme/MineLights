package megabytesme.minelights;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import megabytesme.minelights.effects.EffectPainter;
import megabytesme.minelights.effects.FrameStateDto;
import megabytesme.minelights.effects.KeyColorDto;
import megabytesme.minelights.effects.RGBColorDto;
import megabytesme.minelights.rgb.OpenRGBController;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.concurrent.atomic.AtomicInteger;

public class LightingManager implements Runnable {
    public static final Logger LOGGER = LoggerFactory.getLogger("MineLights-LM");

    private EffectPainter effectPainter;
    private final Gson gson = new Gson();
    private static final int FRAME_DURATION_MS = 33;
    private volatile boolean isInitialized = false;
    private FrameStateDto lastSentFrame = null;

    private final OpenRGBController openRgbController = new OpenRGBController();
    private final Map<Integer, Integer> openRgbLedToDeviceMap = new HashMap<>();

    private final List<Integer> masterLedList = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Integer> masterKeyMap = Collections.synchronizedMap(new HashMap<>());
    private final AtomicInteger proxyLedCount = new AtomicInteger(0);
    private final AtomicInteger openRgbLedCount = new AtomicInteger(0);

    public LightingManager() {
        performHandshakes();
    }

    private void performHandshakes() {
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
                    LOGGER.info("Successfully received handshake data of length: {}", jsonString.length());
                    JsonObject handshakeData = JsonParser.parseString(jsonString).getAsJsonObject();

                    parseHandshakeData(handshakeData);
                } else {
                    LOGGER.warn("Server sent a handshake with zero length. No devices loaded from proxy.");
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
            if (openRgbController.connect()) {
                List<OpenRGBController.OpenRGBDevice> devices = openRgbController.getDevices();
                int openRgbLedOffset = 2000;
                for (int i = 0; i < devices.size(); i++) {
                    OpenRGBController.OpenRGBDevice device = devices.get(i);
                    String uniqueId = "OpenRGB|" + device.name;
                    if (MineLightsClient.CONFIG.disabledDevices.contains(uniqueId))
                        continue;
                    MineLightsClient.discoveredDevices.add(uniqueId);
                    openRgbLedCount.addAndGet(device.ledCount);
                    for (int j = 0; j < device.ledCount; j++) {
                        int globalLedId = openRgbLedOffset + j;
                        masterLedList.add(globalLedId);
                        openRgbLedToDeviceMap.put(globalLedId, i);
                    }
                    openRgbLedOffset += device.ledCount;
                }
            }
        });
        openRgbThread.setName("MineLights-OpenRGB-Handshake");

        Thread initializerThread = new Thread(() -> {
            try {
                serverHandshakeThread.start();
                openRgbThread.start();
                serverHandshakeThread.join(5000);
                openRgbThread.join(5000);

                this.effectPainter = new EffectPainter(masterLedList, masterKeyMap);
                this.isInitialized = true;
                LOGGER.info(
                        "Initialization complete. Found {} LEDs from MineLights Server, and {} from OpenRGB. Total: {}",
                        proxyLedCount.get(), openRgbLedCount.get(), masterLedList.size());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        initializerThread.setName("MineLights-Initializer");
        initializerThread.start();
    }

    private void parseHandshakeData(JsonObject handshakeData) {
        if (handshakeData.has("devices")) {
            for (JsonElement deviceElement : handshakeData.getAsJsonArray("devices")) {
                JsonObject deviceObject = deviceElement.getAsJsonObject();
                String uniqueId = deviceObject.get("sdk").getAsString() + "|" + deviceObject.get("name").getAsString();
                MineLightsClient.discoveredDevices.add(uniqueId);
                if (deviceObject.has("leds")) {
                    for (JsonElement id : deviceObject.getAsJsonArray("leds")) {
                        masterLedList.add(id.getAsInt());
                        proxyLedCount.incrementAndGet();
                    }
                }
            }
        }
        if (handshakeData.has("key_map")) {
            JsonObject mapObject = handshakeData.getAsJsonObject("key_map");
            for (String key : mapObject.keySet()) {
                masterKeyMap.put(key, mapObject.get(key).getAsInt());
            }
        }
    }

    @Override
    public void run() {
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
        }
    }
}