package megabytesme.minelights.rgb;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import megabytesme.minelights.effects.KeyColorDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AuraSdkController {
    private static final Logger LOGGER = LoggerFactory.getLogger("AuraSDK-Controller");
    private static final String BASE_URI = "http://127.0.0.1:27339/AuraSDK";
    public static final int KEYCODE_OFFSET = 5000;
    public static final int GRID_OFFSET = 6000;
    private static final Map<String, Integer> FRIENDLY_NAME_TO_KEYCODE_MAP = createKeyMap();

    private final HttpClient httpClient;
    private final Gson gson;

    private final Map<String, AuraDevice> discoveredDevices = new HashMap<>();
    private final Map<Integer, MappedAuraLed> ledIdToAuraLedMap = new HashMap<>();
    private String keyboardDeviceName = null;
    private boolean isInitialized = false;

    public static class AuraDeviceInfo {
        public String name;
        public int ledCount;
        public List<Integer> ledIds = new ArrayList<>();
    }

    private static class AuraDevice {
        String name;
        int count;
        int width;
        int height;
    }

    private static class AuraDeviceAdapter extends TypeAdapter<AuraDevice> {
        @Override
        public void write(JsonWriter out, AuraDevice value) throws IOException {
            out.beginObject();
            out.endObject();
        }

        @Override
        public AuraDevice read(JsonReader in) throws IOException {
            AuraDevice device = new AuraDevice();
            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                switch (name) {
                    case "count":
                        device.count = Integer.parseInt(in.nextString());
                        break;
                    case "width":
                        device.width = Integer.parseInt(in.nextString());
                        break;
                    case "height":
                        device.height = Integer.parseInt(in.nextString());
                        break;
                    default:
                        in.skipValue();
                        break;
                }
            }
            in.endObject();
            return device;
        }
    }

    private static class MappedAuraLed {
        String deviceName;
        int x, y;

        MappedAuraLed(String deviceName, int x, int y) {
            this.deviceName = deviceName;
            this.x = x;
            this.y = y;
        }
    }

    private static Map<String, Integer> createKeyMap() {
        Map<String, Integer> map = new HashMap<>();
        map.put("ESCAPE", 1);
        map.put("1", 2);
        map.put("2", 3);
        map.put("3", 4);
        map.put("4", 5);
        map.put("5", 6);
        map.put("6", 7);
        map.put("7", 8);
        map.put("8", 9);
        map.put("9", 10);
        map.put("0", 11);
        map.put("MINUS", 12);
        map.put("EQUALS", 13);
        map.put("BACKSPACE", 14);
        map.put("TAB", 15);
        map.put("Q", 16);
        map.put("W", 17);
        map.put("E", 18);
        map.put("R", 19);
        map.put("T", 20);
        map.put("Y", 21);
        map.put("U", 22);
        map.put("I", 23);
        map.put("O", 24);
        map.put("P", 25);
        map.put("LBRACKET", 26);
        map.put("RBRACKET", 27);
        map.put("ENTER", 28);
        map.put("LCTRL", 29);
        map.put("A", 30);
        map.put("S", 31);
        map.put("D", 32);
        map.put("F", 33);
        map.put("G", 34);
        map.put("H", 35);
        map.put("J", 36);
        map.put("K", 37);
        map.put("L", 38);
        map.put("SEMICOLON", 39);
        map.put("APOSTROPHE", 40);
        map.put("GRAVE_ACCENT", 41);
        map.put("LSHIFT", 42);
        map.put("BACKSLASH", 43);
        map.put("Z", 44);
        map.put("X", 45);
        map.put("C", 46);
        map.put("V", 47);
        map.put("B", 48);
        map.put("N", 49);
        map.put("M", 50);
        map.put("COMMA", 51);
        map.put("PERIOD", 52);
        map.put("SLASH", 53);
        map.put("RSHIFT", 54);
        map.put("LALT", 56);
        map.put("SPACE", 57);
        map.put("CAPS_LOCK", 58);
        map.put("F1", 59);
        map.put("F2", 60);
        map.put("F3", 61);
        map.put("F4", 62);
        map.put("F5", 63);
        map.put("F6", 64);
        map.put("F7", 65);
        map.put("F8", 66);
        map.put("F9", 67);
        map.put("F10", 68);
        map.put("F11", 87);
        map.put("F12", 88);
        map.put("RCTRL", 157);
        map.put("RALT", 184);
        map.put("UP", 200);
        map.put("DOWN", 208);
        map.put("LEFT", 203);
        map.put("RIGHT", 205);
        map.put("INSERT", 210);
        map.put("DELETE", 211);
        map.put("HOME", 199);
        map.put("END", 207);
        map.put("PAGE_UP", 201);
        map.put("PAGE_DOWN", 209);
        map.put("LWIN", 219);
        return Map.copyOf(map);
    }

    public AuraSdkController() {
        this.httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        this.gson = new GsonBuilder()
                .registerTypeAdapter(AuraDevice.class, new AuraDeviceAdapter())
                .create();
    }

    public Map<String, Integer> getNamedKeyMap() {
        return FRIENDLY_NAME_TO_KEYCODE_MAP.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue() + KEYCODE_OFFSET));
    }

    public boolean connect() {
        if (!initializeSession()) {
            return false;
        }
        isInitialized = true;
        return true;
    }

    public List<AuraDeviceInfo> discoverDevicesAndGetInfo() {
        if (!isInitialized)
            return new ArrayList<>();

        List<AuraDeviceInfo> discoveredList = new ArrayList<>();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URI + "/AuraDevice")).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
                if (!"0".equals(jsonResponse.get("result").getAsString())) {
                    return discoveredList;
                }

                int currentGridLedId = GRID_OFFSET;

                for (Map.Entry<String, JsonElement> entry : jsonResponse.entrySet()) {
                    String deviceKey = entry.getKey();
                    if (deviceKey.equals("result")) {
                        continue;
                    }

                    AuraDevice dev = gson.fromJson(entry.getValue(), AuraDevice.class);
                    dev.name = deviceKey;
                    discoveredDevices.put(dev.name, dev);

                    AuraDeviceInfo info = new AuraDeviceInfo();
                    info.name = dev.name;
                    info.ledCount = dev.count;

                    if (dev.name.contains("Keyboard")) {
                        if (this.keyboardDeviceName == null)
                            this.keyboardDeviceName = dev.name;
                    } else if (dev.width > 0 && dev.height > 0) {
                        for (int y = 0; y < dev.height; y++) {
                            for (int x = 0; x < dev.width; x++) {
                                int ledIndex = y * dev.width + x;
                                if (ledIndex >= dev.count)
                                    break;
                                int globalId = currentGridLedId++;
                                ledIdToAuraLedMap.put(globalId, new MappedAuraLed(dev.name, x, y));
                                info.ledIds.add(globalId);
                            }
                        }
                    } else if (dev.count > 0) {
                        int globalId = currentGridLedId++;
                        ledIdToAuraLedMap.put(globalId, new MappedAuraLed(dev.name, 0, 0));
                        info.ledIds.add(globalId);
                    }
                    if (info.ledCount > 0) {
                        discoveredList.add(info);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("An error occurred during Aura device discovery.", e);
        }
        return discoveredList;
    }

    private boolean initializeSession() {
        try {
            String payload = gson.toJson(Map.of("category", "SDK"));
            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URI))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .header("Content-Type", "application/json")
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
                if ("0".equals(jsonResponse.get("result").getAsString())) {
                    return true;
                }
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.error("Could not connect to Aura SDK service. Is it running? Error: {}", e.getMessage());
        }
        return false;
    }

    public void updateLeds(Map<Integer, KeyColorDto> ledUpdates) {
        if (!isInitialized || ledUpdates.isEmpty())
            return;

        List<Map<String, Object>> setData = new ArrayList<>();
        Map<Integer, List<String>> keycodeUpdates = new HashMap<>();

        for (KeyColorDto colorDto : ledUpdates.values()) {
            if (ledIdToAuraLedMap.containsKey(colorDto.id)) {
                MappedAuraLed led = ledIdToAuraLedMap.get(colorDto.id);
                int bgr = (colorDto.b << 16) | (colorDto.g << 8) | colorDto.r;

                Map<String, Object> ledUpdate = new HashMap<>();
                ledUpdate.put("device", led.deviceName);
                if (discoveredDevices.get(led.deviceName).width > 0) {
                    ledUpdate.put("range", "custom");
                    ledUpdate.put("x", String.valueOf(led.x));
                    ledUpdate.put("y", String.valueOf(led.y));
                } else {
                    ledUpdate.put("range", "all");
                }
                ledUpdate.put("color", String.valueOf(bgr));
                ledUpdate.put("apply", "false");
                setData.add(ledUpdate);
            } else if (keyboardDeviceName != null) {
                int bgr = (colorDto.b << 16) | (colorDto.g << 8) | colorDto.r;
                keycodeUpdates.computeIfAbsent(bgr, k -> new ArrayList<>())
                        .add(String.valueOf(colorDto.id - KEYCODE_OFFSET));
            }
        }

        for (Map.Entry<Integer, List<String>> entry : keycodeUpdates.entrySet()) {
            Map<String, Object> keyUpdate = new HashMap<>();
            keyUpdate.put("device", keyboardDeviceName);
            keyUpdate.put("range", "custom");
            keyUpdate.put("keycode", entry.getValue());
            keyUpdate.put("color", String.valueOf(entry.getKey()));
            keyUpdate.put("apply", "false");
            setData.add(keyUpdate);
        }

        if (!setData.isEmpty()) {
            sendPutRequest("/AuraDevice", Map.of("data", setData));
        }

        List<Map<String, Object>> flushData = discoveredDevices.keySet().stream()
                .map(deviceName -> Map.<String, Object>of("device", deviceName, "apply", "true"))
                .collect(Collectors.toList());

        if (!flushData.isEmpty()) {
            sendPutRequest("/AuraDevice", Map.of("data", flushData));
        }
    }

    private void sendPutRequest(String path, Map<String, Object> body) {
        try {
            String payload = gson.toJson(body);
            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URI + path))
                    .PUT(HttpRequest.BodyPublishers.ofString(payload))
                    .header("Content-Type", "application/json")
                    .build();
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
        }
    }

    public void disconnect() {
        if (!isInitialized)
            return;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URI)).DELETE().build();
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            isInitialized = false;
        } catch (IOException | InterruptedException e) {
        }
    }
}