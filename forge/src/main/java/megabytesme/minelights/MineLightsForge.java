package megabytesme.minelights;

import megabytesme.minelights.config.ModMenuIntegration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
//? if <1.19 {
import net.minecraft.network.chat.TextComponent;
//?}
//? if >=1.19 {
import net.minecraftforge.client.ConfigScreenHandler;
//?} else if >=1.18 {
/* import net.minecraftforge.client.ConfigGuiHandler;
*///?} else {
/* import net.minecraftforge.fmlclient.ConfigGuiHandler;
*///?}
//? if <26.1 {
import net.minecraftforge.fml.ModLoadingContext;
//?}
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import java.util.function.BiFunction;
//? if >=1.21.8 {
import net.minecraftforge.event.TickEvent;
//?} else {
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
//? if >=1.21.6 {
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
//?} else {
import net.minecraftforge.eventbus.api.SubscribeEvent;
//?}
//?}

@Mod(MineLightsClient.MOD_ID)
public final class MineLightsForge {
    private final MineLightsClient client = new MineLightsClient();

    private static Component literal(String text) {
        //? if >=1.19 {
        return Component.literal(text);
        //?} else {
        /* return new TextComponent(text);
        *///?}
    }

//? if >=26.1 {
    public MineLightsForge(FMLJavaModLoadingContext context) {
        initialize(context.getContainer());
    }
    //?} else {
    /* public MineLightsForge() {
        initialize(ModLoadingContext.get().getActiveContainer());
    }
    *///?}

    private void initialize(ModContainer modContainer) {
        MineLightsClient.LOGGER.info("Constructing Forge entrypoint for MineLights.");
        client.init(
                FMLPaths.CONFIGDIR.get(),
                modContainer.getModInfo().getVersion().toString(),
                "forge"
        );

        //? if >=1.21.6 {
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> client.onClientTick(Minecraft.getInstance()));
        //?} else {
        MinecraftForge.EVENT_BUS.register(this);
        //?}

        MineLightsClient.LOGGER.info("Registering Forge config screen factory.");
        BiFunction<Minecraft, Screen, Screen> screenFactory = (minecraft, parent) -> {
            //? if >=26.1 {
            if (!net.minecraftforge.fml.ModList.isLoaded("cloth_config")) {
            //?} else {
            /*if (!net.minecraftforge.fml.ModList.get().isLoaded("cloth_config")) {
            *///?}
                return new AlertScreen(
                        //? if >=26.2 {
                        () -> Minecraft.getInstance().setScreenAndShow(parent),
                        //?} else {
                        /*() -> Minecraft.getInstance().setScreen(parent),
                        *///?}
                        literal("MineLights Config Unavailable"),
                        literal("Install a Forge-compatible Cloth Config version to open MineLights settings.")
                );
            }
            return ModMenuIntegration.createConfigScreen(parent);
        };
        //? if >=1.19 {
        modContainer.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(screenFactory));
        //?} else {
        /* ModLoadingContext.get().registerExtensionPoint(ConfigGuiHandler.ConfigGuiFactory.class,
                () -> new ConfigGuiHandler.ConfigGuiFactory(screenFactory));
        *///?}
    }

    //? if <1.21.6 {
    @SubscribeEvent
    //? if >=1.21.1 {
    public void onClientTick(TickEvent.ClientTickEvent.Post event) {
        client.onClientTick(Minecraft.getInstance());
    }
    //?} else {
    /* public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            client.onClientTick(Minecraft.getInstance());
        }
    }
    *///?}
    //?}
}
