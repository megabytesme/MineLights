package megabytesme.minelights;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UDPClient {
    private static DatagramSocket socket;
    private static InetAddress address;
    private static final int PORT = 63212;

    static {
        try {
            socket = new DatagramSocket();
            address = InetAddress.getByName("localhost");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendFrameData(String frameJson) {
        if (socket == null || socket.isClosed())
            return;
        try {
            byte[] buffer = frameJson.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, PORT);
            socket.send(packet);
        } catch (Exception e) {
            // Silently ignore rapid-fire errors
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
    }
}