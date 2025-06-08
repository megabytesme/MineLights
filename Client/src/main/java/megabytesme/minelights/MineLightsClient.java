package megabytesme.minelights;

import megabytesme.minelights.config.MineLightsConfig;
import megabytesme.minelights.config.SimpleJsonConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

public class MineLightsClient implements ClientModInitializer {
    public static MineLightsConfig CONFIG;
    private static SimpleJsonConfig CONFIG_MANAGER;

    private Thread lightingManagerThread;
    private LightingManager lightingManager;

    @Override
    public void onInitializeClient() {
        CONFIG_MANAGER = new SimpleJsonConfig("mine-lights");
        CONFIG = CONFIG_MANAGER.load(MineLightsConfig.class, new MineLightsConfig());

        this.lightingManager = new LightingManager();
        this.lightingManagerThread = new Thread(this.lightingManager, "MineLights-LightingManager");
        this.lightingManagerThread.start();

        try {
            String modsFolder = System.getProperty("user.dir") + "/mods";
            ProcessBuilder processBuilder = new ProcessBuilder(modsFolder + "/MineLights/MineLights.exe");
            processBuilder.start();
        } catch (Exception e) {
            System.err.println("Failed to start MineLights.exe: " + e.getMessage());
        }

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (this.lightingManagerThread != null) {
                this.lightingManagerThread.interrupt();
            }
            UDPClient.close();
            try {
                String processName = "MineLights.exe";
                String[] command = { "taskkill", "/F", "/IM", processName };
                new ProcessBuilder(command).start().waitFor();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void saveConfig() {
        CONFIG_MANAGER.save(CONFIG);
    }
}