package megabytesme.minelights;

import megabytesme.minelights.config.CompassPriority;
import megabytesme.minelights.config.DimmingMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** A small vanilla settings screen for Forge targets without a Cloth Config build. */
public final class MineLightsModernConfigScreen extends Screen {
    private static final int PAGE_SIZE = 8;

    private final Screen parent;
    private final int page;
    private final int optionPage;

    public MineLightsModernConfigScreen(Screen parent) {
        this(parent, 0, 0);
    }

    private MineLightsModernConfigScreen(Screen parent, int page, int optionPage) {
        super(Component.literal("MineLights Configuration"));
        this.parent = parent;
        this.page = page;
        this.optionPage = optionPage;
    }

    @Override
    protected void init() {
        List<Option> options = optionsForPage(page);
        int optionPageCount = Math.max(1, (options.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int selectedOptionPage = Math.min(optionPage, optionPageCount - 1);
        int buttonWidth = Math.min(440, this.width - 40);
        int left = (this.width - buttonWidth) / 2;
        int top = 42;
        int startIndex = selectedOptionPage * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, options.size());

        for (int index = startIndex; index < endIndex; index++) {
            Option option = options.get(index);
            int y = top + (index - startIndex) * 23;
            addButton(option.label(), left, y, buttonWidth, () -> {
                option.change.run();
                MineLightsClient.saveConfig();
                show(new MineLightsModernConfigScreen(parent, page, selectedOptionPage));
            });
        }

        int navY = this.height - 28;
        int pageCount = 5;
        addButton("< " + pageName(page), left, navY, 100,
                () -> show(new MineLightsModernConfigScreen(parent,
                        selectedOptionPage > 0 ? page : Math.max(0, page - 1),
                        selectedOptionPage > 0 ? selectedOptionPage - 1 : page - 1 >= 0
                                ? Math.max(0, (optionsForPage(page - 1).size() - 1) / PAGE_SIZE) : 0)),
                selectedOptionPage > 0 || page > 0);
        addButton(pageName(page) + " >", left + buttonWidth - 100, navY, 100,
                () -> show(new MineLightsModernConfigScreen(parent,
                        selectedOptionPage + 1 < optionPageCount ? page : Math.min(pageCount - 1, page + 1),
                        selectedOptionPage + 1 < optionPageCount ? selectedOptionPage + 1 : 0)),
                selectedOptionPage + 1 < optionPageCount || page + 1 < pageCount);
        addButton("Done", this.width / 2 - 45, navY, 90, () -> closeToParent());
    }

    @Override
    public void onClose() {
        closeToParent();
    }

    private void closeToParent() {
        MineLightsClient.saveConfig();
        show(parent);
    }

    private static void show(Screen screen) {
        //? if >=26.2 {
        Minecraft.getInstance().setScreenAndShow(screen);
        //?} else {
        /* Minecraft.getInstance().setScreen(screen);
        *///?}
    }

    private void addButton(String label, int x, int y, int width, Runnable action) {
        addButton(label, x, y, width, action, true);
    }

    private void addButton(String label, int x, int y, int width, Runnable action, boolean active) {
        Button button = Button.builder(Component.literal(label), ignored -> action.run())
                .bounds(x, y, width, 20)
                .build();
        button.active = active;
        addRenderableWidget(button);
    }

    private List<Option> optionsForPage(int selectedPage) {
        List<Option> options = new ArrayList<>();
        switch (selectedPage) {
            case 0:
                toggle(options, "Enable MineLights", () -> MineLightsClient.CONFIG.enableMod,
                        value -> MineLightsClient.CONFIG.enableMod = value);
                toggle(options, "Start MineLights Server automatically", () -> MineLightsClient.CONFIG.autoStartServer,
                        value -> MineLightsClient.CONFIG.autoStartServer = value);
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
                            if (enabled) {
                                MineLightsClient.CONFIG.disabledDevices.remove(uniqueId);
                            } else if (!MineLightsClient.CONFIG.disabledDevices.contains(uniqueId)) {
                                MineLightsClient.CONFIG.disabledDevices.add(uniqueId);
                            }
                        });
                    }
                }
                break;
            default:
                break;
        }
        return options;
    }

    private static String pageName(int selectedPage) {
        switch (selectedPage) {
            case 0: return "General";
            case 1: return "Integrations";
            case 2: return "Player status";
            case 3: return "Environment";
            default: return "Devices";
        }
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
}
