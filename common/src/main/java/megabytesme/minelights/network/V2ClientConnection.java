package megabytesme.minelights.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonParser;
import megabytesme.minelights.effects.RGBColorDto;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Authenticated V2 transport used for the local server and configured remote servers. */
public final class V2ClientConnection implements AutoCloseable {
    private static final int PROTOCOL_MAJOR = 2;
    private static final int PROTOCOL_MINOR = 0;
    private static final int HEADER_LENGTH = 16;
    private static final int MAX_CONTROL_BYTES = 1024 * 1024;
    private static final int MAX_PAYLOAD_BYTES = 8 * 1024 * 1024;
    private static final int CLIENT_HELLO = 0x0001;
    private static final int SERVER_HELLO = 0x0002;
    private static final int CLIENT_AUTH = 0x0003;
    private static final int AUTH_RESULT = 0x0004;
    private static final int DEVICE_LIST = 0x0010;
    private static final int DEVICE_CHANGED = 0x0011;
    private static final int LIGHTING_FRAME = 0x0020;
    private static final int PING = 0x0030;
    private static final int PONG = 0x0031;
    private static final int REQUEST = 0x0040;
    private static final int RESPONSE = 0x0041;
    private static final int ERROR = 0x00FE;
    private static final int GOODBYE = 0x00FF;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Gson GSON = new Gson();
    private static final Object[] REMOTE_ENROLLMENT_LOCKS = createEnrollmentLocks();

    private static Object[] createEnrollmentLocks() {
        Object[] locks = new Object[64];
        for (int i = 0; i < locks.length; i++) locks[i] = new Object();
        return locks;
    }

    public final String serverId;
    public final String serverName;
    private final SSLSocket socket;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final Object writeLock = new Object();
    private final Set<String> leasedDeviceIds;
    private final Set<String> desiredDeviceIds;
    private final Map<String, ArrayBlockingQueue<JsonObject>> pendingRequests = new ConcurrentHashMap<String, ArrayBlockingQueue<JsonObject>>();
    private final int negotiatedMinor;
    private volatile List<DeviceDescriptor> devices;
    private volatile boolean connected = true;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Thread readerThread;
    private final Thread heartbeatThread;
    private long frameSequence;
    private volatile DeviceChangeListener deviceChangeListener;

    private V2ClientConnection(V2Endpoint endpoint, SSLSocket socket,
            DataInputStream input, DataOutputStream output,
            List<DeviceDescriptor> devices, Set<String> leasedDeviceIds, Set<String> desiredDeviceIds, int negotiatedMinor) {
        this.serverId = endpoint.serverId;
        this.serverName = endpoint.serverName;
        this.socket = socket;
        this.input = input;
        this.output = output;
        this.negotiatedMinor = negotiatedMinor;
        this.devices = Collections.unmodifiableList(new ArrayList<DeviceDescriptor>(devices));
        this.leasedDeviceIds = Collections.synchronizedSet(new HashSet<String>(leasedDeviceIds));
        this.desiredDeviceIds = new HashSet<String>(desiredDeviceIds);
        this.readerThread = new Thread(this::readLoop, "MineLights-V2-" + safeThreadName(serverName));
        this.readerThread.setDaemon(true);
        this.heartbeatThread = new Thread(this::heartbeatLoop, "MineLights-V2-Ping-" + safeThreadName(serverName));
        this.heartbeatThread.setDaemon(true);
        this.readerThread.start();
        this.heartbeatThread.start();
    }

    public static V2ClientConnection connectLocal(V2Endpoint endpoint, String clientId,
            String clientSecret, byte[] localEnrollmentKey, String appId, String appVersion)
            throws IOException {
        return connectLocal(endpoint, clientId, clientSecret, localEnrollmentKey, appId, appVersion,
                Collections.<String>emptySet());
    }

    public static V2ClientConnection connectLocal(V2Endpoint endpoint, String clientId,
            String clientSecret, byte[] localEnrollmentKey, String appId, String appVersion,
            Set<String> disabledDeviceKeys) throws IOException {
        requireClientId(clientId);
        if (clientSecret != null && !clientSecret.isEmpty()) {
            try {
                return connectWithCredential(endpoint, clientId, clientSecret,
                        "client_secret", clientId, decodeBase64Url(clientSecret),
                        disabledDeviceKeys, appId, appVersion);
            } catch (AuthFailureException failure) {
                if (!"UNKNOWN_CLIENT".equals(failure.errorCode)) {
                    throw failure;
                }
            }
        }

        if (localEnrollmentKey == null || localEnrollmentKey.length != 32) {
            throw new IOException("MineLights Server local enrollment key is unavailable.");
        }
        if (clientSecret == null || clientSecret.isEmpty()) {
            clientSecret = randomSecret();
        }
        registerClient(endpoint, clientId, clientSecret, "local_enrollment", "local",
                localEnrollmentKey, appId, appVersion);
        return connectWithCredential(endpoint, clientId, clientSecret,
                "client_secret", clientId, decodeBase64Url(clientSecret),
                disabledDeviceKeys, appId, appVersion);
    }

    public static V2ClientConnection connectRemote(RemoteServerProfile profile,
            String clientId, String appId, String appVersion) throws IOException {
        return connectRemote(profile, clientId, appId, appVersion, Collections.<String>emptySet());
    }

    public static V2ClientConnection connectRemote(RemoteServerProfile profile,
            String clientId, String appId, String appVersion, Set<String> disabledDeviceKeys) throws IOException {
        if (profile == null) throw new IOException("Remote server configuration is missing.");
        String identity = String.valueOf(profile.serverId).toLowerCase(java.util.Locale.ROOT)
                + "|" + String.valueOf(clientId).toLowerCase(java.util.Locale.ROOT);
        Object enrollmentLock = REMOTE_ENROLLMENT_LOCKS[(identity.hashCode() & 0x7fffffff) % REMOTE_ENROLLMENT_LOCKS.length];
        synchronized (enrollmentLock) {
            return connectRemoteSerialized(profile, clientId, appId, appVersion, disabledDeviceKeys);
        }
    }

    private static V2ClientConnection connectRemoteSerialized(RemoteServerProfile profile,
            String clientId, String appId, String appVersion, Set<String> disabledDeviceKeys) throws IOException {
        V2Endpoint endpoint = V2Endpoint.remote(profile);
        String pairingCode;
        String pairingId;
        String secret;
        String pairedClientId;
        synchronized (megabytesme.minelights.config.SimpleJsonConfig.configLock()) {
            pairingCode = profile.pairingCode;
            pairingId = profile.pairingId;
            secret = profile.clientSecret;
            pairedClientId = profile.clientId;
            if (pairingCode != null && pairingId != null) {
                if (megabytesme.minelights.MineLightsClient.CONFIG == null
                        || megabytesme.minelights.MineLightsClient.CONFIG.remoteServers == null
                        || !megabytesme.minelights.MineLightsClient.CONFIG.remoteServers.contains(profile)) {
                    throw new IOException("The server pairing is no longer configured.");
                }
                new megabytesme.minelights.config.SimpleJsonConfig("minelights").ensureCredentialStorageAvailable();
                if (secret == null || secret.isEmpty()) secret = randomSecret();
                pairedClientId = clientId;
            }
        }
        if (pairingCode != null && pairingId != null) {
            registerClient(endpoint, pairedClientId, secret,
                    "pairing_code", pairingId, decodeBase64Url(pairingCode), appId, appVersion);
            synchronized (megabytesme.minelights.config.SimpleJsonConfig.configLock()) {
                if (megabytesme.minelights.MineLightsClient.CONFIG == null
                        || megabytesme.minelights.MineLightsClient.CONFIG.remoteServers == null
                        || !megabytesme.minelights.MineLightsClient.CONFIG.remoteServers.contains(profile)) {
                    throw new IOException("The server pairing was removed while registration was in progress.");
                }
                if (!pairingId.equals(profile.pairingId) || !pairingCode.equals(profile.pairingCode)) {
                    throw new IOException("The server pairing changed while registration was in progress.");
                }
                profile.clientId = pairedClientId;
                profile.clientSecret = secret;
                profile.pairingCode = null;
                profile.pairingId = null;
                megabytesme.minelights.MineLightsClient.saveConfig();
            }
        }
        if (secret == null || secret.isEmpty()) {
            throw new IOException("Remote server is not paired. Import a current .mlpair file.");
        }
        if (pairedClientId == null || pairedClientId.isEmpty()) {
            synchronized (megabytesme.minelights.config.SimpleJsonConfig.configLock()) {
                if (profile.clientId == null || profile.clientId.isEmpty()) profile.clientId = clientId;
                pairedClientId = profile.clientId;
            }
        }
        requireClientId(pairedClientId);
        return connectWithCredential(endpoint, pairedClientId, secret,
                "client_secret", pairedClientId, decodeBase64Url(secret),
                disabledDeviceKeys, appId, appVersion);
    }

    public static String newClientId() {
        return UUID.randomUUID().toString();
    }

    public static String randomSecret() {
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        return encodeBase64Url(secret);
    }

    private static void registerClient(V2Endpoint endpoint, String clientId, String clientSecret,
            String credentialType, String credentialId, byte[] credential,
            String appId, String appVersion) throws IOException {
        SSLSocket socket = openTls(endpoint);
        try {
            DataInputStream input = new DataInputStream(socket.getInputStream());
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            Handshake handshake = sendClientHello(endpoint, clientId, appId, appVersion, input, output, Collections.singleton("*"));
            JsonObject authResult = authenticate(input, output, endpoint, clientId, credentialType, credentialId,
                    credential, handshake.serverNonce, handshake.selectedMinor);
            JsonArray authScopes = authResult.has("scopes") && authResult.get("scopes").isJsonArray()
                    ? authResult.getAsJsonArray("scopes") : new JsonArray();
            if (!contains(authScopes, "client.enroll")) {
                throw new IOException("MineLights Server did not authorize client enrollment.");
            }

            JsonObject params = new JsonObject();
            params.addProperty("client_id", clientId);
            params.addProperty("client_secret", clientSecret);
            params.addProperty("app_id", appId);
            params.addProperty("app_version", appVersion);
            params.addProperty("display_name", appId + " (" + appVersion + ")");
            JsonArray scopes = new JsonArray();
            scopes.add(new JsonPrimitive("devices.read"));
            scopes.add(new JsonPrimitive("lighting.write"));
            params.add("requested_scopes", scopes);
            JsonObject request = new JsonObject();
            request.addProperty("request_id", UUID.randomUUID().toString());
            request.addProperty("operation", "client.register");
            request.add("params", params);
            writeMessage(output, REQUEST, request, handshake.selectedMinor);

            JsonObject response = readExpectedResponse(input, request.get("request_id").getAsString());
            if (!response.has("ok") || !response.get("ok").getAsBoolean()) {
                String code = response.has("error_code") ? response.get("error_code").getAsString() : "REGISTER_FAILED";
                throw new IOException("MineLights Server refused client registration (" + code + ").");
            }
            JsonObject result = response.has("result") && response.get("result").isJsonObject()
                    ? response.getAsJsonObject("result") : new JsonObject();
            JsonArray granted = result.has("granted_scopes") && result.get("granted_scopes").isJsonArray()
                    ? result.getAsJsonArray("granted_scopes") : new JsonArray();
            if (!contains(granted, "devices.read") || !contains(granted, "lighting.write")) {
                throw new IOException("MineLights Server did not grant the required lighting permissions.");
            }
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static V2ClientConnection connectWithCredential(V2Endpoint endpoint, String clientId,
            String clientSecret, String credentialType, String credentialId, byte[] credential,
            Set<String> disabledDeviceKeys, String appId, String appVersion) throws IOException {
        SSLSocket socket = openTls(endpoint);
        boolean keepSocket = false;
        try {
            DataInputStream input = new DataInputStream(socket.getInputStream());
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            Handshake handshake = sendClientHello(endpoint, clientId, appId, appVersion, input, output, disabledDeviceKeys);
            JsonObject authResult = authenticate(input, output, endpoint, clientId,
                    credentialType, credentialId, credential, handshake.serverNonce, handshake.selectedMinor);
            if (authResult.has("ok") && !authResult.get("ok").getAsBoolean()) {
                throw new AuthFailureException(authResult.has("error_code")
                        ? authResult.get("error_code").getAsString() : "AUTH_REJECTED");
            }
            JsonArray grantedScopes = authResult.has("scopes") && authResult.get("scopes").isJsonArray()
                    ? authResult.getAsJsonArray("scopes") : new JsonArray();
            if (!contains(grantedScopes, "devices.read") || !contains(grantedScopes, "lighting.write")) {
                throw new IOException("MineLights Server did not grant the required lighting permissions.");
            }

            JsonObject getDevices = new JsonObject();
            getDevices.addProperty("request_id", UUID.randomUUID().toString());
            getDevices.addProperty("operation", "devices.get");
            getDevices.add("params", new JsonObject());
            writeMessage(output, REQUEST, getDevices, handshake.selectedMinor);
            List<DeviceDescriptor> deviceList = readDeviceSnapshot(input, getDevices.get("request_id").getAsString());

            Set<String> requestedDevices = new HashSet<String>();
            for (DeviceDescriptor device : deviceList) {
                if (V2DevicePolicy.allows(disabledDeviceKeys, endpoint.serverId, device)) {
                    requestedDevices.add(device.deviceId);
                }
            }
            Set<String> leased = acquireDevices(input, output, requestedDevices, handshake.selectedMinor);

            socket.setSoTimeout(5000);
            keepSocket = true;
            return new V2ClientConnection(endpoint, socket, input, output, deviceList, leased, requestedDevices,
                    handshake.selectedMinor);
        } finally {
            if (!keepSocket) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static SSLSocket openTls(final V2Endpoint endpoint) throws IOException {
        try {
            X509TrustManager trustManager = new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    throw new CertificateException("MineLights client certificate authentication is not enabled.");
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    if (chain == null || chain.length == 0) {
                        throw new CertificateException("MineLights Server did not provide a certificate.");
                    }
                    try {
                        String fingerprint = bytesToHex(MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded()));
                        if (!fingerprint.equalsIgnoreCase(endpoint.certificateSha256)) {
                            throw new CertificateException("MineLights Server certificate fingerprint did not match the paired endpoint.");
                        }
                    } catch (CertificateException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new CertificateException("Could not verify MineLights Server certificate.", e);
                    }
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            };
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new X509TrustManager[] { trustManager }, RANDOM);
            SSLSocketFactory factory = context.getSocketFactory();
            SSLSocket socket = (SSLSocket) factory.createSocket();
            socket.connect(new java.net.InetSocketAddress(endpoint.host, endpoint.port), CONNECT_TIMEOUT_MS);
            List<String> protocols = new ArrayList<String>();
            for (String protocol : socket.getSupportedProtocols()) {
                if ("TLSv1.2".equals(protocol) || "TLSv1.3".equals(protocol)) {
                    protocols.add(protocol);
                }
            }
            if (!protocols.contains("TLSv1.2")) {
                socket.close();
                throw new IOException("This Java runtime cannot establish the required TLS 1.2 session.");
            }
            socket.setEnabledProtocols(protocols.toArray(new String[protocols.size()]));
            socket.setSoTimeout(REQUEST_TIMEOUT_MS);
            socket.startHandshake();
            return socket;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Could not establish a pinned TLS connection to MineLights Server.", e);
        }
    }

    private static Handshake sendClientHello(V2Endpoint endpoint, String clientId, String appId,
            String appVersion, DataInputStream input, DataOutputStream output, Set<String> disabledDeviceKeys) throws IOException {
        JsonObject hello = new JsonObject();
        hello.addProperty("client_id", clientId);
        hello.addProperty("app_id", appId);
        hello.addProperty("app_version", appVersion);
        hello.addProperty("min_minor", 0);
        hello.addProperty("max_minor", PROTOCOL_MINOR);
        JsonArray capabilities = new JsonArray();
        capabilities.add(new JsonPrimitive("json_frames"));
        capabilities.add(new JsonPrimitive("device_leases"));
        hello.add("capabilities", capabilities);
        JsonArray providers = new JsonArray();
        for (String provider : V2DevicePolicy.requestedProviders(disabledDeviceKeys)) providers.add(new JsonPrimitive(provider));
        hello.add("requested_integrations", providers);
        writeMessage(output, CLIENT_HELLO, hello);

        Message message = readMessage(input);
        if (message.type == ERROR) {
            throw new IOException(errorDescription(message.json));
        }
        if (message.type != SERVER_HELLO) {
            throw new IOException("MineLights Server sent an unexpected V2 handshake message.");
        }
        JsonObject serverHello = message.json;
        String serverId = stringValue(serverHello, "server_id");
        if (!endpoint.serverId.equals(serverId)) {
            throw new IOException("MineLights Server identity did not match the configured server.");
        }
        int selectedMinor = integerValue(serverHello, "selected_minor", -1);
        if (message.major != PROTOCOL_MAJOR || selectedMinor < 0 || selectedMinor > PROTOCOL_MINOR) {
            throw new IOException("MineLights Server selected an unsupported protocol version.");
        }
        String nonce = stringValue(serverHello, "nonce");
        if (nonce == null) {
            throw new IOException("MineLights Server did not provide an authentication challenge.");
        }
        return new Handshake(nonce, selectedMinor);
    }

    private static JsonObject authenticate(DataInputStream input, DataOutputStream output,
            V2Endpoint endpoint, String clientId, String credentialType, String credentialId,
            byte[] credential, String serverNonce, int negotiatedMinor) throws IOException {
        byte[] serverNonceBytes = decodeBase64Url(serverNonce);
        if (serverNonceBytes.length != 32) {
            throw new IOException("MineLights Server provided an invalid authentication challenge.");
        }
        byte[] clientNonce = new byte[32];
        RANDOM.nextBytes(clientNonce);
        byte[] proof = createProof(credential, credentialType, credentialId,
                endpoint.serverId, clientId, serverNonceBytes, clientNonce, PROTOCOL_MAJOR, negotiatedMinor);

        JsonObject auth = new JsonObject();
        auth.addProperty("credential_type", credentialType);
        auth.addProperty("credential_id", credentialId);
        auth.addProperty("client_nonce", encodeBase64Url(clientNonce));
        auth.addProperty("proof", encodeBase64Url(proof));
        writeMessage(output, CLIENT_AUTH, auth, negotiatedMinor);

        Message result = readMessage(input);
        if (result.type == ERROR) {
            throw new IOException(errorDescription(result.json));
        }
        if (result.type != AUTH_RESULT) {
            throw new IOException("MineLights Server sent an unexpected authentication response.");
        }
        if (!result.json.has("ok") || !result.json.get("ok").getAsBoolean()) {
            String error = stringValue(result.json, "error_code");
            if ("UNKNOWN_CLIENT".equals(error)) {
                throw new AuthFailureException(error);
            }
            throw new IOException("MineLights Server rejected authentication (" + (error == null ? "AUTH_REJECTED" : error) + ").");
        }
        return result.json;
    }

    private static byte[] createProof(byte[] key, String credentialType, String credentialId,
            String serverId, String clientId, byte[] serverNonce, byte[] clientNonce,
            int major, int minor) throws IOException {
        try {
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            data.write("MineLights Protocol V2\0".getBytes(StandardCharsets.US_ASCII));
            data.write(credentialType.getBytes(StandardCharsets.US_ASCII));
            data.write(0);
            data.write(credentialId.getBytes(StandardCharsets.US_ASCII));
            data.write(0);
            data.write(serverId.getBytes(StandardCharsets.US_ASCII));
            data.write(0);
            data.write(clientId.getBytes(StandardCharsets.US_ASCII));
            data.write(serverNonce);
            data.write(clientNonce);
            data.write((major >>> 8) & 0xff);
            data.write(major & 0xff);
            data.write((minor >>> 8) & 0xff);
            data.write(minor & 0xff);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.toByteArray());
        } catch (Exception e) {
            throw new IOException("Could not create MineLights authentication proof.", e);
        }
    }

    private static List<DeviceDescriptor> readDeviceSnapshot(DataInputStream input, String requestId)
            throws IOException {
        List<DeviceDescriptor> devices = new ArrayList<DeviceDescriptor>();
        boolean gotResponse = false;
        long deadline = System.currentTimeMillis() + REQUEST_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline && !gotResponse) {
            try {
                Message message = readMessage(input);
                if (message.type == DEVICE_LIST || message.type == DEVICE_CHANGED) {
                    devices = parseDevices(message.json);
                } else if (message.type == RESPONSE) {
                    String receivedId = stringValue(message.json, "request_id");
                    if (requestId.equals(receivedId)) {
                        if (!message.json.has("ok") || !message.json.get("ok").getAsBoolean()) {
                            throw new IOException("MineLights Server could not return its device list.");
                        }
                        JsonObject result = message.json.has("result") && message.json.get("result").isJsonObject()
                                ? message.json.getAsJsonObject("result") : new JsonObject();
                        if (result.has("devices") && result.get("devices").isJsonArray()) {
                            devices = parseDevices(result);
                        }
                        gotResponse = true;
                    }
                } else if (message.type == ERROR) {
                    throw new IOException(errorDescription(message.json));
                }
            } catch (SocketTimeoutException timeout) {
                break;
            }
        }
        return devices;
    }

    private static Set<String> acquireDevices(DataInputStream input, DataOutputStream output,
            Set<String> requestedDevices, int negotiatedMinor) throws IOException {
        if (requestedDevices.isEmpty()) {
            return Collections.emptySet();
        }
        JsonArray ids = new JsonArray();
        for (String id : requestedDevices) {
            ids.add(new JsonPrimitive(id));
        }
        JsonObject params = new JsonObject();
        params.add("device_ids", ids);
        String requestId = UUID.randomUUID().toString();
        JsonObject request = new JsonObject();
        request.addProperty("request_id", requestId);
        request.addProperty("operation", "lighting.acquire");
        request.add("params", params);
        writeMessage(output, REQUEST, request, negotiatedMinor);
        JsonObject response = readExpectedResponse(input, requestId);
        if (response.has("ok") && !response.get("ok").getAsBoolean()) {
            return Collections.emptySet();
        }
        JsonObject result = response.has("result") && response.get("result").isJsonObject()
                ? response.getAsJsonObject("result") : new JsonObject();
        if (result.has("granted_device_ids") && result.get("granted_device_ids").isJsonArray()) {
            Set<String> granted = new HashSet<String>();
            for (JsonElement id : result.getAsJsonArray("granted_device_ids")) {
                granted.add(id.getAsString());
            }
            return granted;
        }
        return Collections.emptySet();
    }

    private static JsonObject readExpectedResponse(DataInputStream input, String requestId) throws IOException {
        long deadline = System.currentTimeMillis() + REQUEST_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            Message message = readMessage(input);
            if (message.type == RESPONSE && requestId.equals(stringValue(message.json, "request_id"))) {
                return message.json;
            }
            if (message.type == ERROR) {
                throw new IOException(errorDescription(message.json));
            }
            if (message.type != DEVICE_LIST && message.type != DEVICE_CHANGED && message.type != PONG) {
                throw new IOException("MineLights Server sent an unexpected response.");
            }
        }
        throw new IOException("MineLights Server response timed out.");
    }

    private static List<DeviceDescriptor> parseDevices(JsonObject snapshot) {
        List<DeviceDescriptor> devices = new ArrayList<DeviceDescriptor>();
        if (!snapshot.has("devices") || !snapshot.get("devices").isJsonArray()) {
            return devices;
        }
        for (JsonElement deviceElement : snapshot.getAsJsonArray("devices")) {
            if (!deviceElement.isJsonObject()) {
                continue;
            }
            JsonObject object = deviceElement.getAsJsonObject();
            String id = stringValue(object, "device_id");
            if (id == null || id.isEmpty()) {
                continue;
            }
            DeviceDescriptor device = new DeviceDescriptor(id,
                    defaultString(stringValue(object, "name"), "Unknown device"),
                    defaultString(stringValue(object, "vendor"), ""),
                    defaultString(stringValue(object, "integration"), "MineLights Server"),
                    stringValue(object, "provider"), stringValue(object, "legacy_device_key"));
            if (object.has("elements") && object.get("elements").isJsonArray()) {
                for (JsonElement elementValue : object.getAsJsonArray("elements")) {
                    if (!elementValue.isJsonObject()) {
                        continue;
                    }
                    JsonObject element = elementValue.getAsJsonObject();
                    if (!element.has("element_id")) {
                        continue;
                    }
                    long elementId;
                    try {
                        elementId = element.get("element_id").getAsLong();
                    } catch (RuntimeException ignored) {
                        continue;
                    }
                    if (elementId < 0 || elementId > 0xffffffffL) {
                        continue;
                    }
                    device.elements.add(new ElementDescriptor(elementId,
                            firstString(element, "key", "key_name", "zone", "name")));
                }
            }
            if (device.elements.isEmpty() && object.has("key_map") && object.get("key_map").isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("key_map").entrySet()) {
                    try {
                        long elementId = entry.getValue().getAsLong();
                        if (elementId >= 0 && elementId <= 0xffffffffL) {
                            device.elements.add(new ElementDescriptor(elementId, entry.getKey()));
                        }
                    } catch (RuntimeException ignored) {
                    }
                }
            }
            devices.add(device);
        }
        return devices;
    }

    public void setDeviceChangeListener(DeviceChangeListener listener) {
        this.deviceChangeListener = listener;
    }

    public List<DeviceDescriptor> getDevices() {
        return devices;
    }

    public boolean isConnected() {
        return connected && !closed.get();
    }

    public boolean hasLease(String deviceId) {
        return leasedDeviceIds.contains(deviceId);
    }

    public void sendFrame(Map<String, Map<Long, RGBColorDto>> targets) throws IOException {
        if (!isConnected() || targets.isEmpty()) {
            return;
        }
        JsonObject frame = new JsonObject();
        frame.addProperty("sequence", frameSequence);
        frameSequence = (frameSequence + 1L) & 0xffffffffL;
        frame.addProperty("ttl_ms", 1000);
        JsonArray targetArray = new JsonArray();
        for (Map.Entry<String, Map<Long, RGBColorDto>> targetEntry : targets.entrySet()) {
            if (!leasedDeviceIds.contains(targetEntry.getKey())) {
                continue;
            }
            JsonObject target = new JsonObject();
            target.addProperty("device_id", targetEntry.getKey());
            JsonArray colors = new JsonArray();
            for (Map.Entry<Long, RGBColorDto> colorEntry : targetEntry.getValue().entrySet()) {
                RGBColorDto color = colorEntry.getValue();
                JsonObject colorJson = new JsonObject();
                colorJson.addProperty("element_id", colorEntry.getKey());
                colorJson.addProperty("r", clamp(color.r));
                colorJson.addProperty("g", clamp(color.g));
                colorJson.addProperty("b", clamp(color.b));
                colors.add(colorJson);
            }
            target.add("colors", colors);
            targetArray.add(target);
        }
        frame.add("targets", targetArray);
        if (targetArray.size() > 0) {
            writeMessage(output, LIGHTING_FRAME, frame, negotiatedMinor);
        }
    }

    private void readLoop() {
        while (isConnected()) {
            try {
                Message message = readMessage(input);
                if (message.type == DEVICE_CHANGED || message.type == DEVICE_LIST) {
                    List<DeviceDescriptor> updated = parseDevices(message.json);
                    devices = Collections.unmodifiableList(updated);
                    DeviceChangeListener listener = deviceChangeListener;
                    if (listener != null) {
                        listener.onDevicesChanged(this, updated);
                    }
                } else if (message.type == RESPONSE) {
                    ArrayBlockingQueue<JsonObject> pending = pendingRequests.get(stringValue(message.json, "request_id"));
                    if (pending != null) pending.offer(message.json);
                } else if (message.type == PING) {
                    JsonObject pong = new JsonObject();
                    pong.add("nonce", message.json.get("nonce"));
                    writeMessage(output, PONG, pong, negotiatedMinor);
                } else if (message.type == ERROR) {
                    if ("LEASE_REQUIRED".equals(stringValue(message.json, "error_code"))) {
                        leasedDeviceIds.clear();
                    } else {
                        connected = false;
                        break;
                    }
                }
            } catch (SocketTimeoutException ignored) {
                // Heartbeats continue on their own thread; an idle reader is normal.
            } catch (IOException e) {
                connected = false;
                break;
            }
        }
        closeQuietly();
    }

    private void heartbeatLoop() {
        long lastLeaseAttempt = System.currentTimeMillis();
        while (isConnected()) {
            try {
                Thread.sleep(2000);
                JsonObject ping = new JsonObject();
                ping.addProperty("nonce", UUID.randomUUID().toString());
                writeMessage(output, PING, ping, negotiatedMinor);
                if (System.currentTimeMillis() - lastLeaseAttempt >= 5000 && !desiredDeviceIds.isEmpty()) {
                    retryLeases();
                    lastLeaseAttempt = System.currentTimeMillis();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (IOException e) {
                connected = false;
                break;
            }
        }
        closeQuietly();
    }

    private JsonObject request(String operation, JsonObject params) throws IOException, InterruptedException {
        String id = UUID.randomUUID().toString();
        ArrayBlockingQueue<JsonObject> response = new ArrayBlockingQueue<JsonObject>(1);
        pendingRequests.put(id, response);
        try {
            JsonObject request = new JsonObject();
            request.addProperty("request_id", id);
            request.addProperty("operation", operation);
            request.add("params", params);
            writeMessage(output, REQUEST, request, negotiatedMinor);
            JsonObject result = response.poll(3, TimeUnit.SECONDS);
            if (result == null) throw new IOException("MineLights Server request timed out");
            return result;
        } finally {
            pendingRequests.remove(id);
        }
    }

    private void retryLeases() throws IOException, InterruptedException {
        JsonArray ids = new JsonArray();
        for (String id : desiredDeviceIds) ids.add(new JsonPrimitive(id));
        JsonObject params = new JsonObject();
        params.add("device_ids", ids);
        JsonObject response = request("lighting.acquire", params);
        Set<String> granted = new HashSet<String>();
        if (response.has("ok") && response.get("ok").getAsBoolean()
                && response.has("result") && response.get("result").isJsonObject()) {
            JsonObject result = response.getAsJsonObject("result");
            if (result.has("granted_device_ids") && result.get("granted_device_ids").isJsonArray()) {
                for (JsonElement id : result.getAsJsonArray("granted_device_ids")) granted.add(id.getAsString());
            }
        }
        synchronized (leasedDeviceIds) {
            leasedDeviceIds.clear();
            leasedDeviceIds.addAll(granted);
        }
    }

    public boolean matchesLaunchId(String expectedLaunchId) {
        if (expectedLaunchId == null) return false;
        try {
            JsonObject response = request("server.info", new JsonObject());
            if (!response.has("ok") || !response.get("ok").getAsBoolean()
                    || !response.has("result") || !response.get("result").isJsonObject()) return false;
            return expectedLaunchId.equals(stringValue(response.getAsJsonObject("result"), "launch_id"));
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Older V2 servers omit the legacy setting; unknown is treated as false. */
    public boolean legacyLocalAccessEnabled() {
        try {
            JsonObject response = request("server.info", new JsonObject());
            if (!response.has("ok") || !response.get("ok").getAsBoolean()
                    || !response.has("result") || !response.get("result").isJsonObject()) return false;
            JsonObject result = response.getAsJsonObject("result");
            return result.has("legacy_v1_enabled") && result.get("legacy_v1_enabled").isJsonPrimitive()
                    && result.getAsJsonPrimitive("legacy_v1_enabled").isBoolean()
                    && result.get("legacy_v1_enabled").getAsBoolean();
        } catch (IOException e) { return false; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
    }

    /** Update this authenticated session's provider interest; old V2 servers may ignore it. */
    public void setRequestedIntegrations(Set<String> exclusions) {
        JsonArray providers = new JsonArray();
        for (String provider : V2DevicePolicy.requestedProviders(exclusions)) providers.add(new JsonPrimitive(provider));
        JsonObject params = new JsonObject();
        params.add("providers", providers);
        try { request("integrations.set", params); }
        catch (IOException ignored) { }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    /** Ask the authenticated server to shut down only if no other client is connected. */
    public String requestServerShutdown() {
        try {
            JsonObject response = request("server.shutdown", new JsonObject());
            if (!response.has("ok") || !response.get("ok").getAsBoolean()
                    || !response.has("result") || !response.get("result").isJsonObject()) return "unsupported";
            JsonObject result = response.getAsJsonObject("result");
            if (result.has("accepted") && result.get("accepted").getAsBoolean()) return "accepted";
            if ("legacy_enabled".equals(stringValue(result, "reason"))) return "legacy_enabled";
            if (result.has("pending_for_ms") && result.get("pending_for_ms").getAsInt() > 0) return "pending";
            return "other_clients";
        } catch (IOException e) {
            return "unavailable";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "unavailable";
        }
    }

    private void closeQuietly() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        connected = false;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
        if (Thread.currentThread() != readerThread) {
            readerThread.interrupt();
        }
        if (Thread.currentThread() != heartbeatThread) {
            heartbeatThread.interrupt();
        }
    }

    @Override
    public void close() {
        if (closed.get()) {
            return;
        }
        try {
            Set<String> leased = new HashSet<String>(leasedDeviceIds);
            if (!leased.isEmpty()) {
                JsonArray ids = new JsonArray();
                for (String id : leased) {
                    ids.add(new JsonPrimitive(id));
                }
                JsonObject params = new JsonObject();
                params.add("device_ids", ids);
                JsonObject release = new JsonObject();
                release.addProperty("request_id", UUID.randomUUID().toString());
                release.addProperty("operation", "lighting.release");
                release.add("params", params);
                writeMessage(output, REQUEST, release, negotiatedMinor);
            }
            JsonObject goodbye = new JsonObject();
            goodbye.addProperty("reason", "client_disconnect");
            writeMessage(output, GOODBYE, goodbye, negotiatedMinor);
        } catch (IOException ignored) {
        }
        closeQuietly();
    }

    private static void writeMessage(DataOutputStream output, int type, JsonObject json) throws IOException {
        writeMessage(output, type, json, PROTOCOL_MINOR);
    }

    private static void writeMessage(DataOutputStream output, int type, JsonObject json, int minor) throws IOException {
        byte[] payload = GSON.toJson(json).getBytes(StandardCharsets.UTF_8);
        int max = type == LIGHTING_FRAME || type == DEVICE_LIST || type == DEVICE_CHANGED
                ? MAX_PAYLOAD_BYTES : MAX_CONTROL_BYTES;
        if (payload.length > max) {
            throw new IOException("MineLights V2 message exceeds the protocol size limit.");
        }
        synchronized (output) {
            output.write(new byte[] { 'M', 'L', 'V', '2' });
            output.writeShort(PROTOCOL_MAJOR);
            output.writeShort(minor);
            output.writeShort(type);
            output.writeShort(0);
            output.writeInt(payload.length);
            output.write(payload);
            output.flush();
        }
    }

    private static Message readMessage(DataInputStream input) throws IOException {
        byte[] header = new byte[HEADER_LENGTH];
        input.readFully(header);
        ByteBuffer bytes = ByteBuffer.wrap(header);
        if (bytes.get() != 'M' || bytes.get() != 'L' || bytes.get() != 'V' || bytes.get() != '2') {
            throw new IOException("MineLights Server sent an invalid V2 frame header.");
        }
        int major = bytes.getShort() & 0xffff;
        int minor = bytes.getShort() & 0xffff;
        int type = bytes.getShort() & 0xffff;
        int flags = bytes.getShort() & 0xffff;
        int length = bytes.getInt();
        if (major != PROTOCOL_MAJOR || minor > PROTOCOL_MINOR || flags != 0
                || length < 0 || length > MAX_PAYLOAD_BYTES) {
            throw new IOException("MineLights Server sent an unsupported or oversized V2 frame.");
        }
        if (type != LIGHTING_FRAME && type != DEVICE_LIST && type != DEVICE_CHANGED && length > MAX_CONTROL_BYTES) {
            throw new IOException("MineLights Server sent an oversized control message.");
        }
        byte[] payload = new byte[length];
        input.readFully(payload);
        JsonObject json;
        try {
            json = new JsonParser().parse(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("MineLights Server sent invalid JSON in a V2 message.", e);
        }
        return new Message(major, minor, type, json);
    }

    private static boolean contains(JsonArray array, String expected) {
        for (JsonElement element : array) {
            if (expected.equals(element.getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static String stringValue(JsonObject json, String key) {
        try {
            return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static int integerValue(JsonObject json, String key, int fallback) {
        try {
            return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsInt() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static String firstString(JsonObject json, String... keys) {
        for (String key : keys) {
            String value = stringValue(json, key);
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static String errorDescription(JsonObject json) {
        String code = stringValue(json, "error_code");
        return "MineLights Server error" + (code == null ? "." : " (" + code + ").");
    }

    private static byte[] decodeBase64Url(String value) throws IOException {
        try {
            if (value == null || value.contains("=")) {
                throw new IllegalArgumentException("Expected unpadded base64url.");
            }
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            if (decoded.length != 32 || !encodeBase64Url(decoded).equals(value)) {
                throw new IllegalArgumentException("Expected canonical base64url for 32 bytes.");
            }
            return decoded;
        } catch (IllegalArgumentException e) {
            throw new IOException("MineLights credential is malformed.", e);
        }
    }

    private static void requireClientId(String clientId) throws IOException {
        try {
            UUID parsed = UUID.fromString(clientId);
            if (!parsed.toString().equals(clientId)) {
                throw new IllegalArgumentException("Non-canonical UUID.");
            }
        } catch (RuntimeException e) {
            throw new IOException("MineLights client ID is invalid.", e);
        }
    }

    private static String encodeBase64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
        }
        return result.toString();
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static String safeThreadName(String value) {
        return value == null ? "server" : value.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int REQUEST_TIMEOUT_MS = 5000;

    public static final class DeviceDescriptor {
        public final String deviceId;
        public final String name;
        public final String vendor;
        public final String integration;
        public final String provider;
        public final String legacyDeviceKey;
        public final List<ElementDescriptor> elements = new ArrayList<ElementDescriptor>();

        DeviceDescriptor(String deviceId, String name, String vendor, String integration, String provider, String legacyDeviceKey) {
            this.deviceId = deviceId;
            this.name = name;
            this.vendor = vendor;
            this.integration = integration;
            this.provider = provider;
            this.legacyDeviceKey = legacyDeviceKey;
        }
    }

    public static final class ElementDescriptor {
        public final long elementId;
        public final String keyName;

        ElementDescriptor(long elementId, String keyName) {
            this.elementId = elementId;
            this.keyName = keyName;
        }
    }

    public interface DeviceChangeListener {
        void onDevicesChanged(V2ClientConnection connection, List<DeviceDescriptor> devices);
    }

    private static final class Message {
        final int major;
        final int minor;
        final int type;
        final JsonObject json;

        Message(int major, int minor, int type, JsonObject json) {
            this.major = major;
            this.minor = minor;
            this.type = type;
            this.json = json;
        }
    }

    private static final class Handshake {
        final String serverNonce;
        final int selectedMinor;

        Handshake(String serverNonce, int selectedMinor) {
            this.serverNonce = serverNonce;
            this.selectedMinor = selectedMinor;
        }
    }

    private static final class AuthFailureException extends IOException {
        final String errorCode;

        AuthFailureException(String errorCode) {
            super("MineLights Server rejected authentication (" + errorCode + ").");
            this.errorCode = errorCode;
        }
    }
}
