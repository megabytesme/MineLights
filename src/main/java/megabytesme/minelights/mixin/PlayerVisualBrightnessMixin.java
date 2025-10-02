package megabytesme.minelights.mixin;

import net.minecraft.world.LightType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import megabytesme.minelights.accessor.PlayerVisualBrightnessAccessor;

@Mixin(PlayerEntity.class)
public abstract class PlayerVisualBrightnessMixin implements PlayerVisualBrightnessAccessor {

    @Unique
    @Override
    public int getSkyLightLevel() {
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity player = (PlayerEntity)(Object)this;
        BlockPos pos = player.getBlockPos();
        return mc.world.getLightLevel(LightType.SKY, pos);
    }

    @Unique
    @Override
    public float getRenderedBrightness() {
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity player = (PlayerEntity)(Object)this;
        BlockPos pos = player.getBlockPos();
        int blockLight = mc.world.getLightLevel(LightType.BLOCK, pos);
        int skyLight   = mc.world.getLightLevel(LightType.SKY, pos);
        int combined   = Math.max(blockLight, skyLight);

        float f = (float) combined / 15.0F;
        float g = f / (4.0F - 3.0F * f);
        return g;
    }
}