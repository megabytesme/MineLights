package megabytesme.minelights.mixin;

//? if >=26.1 {
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

//? if >=26.1 {
@Mixin(Player.class)
//?} else {
/* @Mixin(PlayerEntity.class)
*///?}
public abstract class PlayerVisualBrightnessMixin implements PlayerVisualBrightnessAccessor {

    @Unique
    @Override
    public int getSkyLightLevel() {
        //? if >=26.1 {
        Minecraft mc = Minecraft.getInstance();
        Player player = (Player)(Object)this;
        //?} else {
        /* MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity player = (PlayerEntity)(Object)this;
        *///?}
        //? if >=26.1 {
        BlockPos pos = player.blockPosition();
        //?} else {
        /* BlockPos pos = player.getBlockPos(); */
        //?}
        //? if >=26.1 {
        return mc.level.getBrightness(LightLayer.SKY, pos);
        //?} else {
        /* return mc.world.getLightLevel(LightType.SKY, pos);
        *///?}
    }

    @Unique
    @Override
    public float getRenderedBrightness() {
        //? if >=26.1 {
        Minecraft mc = Minecraft.getInstance();
        Player player = (Player)(Object)this;
        //?} else {
        /* MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity player = (PlayerEntity)(Object)this;
        *///?}
        //? if >=26.1 {
        BlockPos pos = player.blockPosition();
        //?} else {
        /* BlockPos pos = player.getBlockPos(); */
        //?}
        //? if >=26.1 {
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
