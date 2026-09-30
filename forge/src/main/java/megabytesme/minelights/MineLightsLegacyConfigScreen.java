package megabytesme.minelights;

import megabytesme.minelights.config.CompassPriority;
import megabytesme.minelights.config.DimmingMode;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** A small Forge-native settings screen for versions before Cloth Config is available. */
public final class MineLightsLegacyConfigScreen extends GuiScreen {
    private static final int PAGE_PREVIOUS = 100;
    private static final int PAGE_NEXT = 101;
    private static final int DONE = 102;
    private static final int PAGE_SIZE = 8;

    private final GuiScreen parent;
    private final String[] pages = {"General", "Integrations", "Player status", "Environment", "Devices"};
    private int page;
    private int optionPage;
    private int optionPageCount;
    private List<Option> visibleOptions = new ArrayList<>();

    public MineLightsLegacyConfigScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        List<Option> options = optionsForPage(page);
        optionPageCount = Math.max(1, (options.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        optionPage = Math.min(optionPage, optionPageCount - 1);
        visibleOptions = options.subList(optionPage * PAGE_SIZE,
                Math.min((optionPage + 1) * PAGE_SIZE, options.size()));

        int buttonWidth = Math.min(440, this.width - 40);
        int left = (this.width - buttonWidth) / 2;
        int top = 42;
        for (int index = 0; index < visibleOptions.size(); index++) {
            Option option = visibleOptions.get(index);
            this.buttonList.add(new GuiButton(index, left, top + index * 23, buttonWidth, 20, option.label()));
        }

        int navY = this.height - 28;
        GuiButton previous = new GuiButton(PAGE_PREVIOUS, left, navY, 100, 20, "< " + pages[page]);
        previous.enabled = optionPage > 0 || page > 0;
        this.buttonList.add(previous);
        GuiButton next = new GuiButton(PAGE_NEXT, left + buttonWidth - 100, navY, 100, 20, pages[page] + " >");
        next.enabled = optionPage + 1 < optionPageCount || page + 1 < pages.length;
        this.buttonList.add(next);
        this.buttonList.add(new GuiButton(DONE, this.width / 2 - 45, navY, 90, 20, "Done"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id >= 0 && button.id < visibleOptions.size()) {
            visibleOptions.get(button.id).change.run();
            MineLightsClient.saveConfig();
            initGui();
        } else if (button.id == PAGE_PREVIOUS) {
            if (optionPage > 0) {
                optionPage--;
            } else if (page > 0) {
                page--;
                optionPage = Math.max(0, (optionsForPage(page).size() - 1) / PAGE_SIZE);
            }
            initGui();
        } else if (button.id == PAGE_NEXT) {
            if (optionPage + 1 < optionPageCount) {
                optionPage++;
            } else if (page + 1 < pages.length) {
                page++;
                optionPage = 0;
            }
            initGui();
        } else if (button.id == DONE) {
            MineLightsClient.saveConfig();
            this.mc.displayGuiScreen(parent);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void onGuiClosed() {
        MineLightsClient.saveConfig();
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
                toggle(options, "Corsair", () -> MineLightsClient.CONFIG.enableCorsair,
                        value -> MineLightsClient.CONFIG.enableCorsair = value);
                toggle(options, "ASUS", () -> MineLightsClient.CONFIG.enableAsus,
                        value -> MineLightsClient.CONFIG.enableAsus = value);
                toggle(options, "Logitech", () -> MineLightsClient.CONFIG.enableLogitech,
                        value -> MineLightsClient.CONFIG.enableLogitech = value);
                toggle(options, "Razer", () -> MineLightsClient.CONFIG.enableRazer,
                        value -> MineLightsClient.CONFIG.enableRazer = value);
                toggle(options, "Wooting", () -> MineLightsClient.CONFIG.enableWooting,
                        value -> MineLightsClient.CONFIG.enableWooting = value);
                toggle(options, "SteelSeries", () -> MineLightsClient.CONFIG.enableSteelSeries,
                        value -> MineLightsClient.CONFIG.enableSteelSeries = value);
                toggle(options, "MSI", () -> MineLightsClient.CONFIG.enableMsi,
                        value -> MineLightsClient.CONFIG.enableMsi = value);
                toggle(options, "Novation", () -> MineLightsClient.CONFIG.enableNovation,
                        value -> MineLightsClient.CONFIG.enableNovation = value);
                toggle(options, "PicoPi", () -> MineLightsClient.CONFIG.enablePicoPi,
                        value -> MineLightsClient.CONFIG.enablePicoPi = value);
                toggle(options, "OpenRGB", () -> MineLightsClient.CONFIG.enableOpenRgb,
                        value -> MineLightsClient.CONFIG.enableOpenRgb = value);
                toggle(options, "Yeelight", () -> MineLightsClient.CONFIG.enableYeelight,
                        value -> MineLightsClient.CONFIG.enableYeelight = value);
                break;
            case 2:
                toggle(options, "Health bar", () -> MineLightsClient.CONFIG.enableHealthBar,
                        value -> MineLightsClient.CONFIG.enableHealthBar = value);
                toggle(options, "Hunger bar", () -> MineLightsClient.CONFIG.enableHungerBar,
                        value -> MineLightsClient.CONFIG.enableHungerBar = value);
                toggle(options, "Saturation bar", () -> MineLightsClient.CONFIG.enableSaturationBar,
                        value -> MineLightsClient.CONFIG.enableSaturationBar = value);
                toggle(options, "Experience bar", () -> MineLightsClient.CONFIG.enableExperienceBar,
                        value -> MineLightsClient.CONFIG.enableExperienceBar = value);
                toggle(options, "Locator bar", () -> MineLightsClient.CONFIG.enableLocatorBar,
                        value -> MineLightsClient.CONFIG.enableLocatorBar = value);
                toggle(options, "Compass effect", () -> MineLightsClient.CONFIG.enableCompassEffect,
                        value -> MineLightsClient.CONFIG.enableCompassEffect = value);
                toggle(options, "Always show compass", () -> MineLightsClient.CONFIG.alwaysShowCompass,
                        value -> MineLightsClient.CONFIG.alwaysShowCompass = value);
                cycle(options, "Compass priority", () -> MineLightsClient.CONFIG.compassPriority.toString(), () -> {
                    CompassPriority[] values = CompassPriority.values();
                    int current = MineLightsClient.CONFIG.compassPriority.ordinal();
                    MineLightsClient.CONFIG.compassPriority = values[(current + 1) % values.length];
                });
                toggle(options, "Low-health warning", () -> MineLightsClient.CONFIG.enableLowHealthWarning,
                        value -> MineLightsClient.CONFIG.enableLowHealthWarning = value);
                toggle(options, "Highlight movement keys", () -> MineLightsClient.CONFIG.highlightMovementKeys,
                        value -> MineLightsClient.CONFIG.highlightMovementKeys = value);
                toggle(options, "Pulse chat key", () -> MineLightsClient.CONFIG.pulseChatKey,
                        value -> MineLightsClient.CONFIG.pulseChatKey = value);
                break;
            case 3:
                toggle(options, "Biome effects", () -> MineLightsClient.CONFIG.enableBiomeEffects,
                        value -> MineLightsClient.CONFIG.enableBiomeEffects = value);
                cycle(options, "Dimming mode", () -> MineLightsClient.CONFIG.dimmingMode.toString(), () -> {
                    DimmingMode[] values = DimmingMode.values();
                    int current = MineLightsClient.CONFIG.dimmingMode.ordinal();
                    MineLightsClient.CONFIG.dimmingMode = values[(current + 1) % values.length];
                });
                cycle(options, "Minimum brightness", () -> String.format("%.2f", MineLightsClient.CONFIG.minBrightness), () -> {
                    float next = MineLightsClient.CONFIG.minBrightness + 0.1F;
                    MineLightsClient.CONFIG.minBrightness = next > 1.0F ? 0.0F : next;
                });
                toggle(options, "Weather effects", () -> MineLightsClient.CONFIG.enableWeatherEffects,
                        value -> MineLightsClient.CONFIG.enableWeatherEffects = value);
                toggle(options, "End flash effect", () -> MineLightsClient.CONFIG.enableEndFlashEffect,
                        value -> MineLightsClient.CONFIG.enableEndFlashEffect = value);
                toggle(options, "On-fire effect", () -> MineLightsClient.CONFIG.enableOnFireEffect,
                        value -> MineLightsClient.CONFIG.enableOnFireEffect = value);
                toggle(options, "Underwater effect", () -> MineLightsClient.CONFIG.enableInWaterEffect,
                        value -> MineLightsClient.CONFIG.enableInWaterEffect = value);
                toggle(options, "Portal effects", () -> MineLightsClient.CONFIG.enablePortalEffects,
                        value -> MineLightsClient.CONFIG.enablePortalEffects = value);
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

    private static void toggle(List<Option> options, String label, BooleanSupplier value, Consumer<Boolean> setter) {
        options.add(new Option(label, () -> value.getAsBoolean() ? "ON" : "OFF",
                () -> setter.accept(!value.getAsBoolean())));
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
