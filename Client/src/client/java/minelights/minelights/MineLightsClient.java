package minelights.minelights;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class MineLightsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        try {
            String modsFolder = System.getProperty("user.dir") + "/mods";
            ProcessBuilder processBuilder = new ProcessBuilder(modsFolder + "/MineLights/MineLights.exe");
            processBuilder.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

        PlayerDataProcessor playerDataProcessor = new PlayerDataProcessor();
        PlayerConnectionHandler playerConnectionHandler = new PlayerConnectionHandler();

        // This entrypoint is suitable for setting up client-specific logic, such as rendering.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            playerDataProcessor.processPlayerData(client);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            playerConnectionHandler.onDisconnect();
        });

        // stop the MineLights helper process when the client is stopping
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            try {
                String processName = "MineLights.exe";
                String[] command = {"taskkill", "/F", "/IM", processName};
                ProcessBuilder processBuilder = new ProcessBuilder(command);
                Process process = processBuilder.start();
                process.waitFor();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}