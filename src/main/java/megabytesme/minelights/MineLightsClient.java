package megabytesme.minelights;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import megabytesme.minelights.config.MineLightsConfig;
import megabytesme.minelights.config.SimpleJsonConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screen.TitleScreen;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class MineLightsClient implements ClientModInitializer {
    public static final Logger LOGGER = LogManager.getLogger("MineLights");
    public static MineLightsConfig CONFIG;
    private static SimpleJsonConfig CONFIG_MANAGER;
    private static LightingManager lightingManager;
    private static Thread lightingManagerThread;
    private static Thread discoveryThread;
    public static Thread serverMonitorThread;

    public static final List<String> discoveredDevices = Collections.synchronizedList(new ArrayList<>());
    public static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    public static volatile boolean isProxyConnected = false;

    public static CountDownLatch proxyDiscoveredLatch = new CountDownLatch(1);

    private static final AtomicBoolean hasPerformedServerCheck = new AtomicBoolean(false);
    private static final String GITHUB_API_URL = "https://api.github.com/repos/megabytesme/MineLights/releases/tags/v2-server";
    private boolean titleScreenHooked = false;

    public enum DownloadStatus {
        IDLE,
        DOWNLOADING,
        VERIFYING,
        SUCCESS,
        FAILED
    }

    public static final AtomicReference<DownloadStatus> downloadStatus = new AtomicReference<>(DownloadStatus.IDLE);
    public static final AtomicInteger downloadProgress = new AtomicInteger(0);
    public static final AtomicReference<String> downloadError = new AtomicReference<>("");
    public static final AtomicLong downloadBytesSoFar = new AtomicLong(0);
    public static final AtomicLong downloadTotalBytes = new AtomicLong(0);
    public static final AtomicReference<String> downloadEta = new AtomicReference<>("");
    public static final AtomicReference<String> downloadSpeedMBps = new AtomicReference<>("");
    private static Process serverProcess = null;

    @Override
    public void onInitializeClient() {
        CONFIG_MANAGER = new SimpleJsonConfig("mine-lights");
        CONFIG = CONFIG_MANAGER.load(MineLightsConfig.class, new MineLightsConfig());

        if (IS_WINDOWS) {
            initializeServerConnection();

            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (client.currentScreen instanceof TitleScreen && !titleScreenHooked) {
                    titleScreenHooked = true;
                    if (!hasPerformedServerCheck.getAndSet(true)) {
                        new Thread(MineLightsClient::checkForServerUpdate, "MineLights-Update-Check").start();
                    }
                }
            });
        }

        new Thread(() -> {
            try {
                if (proxyDiscoveredLatch.await(3, TimeUnit.SECONDS)) {
                    LOGGER.info("MineLights Server discovered via broadcast! Initializing connection.");
                } else if (!IS_WINDOWS) {
                    LOGGER.warn("MineLights Server not discovered via broadcast on non-Windows OS.");
                }
                refreshLightingManager();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "MineLights-Initializer-Waiter").start();

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (lightingManagerThread != null)
                lightingManagerThread.interrupt();
            if (discoveryThread != null)
                discoveryThread.interrupt();
            if (serverMonitorThread != null)
                serverMonitorThread.interrupt();
            UDPClient.close();
            if (IS_WINDOWS) {
                CommandClient.sendCommand("shutdown");
            }
        });
    }

    private static String readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[4096];
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return new String(buffer.toByteArray());
    }
    
    public static void checkForServerUpdate() {
        if (downloadStatus.get() == DownloadStatus.DOWNLOADING) {
            return;
        }

        try {
            Path serverExePath = MinecraftClient.getInstance().runDirectory.toPath().resolve("mods")
                    .resolve("MineLights").resolve("MineLights.exe");
            URI apiUri = URI.create(GITHUB_API_URL);
            HttpURLConnection conn = (HttpURLConnection) apiUri.toURL().openConnection();
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            String json;
            try (InputStream in = conn.getInputStream()) {
                json = readAllBytes(in);
            }

            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            JsonArray assets = root.getAsJsonArray("assets");
            if (assets.size() == 0) return;

            JsonObject asset = assets.get(0).getAsJsonObject();
            String expectedHash = asset.has("digest") ? asset.get("digest").getAsString().replace("sha256:", "") : "";

            boolean needsDownload = false;
            if (!Files.exists(serverExePath)) {
                needsDownload = true;
            } else {
                String localHash = sha256(serverExePath);
                if (!expectedHash.isEmpty() && !localHash.equalsIgnoreCase(expectedHash)) {
                    LOGGER.info("MineLights.exe is outdated or corrupted. An update is required.");
                    needsDownload = true;
                } else if (expectedHash.isEmpty()) {
                    LOGGER.warn("Could not verify server hash from GitHub API, assuming it's up to date.");
                } else {
                    LOGGER.info("MineLights.exe is up to date.");
                }
            }

            if (needsDownload) {
                new Thread(() -> performServerDownload(serverExePath), "MineLights-Background-Downloader").start();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to check for server update", e);
            downloadError.set(e.getMessage());
            downloadStatus.set(DownloadStatus.FAILED);
        }
    }

    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream fis = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        return bytesToHex(digest.digest());
    }
    
    private static boolean performServerDownload(Path destination) {
        if (!downloadStatus.compareAndSet(DownloadStatus.IDLE, DownloadStatus.DOWNLOADING) &&
            !downloadStatus.compareAndSet(DownloadStatus.FAILED, DownloadStatus.DOWNLOADING) &&
            !downloadStatus.compareAndSet(DownloadStatus.SUCCESS, DownloadStatus.DOWNLOADING)) {
            return false;
        }
        
        downloadProgress.set(0);
        downloadError.set("");

        try {
            URL apiUrl = URI.create(GITHUB_API_URL).toURL();
            HttpURLConnection conn = (HttpURLConnection) apiUrl.openConnection();
            conn.setRequestProperty("Accept", "application/vnd.github+json");

            String json;
            try (InputStream in = conn.getInputStream()) {
                json = readAllBytes(in);
            }

            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            JsonArray assets = root.getAsJsonArray("assets");
            if (assets.size() == 0) throw new IOException("No assets found in release");

            JsonObject asset = assets.get(0).getAsJsonObject();
            String downloadUrl = asset.get("browser_download_url").getAsString();
            String expectedHash = asset.has("digest") ? asset.get("digest").getAsString().replace("sha256:", "") : "";

            Files.createDirectories(destination.getParent());
            URL url = URI.create(downloadUrl).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "MineLights-Mod-Downloader/1.0");

            long totalFileSize = connection.getContentLengthLong();
            long totalBytesRead = 0;
            downloadTotalBytes.set(totalFileSize);
            downloadBytesSoFar.set(0);
            long startTime = System.nanoTime();

            try (InputStream inputStream = connection.getInputStream();
                FileOutputStream outputStream = new FileOutputStream(destination.toFile())) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                long lastBytesRead = 0;
                long lastTimeCheck = startTime;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                    downloadBytesSoFar.set(totalBytesRead);

                    if (totalFileSize > 0) {
                        downloadProgress.set((int) ((totalBytesRead * 100) / totalFileSize));
                    }

                    double secondsElapsed = (System.nanoTime() - startTime) / 1_000_000_000.0;
                    if (secondsElapsed > 0 && totalFileSize > 0) {
                        double bytesPerSecond = totalBytesRead / secondsElapsed;
                        long secondsRemaining = (long) ((totalFileSize - totalBytesRead) / bytesPerSecond);
                        downloadEta.set(formatEta(secondsRemaining));
                    }

                    long now = System.nanoTime();
                    double intervalSeconds = (now - lastTimeCheck) / 1_000_000_000.0;
                    if (intervalSeconds >= 1.0) {
                        long bytesInInterval = totalBytesRead - lastBytesRead;
                        double mbps = bytesInInterval / (1024.0 * 1024.0) / intervalSeconds;
                        downloadSpeedMBps.set(String.format("%.2f", mbps));
                        lastBytesRead = totalBytesRead;
                        lastTimeCheck = now;
                    }
                }
            }

            downloadStatus.set(DownloadStatus.VERIFYING);
            String actualHash = sha256(destination);
            if (!expectedHash.isEmpty() && !actualHash.equalsIgnoreCase(expectedHash)) {
                throw new IOException("Hash mismatch! Expected " + expectedHash + " but got " + actualHash);
            }

            LOGGER.info("Server executable downloaded successfully.");
            downloadStatus.set(DownloadStatus.SUCCESS);
            return true;

        } catch (Exception e) {
            LOGGER.error("Failed during background download or verification", e);
            downloadError.set(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            downloadStatus.set(DownloadStatus.FAILED);
            return false;
        }
    }

    private static String formatEta(long seconds) {
        long mins = seconds / 60;
        long secs = seconds % 60;
        return String.format("%dm %ds", mins, secs);
    }

    private void initializeServerConnection() {
        discoveryThread = new Thread(new DiscoveryListener(), "MineLights-Discovery");
        discoveryThread.setDaemon(true);
        discoveryThread.start();

        if (CONFIG.autoStartServer) {
            serverMonitorThread = new Thread(MineLightsClient::serverMonitorLoop, "MineLights-Server-Monitor");
            serverMonitorThread.setDaemon(true);
            serverMonitorThread.start();
        } else {
             new Thread(MineLightsClient::checkForServerUpdate, "MineLights-Initial-Check").start();
        }
    }
    
    private static void serverMonitorLoop() {
        LOGGER.info("Starting MineLights Server monitor.");
        boolean hasConnected = false;
        long lastLaunchAttemptTime = 0;

        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (isServerRunning()) {
                    if (!hasConnected) {
                        LOGGER.info("Server is running. Establishing connection...");
                        refreshLightingManager();
                        hasConnected = true;
                    }
                    Thread.sleep(10000);
                    continue;
                }

                hasConnected = false;
                Path serverExePath = MinecraftClient.getInstance().runDirectory.toPath().resolve("mods").resolve("MineLights").resolve("MineLights.exe");

                if (!Files.exists(serverExePath)) {
                    if (downloadStatus.get() != DownloadStatus.DOWNLOADING) {
                        LOGGER.warn("MineLights.exe not found. Performing synchronous download.");
                        if (!performServerDownload(serverExePath)) {
                            LOGGER.error("Download failed. Will retry after 30 seconds.");
                            Thread.sleep(30000);
                        }
                    } else {
                        Thread.sleep(1000);
                    }
                    continue;
                }

                if (serverProcess == null || !serverProcess.isAlive()) {
                    if (System.currentTimeMillis() - lastLaunchAttemptTime > 10000) {
                        LOGGER.info("Server process is not active. Attempting to launch MineLights.exe...");
                        startServerProcess();
                        lastLaunchAttemptTime = System.currentTimeMillis();
                    }
                } else {
                    LOGGER.info("Waiting for running server process to become responsive...");
                }
                
                Thread.sleep(5000);

            } catch (InterruptedException e) {
                LOGGER.info("Server monitor shutting down.");
                break;
            }
        }
    }
    
    public static boolean isServerRunning() {
        try (Socket ignored = new Socket("127.0.0.1", 63213)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void startServerProcess() {
        Path serverExePath = MinecraftClient.getInstance().runDirectory.toPath().resolve("mods").resolve("MineLights")
                .resolve("MineLights.exe");

        if (!Files.exists(serverExePath)) { return; }

        try {
            LOGGER.info("Launching server from: {}", serverExePath.toAbsolutePath());
            ProcessBuilder pb = new ProcessBuilder(serverExePath.toAbsolutePath().toString());
            pb.directory(serverExePath.getParent().toFile());
            pb.inheritIO();
            
            serverProcess = pb.start();
        } catch (IOException e) {
            LOGGER.warn("Failed to start MineLights.exe process: {}", e.getMessage());
            serverProcess = null;
        }
    }

    public static void refreshLightingManager() {
        LOGGER.info("Attempting to establish lighting connections...");
        isProxyConnected = false;
        if (lightingManagerThread != null && lightingManagerThread.isAlive()) {
            lightingManagerThread.interrupt();
            try {
                lightingManagerThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        discoveredDevices.clear();
        proxyDiscoveredLatch = new CountDownLatch(1);
        lightingManager = new LightingManager();
        lightingManagerThread = new Thread(lightingManager, "MineLights-LightingManager");
        lightingManagerThread.start();
    }

    public static void saveConfig() {
        CONFIG_MANAGER.save(CONFIG);
    }
}