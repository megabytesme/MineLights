package megabytesme.minelights;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
//? if <=1.16.5 {
/* import net.minecraft.nbt.CompoundTag;
*///?} else {
import net.minecraft.nbt.NbtCompound;
//?}
//? if >=1.16 && <1.19 {
/* import net.minecraft.util.dynamic.GlobalPos;
*///?}
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
//? if >=1.20 {
import net.minecraft.registry.Registry;
//?} else {
/* import net.minecraft.util.registry.Registry;
*///?}
//? if >=1.16 {
import net.minecraft.world.World;
//?}
import net.minecraft.world.dimension.DimensionType;

import java.util.ArrayList;
import java.util.List;
//? if >=1.19 {
import java.util.Optional;
import net.minecraft.util.math.GlobalPos;
//?}

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
        //? if <=1.14.3 {
        /* playerDto.setAir(player.getBreath());
        *///?} else {
        /* playerDto.setAir(player.getAir());
        *///?}
        playerDto.setExperience(player.experienceProgress);
        playerDto.setCurrentBlock(world.getBlockState(player.getBlockPos()).getBlock().getTranslationKey());

        //? if >=1.19 {
        world.getBiome(player.getBlockPos()).getKey().ifPresent(key -> playerDto.setCurrentBiome(key.getValue().toString()));
        //?} else if >= 1.16.2 {
        /*
        playerDto.setCurrentBiome(world.getRegistryManager().get(Registry.BIOME_KEY).getId(world.getBiome(player.getBlockPos())).toString());
        *///?} else if >= 1.16 {
        /*
        playerDto.setCurrentWorld(world.getRegistryKey().getValue().toString());
        *///?} else {
        /*
        playerDto.setCurrentBiome(Registry.BIOME.getId(world.getBiome(player.getBlockPos())).toString());
        */
        //?}

        //? if <=1.14.3 {
        /* playerDto.setCurrentWorld(Registry.DIMENSION_TYPE.getId(world.getDimension().getType()).toString());
        *///?} else if <1.16 {
        /* playerDto.setCurrentWorld(Registry.DIMENSION.getId(world.getDimension().getType()).toString());
        *///?} else {
        playerDto.setCurrentWorld(world.getRegistryKey().getValue().toString());
        //?}
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

        return playerDto;
    }

    private static void updateCompassData(PlayerDto dto, ClientPlayerEntity player, ClientWorld world) {
        CompassFindResult result = findCompass(player);

        if (result == null) {
            //? if >=1.16 {
            /* if (MineLightsClient.CONFIG.alwaysShowCompass &&
             world.getRegistryKey().equals(World.OVERWORLD)) {
             dto.setCompassType(CompassType.STANDARD);
             GlobalPos spawnPos = GlobalPos.create(world.getRegistryKey(),
             world.getSpawnPos());
             setCompassTarget(dto, player, spawnPos.getPos());
             } else {
             dto.setCompassState(megabytesme.minelights.CompassState.NONE);
             dto.setCompassType(CompassType.NONE);
             }
            *///?} else {
            /* if (MineLightsClient.CONFIG.alwaysShowCompass && world.dimension.getType() ==
             DimensionType.OVERWORLD) {
             dto.setCompassType(CompassType.STANDARD);
             setCompassTarget(dto, player, world.getSpawnPos());
             } else {
             dto.setCompassState(CompassState.NONE);
             dto.setCompassType(CompassType.NONE);
             }
            *///?}
            return;
        }

        dto.setCompassType(result.type);

        BlockPos targetPos = getCompassTargetPos(result.stack, player, world);

        if (targetPos != null && !(targetPos.getSquaredDistance(player.getBlockPos()) < 1.0E-5)) {
            setCompassTarget(dto, player, targetPos);
        } else {
            dto.setCompassState(CompassState.SPINNING);
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
        //? if >=1.16 {
        List<CompassFindResult> foundCompasses = new ArrayList<>();
        List<ItemStack> inventory = new ArrayList<>();
        inventory.add(player.getMainHandStack());
        inventory.add(player.getOffHandStack());
        for (int i = 0; i < 36; i++) {
            //? if >=1.17 {
            inventory.add(player.getInventory().getStack(i));
            //?} else {
            /* inventory.add(player.inventory.getStack(i));
            *///?}
        }

        for (ItemStack stack : inventory) {
            if (stack.isEmpty()) continue;

            //? if >=1.19 {
            if (stack.getItem() == Items.RECOVERY_COMPASS) {
                foundCompasses.add(new CompassFindResult(stack, CompassType.RECOVERY));
                continue;
            }
            //?}

            if (stack.getItem() == Items.COMPASS) {
                boolean isLodestone = false;
                //? if >= 1.18 {
                if (stack.hasNbt()) {
                    NbtCompound tag = stack.getNbt();
                    if (tag != null && tag.contains("LodestonePos")) isLodestone = true;
                }
                //?} else if >= 1.17 {
                /* if (stack.hasTag()) {
                    NbtCompound tag = stack.getTag();
                    if (tag != null && tag.contains("LodestonePos")) isLodestone = true;
                }
                *///?} else {
                /* if (stack.hasTag()) {
                    CompoundTag tag = stack.getTag();
                    if (tag != null && tag.contains("LodestonePos")) isLodestone = true;
                }
                *///?}
                
                if (isLodestone) {
                    foundCompasses.add(new CompassFindResult(stack, CompassType.LODESTONE));
                } else {
                    foundCompasses.add(new CompassFindResult(stack, CompassType.STANDARD));
                }
            }
        }

        if (foundCompasses.isEmpty()) {
            return null;
        }
        if (foundCompasses.size() == 1) {
            return foundCompasses.get(0);
        }

        switch (MineLightsClient.CONFIG.compassPriority) {
            case STANDARD_FIRST:
                return foundCompasses.stream().filter(r -> r.type == CompassType.STANDARD).findFirst().orElse(foundCompasses.get(0));
            case LODESTONE_FIRST:
                return foundCompasses.stream().filter(r -> r.type == CompassType.LODESTONE).findFirst().orElse(foundCompasses.get(0));
            //? if >=1.19 {
            case RECOVERY_FIRST:
                return foundCompasses.stream().filter(r -> r.type == CompassType.RECOVERY).findFirst().orElse(foundCompasses.get(0));
            //?}
            case PRIORITY:
            default:
                return foundCompasses.get(0);
        }
        //?} else {
        /*
        List<ItemStack> stacksToCheck = new ArrayList<>();
        stacksToCheck.add(player.getMainHandStack());
        stacksToCheck.add(player.getOffHandStack());
        for (int i = 0; i < 36; i++) {
            stacksToCheck.add(player.inventory.getInvStack(i));
        }
        for (ItemStack stack : stacksToCheck) {
            if (stack.getItem() == Items.COMPASS) {
                return new CompassFindResult(stack, CompassType.STANDARD);
            }
        }
        return null;
        *///?}
    }

    private static BlockPos getCompassTargetPos(ItemStack stack, PlayerEntity holder, ClientWorld world) {
        //? if >= 1.19 {
        if (stack.getItem() == Items.RECOVERY_COMPASS) {
            Optional<GlobalPos> lastDeathPos = holder.getLastDeathPos();
            if (lastDeathPos.isPresent()) {
                GlobalPos pos = lastDeathPos.get();
                if (pos.getDimension().equals(world.getRegistryKey())) {
                    return pos.getPos();
                }
            }
            return null;
        }
        if (stack.hasNbt()) {
            NbtCompound tag = stack.getNbt();
            if (tag != null && tag.contains("LodestonePos") && tag.contains("LodestoneDimension")) {
                String lodestoneDim = tag.getString("LodestoneDimension");
                if (world.getRegistryKey().getValue().toString().equals(lodestoneDim)) {
                    NbtCompound posTag = tag.getCompound("LodestonePos");
                    return new BlockPos(posTag.getInt("X"), posTag.getInt("Y"), posTag.getInt("Z"));
                }
                return null;
            }
        }
        if (world.getRegistryKey().equals(World.OVERWORLD)) {
            return world.getSpawnPos();
        }
        //?} else if >= 1.18 {
        /* if (stack.hasNbt()) {
            NbtCompound tag = stack.getNbt();
            if (tag != null && tag.contains("LodestonePos") && tag.contains("LodestoneDimension")) {
                String lodestoneDim = tag.getString("LodestoneDimension");
                if (world.getRegistryKey().getValue().toString().equals(lodestoneDim)) {
                    NbtCompound posTag = tag.getCompound("LodestonePos");
                    return new BlockPos(posTag.getInt("X"), posTag.getInt("Y"), posTag.getInt("Z"));
                }
                return null;
            }
        }
        if (world.getRegistryKey().equals(World.OVERWORLD)) {
            return world.getSpawnPos();
        }
        *///?} else if >= 1.17 {
        /* if (stack.hasTag()) {
            NbtCompound tag = stack.getTag();
            if (tag != null && tag.contains("LodestonePos") && tag.contains("LodestoneDimension")) {
                String lodestoneDim = tag.getString("LodestoneDimension");
                if (world.getRegistryKey().getValue().toString().equals(lodestoneDim)) {
                    NbtCompound posTag = tag.getCompound("LodestonePos");
                    return new BlockPos(posTag.getInt("X"), posTag.getInt("Y"), posTag.getInt("Z"));
                }
                return null;
            }
        }
        if (world.getRegistryKey().equals(World.OVERWORLD)) {
            return world.getSpawnPos();
        }
        *///?} else if >= 1.16 {
        /* if (stack.hasTag()) {
            CompoundTag tag = stack.getTag();
            if (tag != null && tag.contains("LodestonePos") && tag.contains("LodestoneDimension")) {
                String lodestoneDim = tag.getString("LodestoneDimension");
                if (world.getRegistryKey().getValue().toString().equals(lodestoneDim)) {
                    CompoundTag posTag = tag.getCompound("LodestonePos");
                    return new BlockPos(posTag.getInt("X"), posTag.getInt("Y"), posTag.getInt("Z"));
                }
                return null;
            }
        }
        if (world.getRegistryKey().equals(World.OVERWORLD)) {
            return world.getSpawnPos();
        }
        *///?} else {
        /* if (world.getDimension().getType() == DimensionType.OVERWORLD) {
            return world.getSpawnPos();
        }
        *///?}
        return null;
    }

    private static void setCompassTarget(PlayerDto dto, ClientPlayerEntity player, BlockPos target) {
        Vec3d playerPos = player.getPos();
        Vec3d targetPos = new Vec3d(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);

        double deltaX = targetPos.x - playerPos.x;
        double deltaZ = targetPos.z - playerPos.z;

        double targetYaw = Math.toDegrees(Math.atan2(-deltaX, deltaZ));

        double playerYaw;
        //? if <=1.16.5 {
        /* playerYaw = player.yaw;
        *///?} else {
        playerYaw = player.getYaw();
        //?}

        double relativeYaw = targetYaw - playerYaw;
        while (relativeYaw <= -180.0D)
            relativeYaw += 360.0D;
        while (relativeYaw > 180.0D)
            relativeYaw -= 360.0D;

        dto.setCompassState(CompassState.POINTING);
        dto.setCompassRelativeYaw(relativeYaw);
        dto.setCompassDistance(Math.sqrt(deltaX * deltaX + deltaZ * deltaZ));
    }
}