package megabytesme.minelights.rgb;

import com.google.gson.Gson;
import megabytesme.minelights.effects.RGBColorDto;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class YeelightController {
    public static final Logger LOGGER = LogManager.getLogger("Minelights-Yeelight");
    private static final String DISCOVERY_MSG = "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: 239.255.255.250:1982\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "ST: wifi_bulb\r\n";
    private static final String MULTICAST_ADDRESS = "239.255.255.250";
    private static final int MULTICAST_PORT = 1982;
    private static final int COMMAND_PORT = 55443;
    private static final int DISCOVERY_TIMEOUT_MS = 3000;
    private static final int YEELIGHT_SMOOTH_DURATION = 400;
    static final int MAX_REPLY_BYTES = 4096;

    private final List<YeelightDevice> devices = Collections.synchronizedList(new ArrayList<>());

    public static class YeelightDevice {
        public final String id;
        public final String location;
        public final String ip;
        public final int port;
        public final String model;
        public final String name;
        private final InetSocketAddress endpoint;
        private Socket socket;
        private DataOutputStream out;
        private final AtomicInteger commandId = new AtomicInteger(1);
        private final RGBColorDto lastColor = new RGBColorDto(-1, -1, -1);
        private final Object socketLock = new Object();

        private YeelightDevice(String id, String location, InetAddress sender, String model, String name) {
            this.id = id;
            this.location = location;
            this.endpoint = new InetSocketAddress(sender, COMMAND_PORT);
            this.ip = sender.getHostAddress();
            this.port = endpoint.getPort();
            this.model = model;
            this.name = name;
        }

        public boolean connect() {
            synchronized (socketLock) {
                if (socket != null && socket.isConnected() && !socket.isClosed()) {
                    return true;
                }
                try {
                    disconnect();
                    socket = new Socket();
                    socket.connect(endpoint, 1000);
                    out = new DataOutputStream(socket.getOutputStream());
                    lastColor.r = lastColor.g = lastColor.b = -1;
                    LOGGER.info("Connected to Yeelight device: {}", id);

                    Socket connection = socket;
                    Thread readerThread = new Thread(() -> drainReplies(connection));
                    readerThread.setDaemon(true);
                    readerThread.setName("Yeelight-Reader-" + id);
                    readerThread.start();

                    return true;
                } catch (IOException e) {
                    LOGGER.error("Failed to connect to Yeelight device {}: {}", id, e.getMessage());
                    disconnect();
                    return false;
                }
            }
        }

        public void disconnect() {
            synchronized (socketLock) {
                closeSocket(socket);
                socket = null;
                out = null;
            }
        }

        private void drainReplies(Socket connection) {
            try {
                InputStream replies = new BufferedInputStream(connection.getInputStream(), MAX_REPLY_BYTES);
                byte[] frame = new byte[MAX_REPLY_BYTES];
                String line;
                while ((line = readReply(replies, frame)) != null) {
                    LOGGER.trace("[TCP-RECV {}] {}", id, line);
                }
            } catch (IOException e) {
                LOGGER.debug("Yeelight reply reader for {} stopped: {}", id, e.getMessage());
            } finally {
                closeSocket(connection);
                synchronized (socketLock) {
                    if (socket == connection) {
                        socket = null;
                        out = null;
                    }
                }
                LOGGER.info("Reader thread for Yeelight {} stopping.", id);
            }
        }

        private static void closeSocket(Socket connection) {
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (IOException ignored) {
            }
        }

        public void setColor(int r, int g, int b) {
            synchronized (socketLock) {
                if (socket == null || !socket.isConnected() || socket.isClosed()) {
                    if (!connect()) {
                        return;
                    }
                }
                if (r == lastColor.r && g == lastColor.g && b == lastColor.b) {
                    return;
                }

                try {
                    int rgbVal = (r << 16) | (g << 8) | b;

                    Map<String, Object> command = new HashMap<>();
                    command.put("id", commandId.getAndIncrement());
                    command.put("method", "set_rgb");
                    command.put("params", new Object[] { rgbVal, "smooth", YEELIGHT_SMOOTH_DURATION });

                    String jsonCmd = new Gson().toJson(command) + "\r\n";
                    LOGGER.trace("[TCP-SEND {}] {}", id, jsonCmd.trim());
                    out.write(jsonCmd.getBytes(StandardCharsets.UTF_8));
                    out.flush();

                    lastColor.r = r;
                    lastColor.g = g;
                    lastColor.b = b;
                } catch (IOException e) {
                    LOGGER.error("Failed to send command to Yeelight device {}: {}", id, e.getMessage());
                    disconnect();
                }
            }
        }
    }

    public synchronized boolean discover(java.util.function.BooleanSupplier shouldContinue) {
        if (!shouldContinue.getAsBoolean()) return false;
        LOGGER.info("Starting Yeelight discovery...");
        Map<String, YeelightDevice> foundDevices = new HashMap<>();

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(DISCOVERY_TIMEOUT_MS);
            byte[] discoveryMsgBytes = DISCOVERY_MSG.getBytes(StandardCharsets.UTF_8);
            InetAddress multicastAddress = InetAddress.getByName(MULTICAST_ADDRESS);
            DatagramPacket sendPacket = new DatagramPacket(discoveryMsgBytes, discoveryMsgBytes.length,
                    multicastAddress, MULTICAST_PORT);

            LOGGER.debug("[UDP-SEND] Broadcasting discovery message from local port: {}", socket.getLocalPort());
            socket.send(sendPacket);
            LOGGER.debug("[UDP-SEND] Broadcast sent successfully.");

            long startTime = System.currentTimeMillis();
            while (shouldContinue.getAsBoolean() && System.currentTimeMillis() - startTime < DISCOVERY_TIMEOUT_MS) {
                try {
                    byte[] receiveBuffer = new byte[1500];
                    DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);

                    socket.receive(receivePacket);

                    String response = new String(receivePacket.getData(), 0, receivePacket.getLength(),
                            StandardCharsets.UTF_8);
                    LOGGER.info("[UDP-RECV] Received response from {}:{}", receivePacket.getAddress().getHostAddress(),
                            receivePacket.getPort());
                    LOGGER.trace("[UDP-RECV] Raw data:\n{}", response);

                    YeelightDevice device = parseDiscoveryResponse(response, receivePacket.getAddress());
                    if (device != null && foundDevices.putIfAbsent(device.id, device) == null) {
                        LOGGER.info("Discovered Yeelight device: id={}, name='{}', location={}", device.id,
                                device.name, device.location);
                    }
                } catch (SocketTimeoutException e) {
                    LOGGER.debug("[UDP-RECV] Discovery listener timed out. Assuming all devices have responded.");
                    break;
                } catch (IOException e) {
                    LOGGER.error("Error during Yeelight discovery receive loop", e);
                    break;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Fatal error during Yeelight discovery setup or broadcast", e);
            return false;
        }

        if (!shouldContinue.getAsBoolean()) return false;
        this.devices.clear();
        this.devices.addAll(foundDevices.values());
        LOGGER.info("Yeelight discovery process finished. Found {} total devices.", this.devices.size());
        if (!this.devices.isEmpty()) {
            devices.parallelStream().forEach(device -> {
                if (shouldContinue.getAsBoolean()) device.connect();
                if (!shouldContinue.getAsBoolean()) device.disconnect();
            });
        }
        return true;
    }

    static YeelightDevice parseDiscoveryResponse(String response, InetAddress sender) {
        if (!(sender instanceof Inet4Address)) {
            return null;
        }
        Map<String, String> headers = new HashMap<>();
        String[] lines = response.split("\r\n");
        for (String line : lines) {
            if (line.contains(":")) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    headers.put(parts[0].trim().toLowerCase(Locale.ROOT), parts[1].trim());
                }
            }
        }
        String id = headers.get("id");
        String location = headers.get("location");
        String expectedLocation = "yeelight://" + sender.getHostAddress() + ":" + COMMAND_PORT;
        if (id == null || !expectedLocation.equals(location)) {
            return null;
        }
        return new YeelightDevice(id, location, sender, headers.getOrDefault("model", "unknown"),
                headers.getOrDefault("name", ""));
    }

    /** Read one LF-terminated reply, with the terminator included in the byte budget. */
    static String readReply(InputStream replies, byte[] frame) throws IOException {
        for (int length = 0; length < frame.length; length++) {
            int value = replies.read();
            if (value == -1) {
                if (length == 0) {
                    return null;
                }
                throw new EOFException("Incomplete Yeelight reply");
            }
            if (value == '\n') {
                int payloadLength = length > 0 && frame[length - 1] == '\r' ? length - 1 : length;
                return new String(frame, 0, payloadLength, StandardCharsets.UTF_8);
            }
            frame[length] = (byte) value;
        }
        throw new IOException("Yeelight reply exceeds " + frame.length + " bytes");
    }

    public List<YeelightDevice> getDevices() {
        return devices;
    }

    public void updateLed(int deviceId, RGBColorDto color) {
        if (deviceId >= 0 && deviceId < devices.size()) {
            devices.get(deviceId).setColor(color.r, color.g, color.b);
        }
    }

    public synchronized void disconnect() {
        devices.forEach(YeelightDevice::disconnect);
    }
}
