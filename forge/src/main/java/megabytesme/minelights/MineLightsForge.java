package megabytesme.minelights;

//? if loader_forge && >=1.16.3 {
import megabytesme.minelights.config.ModMenuIntegration;
//?}
import net.minecraft.client.Minecraft;
//? if loader_forge && >=1.16.3 {
//? if loader_forge && <1.17 {
import net.minecraft.client.gui.screen.AlertScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.fml.ExtensionPoint;
//?} else {
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
//?}
//?}
//? if loader_forge && >=1.13.2 && <26.1 {
import net.minecraftforge.fml.ModLoadingContext;
//?}
//? if loader_forge && <=1.7.10 {
import cpw.mods.fml.common.Mod;
//?} else {
import net.minecraftforge.fml.common.Mod;
//?}
//? if loader_forge && <=1.12.2 {
//? if loader_forge && <=1.7.10 {
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
//?} else {
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
//?}
//?} else {
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
//?}
//? if loader_forge && >=1.16.3 {
import java.util.function.BiFunction;
//?}
//? if loader_forge && <=1.14.3 {
//? if loader_forge && <=1.7.10 {
import cpw.mods.fml.common.gameevent.TickEvent;
//?} else {
import net.minecraftforge.fml.common.gameevent.TickEvent;
//?}
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
//? if loader_forge && <=1.12.2 {
//? if loader_forge && <=1.7.10 {
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
//?} else {
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
//?}
//?} else {
import net.minecraftforge.eventbus.api.SubscribeEvent;
//?}
//?} else if >=1.21.8 {
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

//? if loader_forge && <=1.12.2 {
//? if loader_forge && <=1.7.10 {
@Mod(modid = MineLightsClient.MOD_ID, name = "MineLights", version = MineLightsClient.MOD_VERSION)
//?} else {
@Mod(modid = MineLightsClient.MOD_ID, name = "MineLights", version = MineLightsClient.MOD_VERSION, clientSideOnly = true)
//?}
//?} else {
@Mod(MineLightsClient.MOD_ID)
//?}
public final class MineLightsForge {
    private final MineLightsClient client = new MineLightsClient();
    //? if loader_forge && <=1.14.3 {
    private static volatile boolean chatReceivedThisTick;
    //?}

    //? if loader_forge && >=1.16.3 && <1.17 {
    private static ITextComponent literal(String text) {
        return new StringTextComponent(text);
    }
    //?} else if >=1.16.3 {
    private static Component literal(String text) {
        //? if >=1.19 {
        return Component.literal(text);
        //?} else {
        /* return new TextComponent(text);
        *///?}
    }
    //?}

//? if loader_forge && <=1.12.2 {
    public MineLightsForge() {
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        MineLightsClient.LOGGER.info("Constructing Forge entrypoint for MineLights.");
        client.init(event.getModConfigurationDirectory().toPath(), MineLightsClient.MOD_VERSION, "forge");
        MinecraftForge.EVENT_BUS.register(this);
    }
    //?} else if >=26.1 {
    public MineLightsForge(FMLJavaModLoadingContext context) {
        initialize(context.getContainer());
    }
    //?} else {
    /* public MineLightsForge() {
        initialize(ModLoadingContext.get().getActiveContainer());
    }
    *///?}

//? if loader_forge && <=1.12.2 {
    //?} else {
    private void initialize(ModContainer modContainer) {
        MineLightsClient.LOGGER.info("Constructing Forge entrypoint for MineLights.");
        client.init(
                FMLPaths.CONFIGDIR.get(),
                modContainer.getModInfo().getVersion().toString(),
                "forge"
        );

        //? if >=1.21.6 {
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> client.onClientTick(MineLightsClient.getMinecraft()));
        //?} else {
        MinecraftForge.EVENT_BUS.register(this);
        //?}

        //? if loader_forge && >=1.16.3 {
        MineLightsClient.LOGGER.info("Registering Forge config screen factory.");
        BiFunction<Minecraft, Screen, Screen> screenFactory = (minecraft, parent) -> {
            //? if >=26.1 {
            if (!net.minecraftforge.fml.ModList.isLoaded("cloth_config")) {
            //?} else {
            /*if (!net.minecraftforge.fml.ModList.get().isLoaded("cloth_config")) {
            *///?}
                return new AlertScreen(
                        //? if >=26.2 {
                        () -> MineLightsClient.getMinecraft().setScreenAndShow(parent),
                        //?} else {
                        /*() -> MineLightsClient.getMinecraft().setScreen(parent),
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
        /* ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.CONFIGGUIFACTORY,
                () -> screenFactory);
        *///?}
        //?}
    }
    //?}

    //? if loader_forge && <=1.14.3 {
    public static boolean consumeChatReceivedThisTick() {
        boolean received = chatReceivedThisTick;
        chatReceivedThisTick = false;
        return received;
    }

    @SubscribeEvent
    public void onClientChatReceived(ClientChatReceivedEvent event) {
        chatReceivedThisTick = true;
    }
    //?}

    //? if <1.21.6 {
    @SubscribeEvent
    //? if >=1.21.1 {
    public void onClientTick(TickEvent.ClientTickEvent.Post event) {
        client.onClientTick(MineLightsClient.getMinecraft());
    }
    //?} else {
    /* public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            client.onClientTick(MineLightsClient.getMinecraft());
        }
    }
    *///?}
    //?}
}
