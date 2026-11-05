package megabytesme.minelights.rgb;

/** An OpenRGB native-protocol endpoint. This is separate from MineLights V2 auth/TLS. */
public class OpenRGBEndpoint {
    public String host = "127.0.0.1";
    public int port = 6742;

    public OpenRGBEndpoint() {
    }

    public OpenRGBEndpoint(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String address() {
        return host + ":" + port;
    }

    public boolean isValid() {
        return host != null && !host.trim().isEmpty() && port >= 1 && port <= 65535;
    }
}
