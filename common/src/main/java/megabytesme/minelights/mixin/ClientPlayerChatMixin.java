package megabytesme.minelights.mixin;

import megabytesme.minelights.network.MineLightsCommands;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerChatMixin {
    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
    private void minelights$interceptChat(String message, CallbackInfo ci) {
        if (MineLightsCommands.handleSlashMessage(message)) {
            ci.cancel();
        }
    }
}
