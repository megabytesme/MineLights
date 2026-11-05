package megabytesme.minelights.network;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

/** Validates release digests before download and verifies the downloaded bytes. */
public final class ServerReleaseIntegrity {
    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private ServerReleaseIntegrity() { }

    public static boolean isValidDigest(String digest) {
        return digest != null && SHA256.matcher(digest).matches();
    }

    public static void requireValidDigest(String digest) throws IOException {
        if (!isValidDigest(digest)) throw new IOException("MineLights Server release has no valid SHA-256 digest.");
    }

    public static void verify(Path file, String expected) throws IOException {
        requireValidDigest(expected);
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is unavailable.", e);
        }
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        byte[] actual = digest.digest();
        char[] encoded = new char[actual.length * 2];
        for (int i = 0; i < actual.length; i++) {
            int value = actual[i] & 0xff;
            encoded[i * 2] = HEX[value >>> 4];
            encoded[i * 2 + 1] = HEX[value & 0x0f];
        }
        if (!new String(encoded).equalsIgnoreCase(expected)) {
            throw new IOException("MineLights Server SHA-256 did not match the release digest.");
        }
    }
}
