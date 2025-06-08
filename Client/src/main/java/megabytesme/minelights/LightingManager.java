package megabytesme.minelights;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.gitlab.mguimard.openrgb.entity.OpenRGBDevice;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class LightingManager implements Runnable {
    public static final Logger LOGGER = LoggerFactory.getLogger("MineLights");

    private EffectPainter effectPainter;
    private final Gson gson = new Gson();
    private static final int FRAME_DURATION_MS = 33;
    private static final int HANDSHAKE_PORT = 63211;
    private volatile boolean isInitialized = false;

    private final OpenRGBController openRgbController = new OpenRGBController();
    private final Map<Integer, Integer> openRgbLedToDeviceMap = new HashMap<>();
    private final List<KeyColorDto> iCueUpdateList = new ArrayList<>();

    public LightingManager() {
        CompletableFuture.runAsync(this::performHandshakes);
    }

    private void performHandshakes() {
        LOGGER.info("Starting all controller handshakes...");
        List<Integer> masterLedList = new ArrayList<>();
        Map<String, Integer> masterKeyMap = new HashMap<>();

        if (openRgbController.connect()) {
            LOGGER.info("Successfully connected to OpenRGB server.");
            List<OpenRGBDevice> devices = openRgbController.getDevices();
            int openRgbLedOffset = 2000;
            for (int i = 0; i < devices.size(); i++) {
                OpenRGBDevice device = devices.get(i);
                LOGGER.info("Found OpenRGB device: {}", device.getName());
                for (int j = 0; j < device.getLeds().size(); j++) {
                    int globalLedId = openRgbLedOffset + j;
                    masterLedList.add(globalLedId);
                    openRgbLedToDeviceMap.put(globalLedId, i);
                }
                openRgbLedOffset += device.getLeds().size();
            }
        }

        try (ServerSocket serverSocket = new ServerSocket(HANDSHAKE_PORT)) {
            Socket clientSocket = serverSocket.accept();
            LOGGER.info("Handshake connection received from C++ proxy.");

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
        } catch (Exception e) {
            LOGGER.error("iCUE Proxy handshake failed!", e);
        }

        this.effectPainter = new EffectPainter(masterLedList, masterKeyMap);
        this.isInitialized = true;
        LOGGER.info("Initialization complete. Total controllable LEDs: {}", masterLedList.size());
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

                iCueUpdateList.clear();
                Map<Integer, List<KeyColorDto>> openRgbUpdates = new HashMap<>();

                for (Map.Entry<Integer, RGBColorDto> entry : frameState.keys.entrySet()) {
                    int globalLedId = entry.getKey();
                    RGBColorDto color = entry.getValue();
                    KeyColorDto keyColor = new KeyColorDto(globalLedId, color);

                    if (openRgbLedToDeviceMap.containsKey(globalLedId)) {
                        int deviceIndex = openRgbLedToDeviceMap.get(globalLedId);
                        openRgbUpdates.computeIfAbsent(deviceIndex, k -> new ArrayList<>()).add(keyColor);
                    } else {
                        iCueUpdateList.add(keyColor);
                    }
                }

                if (!iCueUpdateList.isEmpty()) {
                    JsonObject iCuePayload = new JsonObject();
                    iCuePayload.add("led_colors", gson.toJsonTree(iCueUpdateList));
                    UDPClient.sendFrameData(gson.toJson(iCuePayload));
                }

                if (!openRgbUpdates.isEmpty()) {
                    openRgbController.updateLeds(openRgbUpdates);
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