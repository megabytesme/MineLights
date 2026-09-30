package megabytesme.minelights.runtime;

import megabytesme.minelights.MineLightsClient;
//? if loader_forge && <=1.14.3 {
import megabytesme.minelights.MineLightsForge;
//?}
import megabytesme.minelights.accessor.ChatReceivedAccessor;
import megabytesme.minelights.accessor.PlayerVisualBrightnessAccessor;
//? if loader_forge && <=1.14.3 {
//?} else {
import megabytesme.minelights.mixin.LightningAccessor;
//?}
import megabytesme.minelights.model.CompassState;
import megabytesme.minelights.model.CompassType;
import megabytesme.minelights.model.PlayerDto;
import megabytesme.minelights.model.WaypointDto;
//? if loader_forge && <=1.7.10 {
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.Vec3;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.ReflectionHelper;
//?} else if loader_forge && <=1.8.9 {
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.registry.GameRegistry;
//?} else if loader_forge && <=1.12.2 {
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.DimensionType;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
//?} else if loader_forge && 1.13.2 {
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EnumLightType;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistries;
//?} else if loader_forge && <=1.15.2 {
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.network.play.ClientPlayNetHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.LightningBoltEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.potion.Effects;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import net.minecraftforge.registries.ForgeRegistries;
//? if loader_forge && <=1.14.3 {
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraft.world.LightType;
//?}
//?} else if loader_forge && <1.17 {
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.network.play.ClientPlayNetHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.LightningBoltEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.potion.Effects;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraftforge.registries.ForgeRegistries;
//? if loader_forge && 1.16.1 {
import net.minecraft.world.DimensionType;
import net.minecraft.world.LightType;
import net.minecraft.world.storage.ISpawnWorldInfo;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
//?}
//?} else {
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
//? if >=1.19.3 {
import net.minecraft.core.registries.BuiltInRegistries;
//?} else {
import net.minecraft.core.Registry;
//?}
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
//?}
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
        //? if loader_forge && <=1.9.4 {
        if (client == null || client.theWorld == null || client.thePlayer == null) {
        //?} else if (loader_forge && <=1.14.3) || (loader_forge && 1.16.1) {
        if (client == null || client.world == null || client.player == null) {
        //?} else {
        /* if (client == null || client.level == null || client.player == null) {
        *///?}
            playerDto.setInGame(false);
            return playerDto;
        }

        //? if loader_forge && <=1.9.4 {
        EntityPlayerSP player = client.thePlayer;
        WorldClient world = client.theWorld;
        //?} else if loader_forge && <=1.13.2 {
        EntityPlayerSP player = client.player;
        WorldClient world = client.world;
        //?} else if (loader_forge && <=1.14.3) || (loader_forge && 1.16.1) {
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        //?} else if loader_forge && <1.17 {
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.level;
        //?} else {
        LocalPlayer player = client.player;
        ClientLevel world = client.level;
        //?}

        playerDto.setInGame(true);
        playerDto.setHealth(player.getHealth());
        //? if (loader_forge && <=1.14.3) || (loader_forge && 1.16.1) {
        playerDto.setHunger(player.getFoodStats().getFoodLevel());
        playerDto.setSaturation(player.getFoodStats().getSaturationLevel());
        playerDto.setAir(player.getAir());
        playerDto.setExperience(player.experience);
        //?} else {
        playerDto.setHunger(player.getFoodData().getFoodLevel());
        playerDto.setSaturation(player.getFoodData().getSaturationLevel());
        playerDto.setAir(player.getAirSupply());
        playerDto.setExperience(player.experienceProgress);
        //?}

        //? if loader_forge && <=1.7.10 {
        ChunkCoordinates playerPos = new ChunkCoordinates((int) Math.floor(player.posX), (int) Math.floor(player.posY), (int) Math.floor(player.posZ));
        //?} else if loader_forge && <=1.12.2 {
        BlockPos playerPos = new BlockPos(player.posX, player.posY, player.posZ);
        //?} else if loader_forge && <=1.14.3 {
        BlockPos playerPos = new BlockPos(player.posX, player.posY, player.posZ);
        //?} else if loader_forge && 1.16.1 {
        BlockPos playerPos = new BlockPos(player.getPosX(), player.getPosY(), player.getPosZ());
        //?} else if loader_forge && <=1.14.4 {
        BlockPos playerPos = new BlockPos(player.x, player.y, player.z);
        //?} else if loader_forge && <=1.15.2 {
        BlockPos playerPos = new BlockPos(player.getX(), player.getY(), player.getZ());
        //?} else {
        BlockPos playerPos = player.blockPosition();
        //?}
        //? if loader_forge && <=1.7.10 {
        playerDto.setBlockAtFeet(GameRegistry.findUniqueIdentifierFor(world.getBlock(playerPos.posX, playerPos.posY, playerPos.posZ)).toString());
        playerDto.setBlockOn(GameRegistry.findUniqueIdentifierFor(world.getBlock(playerPos.posX, playerPos.posY - 1, playerPos.posZ)).toString());
        //?} else if loader_forge && <=1.8.9 {
        playerDto.setBlockAtFeet(GameRegistry.findUniqueIdentifierFor(world.getBlockState(playerPos).getBlock()).toString());
        playerDto.setBlockOn(GameRegistry.findUniqueIdentifierFor(world.getBlockState(playerPos.down()).getBlock()).toString());
        //?} else if (loader_forge && <=1.15.2) || (loader_forge && 1.16.1) {
        playerDto.setBlockAtFeet(ForgeRegistries.BLOCKS.getKey(world.getBlockState(playerPos).getBlock()).toString());
        //? if (loader_forge && <=1.14.3) || (loader_forge && 1.16.1) {
        playerDto.setBlockOn(ForgeRegistries.BLOCKS.getKey(world.getBlockState(playerPos.down()).getBlock()).toString());
        //?} else {
        playerDto.setBlockOn(ForgeRegistries.BLOCKS.getKey(world.getBlockState(playerPos.below()).getBlock()).toString());
        //?}
        //?} else if >=1.19.3 {
        playerDto.setBlockAtFeet(BuiltInRegistries.BLOCK.getKey(world.getBlockState(playerPos).getBlock()).toString());
        playerDto.setBlockOn(BuiltInRegistries.BLOCK.getKey(world.getBlockState(playerPos.below()).getBlock()).toString());
        //?} else {
        /* playerDto.setBlockAtFeet(Registry.BLOCK.getKey(world.getBlockState(playerPos).getBlock()).toString());
        playerDto.setBlockOn(Registry.BLOCK.getKey(world.getBlockState(playerPos.below()).getBlock()).toString());
        *///?}
        //? if loader_forge && <=1.7.10 {
        Vec3 eyePos = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        //?} else if loader_forge && <=1.8.9 {
        Vec3 eyePos = player.getPositionEyes(1.0F);
        //?} else if loader_forge && <=1.12.2 {
        Vec3d eyePos = player.getPositionEyes(1.0F);
        //?} else if loader_forge && <=1.15.2 {
        Vec3d eyePos = player.getEyePosition(1.0F);
        //?} else if loader_forge && <1.17 {
        Vector3d eyePos = player.getEyePosition(1.0F);
        //?} else {
        Vec3 eyePos = player.getEyePosition();
        //?}
        //? if loader_forge && <=1.7.10 {
        ChunkCoordinates headPos = new ChunkCoordinates((int) Math.floor(eyePos.xCoord), (int) Math.floor(eyePos.yCoord), (int) Math.floor(eyePos.zCoord));
        //?} else if loader_forge && <=1.8.9 {
        BlockPos headPos = new BlockPos(eyePos.xCoord, eyePos.yCoord, eyePos.zCoord);
        //?} else if loader_forge && <=1.10.2 {
        BlockPos headPos = new BlockPos(eyePos.xCoord, eyePos.yCoord, eyePos.zCoord);
        //?} else if loader_forge && <=1.12.2 {
        BlockPos headPos = new BlockPos(eyePos.x, eyePos.y, eyePos.z);
        //?} else if >=1.19.4 {
        BlockPos headPos = BlockPos.containing(eyePos.x, eyePos.y, eyePos.z);
        //?} else {
        /* BlockPos headPos = new BlockPos(eyePos.x, eyePos.y, eyePos.z);
        *///?}
        //? if loader_forge && <=1.7.10 {
        playerDto.setBlockAtHead(GameRegistry.findUniqueIdentifierFor(world.getBlock(headPos.posX, headPos.posY, headPos.posZ)).toString());
        //?} else if loader_forge && <=1.8.9 {
        playerDto.setBlockAtHead(GameRegistry.findUniqueIdentifierFor(world.getBlockState(headPos).getBlock()).toString());
        //?} else if loader_forge && <=1.15.2 {
        playerDto.setBlockAtHead(ForgeRegistries.BLOCKS.getKey(world.getBlockState(headPos).getBlock()).toString());
        //?} else if >=1.19.3 {
        playerDto.setBlockAtHead(BuiltInRegistries.BLOCK.getKey(world.getBlockState(headPos).getBlock()).toString());
        //?} else {
        /* playerDto.setBlockAtHead(Registry.BLOCK.getKey(world.getBlockState(headPos).getBlock()).toString());
        *///?}

        //? if >=1.21.11 {
        world.getBiome(playerPos).unwrapKey().ifPresent(key -> playerDto.setCurrentBiome(key.identifier().toString()));
        playerDto.setCurrentWorld(getDimensionId(world));
        //?} else if >=1.18.2 {
        /* world.getBiome(playerPos).unwrapKey().ifPresent(key -> playerDto.setCurrentBiome(key.location().toString()));
        playerDto.setCurrentWorld(getDimensionId(world)); */
        //?} else if loader_forge && <=1.7.10 {
        playerDto.setCurrentBiome("minecraft:" + world.getBiomeGenForCoords(playerPos.posX, playerPos.posZ).biomeName.toLowerCase(java.util.Locale.ROOT).replace(' ', '_'));
        playerDto.setCurrentWorld(getDimensionId(world));
        //?} else if loader_forge && <=1.8.9 {
        playerDto.setCurrentBiome("minecraft:" + world.getBiomeGenForCoords(playerPos).biomeName.toLowerCase(java.util.Locale.ROOT).replace(' ', '_'));
        playerDto.setCurrentWorld(getDimensionId(world));
        //?} else if loader_forge && 1.9 {
        playerDto.setCurrentBiome(ForgeRegistries.BIOMES.getKey(world.getBiomeGenForCoords(playerPos)).toString());
        playerDto.setCurrentWorld(getDimensionId(world));
        //?} else if loader_forge && 1.9.4 {
        playerDto.setCurrentBiome(ForgeRegistries.BIOMES.getKey(world.getBiome(playerPos)).toString());
        playerDto.setCurrentWorld(getDimensionId(world));
        //?} else if loader_forge && <1.17 {
        playerDto.setCurrentBiome(ForgeRegistries.BIOMES.getKey(world.getBiome(playerPos)).toString());
        playerDto.setCurrentWorld(getDimensionId(world));
        //?} else {
        /* playerDto.setCurrentBiome(world.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY)
                .getKey(world.getBiome(playerPos)).toString());
        playerDto.setCurrentWorld(getDimensionId(world)); */
        //?}

        //? if loader_forge && <=1.8.9 {
        playerDto.setIsOnFire(player.isBurning());
        playerDto.setIsPoisoned(player.isPotionActive(Potion.poison));
        playerDto.setIsWithering(player.isPotionActive(Potion.wither));
        //?} else if loader_forge && <=1.12.2 {
        playerDto.setIsOnFire(player.isBurning());
        playerDto.setIsPoisoned(player.isPotionActive(MobEffects.POISON));
        playerDto.setIsWithering(player.isPotionActive(MobEffects.WITHER));
        //?} else if loader_forge && <=1.13.2 {
        playerDto.setIsOnFire(player.isBurning());
        playerDto.setIsPoisoned(player.isPotionActive(MobEffects.POISON));
        playerDto.setIsWithering(player.isPotionActive(MobEffects.WITHER));
        //?} else if loader_forge && 1.16.1 {
        playerDto.setIsOnFire(player.isBurning());
        playerDto.setIsPoisoned(player.isPotionActive(Effects.POISON));
        playerDto.setIsWithering(player.isPotionActive(Effects.WITHER));
        //?} else if (loader_forge && <=1.14.3) || (loader_forge && 1.16.1) {
        playerDto.setIsOnFire(player.isBurning());
        playerDto.setIsPoisoned(player.isPotionActive(Effects.POISON));
        playerDto.setIsWithering(player.isPotionActive(Effects.WITHER));
        //?} else {
        playerDto.setIsOnFire(player.isOnFire());
        //? if loader_forge && <1.17 {
        playerDto.setIsPoisoned(player.hasEffect(Effects.POISON));
        playerDto.setIsWithering(player.hasEffect(Effects.WITHER));
        //?} else {
        playerDto.setIsPoisoned(player.hasEffect(MobEffects.POISON));
        playerDto.setIsWithering(player.hasEffect(MobEffects.WITHER));
        //?}
        //?}
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

        //? if loader_forge && <=1.8.9 {
        for (Object object : world.getLoadedEntityList()) {
            Entity entity = (Entity) object;
            if (entity instanceof EntityLightningBolt) {
                EntityLightningBolt lightning = (EntityLightningBolt) entity;
                    //? if loader_forge && <=1.7.10 {
                    int ambientTick = ReflectionHelper.getPrivateValue(EntityLightningBolt.class, lightning, "lightningState", "field_70261_a");
                    int remainingActions = ReflectionHelper.getPrivateValue(EntityLightningBolt.class, lightning, "boltLivingTime", "field_70259_b");
                    //?} else {
                int ambientTick = ObfuscationReflectionHelper.getPrivateValue(EntityLightningBolt.class, lightning, "lightningState", "field_70261_a");
                int remainingActions = ObfuscationReflectionHelper.getPrivateValue(EntityLightningBolt.class, lightning, "boltLivingTime", "field_70259_b");
                    //?}
                playerDto.setIsLightningFlashing((ambientTick % 3) < 2 && remainingActions > 0);
            }
        }
        //?} else if loader_forge && <=1.12.2 {
        for (Entity entity : world.getLoadedEntityList()) {
            if (entity instanceof EntityLightningBolt) {
                EntityLightningBolt lightning = (EntityLightningBolt) entity;
                int ambientTick = ObfuscationReflectionHelper.getPrivateValue(EntityLightningBolt.class, lightning, "lightningState", "field_70261_a");
                int remainingActions = ObfuscationReflectionHelper.getPrivateValue(EntityLightningBolt.class, lightning, "boltLivingTime", "field_70259_b");
                playerDto.setIsLightningFlashing((ambientTick % 3) < 2 && remainingActions > 0);
            }
        }
        //?} else if loader_forge && 1.13.2 {
        for (EntityLightningBolt lightning : world.getEntities(EntityLightningBolt.class, candidate -> true)) {
            int ambientTick = ObfuscationReflectionHelper.getPrivateValue(EntityLightningBolt.class, lightning, "lightningState");
            int remainingActions = ObfuscationReflectionHelper.getPrivateValue(EntityLightningBolt.class, lightning, "boltLivingTime");
            playerDto.setIsLightningFlashing((ambientTick % 3) < 2 && remainingActions > 0);
        }
        //?} else if loader_forge && 1.14.2 {
        for (Entity entity : world.func_217416_b()) {
            if (entity instanceof LightningBoltEntity) {
                LightningBoltEntity lightning = (LightningBoltEntity) entity;
                int ambientTick = ObfuscationReflectionHelper.getPrivateValue(LightningBoltEntity.class, lightning, "lightningState");
                int remainingActions = ObfuscationReflectionHelper.getPrivateValue(LightningBoltEntity.class, lightning, "boltLivingTime");
                playerDto.setIsLightningFlashing((ambientTick % 3) < 2 && remainingActions > 0);
            }
        }
        //?} else if loader_forge && 1.14.3 {
        for (Entity entity : world.getAllEntities()) {
            if (entity instanceof LightningBoltEntity) {
                LightningBoltEntity lightning = (LightningBoltEntity) entity;
                int ambientTick = ObfuscationReflectionHelper.getPrivateValue(LightningBoltEntity.class, lightning, "lightningState");
                int remainingActions = ObfuscationReflectionHelper.getPrivateValue(LightningBoltEntity.class, lightning, "boltLivingTime");
                playerDto.setIsLightningFlashing((ambientTick % 3) < 2 && remainingActions > 0);
            }
        }
        //?} else if loader_forge && 1.16.1 {
        for (Entity entity : world.getAllEntities()) {
            if (entity instanceof LightningBoltEntity) {
                LightningAccessor acc = (LightningAccessor) entity;
                int ambientTick = acc.getAmbientTick();
                int remainingActions = acc.getRemainingActions();
                playerDto.setIsLightningFlashing((ambientTick % 3) < 2 && remainingActions > 0);
            }
        }
        //?} else if loader_forge && <1.17 {
        for (Entity entity : world.entitiesForRendering()) {
            if (entity instanceof LightningBoltEntity) {
                LightningAccessor acc = (LightningAccessor) entity;
                int ambientTick = acc.getAmbientTick();
                int remainingActions = acc.getRemainingActions();
                playerDto.setIsLightningFlashing((ambientTick % 3) < 2 && remainingActions > 0);
            }
        }
        //?} else {
        /* for (Entity entity : world.entitiesForRendering()) {
            if (entity instanceof LightningBolt) {
                LightningAccessor acc = (LightningAccessor) entity;
                int ambientTick = acc.getAmbientTick();
                int remainingActions = acc.getRemainingActions();
                playerDto.setIsLightningFlashing((ambientTick % 3) < 2 && remainingActions > 0);
            }
        }
        *///?}

        //? if loader_forge && <=1.7.10 {
        int x = (int) Math.floor(player.posX);
        int y = (int) Math.floor(player.posY);
        int z = (int) Math.floor(player.posZ);
        int skyLight = world.getSavedLightValue(EnumSkyBlock.Sky, x, y, z);
        int blockLight = world.getSavedLightValue(EnumSkyBlock.Block, x, y, z);
        playerDto.setSkyLightLevel(skyLight);
        float brightness = (float) Math.max(blockLight, skyLight) / 15.0F;
        playerDto.setRenderedBrightnessLevel(brightness / (4.0F - 3.0F * brightness));
        //?} else if loader_forge && <=1.12.2 {
        BlockPos brightnessPos = new BlockPos(player.posX, player.posY, player.posZ);
        int skyLight = world.getLightFor(EnumSkyBlock.SKY, brightnessPos);
        int blockLight = world.getLightFor(EnumSkyBlock.BLOCK, brightnessPos);
        playerDto.setSkyLightLevel(skyLight);
        float brightness = (float) Math.max(blockLight, skyLight) / 15.0F;
        playerDto.setRenderedBrightnessLevel(brightness / (4.0F - 3.0F * brightness));
        //?} else if loader_forge && 1.13.2 {
        BlockPos brightnessPos = new BlockPos(player.posX, player.posY, player.posZ);
        int skyLight = world.getLightFor(EnumLightType.SKY, brightnessPos);
        int blockLight = world.getLightFor(EnumLightType.BLOCK, brightnessPos);
        playerDto.setSkyLightLevel(skyLight);
        float brightness = (float) Math.max(blockLight, skyLight) / 15.0F;
        playerDto.setRenderedBrightnessLevel(brightness / (4.0F - 3.0F * brightness));
        //?} else if loader_forge && <=1.14.3 {
        BlockPos brightnessPos = new BlockPos(player.posX, player.posY, player.posZ);
        int skyLight = world.getLightFor(LightType.SKY, brightnessPos);
        int blockLight = world.getLightFor(LightType.BLOCK, brightnessPos);
        playerDto.setSkyLightLevel(skyLight);
        float brightness = (float) Math.max(blockLight, skyLight) / 15.0F;
        playerDto.setRenderedBrightnessLevel(brightness / (4.0F - 3.0F * brightness));
        //?} else if loader_forge && 1.16.1 {
        BlockPos brightnessPos = new BlockPos(player.getPosX(), player.getPosY(), player.getPosZ());
        int skyLight = world.getLightFor(LightType.SKY, brightnessPos);
        int blockLight = world.getLightFor(LightType.BLOCK, brightnessPos);
        playerDto.setSkyLightLevel(skyLight);
        float brightness = (float) Math.max(blockLight, skyLight) / 15.0F;
        playerDto.setRenderedBrightnessLevel(brightness / (4.0F - 3.0F * brightness));
        //?} else {
        playerDto.setSkyLightLevel(((PlayerVisualBrightnessAccessor) player).getSkyLightLevel());
        playerDto.setRenderedBrightnessLevel(((PlayerVisualBrightnessAccessor) player).getRenderedBrightness());
        //?}

        //? if loader_forge && <=1.8.9 {
        NetHandlerPlayClient handler = client.getNetHandler();
        //?} else if loader_forge && <=1.13.2 {
        NetHandlerPlayClient handler = client.getConnection();
        //?} else if loader_forge && <1.17 {
        ClientPlayNetHandler handler = client.getConnection();
        //?} else {
        ClientPacketListener handler = client.getConnection();
        //?}
        //? if loader_forge && <=1.14.3 {
        playerDto.setIsChatReceived(MineLightsForge.consumeChatReceivedThisTick());
        //?} else {
        if (handler instanceof ChatReceivedAccessor) {
            ChatReceivedAccessor accessor = (ChatReceivedAccessor) handler;
            if (accessor.wasChatReceivedThisTick()) {
                playerDto.setIsChatReceived(true);
                accessor.resetChatReceivedFlag();
            } else {
                playerDto.setIsChatReceived(false);
            }
        } else {
            playerDto.setIsChatReceived(false);
        }
        //?}

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

    //? if loader_forge && <=1.13.2 {
    private static void updateCompassData(PlayerDto dto, EntityPlayerSP player, WorldClient world) {
    //?} else if loader_forge && <1.17 {
    private static void updateCompassData(PlayerDto dto, ClientPlayerEntity player, ClientWorld world) {
    //?} else {
    private static void updateCompassData(PlayerDto dto, LocalPlayer player, ClientLevel world) {
    //?}
        CompassFindResult result = findCompass(player);

        if (result == null) {
            //? if >=1.21.9 {
            if (MineLightsClient.CONFIG.alwaysShowCompass && isOverworld(world)) {
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
            if (MineLightsClient.CONFIG.alwaysShowCompass && isOverworld(world)) {
                dto.setCompassType(CompassType.STANDARD);
                //? if loader_forge && <=1.14.3 {
                //? if loader_forge && <=1.12.2 {
                setCompassTarget(dto, player, world.provider.getSpawnPoint());
                //?} else {
                setCompassTarget(dto, player, world.getSpawnPoint());
                //?}
                //?} else if loader_forge && 1.16.1 {
                setCompassTarget(dto, player, getSpawnPosition(world));
                //?} else {
                setCompassTarget(dto, player, world.getSharedSpawnPos());
                //?}
            } else {
                dto.setCompassState(CompassState.NONE);
                dto.setCompassType(CompassType.NONE);
            }
            //?}
            return;
        }

        dto.setCompassType(result.type);
        //? if loader_forge && <=1.7.10 {
        ChunkCoordinates targetPos = getCompassTargetPos(result.stack, player, world);
        //?} else {
        BlockPos targetPos = getCompassTargetPos(result.stack, player, world);
        //?}

        if (targetPos != null &&
                //? if loader_forge && <=1.7.10 {
                player.getDistanceSq(targetPos.posX + 0.5, targetPos.posY + 0.5, targetPos.posZ + 0.5)
                //?} else if loader_forge && <=1.12.2 {
                player.getDistanceSq(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5)
                //?} else if loader_forge && <=1.14.3 {
                player.getDistanceSq(new Vec3d(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5))
                //?} else if loader_forge && <=1.15.2 {
                player.distanceToSqr(new Vec3d(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5))
                //?} else if loader_forge && 1.16.1 {
                player.getDistanceSq(new Vector3d(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5))
                //?} else if loader_forge && <1.17 {
                player.distanceToSqr(Vector3d.atCenterOf(targetPos))
                //?} else {
                /* player.distanceToSqr(Vec3.atCenterOf(targetPos)) */
                //?}
                >= 1.0E-5) {
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

    //? if loader_forge && <=1.13.2 {
    private static CompassFindResult findCompass(EntityPlayer player) {
    //?} else if loader_forge && <1.17 {
    private static CompassFindResult findCompass(PlayerEntity player) {
    //?} else {
    private static CompassFindResult findCompass(Player player) {
    //?}
        List<CompassFindResult> foundCompasses = new ArrayList<>();
        List<ItemStack> inventory = new ArrayList<>();
        //? if loader_forge && <=1.8.9 {
        inventory.add(player.getHeldItem());
        //?} else if loader_forge && 1.13.2 {
        inventory.add(player.getHeldItem(net.minecraft.util.EnumHand.MAIN_HAND));
        inventory.add(player.getHeldItem(net.minecraft.util.EnumHand.OFF_HAND));
        //?} else if (loader_forge && <=1.14.3) || (loader_forge && 1.16.1) {
        inventory.add(player.getHeldItemMainhand());
        inventory.add(player.getHeldItemOffhand());
        //?} else {
        inventory.add(player.getMainHandItem());
        inventory.add(player.getOffhandItem());
        //?}
        for (int i = 0; i < 36; i++) {
            //? if loader_forge && <1.17 {
            //? if (loader_forge && <=1.14.3) || (loader_forge && 1.16.1) {
            inventory.add(player.inventory.getStackInSlot(i));
            //?} else {
            inventory.add(player.inventory.getItem(i));
            //?}
            //?} else {
            inventory.add(player.getInventory().getItem(i));
            //?}
        }

        for (ItemStack stack : inventory) {
            //? if loader_forge && <=1.10.2 {
            if (stack.stackSize <= 0) continue;
            //?} else {
            if (stack.isEmpty()) continue;
            //?}

            //? if >=1.19 {
            if (stack.getItem() == Items.RECOVERY_COMPASS) {
                foundCompasses.add(new CompassFindResult(stack, CompassType.RECOVERY));
                continue;
            }
            //?}

            //? if loader_forge && <=1.8.9 {
            if (stack.getItem() == Items.compass) {
            //?} else {
            if (stack.getItem() == Items.COMPASS) {
            //?}
                boolean isLodestone = false;
                //? if >=1.20.5 {
                LodestoneTracker lodestoneData = stack.get(DataComponents.LODESTONE_TRACKER);
                if (lodestoneData != null && lodestoneData.target().isPresent()) {
                    isLodestone = true;
                }
                //?} else {
                //? if loader_forge && <=1.12.2 {
                //?} else {
                if (stack.hasTag()) {
                    //? if loader_forge && 1.13.2 {
                    NBTTagCompound tag = stack.getTag();
                    //?} else if loader_forge && <1.17 {
                    CompoundNBT tag = stack.getTag();
                    //?} else {
                    CompoundTag tag = stack.getTag();
                    //?}
                    if (tag != null && tag.contains("LodestonePos")) isLodestone = true;
                }
                //?}
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
            //? if >=1.19 {
            case RECOVERY_FIRST:
                return foundCompasses.stream().filter(r -> r.type == CompassType.RECOVERY).findFirst().orElse(foundCompasses.get(0));
            //?}
            case PRIORITY:
            default:
                return foundCompasses.get(0);
        }
    }

    //? if >=1.21.9 {
    private static BlockPos getCompassTargetPos(ItemStack stack, Player holder, ClientLevel world) {
        //? if >=1.19 {
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
        //?}

        LodestoneTracker lodestoneData = stack.get(DataComponents.LODESTONE_TRACKER);
        if (lodestoneData != null) {
            return lodestoneData.target()
                    .filter(pos -> pos.dimension().equals(world.dimension()))
                    .map(GlobalPos::pos)
                    .orElse(null);
        }

        if (isOverworld(world)) {
            net.minecraft.world.level.storage.LevelData.RespawnData respawnData = world.getLevelData().getRespawnData();
            if (respawnData != null && respawnData.dimension().equals(world.dimension())) {
                return respawnData.pos();
            }
        }
        return null;
    }
    //?} else if >=1.20.5 {
    private static BlockPos getCompassTargetPos(ItemStack stack, Player holder, ClientLevel world) {
        //? if >=1.19 {
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
        //?}

        LodestoneTracker lodestoneData = stack.get(DataComponents.LODESTONE_TRACKER);
        if (lodestoneData != null) {
            return lodestoneData.target()
                    .filter(pos -> pos.dimension().equals(world.dimension()))
                    .map(GlobalPos::pos)
                    .orElse(null);
        }

        if (isOverworld(world)) {
            //? if loader_forge && 1.16.1 {
            return getSpawnPosition(world);
            //?} else if loader_forge && <=1.14.3 {
            //? if loader_forge && <=1.12.2 {
            return world.provider.getSpawnPoint();
            //?} else {
            return world.getSpawnPoint();
            //?}
            //?} else {
            return world.getSharedSpawnPos();
            //?}
        }
        return null;
    }
    //?} else {
    //? if loader_forge && <=1.7.10 {
    private static ChunkCoordinates getCompassTargetPos(ItemStack stack, EntityPlayer holder, WorldClient world) {
    //?} else if loader_forge && <=1.13.2 {
    private static BlockPos getCompassTargetPos(ItemStack stack, EntityPlayer holder, WorldClient world) {
    //?} else if loader_forge && <1.17 {
    private static BlockPos getCompassTargetPos(ItemStack stack, PlayerEntity holder, ClientWorld world) {
    //?} else {
    private static BlockPos getCompassTargetPos(ItemStack stack, Player holder, ClientLevel world) {
    //?}
        //? if >=1.19 {
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
        //?}

        //? if loader_forge && <=1.12.2 {
        //?} else {
        if (stack.hasTag()) {
            //? if loader_forge && 1.13.2 {
            NBTTagCompound tag = stack.getTag();
            //?} else if loader_forge && <1.17 {
            CompoundNBT tag = stack.getTag();
            //?} else {
            CompoundTag tag = stack.getTag();
            //?}
            if (tag != null && tag.contains("LodestonePos") && tag.contains("LodestoneDimension")) {
                String lodestoneDim = tag.getString("LodestoneDimension");
                if (getDimensionId(world).equals(lodestoneDim)) {
                    //? if loader_forge && 1.13.2 {
                    NBTTagCompound posTag = tag.getCompound("LodestonePos");
                    //?} else if loader_forge && <1.17 {
                    CompoundNBT posTag = tag.getCompound("LodestonePos");
                    //?} else {
                    CompoundTag posTag = tag.getCompound("LodestonePos");
                    //?}
                    return new BlockPos(posTag.getInt("X"), posTag.getInt("Y"), posTag.getInt("Z"));
                }
                return null;
            }
        }
        //?}

        if (isOverworld(world)) {
            //? if loader_forge && 1.16.1 {
            return getSpawnPosition(world);
            //?} else if loader_forge && <=1.14.3 {
            return world.getSpawnPoint();
            //?} else {
            return world.getSharedSpawnPos();
            //?}
        }
        return null;
    }
    //?}

    //? if loader_forge && <=1.7.10 {
    private static void setCompassTarget(PlayerDto dto, EntityPlayer player, ChunkCoordinates target) {
    //?} else if loader_forge && <=1.13.2 {
    private static void setCompassTarget(PlayerDto dto, EntityPlayer player, BlockPos target) {
    //?} else if loader_forge && <1.17 {
    private static void setCompassTarget(PlayerDto dto, PlayerEntity player, BlockPos target) {
    //?} else {
    private static void setCompassTarget(PlayerDto dto, Player player, BlockPos target) {
    //?}
        //? if loader_forge && <=1.7.10 {
        Vec3 playerPos = Vec3.createVectorHelper(player.posX, player.posY, player.posZ);
        Vec3 targetPos = Vec3.createVectorHelper(target.posX + 0.5, target.posY + 0.5, target.posZ + 0.5);
        //?} else if loader_forge && <=1.8.9 {
        Vec3 playerPos = new Vec3(player.posX, player.posY, player.posZ);
        Vec3 targetPos = new Vec3(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        //?} else if loader_forge && <=1.14.4 {
        //? if loader_forge && <=1.14.3 {
        Vec3d playerPos = new Vec3d(player.posX, player.posY, player.posZ);
        //?} else if loader_forge && <=1.14.4 {
        Vec3d playerPos = new Vec3d(player.x, player.y, player.z);
        //?}
        Vec3d targetPos = new Vec3d(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        //?} else if loader_forge && 1.16.1 {
        Vector3d playerPos = new Vector3d(player.getPosX(), player.getPosY(), player.getPosZ());
        Vector3d targetPos = new Vector3d(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        //?} else if loader_forge && <=1.15.2 {
        Vec3d playerPos = new Vec3d(player.getX(), player.getY(), player.getZ());
        Vec3d targetPos = new Vec3d(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        //?} else if loader_forge && <1.17 {
        Vector3d playerPos = new Vector3d(player.getX(), player.getY(), player.getZ());
        Vector3d targetPos = new Vector3d(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        //?} else {
        Vec3 playerPos = new Vec3(player.getX(), player.getY(), player.getZ());
        Vec3 targetPos = new Vec3(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        //?}

        //? if loader_forge && <=1.10.2 {
        double deltaX = targetPos.xCoord - playerPos.xCoord;
        double deltaZ = targetPos.zCoord - playerPos.zCoord;
        //?} else {
        double deltaX = targetPos.x - playerPos.x;
        double deltaZ = targetPos.z - playerPos.z;
        //?}
        double targetYaw = Math.toDegrees(Math.atan2(-deltaX, deltaZ));
        //? if loader_forge && <1.17 {
        //? if (loader_forge && <=1.14.3) || (loader_forge && 1.16.1) {
        double playerYaw = player.rotationYaw;
        //?} else if loader_forge && <1.17 {
        double playerYaw = player.yRot;
        //?}
        //?} else {
        double playerYaw = player.getYRot();
        //?}

        double relativeYaw = targetYaw - playerYaw;
        while (relativeYaw <= -180.0D) relativeYaw += 360.0D;
        while (relativeYaw > 180.0D) relativeYaw -= 360.0D;

        dto.setCompassState(CompassState.POINTING);
        dto.setCompassRelativeYaw(relativeYaw);
        dto.setCompassDistance(Math.sqrt(deltaX * deltaX + deltaZ * deltaZ));
    }

    //? if loader_forge && 1.16.1 {
    private static BlockPos getSpawnPosition(ClientWorld world) {
        ISpawnWorldInfo worldInfo = ObfuscationReflectionHelper.getPrivateValue(World.class, world, "worldInfo");
        return new BlockPos(worldInfo.getSpawnX(), worldInfo.getSpawnY(), worldInfo.getSpawnZ());
    }
    //?}

    //? if loader_forge && <=1.13.2 {
    private static String getDimensionId(WorldClient world) {
    //?} else if loader_forge && <1.17 {
    private static String getDimensionId(ClientWorld world) {
    //?} else {
    private static String getDimensionId(ClientLevel world) {
    //?}
        //? if loader_forge && <=1.7.10 {
        int dimension = world.provider.dimensionId;
        switch (dimension) {
            case -1: return "minecraft:the_nether";
            case 0: return "minecraft:overworld";
            case 1: return "minecraft:the_end";
            default: return Integer.toString(dimension);
        }
        //?} else if loader_forge && <=1.8.9 {
        int dimension = world.provider.getDimensionId();
        switch (dimension) {
            case -1: return "minecraft:the_nether";
            case 0: return "minecraft:overworld";
            case 1: return "minecraft:the_end";
            default: return Integer.toString(dimension);
        }
        //?} else if loader_forge && <=1.12.2 {
        switch (world.provider.getDimension()) {
            case -1:
                return "minecraft:the_nether";
            case 0:
                return "minecraft:overworld";
            case 1:
                return "minecraft:the_end";
            default:
                return Integer.toString(world.provider.getDimension());
        }
        //?} else if loader_forge && 1.16.1 {
        return world.func_234923_W_().getRegistryName().toString();
        //?} else if loader_forge && <=1.15.2 {
        return world.dimension.getType().getRegistryName().toString();
        //?} else if >=1.21.11 {
        return world.dimension().identifier().toString();
        //?} else {
        return world.dimension().location().toString();
        //?}
    }

    //? if loader_forge && <=1.13.2 {
    private static boolean isOverworld(WorldClient world) {
    //?} else if loader_forge && <1.17 {
    private static boolean isOverworld(ClientWorld world) {
    //?} else {
    private static boolean isOverworld(ClientLevel world) {
    //?}
        //? if loader_forge && <=1.7.10 {
        return world.provider.dimensionId == 0;
        //?} else if loader_forge && <=1.8.9 {
        return world.provider.getDimensionId() == 0;
        //?} else if loader_forge && <=1.12.2 {
        return world.provider.getDimensionType() == DimensionType.OVERWORLD;
        //?} else if loader_forge && <=1.15.2 {
        return world.dimension.getType() == DimensionType.OVERWORLD;
        //?} else if loader_forge && 1.16.1 {
        return world.func_234923_W_().getRegistryName().toString().equals("minecraft:overworld");
        //?} else if loader_forge && <1.17 {
        return world.dimension().equals(World.OVERWORLD);
        //?} else {
        return world.dimension().equals(Level.OVERWORLD);
        //?}
    }
}
