package megabytesme.minelights.rgb;

import megabytesme.minelights.effects.KeyColorDto;
import megabytesme.minelights.effects.KeyNameStandardizer;

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
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class OpenRGBController {
    public static final Logger LOGGER = LogManager.getLogger("MineLights - OpenRGBController");
    private static final int MAX_CLIENT_PROTOCOL_VERSION = 5;
    private static final int MAX_DEVICE_COUNT = 4096;
    private static final int MAX_RESPONSE_DATA_SIZE = 64 * 1024 * 1024;
    private static final int MODE_COLORS_PER_LED = 1;

    private String processKeyName(String name) {
        return KeyNameStandardizer.standardize(name);
    }

    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    private int serverProtocolVersion;
    private String endpointAddress = "127.0.0.1:6742";

    public static class OpenRGBDevice {
        public String name;
        public String endpointAddress;
        public int controllerId;
        public int ledCount;
        public Map<String, Integer> keyMap = new HashMap<>();
        public List<String> ledNames = new ArrayList<>();
        public int originalModeIndex = -1;
        public byte[] originalModeData;
        public boolean originalModeUsesPerLedColors;
        public byte[] originalColors;
    }

    private final List<OpenRGBDevice> devices = new ArrayList<>();

    public boolean connect() {
        return connect("127.0.0.1", 6742);
    }

    public boolean connect(String host, int port) {
        try {
            if (host == null || host.trim().isEmpty() || port < 1 || port > 65535) {
                LOGGER.warn("Ignoring invalid OpenRGB endpoint.");
                return false;
            }
            endpointAddress = host + ":" + port;
            socket = new Socket();
            socket.connect(new java.net.InetSocketAddress(host, port), 2000);
            socket.setSoTimeout(5000);
            out = new DataOutputStream(socket.getOutputStream());
            in = new DataInputStream(socket.getInputStream());

            String clientName = "MineLights";
            byte[] nameBytes = clientName.getBytes(StandardCharsets.US_ASCII);
            sendPacket(50, 0, ByteBuffer.allocate(nameBytes.length + 1).put(nameBytes).put((byte) 0).array());

            byte[] clientVersionData = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(5).array();
            sendPacket(40, 0, clientVersionData);
            int serverMaxProtocolVersion = readResponse().getInt();
            this.serverProtocolVersion = Math.min(serverMaxProtocolVersion, MAX_CLIENT_PROTOCOL_VERSION);
            if (serverMaxProtocolVersion > this.serverProtocolVersion) {
                LOGGER.info("OpenRGB server protocol " + serverMaxProtocolVersion
                        + " is newer than MineLights supports; using compatible protocol "
                        + this.serverProtocolVersion + ".");
            }

            sendPacket(0, 0, new byte[0]);
            ByteBuffer controllerList = readResponse();
            int deviceCount = controllerList.getInt();
            if (deviceCount < 0 || deviceCount > MAX_DEVICE_COUNT) {
                throw new IOException("OpenRGB returned an invalid device count: " + deviceCount);
            }
            int[] controllerIds = new int[deviceCount];
            boolean hasControllerIds = serverMaxProtocolVersion >= 6
                    && controllerList.remaining() >= deviceCount * Integer.BYTES;
            for (int i = 0; i < deviceCount; i++) {
                controllerIds[i] = hasControllerIds ? controllerList.getInt() : i;
            }

            for (int i = 0; i < deviceCount; i++) {
                int controllerId = controllerIds[i];
                byte[] deviceRequestData = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                        .putInt(this.serverProtocolVersion).array();
                sendPacket(1, controllerId, deviceRequestData);

                ByteBuffer response = readResponse();
                OpenRGBDevice device = parseDeviceData(response);
                device.controllerId = controllerId;
                device.endpointAddress = endpointAddress;
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

            }
            return true;
        } catch (IOException e) {
            LOGGER.warn("Could not connect to OpenRGB endpoint {}:{}.", host, port);
            disconnect();
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
        int activeMode = data.getInt();

        for (int i = 0; i < numModes; i++) {
            int modeStart = data.position();
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
            int colorMode = data.getInt();

            int modeNumColors = Short.toUnsignedInt(data.getShort());
            if (modeNumColors > 0) {
                data.position(data.position() + (modeNumColors * 4));
            }

            if (i == activeMode) {
                device.originalModeIndex = i;
                device.originalModeData = new byte[data.position() - modeStart];
                ByteBuffer modeBytes = data.duplicate();
                modeBytes.position(modeStart);
                modeBytes.get(device.originalModeData);
                device.originalModeUsesPerLedColors = colorMode == MODE_COLORS_PER_LED;
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
            device.ledNames.add(ledName);
            if (!ledName.isEmpty()) {
                String processedName = processKeyName(ledName);
                device.keyMap.put(processedName, i);
                LOGGER.info("  LED " + i + " name: \"" + ledName + "\" -> Mapped as: \"" + processedName + "\"");
            }
            data.getInt();
        }

        int numColors = Short.toUnsignedInt(data.getShort());
        device.originalColors = new byte[numColors * 4];
        data.get(device.originalColors);

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
        updateLeds(deviceId, colorsToUpdate, getLedOffsetForDevice(deviceId));
    }

    public void updateLeds(int deviceId, List<KeyColorDto> colorsToUpdate, int globalLedOffset) {
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

        /**
         * Fallback for RGB devices where the calculated LocalLedId is invalid or
         * the device does not expose per-key LED mappings (e.g. zone-based keyboards
         * like SteelSeries Apex 3).
         *
         * Without this fallback, LEDs that are not explicitly mapped receive (0,0,0)
         * and the device turns partially or fully black.
         *
         * This distributes the available colors cyclically across all LEDs so that
         * zone-based devices still receive a valid color update.
         */
        {
            int colorId = 0;
            for(int ledId=0;ledId<numLeds;++ledId){
                KeyColorDto color = colorsToUpdate.get(colorId);

                colorArray[ledId][0] = (byte) color.r;
                colorArray[ledId][1] = (byte) color.g;
                colorArray[ledId][2] = (byte) color.b;
                colorArray[ledId][3] = (byte) 255;

                colorId = (colorId + 1) % colorsToUpdate.size();
            }
        }


        for (KeyColorDto dto : colorsToUpdate) {
            int localLedId = dto.id - globalLedOffset;
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
            sendPacket(1050, device.controllerId, fullPayload.array());
        } catch (IOException e) {
            System.err
                    .println("Failed to send LED update to OpenRGB device " + deviceId + ". Connection may be closed.");
            e.printStackTrace();
        }
    }

    public void restoreOriginalModes(Set<Integer> enabledDevices) {
        if (socket == null || socket.isClosed()) {
            return;
        }
        for (int index = 0; index < devices.size(); index++) {
            if (!enabledDevices.contains(index)) continue;
            OpenRGBDevice device = devices.get(index);
            if (device.originalModeData == null) {
                continue;
            }
            try {
                ByteBuffer mode = ByteBuffer.allocate(8 + device.originalModeData.length).order(ByteOrder.LITTLE_ENDIAN);
                mode.putInt(mode.capacity());
                mode.putInt(device.originalModeIndex);
                mode.put(device.originalModeData);
                sendPacket(1101, device.controllerId, mode.array());

                if (device.originalModeUsesPerLedColors && device.originalColors.length > 0) {
                    ByteBuffer colors = ByteBuffer.allocate(6 + device.originalColors.length)
                            .order(ByteOrder.LITTLE_ENDIAN);
                    colors.putInt(colors.capacity());
                    colors.putShort((short) (device.originalColors.length / 4));
                    colors.put(device.originalColors);
                    sendPacket(1050, device.controllerId, colors.array());
                }
            } catch (IOException e) {
                LOGGER.warn("Failed to restore the original mode of OpenRGB device " + device.name, e);
            }
        }
    }

    public void loadProfile(String profileName) {
        if (socket == null || socket.isClosed()) {
            return;
        }
        try {
            byte[] name = (profileName + "\0").getBytes(StandardCharsets.UTF_8);
            // 152 loads a profile; 151 would save over it.
            sendPacket(152, 0, name);
        } catch (IOException e) {
            LOGGER.warn("Failed to load OpenRGB profile " + profileName, e);
        }
    }

    public void enterCustomMode(Set<Integer> enabledDevices) {
        if (socket == null || socket.isClosed()) {
            return;
        }
        for (int index = 0; index < devices.size(); index++) {
            if (!enabledDevices.contains(index)) continue;
            OpenRGBDevice device = devices.get(index);
            try {
                byte[] request = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                        .putInt(serverProtocolVersion).array();
                sendPacket(1, device.controllerId, request);
                OpenRGBDevice current = parseDeviceData(readResponse());
                device.originalModeIndex = current.originalModeIndex;
                device.originalModeData = current.originalModeData;
                device.originalModeUsesPerLedColors = current.originalModeUsesPerLedColors;
                device.originalColors = current.originalColors;
                sendPacket(1100, device.controllerId, new byte[0]);
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("Failed to switch OpenRGB device " + device.name + " to direct mode", e);
            }
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
        if (dataSize < 0 || dataSize > MAX_RESPONSE_DATA_SIZE) {
            throw new IOException("OpenRGB response exceeded the 64 MiB limit");
        }

        byte[] dataBytes = new byte[dataSize];
        if (dataSize > 0) {
            in.readFully(dataBytes);
        }
        return ByteBuffer.wrap(dataBytes).order(ByteOrder.LITTLE_ENDIAN);
    }

    private int getLedOffsetForDevice(int deviceIndex) {
        return 2000 + (deviceIndex * 1000);
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
