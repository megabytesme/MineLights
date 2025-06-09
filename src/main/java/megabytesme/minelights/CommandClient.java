package megabytesme.minelights;

import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class CommandClient {
    public static void sendCommand(String command) {
        new Thread(() -> {
            try (Socket socket = new Socket("127.0.0.1", 63213);
                    OutputStream out = socket.getOutputStream()) {
                out.write(command.getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (Exception e) {
                MineLightsClient.LOGGER.error("Failed to send command '{}' to proxy: {}", command, e.getMessage());
            }
        }).start();
    }
}