package megabytesme.minelights.network;

import megabytesme.minelights.model.PlayerDto;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class UDPClient {
    private static final Logger LOGGER = LogManager.getLogger("MineLights-UDPClient");
    private static final String LOOPBACK_HOST = "127.0.0.1";
    private static DatagramSocket socket;
    private static InetAddress address;
    private static final int PORT = 63212;
    private static volatile boolean sendFailureLogged = false;

    static {
        ensureSocket();
    }

    private static synchronized boolean ensureSocket() {
        try {
            if (address == null) {
                address = InetAddress.getByName(LOOPBACK_HOST);
            }
            if (socket == null || socket.isClosed()) {
                socket = new DatagramSocket();
                sendFailureLogged = false;
            }
            return true;
        } catch (Exception e) {
            if (!sendFailureLogged) {
                LOGGER.error("Failed to initialize UDP client for MineLights Server updates", e);
                sendFailureLogged = true;
            }
            return false;
        }
    }

    public static void sendFrameData(String frameJson) {
        if (!ensureSocket()) {
            return;
        }
        try {
            byte[] buffer = frameJson.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, PORT);
            socket.send(packet);
            sendFailureLogged = false;
        } catch (Exception e) {
            if (!sendFailureLogged) {
                LOGGER.error("Failed to send UDP frame to MineLights Server", e);
                sendFailureLogged = true;
            }
            synchronized (UDPClient.class) {
                if (socket != null) {
                    socket.close();
                }
                socket = null;
            }
        }
    }

    public static void sendDisconnectSignal() {
        PlayerDto dto = new PlayerDto();
        dto.setInGame(false);
        String disconnectJson = new com.google.gson.Gson().toJson(dto);
        sendFrameData(disconnectJson);
    }

    public static void close() {
        if (socket != null) {
            socket.close();
        }
        socket = null;
    }
}
