package megabytesme.minelights.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
//? if 1.14.4 || >=1.19 {
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
//?} else if >=1.16 && <=1.18.2 {
/* import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
*///?} else {
/* import net.minecraft.client.network.packet.ChatMessageS2CPacket;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import megabytesme.minelights.accessor.ChatReceivedAccessor;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin implements ChatReceivedAccessor {
    @Unique
    private boolean chatReceivedThisTick = false;

    //? if >=1.16 && <=1.18.2 {
    /* @Inject(method = "onGameMessage", at = @At("HEAD"))
    private void onGameMessage(GameMessageS2CPacket packet, CallbackInfo ci) {
        chatReceivedThisTick = true;
    }
    *///?} else {
    @Inject(method = "onChatMessage", at = @At("HEAD"))
    private void onChatMessage(ChatMessageS2CPacket packet, CallbackInfo ci) {
        chatReceivedThisTick = true;
    }
    //?}

    @Unique
    @Override
    public boolean wasChatReceivedThisTick() {
        return chatReceivedThisTick;
    }

    @Unique
    @Override
    public void resetChatReceivedFlag() {
        chatReceivedThisTick = false;
    }
}