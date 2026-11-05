package megabytesme.minelights.runtime;

/** Reads the contextual bar selected by the vanilla HUD without relying on obfuscated field names. */
public final class PlayerHudBar {
    private PlayerHudBar() {}

    //? if >=1.21.6 && (loader_forge || loader_neoforge || >=26.1) {
    public static String read(net.minecraft.client.Minecraft client) {
        //? if >=26.2 {
        return readBar(client.gui.hud,
                net.minecraft.client.gui.contextualbar.ExperienceBar.class,
                net.minecraft.client.gui.contextualbar.LocatorBar.class,
                net.minecraft.client.gui.contextualbar.JumpableVehicleBar.class);
        //?} else {
        /*return readBar(client.gui,
                net.minecraft.client.gui.contextualbar.ExperienceBarRenderer.class,
                net.minecraft.client.gui.contextualbar.LocatorBarRenderer.class,
                net.minecraft.client.gui.contextualbar.JumpableVehicleBarRenderer.class);
        *///?}
    }

    private static String readBar(Object hud, Class<?> experience, Class<?> locator, Class<?> jump) {
        try {
            for (java.lang.reflect.Field field : hud.getClass().getDeclaredFields()) {
                if (field.getType() != org.apache.commons.lang3.tuple.Pair.class) continue;
                field.setAccessible(true);
                Object pair = field.get(hud);
                if (!(pair instanceof org.apache.commons.lang3.tuple.Pair)) continue;
                Object bar = ((org.apache.commons.lang3.tuple.Pair<?, ?>) pair).getRight();
                if (experience.isInstance(bar)) return "EXPERIENCE";
                if (locator.isInstance(bar)) return "LOCATOR";
                if (jump.isInstance(bar)) return "JUMP";
                return "EMPTY";
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Fall back to waypoint presence if another mod changes the HUD.
        }
        return null;
    }
    //?}
}
