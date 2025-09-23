package megabytesme.minelights;

import megabytesme.minelights.config.MineLightsConfig;
import megabytesme.minelights.config.SimpleJsonConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.MultilineTextWidget;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MineLightsClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("MineLights");
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

    @Override
    public void onInitializeClient() {
        CONFIG_MANAGER = new SimpleJsonConfig("mine-lights");
        CONFIG = CONFIG_MANAGER.load(MineLightsConfig.class, new MineLightsConfig());

        if (IS_WINDOWS) {
            ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
                if (screen instanceof TitleScreen && !hasPerformedServerCheck.getAndSet(true)) {
                    initializeServerConnection(screen);
                }
            });
        } else {
            new Thread(() -> {
                try {
                    proxyDiscoveredLatch.await(3, TimeUnit.SECONDS);
                    refreshLightingManager();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "MineLights-Initializer-Waiter").start();
        }

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

    private static void checkForServerUpdate(Screen parentScreen) {
        if (!IS_WINDOWS)
            return;

        try {
            Path serverExePath = MinecraftClient.getInstance().runDirectory.toPath()
                    .resolve("mods").resolve("MineLights").resolve("MineLights.exe");

            URI apiUri = URI.create("https://api.github.com/repos/megabytesme/MineLights/releases/tags/v2-server");
            HttpURLConnection conn = (HttpURLConnection) apiUri.toURL().openConnection();
            conn.setRequestProperty("Accept", "application/vnd.github+json");

            String json;
            try (InputStream in = conn.getInputStream()) {
                json = new String(in.readAllBytes());
            }

            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray assets = root.getAsJsonArray("assets");
            if (assets.isEmpty())
                return;

            JsonObject asset = assets.get(0).getAsJsonObject();
            String expectedHash = asset.get("digest").getAsString().replace("sha256:", "");

            if (Files.exists(serverExePath)) {
                String localHash = sha256(serverExePath);
                if (!localHash.equalsIgnoreCase(expectedHash)) {
                    LOGGER.info("MineLights.exe is outdated or corrupted.");
                    MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance()
                            .setScreen(new UpdatePromptScreen(parentScreen, serverExePath)));
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to check for server update", e);
        }
    }

    @Environment(EnvType.CLIENT)
    public static class UpdatePromptScreen extends Screen {
        private final Screen parent;
        private final Path destination;

        public UpdatePromptScreen(Screen parent, Path destination) {
            super(Text.translatable("minelights.gui.update.title"));
            this.parent = parent;
            this.destination = destination;
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            int centerY = this.height / 2;
            int widgetWidth = 300;
            int widgetX = centerX - (widgetWidth / 2);

            this.addDrawableChild(new MultilineTextWidget(widgetX, centerY - 40,
                    Text.translatable("minelights.gui.update.info"), this.textRenderer)
                    .setMaxWidth(widgetWidth).setCentered(true));

            this.addDrawableChild(ButtonWidget.builder(
                    Text.translatable("minelights.gui.button.update_now"),
                    b -> this.client.setScreen(new DownloadProgressScreen(this.parent, destination)))
                    .dimensions(centerX - 100, centerY, 200, 20).build());

            this.addDrawableChild(ButtonWidget.builder(
                    Text.translatable("minelights.gui.button.skip"),
                    b -> this.close()).dimensions(centerX - 100, centerY + 24, 200, 20).build());
        }

        @Override
        public void close() {
            this.client.setScreen(this.parent);
        }
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
        return HexFormat.of().formatHex(digest.digest());
    }

    private void initializeServerConnection(Screen parentScreen) {
        if (!IS_WINDOWS) {
            return;
        }
        checkForServerUpdate(parentScreen);
        discoveryThread = new Thread(new DiscoveryListener(), "MineLights-Discovery");
        discoveryThread.setDaemon(true);
        discoveryThread.start();

        if (CONFIG.autoStartServer) {
            serverMonitorThread = new Thread(() -> serverMonitorLoop(parentScreen), "MineLights-Server-Monitor");
            serverMonitorThread.setDaemon(true);
            serverMonitorThread.start();
        }

        new Thread(() -> {
            try {
                if (proxyDiscoveredLatch.await(3, TimeUnit.SECONDS)) {
                    LOGGER.info("MineLights Server discovered via broadcast! Initializing connection.");
                } else {
                    LOGGER.warn("MineLights Server not discovered via broadcast.");
                    if (!CONFIG.autoStartServer) {
                        Path serverExePath = MinecraftClient.getInstance().runDirectory.toPath().resolve("mods")
                                .resolve("MineLights").resolve("MineLights.exe");
                        showDownloadPrompt(parentScreen, serverExePath, false);
                    }
                }
                refreshLightingManager();
            } catch (InterruptedException e) {
                LOGGER.error("Interrupted while waiting for server discovery.");
                Thread.currentThread().interrupt();
            }
        }, "MineLights-Initializer-Waiter").start();
    }

    private static void serverMonitorLoop(Screen parentScreen) {
        LOGGER.info("Starting MineLights Server monitor.");
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (!isServerRunning()) {
                    LOGGER.info("Server is not running. Attempting to launch MineLights.exe...");
                    startServerProcess(parentScreen);
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

    private static void startServerProcess(Screen parentScreen) {
        Path serverExePath = MinecraftClient.getInstance().runDirectory.toPath().resolve("mods").resolve("MineLights")
                .resolve("MineLights.exe");

        if (!Files.exists(serverExePath)) {
            LOGGER.error("MineLights.exe not found. Prompting user to download.");
            showDownloadPrompt(parentScreen, serverExePath, true);
            if (serverMonitorThread != null)
                serverMonitorThread.interrupt();
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
            Text title = Text.translatable("minelights.gui.download.title");
            Text message = isMissingFile
                    ? Text.translatable("minelights.gui.download.prompt_missing")
                    : Text.translatable("minelights.gui.download.prompt_not_running");

            ConfirmScreen confirmScreen = new ConfirmScreen(
                    (result) -> {
                        if (result) {
                            client.setScreen(new DownloadProgressScreen(parentScreen, destination));
                        } else {
                            client.setScreen(parentScreen);
                        }
                    },
                    title,
                    message,
                    Text.translatable("minelights.gui.button.download"),
                    Text.translatable("minelights.gui.button.cancel"));
            client.setScreen(confirmScreen);
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