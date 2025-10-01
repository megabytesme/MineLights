package megabytesme.minelights.mixin;

import net.minecraft.entity.LightningEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LightningEntity.class)
public interface LightningAccessor {
    @Accessor("ambientTick")
    int getAmbientTick();

    @Accessor("remainingActions")
    int getRemainingActions();
}