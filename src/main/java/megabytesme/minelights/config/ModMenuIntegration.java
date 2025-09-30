package megabytesme.minelights.config;

//? if <1.17 {
/*
import io.github.prospector.modmenu.api.ModMenuApi;
*/
//?}
//? if >=1.17 {
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
//?}

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import megabytesme.minelights.CommandClient;
import megabytesme.minelights.MineLightsClient;
import megabytesme.minelights.config.LiveLogEntry;
import megabytesme.minelights.config.LiveStatusEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;

import java.util.Comparator;
import java.util.function.Function;
import java.util.function.Supplier;

public class ModMenuIntegration implements ModMenuApi {
    //? if <1.17 {
    /*
    @Override
    public String getModId() {
        return "mine-lights";
    }

    @Override
    public Function<Screen, Screen> getConfigScreenFactory() {
        return this::buildConfigScreen;
    }
    */
    //?}

    //? if >=1.17 {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return this::buildConfigScreen;
    }
    //?}

    private Screen buildConfigScreen(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(new TranslatableText("title.mine-lights.config")
                    //? if <1.16 {
                    /* .getString()
                    *///? }
                );

        builder.setSavingRunnable(() -> {
            MineLightsClient.saveConfig();
            boolean needsRefresh = false;

            if (MineLightsClient.CONFIG.forceServerUpdate) {
                new Thread(MineLightsClient::checkForServerUpdate,
                        "MineLights-Manual-Update-Check").start();
                MineLightsClient.CONFIG.forceServerUpdate = false;
            }
            if (MineLightsClient.CONFIG.refreshDevices) {
                MineLightsClient.CONFIG.refreshDevices = false;
                needsRefresh = true;
            }
            if (MineLightsClient.CONFIG.restartProxy) {
                CommandClient.sendCommand("restart");
                MineLightsClient.CONFIG.restartProxy = false;
                needsRefresh = true;
            }
            if (MineLightsClient.CONFIG.restartProxyAsAdmin) {
                CommandClient.sendCommand("restart_admin");
                MineLightsClient.CONFIG.restartProxyAsAdmin = false;
                needsRefresh = true;
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
                } catch (InterruptedException ignored) { }

                MineLightsClient.refreshLightingManager();

                new Thread(() -> {
                    try {
                        Thread.sleep(1000);
                        MinecraftClient.getInstance().execute(() -> {
                            MinecraftClient.getInstance().openScreen(buildConfigScreen(parent));
                        });
                    } catch (InterruptedException ignored) { }
                }).start();
            }
        });

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        if (MineLightsClient.IS_WINDOWS) {
            ConfigCategory serverManagement = builder.getOrCreateCategory(
                    new TranslatableText("category.mine-lights.server_management")
                    //? if <1.16 {
                    /* .getString()
                    *///? }
            );

            Supplier<Text> statusTextSupplier = () -> {
                MineLightsClient.DownloadStatus status = MineLightsClient.downloadStatus.get();
                switch (status) {
                    case DOWNLOADING:
                        long soFarMB = MineLightsClient.downloadBytesSoFar.get() / (1024 * 1024);
                        long totalMB = MineLightsClient.downloadTotalBytes.get() / (1024 * 1024);
                        String eta = MineLightsClient.downloadEta.get();
                        String speed = MineLightsClient.downloadSpeedMBps.get();
                        return new TranslatableText(
                                "minelights.status.downloading",
                                soFarMB,
                                totalMB,
                                MineLightsClient.downloadProgress.get(),
                                eta,
                                speed
                        );
                    case VERIFYING:
                        return new TranslatableText("minelights.status.verifying");
                    case SUCCESS:
                        return new TranslatableText("minelights.status.success");
                    case FAILED:
                        return new TranslatableText("minelights.status.failed",
                                MineLightsClient.downloadError.get());
                    case IDLE:
                    default:
                        return MineLightsClient.isServerRunning()
                                ? new TranslatableText("minelights.status.running")
                                : new TranslatableText("minelights.status.not_running");
                }
            };

            serverManagement.addEntry(new LiveStatusEntry("minelights.status", statusTextSupplier));

            serverManagement.addEntry(entryBuilder
                    .startBooleanToggle(
                            new TranslatableText("option.mine-lights.force_update.label")
                            //? if <1.16 {
                            /* .getString()
                            *///? }
                            ,
                            MineLightsClient.CONFIG.forceServerUpdate
                    )
                    .setDefaultValue(false)
                    //? if >=1.16 {
                    .setTooltip(new TranslatableText("option.mine-lights.force_update.tooltip"))
                    //?}
                    //? if <1.16 {
                    /* .setTooltip(new TranslatableText("option.mine-lights.force_update.tooltip").getString()) */
                    //?}
                    .setSaveConsumer(newValue -> MineLightsClient.CONFIG.forceServerUpdate = newValue)
                    .build()
            );

            if (MineLightsClient.IS_WINDOWS) {
                serverManagement.addEntry(entryBuilder
                        .startBooleanToggle(
                                new TranslatableText("option.mine-lights.autoStartServer")
                                //? if <1.16 {
                                /* .getString()
                                *///? }
                                ,
                                MineLightsClient.CONFIG.autoStartServer)
                        .setDefaultValue(true)
                        //? if >=1.16 {
                        .setTooltip(new TranslatableText("option.mine-lights.autoStartServer.tooltip"))
                        //?}
                        //? if <1.16 {
                        /* .setTooltip(new TranslatableText("option.mine-lights.autoStartServer.tooltip").getString()) */
                        //?}
                        .setSaveConsumer(
                                newValue -> MineLightsClient.CONFIG.autoStartServer = newValue)
                        .build());
            }

            serverManagement.addEntry(entryBuilder.startTextDescription(
                    new LiteralText("")
                    //? if <1.16 {
                    /* .getString()
                    *///? }
            ).build());
            serverManagement.addEntry(entryBuilder
                    .startBooleanToggle(
                            new TranslatableText("option.mine-lights.restart.label")
                            //? if <1.16 {
                            /* .getString()
                            *///? }
                            ,
                            MineLightsClient.CONFIG.restartProxy)
                    .setDefaultValue(false)
                    //? if >=1.16 {
                    .setTooltip(new TranslatableText("option.mine-lights.restart.tooltip"))
                    //?}
                    //? if <1.16 {
                    /* .setTooltip(new TranslatableText("option.mine-lights.restart.tooltip").getString()) */
                    //?}
                    .setSaveConsumer(newValue -> MineLightsClient.CONFIG.restartProxy = newValue)
                    .build());
            serverManagement.addEntry(entryBuilder
                    .startBooleanToggle(
                            new TranslatableText("option.mine-lights.restart_admin.label")
                            //? if <1.16 {
                            /* .getString()
                            *///? }
                            ,
                            MineLightsClient.CONFIG.restartProxyAsAdmin)
                    .setDefaultValue(false)
                    //? if >=1.16 {
                    .setTooltip(new TranslatableText("option.mine-lights.restart_admin.tooltip"))
                    //?}
                    //? if <1.16 {
                    /* .setTooltip(new TranslatableText("option.mine-lights.restart_admin.tooltip").getString()) */
                    //?}
                    .setSaveConsumer(
                            newValue -> MineLightsClient.CONFIG.restartProxyAsAdmin = newValue)
                    .build());
            
            serverManagement.addEntry(new LiveLogEntry("Server Log", MineLightsClient.serverLogLines));
        }
        
        ConfigCategory general = builder.getOrCreateCategory(
                new TranslatableText("category.mine-lights.general")
                //? if <1.16 {
                /* .getString()
                *///? }
        );

        general.addEntry(entryBuilder
                .startBooleanToggle(
                        new TranslatableText("option.mine-lights.enableMod")
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.enableMod)
                .setDefaultValue(true)
                //? if >=1.16 {
                .setTooltip(new TranslatableText("option.mine-lights.enableMod.tooltip"))
                //?}
                //? if <1.16 {
                /* .setTooltip(new TranslatableText("option.mine-lights.enableMod.tooltip").getString()) */
                //?}
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableMod = newValue)
                .build());

        general.addEntry(entryBuilder
                .startBooleanToggle(
                        new TranslatableText("option.mine-lights.refresh_devices.label")
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.refreshDevices)
                .setDefaultValue(false)
                //? if >=1.16 {
                .setTooltip(new TranslatableText("option.mine-lights.refresh_devices.tooltip"))
                //?}
                //? if <1.16 {
                /* .setTooltip(new TranslatableText("option.mine-lights.refresh_devices.tooltip").getString()) */
                //?}
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.refreshDevices = newValue)
                .build());

        ConfigCategory integrations = builder.getOrCreateCategory(
                new TranslatableText("category.mine-lights.integrations")
                //? if <1.16 {
                /* .getString()
                *///? }
        );

        integrations.addEntry(entryBuilder
                .startBooleanToggle(
                        new TranslatableText("integration.mine-lights.corsair")
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.enableCorsair)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableCorsair = newValue)
                .build());
        integrations.addEntry(entryBuilder
                .startBooleanToggle(
                        new TranslatableText("integration.mine-lights.asus")
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.enableAsus)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableAsus = newValue)
                .build());
        integrations.addEntry(entryBuilder
                .startBooleanToggle(
                        new TranslatableText("integration.mine-lights.logitech")
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.enableLogitech)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableLogitech = newValue)
                .build());
        integrations.addEntry(entryBuilder
                .startBooleanToggle(
                        new TranslatableText("integration.mine-lights.razer")
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.enableRazer)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableRazer = newValue)
                .build());
        integrations.addEntry(entryBuilder
                .startBooleanToggle(
                        new TranslatableText("integration.mine-lights.wooting")
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.enableWooting)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableWooting = newValue)
                .build());
        integrations.addEntry(entryBuilder
                .startBooleanToggle(
                        new TranslatableText("integration.mine-lights.steelseries")
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.enableSteelSeries)
                .setDefaultValue(true)
                .setSaveConsumer(
                        newValue -> MineLightsClient.CONFIG.enableSteelSeries = newValue)
                .build());
        integrations.addEntry(entryBuilder
                .startBooleanToggle(
                        new TranslatableText("integration.mine-lights.msi")
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.enableMsi)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableMsi = newValue)
                .build());
        integrations.addEntry(entryBuilder
                .startBooleanToggle(
                        new TranslatableText("integration.mine-lights.novation")
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.enableNovation)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableNovation = newValue)
                .build());
        integrations.addEntry(entryBuilder
                .startBooleanToggle(
                        new TranslatableText("integration.mine-lights.picopi")
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.enablePicoPi)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enablePicoPi = newValue)
                .build());
        integrations.addEntry(entryBuilder
                .startBooleanToggle(
                        new TranslatableText("integration.mine-lights.openrgb")
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.enableOpenRgb)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableOpenRgb = newValue)
                .build());
        integrations.addEntry(entryBuilder
                .startBooleanToggle(
                        new TranslatableText("integration.mine-lights.yeelight")
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.enableYeelight)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableYeelight = newValue)
                .build());

        ConfigCategory devices = builder.getOrCreateCategory(
                new TranslatableText("category.mine-lights.devices")
                //? if <1.16 {
                /* .getString()
                *///? }
        );
        devices.addEntry(entryBuilder.startTextDescription(
                new TranslatableText("option.mine-lights.device.header")
                //? if <1.16 {
                /* .getString()
                *///? }
        ).build());

        MineLightsClient.discoveredDevices.stream().sorted(Comparator.naturalOrder())
                .forEach(uniqueId -> {
                    boolean isEnabled = !MineLightsClient.CONFIG.disabledDevices
                            .contains(uniqueId);
                    String[] parts = uniqueId.split("\\|", 2);
                    String deviceSdk = parts.length > 1 ? parts[0] : "Unknown";
                    String deviceName = parts.length > 1 ? parts[1] : uniqueId;
                    Text label = new LiteralText(deviceName)
                            .append(new LiteralText(" (" + deviceSdk + ")")
                                    .formatted(Formatting.GRAY));
                    devices.addEntry(entryBuilder
                            .startBooleanToggle(label
                            //? if <1.16 {
                            /* .getString()
                            *///? }
                            , isEnabled)
                            .setDefaultValue(true)
                            .setSaveConsumer(newValue -> {
                                if (newValue) {
                                    MineLightsClient.CONFIG.disabledDevices
                                            .remove(uniqueId);
                                } else {
                                    if (!MineLightsClient.CONFIG.disabledDevices
                                            .contains(uniqueId)) {
                                        MineLightsClient.CONFIG.disabledDevices
                                                .add(uniqueId);
                                    }
                                }
                            }).build());
                });

        if (!MineLightsClient.CONFIG.disabledDevices.isEmpty()) {
            devices.addEntry(entryBuilder.startTextDescription(
                    new LiteralText("")
                    //? if <1.16 {
                    /* .getString()
                    *///? }
            ).build());

            devices.addEntry(entryBuilder
                    .startBooleanToggle(new TranslatableText(
                            "option.mine-lights.clear_disabled.label")
                            //? if <1.16 {
                            /* .getString()
                            *///? }
                            ,
                            MineLightsClient.CONFIG.clearDisabledDevices)
                    .setDefaultValue(false)
                    //? if >=1.16 {
                    .setTooltip(new TranslatableText("option.mine-lights.clear_disabled.tooltip"))
                    //?}
                    //? if <1.16 {
                    /* .setTooltip(new TranslatableText("option.mine-lights.clear_disabled.tooltip").getString()) */
                    //?}
                    .setSaveConsumer(
                            newValue -> MineLightsClient.CONFIG.clearDisabledDevices = newValue)
                    .build());
        }

        ConfigCategory playerStatus = builder.getOrCreateCategory(
                new TranslatableText("category.mine-lights.player_status")
                //? if <1.16 {
                /* .getString()
                *///? }
        );

        playerStatus.addEntry(entryBuilder
                .startBooleanToggle(
                        new TranslatableText("option.mine-lights.enableHealthBar")
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.enableHealthBar)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableHealthBar = newValue)
                .build());
        playerStatus.addEntry(entryBuilder
                .startBooleanToggle(
                        new TranslatableText("option.mine-lights.enableHungerBar")
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.enableHungerBar)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableHungerBar = newValue)
                .build());
        playerStatus.addEntry(entryBuilder
                .startBooleanToggle(
                        new TranslatableText("option.mine-lights.enableSaturationBar")
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.enableSaturationBar)
                .setDefaultValue(true)
                //? if >=1.16 {
                .setTooltip(new TranslatableText("option.mine-lights.enableSaturationBar.tooltip"))
                //?}
                //? if <1.16 {
                /* .setTooltip(new TranslatableText("option.mine-lights.enableSaturationBar.tooltip").getString()) */
                //?}
                .setSaveConsumer(
                        newValue -> MineLightsClient.CONFIG.enableSaturationBar = newValue)
                .build());
        playerStatus.addEntry(entryBuilder
                .startBooleanToggle(
                        new TranslatableText("option.mine-lights.enableExperienceBar")
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.enableExperienceBar)
                .setDefaultValue(true)
                //? if >=1.16 {
                .setTooltip(new TranslatableText("option.mine-lights.enableExperienceBar.tooltip"))
                //?}
                //? if <1.16 {
                /* .setTooltip(new TranslatableText("option.mine-lights.enableExperienceBar.tooltip").getString()) */
                //?}
                .setSaveConsumer(
                        newValue -> MineLightsClient.CONFIG.enableExperienceBar = newValue)
                .build());
        playerStatus.addEntry(entryBuilder
                .startBooleanToggle(
                        new TranslatableText("option.mine-lights.enableCompassEffect")
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.enableCompassEffect)
                .setDefaultValue(true)
                .setSaveConsumer(
                        newValue -> MineLightsClient.CONFIG.enableCompassEffect = newValue)
                .build());
        playerStatus.addEntry(entryBuilder
                .startBooleanToggle(
                        new TranslatableText("option.mine-lights.alwaysShowCompass")
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.alwaysShowCompass)
                .setDefaultValue(false)
                //? if >=1.16 {
                .setTooltip(new TranslatableText("option.mine-lights.alwaysShowCompass.tooltip"))
                //?}
                //? if <1.16 {
                /* .setTooltip(new TranslatableText("option.mine-lights.alwaysShowCompass.tooltip").getString()) */
                //?}
                .setSaveConsumer(
                        newValue -> MineLightsClient.CONFIG.alwaysShowCompass = newValue)
                .build());
        playerStatus.addEntry(entryBuilder
                .startBooleanToggle(
                        new TranslatableText(
                                "option.mine-lights.enableLowHealthWarning")
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.enableLowHealthWarning)
                .setDefaultValue(true)
                .setSaveConsumer(
                        newValue -> MineLightsClient.CONFIG.enableLowHealthWarning = newValue)
                .build());
        playerStatus.addEntry(entryBuilder
                .startBooleanToggle(
                        new TranslatableText("option.mine-lights.highlightMovementKeys")
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.highlightMovementKeys)
                .setDefaultValue(true)
                //? if >=1.16 {
                .setTooltip(new TranslatableText("option.mine-lights.highlightMovementKeys.tooltip"))
                //?}
                //? if <1.16 {
                /* .setTooltip(new TranslatableText("option.mine-lights.highlightMovementKeys.tooltip").getString()) */
                //?}
                .setSaveConsumer(
                        newValue -> MineLightsClient.CONFIG.highlightMovementKeys = newValue)
                .build());

        ConfigCategory environment = builder.getOrCreateCategory(
                new TranslatableText("category.mine-lights.environment")
                //? if <1.16 {
                /* .getString()
                *///? }
        );

        environment.addEntry(entryBuilder
                .startBooleanToggle(
                        new TranslatableText("option.mine-lights.enableBiomeEffects")
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.enableBiomeEffects)
                .setDefaultValue(true)
                //? if >=1.16 {
                .setTooltip(new TranslatableText("option.mine-lights.enableBiomeEffects.tooltip"))
                //?}
                //? if <1.16 {
                /* .setTooltip(new TranslatableText("option.mine-lights.enableBiomeEffects.tooltip").getString()) */
                //?}
                .setSaveConsumer(
                        newValue -> MineLightsClient.CONFIG.enableBiomeEffects = newValue)
                .build());
        environment.addEntry(entryBuilder
                .startBooleanToggle(
                        new TranslatableText("option.mine-lights.enableWeatherEffects")
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.enableWeatherEffects)
                .setDefaultValue(true)
                //? if >=1.16 {
                .setTooltip(new TranslatableText("option.mine-lights.enableWeatherEffects.tooltip"))
                //?}
                //? if <1.16 {
                /* .setTooltip(new TranslatableText("option.mine-lights.enableWeatherEffects.tooltip").getString()) */
                //?}
                .setSaveConsumer(
                        newValue -> MineLightsClient.CONFIG.enableWeatherEffects = newValue)
                .build());
        environment.addEntry(entryBuilder
                .startBooleanToggle(
                        new TranslatableText("option.mine-lights.enableOnFireEffect")
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.enableOnFireEffect)
                .setDefaultValue(true)
                .setSaveConsumer(
                        newValue -> MineLightsClient.CONFIG.enableOnFireEffect = newValue)
                .build());
        environment.addEntry(entryBuilder
                .startBooleanToggle(
                        new TranslatableText("option.mine-lights.enableInWaterEffect")
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.enableInWaterEffect)
                .setDefaultValue(true)
                //? if >=1.16 {
                .setTooltip(new TranslatableText("option.mine-lights.enableInWaterEffect.tooltip"))
                //?}
                //? if <1.16 {
                /* .setTooltip(new TranslatableText("option.mine-lights.enableInWaterEffect.tooltip").getString()) */
                //?}
                .setSaveConsumer(
                        newValue -> MineLightsClient.CONFIG.enableInWaterEffect = newValue)
                .build());
        environment.addEntry(entryBuilder
                .startBooleanToggle(
                        new TranslatableText("option.mine-lights.enablePortalEffects")
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.enablePortalEffects)
                .setDefaultValue(true)
                .setSaveConsumer(
                        newValue -> MineLightsClient.CONFIG.enablePortalEffects = newValue)
                .build());

        ConfigCategory aboutCategory = builder
                .getOrCreateCategory(
                        new TranslatableText("category.mine-lights.about")
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                );

        aboutCategory.addEntry(entryBuilder.startTextDescription(
                new TranslatableText("text.mine-lights.about.title")
                //? if <1.16 {
                /* .getString()
                *///? }
        ).build());

        aboutCategory.addEntry(entryBuilder.startTextDescription(
                new TranslatableText("text.mine-lights.about.version", "2.2.1")
                //? if <1.16 {
                /* .getString()
                *///? }
        ).build());

        aboutCategory.addEntry(entryBuilder.startTextDescription(
                new TranslatableText("text.mine-lights.about.copyright")
                //? if <1.16 {
                /* .getString()
                *///? }
        ).build());

        aboutCategory.addEntry(entryBuilder.startTextDescription(
                new LiteralText("")
                //? if <1.16 {
                /* .getString()
                *///? }
        ).build());

        aboutCategory.addEntry(entryBuilder.startTextField(
                new TranslatableText("text.mine-lights.about.source_code")
                //? if <1.16 {
                /* .getString()
                *///? }
                ,
                "https://github.com/megabytesme/MineLights").build());

        aboutCategory.addEntry(entryBuilder.startTextField(
                new TranslatableText("text.mine-lights.about.issues")
                //? if <1.16 {
                /* .getString()
                *///? }
                ,
                "https://github.com/megabytesme/MineLights/issues").build());

        aboutCategory.addEntry(entryBuilder.startTextDescription(
                new LiteralText("")
                //? if <1.16 {
                /* .getString()
                *///? }
        ).build());

        aboutCategory.addEntry(entryBuilder.startTextDescription(
                new TranslatableText("text.mine-lights.about.support_intro")
                //? if <1.16 {
                /* .getString()
                *///? }
        ).build());

        aboutCategory.addEntry(entryBuilder.startTextField(
                new TranslatableText("text.mine-lights.about.kofi")
                //? if <1.16 {
                /* .getString()
                *///? }
                ,
                "https://ko-fi.com/megabytesme").build());

        aboutCategory.addEntry(entryBuilder.startTextDescription(
                new LiteralText("")
                //? if <1.16 {
                /* .getString()
                *///? }
        ).build());

        aboutCategory.addEntry(entryBuilder.startTextDescription(
                new TranslatableText("text.mine-lights.about.description")
                //? if <1.16 {
                /* .getString()
                *///? }
        ).build());

        return builder.build();
    }
}