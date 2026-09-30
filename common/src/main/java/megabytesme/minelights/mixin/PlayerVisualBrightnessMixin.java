package megabytesme.minelights.mixin;

//? if loader_forge && <1.17 {
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
//?} else if loader_neoforge || >=26.1 {
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
//?} else {
/* import net.minecraft.world.LightType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import megabytesme.minelights.accessor.PlayerVisualBrightnessAccessor;

//? if loader_forge && <1.17 {
@Mixin(PlayerEntity.class)
//?} else if loader_neoforge || >=26.1 {
@Mixin(Player.class)
//?} else {
/* @Mixin(PlayerEntity.class)
*///?}
public abstract class PlayerVisualBrightnessMixin implements PlayerVisualBrightnessAccessor {

    @Unique
    @Override
    public int getSkyLightLevel() {
        //? if loader_forge && <1.17 {
        Minecraft mc = Minecraft.getInstance();
        PlayerEntity player = (PlayerEntity)(Object)this;
        //?} else if loader_neoforge || >=26.1 {
        Minecraft mc = Minecraft.getInstance();
        Player player = (Player)(Object)this;
        //?} else {
        /* MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity player = (PlayerEntity)(Object)this;
        *///?}
        //? if loader_forge && <=1.14.3 {
        BlockPos pos = new BlockPos(player.posX, player.posY, player.posZ);
        //?} else if loader_forge && <=1.14.4 {
        BlockPos pos = new BlockPos(player.x, player.y, player.z);
        //?} else if loader_forge && <=1.15.2 {
        BlockPos pos = new BlockPos(player.getX(), player.getY(), player.getZ());
        //?} else if loader_forge && 1.16.1 {
        BlockPos pos = new BlockPos(player.getPosX(), player.getPosY(), player.getPosZ());
        //?} else if loader_forge && <1.17 {
        BlockPos pos = player.blockPosition();
        //?} else if loader_neoforge || >=26.1 {
        BlockPos pos = player.blockPosition();
        //?} else {
        /* BlockPos pos = player.getBlockPos(); */
        //?}
        //? if (loader_forge && <=1.14.3) || (loader_forge && 1.16.1) {
        return mc.world.getLightFor(LightType.SKY, pos);
        //?} else if loader_forge && <=1.14.4 {
        return mc.level.getBrightness(LightType.SKY, pos);
        //?} else if loader_forge && <1.17 {
        return mc.level.getLightEngine().getLayerListener(LightType.SKY).getLightValue(pos);
        //?} else if loader_neoforge || >=26.1 {
        return mc.level.getBrightness(LightLayer.SKY, pos);
        //?} else {
        /* return mc.world.getLightLevel(LightType.SKY, pos);
        *///?}
    }

    @Unique
    @Override
    public float getRenderedBrightness() {
        //? if loader_forge && <1.17 {
        Minecraft mc = Minecraft.getInstance();
        PlayerEntity player = (PlayerEntity)(Object)this;
        //?} else if loader_neoforge || >=26.1 {
        Minecraft mc = Minecraft.getInstance();
        Player player = (Player)(Object)this;
        //?} else {
        /* MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity player = (PlayerEntity)(Object)this;
        *///?}
        //? if loader_forge && <=1.14.3 {
        BlockPos pos = new BlockPos(player.posX, player.posY, player.posZ);
        //?} else if loader_forge && <=1.14.4 {
        BlockPos pos = new BlockPos(player.x, player.y, player.z);
        //?} else if loader_forge && <=1.15.2 {
        BlockPos pos = new BlockPos(player.getX(), player.getY(), player.getZ());
        //?} else if loader_forge && 1.16.1 {
        BlockPos pos = new BlockPos(player.getPosX(), player.getPosY(), player.getPosZ());
        //?} else if loader_forge && <1.17 {
        BlockPos pos = player.blockPosition();
        //?} else if loader_neoforge || >=26.1 {
        BlockPos pos = player.blockPosition();
        //?} else {
        /* BlockPos pos = player.getBlockPos(); */
        //?}
        //? if (loader_forge && <=1.14.3) || (loader_forge && 1.16.1) {
        int blockLight = mc.world.getLightFor(LightType.BLOCK, pos);
        int skyLight   = mc.world.getLightFor(LightType.SKY, pos);
        //?} else if loader_forge && <=1.14.4 {
        int blockLight = mc.level.getBrightness(LightType.BLOCK, pos);
        int skyLight   = mc.level.getBrightness(LightType.SKY, pos);
        //?} else if loader_forge && <1.17 {
        int blockLight = mc.level.getLightEngine().getLayerListener(LightType.BLOCK).getLightValue(pos);
        int skyLight   = mc.level.getLightEngine().getLayerListener(LightType.SKY).getLightValue(pos);
        //?} else if loader_neoforge || >=26.1 {
        int blockLight = mc.level.getBrightness(LightLayer.BLOCK, pos);
        int skyLight   = mc.level.getBrightness(LightLayer.SKY, pos);
        //?} else {
        /* int blockLight = mc.world.getLightLevel(LightType.BLOCK, pos);
        int skyLight   = mc.world.getLightLevel(LightType.SKY, pos);
        *///?}
        int combined   = Math.max(blockLight, skyLight);

        float f = (float) combined / 15.0F;
        float g = f / (4.0F - 3.0F * f);
        return g;
    }
}
