package megabytesme.minelights.network;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Manual cross-process lock test; run with javac/java alongside its production peer. */
public final class ServerExecutableUpdateLockTest {
    private ServerExecutableUpdateLockTest() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 3 && "--child".equals(args[0])) {
            childAttempt(Paths.get(args[1]));
            return;
        }

        Path directory = Files.createTempDirectory("minelights-update-lock-test-");
        Path lockFile = directory.resolve("server-operation.lock");
        try {
            sameJvmConcurrentCallersSerialize(lockFile);
            ServerExecutableUpdateLock lock = ServerExecutableUpdateLock.tryAcquire(lockFile);
            if (lock == null) throw new AssertionError("first process could not acquire the lock");
            try {
                equal("busy", childAttemptInProcess(lockFile));
            } finally {
                lock.close();
            }
            equal("acquired", childAttemptInProcess(lockFile));
        } finally {
            Files.deleteIfExists(lockFile);
            Files.deleteIfExists(directory);
        }
    }

    private static void sameJvmConcurrentCallersSerialize(final Path lockFile) throws Exception {
        final CountDownLatch acquired = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        Thread first = new Thread(new Runnable() {
            @Override
            public void run() {
                try (ServerExecutableUpdateLock lock = ServerExecutableUpdateLock.tryAcquire(lockFile)) {
                    if (lock == null) throw new AssertionError("first concurrent caller did not acquire the lock");
                    acquired.countDown();
                    if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("lock release timed out");
                } catch (Throwable e) {
                    failure.set(e);
                    acquired.countDown();
                }
            }
        }, "MineLights-update-lock-test-first");
        first.start();
        if (!acquired.await(5, TimeUnit.SECONDS)) throw new AssertionError("first caller did not acquire the lock");
        try {
            if (failure.get() != null) throw new AssertionError("first caller failed", failure.get());
            if (ServerExecutableUpdateLock.tryAcquire(lockFile) != null) {
                throw new AssertionError("second concurrent caller also acquired the lock");
            }
        } finally {
            release.countDown();
            first.join(5000);
            if (first.isAlive()) throw new AssertionError("first caller did not release the lock");
        }
        if (failure.get() != null) throw new AssertionError("first caller failed", failure.get());
    }

    private static void childAttempt(Path lockFile) throws Exception {
        try (ServerExecutableUpdateLock lock = ServerExecutableUpdateLock.tryAcquire(lockFile)) {
            System.out.println(lock == null ? "busy" : "acquired");
        }
    }

    private static String childAttemptInProcess(Path lockFile) throws Exception {
        String javaName = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
        Path java = new File(new File(System.getProperty("java.home"), "bin"), javaName).toPath();
        Process process = new ProcessBuilder(java.toString(), "-cp", System.getProperty("java.class.path"),
                ServerExecutableUpdateLockTest.class.getName(), "--child", lockFile.toString(), "unused")
                .redirectErrorStream(true).start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() > 0) output.append('\n');
                output.append(line);
            }
        }
        process.waitFor();
        if (process.exitValue() != 0) throw new AssertionError("child lock probe failed: " + output);
        return output.toString().trim();
    }

    private static void equal(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }
}
