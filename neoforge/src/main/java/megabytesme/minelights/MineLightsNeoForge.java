package megabytesme.minelights;

import megabytesme.minelights.config.ModMenuIntegration;
//? if >=1.20.1 {
/*import net.minecraft.client.Minecraft;
*///?} else {
 import net.minecraft.client.MinecraftClient; 
//?}
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
//? if >=1.20.5 {
/*import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.minecraft.client.gui.screens.Screen;
*///?} else {
 import net.neoforged.neoforge.client.ConfigScreenHandler;
import net.neoforged.neoforge.event.TickEvent;
//?}
import net.neoforged.neoforge.common.NeoForge;

@Mod(MineLightsClient.MOD_ID)
public final class MineLightsNeoForge {
    private final MineLightsClient client = new MineLightsClient();

    public MineLightsNeoForge(ModContainer modContainer) {
        MineLightsClient.LOGGER.info("Constructing NeoForge entrypoint for MineLights.");
        client.init(
                FMLPaths.CONFIGDIR.get(),
                modContainer.getModInfo().getVersion().toString(),
                "neoforge"
        );
        NeoForge.EVENT_BUS.register(this);
        //? if >=1.20.5 {
        /*MineLightsClient.LOGGER.info("Registering NeoForge config screen factory.");
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                new MineLightsConfigScreenFactory());
        *///?} else {
         MineLightsClient.LOGGER.info("Registering NeoForge config screen factory.");
         modContainer.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) ->
                        ModMenuIntegration.createConfigScreen(parent))); 
        //?}
    }

    //? if >=1.20.5 {
    /*
    private static final class MineLightsConfigScreenFactory implements IConfigScreenFactory {
        // NeoForge 20.5 passes Minecraft, while 21.1 passes ModContainer.
        // Keep both JVM descriptors so the same supported mod build works on both API shapes.
        public Screen createScreen(Minecraft minecraft, Screen parent) {
            return ModMenuIntegration.createConfigScreen(parent);
        }

        public Screen createScreen(ModContainer container, Screen parent) {
            return ModMenuIntegration.createConfigScreen(parent);
        }
    }
    *///?}

    //? if >=1.20.5 {
    /*@SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        client.onClientTick(Minecraft.getInstance());
    }
    *///?} else {
     @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            //? if >=1.20.1 {
            /*client.onClientTick(Minecraft.getInstance());
            *///?} else {
            client.onClientTick(MinecraftClient.getInstance());
            //?}
        }
    } 
    //?}

}
