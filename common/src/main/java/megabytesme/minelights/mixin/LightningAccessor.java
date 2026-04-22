package megabytesme.minelights.mixin;

//? if >=26.1 {
import net.minecraft.world.entity.LightningBolt;
//?} else {
/* import net.minecraft.entity.LightningEntity;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//? if >=26.1 {
@Mixin(LightningBolt.class)
//?} else {
/* @Mixin(LightningEntity.class)
*///?}
public interface LightningAccessor {
    //? if >=26.1 {
    @Accessor("life")
    //?} else {
    /* @Accessor("ambientTick")
    *///?}
    int getAmbientTick();

    //? if >=26.1 {
    @Accessor("flashes")
    //?} else {
    /* @Accessor("remainingActions")
    *///?}
    int getRemainingActions();
}
