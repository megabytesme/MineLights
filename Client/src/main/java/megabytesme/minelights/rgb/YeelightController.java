package megabytesme.minelights.rgb;

import com.google.gson.Gson;
import megabytesme.minelights.effects.RGBColorDto;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
    private static final int DISCOVERY_TIMEOUT_MS = 3000;
    private static final int YEELIGHT_SMOOTH_DURATION = 400;

    private final List<YeelightDevice> devices = Collections.synchronizedList(new ArrayList<>());

    public static class YeelightDevice {
        public String id;
        public String location;
        public String ip;
        public int port;
        public String model;
        public String name;
        Socket socket;
        DataOutputStream out;
        BufferedReader in;
        final AtomicInteger commandId = new AtomicInteger(1);
        final RGBColorDto lastColor = new RGBColorDto(-1, -1, -1);
        private final Object socketLock = new Object();

        public YeelightDevice(String id, String location, String model, String name) {
            this.id = id;
            this.location = location;
            this.model = model;
            this.name = name;
            String[] parts = location.replace("yeelight://", "").split(":");
            this.ip = parts[0];
            this.port = Integer.parseInt(parts[1]);
        }

        public boolean connect() {
            synchronized (socketLock) {
                if (socket != null && socket.isConnected() && !socket.isClosed()) {
                    return true;
                }
                try {
                    disconnect();
                    socket = new Socket();
                    socket.connect(new InetSocketAddress(ip, port), 1000);
                    out = new DataOutputStream(socket.getOutputStream());
                    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    LOGGER.info("Connected to Yeelight device: {}", id);

                    Thread readerThread = new Thread(() -> {
                        try {
                            while (socket != null && !socket.isClosed() && in != null) {
                                String line = in.readLine();
                                if (line == null)
                                    break;
                                LOGGER.trace("[TCP-RECV {}] {}", id, line);
                            }
                        } catch (IOException ignored) {
                        } finally {
                            LOGGER.info("Reader thread for Yeelight {} stopping.", id);
                        }
                    });
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
                try {
                    if (socket != null)
                        socket.close();
                } catch (IOException ignored) {
                }
                socket = null;
                out = null;
                in = null;
            }
        }

        public void setColor(int r, int g, int b) {
            if (r == lastColor.r && g == lastColor.g && b == lastColor.b) {
                return;
            }

            synchronized (socketLock) {
                if (socket == null || !socket.isConnected() || socket.isClosed()) {
                    if (!connect()) {
                        return;
                    }
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

    public boolean discover() {
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
            while (System.currentTimeMillis() - startTime < DISCOVERY_TIMEOUT_MS) {
                try {
                    byte[] receiveBuffer = new byte[1500];
                    DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);

                    socket.receive(receivePacket);

                    String response = new String(receivePacket.getData(), 0, receivePacket.getLength(),
                            StandardCharsets.UTF_8);
                    LOGGER.info("[UDP-RECV] Received response from {}:{}", receivePacket.getAddress().getHostAddress(),
                            receivePacket.getPort());
                    LOGGER.trace("[UDP-RECV] Raw data:\n{}", response);

                    Map<String, String> headers = parseDiscoveryResponse(response);
                    String id = headers.get("id");
                    String location = headers.get("Location");

                    if (id != null && location != null && !foundDevices.containsKey(id)) {
                        String model = headers.getOrDefault("model", "unknown");
                        String name = headers.getOrDefault("name", "");
                        LOGGER.info("SUCCESS: Discovered and parsed Yeelight device: id={}, name='{}', location={}", id,
                                name, location);
                        foundDevices.put(id, new YeelightDevice(id, location, model, name));
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

        this.devices.clear();
        this.devices.addAll(foundDevices.values());
        LOGGER.info("Yeelight discovery process finished. Found {} total devices.", this.devices.size());
        if (!this.devices.isEmpty()) {
            devices.parallelStream().forEach(YeelightDevice::connect);
        }
        return true;
    }

    private Map<String, String> parseDiscoveryResponse(String response) {
        Map<String, String> headers = new HashMap<>();
        String[] lines = response.split("\r\n");
        for (String line : lines) {
            if (line.contains(":")) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    headers.put(parts[0].trim(), parts[1].trim());
                }
            }
        }
        return headers;
    }

    public List<YeelightDevice> getDevices() {
        return devices;
    }

    public void updateLed(int deviceId, RGBColorDto color) {
        if (deviceId >= 0 && deviceId < devices.size()) {
            devices.get(deviceId).setColor(color.r, color.g, color.b);
        }
    }

    public void disconnect() {
        devices.forEach(YeelightDevice::disconnect);
    }
}