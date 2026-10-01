package megabytesme.minelights;

//? if loader_forge && >=1.16.3 && <1.21.4 {
import megabytesme.minelights.config.ModMenuIntegration;
//?} else if loader_forge && >=1.21.5 && <26.3 {
/* import megabytesme.minelights.config.ModMenuIntegration;
*///?}
//? if loader_forge && >=1.13.2 && <1.16.3 {
import megabytesme.minelights.MineLightsTransitionalConfigScreen;
//?}
//? if loader_forge && >=1.21.4 {
import megabytesme.minelights.MineLightsModernConfigScreen;
//?}
import net.minecraft.client.Minecraft;
//? if loader_forge && >=1.14.2 && <1.17 {
import net.minecraft.client.gui.screen.Screen;
//?} else if loader_forge && >=1.17 {
import net.minecraft.client.gui.screens.Screen;
//?} else if loader_forge && >=1.13.2 {
import net.minecraft.client.gui.GuiScreen;
//?}
//? if loader_forge && >=1.16.3 {
//? if loader_forge && <1.17 {
import net.minecraft.client.gui.screen.AlertScreen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
//?} else {
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.network.chat.Component;
//? if <1.19 {
import net.minecraft.network.chat.TextComponent;
//?}
//? if >=1.19 {
import net.minecraftforge.client.ConfigScreenHandler;
//?} else if >=1.18 {
import net.minecraftforge.client.ConfigGuiHandler;
//?} else if >=1.17 {
import net.minecraftforge.fmlclient.ConfigGuiHandler;
//?}
//?}
//?}
//? if loader_forge && >=1.13.2 && <1.17 {
import net.minecraftforge.fml.ExtensionPoint;
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
import cpw.mods.fml.common.FMLCommonHandler;
//?} else {
import net.minecraftforge.fml.common.FMLCommonHandler;
//?}
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
//? if loader_forge && >=1.13.2 {
import java.util.function.BiFunction;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
//?}
//? if loader_forge && >=1.13.2 && <26.3 {
import java.lang.reflect.Method;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWWindowCloseCallback;
import org.lwjgl.glfw.GLFWWindowCloseCallbackI;
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
@Mod(modid = MineLightsClient.MOD_ID, name = "MineLights", version = MineLightsClient.MOD_VERSION,
        guiFactory = "megabytesme.minelights.MineLightsLegacyGuiFactory")
//?} else {
@Mod(modid = MineLightsClient.MOD_ID, name = "MineLights", version = MineLightsClient.MOD_VERSION,
        clientSideOnly = true, guiFactory = "megabytesme.minelights.MineLightsLegacyGuiFactory")
//?}
//?} else {
@Mod(MineLightsClient.MOD_ID)
//?}
public final class MineLightsForge {
    private final MineLightsClient client = new MineLightsClient();
    //? if loader_forge && >=1.13.2 {
    private static final AtomicBoolean closeSignalHandled = new AtomicBoolean(false);
    //?}
    //? if loader_forge && >=1.13.2 && <26.3 {
    private static volatile long glfwWindowHandle;
    private GLFWWindowCloseCallbackI mineLightsWindowCloseCallback;
    //?}
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
        FMLCommonHandler.instance().bus().register(this);
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
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> {
            Minecraft minecraft = MineLightsClient.getMinecraft();
            //? if <26.3 {
            installCloseCallback(minecraft);
            //?}
            if (clientShouldClose(minecraft)) {
                client.shutdown();
            }
            client.onClientTick(minecraft);
        });
        //?} else {
        MinecraftForge.EVENT_BUS.register(this);
        //?}

        //? if loader_forge && >=1.13.2 && <1.14.2 {
        MineLightsClient.LOGGER.info("Registering Forge config screen factory.");
        BiFunction<Minecraft, GuiScreen, GuiScreen> screenFactory = (minecraft, parent) ->
                new MineLightsTransitionalConfigScreen(parent);
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.CONFIGGUIFACTORY,
                () -> screenFactory);
        //?} else if loader_forge && >=1.14.2 && <1.16.3 {
        MineLightsClient.LOGGER.info("Registering native Forge config screen factory.");
        BiFunction<Minecraft, Screen, Screen> screenFactory = (minecraft, parent) ->
                new MineLightsTransitionalConfigScreen(parent);
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.CONFIGGUIFACTORY,
                () -> screenFactory);
        //?} else if loader_forge && >=1.16.3 && <1.17 {
        MineLightsClient.LOGGER.info("Registering Forge config screen factory.");
        BiFunction<Minecraft, Screen, Screen> screenFactory = (minecraft, parent) ->
                ModMenuIntegration.createConfigScreen(parent);
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.CONFIGGUIFACTORY,
                () -> screenFactory);
        //?} else if loader_forge && >=1.17 && <1.19 {
        MineLightsClient.LOGGER.info("Registering Forge config screen factory.");
        BiFunction<Minecraft, Screen, Screen> screenFactory = (minecraft, parent) ->
                ModMenuIntegration.createConfigScreen(parent);
        ModLoadingContext.get().registerExtensionPoint(ConfigGuiHandler.ConfigGuiFactory.class,
                () -> new ConfigGuiHandler.ConfigGuiFactory(screenFactory));
        //?} else if loader_forge && >=1.21.4 && <1.21.5 {
        MineLightsClient.LOGGER.info("Registering native Forge config screen factory.");
        BiFunction<Minecraft, Screen, Screen> screenFactory = (minecraft, parent) ->
                new MineLightsModernConfigScreen(parent);
        modContainer.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(screenFactory));
        //?} else if loader_forge && >=26.3 {
        MineLightsClient.LOGGER.info("Registering native Forge config screen factory.");
        BiFunction<Minecraft, Screen, Screen> screenFactory = (minecraft, parent) ->
                new MineLightsModernConfigScreen(parent);
        modContainer.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(screenFactory));
        //?} else if loader_forge && >=1.19 {
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
        modContainer.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(screenFactory));
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
        Minecraft minecraft = MineLightsClient.getMinecraft();
        installCloseCallback(minecraft);
        if (clientShouldClose(minecraft)) {
            client.shutdown();
        }
        client.onClientTick(minecraft);
    }
    //?} else {
    /* public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            client.onClientTick(MineLightsClient.getMinecraft());
        }
    }
    *///?}
    //?}

    //? if loader_forge && >=1.13.2 && <1.21.1 {
    @SubscribeEvent
    public void onClientTickClose(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = MineLightsClient.getMinecraft();
        installCloseCallback(minecraft);
        if (clientShouldClose(minecraft)) {
            client.shutdown();
        }
    }
    //?}

    //? if loader_forge && >=1.13.2 && <26.3 {
    private void installCloseCallback(Minecraft minecraft) {
        if (mineLightsWindowCloseCallback != null || minecraft == null) {
            return;
        }

        long handle = glfwWindowHandle;
        if (handle == 0L) {
            handle = findGlfwWindowHandle(minecraft);
            if (handle != 0L) {
                glfwWindowHandle = handle;
            }
        }
        if (handle == 0L) {
            return;
        }

        final GLFWWindowCloseCallback[] previous = new GLFWWindowCloseCallback[1];
        GLFWWindowCloseCallback callback = new GLFWWindowCloseCallback() {
            @Override
            public void invoke(long window) {
                MineLightsClient.LOGGER.info("Stopping MineLights before the Forge window closes.");
                closeSignalHandled.set(true);
                try {
                    client.shutdown();
                } finally {
                    if (previous[0] != null) {
                        previous[0].invoke(window);
                    }
                }
            }
        };
        previous[0] = GLFW.glfwSetWindowCloseCallback(handle, callback);
        mineLightsWindowCloseCallback = callback;
        MineLightsClient.LOGGER.debug("Installed MineLights shutdown handler for the Forge window.");
    }
    //?}

    //? if loader_forge && >=1.13.2 {
    private static boolean clientShouldClose(Minecraft minecraft) {
        if (closeSignalHandled.get()) {
            return true;
        }
        if (isMinecraftStopped(minecraft)) {
            closeSignalHandled.set(true);
            MineLightsClient.LOGGER.info("Stopping MineLights before the Forge client exits.");
            return true;
        }

        //? if loader_forge && <26.3 {
        long handle = glfwWindowHandle;
        if (handle == 0L) {
            handle = findGlfwWindowHandle(minecraft);
            if (handle != 0L) {
                glfwWindowHandle = handle;
            }
        }
        if (handle != 0L) {
            try {
                if (GLFW.glfwWindowShouldClose(handle)) {
                    closeSignalHandled.set(true);
                    MineLightsClient.LOGGER.info("Stopping MineLights before the Forge window closes.");
                    return true;
                }
            } catch (Throwable ignored) {
                glfwWindowHandle = 0L;
            }
        }
        //?}
        return false;
    }

    private static boolean isMinecraftStopped(Minecraft minecraft) {
        if (minecraft == null) {
            return false;
        }
        for (Class<?> type = minecraft.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() == boolean.class
                        && ("running".equals(field.getName()) || "field_71425_J".equals(field.getName()))) {
                    try {
                        field.setAccessible(true);
                        return !field.getBoolean(minecraft);
                    } catch (Throwable ignored) {
                        return false;
                    }
                }
            }
        }
        return false;
    }
    //?}

    //? if loader_forge && >=1.13.2 && <26.3 {
    private static long findGlfwWindowHandle(Minecraft minecraft) {
        if (minecraft == null) {
            return 0L;
        }
        for (Class<?> type = minecraft.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getParameterTypes().length == 0
                        && method.getReturnType().getSimpleName().toLowerCase().contains("window")) {
                    try {
                        method.setAccessible(true);
                        long handle = extractGlfwWindowHandle(method.invoke(minecraft));
                        if (handle != 0L) {
                            return handle;
                        }
                    } catch (Throwable ignored) {
                        // Continue with other mapped accessors and fields.
                    }
                }
            }
            for (Field field : type.getDeclaredFields()) {
                if (field.getType().getSimpleName().toLowerCase().contains("window")) {
                    try {
                        field.setAccessible(true);
                        long handle = extractGlfwWindowHandle(field.get(minecraft));
                        if (handle != 0L) {
                            return handle;
                        }
                    } catch (Throwable ignored) {
                        // Continue with other mapped accessors and fields.
                    }
                }
            }
        }
        return 0L;
    }

    private static long extractGlfwWindowHandle(Object window) {
        if (window == null) {
            return 0L;
        }
        for (Class<?> type = window.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                String name = method.getName().toLowerCase();
                if (method.getParameterTypes().length == 0
                        && ("gethandle".equals(name) || "getwindow".equals(name))
                        && (method.getReturnType() == long.class || method.getReturnType() == Long.class)) {
                    try {
                        method.setAccessible(true);
                        long handle = ((Number) method.invoke(window)).longValue();
                        if (isVisibleGlfwWindow(handle)) {
                            return handle;
                        }
                    } catch (Throwable ignored) {
                        // Try other window members.
                    }
                }
            }
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() == long.class || field.getType() == Long.class) {
                    try {
                        field.setAccessible(true);
                        long handle = ((Number) field.get(window)).longValue();
                        if (isVisibleGlfwWindow(handle)) {
                            return handle;
                        }
                    } catch (Throwable ignored) {
                        // Try other window members.
                    }
                }
            }
        }
        return 0L;
    }

    private static boolean isVisibleGlfwWindow(long handle) {
        if (handle == 0L) {
            return false;
        }
        try {
            return GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_VISIBLE) == GLFW.GLFW_TRUE;
        } catch (Throwable ignored) {
            return false;
        }
    }
    //?}

}
