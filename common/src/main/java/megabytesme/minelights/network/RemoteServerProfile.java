package megabytesme.minelights.network;

/** Persisted connection details for one authenticated remote MineLights Server. */
public class RemoteServerProfile {
    public String serverId;
    public String serverName;
    public String host;
    public int port;
    public String tlsCertificateSha256;
    public String clientId;
    public String clientSecret;

    /** Pairing credentials exist only in memory until registration succeeds. */
    public transient String pairingId;
    public transient String pairingCode;

    public RemoteServerProfile() {
    }

    public String displayName() {
        if (serverName != null && !serverName.trim().isEmpty()) {
            return serverName;
        }
        return host == null ? "MineLights Server" : host + ":" + port;
    }
}
