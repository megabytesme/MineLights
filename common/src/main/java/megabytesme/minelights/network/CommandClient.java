package megabytesme.minelights.network;

import megabytesme.minelights.MineLightsClient;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class CommandClient {
    public static void sendCommand(String command) {
        new Thread(() -> {
            sendCommandSync(command);
        }).start();
    }

    public static boolean sendCommandSync(String command) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 63213), 1000);
            try (OutputStream out = socket.getOutputStream()) {
                out.write(command.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            return true;
        } catch (Exception e) {
            MineLightsClient.LOGGER.error("Failed to send command '{}' to proxy: {}", command, e.getMessage());
            return false;
        }
    }
}
