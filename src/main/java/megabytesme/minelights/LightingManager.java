package megabytesme.minelights;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import megabytesme.minelights.effects.EffectPainter;
import megabytesme.minelights.effects.FrameStateDto;
import megabytesme.minelights.effects.KeyColorDto;
import megabytesme.minelights.effects.RGBColorDto;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LightingManager implements Runnable {
    public static final Logger LOGGER = LoggerFactory.getLogger("MineLights");

    private EffectPainter effectPainter;
    private final Gson gson = new Gson();
    private static final int FRAME_DURATION_MS = 33;
    private static final int HANDSHAKE_PORT = 63211;
    private volatile boolean isInitialized = false;

    public LightingManager() {
        startHandshakeListener();
    }

    private void startHandshakeListener() {
        Thread handshakeThread = new Thread(() -> {
            LOGGER.info("Starting handshake listener on TCP port {}.", HANDSHAKE_PORT);
            try (ServerSocket serverSocket = new ServerSocket(HANDSHAKE_PORT)) {
                Socket clientSocket = serverSocket.accept();
                LOGGER.info("Handshake connection received from C++ proxy.");

                InputStream inputStream = clientSocket.getInputStream();
                InputStreamReader reader = new InputStreamReader(inputStream);
                JsonObject handshakeData = JsonParser.parseReader(reader).getAsJsonObject();

                List<Integer> allLedIds = new ArrayList<>();
                if (handshakeData.has("all_led_ids")) {
                    for (JsonElement id : handshakeData.getAsJsonArray("all_led_ids")) {
                        allLedIds.add(id.getAsInt());
                    }
                }

                Map<String, Integer> keyMap = new HashMap<>();
                if (handshakeData.has("key_map")) {
                    JsonObject mapObject = handshakeData.getAsJsonObject("key_map");
                    for (String key : mapObject.keySet()) {
                        keyMap.put(key, mapObject.get(key).getAsInt());
                    }
                }

                this.effectPainter = new EffectPainter(allLedIds, keyMap);
                this.isInitialized = true;
                LOGGER.info("Initialization complete. Found {} total LEDs and {} named keys.", allLedIds.size(),
                        keyMap.size());

                clientSocket.close();
            } catch (Exception e) {
                LOGGER.error("Handshake listener failed!", e);
            }
        });
        handshakeThread.setName("MineLights-Handshake-Listener");
        handshakeThread.setDaemon(true);
        handshakeThread.start();
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
                if (client.player == null) {
                    Thread.sleep(FRAME_DURATION_MS);
                    continue;
                }
                PlayerDto playerState = PlayerDataCollector.getCurrentState(client);

                FrameStateDto frameState = effectPainter.paint(playerState);

                List<KeyColorDto> colorsToSend = new ArrayList<>();
                for (Map.Entry<Integer, RGBColorDto> entry : frameState.keys.entrySet()) {
                    colorsToSend.add(new KeyColorDto(entry.getKey(), entry.getValue()));
                }

                JsonObject payload = new JsonObject();
                payload.add("led_colors", gson.toJsonTree(colorsToSend));
                String payloadJson = gson.toJson(payload);
                UDPClient.sendFrameData(payloadJson);

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
    }
}