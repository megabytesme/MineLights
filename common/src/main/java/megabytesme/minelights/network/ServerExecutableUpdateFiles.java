package megabytesme.minelights.network;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Verified staging and replacement operations for MineLights Server executables. */
public final class ServerExecutableUpdateFiles {
    public interface InstallGuard {
        boolean mayReplace();
    }

    public interface DownloadGuard {
        boolean mayContinue();
    }

    public interface DownloadListener {
        void transferStarted();
        void bytesTransferred();
    }

    private ServerExecutableUpdateFiles() { }

    /** Copies and verifies a response directly into the durable staging path. */
    public static void downloadVerified(InputStream input, Path destination, String expectedHash,
            DownloadGuard guard, DownloadListener listener) throws IOException {
        Path parent = destination.toAbsolutePath().normalize().getParent();
        if (input == null || parent == null) throw new IOException("MineLights Server download path is unavailable.");
        Files.createDirectories(parent);
        try (OutputStream output = Files.newOutputStream(destination)) {
            if (listener != null) listener.transferStarted();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                if (guard == null || !guard.mayContinue()) {
                    throw new IOException("Automatic server installation was disabled during the download.");
                }
                output.write(buffer, 0, bytesRead);
                if (listener != null) listener.bytesTransferred();
            }
        } catch (IOException | RuntimeException failure) {
            Files.deleteIfExists(destination);
            throw failure;
        }
        try {
            ServerReleaseIntegrity.verify(destination, expectedHash);
        } catch (IOException failure) {
            Files.deleteIfExists(destination);
            throw failure;
        }
    }

    /**
     * Installs a verified staged file while retaining it if the executable cannot be replaced.
     * The separate candidate also protects the verified stage from partial-copy failures.
     */
    public static void installVerified(Path staged, Path executable, String expectedHash) throws IOException {
        if (!installVerified(staged, executable, expectedHash, new InstallGuard() {
            @Override
            public boolean mayReplace() {
                return true;
            }
        })) {
            throw new IOException("MineLights Server replacement was skipped.");
        }
    }

    /** Installs only if the caller's process/settings guard still passes immediately before replacement. */
    public static boolean installVerified(Path staged, Path executable, String expectedHash,
            InstallGuard guard) throws IOException {
        ServerReleaseIntegrity.verify(staged, expectedHash);
        Path parent = executable.toAbsolutePath().normalize().getParent();
        if (parent == null) throw new IOException("MineLights Server install directory is unavailable.");
        Files.createDirectories(parent);
        Path candidate = parent.resolve(executable.getFileName().toString() + ".installing");
        try {
            Files.copy(staged, candidate, StandardCopyOption.REPLACE_EXISTING);
            ServerReleaseIntegrity.verify(candidate, expectedHash);
            if (guard == null || !guard.mayReplace()) return false;
            moveReplacing(candidate, executable);
            ServerReleaseIntegrity.verify(executable, expectedHash);
        } finally {
            Files.deleteIfExists(candidate);
        }
        Files.deleteIfExists(staged);
        return true;
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
