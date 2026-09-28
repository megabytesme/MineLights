package megabytesme.minelights.quilt;

import megabytesme.minelights.MineLightsClient;
//? if >=26.1 {
/*import net.minecraft.client.Minecraft;
*///?} else {
 import net.minecraft.client.MinecraftClient;
//?}
import org.quiltmc.loader.api.QuiltLoader;

public final class QuiltClientBootstrap {
    private static final MineLightsClient CLIENT = new MineLightsClient();
    private static boolean initialized;

    private QuiltClientBootstrap() {
    }

    //? if >=26.1 {
    /*public static void onClientTick(Minecraft minecraft) {
    *///?} else {
     public static void onClientTick(MinecraftClient minecraft) {
    //?}
        if (!initialized) {
            String version = QuiltLoader.getModContainer(MineLightsClient.MOD_ID)
                    .map(container -> container.metadata().version().raw())
                    .orElse(MineLightsClient.MOD_VERSION);
            CLIENT.init(QuiltLoader.getConfigDir(), version, "quilt");
            initialized = true;
        }

        CLIENT.onClientTick(minecraft);
    }
}
