package megabytesme.minelights;

import megabytesme.minelights.config.CompassFallbackTarget;
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
        int pageSize = pageSizeFor(page);
        optionPageCount = pageCountFor(page);
        optionPage = Math.min(optionPage, optionPageCount - 1);
        int buttonWidth = Math.min(440, this.width - 40);
        int left = (this.width - buttonWidth) / 2;
        int start = optionPage * pageSize;
        int end = Math.min(start + pageSize, options.size());

        for (int index = start; index < end; index++) {
            Option option = options.get(index);
            int y = 42 + (index - start) * 23;
            addMenuButton(index, left, y, buttonWidth, option.label(), () -> {
                option.change.run();
                MineLightsClient.saveConfig();
                open(new MineLightsTransitionalConfigScreen(parent, page, optionPage));
            }, option.active);
        }

        int navY = this.height - 28;
        addMenuButton(100, left, navY, 100, "< Back", () -> {
            if (optionPage > 0) {
                open(new MineLightsTransitionalConfigScreen(parent, page, optionPage - 1));
            } else if (page > 0) {
                int previousPage = page - 1;
                int lastOptionPage = pageCountFor(previousPage) - 1;
                open(new MineLightsTransitionalConfigScreen(parent, previousPage, lastOptionPage));
            }
        }, optionPage > 0 || page > 0);
        addMenuButton(101, left + buttonWidth - 100, navY, 100, "Next >", () -> {
            if (optionPage + 1 < optionPageCount) {
                open(new MineLightsTransitionalConfigScreen(parent, page, optionPage + 1));
            } else if (page < 5) {
                open(new MineLightsTransitionalConfigScreen(parent, page + 1, 0));
            }
        }, optionPage + 1 < optionPageCount || page < 5);
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
                if (MineLightsClient.IS_WINDOWS) {
                    toggle(options, "Manage MineLights Server manually", () -> MineLightsClient.CONFIG.manualServerManagement,
                            value -> MineLightsClient.CONFIG.manualServerManagement = value);
                }
                toggle(options, "Start MineLights Server automatically", () -> MineLightsClient.CONFIG.autoStartServer, value -> MineLightsClient.CONFIG.autoStartServer = value);
                if (MineLightsClient.IS_WINDOWS) {
                    toggle(options, "Notify when MineLights Server is missing", () -> MineLightsClient.CONFIG.notifyMissingServer, value -> MineLightsClient.CONFIG.notifyMissingServer = value);
                    toggle(options, "Check MineLights Server for updates", () -> MineLightsClient.CONFIG.checkServerUpdates, value -> MineLightsClient.CONFIG.checkServerUpdates = value);
                    toggle(options, translated("option.mine-lights.notifyServerDownloadProgress"), () -> MineLightsClient.CONFIG.notifyServerDownloadProgress, value -> MineLightsClient.CONFIG.notifyServerDownloadProgress = value);
                }
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
                toggle(options, "Compass effect", () -> MineLightsClient.CONFIG.enableCompassEffect, value -> MineLightsClient.CONFIG.enableCompassEffect = value);
                toggle(options, "Always show compass", () -> MineLightsClient.CONFIG.alwaysShowCompass, value -> MineLightsClient.CONFIG.alwaysShowCompass = value);
                toggle(options, "Ignore carried compasses", () -> MineLightsClient.CONFIG.ignoreInventoryCompass, value -> MineLightsClient.CONFIG.ignoreInventoryCompass = value);
                toggle(options, "Spin compass without target", () -> MineLightsClient.CONFIG.spinCompassWithoutTarget, value -> MineLightsClient.CONFIG.spinCompassWithoutTarget = value);
                cycle(options, "Compass target without one", () -> String.valueOf(MineLightsClient.CONFIG.compassFallbackTarget), () -> {
                    CompassFallbackTarget[] values = CompassFallbackTarget.values();
                    int current = MineLightsClient.CONFIG.compassFallbackTarget == null ? -1 : MineLightsClient.CONFIG.compassFallbackTarget.ordinal();
                    MineLightsClient.CONFIG.compassFallbackTarget = values[(current + 1) % values.length];
                });
                cycle(options, "Compass priority", () -> MineLightsClient.CONFIG.compassPriority.toString(), () -> {
                    CompassPriority[] values = CompassPriority.values();
                    MineLightsClient.CONFIG.compassPriority = values[(MineLightsClient.CONFIG.compassPriority.ordinal() + 1) % values.length];
                });
                toggle(options, "Low-health warning", () -> MineLightsClient.CONFIG.enableLowHealthWarning, value -> MineLightsClient.CONFIG.enableLowHealthWarning = value);
                toggle(options, "Absorption overlay", () -> MineLightsClient.CONFIG.enableAbsorptionOverlay, value -> MineLightsClient.CONFIG.enableAbsorptionOverlay = value);
                toggle(options, "Merge saturation into hunger", () -> MineLightsClient.CONFIG.mergeSaturationIntoHunger, value -> MineLightsClient.CONFIG.mergeSaturationIntoHunger = value);
                toggle(options, "Restore OpenRGB outside worlds", () -> MineLightsClient.CONFIG.restoreLightingOutsideWorld, value -> MineLightsClient.CONFIG.restoreLightingOutsideWorld = value);
                cycle(options, "Low-health threshold", () -> String.format("%.0f", MineLightsClient.CONFIG.lowHealthThreshold), () -> MineLightsClient.CONFIG.lowHealthThreshold = MineLightsClient.CONFIG.lowHealthThreshold >= 20.0F ? 1.0F : Math.min(20.0F, MineLightsClient.CONFIG.lowHealthThreshold + 1.0F));
                cycle(options, "Saturation tint strength", () -> String.format("%.1f", MineLightsClient.CONFIG.saturationTintStrength), () -> MineLightsClient.CONFIG.saturationTintStrength = MineLightsClient.CONFIG.saturationTintStrength >= 1.0F ? 0.0F : Math.min(1.0F, MineLightsClient.CONFIG.saturationTintStrength + 0.1F));
                options.add(new Option("Keys and colors: /minelights set", () -> "", () -> { }, false));
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
                toggle(options, "Mirror DRAM rain direction", () -> MineLightsClient.CONFIG.mirrorRamRainDirection, value -> MineLightsClient.CONFIG.mirrorRamRainDirection = value);
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
                        String name = MineLightsClient.getDiscoveredDeviceDisplayName(uniqueId);
                        toggle(options, name, () -> MineLightsClient.isDeviceEnabled(uniqueId), enabled -> MineLightsClient.setDeviceEnabled(uniqueId, enabled));
                    }
                }
                break;
            case 5:
                aboutText(options, "MineLights", "Version " + MineLightsClient.getModVersion(),
                        "RGB lighting integrations for Minecraft Java Edition.", "Copyright \u00a9 2026 MegaBytesMe",
                        "Source code: github.com/megabytesme/MineLights",
                        "Report issues: github.com/megabytesme/MineLights/issues",
                        "Support development: ko-fi.com/megabytesme");
                break;
            default:
                break;
        }
        return options;
    }

    private int pageSizeFor(int selectedPage) {
        return selectedPage == 5 ? 4 : PAGE_SIZE;
    }

    private int pageCountFor(int selectedPage) {
        List<Option> options = optionsForPage(selectedPage);
        int pageSize = pageSizeFor(selectedPage);
        return Math.max(1, (options.size() + pageSize - 1) / pageSize);
    }

    private static void aboutText(List<Option> options, String... lines) {
        for (String line : lines) {
            options.add(new Option(line, () -> "", () -> { }, false));
        }
    }

    private static void toggle(List<Option> options, String label, BooleanSupplier value, Consumer<Boolean> setter) {
        options.add(new Option(label, () -> value.getAsBoolean() ? "ON" : "OFF", () -> setter.accept(!value.getAsBoolean())));
    }

    private static String translated(String key) {
        //? if loader_forge && >=1.14.4 && <1.16.1 {
        return net.minecraft.client.resources.I18n.get(key);
        //?} else if loader_forge && >=1.16.2 && <1.17 {
        return net.minecraft.client.resources.I18n.get(key);
        //?} else {
        return net.minecraft.client.resources.I18n.format(key);
        //?}
    }

    private static void cycle(List<Option> options, String label, Supplier<String> value, Runnable change) {
        options.add(new Option(label, value, change));
    }

    private static final class Option {
        private final String name;
        private final Supplier<String> value;
        private final Runnable change;
        private final boolean active;

        private Option(String name, Supplier<String> value, Runnable change) {
            this(name, value, change, true);
        }

        private Option(String name, Supplier<String> value, Runnable change, boolean active) {
            this.name = name;
            this.value = value;
            this.change = change;
            this.active = active;
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
