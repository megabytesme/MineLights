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
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
                        new Thread(() -> checkForServerUpdate(client.currentScreen), "MineLights-Update-Check").start();
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

    private static void checkForServerUpdate(Screen parentScreen) {
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
            if (assets.size() == 0)
                return;

            JsonObject asset = assets.get(0).getAsJsonObject();
            String expectedHash = "";
            if (asset.has("digest")) {
                expectedHash = asset.get("digest").getAsString().replace("sha256:", "");
            } else {
                LOGGER.warn("GitHub release asset is missing 'digest' field for hash verification.");
                return;
            }

            if (!Files.exists(serverExePath)) {
                LOGGER.info("MineLights.exe not found. Prompting for initial download.");
                showDownloadPrompt(parentScreen, serverExePath, true);
                return;
            }

            String localHash = sha256(serverExePath);
            if (!localHash.equalsIgnoreCase(expectedHash)) {
                LOGGER.info("MineLights.exe is outdated or corrupted. Local hash: {}, Expected hash: {}", localHash,
                        expectedHash);
                showUpdatePrompt(parentScreen, serverExePath);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to check for server update", e);
        }
    }

    private static void showUpdatePrompt(Screen parentScreen, Path destination) {
        MinecraftClient.getInstance().execute(() -> {
            ConfirmScreen confirmScreen = new ConfirmScreen(
                    (result) -> {
                        if (result) {
                            MinecraftClient.getInstance()
                                    .openScreen(new DownloadProgressScreen(parentScreen, destination));
                        } else {
                            MinecraftClient.getInstance().openScreen(parentScreen);
                        }
                    },
                    new TranslatableText("minelights.gui.update.title"),
                    new TranslatableText("minelights.gui.update.info"));
            MinecraftClient.getInstance().openScreen(confirmScreen);
        });
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

    private void initializeServerConnection() {
        discoveryThread = new Thread(new DiscoveryListener(), "MineLights-Discovery");
        discoveryThread.setDaemon(true);
        discoveryThread.start();

        if (CONFIG.autoStartServer) {
            serverMonitorThread = new Thread(MineLightsClient::serverMonitorLoop, "MineLights-Server-Monitor");
            serverMonitorThread.setDaemon(true);
            serverMonitorThread.start();
        } else {
            new Thread(() -> {
                if (!isServerRunning()) {
                    Path serverExePath = MinecraftClient.getInstance().runDirectory.toPath().resolve("mods")
                            .resolve("MineLights").resolve("MineLights.exe");
                    if (!Files.exists(serverExePath)) {
                        ClientTickEvents.END_CLIENT_TICK.register(client -> {
                            if (client.currentScreen instanceof TitleScreen && !titleScreenHooked) {
                                titleScreenHooked = true;
                                if (!hasPerformedServerCheck.getAndSet(true)) {
                                    showDownloadPrompt(client.currentScreen, serverExePath, true);
                                }
                            }
                        });
                    }
                }
            }).start();
        }
    }

    private static void serverMonitorLoop() {
        LOGGER.info("Starting MineLights Server monitor.");
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (!isServerRunning()) {
                    LOGGER.info("Server is not running. Attempting to launch MineLights.exe...");
                    startServerProcess();
                }
                Thread.sleep(10000);
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

        if (!Files.exists(serverExePath)) {
            LOGGER.error("MineLights.exe not found.");
            if (serverMonitorThread != null)
                serverMonitorThread.interrupt();

            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (client.currentScreen instanceof TitleScreen && !hasPerformedServerCheck.getAndSet(true)) {
                    showDownloadPrompt(client.currentScreen, serverExePath, true);
                }
            });
            return;
        }

        try {
            LOGGER.info("Launching server from: {}", serverExePath.toAbsolutePath());
            ProcessBuilder pb = new ProcessBuilder(serverExePath.toAbsolutePath().toString());
            pb.directory(serverExePath.getParent().toFile());
            pb.start();
        } catch (IOException e) {
            LOGGER.error("Failed to start MineLights.exe process.", e);
        }
    }

    private static void showDownloadPrompt(Screen parentScreen, Path destination, boolean isMissingFile) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            Text title = new TranslatableText("minelights.gui.download.title");
            Text message = isMissingFile
                    ? new TranslatableText("minelights.gui.download.prompt_missing")
                    : new TranslatableText("minelights.gui.download.prompt_not_running");

            ConfirmScreen confirmScreen = new ConfirmScreen(
                    (result) -> {
                        if (result) {
                            client.openScreen(new DownloadProgressScreen(parentScreen, destination));
                        } else {
                            client.openScreen(parentScreen);
                        }
                    },
                    title,
                    message);
            client.openScreen(confirmScreen);
        });
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