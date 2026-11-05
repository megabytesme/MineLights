package megabytesme.minelights.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

/** Validated server endpoint loaded from the per-user local discovery file. */
public final class V2Endpoint {
    private static final Pattern HEX_SHA256 = Pattern.compile("[0-9a-fA-F]{64}");
    private static final int MAX_ENDPOINT_FILE_BYTES = 16 * 1024;
    private static final int MAX_PAIRING_FILE_BYTES = 16 * 1024;

    public final String serverId;
    public final String serverName;
    public final String host;
    public final int port;
    public final String certificateSha256;
    public final int protocolMajor;
    public final int protocolMinor;

    private V2Endpoint(String serverId, String serverName, String host, int port,
            String certificateSha256, int protocolMajor, int protocolMinor) {
        this.serverId = serverId;
        this.serverName = serverName;
        this.host = host;
        this.port = port;
        this.certificateSha256 = certificateSha256.toLowerCase();
        this.protocolMajor = protocolMajor;
        this.protocolMinor = protocolMinor;
    }

    public static V2Endpoint remote(RemoteServerProfile profile) throws IOException {
        if (profile == null || profile.serverId == null
                || profile.host == null || profile.port < 1 || profile.port > 65535
                || profile.tlsCertificateSha256 == null || !HEX_SHA256.matcher(profile.tlsCertificateSha256).matches()) {
            throw new IOException("Remote MineLights Server configuration is invalid.");
        }
        return new V2Endpoint(profile.serverId,
                profile.serverName == null || profile.serverName.trim().isEmpty() ? profile.host : profile.serverName,
                profile.host, profile.port, profile.tlsCertificateSha256, 2, 0);
    }

    public static Path getV2Directory() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.trim().isEmpty()) {
            return Paths.get(localAppData).resolve("MineLights").resolve("v2");
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return Paths.get(System.getProperty("user.home"), "AppData", "Local", "MineLights", "v2");
        }
        String dataHome = System.getenv("XDG_DATA_HOME");
        if (dataHome != null && !dataHome.trim().isEmpty()) {
            return Paths.get(dataHome).resolve("MineLights").resolve("v2");
        }
        return Paths.get(System.getProperty("user.home"), ".local", "share", "MineLights", "v2");
    }

    public static boolean localEndpointDocumentExists() {
        Path endpointPath = getV2Directory().resolve("endpoint.json");
        if (!Files.exists(endpointPath, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        if (!Files.isRegularFile(endpointPath, LinkOption.NOFOLLOW_LINKS)) {
            return true;
        }
        final V2Endpoint endpoint;
        try {
            endpoint = readLocal();
        } catch (IOException e) {
            // Malformed metadata fails closed: it must not silently enable a V1 downgrade.
            return true;
        }
        if (endpoint == null) {
            return true;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(endpoint.host, endpoint.port), 300);
            return true;
        } catch (IOException e) {
            // A valid endpoint file with no listener is stale (for example, after a crash).
            return false;
        }
    }

    public static V2Endpoint readLocal() throws IOException {
        Path endpointPath = getV2Directory().resolve("endpoint.json");
        if (!Files.isRegularFile(endpointPath, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        JsonObject json;
        try {
            json = new JsonParser().parse(new String(
                    readBoundedFile(endpointPath, MAX_ENDPOINT_FILE_BYTES), "UTF-8")).getAsJsonObject();
        } catch (RuntimeException | java.io.UnsupportedEncodingException e) {
            throw new IOException("Invalid MineLights Server endpoint file.", e);
        }

        int schema = intValue(json, "schema_version", -1);
        String serverId = stringValue(json, "server_id");
        String serverName = stringValue(json, "server_name");
        String host = stringValue(json, "host");
        int port = intValue(json, "port", -1);
        String fingerprint = stringValue(json, "tls_certificate_sha256");
        int major = intValue(json, "protocol_major", -1);
        int minor = intValue(json, "protocol_minor", -1);

        if (schema != 1 || serverId == null || serverId.isEmpty() || !"127.0.0.1".equals(host)
                || port < 1 || port > 65535 || fingerprint == null || !HEX_SHA256.matcher(fingerprint).matches()
                || major != 2 || minor < 0) {
            throw new IOException("MineLights Server endpoint file has unsupported or invalid fields.");
        }
        return new V2Endpoint(serverId, serverName == null ? "MineLights Server" : serverName,
                host, port, fingerprint, major, minor);
    }

    public static byte[] readLocalEnrollmentKey() throws IOException {
        Path keyPath = getV2Directory().resolve("local-enrollment.key");
        if (!Files.isRegularFile(keyPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("MineLights Server local enrollment key is unavailable.");
        }
        byte[] key = readBoundedFile(keyPath, 32);
        if (key.length != 32) {
            throw new IOException("MineLights Server local enrollment key must contain 32 bytes.");
        }
        return key;
    }

    public static RemoteServerProfile readPairingFile(Path path) throws IOException {
        if (path == null || path.getFileName() == null
                || !path.getFileName().toString().toLowerCase().endsWith(".mlpair")
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Choose a MineLights .mlpair file.");
        }
        JsonObject json;
        try {
            json = new JsonParser().parse(new String(
                    readBoundedFile(path, MAX_PAIRING_FILE_BYTES), "UTF-8")).getAsJsonObject();
        } catch (RuntimeException | java.io.UnsupportedEncodingException e) {
            throw new IOException("Invalid MineLights pairing file.", e);
        }

        if (intValue(json, "schema_version", -1) != 1) {
            throw new IOException("Unsupported MineLights pairing file schema.");
        }
        RemoteServerProfile profile = new RemoteServerProfile();
        profile.serverId = stringValue(json, "server_id");
        profile.host = stringValue(json, "host");
        profile.port = intValue(json, "port", -1);
        profile.tlsCertificateSha256 = stringValue(json, "tls_certificate_sha256");
        profile.pairingId = stringValue(json, "pairing_id");
        profile.pairingCode = stringValue(json, "pairing_code");
        String expiresAt = stringValue(json, "expires_at_utc");
        if (profile.serverId == null || profile.serverId.trim().isEmpty()
                || profile.host == null || profile.host.trim().isEmpty()
                || profile.port < 1 || profile.port > 65535
                || profile.tlsCertificateSha256 == null || !HEX_SHA256.matcher(profile.tlsCertificateSha256).matches()
                || profile.pairingId == null || profile.pairingCode == null || expiresAt == null) {
            throw new IOException("MineLights pairing file is missing a required field.");
        }
        try {
            UUID parsedServerId = UUID.fromString(profile.serverId);
            UUID parsedPairingId = UUID.fromString(profile.pairingId);
            if (!parsedServerId.toString().equals(profile.serverId)
                    || !parsedPairingId.toString().equals(profile.pairingId)) {
                throw new IOException("MineLights pairing file contains a malformed ID.");
            }
            byte[] pairingCode = Base64.getUrlDecoder().decode(profile.pairingCode);
            if (pairingCode.length != 32 || profile.pairingCode.contains("=")) {
                throw new IOException("MineLights pairing file contains a malformed pairing code.");
            }
            java.time.Instant expiry = java.time.Instant.parse(expiresAt);
            if (expiry.isBefore(java.time.Instant.now())) {
                throw new IOException("This MineLights pairing file has expired. Create a new pairing file on the server.");
            }
            if (expiry.isAfter(java.time.Instant.now().plusSeconds(300))) {
                throw new IOException("MineLights pairing file expires more than five minutes from now.");
            }
        } catch (java.time.format.DateTimeParseException e) {
            throw new IOException("MineLights pairing file has an invalid expiry time.", e);
        } catch (IllegalArgumentException e) {
            throw new IOException("MineLights pairing file contains malformed credentials.", e);
        }
        profile.serverName = profile.host + ":" + profile.port;
        return profile;
    }

    private static String stringValue(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : null;
    }

    private static byte[] readBoundedFile(Path path, int maxBytes) throws IOException {
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS);
                ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 4096))) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count > maxBytes - total) {
                    throw new IOException("MineLights file exceeds the " + maxBytes + " byte size limit.");
                }
                output.write(buffer, 0, count);
                total += count;
            }
            return output.toByteArray();
        }
    }

    private static int intValue(JsonObject json, String key, int fallback) {
        try {
            return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsInt() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
