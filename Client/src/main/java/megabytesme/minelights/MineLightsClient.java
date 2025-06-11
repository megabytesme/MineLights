package megabytesme.minelights;

import megabytesme.minelights.config.MineLightsConfig;
import megabytesme.minelights.config.SimpleJsonConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MineLightsClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("MineLights");
    public static MineLightsConfig CONFIG;
    private static SimpleJsonConfig CONFIG_MANAGER;
    private static LightingManager lightingManager;
    private static Thread lightingManagerThread;
    private static Thread discoveryThread;
    private static Thread serverMonitorThread;

    public static final List<String> discoveredDevices = Collections.synchronizedList(new ArrayList<>());
    public static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    public static volatile boolean isProxyConnected = false;

    public static CountDownLatch proxyDiscoveredLatch = new CountDownLatch(1);

    @Override
    public void onInitializeClient() {
        CONFIG_MANAGER = new SimpleJsonConfig("mine-lights");
        CONFIG = CONFIG_MANAGER.load(MineLightsConfig.class, new MineLightsConfig());

        discoveryThread = new Thread(new DiscoveryListener(), "MineLights-Discovery");
        discoveryThread.setDaemon(true);
        discoveryThread.start();

        if (IS_WINDOWS && CONFIG.autoStartServer) {
            serverMonitorThread = new Thread(MineLightsClient::serverMonitorLoop, "MineLights-Server-Monitor");
            serverMonitorThread.setDaemon(true);
            serverMonitorThread.start();
        }

        new Thread(() -> {
            LOGGER.info("Waiting for MineLights Server broadcast...");
            try {
                if (proxyDiscoveredLatch.await(3, TimeUnit.SECONDS)) {
                    LOGGER.info("MineLights Server discovered via broadcast! Initializing connection.");
                } else {
                    LOGGER.warn(
                            "MineLights Server not discovered via broadcast. Connection will be attempted by the monitor.");
                }
                refreshLightingManager();
            } catch (InterruptedException e) {
                LOGGER.error("Interrupted while waiting for server discovery.");
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

    private static boolean isServerRunning() {
        try (Socket ignored = new Socket("127.0.0.1", 63213)) {
            return true;
        } catch (ConnectException e) {
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    private static void startServerProcess() {
        File gameDir = MinecraftClient.getInstance().runDirectory;
        File serverExe = new File(gameDir, "mods" + File.separator + "MineLights" + File.separator + "MineLights.exe");

        if (!serverExe.exists()) {
            LOGGER.error("MineLights.exe not found at expected location: {}", serverExe.getAbsolutePath());
            LOGGER.error(
                    "Please place the server executable in a 'MineLights' folder inside your mods folder.");
            if (serverMonitorThread != null)
                serverMonitorThread.interrupt();
            return;
        }

        try {
            LOGGER.info("Launching server from: {}", serverExe.getAbsolutePath());
            ProcessBuilder pb = new ProcessBuilder(serverExe.getAbsolutePath());
            pb.directory(serverExe.getParentFile());
            pb.start();
        } catch (IOException e) {
            LOGGER.error("Failed to start MineLights.exe process.", e);
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