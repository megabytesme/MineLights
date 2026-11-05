package megabytesme.minelights.mixin;

import megabytesme.minelights.network.MineLightsCommands;
import net.minecraft.client.network.ClientPlayerEntity;
//? if >=1.19 {
/* import net.minecraft.text.Text;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerChatMixin {
    //? if >=1.19 {
    /* @Inject(method = "sendChatMessage(Ljava/lang/String;Lnet/minecraft/text/Text;)V", at = @At("HEAD"), cancellable = true)
    private void minelights$interceptChat(String message, Text preview, CallbackInfo ci) {
        if (MineLightsCommands.handleSlashMessage(message)) {
            ci.cancel();
        }
    }

    @Inject(method = "sendCommand(Ljava/lang/String;Lnet/minecraft/text/Text;)V", at = @At("HEAD"), cancellable = true)
    private void minelights$interceptCommand(String command, Text preview, CallbackInfo ci) {
        if (MineLightsCommands.handleCommand(command)) {
            ci.cancel();
        }
    }
    *///?} else {
    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
    private void minelights$interceptChat(String message, CallbackInfo ci) {
        if (MineLightsCommands.handleSlashMessage(message)) {
            ci.cancel();
        }
    }
    //?}
}
