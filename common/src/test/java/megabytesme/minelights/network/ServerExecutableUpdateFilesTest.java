package megabytesme.minelights.network;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Manual, dependency-free staging/install test; run with javac/java on the production peers. */
public final class ServerExecutableUpdateFilesTest {
    private ServerExecutableUpdateFilesTest() { }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("minelights-server-update-files-test-");
        try {
            successfulInstallConsumesStage(directory);
            settingsDisabledAtReplacementBoundaryRetainsStage(directory);
            settingsDisabledMidDownloadRemovesPartial(directory);
            serverStartingDuringTransferRetainsStage(directory);
            failedReplacementRetainsVerifiedStage(directory);
        } finally {
            deleteTree(directory);
        }
    }

    private static void successfulInstallConsumesStage(Path directory) throws Exception {
        Path staged = directory.resolve("Install/MineLights.exe.new");
        Path executable = directory.resolve("Install/MineLights.exe");
        byte[] release = new byte[] {6, 7, 8};
        Files.createDirectories(staged.getParent());
        Files.write(staged, release);
        ServerExecutableUpdateFiles.installVerified(staged, executable, sha256(release));
        bytesEqual(release, Files.readAllBytes(executable));
        if (Files.exists(staged)) throw new AssertionError("successful install left a stale staged file");
    }

    private static void failedReplacementRetainsVerifiedStage(Path directory) throws Exception {
        Path staged = directory.resolve("Blocked/MineLights.exe.new");
        Path executable = directory.resolve("Blocked/MineLights.exe");
        byte[] release = new byte[] {10, 11, 12};
        Files.createDirectories(executable);
        Files.write(staged, release);
        boolean rejected = false;
        try {
            ServerExecutableUpdateFiles.installVerified(staged, executable, sha256(release));
        } catch (java.io.IOException expected) {
            rejected = true;
        }
        if (!rejected) throw new AssertionError("replacement unexpectedly succeeded over a directory");
        bytesEqual(release, Files.readAllBytes(staged));
    }

    private static void settingsDisabledAtReplacementBoundaryRetainsStage(Path directory) throws Exception {
        Path staged = directory.resolve("SettingsDisabled/MineLights.exe.new");
        Path executable = directory.resolve("SettingsDisabled/MineLights.exe");
        byte[] release = new byte[] {13, 14, 15};
        byte[] old = new byte[] {1, 1, 1};
        Files.createDirectories(staged.getParent());
        Files.write(staged, release);
        Files.write(executable, old);
        final AtomicBoolean installAllowed = new AtomicBoolean(true);
        boolean installed = ServerExecutableUpdateFiles.installVerified(staged, executable,
                sha256(release), new ServerExecutableUpdateFiles.InstallGuard() {
                    @Override
                    public boolean mayReplace() {
                        installAllowed.set(false); // The setting changed while the candidate was prepared.
                        return installAllowed.get();
                    }
                });
        if (installed) throw new AssertionError("replacement ignored the disabled install setting");
        bytesEqual(old, Files.readAllBytes(executable));
        bytesEqual(release, Files.readAllBytes(staged));
    }

    private static void serverStartingDuringTransferRetainsStage(Path directory) throws Exception {
        Path staged = directory.resolve("ServerStarted/MineLights.exe.new");
        Path executable = directory.resolve("ServerStarted/MineLights.exe");
        byte[] release = new byte[] {16, 17, 18, 19, 20, 21};
        byte[] old = new byte[] {2, 2, 2};
        Files.createDirectories(staged.getParent());
        Files.write(executable, old);
        final AtomicBoolean serverRunning = new AtomicBoolean(false);
        final AtomicInteger chunksTransferred = new AtomicInteger();
        try (InputStream input = chunkedInput(release)) {
            ServerExecutableUpdateFiles.downloadVerified(input, staged, sha256(release),
                    new ServerExecutableUpdateFiles.DownloadGuard() {
                        @Override
                        public boolean mayContinue() {
                            return true;
                        }
                    }, new ServerExecutableUpdateFiles.DownloadListener() {
                        @Override
                        public void transferStarted() { }

                        @Override
                        public void bytesTransferred() {
                            if (chunksTransferred.incrementAndGet() == 1) serverRunning.set(true);
                        }
                    });
        }
        if (chunksTransferred.get() < 2) throw new AssertionError("download did not span multiple chunks");
        boolean installed = ServerExecutableUpdateFiles.installVerified(staged, executable,
                sha256(release), new ServerExecutableUpdateFiles.InstallGuard() {
                    @Override
                    public boolean mayReplace() {
                        return !serverRunning.get();
                    }
                });
        if (installed) throw new AssertionError("replacement proceeded after a server appeared");
        if (!serverRunning.get()) throw new AssertionError("test did not simulate a process race");
        bytesEqual(old, Files.readAllBytes(executable));
        bytesEqual(release, Files.readAllBytes(staged));
        if (!ServerDownloadNotificationState.RESTART_REQUIRED.equals(
                new ServerDownloadNotificationState().restartRequired(true))) {
            throw new AssertionError("running shared server did not produce the restart-required notice");
        }
    }

    private static void settingsDisabledMidDownloadRemovesPartial(Path directory) throws Exception {
        Path staged = directory.resolve("DisabledDuringDownload/MineLights.exe.new");
        byte[] release = new byte[] {22, 23, 24, 25, 26, 27};
        final AtomicBoolean installAllowed = new AtomicBoolean(true);
        final AtomicInteger chunksTransferred = new AtomicInteger();
        boolean rejected = false;
        try (InputStream input = chunkedInput(release)) {
            ServerExecutableUpdateFiles.downloadVerified(input, staged, sha256(release),
                    new ServerExecutableUpdateFiles.DownloadGuard() {
                        @Override
                        public boolean mayContinue() {
                            return installAllowed.get();
                        }
                    }, new ServerExecutableUpdateFiles.DownloadListener() {
                        @Override
                        public void transferStarted() { }

                        @Override
                        public void bytesTransferred() {
                            if (chunksTransferred.incrementAndGet() == 1) installAllowed.set(false);
                        }
                    });
        } catch (java.io.IOException expected) {
            rejected = true;
        }
        if (!rejected) throw new AssertionError("download continued after install permission was disabled");
        if (chunksTransferred.get() != 1) throw new AssertionError("setting change was not mid-transfer");
        if (Files.exists(staged)) throw new AssertionError("partial download was left at the verified stage path");
    }

    private static InputStream chunkedInput(final byte[] bytes) {
        return new InputStream() {
            private int offset;

            @Override
            public int read() {
                return offset == bytes.length ? -1 : bytes[offset++] & 0xff;
            }

            @Override
            public int read(byte[] buffer, int start, int length) {
                if (offset == bytes.length) return -1;
                int count = Math.min(2, Math.min(length, bytes.length - offset));
                System.arraycopy(bytes, offset, buffer, start, count);
                offset += count;
                return count;
            }
        };
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) hex.append(String.format("%02x", value & 0xff));
        return hex.toString();
    }

    private static void bytesEqual(byte[] expected, byte[] actual) {
        if (!java.util.Arrays.equals(expected, actual)) throw new AssertionError("file bytes did not match");
    }

    private static void deleteTree(Path root) throws Exception {
        Files.walkFileTree(root, new java.nio.file.SimpleFileVisitor<Path>() {
            @Override
            public java.nio.file.FileVisitResult visitFile(Path file,
                    java.nio.file.attribute.BasicFileAttributes attributes) throws java.io.IOException {
                Files.deleteIfExists(file);
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult postVisitDirectory(Path directory,
                    java.io.IOException failure) throws java.io.IOException {
                if (failure != null) throw failure;
                Files.deleteIfExists(directory);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }
}
