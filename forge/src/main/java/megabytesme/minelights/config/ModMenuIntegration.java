package megabytesme.minelights.config;

//? if loader_forge && <1.16.3 {
import me.shedaniel.clothconfig2.forge.api.ConfigBuilder;
import me.shedaniel.clothconfig2.forge.api.ConfigCategory;
import me.shedaniel.clothconfig2.forge.api.ConfigEntryBuilder;
//?} else {
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
//?}
import megabytesme.minelights.MineLightsClient;
import megabytesme.minelights.network.CommandClient;
//? if loader_forge && <1.17 {
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
//?} else {
import net.minecraft.ChatFormatting;
//?}
import net.minecraft.client.Minecraft;
//? if loader_forge && <1.17 {
import net.minecraft.client.gui.screen.AlertScreen;
import net.minecraft.client.gui.screen.Screen;
//?} else {
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
//? if <1.19 {
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
//?}
//?}
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class ModMenuIntegration {
    private static final Logger LOGGER = LogManager.getLogger("MineLights-ConfigScreen");

    private ModMenuIntegration() {
    }

    public static Screen createConfigScreen(Screen parent) {
        LOGGER.info("Opening Forge config screen for MineLights.");

        try {
            return buildConfigScreen(parent);
        } catch (Throwable throwable) {
            LOGGER.error("Failed to build Forge config screen. Falling back to an error screen.", throwable);
            String message = throwable.getMessage() == null ? "No additional details were provided." : throwable.getMessage();
            return new AlertScreen(
                    //? if >=26.2 {
                    () -> Minecraft.getInstance().setScreenAndShow(parent),
                    //?} else {
                    /* () -> Minecraft.getInstance().setScreen(parent),
                    *///?}
                    literal("MineLights Config Error"),
                    literal("MineLights could not open its config screen: " + message)
            );
        }
    }

    //? if loader_forge && <1.17 {
    private static IFormattableTextComponent translatable(String key, Object... args) {
        return new TranslationTextComponent(key, args);
    }
    //?} else {
    private static MutableComponent translatable(String key, Object... args) {
        //? if >=1.19 {
        return Component.translatable(key, args);
        //?} else {
        /* return new TranslatableComponent(key, args);
        *///?}
    }
    //?}

    //? if loader_forge && <1.17 {
    private static IFormattableTextComponent literal(String text) {
        return new StringTextComponent(text);
    }
    //?} else {
    private static MutableComponent literal(String text) {
        //? if >=1.19 {
        return Component.literal(text);
        //?} else {
        /* return new TextComponent(text);
        *///?}
    }
    //?}

    //? if loader_forge && <1.17 {
    private static IFormattableTextComponent gray(IFormattableTextComponent component) {
        return component.copy().withStyle(TextFormatting.GRAY);
    }
    //?} else {
    private static MutableComponent gray(MutableComponent component) {
        return component.copy().withStyle(ChatFormatting.GRAY);
    }
    //?}

    private static Screen buildConfigScreen(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(translatable("title.mine-lights.config"));

        builder.setSavingRunnable(() -> {
            MineLightsClient.saveConfig();
            boolean needsRefresh = false;

            if (MineLightsClient.CONFIG.forceServerUpdate) {
                new Thread(MineLightsClient::checkForServerUpdate, "MineLights-Manual-Update-Check").start();
                MineLightsClient.CONFIG.forceServerUpdate = false;
            }
            if (MineLightsClient.CONFIG.refreshDevices) {
                MineLightsClient.CONFIG.refreshDevices = false;
                needsRefresh = true;
            }
            if (MineLightsClient.CONFIG.restartProxy) {
                MineLightsClient.isManualRestart = true;
                CommandClient.sendCommand("restart");
                MineLightsClient.CONFIG.restartProxy = false;
            }
            if (MineLightsClient.CONFIG.restartProxyAsAdmin) {
                MineLightsClient.isManualRestart = true;
                CommandClient.sendCommand("restart_admin");
                MineLightsClient.CONFIG.restartProxyAsAdmin = false;
            }
            if (MineLightsClient.CONFIG.clearDisabledDevices) {
                MineLightsClient.CONFIG.disabledDevices.clear();
                MineLightsClient.saveConfig();
                MineLightsClient.CONFIG.clearDisabledDevices = false;
                needsRefresh = true;
            }

            if (needsRefresh) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }

                MineLightsClient.refreshLightingManager();

                new Thread(() -> {
                    try {
                        waitForDeviceRefresh();
                        //? if >=26.2 {
                        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreenAndShow(buildConfigScreen(parent)));
                        //?} else {
                        /* Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(buildConfigScreen(parent)));
                        *///?}
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }, "MineLights-Forge-Config-Refresh").start();
            }
        });

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        if (MineLightsClient.IS_WINDOWS) {
            ConfigCategory serverManagement = builder.getOrCreateCategory(translatable("category.mine-lights.server_management"));
            //? if loader_forge && <1.17 {
            Supplier<ITextComponent> statusTextSupplier = () -> {
            //?} else {
            Supplier<Component> statusTextSupplier = () -> {
            //?}
                MineLightsClient.DownloadStatus status = MineLightsClient.downloadStatus.get();
                switch (status) {
                    case DOWNLOADING:
                        long soFarMB = MineLightsClient.downloadBytesSoFar.get() / (1024 * 1024);
                        long totalMB = MineLightsClient.downloadTotalBytes.get() / (1024 * 1024);
                        String eta = MineLightsClient.downloadEta.get();
                        String speed = MineLightsClient.downloadSpeedMBps.get();
                        return translatable("minelights.status.downloading",
                                soFarMB, totalMB, MineLightsClient.downloadProgress.get(), eta, speed);
                    case VERIFYING:
                        return translatable("minelights.status.verifying");
                    case SUCCESS:
                        return translatable("minelights.status.success");
                    case FAILED:
                        return translatable("minelights.status.failed", MineLightsClient.downloadError.get());
                    case IDLE:
                    default:
                        return MineLightsClient.isServerRunning()
                                ? translatable("minelights.status.running")
                                : translatable("minelights.status.not_running");
                }
            };

            //? if <26.1 {
            serverManagement.addEntry(new LiveStatusEntry("minelights.status", statusTextSupplier));
            //?} else {
            /*serverManagement.addEntry(entryBuilder.startTextDescription(statusTextSupplier.get()).build());
            serverManagement.addEntry(entryBuilder.startTextDescription(
                    translatable("text.mine-lights.about.description")).build());
            *///?}
            serverManagement.addEntry(entryBuilder
                    .startBooleanToggle(translatable("option.mine-lights.force_update.label"), MineLightsClient.CONFIG.forceServerUpdate)
                    .setDefaultValue(false)
                    .setTooltip(translatable("option.mine-lights.force_update.tooltip"))
                    .setSaveConsumer(newValue -> MineLightsClient.CONFIG.forceServerUpdate = newValue)
                    .build());
            serverManagement.addEntry(entryBuilder
                    .startBooleanToggle(translatable("option.mine-lights.autoStartServer"), MineLightsClient.CONFIG.autoStartServer)
                    .setDefaultValue(true)
                    .setTooltip(translatable("option.mine-lights.autoStartServer.tooltip"))
                    .setSaveConsumer(newValue -> MineLightsClient.CONFIG.autoStartServer = newValue)
                    .build());
            serverManagement.addEntry(entryBuilder.startTextDescription(literal("")).build());
            serverManagement.addEntry(entryBuilder
                    .startBooleanToggle(translatable("option.mine-lights.restart.label"), MineLightsClient.CONFIG.restartProxy)
                    .setDefaultValue(false)
                    .setTooltip(translatable("option.mine-lights.restart.tooltip"))
                    .setSaveConsumer(newValue -> MineLightsClient.CONFIG.restartProxy = newValue)
                    .build());
            serverManagement.addEntry(entryBuilder
                    .startBooleanToggle(translatable("option.mine-lights.restart_admin.label"), MineLightsClient.CONFIG.restartProxyAsAdmin)
                    .setDefaultValue(false)
                    .setTooltip(translatable("option.mine-lights.restart_admin.tooltip"))
                    .setSaveConsumer(newValue -> MineLightsClient.CONFIG.restartProxyAsAdmin = newValue)
                    .build());
        }

        ConfigCategory general = builder.getOrCreateCategory(translatable("category.mine-lights.general"));
        general.addEntry(entryBuilder
                .startBooleanToggle(translatable("option.mine-lights.enableMod"), MineLightsClient.CONFIG.enableMod)
                .setDefaultValue(true)
                .setTooltip(translatable("option.mine-lights.enableMod.tooltip"))
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableMod = newValue)
                .build());
        general.addEntry(entryBuilder
                .startBooleanToggle(translatable("option.mine-lights.refresh_devices.label"), MineLightsClient.CONFIG.refreshDevices)
                .setDefaultValue(false)
                .setTooltip(translatable("option.mine-lights.refresh_devices.tooltip"))
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.refreshDevices = newValue)
                .build());

        ConfigCategory integrations = builder.getOrCreateCategory(translatable("category.mine-lights.integrations"));
        integrations.addEntry(entryBuilder.startBooleanToggle(translatable("integration.mine-lights.corsair"), MineLightsClient.CONFIG.enableCorsair)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableCorsair = newValue)
                .build());
        integrations.addEntry(entryBuilder.startBooleanToggle(translatable("integration.mine-lights.asus"), MineLightsClient.CONFIG.enableAsus)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableAsus = newValue)
                .build());
        integrations.addEntry(entryBuilder.startBooleanToggle(translatable("integration.mine-lights.logitech"), MineLightsClient.CONFIG.enableLogitech)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableLogitech = newValue)
                .build());
        integrations.addEntry(entryBuilder.startBooleanToggle(translatable("integration.mine-lights.razer"), MineLightsClient.CONFIG.enableRazer)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableRazer = newValue)
                .build());
        integrations.addEntry(entryBuilder.startBooleanToggle(translatable("integration.mine-lights.wooting"), MineLightsClient.CONFIG.enableWooting)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableWooting = newValue)
                .build());
        integrations.addEntry(entryBuilder.startBooleanToggle(translatable("integration.mine-lights.steelseries"), MineLightsClient.CONFIG.enableSteelSeries)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableSteelSeries = newValue)
                .build());
        integrations.addEntry(entryBuilder.startBooleanToggle(translatable("integration.mine-lights.msi"), MineLightsClient.CONFIG.enableMsi)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableMsi = newValue)
                .build());
        integrations.addEntry(entryBuilder.startBooleanToggle(translatable("integration.mine-lights.novation"), MineLightsClient.CONFIG.enableNovation)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableNovation = newValue)
                .build());
        integrations.addEntry(entryBuilder.startBooleanToggle(translatable("integration.mine-lights.picopi"), MineLightsClient.CONFIG.enablePicoPi)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enablePicoPi = newValue)
                .build());
        integrations.addEntry(entryBuilder.startBooleanToggle(translatable("integration.mine-lights.openrgb"), MineLightsClient.CONFIG.enableOpenRgb)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableOpenRgb = newValue)
                .build());
        integrations.addEntry(entryBuilder.startBooleanToggle(translatable("integration.mine-lights.yeelight"), MineLightsClient.CONFIG.enableYeelight)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableYeelight = newValue)
                .build());

        ConfigCategory devices = builder.getOrCreateCategory(translatable("category.mine-lights.devices"));
        devices.addEntry(entryBuilder.startTextDescription(translatable("option.mine-lights.device.header")).build());
        MineLightsClient.discoveredDevices.stream().sorted(Comparator.naturalOrder()).forEach(uniqueId -> {
            boolean isEnabled = !MineLightsClient.CONFIG.disabledDevices.contains(uniqueId);
            String[] parts = uniqueId.split("\\|", 2);
            String deviceSdk = parts.length > 1 ? parts[0] : "Unknown";
            String deviceName = parts.length > 1 ? parts[1] : uniqueId;
            //? if loader_forge && <1.17 {
            ITextComponent label = literal(deviceName).append(gray(literal(" (" + deviceSdk + ")")));
            //?} else {
            Component label = literal(deviceName).append(gray(literal(" (" + deviceSdk + ")")));
            //?}

            devices.addEntry(entryBuilder
                    .startBooleanToggle(label, isEnabled)
                    .setDefaultValue(true)
                    .setSaveConsumer(newValue -> {
                        if (newValue) {
                            MineLightsClient.CONFIG.disabledDevices.remove(uniqueId);
                        } else if (!MineLightsClient.CONFIG.disabledDevices.contains(uniqueId)) {
                            MineLightsClient.CONFIG.disabledDevices.add(uniqueId);
                        }
                    })
                    .build());
        });
        if (!MineLightsClient.CONFIG.disabledDevices.isEmpty()) {
            devices.addEntry(entryBuilder.startTextDescription(literal("")).build());
            devices.addEntry(entryBuilder
                    .startBooleanToggle(translatable("option.mine-lights.clear_disabled.label"), MineLightsClient.CONFIG.clearDisabledDevices)
                    .setDefaultValue(false)
                    .setTooltip(translatable("option.mine-lights.clear_disabled.tooltip"))
                    .setSaveConsumer(newValue -> MineLightsClient.CONFIG.clearDisabledDevices = newValue)
                    .build());
        }

        ConfigCategory playerStatus = builder.getOrCreateCategory(translatable("category.mine-lights.player_status"));
        playerStatus.addEntry(entryBuilder.startBooleanToggle(translatable("option.mine-lights.enableHealthBar"), MineLightsClient.CONFIG.enableHealthBar)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableHealthBar = newValue)
                .build());
        playerStatus.addEntry(entryBuilder.startBooleanToggle(translatable("option.mine-lights.enableHungerBar"), MineLightsClient.CONFIG.enableHungerBar)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableHungerBar = newValue)
                .build());
        playerStatus.addEntry(entryBuilder.startBooleanToggle(translatable("option.mine-lights.enableSaturationBar"), MineLightsClient.CONFIG.enableSaturationBar)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableSaturationBar = newValue)
                .build());
        playerStatus.addEntry(entryBuilder.startBooleanToggle(translatable("option.mine-lights.enableExperienceBar"), MineLightsClient.CONFIG.enableExperienceBar)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableExperienceBar = newValue)
                .build());
        playerStatus.addEntry(entryBuilder.startBooleanToggle(translatable("option.mine-lights.enableLocatorBar"), MineLightsClient.CONFIG.enableLocatorBar)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableLocatorBar = newValue)
                .build());
        playerStatus.addEntry(entryBuilder.startBooleanToggle(translatable("option.mine-lights.enableLowHealthWarning"), MineLightsClient.CONFIG.enableLowHealthWarning)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableLowHealthWarning = newValue)
                .build());
        playerStatus.addEntry(entryBuilder.startBooleanToggle(translatable("option.mine-lights.highlightMovementKeys"), MineLightsClient.CONFIG.highlightMovementKeys)
                .setDefaultValue(true)
                .setTooltip(translatable("option.mine-lights.highlightMovementKeys.tooltip"))
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.highlightMovementKeys = newValue)
                .build());
        playerStatus.addEntry(entryBuilder.startBooleanToggle(translatable("option.mine-lights.pulseChatKey"), MineLightsClient.CONFIG.pulseChatKey)
                .setDefaultValue(true)
                .setTooltip(translatable("option.mine-lights.pulseChatKey.tooltip"))
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.pulseChatKey = newValue)
                .build());
        playerStatus.addEntry(entryBuilder.startBooleanToggle(translatable("option.mine-lights.enableCompassEffect"), MineLightsClient.CONFIG.enableCompassEffect)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableCompassEffect = newValue)
                .build());
        playerStatus.addEntry(entryBuilder.startBooleanToggle(translatable("option.mine-lights.alwaysShowCompass"), MineLightsClient.CONFIG.alwaysShowCompass)
                .setDefaultValue(false)
                .setTooltip(translatable("option.mine-lights.alwaysShowCompass.tooltip"))
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.alwaysShowCompass = newValue)
                .build());
        playerStatus.addEntry(entryBuilder.startEnumSelector(translatable("option.mine-lights.compassPriority"),
                        CompassPriority.class,
                        MineLightsClient.CONFIG.compassPriority != null ? MineLightsClient.CONFIG.compassPriority : CompassPriority.PRIORITY)
                .setDefaultValue(CompassPriority.PRIORITY)
                .setEnumNameProvider(value -> translatable("enum.mine-lights.compassPriority." + value.toString().toLowerCase()))
                .setTooltip(translatable("option.mine-lights.compassPriority.tooltip"))
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.compassPriority = newValue)
                .build());

        ConfigCategory environment = builder.getOrCreateCategory(translatable("category.mine-lights.environment"));
        environment.addEntry(entryBuilder.startBooleanToggle(translatable("option.mine-lights.enableBiomeEffects"), MineLightsClient.CONFIG.enableBiomeEffects)
                .setDefaultValue(true)
                .setTooltip(translatable("option.mine-lights.enableBiomeEffects.tooltip"))
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableBiomeEffects = newValue)
                .build());
        environment.addEntry(entryBuilder.startEnumSelector(translatable("option.mine-lights.dimmingMode"),
                        DimmingMode.class,
                        MineLightsClient.CONFIG.dimmingMode != null ? MineLightsClient.CONFIG.dimmingMode : DimmingMode.LOCAL_LIGHT)
                .setDefaultValue(DimmingMode.NONE)
                .setEnumNameProvider(value -> translatable("enum.mine-lights.dimmingMode." + value.toString().toLowerCase()))
                .setTooltip(translatable("option.mine-lights.dimmingMode.tooltip"))
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.dimmingMode = newValue)
                .build());
        environment.addEntry(entryBuilder.startFloatField(translatable("option.mine-lights.minBrightness"), MineLightsClient.CONFIG.minBrightness)
                .setDefaultValue(0.2F)
                .setTooltip(translatable("option.mine-lights.minBrightness.tooltip"))
                .setMin(0.0F)
                .setMax(1.0F)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.minBrightness = newValue)
                .build());
        environment.addEntry(entryBuilder.startBooleanToggle(translatable("option.mine-lights.enableWeatherEffects"), MineLightsClient.CONFIG.enableWeatherEffects)
                .setDefaultValue(true)
                .setTooltip(translatable("option.mine-lights.enableWeatherEffects.tooltip"))
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableWeatherEffects = newValue)
                .build());
        environment.addEntry(entryBuilder.startBooleanToggle(translatable("option.mine-lights.enableEndFlashEffect"), MineLightsClient.CONFIG.enableEndFlashEffect)
                .setDefaultValue(true)
                .setTooltip(translatable("option.mine-lights.enableEndFlashEffect.tooltip"))
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableEndFlashEffect = newValue)
                .build());
        environment.addEntry(entryBuilder.startBooleanToggle(translatable("option.mine-lights.enableOnFireEffect"), MineLightsClient.CONFIG.enableOnFireEffect)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableOnFireEffect = newValue)
                .build());
        environment.addEntry(entryBuilder.startBooleanToggle(translatable("option.mine-lights.enableInWaterEffect"), MineLightsClient.CONFIG.enableInWaterEffect)
                .setDefaultValue(true)
                .setTooltip(translatable("option.mine-lights.enableInWaterEffect.tooltip"))
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableInWaterEffect = newValue)
                .build());
        environment.addEntry(entryBuilder.startBooleanToggle(translatable("option.mine-lights.enablePortalEffects"), MineLightsClient.CONFIG.enablePortalEffects)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enablePortalEffects = newValue)
                .build());

        ConfigCategory about = builder.getOrCreateCategory(translatable("category.mine-lights.about"));
        about.addEntry(entryBuilder.startTextDescription(translatable("text.mine-lights.about.title")).build());
        about.addEntry(entryBuilder.startTextDescription(translatable("text.mine-lights.about.version", MineLightsClient.getModVersion())).build());
        about.addEntry(entryBuilder.startTextDescription(translatable("text.mine-lights.about.copyright")).build());
        about.addEntry(entryBuilder.startTextField(translatable("text.mine-lights.about.source_code"), "https://github.com/megabytesme/MineLights").build());
        about.addEntry(entryBuilder.startTextField(translatable("text.mine-lights.about.issues"), "https://github.com/megabytesme/MineLights/issues").build());
        about.addEntry(entryBuilder.startTextDescription(translatable("text.mine-lights.about.support_intro")).build());
        about.addEntry(entryBuilder.startTextField(translatable("text.mine-lights.about.kofi"), "https://ko-fi.com/megabytesme").build());
        about.addEntry(entryBuilder.startTextDescription(translatable("text.mine-lights.about.description")).build());

        return builder.build();
    }

    private static void waitForDeviceRefresh() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

        while (System.nanoTime() < deadline) {
            if (!MineLightsClient.discoveredDevices.isEmpty()) {
                return;
            }

            Thread.sleep(100);
        }
    }
}
