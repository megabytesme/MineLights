package megabytesme.minelights;

import megabytesme.minelights.config.CompassPriority;
import megabytesme.minelights.config.DimmingMode;
//? if loader_forge && <=1.13.2 {
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
//?} else {
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.text.StringTextComponent;
//?}

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** A vanilla settings menu for Forge versions with a post-1.12 GUI API and no Cloth Config screen. */
//? if loader_forge && <=1.13.2 {
public final class MineLightsTransitionalConfigScreen extends GuiScreen {
//?} else {
/* public final class MineLightsTransitionalConfigScreen extends Screen {
*///?}
    private static final int PAGE_SIZE = 8;
    //? if loader_forge && <=1.13.2 {
    private final GuiScreen parent;
    //?} else {
    /* private final Screen parent;
    *///?}
    private int page;
    private int optionPage;
    private int optionPageCount;

    //? if loader_forge && <=1.13.2 {
    public MineLightsTransitionalConfigScreen(GuiScreen parent) {
        this.parent = parent;
    }
    //?} else {
    /* public MineLightsTransitionalConfigScreen(Screen parent) {
        super(new StringTextComponent("MineLights Configuration"));
        this.parent = parent;
    }
    *///?}

    //? if loader_forge && <=1.13.2 {
    @Override
    public void initGui() {
        this.buttons.clear();
        this.children.clear();
    //?} else {
    /* @Override
    protected void init() {
    *///?}
        List<Option> options = optionsForPage(page);
        optionPageCount = Math.max(1, (options.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        optionPage = Math.min(optionPage, optionPageCount - 1);
        int buttonWidth = Math.min(440, this.width - 40);
        int left = (this.width - buttonWidth) / 2;
        int start = optionPage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, options.size());

        for (int index = start; index < end; index++) {
            Option option = options.get(index);
            int y = 42 + (index - start) * 23;
            addMenuButton(index, left, y, buttonWidth, option.label(), () -> {
                option.change.run();
                MineLightsClient.saveConfig();
                open(new MineLightsTransitionalConfigScreen(parent, page, optionPage));
            }, true);
        }

        int navY = this.height - 28;
        addMenuButton(100, left, navY, 100, "< Back", () -> {
            if (optionPage > 0) {
                open(new MineLightsTransitionalConfigScreen(parent, page, optionPage - 1));
            } else if (page > 0) {
                int previousPage = page - 1;
                int lastOptionPage = Math.max(0, (optionsForPage(previousPage).size() - 1) / PAGE_SIZE);
                open(new MineLightsTransitionalConfigScreen(parent, previousPage, lastOptionPage));
            }
        }, optionPage > 0 || page > 0);
        addMenuButton(101, left + buttonWidth - 100, navY, 100, "Next >", () -> {
            if (optionPage + 1 < optionPageCount) {
                open(new MineLightsTransitionalConfigScreen(parent, page, optionPage + 1));
            } else if (page < 4) {
                open(new MineLightsTransitionalConfigScreen(parent, page + 1, 0));
            }
        }, optionPage + 1 < optionPageCount || page < 4);
        addMenuButton(102, this.width / 2 - 45, navY, 90, "Done", this::closeToParent, true);
    }

    //? if loader_forge && <=1.13.2 {
    private MineLightsTransitionalConfigScreen(GuiScreen parent, int page, int optionPage) {
    //?} else {
    /* private MineLightsTransitionalConfigScreen(Screen parent, int page, int optionPage) {
        super(new StringTextComponent("MineLights Configuration"));
    *///?}
        this.parent = parent;
        this.page = page;
        this.optionPage = optionPage;
    }

    private void open(MineLightsTransitionalConfigScreen screen) {
        //? if loader_forge && <=1.13.2 {
        this.mc.displayGuiScreen(screen);
        //?} else if loader_forge && <=1.14.3 {
        /* Minecraft.getInstance().displayGuiScreen(screen);
        *///?} else if loader_forge && >=1.16.1 && <1.16.2 {
        /* Minecraft.getInstance().displayGuiScreen(screen);
        *///?} else {
        /* Minecraft.getInstance().setScreen(screen);
        *///?}
    }

    private void addMenuButton(int id, int x, int y, int width, String label, Runnable action, boolean active) {
        //? if loader_forge && <=1.13.2 {
        addButton(new ConfigButton(id, x, y, width, 20, label, action, active));
        //?} else {
        /* Button button = new Button(x, y, width, 20,
                //? if loader_forge && >=1.16.1 {
                new StringTextComponent(label),
                //?} else {
                label,
                //?}
                ignored -> action.run());
        button.active = active;
        addButton(button);
        *///?}
    }

    private void closeToParent() {
        MineLightsClient.saveConfig();
        //? if loader_forge && <=1.13.2 {
        this.mc.displayGuiScreen(parent);
        //?} else if loader_forge && <=1.14.3 {
        /* Minecraft.getInstance().displayGuiScreen(parent);
        *///?} else if loader_forge && >=1.16.1 && <1.16.2 {
        /* Minecraft.getInstance().displayGuiScreen(parent);
        *///?} else {
        /* Minecraft.getInstance().setScreen(parent);
        *///?}
    }

    //? if loader_forge && >=1.14.2 {
    @Override
    public void onClose() {
        closeToParent();
    }
    //?}

    private List<Option> optionsForPage(int selectedPage) {
        List<Option> options = new ArrayList<>();
        switch (selectedPage) {
            case 0:
                toggle(options, "Enable MineLights", () -> MineLightsClient.CONFIG.enableMod, value -> MineLightsClient.CONFIG.enableMod = value);
                toggle(options, "Start MineLights Server automatically", () -> MineLightsClient.CONFIG.autoStartServer, value -> MineLightsClient.CONFIG.autoStartServer = value);
                break;
            case 1:
                toggle(options, "Corsair", () -> MineLightsClient.CONFIG.enableCorsair, value -> MineLightsClient.CONFIG.enableCorsair = value);
                toggle(options, "ASUS", () -> MineLightsClient.CONFIG.enableAsus, value -> MineLightsClient.CONFIG.enableAsus = value);
                toggle(options, "Logitech", () -> MineLightsClient.CONFIG.enableLogitech, value -> MineLightsClient.CONFIG.enableLogitech = value);
                toggle(options, "Razer", () -> MineLightsClient.CONFIG.enableRazer, value -> MineLightsClient.CONFIG.enableRazer = value);
                toggle(options, "Wooting", () -> MineLightsClient.CONFIG.enableWooting, value -> MineLightsClient.CONFIG.enableWooting = value);
                toggle(options, "SteelSeries", () -> MineLightsClient.CONFIG.enableSteelSeries, value -> MineLightsClient.CONFIG.enableSteelSeries = value);
                toggle(options, "MSI", () -> MineLightsClient.CONFIG.enableMsi, value -> MineLightsClient.CONFIG.enableMsi = value);
                toggle(options, "Novation", () -> MineLightsClient.CONFIG.enableNovation, value -> MineLightsClient.CONFIG.enableNovation = value);
                toggle(options, "PicoPi", () -> MineLightsClient.CONFIG.enablePicoPi, value -> MineLightsClient.CONFIG.enablePicoPi = value);
                toggle(options, "OpenRGB", () -> MineLightsClient.CONFIG.enableOpenRgb, value -> MineLightsClient.CONFIG.enableOpenRgb = value);
                toggle(options, "Yeelight", () -> MineLightsClient.CONFIG.enableYeelight, value -> MineLightsClient.CONFIG.enableYeelight = value);
                break;
            case 2:
                toggle(options, "Health bar", () -> MineLightsClient.CONFIG.enableHealthBar, value -> MineLightsClient.CONFIG.enableHealthBar = value);
                toggle(options, "Hunger bar", () -> MineLightsClient.CONFIG.enableHungerBar, value -> MineLightsClient.CONFIG.enableHungerBar = value);
                toggle(options, "Saturation bar", () -> MineLightsClient.CONFIG.enableSaturationBar, value -> MineLightsClient.CONFIG.enableSaturationBar = value);
                toggle(options, "Experience bar", () -> MineLightsClient.CONFIG.enableExperienceBar, value -> MineLightsClient.CONFIG.enableExperienceBar = value);
                toggle(options, "Locator bar", () -> MineLightsClient.CONFIG.enableLocatorBar, value -> MineLightsClient.CONFIG.enableLocatorBar = value);
                toggle(options, "Compass effect", () -> MineLightsClient.CONFIG.enableCompassEffect, value -> MineLightsClient.CONFIG.enableCompassEffect = value);
                toggle(options, "Always show compass", () -> MineLightsClient.CONFIG.alwaysShowCompass, value -> MineLightsClient.CONFIG.alwaysShowCompass = value);
                cycle(options, "Compass priority", () -> MineLightsClient.CONFIG.compassPriority.toString(), () -> {
                    CompassPriority[] values = CompassPriority.values();
                    MineLightsClient.CONFIG.compassPriority = values[(MineLightsClient.CONFIG.compassPriority.ordinal() + 1) % values.length];
                });
                toggle(options, "Low-health warning", () -> MineLightsClient.CONFIG.enableLowHealthWarning, value -> MineLightsClient.CONFIG.enableLowHealthWarning = value);
                toggle(options, "Highlight movement keys", () -> MineLightsClient.CONFIG.highlightMovementKeys, value -> MineLightsClient.CONFIG.highlightMovementKeys = value);
                toggle(options, "Pulse chat key", () -> MineLightsClient.CONFIG.pulseChatKey, value -> MineLightsClient.CONFIG.pulseChatKey = value);
                break;
            case 3:
                toggle(options, "Biome effects", () -> MineLightsClient.CONFIG.enableBiomeEffects, value -> MineLightsClient.CONFIG.enableBiomeEffects = value);
                cycle(options, "Dimming mode", () -> MineLightsClient.CONFIG.dimmingMode.toString(), () -> {
                    DimmingMode[] values = DimmingMode.values();
                    MineLightsClient.CONFIG.dimmingMode = values[(MineLightsClient.CONFIG.dimmingMode.ordinal() + 1) % values.length];
                });
                cycle(options, "Minimum brightness", () -> String.format("%.2f", MineLightsClient.CONFIG.minBrightness), () -> {
                    float next = MineLightsClient.CONFIG.minBrightness + 0.1F;
                    MineLightsClient.CONFIG.minBrightness = next > 1.0F ? 0.0F : next;
                });
                toggle(options, "Weather effects", () -> MineLightsClient.CONFIG.enableWeatherEffects, value -> MineLightsClient.CONFIG.enableWeatherEffects = value);
                toggle(options, "End flash effect", () -> MineLightsClient.CONFIG.enableEndFlashEffect, value -> MineLightsClient.CONFIG.enableEndFlashEffect = value);
                toggle(options, "On-fire effect", () -> MineLightsClient.CONFIG.enableOnFireEffect, value -> MineLightsClient.CONFIG.enableOnFireEffect = value);
                toggle(options, "Underwater effect", () -> MineLightsClient.CONFIG.enableInWaterEffect, value -> MineLightsClient.CONFIG.enableInWaterEffect = value);
                toggle(options, "Portal effects", () -> MineLightsClient.CONFIG.enablePortalEffects, value -> MineLightsClient.CONFIG.enablePortalEffects = value);
                break;
            case 4:
                List<String> devices = new ArrayList<>(MineLightsClient.discoveredDevices);
                devices.sort(Comparator.naturalOrder());
                if (devices.isEmpty()) {
                    cycle(options, "No RGB devices discovered yet", () -> "", () -> { });
                } else {
                    for (String uniqueId : devices) {
                        String[] parts = uniqueId.split("\\|", 2);
                        String name = parts.length > 1 ? parts[1] : uniqueId;
                        toggle(options, name, () -> !MineLightsClient.CONFIG.disabledDevices.contains(uniqueId), enabled -> {
                            if (enabled) MineLightsClient.CONFIG.disabledDevices.remove(uniqueId);
                            else if (!MineLightsClient.CONFIG.disabledDevices.contains(uniqueId)) MineLightsClient.CONFIG.disabledDevices.add(uniqueId);
                        });
                    }
                }
                break;
            default:
                break;
        }
        return options;
    }

    private static void toggle(List<Option> options, String label, BooleanSupplier value, Consumer<Boolean> setter) {
        options.add(new Option(label, () -> value.getAsBoolean() ? "ON" : "OFF", () -> setter.accept(!value.getAsBoolean())));
    }

    private static void cycle(List<Option> options, String label, Supplier<String> value, Runnable change) {
        options.add(new Option(label, value, change));
    }

    private static final class Option {
        private final String name;
        private final Supplier<String> value;
        private final Runnable change;

        private Option(String name, Supplier<String> value, Runnable change) {
            this.name = name;
            this.value = value;
            this.change = change;
        }

        private String label() {
            return name + (value.get().isEmpty() ? "" : ": " + value.get());
        }
    }

    //? if loader_forge && <=1.13.2 {
    private static final class ConfigButton extends GuiButton {
        private final Runnable action;

        private ConfigButton(int id, int x, int y, int width, int height, String label, Runnable action, boolean active) {
            super(id, x, y, width, height, label);
            this.action = action;
            this.enabled = active;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            action.run();
        }
    }
    //?}
}
