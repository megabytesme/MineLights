package megabytesme.minelights.runtime;

import megabytesme.minelights.MineLightsClient;
import megabytesme.minelights.accessor.ChatReceivedAccessor;
import megabytesme.minelights.accessor.PlayerVisualBrightnessAccessor;
import megabytesme.minelights.mixin.LightningAccessor;
import megabytesme.minelights.model.CompassState;
import megabytesme.minelights.model.CompassType;
import megabytesme.minelights.model.PlayerDto;
import megabytesme.minelights.model.WaypointDto;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
//? if >=1.20.5 {
import net.minecraft.core.component.DataComponents;
//?}
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
//? if >=1.20.5 {
import net.minecraft.world.item.component.LodestoneTracker;
//?}
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
//? if >=1.21.8 {
import net.minecraft.world.waypoints.TrackedWaypoint;
//?}
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PlayerDataCollector {
    public static final Logger LOGGER = LogManager.getLogger("MineLights-PlayerDataCollector");

    public static PlayerDto getCurrentState(Minecraft client) {
        PlayerDto playerDto = new PlayerDto();
        if (client == null || client.level == null || client.player == null) {
            playerDto.setInGame(false);
            return playerDto;
        }

        LocalPlayer player = client.player;
        ClientLevel world = client.level;

        playerDto.setInGame(true);
        playerDto.setHealth(player.getHealth());
        playerDto.setHunger(player.getFoodData().getFoodLevel());
        playerDto.setSaturation(player.getFoodData().getSaturationLevel());
        playerDto.setAir(player.getAirSupply());
        playerDto.setExperience(player.experienceProgress);

        BlockPos playerPos = player.blockPosition();
        playerDto.setBlockAtFeet(BuiltInRegistries.BLOCK.getKey(world.getBlockState(playerPos).getBlock()).toString());
        playerDto.setBlockOn(BuiltInRegistries.BLOCK.getKey(world.getBlockState(playerPos.below()).getBlock()).toString());
        Vec3 eyePos = player.getEyePosition();
        BlockPos headPos = BlockPos.containing(eyePos.x, eyePos.y, eyePos.z);
        playerDto.setBlockAtHead(BuiltInRegistries.BLOCK.getKey(world.getBlockState(headPos).getBlock()).toString());

        //? if >=26.1 {
        world.getBiome(playerPos).unwrapKey().ifPresent(key -> playerDto.setCurrentBiome(key.identifier().toString()));
        playerDto.setCurrentWorld(world.dimension().identifier().toString());
        //?} else {
        /* world.getBiome(playerPos).unwrapKey().ifPresent(key -> playerDto.setCurrentBiome(key.location().toString()));
        playerDto.setCurrentWorld(world.dimension().location().toString()); */
        //?}

        playerDto.setIsOnFire(player.isOnFire());
        playerDto.setIsPoisoned(player.hasEffect(MobEffects.POISON));
        playerDto.setIsWithering(player.hasEffect(MobEffects.WITHER));
        playerDto.setIsTakingDamage(player.hurtTime > 0);
        updateCompassData(playerDto, player, world);

        if (world.isThundering()) {
            playerDto.setWeather("Thunderstorm");
        } else if (world.isRaining()) {
            playerDto.setWeather("Rain");
        } else {
            playerDto.setWeather("Clear");
        }

        //? if >=1.21.9 {
        if (world.dimension().equals(Level.END)) {
            float intensity = world.endFlashState().getIntensity(client.getDeltaTracker().getGameTimeDeltaPartialTick(true));
            playerDto.setEndFlashIntensity(intensity);
        } else {
            playerDto.setEndFlashIntensity(0.0f);
        }
        //?} else {
        playerDto.setEndFlashIntensity(0.0f);
        //?}

        //? if >=1.21.8 {
        playerDto.setWaypoints(collectWaypoints(client, player, world));
        //?} else {
        playerDto.setWaypoints(new ArrayList<>());
        //?}

        for (Entity entity : world.entitiesForRendering()) {
            if (entity instanceof LightningBolt lightning) {
                LightningAccessor acc = (LightningAccessor) lightning;
                int ambientTick = acc.getAmbientTick();
                int remainingActions = acc.getRemainingActions();
                playerDto.setIsLightningFlashing((ambientTick % 3) < 2 && remainingActions > 0);
            }
        }

        playerDto.setSkyLightLevel(((PlayerVisualBrightnessAccessor) player).getSkyLightLevel());
        playerDto.setRenderedBrightnessLevel(((PlayerVisualBrightnessAccessor) player).getRenderedBrightness());

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

        return playerDto;
    }

    //? if >=1.21.8 {
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
        //? if >=1.21.9 {
        waypointDto.setRelativeYaw(normalizeYaw(trackedWaypoint.yawAngleToCamera(world, camera, entity -> partialTick)));
        //?} else {
        /* waypointDto.setRelativeYaw(normalizeYaw(trackedWaypoint.yawAngleToCamera(world, camera))); */
        //?}
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

    private static void updateCompassData(PlayerDto dto, LocalPlayer player, ClientLevel world) {
        CompassFindResult result = findCompass(player);

        if (result == null) {
            //? if >=1.21.9 {
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
                dto.setCompassState(CompassState.NONE);
                dto.setCompassType(CompassType.NONE);
            }
            //?} else {
            if (MineLightsClient.CONFIG.alwaysShowCompass && world.dimension().equals(Level.OVERWORLD)) {
                dto.setCompassType(CompassType.STANDARD);
                setCompassTarget(dto, player, world.getSharedSpawnPos());
            } else {
                dto.setCompassState(CompassState.NONE);
                dto.setCompassType(CompassType.NONE);
            }
            //?}
            return;
        }

        dto.setCompassType(result.type);
        BlockPos targetPos = getCompassTargetPos(result.stack, player, world);

        if (targetPos != null && player.distanceToSqr(Vec3.atCenterOf(targetPos)) >= 1.0E-5) {
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

    private static CompassFindResult findCompass(Player player) {
        List<CompassFindResult> foundCompasses = new ArrayList<>();
        List<ItemStack> inventory = new ArrayList<>();
        inventory.add(player.getMainHandItem());
        inventory.add(player.getOffhandItem());
        for (int i = 0; i < 36; i++) {
            inventory.add(player.getInventory().getItem(i));
        }

        for (ItemStack stack : inventory) {
            if (stack.isEmpty()) continue;

            if (stack.getItem() == Items.RECOVERY_COMPASS) {
                foundCompasses.add(new CompassFindResult(stack, CompassType.RECOVERY));
                continue;
            }

            if (stack.getItem() == Items.COMPASS) {
                boolean isLodestone = false;
                //? if >=1.20.5 {
                LodestoneTracker lodestoneData = stack.get(DataComponents.LODESTONE_TRACKER);
                if (lodestoneData != null && lodestoneData.target().isPresent()) {
                    isLodestone = true;
                }
                //?} else {
                if (stack.hasTag()) {
                    CompoundTag tag = stack.getTag();
                    if (tag != null && tag.contains("LodestonePos")) isLodestone = true;
                }
                //?}

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
            case RECOVERY_FIRST:
                return foundCompasses.stream().filter(r -> r.type == CompassType.RECOVERY).findFirst().orElse(foundCompasses.get(0));
            case PRIORITY:
            default:
                return foundCompasses.get(0);
        }
    }

    //? if >=1.21.9 {
    private static BlockPos getCompassTargetPos(ItemStack stack, Player holder, ClientLevel world) {
        if (stack.getItem() == Items.RECOVERY_COMPASS) {
            Optional<GlobalPos> lastDeathPos = holder.getLastDeathLocation();
            if (lastDeathPos.isPresent()) {
                GlobalPos pos = lastDeathPos.get();
                if (pos.dimension().equals(world.dimension())) {
                    return pos.pos();
                }
            }
            return null;
        }

        LodestoneTracker lodestoneData = stack.get(DataComponents.LODESTONE_TRACKER);
        if (lodestoneData != null) {
            return lodestoneData.target()
                    .filter(pos -> pos.dimension().equals(world.dimension()))
                    .map(GlobalPos::pos)
                    .orElse(null);
        }

        if (world.dimension().equals(Level.OVERWORLD)) {
            net.minecraft.world.level.storage.LevelData.RespawnData respawnData = world.getLevelData().getRespawnData();
            if (respawnData != null && respawnData.dimension().equals(world.dimension())) {
                return respawnData.pos();
            }
        }
        return null;
    }
    //?} else if >=1.20.5 {
    private static BlockPos getCompassTargetPos(ItemStack stack, Player holder, ClientLevel world) {
        if (stack.getItem() == Items.RECOVERY_COMPASS) {
            Optional<GlobalPos> lastDeathPos = holder.getLastDeathLocation();
            if (lastDeathPos.isPresent()) {
                GlobalPos pos = lastDeathPos.get();
                if (pos.dimension().equals(world.dimension())) {
                    return pos.pos();
                }
            }
            return null;
        }

        LodestoneTracker lodestoneData = stack.get(DataComponents.LODESTONE_TRACKER);
        if (lodestoneData != null) {
            return lodestoneData.target()
                    .filter(pos -> pos.dimension().equals(world.dimension()))
                    .map(GlobalPos::pos)
                    .orElse(null);
        }

        if (world.dimension().equals(Level.OVERWORLD)) {
            return world.getSharedSpawnPos();
        }
        return null;
    }
    //?} else {
    private static BlockPos getCompassTargetPos(ItemStack stack, Player holder, ClientLevel world) {
        if (stack.getItem() == Items.RECOVERY_COMPASS) {
            Optional<GlobalPos> lastDeathPos = holder.getLastDeathLocation();
            if (lastDeathPos.isPresent()) {
                GlobalPos pos = lastDeathPos.get();
                if (pos.dimension().equals(world.dimension())) {
                    return pos.pos();
                }
            }
            return null;
        }

        if (stack.hasTag()) {
            CompoundTag tag = stack.getTag();
            if (tag != null && tag.contains("LodestonePos") && tag.contains("LodestoneDimension")) {
                String lodestoneDim = tag.getString("LodestoneDimension");
                if (world.dimension().location().toString().equals(lodestoneDim)) {
                    CompoundTag posTag = tag.getCompound("LodestonePos");
                    return new BlockPos(posTag.getInt("X"), posTag.getInt("Y"), posTag.getInt("Z"));
                }
                return null;
            }
        }

        if (world.dimension().equals(Level.OVERWORLD)) {
            return world.getSharedSpawnPos();
        }
        return null;
    }
    //?}

    private static void setCompassTarget(PlayerDto dto, Player player, BlockPos target) {
        Vec3 playerPos = new Vec3(player.getX(), player.getY(), player.getZ());
        Vec3 targetPos = new Vec3(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);

        double deltaX = targetPos.x - playerPos.x;
        double deltaZ = targetPos.z - playerPos.z;
        double targetYaw = Math.toDegrees(Math.atan2(-deltaX, deltaZ));
        double playerYaw = player.getYRot();

        double relativeYaw = targetYaw - playerYaw;
        while (relativeYaw <= -180.0D) relativeYaw += 360.0D;
        while (relativeYaw > 180.0D) relativeYaw -= 360.0D;

        dto.setCompassState(CompassState.POINTING);
        dto.setCompassRelativeYaw(relativeYaw);
        dto.setCompassDistance(Math.sqrt(deltaX * deltaX + deltaZ * deltaZ));
    }
}
