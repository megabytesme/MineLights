package megabytesme.minelights.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import megabytesme.minelights.MineLightsClient;
import megabytesme.minelights.network.EffectSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
        LOGGER.info("Opening NeoForge config screen for MineLights.");

        try {
            return buildConfigScreen(parent);
        } catch (Throwable throwable) {
            LOGGER.error("Failed to build NeoForge config screen. Falling back to an error screen.", throwable);
            String message = throwable.getMessage() == null ? "No additional details were provided." : throwable.getMessage();
            return new AlertScreen(
                    //? if >=26.2 {
                    () -> Minecraft.getInstance().setScreenAndShow(parent),
                    //?} else {
                    /* () -> Minecraft.getInstance().setScreen(parent),
                    *///?}
                    Component.literal("MineLights Config Error"),
                    Component.literal("MineLights could not open its config screen: " + message)
            );
        }
    }

    private static MutableComponent translatable(String key, Object... args) {
        return Component.translatable(key, args);
    }

    private static MutableComponent literal(String text) {
        return Component.literal(text);
    }

    private static MutableComponent gray(MutableComponent component) {
        return component.copy().withStyle(ChatFormatting.GRAY);
    }

    private static Screen buildConfigScreen(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(translatable("title.mine-lights.config"));

        builder.setSavingRunnable(() -> {
            MineLightsClient.saveConfig();
            boolean needsRefresh = false;

            if (MineLightsClient.CONFIG.requestServerUpdateCheck) {
                new Thread(MineLightsClient::checkForServerUpdate, "MineLights-Manual-Update-Check").start();
                MineLightsClient.CONFIG.requestServerUpdateCheck = false;
            }
            if (MineLightsClient.CONFIG.refreshDevices) {
                MineLightsClient.CONFIG.refreshDevices = false;
                needsRefresh = true;
            }
            MineLightsClient.CONFIG.restartProxy = false;
            MineLightsClient.CONFIG.restartProxyAsAdmin = false;
            if (MineLightsClient.CONFIG.clearDisabledDevices) {
                MineLightsClient.CONFIG.disabledDevices.clear();
                MineLightsClient.CONFIG.enabledLegacyDevices.clear();
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
                }, "MineLights-NeoForge-Config-Refresh").start();
            }
        });

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        ConfigCategory effectSettings = builder.getOrCreateCategory(translatable("category.mine-lights.key_layout"));
        effectSettings.addEntry(entryBuilder.startTextDescription(translatable("option.mine-lights.effectConfigHelp")).build());
        MineLightsConfig effectDefaults = new MineLightsConfig();
        for (String name : EffectSettings.NAMES) {
            if (!EffectSettings.isAvailable(name)) continue;
            effectSettings.addEntry(entryBuilder.startStrField(translatable(EffectSettings.labelKey(name)),
                            EffectSettings.get(MineLightsClient.CONFIG, name))
                    .setDefaultValue(EffectSettings.get(effectDefaults, name))
                    .setErrorSupplier(value -> {
                        try { EffectSettings.set(new MineLightsConfig(), name, value); return java.util.Optional.empty(); }
                        catch (IllegalArgumentException exception) { return java.util.Optional.of(literal(exception.getMessage())); }
                    })
                    .setSaveConsumer(value -> {
                        boolean changed = !EffectSettings.get(MineLightsClient.CONFIG, name).equals(value);
                        EffectSettings.set(MineLightsClient.CONFIG, name, value);
                        if (changed && name.equals("led-overrides")) MineLightsClient.CONFIG.refreshDevices = true;
                    }).build());
        }
        effectSettings.addEntry(entryBuilder.startBooleanToggle(translatable("option.mine-lights.enableAbsorptionOverlay"),
                        MineLightsClient.CONFIG.enableAbsorptionOverlay)
                .setDefaultValue(effectDefaults.enableAbsorptionOverlay)
                .setSaveConsumer(value -> MineLightsClient.CONFIG.enableAbsorptionOverlay = value).build());
        effectSettings.addEntry(entryBuilder.startBooleanToggle(translatable("option.mine-lights.mergeSaturationIntoHunger"),
                        MineLightsClient.CONFIG.mergeSaturationIntoHunger)
                .setDefaultValue(effectDefaults.mergeSaturationIntoHunger)
                .setSaveConsumer(value -> MineLightsClient.CONFIG.mergeSaturationIntoHunger = value).build());
        effectSettings.addEntry(entryBuilder.startBooleanToggle(translatable("option.mine-lights.restoreLightingOutsideWorld"),
                        MineLightsClient.CONFIG.restoreLightingOutsideWorld)
                .setDefaultValue(effectDefaults.restoreLightingOutsideWorld)
                .setSaveConsumer(value -> MineLightsClient.CONFIG.restoreLightingOutsideWorld = value).build());


        if (MineLightsClient.IS_WINDOWS) {
            ConfigCategory serverManagement = builder.getOrCreateCategory(translatable("category.mine-lights.server_management"));
            Supplier<Component> statusTextSupplier = () -> {
                return MineLightsClient.isServerRunning()
                        ? translatable("minelights.status.running")
                        : translatable("minelights.status.not_running");
            };

            //? if <26.1 {
            serverManagement.addEntry(new LiveStatusEntry("minelights.status", statusTextSupplier));
            //?} else {
            /*serverManagement.addEntry(entryBuilder.startTextDescription(statusTextSupplier.get()).build());
            serverManagement.addEntry(entryBuilder.startTextDescription(
                    translatable("text.mine-lights.about.description")).build());
            *///?}
            serverManagement.addEntry(entryBuilder
                    .startBooleanToggle(translatable("option.mine-lights.force_update.label"), MineLightsClient.CONFIG.requestServerUpdateCheck)
                    .setDefaultValue(false)
                    .setTooltip(translatable("option.mine-lights.force_update.tooltip"))
                    .setSaveConsumer(newValue -> MineLightsClient.CONFIG.requestServerUpdateCheck = newValue)
                    .build());
            if (MineLightsClient.IS_WINDOWS) {
                serverManagement.addEntry(entryBuilder
                        .startBooleanToggle(translatable("option.mine-lights.manualServerManagement"), MineLightsClient.CONFIG.manualServerManagement)
                        .setDefaultValue(false)
                        .setTooltip(translatable("option.mine-lights.manualServerManagement.tooltip"))
                        .setSaveConsumer(newValue -> MineLightsClient.CONFIG.manualServerManagement = newValue)
                        .build());
            }
            serverManagement.addEntry(entryBuilder
                    .startBooleanToggle(translatable("option.mine-lights.autoStartServer"), MineLightsClient.CONFIG.autoStartServer)
                    .setDefaultValue(true)
                    .setTooltip(translatable("option.mine-lights.autoStartServer.tooltip"))
                    .setSaveConsumer(newValue -> MineLightsClient.CONFIG.autoStartServer = newValue)
                    .build());
            serverManagement.addEntry(entryBuilder
                    .startBooleanToggle(translatable("option.mine-lights.notifyMissingServer"), MineLightsClient.CONFIG.notifyMissingServer)
                    .setDefaultValue(true)
                    .setTooltip(translatable("option.mine-lights.notifyMissingServer.tooltip"))
                    .setSaveConsumer(newValue -> MineLightsClient.CONFIG.notifyMissingServer = newValue)
                    .build());
            serverManagement.addEntry(entryBuilder
                    .startBooleanToggle(translatable("option.mine-lights.checkServerUpdates"), MineLightsClient.CONFIG.checkServerUpdates)
                    .setDefaultValue(true)
                    .setTooltip(translatable("option.mine-lights.checkServerUpdates.tooltip"))
                    .setSaveConsumer(newValue -> MineLightsClient.CONFIG.checkServerUpdates = newValue)
                    .build());
            serverManagement.addEntry(entryBuilder
                    .startBooleanToggle(translatable("option.mine-lights.notifyServerDownloadProgress"), MineLightsClient.CONFIG.notifyServerDownloadProgress)
                    .setDefaultValue(true)
                    .setTooltip(translatable("option.mine-lights.notifyServerDownloadProgress.tooltip"))
                    .setSaveConsumer(newValue -> MineLightsClient.CONFIG.notifyServerDownloadProgress = newValue)
                    .build());
            serverManagement.addEntry(entryBuilder.startTextDescription(literal("")).build());
            serverManagement.addEntry(entryBuilder.startTextDescription(
                    translatable("text.mine-lights.server.restart_from_tray")).build());
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
            boolean isEnabled = MineLightsClient.isDeviceEnabled(uniqueId);
            String[] parts = uniqueId.split("\\|", 2);
            String deviceSdk = parts.length > 1 ? parts[0] : "Unknown";
            String deviceName = MineLightsClient.getDiscoveredDeviceDisplayName(uniqueId);
            Component label = literal(deviceName).append(gray(literal(" (" + deviceSdk + ")")));

            devices.addEntry(entryBuilder
                    .startBooleanToggle(label, isEnabled)
                    .setDefaultValue(true)
                    .setSaveConsumer(newValue -> MineLightsClient.setDeviceEnabled(uniqueId, newValue))
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
        playerStatus.addEntry(entryBuilder.startBooleanToggle(translatable("option.mine-lights.enableHealthBar.configured", String.join(" ", MineLightsClient.CONFIG.healthBarKeys)), MineLightsClient.CONFIG.enableHealthBar)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableHealthBar = newValue)
                .build());
        playerStatus.addEntry(entryBuilder.startBooleanToggle(translatable("option.mine-lights.enableHungerBar.configured", String.join(" ", MineLightsClient.CONFIG.hungerBarKeys)), MineLightsClient.CONFIG.enableHungerBar)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableHungerBar = newValue)
                .build());
        playerStatus.addEntry(entryBuilder.startBooleanToggle(translatable("option.mine-lights.enableSaturationBar.configured", String.join(" ", MineLightsClient.CONFIG.saturationBarKeys)), MineLightsClient.CONFIG.enableSaturationBar)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableSaturationBar = newValue)
                .build());
        playerStatus.addEntry(entryBuilder.startBooleanToggle(translatable("option.mine-lights.enableExperienceBar.configured", String.join(" ", MineLightsClient.CONFIG.experienceBarKeys)), MineLightsClient.CONFIG.enableExperienceBar)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableExperienceBar = newValue)
                .build());
        //? if >=1.21.6 {
        playerStatus.addEntry(entryBuilder.startBooleanToggle(translatable("option.mine-lights.enableLocatorBar.configured", String.join(" ", MineLightsClient.CONFIG.locatorBarKeys)), MineLightsClient.CONFIG.enableLocatorBar)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableLocatorBar = newValue)
                .build());
        //?}
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
        playerStatus.addEntry(entryBuilder.startBooleanToggle(translatable("option.mine-lights.enableCompassEffect.configured", String.join(" ", MineLightsClient.CONFIG.compassKeys)), MineLightsClient.CONFIG.enableCompassEffect)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableCompassEffect = newValue)
                .build());
        playerStatus.addEntry(entryBuilder.startBooleanToggle(translatable("option.mine-lights.alwaysShowCompass"), MineLightsClient.CONFIG.alwaysShowCompass)
                .setDefaultValue(false)
                .setTooltip(translatable("option.mine-lights.alwaysShowCompass.tooltip"))
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.alwaysShowCompass = newValue)
                .build());
        playerStatus.addEntry(entryBuilder.startEnumSelector(translatable("option.mine-lights.compassFallbackTarget"),
                        CompassFallbackTarget.class,
                        MineLightsClient.CONFIG.compassFallbackTarget != null ? MineLightsClient.CONFIG.compassFallbackTarget : CompassFallbackTarget.WORLD_SPAWN)
                .setDefaultValue(CompassFallbackTarget.WORLD_SPAWN)
                .setEnumNameProvider(value -> translatable("enum.mine-lights.compassFallbackTarget." + value.toString().toLowerCase()))
                .setTooltip(translatable("option.mine-lights.compassFallbackTarget.tooltip"))
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.compassFallbackTarget = newValue)
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
        environment.addEntry(entryBuilder.startBooleanToggle(translatable("option.mine-lights.mirrorRamRainDirection"), MineLightsClient.CONFIG.mirrorRamRainDirection)
                .setDefaultValue(false)
                .setTooltip(translatable("option.mine-lights.mirrorRamRainDirection.tooltip"))
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.mirrorRamRainDirection = newValue)
                .build());
        //? if >=1.21.9 {
        environment.addEntry(entryBuilder.startBooleanToggle(translatable("option.mine-lights.enableEndFlashEffect"), MineLightsClient.CONFIG.enableEndFlashEffect)
                .setDefaultValue(true)
                .setTooltip(translatable("option.mine-lights.enableEndFlashEffect.tooltip"))
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableEndFlashEffect = newValue)
                .build());
        //?}
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
        about.addEntry(entryBuilder.startTextDescription(Component.translatable("text.mine-lights.about.title")).build());
        about.addEntry(entryBuilder.startTextDescription(Component.translatable("text.mine-lights.about.version", MineLightsClient.getModVersion())).build());
        about.addEntry(entryBuilder.startTextDescription(Component.translatable("text.mine-lights.about.copyright")).build());
        about.addEntry(entryBuilder.startTextField(Component.translatable("text.mine-lights.about.source_code"), "https://github.com/megabytesme/MineLights").build());
        about.addEntry(entryBuilder.startTextField(Component.translatable("text.mine-lights.about.issues"), "https://github.com/megabytesme/MineLights/issues").build());
        about.addEntry(entryBuilder.startTextDescription(Component.translatable("text.mine-lights.about.support_intro")).build());
        about.addEntry(entryBuilder.startTextField(Component.translatable("text.mine-lights.about.kofi"), "https://ko-fi.com/megabytesme").build());
        about.addEntry(entryBuilder.startTextDescription(Component.translatable("text.mine-lights.about.description")).build());

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
