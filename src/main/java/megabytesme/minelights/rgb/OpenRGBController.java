package megabytesme.minelights.rgb;

import megabytesme.minelights.effects.KeyColorDto;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class OpenRGBController {
    public static final Logger LOGGER = LogManager.getLogger("MineLights - OpenRGBController");

    private static final Map<String, String> KEY_NAME_MAP = new HashMap<>();
    static {
        KEY_NAME_MAP.put("`", "BACKTICK");
        KEY_NAME_MAP.put("-", "MINUS");
        KEY_NAME_MAP.put("=", "EQUALS");
        KEY_NAME_MAP.put("[", "LEFT_BRACKET");
        KEY_NAME_MAP.put("]", "RIGHT_BRACKET");
        KEY_NAME_MAP.put(";", "SEMICOLON");
        KEY_NAME_MAP.put("'", "APOSTROPHE");
        KEY_NAME_MAP.put("#", "HASH");
        KEY_NAME_MAP.put(",", "COMMA");
        KEY_NAME_MAP.put(".", "PERIOD");
        KEY_NAME_MAP.put("/", "SLASH");
        KEY_NAME_MAP.put("\\ (ISO)", "ISO_BACKSLASH");
        KEY_NAME_MAP.put("Space", "SPACE");

        KEY_NAME_MAP.put("Left Shift", "LSHIFT");
        KEY_NAME_MAP.put("Right Shift", "RSHIFT");
        KEY_NAME_MAP.put("Left Control", "LCTRL");
        KEY_NAME_MAP.put("Right Control", "RCTRL");
        KEY_NAME_MAP.put("Left Windows", "LWIN");
        KEY_NAME_MAP.put("Left Alt", "LALT");
        KEY_NAME_MAP.put("Right Alt", "RALT");
        KEY_NAME_MAP.put("Right Fn", "FN");
        KEY_NAME_MAP.put("Caps Lock", "CAPSLOCK");
        KEY_NAME_MAP.put("Num Lock", "NUMLOCK");
        KEY_NAME_MAP.put("Scroll Lock", "SCROLLLOCK");
        KEY_NAME_MAP.put("Print Screen", "PRINTSCREEN");
        KEY_NAME_MAP.put("Pause/Break", "PAUSE");

        KEY_NAME_MAP.put("Media Mute", "MEDIA_MUTE");
        KEY_NAME_MAP.put("Media Stop", "MEDIA_STOP");
        KEY_NAME_MAP.put("Media Previous", "MEDIA_PREVIOUS");
        KEY_NAME_MAP.put("Media Play/Pause", "MEDIA_PLAY_PAUSE");
        KEY_NAME_MAP.put("Media Next", "MEDIA_NEXT");

        KEY_NAME_MAP.put("Number Pad /", "NUMPAD_DIVIDE");
        KEY_NAME_MAP.put("Number Pad *", "NUMPAD_MULTIPLY");
        KEY_NAME_MAP.put("Number Pad -", "NUMPAD_SUBTRACT");
        KEY_NAME_MAP.put("Number Pad +", "NUMPAD_ADD");
        KEY_NAME_MAP.put("Number Pad Enter", "NUMPAD_ENTER");
        KEY_NAME_MAP.put("Number Pad .", "NUMPAD_DECIMAL");
        for (int i = 0; i <= 9; i++) {
            KEY_NAME_MAP.put("Number Pad " + i, "NUMPAD" + i);
        }
        
        KEY_NAME_MAP.put("Up Arrow", "UP");
        KEY_NAME_MAP.put("Down Arrow", "DOWN");
        KEY_NAME_MAP.put("Left Arrow", "LEFT");
        KEY_NAME_MAP.put("Right Arrow", "RIGHT");
    }

    private String processKeyName(String name) {
        if (name.startsWith("Key: ")) {
            name = name.substring(5);
        }
        if (KEY_NAME_MAP.containsKey(name)) {
            return KEY_NAME_MAP.get(name);
        }
        return name.toUpperCase().replaceAll("\\s+", "_");
    }

    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    private int serverProtocolVersion;

    public static class OpenRGBDevice {
        public String name;
        public int ledCount;
        public Map<String, Integer> keyMap = new HashMap<>();
    }

    private final List<OpenRGBDevice> devices = new ArrayList<>();

    public boolean connect() {
        try {
            socket = new Socket("127.0.0.1", 6742);
            out = new DataOutputStream(socket.getOutputStream());
            in = new DataInputStream(socket.getInputStream());

            String clientName = "MineLights";
            byte[] nameBytes = clientName.getBytes(StandardCharsets.US_ASCII);
            sendPacket(50, 0, ByteBuffer.allocate(nameBytes.length + 1).put(nameBytes).put((byte) 0).array());

            byte[] clientVersionData = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(5).array();
            sendPacket(40, 0, clientVersionData);
            this.serverProtocolVersion = readResponse().getInt();

            sendPacket(0, 0, new byte[0]);
            int deviceCount = readResponse().getInt();

            for (int i = 0; i < deviceCount; i++) {
                byte[] deviceRequestData = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                        .putInt(this.serverProtocolVersion).array();
                sendPacket(1, i, deviceRequestData);

                ByteBuffer response = readResponse();
                OpenRGBDevice device = parseDeviceData(response);
                devices.add(device);

                LOGGER.info("Discovered device #" + i + ": " + device.name);
                LOGGER.info("LED count: " + device.ledCount);
                if (device.keyMap.isEmpty()) {
                    LOGGER.info("No key mappings reported.");
                } else {
                    device.keyMap.forEach((key, index) ->
                        LOGGER.info("  LED " + index + " → \"" + key + "\"")
                    );
                }

                sendPacket(1100, i, new byte[0]);
            }
            return true;
        } catch (IOException e) {
            LOGGER.warn("Could not connect to OpenRGB server. Is it running?");
            e.printStackTrace();
            return false;
        }
    }

    private OpenRGBDevice parseDeviceData(ByteBuffer data) {
        OpenRGBDevice device = new OpenRGBDevice();
        data.getInt();
        data.getInt();

        device.name = readString(data);
        if (this.serverProtocolVersion >= 1) {
            readString(data);
        }
        readString(data);
        readString(data);
        readString(data);
        readString(data);

        int numModes = Short.toUnsignedInt(data.getShort());
        LOGGER.info("Modes: " + numModes);
        data.getInt();

        for (int i = 0; i < numModes; i++) {
            readString(data);
            data.getInt();
            data.getInt();
            data.getInt();
            data.getInt();

            if (this.serverProtocolVersion >= 3) {
                data.getInt();
                data.getInt();
            }

            data.getInt();
            data.getInt();
            data.getInt();

            if (this.serverProtocolVersion >= 3) {
                data.getInt();
            }

            data.getInt();
            data.getInt();

            int modeNumColors = Short.toUnsignedInt(data.getShort());
            if (modeNumColors > 0) {
                data.position(data.position() + (modeNumColors * 4));
            }
        }

        int numZones = Short.toUnsignedInt(data.getShort());
        LOGGER.info("Zones: " + numZones);
        int totalLeds = 0;
        for (int i = 0; i < numZones; i++) {
            readString(data);
            data.getInt();
            data.getInt();
            data.getInt();
            totalLeds += data.getInt();

            int matrixLen = Short.toUnsignedInt(data.getShort());
            if (matrixLen > 0) {
                data.position(data.position() + matrixLen);
            }

            if (this.serverProtocolVersion >= 4) {
                int numSegments = Short.toUnsignedInt(data.getShort());
                for (int j = 0; j < numSegments; j++) {
                    readString(data);
                    data.getInt();
                    data.getInt();
                    data.getInt();
                }
            }
            if (this.serverProtocolVersion >= 5) {
                data.getInt();
            }
        }

        int numLeds = Short.toUnsignedInt(data.getShort());
        LOGGER.info("[OpenRGB] LEDs reported: " + numLeds);
        for (int i = 0; i < numLeds; i++) {
            String ledName = readString(data);
            if (!ledName.isEmpty()) {
                String processedName = processKeyName(ledName);
                device.keyMap.put(processedName, i);
                LOGGER.info("  LED " + i + " name: \"" + ledName + "\" -> Mapped as: \"" + processedName + "\"");
            }
            data.getInt();
        }

        int numColors = Short.toUnsignedInt(data.getShort());
        if (numColors > 0) {
            data.position(data.position() + (numColors * 4));
        }

        if (this.serverProtocolVersion >= 5) {
            int numAltNames = Short.toUnsignedInt(data.getShort());
            for (int i = 0; i < numAltNames; i++) {
                readString(data);
            }
            data.getInt();
        }

        device.ledCount = totalLeds;
        return device;
    }

    private String readString(ByteBuffer buffer) {
        if (buffer.remaining() < 2)
            return "";
        int len = Short.toUnsignedInt(buffer.getShort());
        if (len == 0 || buffer.remaining() < len)
            return "";
        byte[] strBytes = new byte[len];
        buffer.get(strBytes, 0, len);
        return new String(strBytes, 0, len - 1, StandardCharsets.US_ASCII);
    }

    public List<OpenRGBDevice> getDevices() {
        return devices;
    }

    public void updateLeds(int deviceId, List<KeyColorDto> colorsToUpdate) {
        if (socket == null || socket.isClosed() || colorsToUpdate.isEmpty()) {
            return;
        }

        OpenRGBDevice device = devices.get(deviceId);
        int numLeds = device.ledCount;
        if (numLeds == 0) {
            return;
        }

        ByteBuffer innerPayload = ByteBuffer.allocate(2 + (numLeds * 4)).order(ByteOrder.LITTLE_ENDIAN);
        innerPayload.putShort((short) numLeds);

        byte[][] colorArray = new byte[numLeds][4];
        for (KeyColorDto dto : colorsToUpdate) {
            int localLedId = dto.id - getLedOffsetForDevice(deviceId);
            if (localLedId >= 0 && localLedId < numLeds) {
                colorArray[localLedId][0] = (byte) dto.r;
                colorArray[localLedId][1] = (byte) dto.g;
                colorArray[localLedId][2] = (byte) dto.b;
            }
        }
        for (int i = 0; i < numLeds; i++) {
            innerPayload.put(colorArray[i]);
        }

        int payloadSize = innerPayload.capacity() + 4;
        ByteBuffer fullPayload = ByteBuffer.allocate(payloadSize).order(ByteOrder.LITTLE_ENDIAN);
        fullPayload.putInt(payloadSize);
        fullPayload.put(innerPayload.array());

        try {
            sendPacket(1050, deviceId, fullPayload.array());
        } catch (IOException e) {
            System.err
                    .println("Failed to send LED update to OpenRGB device " + deviceId + ". Connection may be closed.");
            e.printStackTrace();
        }
    }

    public void disconnect() {
        try {
            if (socket != null)
                socket.close();
        } catch (IOException e) {
        }
    }

    private void sendPacket(int command, int deviceId, byte[] data) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(16);
        header.order(ByteOrder.LITTLE_ENDIAN);
        header.put("ORGB".getBytes(StandardCharsets.US_ASCII));
        header.putInt(deviceId);
        header.putInt(command);
        header.putInt(data.length);
        out.write(header.array());
        out.write(data);
        out.flush();
    }

    private ByteBuffer readResponse() throws IOException {
        byte[] headerBytes = new byte[16];
        in.readFully(headerBytes);
        ByteBuffer header = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN);
        header.position(12);
        int dataSize = header.getInt();

        byte[] dataBytes = new byte[dataSize];
        if (dataSize > 0) {
            in.readFully(dataBytes);
        }
        return ByteBuffer.wrap(dataBytes).order(ByteOrder.LITTLE_ENDIAN);
    }

    private int getLedOffsetForDevice(int deviceIndex) {
        int offset = 2000;
        for (int i = 0; i < deviceIndex; i++) {
            offset += devices.get(i).ledCount;
        }
        return offset;
    }

    public Map<String, Integer> getGlobalKeyMap() {
        Map<String, Integer> globalKeyMap = new HashMap<>();
        for (int i = 0; i < devices.size(); i++) {
            OpenRGBDevice device = devices.get(i);
            int globalOffset = getLedOffsetForDevice(i);

            for (Map.Entry<String, Integer> entry : device.keyMap.entrySet()) {
                String keyName = entry.getKey();
                int localLedIndex = entry.getValue();
                int globalLedId = globalOffset + localLedIndex;
                globalKeyMap.put(keyName, globalLedId);
            }
        }
        return globalKeyMap;
    }
}