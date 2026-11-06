package megabytesme.minelights.network;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

/** Cross-process lock for MineLights Server updates and auto-starts on one user profile. */
public final class ServerExecutableUpdateLock implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;

    private ServerExecutableUpdateLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    /**
     * Attempts to serialize shared-service updates and auto-starts across loader instances.
     * Returns {@code null} when another JVM (or thread in this JVM) already holds the lock.
     */
    public static ServerExecutableUpdateLock tryAcquire(Path lockFilePath) throws IOException {
        if (lockFilePath == null || lockFilePath.getFileName() == null) {
            throw new IOException("MineLights Server operation lock path is invalid.");
        }
        Path parent = lockFilePath.toAbsolutePath().normalize().getParent();
        if (parent == null) throw new IOException("MineLights Server operation lock directory is unavailable.");
        Files.createDirectories(parent);
        Path realParent = parent.toRealPath();
        String name = lockFilePath.getFileName().toString().toLowerCase(Locale.ROOT);
        Path lockPath = realParent.resolve(name);
        FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException alreadyHeldInThisJvm) {
                channel.close();
                return null;
            }
            if (lock == null) {
                channel.close();
                return null;
            }
            return new ServerExecutableUpdateLock(channel, lock);
        } catch (IOException | RuntimeException failure) {
            channel.close();
            throw failure;
        }
    }

    public boolean isValid() {
        return lock.isValid();
    }

    @Override
    public void close() throws IOException {
        try {
            if (lock.isValid()) lock.release();
        } finally {
            channel.close();
        }
    }
}
