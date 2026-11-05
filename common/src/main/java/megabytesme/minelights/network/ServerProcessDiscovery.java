package megabytesme.minelights.network;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Detects an existing Windows server without sending any legacy protocol traffic. */
public final class ServerProcessDiscovery {
    private static long nextCheck;
    private static boolean cached = true;

    private ServerProcessDiscovery() {}

    public static synchronized boolean isRunning() {
        long now = System.nanoTime();
        if (now < nextCheck) return cached;
        nextCheck = now + TimeUnit.SECONDS.toNanos(3);
        Process process = null;
        try {
            String systemRoot = System.getenv("SystemRoot");
            if (systemRoot == null || systemRoot.trim().isEmpty()) return cached = true;
            Path root = Paths.get(systemRoot);
            if (!root.isAbsolute()) return cached = true;
            String wow64 = System.getenv("PROCESSOR_ARCHITEW6432");
            Path executable = root.resolve(wow64 == null || wow64.isEmpty() ? "System32" : "Sysnative")
                    .resolve("tasklist.exe");
            if (!Files.isRegularFile(executable)) return cached = true;
            process = new ProcessBuilder(executable.toString(), "/FI", "IMAGENAME eq MineLights.exe", "/FO", "CSV", "/NH")
                    .redirectErrorStream(true).start();
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return cached = true;
            }
            if (process.exitValue() != 0) return cached = true;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.toLowerCase(Locale.ROOT).startsWith("\"minelights.exe\",")) return cached = true;
                }
            }
            return cached = false;
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            return cached = true;
        }
    }
}
