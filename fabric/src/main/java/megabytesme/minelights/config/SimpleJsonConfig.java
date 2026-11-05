package megabytesme.minelights.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import megabytesme.minelights.MineLightsClient;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Serializes configuration writes and protects any persisted V2 credentials. */
public class SimpleJsonConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Object FILE_LOCK = new Object();
    public static Object configLock() { return FILE_LOCK; }
    private final String modId;
    private boolean recoveryInUse;

    public SimpleJsonConfig(String modId) { this.modId = modId; }

    public <T> T load(Class<T> configClass, T defaultConfig) {
        synchronized (FILE_LOCK) {
            Path path = getConfigFile();
            Path recovery = recoveryPath(path);
            try {
                Path ready = recoveryReadyPath(recovery);
                if (!Files.exists(recovery, LinkOption.NOFOLLOW_LINKS) && Files.exists(ready, LinkOption.NOFOLLOW_LINKS)) {
                    T intact = read(path, configClass);
                    Files.delete(ready);
                    return intact;
                }
                if (Files.exists(recovery, LinkOption.NOFOLLOW_LINKS)) {
                    if (recoveryIsComplete(recovery)) {
                        T restored = read(recovery, configClass);
                        recoveryInUse = true;
                        MineLightsClient.LOGGER.warn("Using the preserved MineLights configuration recovery copy.");
                        return restored;
                    }
                    // A backup without a verified ready marker was interrupted before
                    // replacement began. Delete it only after validating the primary.
                    T intact = read(path, configClass);
                    Files.delete(recovery);
                    Files.deleteIfExists(recoveryReadyPath(recovery));
                    MineLightsClient.LOGGER.warn("Discarded an incomplete recovery copy after validating the original configuration.");
                    return intact;
                }
                if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                    save(defaultConfig);
                    return defaultConfig;
                }
                return read(path, configClass);
            } catch (IOException e) {
                throw new IllegalStateException("Could not safely read MineLights configuration: " + e.getMessage(), e);
            }
        }
    }

    public <T> void save(T config) {
        synchronized (FILE_LOCK) {
            Path path = getConfigFile();
            Path temporary = null;
            try {
                Files.createDirectories(path.getParent());
                if ((Files.exists(recoveryPath(path), LinkOption.NOFOLLOW_LINKS)
                        || Files.exists(recoveryReadyPath(recoveryPath(path)), LinkOption.NOFOLLOW_LINKS)) && !recoveryInUse) {
                    throw new IOException("A recovery copy exists; load and review it before saving.");
                }
                if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Configuration path is not a regular file.");
                }
                if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !protectFile(path)
                        && containsCredentials(parseJson(readBytes(path)))) throw new UnprotectedCredentialsException();
                JsonElement json = GSON.toJsonTree(config);
                boolean sensitive = containsCredentials(json);
                temporary = Files.createTempFile(path.getParent(), ".minelights-", ".tmp");
                boolean protectedFile = protectFile(temporary);
                if (!protectedFile && sensitive) throw new UnprotectedCredentialsException();
                byte[] bytes = GSON.toJson(json).getBytes(StandardCharsets.UTF_8);
                writeAndSync(temporary, bytes);
                try {
                    moveAtomically(temporary, path);
                } catch (AtomicMoveNotSupportedException e) {
                    replaceWithRecovery(temporary, path);
                }
                temporary = null;
                if (recoveryInUse) {
                    Files.deleteIfExists(recoveryPath(path));
                    Files.deleteIfExists(recoveryReadyPath(recoveryPath(path)));
                    recoveryInUse = false;
                }
            } catch (IOException e) {
                throw new IllegalStateException("Could not safely save MineLights configuration: " + e.getMessage(), e);
            } finally {
                if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            }
        }
    }

    private <T> T read(Path path, Class<T> configClass) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Configuration path is not a regular file.");
        boolean protectedFile = protectFile(path);
        JsonElement json;
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS);
                InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            json = new JsonParser().parse(reader);
        } catch (RuntimeException e) {
            throw new IOException("Configuration is invalid JSON.", e);
        }
        if (json == null || !json.isJsonObject()) throw new IOException("Configuration is not a JSON object.");
        if (!protectedFile && containsCredentials(json)) throw new UnprotectedCredentialsException();
        try { return GSON.fromJson(json, configClass); }
        catch (RuntimeException e) { throw new IOException("Configuration fields are invalid.", e); }
    }

    private void replaceWithRecovery(Path temporary, Path path) throws IOException {
        Path recovery = recoveryPath(path);
        boolean backupCreated = false;
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.exists(recovery, LinkOption.NOFOLLOW_LINKS)) {
                if (!recoveryInUse) throw new IOException("A recovery copy already exists at " + recovery + "; preserve it before saving on this filesystem.");
            } else {
                Files.createFile(recovery);
                backupCreated = true;
                boolean protectedFile = protectFile(recovery);
                byte[] previous = readBytes(path);
                if (!protectedFile && containsCredentials(parseJson(previous))) throw new UnprotectedCredentialsException();
                writeAndSync(recovery, previous);
                Path ready = recoveryReadyPath(recovery);
                Files.createFile(ready);
                writeAndSync(ready, sha256(previous).getBytes(StandardCharsets.US_ASCII));
            }
        }
        try {
            moveWithoutAtomicGuarantee(temporary, path);
            if (backupCreated) {
                Files.deleteIfExists(recovery);
                Files.deleteIfExists(recoveryReadyPath(recovery));
            }
        } catch (IOException e) {
            throw new IOException("Replacement failed; the previous configuration is preserved at " + recovery, e);
        }
    }

    protected void moveAtomically(Path source, Path target) throws IOException {
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Verifies that a future credential write can be protected before registration. */
    public void ensureCredentialStorageAvailable() throws IOException {
        synchronized (FILE_LOCK) {
            Path path = getConfigFile();
            Files.createDirectories(path.getParent());
            Path probe = Files.createTempFile(path.getParent(), ".minelights-permission-", ".tmp");
            try {
                if (!protectFile(probe)) throw new UnprotectedCredentialsException();
            } finally { Files.deleteIfExists(probe); }
        }
    }

    protected void moveWithoutAtomicGuarantee(Path source, Path target) throws IOException {
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Returns false only where the filesystem supports neither ACLs nor POSIX modes. */
    protected boolean protectFile(Path path) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
            Set<PosixFilePermission> permissions = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            PosixFileAttributeView view = Files.getFileAttributeView(path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            if (view == null) throw new IOException("Cannot access the configuration permissions.");
            view.setPermissions(permissions);
            if (!view.readAttributes().permissions().equals(permissions)) throw new IOException("Could not make the configuration owner-only.");
            return true;
        }
        if (Files.getFileStore(path).supportsFileAttributeView("acl")) {
            AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            if (view == null) throw new IOException("Cannot access the configuration ACL.");
            UserPrincipal owner = view.getOwner();
            AclEntry entry = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner)
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build();
            view.setAcl(Collections.singletonList(entry));
            java.util.List<AclEntry> applied = view.getAcl();
            if (applied.size() != 1 || applied.get(0).type() != AclEntryType.ALLOW
                    || !applied.get(0).principal().equals(owner)
                    || !applied.get(0).permissions().containsAll(entry.permissions())) {
                throw new IOException("Could not make the configuration ACL owner-only.");
            }
            return true;
        }
        return false;
    }

    private static void writeAndSync(Path path, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }

    private static JsonElement parseJson(byte[] bytes) throws IOException {
        try { return new JsonParser().parse(new String(bytes, StandardCharsets.UTF_8)); }
        catch (RuntimeException e) { throw new IOException("Existing configuration is invalid JSON.", e); }
    }

    private static String sha256(byte[] bytes) throws IOException {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            char[] hex = "0123456789abcdef".toCharArray();
            char[] result = new char[hash.length * 2];
            for (int i = 0; i < hash.length; i++) {
                int value = hash[i] & 255;
                result[i * 2] = hex[value >>> 4];
                result[i * 2 + 1] = hex[value & 15];
            }
            return new String(result);
        } catch (NoSuchAlgorithmException e) { throw new IOException("SHA-256 is unavailable.", e); }
    }

    private static boolean recoveryIsComplete(Path recovery) throws IOException {
        Path ready = recoveryReadyPath(recovery);
        if (!Files.isRegularFile(ready, LinkOption.NOFOLLOW_LINKS)) return false;
        if (Files.size(ready) != 64) return false;
        byte[] marker = readBytes(ready);
        return sha256(readBytes(recovery)).equals(new String(marker, StandardCharsets.US_ASCII));
    }

    private static byte[] readBytes(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS);
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) out.write(buffer, 0, count);
            return out.toByteArray();
        }
    }

    private static boolean containsCredentials(JsonElement value) {
        if (value == null || value.isJsonNull()) return false;
        if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) if (containsCredentials(child)) return true;
        } else if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                String name = entry.getKey();
                JsonElement child = entry.getValue();
                if (("v2ClientSecret".equals(name) || "clientSecret".equals(name) || "pairingCode".equals(name))
                        && child != null && !child.isJsonNull() && (!child.isJsonPrimitive() || !child.getAsString().isEmpty())) return true;
                if (containsCredentials(child)) return true;
            }
        }
        return false;
    }

    private static final class UnprotectedCredentialsException extends IOException {
        UnprotectedCredentialsException() {
            super("This filesystem does not support private file permissions; move the game/config directory to a filesystem with ACLs or POSIX permissions before storing V2 credentials.");
        }
    }

    private Path getConfigFile() { return MineLightsClient.getConfigDir().resolve(modId + ".json"); }
    private static Path recoveryPath(Path path) { return path.resolveSibling(path.getFileName().toString() + ".recovery"); }
    private static Path recoveryReadyPath(Path recovery) { return recovery.resolveSibling(recovery.getFileName().toString() + ".ready"); }
}
