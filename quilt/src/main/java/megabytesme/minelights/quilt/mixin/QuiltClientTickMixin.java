package megabytesme.minelights.quilt.mixin;

import megabytesme.minelights.quilt.QuiltClientBootstrap;
//? if >=26.1 {
/*import net.minecraft.client.Minecraft;
*///?} else {
 import net.minecraft.client.MinecraftClient;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=26.1 {
/*@Mixin(Minecraft.class)
*///?} else {
@Mixin(MinecraftClient.class)
//?}
public abstract class QuiltClientTickMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void minelights$onClientTick(CallbackInfo callbackInfo) {
        //? if >=26.1 {
        /*QuiltClientBootstrap.onClientTick((Minecraft) (Object) this);
        *///?} else {
        QuiltClientBootstrap.onClientTick((MinecraftClient) (Object) this);
        //?}
    }
}
