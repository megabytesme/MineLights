package megabytesme.minelights.mixin;

//? if loader_forge && <1.17 {
import net.minecraft.entity.effect.LightningBoltEntity;
//?} else if loader_neoforge || >=26.1 {
import net.minecraft.world.entity.LightningBolt;
//?} else {
/* import net.minecraft.entity.LightningEntity;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//? if loader_forge && <1.17 {
@Mixin(LightningBoltEntity.class)
//?} else if loader_neoforge || >=26.1 {
@Mixin(LightningBolt.class)
//?} else {
/* @Mixin(LightningEntity.class)
*///?}
public interface LightningAccessor {
    //? if (loader_forge && <=1.14.3) || (loader_forge && 1.16.1) {
    @Accessor("lightningState")
    //?} else if loader_forge && <=1.14.4 {
    @Accessor("life")
    //?} else if loader_forge && <=1.15.2 {
    @Accessor("life")
    //?} else if loader_forge && <1.17 {
    @Accessor("ambientTick")
    //?} else if loader_neoforge || >=26.1 {
    @Accessor("life")
    //?} else {
    /* @Accessor("ambientTick")
    *///?}
    int getAmbientTick();

    //? if (loader_forge && <=1.14.3) || (loader_forge && 1.16.1) {
    @Accessor("boltLivingTime")
    //?} else if loader_forge && <=1.14.4 {
    @Accessor("flashes")
    //?} else if loader_forge && <=1.15.2 {
    @Accessor("flashes")
    //?} else if loader_forge && <1.17 {
    @Accessor("remainingActions")
    //?} else if loader_neoforge || >=26.1 {
    @Accessor("flashes")
    //?} else {
    /* @Accessor("remainingActions")
    *///?}
    int getRemainingActions();
}
