package megabytesme.minelights.rgb;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** In-memory discovery and reply validation without sockets. */
public final class YeelightControllerTest {
    private YeelightControllerTest() { }

    public static void main(String[] args) throws Exception {
        discoveryRequiresTheSenderAndStandardPort();
        replyLimitCountsBytesAndLineEndings();
    }

    private static void discoveryRequiresTheSenderAndStandardPort() throws Exception {
        InetAddress sender = InetAddress.getByAddress(new byte[] { (byte) 192, 0, 2, 1 });
        YeelightController.YeelightDevice device = YeelightController.parseDiscoveryResponse(
                discovery("yeelight://192.0.2.1:55443"), sender);
        check(device != null && "fixture".equals(device.id) && "Lamp".equals(device.name)
                && sender.getHostAddress().equals(device.ip) && device.port == 55443, "valid reply");
        for (String location : new String[] {
                "yeelight://192.0.2.2:55443", "yeelight://192.0.2.1:55444",
                "yeelight://lamp.example:55443", "yeelight://192.0.2.1:55443/path",
                "yeelight://192.0.2.1:055443" }) {
            check(YeelightController.parseDiscoveryResponse(discovery(location), sender) == null,
                    "unexpected discovery endpoint accepted");
        }
    }

    private static String discovery(String location) {
        return "HTTP/1.1 200 OK\r\nLoCaTiOn: " + location + "\r\nid: fixture\r\nname: Lamp\r\n";
    }

    private static void replyLimitCountsBytesAndLineEndings() throws Exception {
        byte[] buffer = new byte[YeelightController.MAX_REPLY_BYTES];
        ByteArrayInputStream normal = input("{}\r\n{\"result\":\"ok\"}\n");
        check("{}".equals(YeelightController.readReply(normal, buffer)), "CRLF reply");
        check("{\"result\":\"ok\"}".equals(YeelightController.readReply(normal, buffer)), "LF reply");
        check(YeelightController.readReply(normal, buffer) == null, "clean EOF");

        byte[] exact = new byte[buffer.length];
        Arrays.fill(exact, (byte) 'x');
        exact[exact.length - 2] = '\r';
        exact[exact.length - 1] = '\n';
        check(YeelightController.readReply(new ByteArrayInputStream(exact), buffer).length()
                == buffer.length - 2, "reply exactly at byte limit");

        byte[] over = Arrays.copyOf(exact, exact.length + 2);
        over[exact.length - 1] = (byte) 0xc2;
        over[exact.length] = (byte) 0xa9;
        over[exact.length + 1] = '\n';
        ByteArrayInputStream oversized = new ByteArrayInputStream(over);
        expectIOException(oversized, buffer, IOException.class);
        check(oversized.available() == 2, "oversized reply consumed bytes beyond the limit");
        expectIOException(input("partial reply"), buffer, EOFException.class);
    }

    private static ByteArrayInputStream input(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void expectIOException(ByteArrayInputStream stream, byte[] buffer,
            Class<? extends IOException> expected) throws Exception {
        try {
            YeelightController.readReply(stream, buffer);
        } catch (IOException error) {
            check(expected.isInstance(error), "unexpected error type");
            return;
        }
        throw new AssertionError("Expected " + expected.getSimpleName());
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
