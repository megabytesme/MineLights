package megabytesme.minelights.runtime;

import megabytesme.minelights.MineLightsClient;
import megabytesme.minelights.config.CompassFallbackTarget;
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
//? if >=1.21.6 {
import net.minecraft.world.waypoints.TrackedWaypoint;
//?}
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

public class PlayerDataCollector {
    public static final Logger LOGGER = LogManager.getLogger("MineLights-PlayerDataCollector");
    private static final long PLAYER_SPAWN_REFRESH_NANOS = 250_000_000L;
    private static final Object playerSpawnLookupLock = new Object();
    private static final AtomicReference<PlayerSpawnRequest> pendingPlayerSpawnLookup = new AtomicReference<>();
    private static volatile PlayerSpawnLookup cachedPlayerSpawnLookup;

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
        playerDto.setAbsorption(player.getAbsorptionAmount());
        playerDto.setAirBarVisible(player.isUnderWater() || player.getAirSupply() < player.getMaxAirSupply());
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

        //? if >=1.21.11 {
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

        //? if >=1.21.6 {
        playerDto.setWaypoints(collectWaypoints(client, player, world));
        playerDto.setHudBar(PlayerHudBar.read(client));
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

    //? if >=1.21.6 {
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
        CompassFindResult result = MineLightsClient.CONFIG.ignoreInventoryCompass ? null : findCompass(player);

        if (result == null) {
            BlockPos fallbackPos = showsFallbackCompass() ? getFallbackCompassTarget(player, world) : null;
            if (fallbackPos != null) {
                dto.setCompassType(CompassType.STANDARD);
                setCompassTarget(dto, player, fallbackPos);
            } else if (showsFallbackCompass() && MineLightsClient.CONFIG.spinCompassWithoutTarget) {
                dto.setCompassType(CompassType.STANDARD);
                setCompassSpinning(dto, world.getGameTime(), 0);
            } else {
                dto.setCompassState(CompassState.NONE);
                dto.setCompassType(CompassType.NONE);
            }
            return;
        }

        dto.setCompassType(result.type);
        BlockPos targetPos = getCompassTargetPos(result.stack, player, world);

        if (targetPos != null && player.distanceToSqr(Vec3.atCenterOf(targetPos)) >= 1.0E-5) {
            setCompassTarget(dto, player, targetPos);
        } else if (MineLightsClient.CONFIG.spinCompassWithoutTarget) {
            setCompassSpinning(dto, world.getGameTime(), result.seed);
        } else {
            dto.setCompassState(CompassState.NONE);
            dto.setCompassType(CompassType.NONE);
        }
    }

    private static BlockPos getFallbackCompassTarget(LocalPlayer player, ClientLevel world) {
        CompassFallbackTarget target = MineLightsClient.CONFIG.compassFallbackTarget == null
                ? CompassFallbackTarget.WORLD_SPAWN
                : MineLightsClient.CONFIG.compassFallbackTarget;
        if (target == CompassFallbackTarget.ORIGIN) {
            return new BlockPos(0, 0, 0);
        }
        if (target == CompassFallbackTarget.PLAYER_SPAWN) {
            PlayerSpawnLookup lookup = getPlayerSpawnLookup(player, world);
            return resolvePlayerSpawnFallback(lookup != null, lookup != null && lookup.respawnConfigured,
                    lookup == null ? null : lookup.target, getWorldSpawnPos(world));
        }
        return getWorldSpawnPos(world);
    }

    private static BlockPos getWorldSpawnPos(ClientLevel world) {
        if (!world.dimension().equals(Level.OVERWORLD)) {
            return null;
        }
        //? if >=1.21.9 {
        net.minecraft.world.level.storage.LevelData.RespawnData respawnData = world.getLevelData().getRespawnData();
        return respawnData != null && respawnData.dimension().equals(world.dimension()) ? respawnData.pos() : null;
        //?} else {
        /*return world.getSharedSpawnPos();
        *///?}
    }

    /** The server only shares the player's bed or anchor with the client in singleplayer. */
    private static PlayerSpawnLookup getPlayerSpawnLookup(LocalPlayer player, ClientLevel world) {
        try {
            net.minecraft.client.server.IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
            if (server == null) return null;

            UUID playerId = player.getUUID();
            Object clientWorld = world;
            net.minecraft.resources.ResourceKey<Level> dimensionKey = world.dimension();
            Object dimension = dimensionKey;
            return getCachedPlayerSpawnLookup(server, playerId, clientWorld, dimension,
                    task -> server.submit(task),
                    () -> readPlayerSpawnData(server, playerId, dimensionKey));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static PlayerSpawnData readPlayerSpawnData(net.minecraft.client.server.IntegratedServer server,
            UUID playerId, net.minecraft.resources.ResourceKey<Level> dimension) {
        net.minecraft.server.level.ServerPlayer serverPlayer = server.getPlayerList().getPlayer(playerId);
        if (serverPlayer == null) {
            return new PlayerSpawnData(false, null);
        }
        //? if >=1.21.9 {
        if (serverPlayer.getRespawnConfig() == null) {
            return new PlayerSpawnData(false, null);
        }
        net.minecraft.world.level.storage.LevelData.RespawnData respawnData = serverPlayer.getRespawnConfig().respawnData();
        return new PlayerSpawnData(true,
                respawnData.dimension().equals(dimension) ? respawnData.pos() : null);
        //?} else if >=1.21.5 {
        /* if (serverPlayer.getRespawnConfig() == null) {
            return new PlayerSpawnData(false, null);
        }
        return new PlayerSpawnData(true,
                serverPlayer.getRespawnConfig().dimension().equals(dimension)
                        ? serverPlayer.getRespawnConfig().pos() : null);
        *///?} else {
        /* if (serverPlayer.getRespawnPosition() == null) {
            return new PlayerSpawnData(false, null);
        }
        return new PlayerSpawnData(true,
                serverPlayer.getRespawnDimension().equals(dimension) ? serverPlayer.getRespawnPosition() : null);
        *///?}
    }

    private static PlayerSpawnLookup getCachedPlayerSpawnLookup(Object server, Object playerId, Object clientWorld,
            Object dimension, Function<Runnable, ? extends Future<?>> scheduleOnServer, Supplier<PlayerSpawnData> readOnServer) {
        long now = System.nanoTime();
        PlayerSpawnLookup snapshot = cachedPlayerSpawnLookup;
        boolean matches = snapshot != null && snapshot.matches(server, playerId, clientWorld, dimension);
        if (matches && now - snapshot.updatedAtNanos < PLAYER_SPAWN_REFRESH_NANOS) {
            return snapshot;
        }

        PlayerSpawnRequest request = new PlayerSpawnRequest(server, playerId, clientWorld, dimension);
        boolean scheduleRequest = false;
        synchronized (playerSpawnLookupLock) {
            PlayerSpawnRequest pending = pendingPlayerSpawnLookup.get();
            if (pending == null || !pending.matches(server, playerId, clientWorld, dimension)) {
                if (pending != null) {
                    pending.cancel();
                }
                pendingPlayerSpawnLookup.set(request);
                scheduleRequest = true;
            }
        }
        if (scheduleRequest) {
            try {
                Future<?> scheduled = scheduleOnServer.apply(() -> {
                    PlayerSpawnData data;
                    try {
                        data = readOnServer.get();
                    } catch (RuntimeException e) {
                        data = new PlayerSpawnData(false, null);
                    }
                    PlayerSpawnLookup result = new PlayerSpawnLookup(
                            server, playerId, clientWorld, dimension,
                            data.respawnConfigured, data.target, System.nanoTime());
                    synchronized (playerSpawnLookupLock) {
                        if (pendingPlayerSpawnLookup.get() == request) {
                            cachedPlayerSpawnLookup = result;
                            pendingPlayerSpawnLookup.set(null);
                        }
                    }
                });
                synchronized (playerSpawnLookupLock) {
                    if (pendingPlayerSpawnLookup.get() == request) {
                        request.setFuture(scheduled);
                    } else {
                        scheduled.cancel(false);
                    }
                }
            } catch (RuntimeException e) {
                synchronized (playerSpawnLookupLock) {
                    if (pendingPlayerSpawnLookup.get() == request) {
                        pendingPlayerSpawnLookup.set(null);
                    }
                }
            }
        }

        return matches ? snapshot : null;
    }

    static BlockPos resolvePlayerSpawnFallback(boolean lookupAvailable, boolean respawnConfigured,
            BlockPos playerSpawn, BlockPos worldSpawn) {
        if (!lookupAvailable || !respawnConfigured) {
            return worldSpawn;
        }
        return playerSpawn;
    }

    private static class PlayerSpawnData {
        final boolean respawnConfigured;
        final BlockPos target;

        PlayerSpawnData(boolean respawnConfigured, BlockPos target) {
            this.respawnConfigured = respawnConfigured;
            this.target = target;
        }
    }

    private static class PlayerSpawnLookup {
        final Object server;
        final Object playerId;
        final Object clientWorld;
        final Object dimension;
        final boolean respawnConfigured;
        final BlockPos target;
        final long updatedAtNanos;

        PlayerSpawnLookup(Object server, Object playerId, Object clientWorld, Object dimension,
                boolean respawnConfigured, BlockPos target, long updatedAtNanos) {
            this.server = server;
            this.playerId = playerId;
            this.clientWorld = clientWorld;
            this.dimension = dimension;
            this.respawnConfigured = respawnConfigured;
            this.target = target;
            this.updatedAtNanos = updatedAtNanos;
        }

        boolean matches(Object server, Object playerId, Object clientWorld, Object dimension) {
            return this.server == server && this.clientWorld == clientWorld
                    && this.playerId.equals(playerId) && this.dimension.equals(dimension);
        }
    }

    private static class PlayerSpawnRequest {
        final Object server;
        final Object playerId;
        final Object clientWorld;
        final Object dimension;
        private volatile Future<?> future;

        PlayerSpawnRequest(Object server, Object playerId, Object clientWorld, Object dimension) {
            this.server = server;
            this.playerId = playerId;
            this.clientWorld = clientWorld;
            this.dimension = dimension;
        }

        boolean matches(Object server, Object playerId, Object clientWorld, Object dimension) {
            return this.server == server && this.clientWorld == clientWorld
                    && this.playerId.equals(playerId) && this.dimension.equals(dimension);
        }

        void setFuture(Future<?> future) {
            this.future = future;
        }

        void cancel() {
            Future<?> future = this.future;
            if (future != null) {
                future.cancel(false);
            }
        }
    }

    // Same wobble as Minecraft's compass needle when it has no target (CompassAngleState):
    // the needle chases a new random angle each game tick, and the seed offsets it per slot.
    private static float spinWobbleRotation;
    private static float spinWobbleStep;
    private static long spinWobbleTime = Long.MIN_VALUE;

    private static void setCompassSpinning(PlayerDto dto, long time, int seed) {
        if (time != spinWobbleTime) {
            spinWobbleTime = time;
            float delta = floorMod((float) Math.random() - spinWobbleRotation + 0.5F, 1.0F) - 0.5F;
            spinWobbleStep += delta * 0.1F;
            spinWobbleStep *= 0.8F;
            spinWobbleRotation = floorMod(spinWobbleRotation + spinWobbleStep, 1.0F);
        }
        float rotation = floorMod(spinWobbleRotation + (float) (seed * 1327217883) / 2.1474836E9F, 1.0F);
        dto.setCompassState(CompassState.SPINNING);
        dto.setCompassSpinRotation(rotation * 360.0D);
    }

    private static float floorMod(float value, float mod) {
        return (value % mod + mod) % mod;
    }

    // Minecraft passes slot + 1 as the needle seed for hotbar slots and 10 for the offhand.
    private static int compassSeed(List<ItemStack> inventory, int index) {
        int hands = inventory.size() - 36;
        if (index == 0) {
            for (int slot = 0; slot < 9; slot++) {
                if (inventory.get(hands + slot) == inventory.get(0)) {
                    return slot + 1;
                }
            }
            return 0;
        }
        if (index < hands) {
            return 10;
        }
        int slot = index - hands;
        return slot < 9 ? slot + 1 : 0;
    }

    private static boolean showsFallbackCompass() {
        return MineLightsClient.CONFIG.alwaysShowCompass || MineLightsClient.CONFIG.ignoreInventoryCompass;
    }

    private static class CompassFindResult {
        final ItemStack stack;
        final CompassType type;
        final int seed;

        CompassFindResult(ItemStack stack, CompassType type) {
            this(stack, type, 0);
        }

        CompassFindResult(ItemStack stack, CompassType type, int seed) {
            this.stack = stack;
            this.type = type;
            this.seed = seed;
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

        for (int index = 0; index < inventory.size(); index++) {
            ItemStack stack = inventory.get(index);
            if (stack.isEmpty()) continue;
            int seed = compassSeed(inventory, index);

            if (stack.getItem() == Items.RECOVERY_COMPASS) {
                foundCompasses.add(new CompassFindResult(stack, CompassType.RECOVERY, seed));
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
                    foundCompasses.add(new CompassFindResult(stack, CompassType.LODESTONE, seed));
                } else {
                    foundCompasses.add(new CompassFindResult(stack, CompassType.STANDARD, seed));
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
