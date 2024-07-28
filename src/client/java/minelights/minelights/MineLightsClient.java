package minelights.minelights;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class MineLightsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PlayerDataProcessor playerDataProcessor = new PlayerDataProcessor();

        // This entrypoint is suitable for setting up client-specific logic, such as rendering.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            playerDataProcessor.processPlayerData(client);
        });
    }
}