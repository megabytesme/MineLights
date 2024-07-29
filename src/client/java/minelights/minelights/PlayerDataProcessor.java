package minelights.minelights;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.world.World;
import com.google.gson.Gson;

public class PlayerDataProcessor {
    public void processPlayerData(MinecraftClient client) {
        // Check if the client is null
        if (client.world == null || client.player == null) {
            return;
        }

        // Get the player and world
        ClientPlayerEntity player = client.player;
        World world = client.world;

        // Set the player data
        PlayerDto playerDto = new PlayerDto();
        playerDto.setWorldLevel(world.getRegistryKey().getValue().getPath());
        playerDto.setHealth(player.getHealth());
        playerDto.setHunger(player.getHungerManager().getFoodLevel());
        if (world.isRaining()) {
            if (world.isThundering()) {
                playerDto.setWeather("Thunderstorm");
            } else {
                playerDto.setWeather("Rain");
            }
        } else {
            playerDto.setWeather("Clear");
        }
        playerDto.setCurrentBlock(world.getBlockState(player.getBlockPos()).getBlock().getTranslationKey());
        playerDto.setCurrentBiome(world.getBiome(player.getBlockPos()).getIdAsString());

        // Serialize playerDto to JSON
        Gson gson = new Gson();
        String playerDtoJson = gson.toJson(playerDto);

        // Send the JSON data via UDP
        UDPClient.sendPlayerData(playerDtoJson);
    }
}