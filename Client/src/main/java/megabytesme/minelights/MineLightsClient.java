package megabytesme.minelights;

import megabytesme.minelights.config.MineLightsConfig;
import megabytesme.minelights.config.SimpleJsonConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MineLightsClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("MineLights");
    public static MineLightsConfig CONFIG;
    private static SimpleJsonConfig CONFIG_MANAGER;
    private static Thread lightingManagerThread;
    private static Thread discoveryThread;
    private static LightingManager lightingManager;

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

        new Thread(() -> {
            LOGGER.info("Waiting for MineLights Server broadcast...");
            try {
                if (proxyDiscoveredLatch.await(10, TimeUnit.SECONDS)) {
                    LOGGER.info("MineLights Server discovered! Initializing connection.");
                } else {
                    LOGGER.warn(
                            "MineLights Server not discovered via broadcast after 10 seconds. Proceeding with connection attempt anyway.");
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
            UDPClient.close();
            if (IS_WINDOWS) {
                CommandClient.sendCommand("shutdown");
            }
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