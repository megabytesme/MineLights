package megabytesme.minelights;

import com.google.gson.Gson;
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
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class LightingManager implements Runnable {
    public static final Logger LOGGER = LoggerFactory.getLogger("MineLights");

    private EffectPainter effectPainter;
    private final Gson gson = new Gson();
    private static final int FRAME_DURATION_MS = 33;
    private static final int HANDSHAKE_PORT = 63211;
    private volatile boolean isInitialized = false;

    private final OpenRGBController openRgbController = new OpenRGBController();
    private final Map<Integer, Integer> openRgbLedToDeviceMap = new HashMap<>();

    private final List<Integer> masterLedList = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Integer> masterKeyMap = Collections.synchronizedMap(new HashMap<>());

    public LightingManager() {
        performHandshakes();
    }

    private void performHandshakes() {
        CountDownLatch latch = new CountDownLatch(1);

        Thread openRgbThread = new Thread(() -> {
            try {
                if (openRgbController.connect()) {
                    LOGGER.info("Successfully connected to OpenRGB server.");
                    List<OpenRGBController.OpenRGBDevice> devices = openRgbController.getDevices();
                    int openRgbLedOffset = 2000;
                    for (int i = 0; i < devices.size(); i++) {
                        OpenRGBController.OpenRGBDevice device = devices.get(i);
                        LOGGER.info("Found OpenRGB device: {} with {} LEDs", device.name, device.ledCount);

                        for (int j = 0; j < device.ledCount; j++) {
                            int globalLedId = openRgbLedOffset + j;
                            masterLedList.add(globalLedId);
                            openRgbLedToDeviceMap.put(globalLedId, i);
                        }
                        openRgbLedOffset += device.ledCount;
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error during OpenRGB handshake", e);
            } finally {
                latch.countDown();
            }
        });
        openRgbThread.setName("MineLights-OpenRGB-Handshake");
        openRgbThread.start();

        Thread MineLightsProxyThread = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(HANDSHAKE_PORT)) {
                serverSocket.setSoTimeout(10000);
                Socket clientSocket = serverSocket.accept();
                LOGGER.info("Handshake connection received from MineLights proxy.");

                InputStreamReader reader = new InputStreamReader(clientSocket.getInputStream());
                JsonObject handshakeData = JsonParser.parseReader(reader).getAsJsonObject();

                if (handshakeData.has("all_led_ids")) {
                    for (JsonElement id : handshakeData.getAsJsonArray("all_led_ids")) {
                        masterLedList.add(id.getAsInt());
                    }
                }
                if (handshakeData.has("key_map")) {
                    JsonObject mapObject = handshakeData.getAsJsonObject("key_map");
                    for (String key : mapObject.keySet()) {
                        masterKeyMap.put(key, mapObject.get(key).getAsInt());
                    }
                }
                clientSocket.close();
                LOGGER.info("MineLights Proxy devices have been added to the lighting system.");
            } catch (Exception e) {
                LOGGER.warn("Did not receive handshake from MineLights Proxy (is it running?). Continuing without it.");
            }
        });
        MineLightsProxyThread.setName("MineLights-Proxy-Handshake");
        MineLightsProxyThread.start();

        new Thread(() -> {
            try {
                latch.await();
                this.effectPainter = new EffectPainter(masterLedList, masterKeyMap);
                this.isInitialized = true;
                LOGGER.info("Initialization complete. Total controllable LEDs found: {}", masterLedList.size());
            } catch (InterruptedException e) {
                LOGGER.error("Initialization was interrupted.");
            }
        }).start();
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
                if (client.player == null) {
                    playerState = new PlayerDto();
                    playerState.setInGame(false);
                } else {
                    playerState = PlayerDataCollector.getCurrentState(client);
                }

                FrameStateDto frameState = effectPainter.paint(playerState);

                List<KeyColorDto> iCueUpdateList = new ArrayList<>();
                Map<Integer, List<KeyColorDto>> openRgbUpdates = new HashMap<>();

                for (Map.Entry<Integer, RGBColorDto> entry : frameState.keys.entrySet()) {
                    int globalLedId = entry.getKey();
                    RGBColorDto color = entry.getValue();

                    if (openRgbLedToDeviceMap.containsKey(globalLedId)) {
                        int deviceIndex = openRgbLedToDeviceMap.get(globalLedId);
                        openRgbUpdates.computeIfAbsent(deviceIndex, k -> new ArrayList<>())
                                .add(new KeyColorDto(globalLedId, color));
                    } else {
                        iCueUpdateList.add(new KeyColorDto(globalLedId, color));
                    }
                }

                if (!iCueUpdateList.isEmpty()) {
                    JsonObject iCuePayload = new JsonObject();
                    iCuePayload.add("led_colors", gson.toJsonTree(iCueUpdateList));
                    UDPClient.sendFrameData(gson.toJson(iCuePayload));
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