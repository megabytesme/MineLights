package megabytesme.minelights;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.world.World;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PlayerDataCollector {
    public static PlayerDto getCurrentState(MinecraftClient client) {
        PlayerDto playerDto = new PlayerDto();

        if (client == null || client.world == null || client.player == null) {
            playerDto.setInGame(false);
            return playerDto;
        }

        ClientPlayerEntity player = client.player;
        World world = client.world;

        playerDto.setInGame(true);
        playerDto.setHealth(player.getHealth());
        playerDto.setHunger(player.getHungerManager().getFoodLevel());
        playerDto.setSaturation(player.getHungerManager().getSaturationLevel());
        playerDto.setAir(player.getAir());
        playerDto.setExperience(player.experienceProgress);
        playerDto.setCurrentBlock(world.getBlockState(player.getBlockPos()).getBlock().getTranslationKey());

        Optional<RegistryKey<Biome>> biomeKey = world.getBiome(player.getBlockPos()).getKey();
        biomeKey.ifPresent(key -> playerDto.setCurrentBiome(key.getValue().toString()));

        playerDto.setIsOnFire(player.isOnFire());
        playerDto.setIsPoisoned(player.hasStatusEffect(StatusEffects.POISON));
        playerDto.setIsWithering(player.hasStatusEffect(StatusEffects.WITHER));
        playerDto.setIsTakingDamage(player.hurtTime > 0);

        if (world.isThundering()) {
            playerDto.setWeather("Thunderstorm");
        } else if (world.isRaining()) {
            playerDto.setWeather("Rain");
        } else {
            playerDto.setWeather("Clear");
        }

        List<WaypointDto> waypoints = new ArrayList<>();
        client.player.networkHandler.getWaypointHandler().forEachWaypoint(client.cameraEntity, (waypoint) -> {
            if (waypoint.getSource().left().map(uuid -> uuid.equals(client.cameraEntity.getUuid())).orElse(false)) {
                return;
            }

            WaypointDto waypointDto = new WaypointDto();
            waypointDto.setRelativeYaw(waypoint.getRelativeYaw(world, client.gameRenderer.getCamera()));
            waypointDto.setPitch(waypoint.getPitch(world, client.gameRenderer));
            waypointDto.setDistance((float) Math.sqrt(waypoint.squaredDistanceTo(client.cameraEntity)));

            int color = waypoint.getConfig().color.orElseGet(() -> waypoint.getSource().map(
                    uuid -> ColorHelper.withBrightness(ColorHelper.withAlpha(255, uuid.hashCode()), 0.9F),
                    name -> ColorHelper.withBrightness(ColorHelper.withAlpha(255, name.hashCode()), 0.9F)));
            waypointDto.setColor(color);

            waypoints.add(waypointDto);
        });
        playerDto.setWaypoints(waypoints);

        return playerDto;
    }
}