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

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
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
    public static final Logger LOGGER = LoggerFactory.getLogger("MineLights");

    private EffectPainter effectPainter;
    private final Gson gson = new Gson();
    private static final int FRAME_DURATION_MS = 33;
    private volatile boolean isInitialized = false;
    private FrameStateDto lastSentFrame = null;

    private final OpenRGBController openRgbController = new OpenRGBController();
    private final Map<Integer, Integer> openRgbLedToDeviceMap = new HashMap<>();

    private final List<Integer> masterLedList = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Integer> masterKeyMap = Collections.synchronizedMap(new HashMap<>());

    private final AtomicInteger openRgbLedCount = new AtomicInteger(0);
    private final AtomicInteger proxyLedCount = new AtomicInteger(0);

    public LightingManager() {
        performHandshakes();
    }

    private void performHandshakes() {
        Thread openRgbThread = new Thread(() -> {
            if (!MineLightsClient.CONFIG.enableOpenRgb) {
                LOGGER.info("OpenRGB integration is disabled in the config.");
                return;
            }
            try {
                if (openRgbController.connect()) {
                    LOGGER.info("Successfully connected to OpenRGB server.");
                    List<OpenRGBController.OpenRGBDevice> devices = openRgbController.getDevices();
                    int openRgbLedOffset = 2000;
                    for (int i = 0; i < devices.size(); i++) {
                        OpenRGBController.OpenRGBDevice device = devices.get(i);
                        String uniqueId = "OpenRGB|" + device.name;

                        if (MineLightsClient.CONFIG.disabledDevices.contains(uniqueId)) {
                            LOGGER.info("Skipping disabled OpenRGB device: {}", device.name);
                            continue;
                        }

                        MineLightsClient.discoveredDevices.add(uniqueId);
                        LOGGER.info("Found OpenRGB device: {} with {} LEDs", device.name, device.ledCount);
                        openRgbLedCount.addAndGet(device.ledCount);

                        for (int j = 0; j < device.ledCount; j++) {
                            int globalLedId = openRgbLedOffset + j;
                            masterLedList.add(globalLedId);
                            openRgbLedToDeviceMap.put(globalLedId, i);
                        }
                        openRgbLedOffset += device.ledCount;
                    }
                }
            } catch (Exception e) {
                if (!Thread.currentThread().isInterrupted()) {
                    LOGGER.error("Error during OpenRGB handshake", e);
                }
            }
        });
        openRgbThread.setName("MineLights-OpenRGB-Handshake");

        Thread mineLightsProxyThread = new Thread(() -> {
            if (!MineLightsClient.IS_WINDOWS)
                return;
            if (!MineLightsClient.CONFIG.enableIcueProxy && !MineLightsClient.CONFIG.enableMysticLightProxy) {
                LOGGER.info("All Windows-based proxy integrations are disabled in the config.");
                return;
            }

            try (Socket clientSocket = new Socket()) {
                clientSocket.connect(new InetSocketAddress("127.0.0.1", 63211), 2000);
                LOGGER.info("Successfully connected to MineLights Proxy.");
                MineLightsClient.isProxyConnected = true;

                OutputStreamWriter writer = new OutputStreamWriter(clientSocket.getOutputStream(),
                        StandardCharsets.UTF_8);
                JsonObject configPayload = new JsonObject();
                JsonArray enabledIntegrations = new JsonArray();
                if (MineLightsClient.CONFIG.enableIcueProxy)
                    enabledIntegrations.add("iCUE");
                if (MineLightsClient.CONFIG.enableMysticLightProxy)
                    enabledIntegrations.add("MysticLight");

                configPayload.add("enabled_integrations", enabledIntegrations);
                configPayload.add("disabled_devices", new Gson().toJsonTree(MineLightsClient.CONFIG.disabledDevices));
                writer.write(configPayload.toString());
                writer.flush();
                clientSocket.shutdownOutput();

                InputStreamReader reader = new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8);
                JsonObject handshakeData = JsonParser.parseReader(reader).getAsJsonObject();

                if (handshakeData.has("devices")) {
                    JsonArray devicesArray = handshakeData.getAsJsonArray("devices");
                    for (JsonElement deviceElement : devicesArray) {
                        JsonObject deviceObject = deviceElement.getAsJsonObject();
                        String sdk = deviceObject.get("sdk").getAsString();
                        String name = deviceObject.get("name").getAsString();
                        int ledCount = deviceObject.get("ledCount").getAsInt();
                        String uniqueId = sdk + "|" + name;
                        MineLightsClient.discoveredDevices.add(uniqueId);

                        LOGGER.info("Found {} device: {} with {} LEDs", sdk, name, ledCount);

                        if (deviceObject.has("leds")) {
                            JsonArray ledsArray = deviceObject.getAsJsonArray("leds");
                            for (JsonElement id : ledsArray) {
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

                if (proxyLedCount.get() > 0) {
                    LOGGER.info("MineLights Proxy devices successfully added to the lighting system.");
                }

            } catch (Exception e) {
                LOGGER.warn(
                        "Could not connect to MineLights Proxy (is it running and has it been run as an administrator?).");
            }
        });
        mineLightsProxyThread.setName("MineLights-Proxy-Handshake");

        Thread initializerThread = new Thread(() -> {
            try {
                openRgbThread.start();
                mineLightsProxyThread.start();
                openRgbThread.join(5000);
                mineLightsProxyThread.join(5000);

                this.effectPainter = new EffectPainter(masterLedList, masterKeyMap);
                this.isInitialized = true;

                LOGGER.info(
                        "Initialization complete. Found {} LEDs from OpenRGB and {} LEDs from MineLights Proxy. Total controllable LEDs: {}",
                        openRgbLedCount.get(),
                        proxyLedCount.get(),
                        masterLedList.size());

            } catch (InterruptedException e) {
                LOGGER.error("Initialization process was interrupted.");
                Thread.currentThread().interrupt();
            }
        });
        initializerThread.setName("MineLights-Initializer");
        initializerThread.start();
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
                    if (sleepTime > 0) {
                        Thread.sleep(sleepTime);
                    }
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
                if (sleepTime > 0) {
                    Thread.sleep(sleepTime);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            e.printStackTrace();
        }
        openRgbController.disconnect();
    }
}