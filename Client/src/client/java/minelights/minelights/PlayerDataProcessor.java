package minelights.minelights;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffects;
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
        
        playerDto.setInGame(true);
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

        if (player.isOnFire()) {
            playerDto.setIsOnFire(true);
        } else {
            playerDto.setIsOnFire(false);
        }

        if (player.hasStatusEffect(StatusEffects.POISON)) {
            playerDto.setIsPoisoned(true);
        } else {
            playerDto.setIsPoisoned(false);
        }

        if (player.hasStatusEffect(StatusEffects.WITHER)) {
            playerDto.setIsWithering(true);
        } else {
            playerDto.setIsWithering(false);
        }
        
        if (player.hurtTime > 0) {
            playerDto.setIsTakingDamage(true);
        } else {
            playerDto.setIsTakingDamage(false);
        }

        playerDto.setExperience(player.experienceProgress);

        // Serialize playerDto to JSON
        Gson gson = new Gson();
        String playerDtoJson = gson.toJson(playerDto);

        // Send the JSON data via UDP
        UDPClient.sendPlayerData(playerDtoJson);
    }
}