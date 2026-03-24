package megabytesme.minelights;

//? if >=26.1 {
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ARGB;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.waypoints.TrackedWaypoint;
//?} else {
/* import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
//? if >=1.20.5 {
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.util.math.GlobalPos;
//?} else if >=1.19 {
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.GlobalPos;
//?} else if >=1.17 {
import net.minecraft.nbt.NbtCompound;
//?} else if >=1.16 {
import net.minecraft.nbt.CompoundTag;
//?} else {
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.dimension.DimensionType;
//?}
*///?}

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import megabytesme.minelights.mixin.LightningAccessor;
import megabytesme.minelights.accessor.ChatReceivedAccessor;
import megabytesme.minelights.accessor.PlayerVisualBrightnessAccessor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PlayerDataCollector {
    public static final Logger LOGGER = LogManager.getLogger("MineLights-PlayerDataCollector");

    //? if >=26.1 {
    public static PlayerDto getCurrentState(Minecraft client) {
    //?} else {
    /* public static PlayerDto getCurrentState(MinecraftClient client) {
    *///?}
        PlayerDto playerDto = new PlayerDto();

        //? if >=26.1 {
        if (client == null || client.level == null || client.player == null) {
        //?} else {
        /* if (client == null || client.world == null || client.player == null) {
        *///?}
            playerDto.setInGame(false);
            return playerDto;
        }

        //? if >=26.1 {
        LocalPlayer player = client.player;
        ClientLevel world = client.level;
        //?} else {
        /* ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        *///?}

        playerDto.setInGame(true);
        playerDto.setHealth(player.getHealth());
        //? if >=26.1 {
        playerDto.setHunger(player.getFoodData().getFoodLevel());
        playerDto.setSaturation(player.getFoodData().getSaturationLevel());
        playerDto.setAir(player.getAirSupply());
        //?} else {
        /* playerDto.setHunger(player.getHungerManager().getFoodLevel());
        playerDto.setSaturation(player.getHungerManager().getSaturationLevel());
        //? if >1.14.3 {
        playerDto.setAir(player.getAir());
        //?} else {
        playerDto.setAir(300);
        //?} */
        //?}
        playerDto.setExperience(player.experienceProgress);

        //? if >=26.1 {
        BlockPos playerPos = player.blockPosition();
        playerDto.setBlockAtFeet(BuiltInRegistries.BLOCK.getKey(world.getBlockState(playerPos).getBlock()).toString());
        playerDto.setBlockOn(BuiltInRegistries.BLOCK.getKey(world.getBlockState(playerPos.below()).getBlock()).toString());
        Vec3 eyePos = player.getEyePosition();
        BlockPos headPos = BlockPos.containing(eyePos.x, eyePos.y, eyePos.z);
        playerDto.setBlockAtHead(BuiltInRegistries.BLOCK.getKey(world.getBlockState(headPos).getBlock()).toString());
        world.getBiome(playerPos).unwrapKey().ifPresent(key -> playerDto.setCurrentBiome(key.identifier().toString()));
        playerDto.setCurrentWorld(world.dimension().identifier().toString());
        //?} else {
        /* playerDto.setBlockAtFeet(world.getBlockState(player.getBlockPos()).getBlock().getTranslationKey());
        playerDto.setBlockOn(world.getBlockState(player.getBlockPos().down()).getBlock().getTranslationKey());
        //? if >=1.20 {
        Vec3d eyePos = player.getEyePos();
        BlockPos headPos = BlockPos.ofFloored(eyePos.x, eyePos.y, eyePos.z);
        //?} else if >=1.17 {
        Vec3d eyePos = player.getEyePos();
        BlockPos headPos = new BlockPos(eyePos.x, eyePos.y, eyePos.z);
        //?} else {
        BlockPos headPos = player.getBlockPos().up();
        //?}
        playerDto.setBlockAtHead(world.getBlockState(headPos).getBlock().getTranslationKey());
        //? if >=1.18 {
        world.getBiome(player.getBlockPos()).getKey().ifPresent(key -> playerDto.setCurrentBiome(key.getValue().toString()));
        //?} else {
        playerDto.setCurrentBiome(world.getBiome(player.getBlockPos()).getCategory().toString());
        //?}
        //? if >=1.16 {
        playerDto.setCurrentWorld(world.getRegistryKey().getValue().toString());
        //?} else {
        playerDto.setCurrentWorld(world.dimension.getType().toString());
        //?} */
        //?}
        playerDto.setIsOnFire(player.isOnFire());
        //? if >=26.1 {
        playerDto.setIsPoisoned(player.hasEffect(MobEffects.POISON));
        playerDto.setIsWithering(player.hasEffect(MobEffects.WITHER));
        //?} else {
        /* playerDto.setIsPoisoned(player.hasStatusEffect(StatusEffects.POISON));
        playerDto.setIsWithering(player.hasStatusEffect(StatusEffects.WITHER));
        *///?}
        playerDto.setIsTakingDamage(player.hurtTime > 0);
        updateCompassData(playerDto, player, world);

        if (world.isThundering()) {
            playerDto.setWeather("Thunderstorm");
        } else if (world.isRaining()) {
            playerDto.setWeather("Rain");
        } else {
            playerDto.setWeather("Clear");
        }

        //? if >=26.1 {
        if (world.dimension().equals(Level.END)) {
            float intensity = world.endFlashState().getIntensity(client.getDeltaTracker().getGameTimeDeltaPartialTick(true));
            playerDto.setEndFlashIntensity(intensity);
        } else {
            playerDto.setEndFlashIntensity(0.0f);
        }
        //?} else {
        /* //? if >=1.21.9 {
        if (world.getRegistryKey().equals(World.END)) {
            net.minecraft.client.render.EndLightFlashManager flashManager = world.getEndLightFlashManager();
            float intensity = flashManager.getSkyFactor(client.getRenderTickCounter().getTickProgress(true));
            playerDto.setEndFlashIntensity(intensity);
        } else {
            playerDto.setEndFlashIntensity(0.0f);
        }
        //?} else {
        playerDto.setEndFlashIntensity(0.0f);
        //?} */
        //?}
        //? if >=26.1 {
        playerDto.setWaypoints(collectWaypoints(client, player, world));
        //?} else {
        /* playerDto.setWaypoints(new ArrayList<>()); */
        //?}

        //? if >=26.1 {
        for (Entity entity : world.entitiesForRendering()) {
        //?} else {
        /* for (Entity entity : world.getEntities()) { */
        //?}
            //? if >=26.1 {
            if (entity instanceof LightningBolt) {
                LightningBolt lightning = (LightningBolt) entity;
            //?} else {
            /* if (entity instanceof LightningEntity) {
                LightningEntity lightning = (LightningEntity) entity;
            *///?}
                LightningAccessor acc = (LightningAccessor) lightning;

                int ambientTick = acc.getAmbientTick();
                int remainingActions = acc.getRemainingActions();

                playerDto.setIsLightningFlashing((ambientTick % 3) < 2 && remainingActions > 0);
            }
        }

        playerDto.setSkyLightLevel(((PlayerVisualBrightnessAccessor) player).getSkyLightLevel());
        playerDto.setRenderedBrightnessLevel(((PlayerVisualBrightnessAccessor) player).getRenderedBrightness());

        //? if >=26.1 {
        ClientPacketListener handler = client.getConnection();
        if (handler instanceof ChatReceivedAccessor accessor) {
            if (accessor.wasChatReceivedThisTick()) {
                playerDto.setIsChatReceived(true);
                accessor.resetChatReceivedFlag();
            } else {
                playerDto.setIsChatReceived(false);
            }
        } else {
            playerDto.setIsChatReceived(false);
        }
        //?} else {
        /* ClientPlayNetworkHandler handler = MinecraftClient.getInstance().getNetworkHandler();
        if (handler instanceof ChatReceivedAccessor) {
            ChatReceivedAccessor accessor = (ChatReceivedAccessor) handler;
            if (accessor.wasChatReceivedThisTick()) {
                playerDto.setIsChatReceived(true);
                accessor.resetChatReceivedFlag();
            } else {
                playerDto.setIsChatReceived(false);
            }
        }
        *///?}

        return playerDto;
    }

    //? if >=26.1 {
    private static List<WaypointDto> collectWaypoints(Minecraft client, LocalPlayer player, ClientLevel world) {
        List<WaypointDto> waypoints = new ArrayList<>();
        ClientPacketListener connection = client.getConnection();
        if (connection == null) {
            return waypoints;
        }

        float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        TrackedWaypoint.Camera camera = new TrackedWaypoint.Camera() {
            @Override
            public float yaw() {
                return player.getYRot();
            }

            @Override
            public Vec3 position() {
                return player.getEyePosition(partialTick);
            }
        };

        connection.getWaypointManager().forEachWaypoint(player, trackedWaypoint -> {
            WaypointDto waypoint = toWaypointDto(world, player, trackedWaypoint, camera, partialTick);
            if (waypoint != null) {
                waypoints.add(waypoint);
            }
        });

        return waypoints;
    }

    private static WaypointDto toWaypointDto(ClientLevel world, LocalPlayer player, TrackedWaypoint trackedWaypoint,
            TrackedWaypoint.Camera camera, float partialTick) {
        WaypointDto waypointDto = new WaypointDto();
        waypointDto.setRelativeYaw(normalizeYaw(trackedWaypoint.yawAngleToCamera(world, camera, entity -> partialTick)));
        net.minecraft.world.waypoints.Waypoint.Icon icon = trackedWaypoint.icon().cloneAndAssignStyle(player);
        waypointDto.setColor(icon.color.orElseGet(() -> fallbackWaypointColor(trackedWaypoint)) & 0xFFFFFF);
        waypointDto.setDistance((float) Math.sqrt(trackedWaypoint.distanceSquared(player)));
        waypointDto.setPitch(resolveWaypointPitch(player, trackedWaypoint));
        return waypointDto;
    }

    private static WaypointDto.Pitch resolveWaypointPitch(LocalPlayer player, TrackedWaypoint trackedWaypoint) {
        Vec3 position = resolveWaypointPosition(trackedWaypoint, player);
        if (position == null) {
            return WaypointDto.Pitch.LEVEL;
        }

        double deltaY = position.y - player.getEyePosition().y;
        if (deltaY > 1.0) {
            return WaypointDto.Pitch.UP;
        }
        if (deltaY < -1.0) {
            return WaypointDto.Pitch.DOWN;
        }
        return WaypointDto.Pitch.LEVEL;
    }

    private static Vec3 resolveWaypointPosition(TrackedWaypoint trackedWaypoint, LocalPlayer player) {
        Class<?> waypointClass = trackedWaypoint.getClass();

        if (waypointClass.getSimpleName().equals("Vec3iWaypoint")) {
            Object vector = readWaypointField(waypointClass, trackedWaypoint, "vector");
            if (vector instanceof net.minecraft.core.Vec3i vec) {
                return new Vec3(vec.getX() + 0.5, vec.getY() + 0.5, vec.getZ() + 0.5);
            }
        }

        if (waypointClass.getSimpleName().equals("ChunkWaypoint")) {
            Object chunkPos = readWaypointField(waypointClass, trackedWaypoint, "chunkPos");
            if (chunkPos instanceof net.minecraft.world.level.ChunkPos chunk) {
                return new Vec3(chunk.getMiddleBlockX() + 0.5, player.getEyeY(), chunk.getMiddleBlockZ() + 0.5);
            }
        }

        return null;
    }

    private static Object readWaypointField(Class<?> waypointClass, TrackedWaypoint trackedWaypoint, String fieldName) {
        try {
            Field field = waypointClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(trackedWaypoint);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static double normalizeYaw(double yaw) {
        while (yaw <= -180.0D) {
            yaw += 360.0D;
        }
        while (yaw > 180.0D) {
            yaw -= 360.0D;
        }
        return yaw;
    }

    private static int fallbackWaypointColor(TrackedWaypoint trackedWaypoint) {
        int hash = trackedWaypoint.id().hashCode();
        int r = 96 + ((hash >>> 16) & 0x5F);
        int g = 96 + ((hash >>> 8) & 0x5F);
        int b = 96 + (hash & 0x5F);
        return (r << 16) | (g << 8) | b;
    }
    //?}

    //? if >=26.1 {
    private static void updateCompassData(PlayerDto dto, LocalPlayer player, ClientLevel world) {
    //?} else {
    /* private static void updateCompassData(PlayerDto dto, ClientPlayerEntity player, ClientWorld world) {
    *///?}
        CompassFindResult result = findCompass(player);

        if (result == null) {
            //? if >=1.16 && <1.20.5 {
            /* if (MineLightsClient.CONFIG.alwaysShowCompass &&
            world.getRegistryKey().equals(World.OVERWORLD)) {
            dto.setCompassType(CompassType.STANDARD);
            setCompassTarget(dto, player, world.getSpawnPos());
            } else {
            dto.setCompassState(megabytesme.minelights.CompassState.NONE);
            dto.setCompassType(CompassType.NONE);
            }
            *///?} else if >=1.20.5 && <1.21.9 {
            /* if (MineLightsClient.CONFIG.alwaysShowCompass &&
                world.getRegistryKey().equals(World.OVERWORLD)) {
                dto.setCompassType(CompassType.STANDARD);
                setCompassTarget(dto, player, world.getSpawnPos());
            } else {
                dto.setCompassState(megabytesme.minelights.CompassState.NONE);
                dto.setCompassType(CompassType.NONE);
            }
            *///?} else if >=1.21.9 {
            //? if >=26.1 {
            if (MineLightsClient.CONFIG.alwaysShowCompass && world.dimension().equals(Level.OVERWORLD)) {
                dto.setCompassType(CompassType.STANDARD);
                net.minecraft.world.level.storage.LevelData.RespawnData respawnData = world.getLevelData().getRespawnData();
                if (respawnData != null && respawnData.dimension().equals(world.dimension())) {
                    setCompassTarget(dto, player, respawnData.pos());
                } else {
                    dto.setCompassState(CompassState.NONE);
                    dto.setCompassType(CompassType.NONE);
                }
            } else {
                dto.setCompassState(megabytesme.minelights.CompassState.NONE);
                dto.setCompassType(CompassType.NONE);
            }
            //?} else {
            /* if (MineLightsClient.CONFIG.alwaysShowCompass &&
                world.getRegistryKey().equals(World.OVERWORLD)) {
                dto.setCompassType(CompassType.STANDARD);
                GlobalPos spawnPos = GlobalPos.create(world.getRegistryKey(), world.getSpawnPoint().getPos());
                setCompassTarget(dto, player, spawnPos.pos());
            } else {
                dto.setCompassState(megabytesme.minelights.CompassState.NONE);
                dto.setCompassType(CompassType.NONE);
            } */
            //?}
            //?} else {
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

        //? if >=26.1 {
        if (targetPos != null && player.distanceToSqr(Vec3.atCenterOf(targetPos)) >= 1.0E-5) {
        //?} else {
        /* if (targetPos != null && !(targetPos.getSquaredDistance(player.getBlockPos()) < 1.0E-5)) { */
        //?}
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

    //? if >=26.1 {
    private static CompassFindResult findCompass(Player player) {
    //?} else {
    /* private static CompassFindResult findCompass(PlayerEntity player) {
    *///?}
        //? if >=1.16 {
        List<CompassFindResult> foundCompasses = new ArrayList<>();
        List<ItemStack> inventory = new ArrayList<>();
        //? if >=26.1 {
        inventory.add(player.getMainHandItem());
        inventory.add(player.getOffhandItem());
        //?} else {
        /* inventory.add(player.getMainHandStack());
        inventory.add(player.getOffHandStack()); */
        //?}
        for (int i = 0; i < 36; i++) {
            //? if >=1.17 {
            //? if >=26.1 {
            inventory.add(player.getInventory().getItem(i));
            //?} else {
            /* inventory.add(player.getInventory().getStack(i)); */
            //?}
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
                //? if >=1.20.5 {
                //? if >=26.1 {
                LodestoneTracker lodestoneData = stack.get(DataComponents.LODESTONE_TRACKER);
                //?} else {
                /* LodestoneTrackerComponent lodestoneData = stack.get(DataComponentTypes.LODESTONE_TRACKER); */
                //?}
                if (lodestoneData != null && lodestoneData.target().isPresent()) {
                    isLodestone = true;
                }
                //?} else if >= 1.18 {
                /* if (stack.hasNbt()) {
                    NbtCompound tag = stack.getNbt();
                    if (tag != null && tag.contains("LodestonePos")) isLodestone = true;
                }
                *///?} else if >= 1.17 {
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

    //? if >=26.1 {
    private static BlockPos getCompassTargetPos(ItemStack stack, Player holder, ClientLevel world) {
    //?} else {
    /* private static BlockPos getCompassTargetPos(ItemStack stack, PlayerEntity holder, ClientWorld world) {
    *///?}
        //? if >= 1.19 {
        if (stack.getItem() == Items.RECOVERY_COMPASS) {
            //? if >=26.1 {
            Optional<GlobalPos> lastDeathPos = holder.getLastDeathLocation();
            //?} else {
            /* Optional<GlobalPos> lastDeathPos = holder.getLastDeathPos(); */
            //?}
            if (lastDeathPos.isPresent()) {
                GlobalPos pos = lastDeathPos.get();
                //? if >=1.20.5 {
                //? if >=26.1 {
                if (pos.dimension().equals(world.dimension())) {
                //?} else {
                /* if (pos.dimension().equals(world.getRegistryKey())) { */
                //?}
                    return pos.pos();
                }
                //?} else {
                /* if (pos.getDimension().equals(world.getRegistryKey())) {
                    return pos.getPos();
                }
                *///?}
            }
            return null;
        }
        //? if >=1.20.5 {
        //? if >=26.1 {
        LodestoneTracker lodestoneData = stack.get(DataComponents.LODESTONE_TRACKER);
        //?} else {
        /* LodestoneTrackerComponent lodestoneData = stack.get(DataComponentTypes.LODESTONE_TRACKER); */
        //?}
        if (lodestoneData != null) {
            return lodestoneData.target()
                    //? if >=26.1 {
                    .filter(pos -> pos.dimension().equals(world.dimension()))
                    //?} else {
                    /* .filter(pos -> pos.dimension().equals(world.getRegistryKey())) */
                    //?}
                    .map(GlobalPos::pos)
                    .orElse(null);
        }
        //?} else if >= 1.19 {
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
        *///?}
        //? if >=1.21.9 {
        //? if >=26.1 {
        if (world.dimension().equals(Level.OVERWORLD)) {
            net.minecraft.world.level.storage.LevelData.RespawnData respawnData = world.getLevelData().getRespawnData();
            if (respawnData != null && respawnData.dimension().equals(world.dimension())) {
                return respawnData.pos();
            }
        }
        //?} else {
        /* if (world.getRegistryKey().equals(World.OVERWORLD)) {
            return world.getSpawnPoint().getPos();
        } */
        //?}
        //?} else if >=1.19 && <=1.21.8 {
        /*if (world.getRegistryKey().equals(World.OVERWORLD)) {
            return world.getSpawnPos();
        }
        *///?}
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

    //? if >=26.1 {
    private static void setCompassTarget(PlayerDto dto, Player player, BlockPos target) {
    //?} else {
    /* private static void setCompassTarget(PlayerDto dto, ClientPlayerEntity player, BlockPos target) {
    *///?}
        //? if <=1.14.4 {
        /* Vec3d playerPos = player.getPos();
        *///?} else {
        //? if >=26.1 {
        Vec3 playerPos = new Vec3(player.getX(), player.getY(), player.getZ());
        //?} else {
        /* Vec3d playerPos = new Vec3d(player.getX(), player.getY(), player.getZ()); */
        //?}
        //?}
        //? if >=26.1 {
        Vec3 targetPos = new Vec3(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        //?} else {
        /* Vec3d targetPos = new Vec3d(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5); */
        //?}

        double deltaX = targetPos.x - playerPos.x;
        double deltaZ = targetPos.z - playerPos.z;

        double targetYaw = Math.toDegrees(Math.atan2(-deltaX, deltaZ));

        double playerYaw;
        //? if <=1.16.5 {
        /* playerYaw = player.yaw;
        *///?} else {
        //? if >=26.1 {
        playerYaw = player.getYRot();
        //?} else {
        /* playerYaw = player.getYaw(); */
        //?}
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
