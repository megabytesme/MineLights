package minelights.minelights;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.world.World;

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
        playerDto.setWorldLevel(world.getRegistryKey().getValue().getPath(););
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

        // Print the player data
        System.out.println(playerDto.getWorldLevel());
        System.out.println(playerDto.getHealth());
        System.out.println(playerDto.getHunger());
        System.out.println(playerDto.getWeather());
        System.out.println(playerDto.getCurrentBlock());        
    }
}
