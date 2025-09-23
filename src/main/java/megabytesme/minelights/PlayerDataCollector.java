package megabytesme.minelights;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import megabytesme.minelights.config.CompassPriority;

public class PlayerDataCollector {
    public static PlayerDto getCurrentState(MinecraftClient client) {
        PlayerDto playerDto = new PlayerDto();

        if (client == null || client.world == null || client.player == null) {
            playerDto.setInGame(false);
            return playerDto;
        }

        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;

        playerDto.setInGame(true);
        playerDto.setHealth(player.getHealth());
        playerDto.setHunger(player.getHungerManager().getFoodLevel());
        playerDto.setSaturation(player.getHungerManager().getSaturationLevel());
        playerDto.setAir(player.getAir());
        playerDto.setExperience(player.experienceProgress);
        playerDto.setCurrentBlock(world.getBlockState(player.getBlockPos()).getBlock().getTranslationKey());

        Optional<RegistryKey<Biome>> biomeKey = world.getBiome(player.getBlockPos()).getKey();
        biomeKey.ifPresent(key -> playerDto.setCurrentBiome(key.getValue().toString()));

        playerDto.setCurrentWorld(world.getRegistryKey().getValue().toString());

        playerDto.setIsOnFire(player.isOnFire());
        playerDto.setIsPoisoned(player.hasStatusEffect(StatusEffects.POISON));
        playerDto.setIsWithering(player.hasStatusEffect(StatusEffects.WITHER));
        playerDto.setIsTakingDamage(player.hurtTime > 0);
        updateCompassData(playerDto, player, world);

        if (world.isThundering()) {
            playerDto.setWeather("Thunderstorm");
        } else if (world.isRaining()) {
            playerDto.setWeather("Rain");
        } else {
            playerDto.setWeather("Clear");
        }

        List<WaypointDto> waypoints = new ArrayList<>();
        if (client.cameraEntity != null) {
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
        }
        playerDto.setWaypoints(waypoints);

        return playerDto;
    }

    private static void updateCompassData(PlayerDto dto, ClientPlayerEntity player, ClientWorld world) {
        CompassFindResult result = findCompass(player);

        if (result == null) {
            if (MineLightsClient.CONFIG.alwaysShowCompass && world.getRegistryKey().equals(World.OVERWORLD)) {
                dto.setCompassType(CompassType.STANDARD);
                GlobalPos spawnPos = GlobalPos.create(world.getRegistryKey(), world.getSpawnPos());
                setCompassTarget(dto, player, spawnPos.pos());
            } else {
                dto.setCompassState(megabytesme.minelights.CompassState.NONE);
                dto.setCompassType(CompassType.NONE);
            }
            return;
        }

        dto.setCompassType(result.type);

        GlobalPos targetPos = getCompassTargetPos(result.stack, player, world);

        if (targetPos != null && targetPos.dimension().equals(world.getRegistryKey())
                && !(targetPos.pos().getSquaredDistance(player.getPos()) < 1.0E-5)) {
            setCompassTarget(dto, player, targetPos.pos());
        } else {
            dto.setCompassState(megabytesme.minelights.CompassState.SPINNING);
        }
    }

    private static class CompassFindResult {
        final ItemStack stack;
        final CompassType type;

        CompassFindResult(ItemStack stack, CompassType type) {
            this.stack = stack;
            this.type = type;
        }
    }

    private static CompassFindResult findCompass(PlayerEntity player) {
        CompassPriority priority = MineLightsClient.CONFIG.compassPriority;
        List<ItemStack> stacksToCheck = new ArrayList<>();
        stacksToCheck.add(player.getMainHandStack());
        stacksToCheck.add(player.getOffHandStack());
        for (int i = 0; i < 36; i++) {
            stacksToCheck.add(player.getInventory().getStack(i));
        }

        switch (priority) {
            case RECOVERY_FIRST:
                for (ItemStack stack : stacksToCheck) {
                    if (stack.isOf(Items.RECOVERY_COMPASS)) {
                        return new CompassFindResult(stack, CompassType.RECOVERY);
                    }
                }
                for (ItemStack stack : stacksToCheck) {
                    if (stack.isOf(Items.COMPASS)) {
                        return new CompassFindResult(stack, CompassType.STANDARD);
                    }
                }
                break;

            case STANDARD_FIRST:
                for (ItemStack stack : stacksToCheck) {
                    if (stack.isOf(Items.COMPASS)) {
                        return new CompassFindResult(stack, CompassType.STANDARD);
                    }
                }
                for (ItemStack stack : stacksToCheck) {
                    if (stack.isOf(Items.RECOVERY_COMPASS)) {
                        return new CompassFindResult(stack, CompassType.RECOVERY);
                    }
                }
                break;

            default:
                for (ItemStack stack : stacksToCheck) {
                    if (stack.isOf(Items.RECOVERY_COMPASS)) {
                        return new CompassFindResult(stack, CompassType.RECOVERY);
                    }
                    if (stack.isOf(Items.COMPASS)) {
                        return new CompassFindResult(stack, CompassType.STANDARD);
                    }
                }
                break;
        }

        return null;
    }

    private static GlobalPos getCompassTargetPos(ItemStack stack, PlayerEntity holder, ClientWorld world) {
        if (stack.contains(DataComponentTypes.LODESTONE_TRACKER)) {
            LodestoneTrackerComponent lodestoneTrackerComponent = stack.get(DataComponentTypes.LODESTONE_TRACKER);
            return lodestoneTrackerComponent != null ? lodestoneTrackerComponent.target().orElse(null) : null;
        }
        if (stack.isOf(Items.RECOVERY_COMPASS)) {
            return holder.getLastDeathPos().orElse(null);
        }
        return GlobalPos.create(world.getRegistryKey(), world.getSpawnPos());
    }

    private static void setCompassTarget(PlayerDto dto, ClientPlayerEntity player, BlockPos target) {
        Vec3d playerPos = player.getPos();
        Vec3d targetPos = Vec3d.ofCenter(target);

        double deltaX = targetPos.getX() - playerPos.getX();
        double deltaZ = targetPos.getZ() - playerPos.getZ();

        double targetYaw = Math.toDegrees(Math.atan2(-deltaX, deltaZ));

        double relativeYaw = (targetYaw - player.getYaw());
        while (relativeYaw <= -180.0D)
            relativeYaw += 360.0D;
        while (relativeYaw > 180.0D)
            relativeYaw -= 360.0D;

        dto.setCompassState(megabytesme.minelights.CompassState.POINTING);
        dto.setCompassRelativeYaw(relativeYaw);
        dto.setCompassDistance(Math.sqrt(deltaX * deltaX + deltaZ * deltaZ));
    }
}