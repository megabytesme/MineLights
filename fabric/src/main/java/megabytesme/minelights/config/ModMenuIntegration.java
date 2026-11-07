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
import megabytesme.minelights.MineLightsClient;
import megabytesme.minelights.config.LiveStatusEntry;
//? if >=26.1 {
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
//?} else {
/* import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
*///?}
//? if >=26.1 {
/* */
//?} else {
/* //? if <1.19 {
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
//?}
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
//? if >=1.16 {
import net.minecraft.text.MutableText;
//?}
*///?}

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

    //? if >=26.1 {
    private static MutableComponent translatable(String key, Object... args) {
        return Component.translatable(key, args);
    }

    private static MutableComponent literal(String text) {
        return Component.literal(text);
    }

    private static MutableComponent gray(MutableComponent component) {
        return component.copy().withStyle(ChatFormatting.GRAY);
    }
    //?} else if >=1.19 {
    /* private static MutableText translatable(String key, Object... args) {
        return Text.translatable(key, args);
    }

    private static MutableText literal(String text) {
        return Text.literal(text);
    }

    private static MutableText gray(MutableText text) {
        return text.formatted(Formatting.GRAY);
    }
    *///?} else if >=1.16 {
    /* private static MutableText translatable(String key, Object... args) {
        return new TranslatableText(key, args);
    }

    private static MutableText literal(String text) {
        return new LiteralText(text);
    }

    private static MutableText gray(MutableText text) {
        return text.formatted(Formatting.GRAY);
    }
    *///?}

    //? if >=1.19 && <1.20 {
    private static String stableCategoryName(String key) {
        String translated = translatable(key).getString();
        if (!translated.equals(key)) {
            return translated;
        }
        return switch (key) {
            case "category.mine-lights.server_management" -> "Server Management";
            case "category.mine-lights.general" -> "General";
            case "category.mine-lights.integrations" -> "Integrations";
            case "category.mine-lights.devices" -> "Devices";
            case "category.mine-lights.player_status" -> "Player Status";
            case "category.mine-lights.environment" -> "Environment";
            case "category.mine-lights.about" -> "About";
            case "category.mine-lights.key_layout" -> "Key Layout";
            case "category.mine-lights.colors" -> "Colors";
            default -> key;
        };
    }
    //?}

    private Screen buildConfigScreen(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(
                //? if >=1.19 {
                translatable("title.mine-lights.config")
                //?} else if <1.19 {
                /*new TranslatableText("title.mine-lights.config")
                *///?}
                //? if <1.16 {
                /* .getString()
                *///? }
                );

        builder.setSavingRunnable(() -> {
            MineLightsClient.saveConfig();
            boolean needsRefresh = false;

            if (MineLightsClient.CONFIG.requestServerUpdateCheck) {
                new Thread(MineLightsClient::checkForServerUpdate,
                        "MineLights-Manual-Update-Check").start();
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
                } catch (InterruptedException ignored) { }

                MineLightsClient.refreshLightingManager();

                new Thread(() -> {
                    try {
                        Thread.sleep(1000);
                        //? if >=26.1 {
                        Minecraft.getInstance().execute(() -> {
                        //?} else {
                        /* MinecraftClient.getInstance().execute(() -> {
                        *///?}
                            //? if >=1.19 {
                            //? if >=26.2 {
                            Minecraft.getInstance().setScreenAndShow(buildConfigScreen(parent));
                            //?} else if >=26.1 {
                            /* Minecraft.getInstance().setScreen(buildConfigScreen(parent));
                            *///?} else {
                            /* MinecraftClient.getInstance().setScreen(buildConfigScreen(parent)); */
                            //?}
                            //?}
                            //? if >=1.17.1 && <1.19 {
                            /* MinecraftClient.getInstance().setScreen(buildConfigScreen(parent)); */
                            //?} else if <1.17.1 {
                            /*
                            MinecraftClient.getInstance().openScreen(buildConfigScreen(parent));
                            */
                            //?}
                        });
                    } catch (InterruptedException ignored) { }
                }).start();
            }
        });

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        if (MineLightsClient.IS_WINDOWS) {
            ConfigCategory serverManagement = builder.getOrCreateCategory(
                    //? if >=1.19 && <1.20 {
                    literal(stableCategoryName("category.mine-lights.server_management"))
                    //?} else if >=1.20 {
                    /*translatable("category.mine-lights.server_management")
                    *///?} else if <1.19 {
                    /*new TranslatableText("category.mine-lights.server_management")
                    *///?}
                    //? if <1.16 {
                    /* .getString()
                    *///? }
            );

            //? if >=26.1 {
            Supplier<Component> statusTextSupplier = () -> {
            //?} else {
            /* Supplier<Text> statusTextSupplier = () -> {
            *///?}
                //? if >=1.19 {
                return MineLightsClient.isServerRunning()
                        ? translatable("minelights.status.running")
                        : translatable("minelights.status.not_running");
                //?} else if <1.19 {
                /*return MineLightsClient.isServerRunning()
                        ? new TranslatableText("minelights.status.running")
                        : new TranslatableText("minelights.status.not_running");
                *///?}
            };

            serverManagement.addEntry(new LiveStatusEntry("minelights.status", statusTextSupplier));

            serverManagement.addEntry(entryBuilder
                    .startBooleanToggle(
                            //? if >=1.19 {
                            translatable("option.mine-lights.force_update.label")
                            //?} else if <1.19 {
                            /*new TranslatableText("option.mine-lights.force_update.label")
                            *///?}
                            //? if <1.16 {
                            /* .getString()
                            *///? }
                            ,
                            MineLightsClient.CONFIG.requestServerUpdateCheck
                    )
                    .setDefaultValue(false)
                    //? if >=1.16 {
                    .setTooltip(
                        //? if >=1.19 {
                        translatable("option.mine-lights.force_update.tooltip")
                        //?} else if <1.19 {
                        /*new TranslatableText("option.mine-lights.force_update.tooltip")
                        *///?}
                    )
                    //?}
                    //? if <1.16 {
                    /* .setTooltip(new TranslatableText("option.mine-lights.force_update.tooltip").getString()) */
                    //?}
                    .setSaveConsumer(newValue -> MineLightsClient.CONFIG.requestServerUpdateCheck = newValue)
                    .build()
            );

            if (MineLightsClient.IS_WINDOWS) {
                serverManagement.addEntry(entryBuilder
                        .startBooleanToggle(
                                //? if >=1.19 {
                                translatable("option.mine-lights.manualServerManagement")
                                //?} else if <1.19 {
                                /*new TranslatableText("option.mine-lights.manualServerManagement")
                                *///?}
                                //? if <1.16 {
                                /* .getString()
                                *///? }
                                ,
                                MineLightsClient.CONFIG.manualServerManagement)
                        .setDefaultValue(false)
                        .setTooltip(
                                //? if >=1.19 {
                                translatable("option.mine-lights.manualServerManagement.tooltip")
                                //?} else if <1.19 {
                                /*new TranslatableText("option.mine-lights.manualServerManagement.tooltip")
                                *///?}
                                //? if <1.16 {
                                /* .getString()
                                *///? }
                        )
                        .setSaveConsumer(newValue -> MineLightsClient.CONFIG.manualServerManagement = newValue)
                        .build());
                serverManagement.addEntry(entryBuilder
                        .startBooleanToggle(
                                //? if >=1.19 {
                                translatable("option.mine-lights.autoStartServer")
                                //?} else if <1.19 {
                                /*new TranslatableText("option.mine-lights.autoStartServer")
                                *///?}
                                //? if <1.16 {
                                /* .getString()
                                *///? }
                                ,
                                MineLightsClient.CONFIG.autoStartServer)
                        .setDefaultValue(true)
                        //? if >=1.16 {
                        .setTooltip(
                            //? if >=1.19 {
                            translatable("option.mine-lights.autoStartServer.tooltip")
                            //?} else if <1.19 {
                            /*new TranslatableText("option.mine-lights.autoStartServer.tooltip")
                            *///?}
                        )
                        //?}
                        //? if <1.16 {
                        /* .setTooltip(new TranslatableText("option.mine-lights.autoStartServer.tooltip").getString()) */
                        //?}
                        .setSaveConsumer(
                                newValue -> MineLightsClient.CONFIG.autoStartServer = newValue)
                        .build());
                serverManagement.addEntry(entryBuilder
                        .startBooleanToggle(
                                //? if >=1.19 {
                                translatable("option.mine-lights.notifyMissingServer")
                                //?} else if <1.19 {
                                /*new TranslatableText("option.mine-lights.notifyMissingServer")
                                *///?}
                                //? if <1.16 {
                                /* .getString()
                                *///? }
                                ,
                                MineLightsClient.CONFIG.notifyMissingServer)
                        .setDefaultValue(true)
                        .setTooltip(
                                //? if >=1.19 {
                                translatable("option.mine-lights.notifyMissingServer.tooltip")
                                //?} else if <1.19 {
                                /*new TranslatableText("option.mine-lights.notifyMissingServer.tooltip")
                                *///?}
                                //? if <1.16 {
                                /* .getString()
                                *///? }
                        )
                        .setSaveConsumer(newValue -> MineLightsClient.CONFIG.notifyMissingServer = newValue)
                        .build());
                serverManagement.addEntry(entryBuilder
                        .startBooleanToggle(
                                //? if >=1.19 {
                                translatable("option.mine-lights.checkServerUpdates")
                                //?} else if <1.19 {
                                /*new TranslatableText("option.mine-lights.checkServerUpdates")
                                *///?}
                                //? if <1.16 {
                                /* .getString()
                                *///? }
                                ,
                                MineLightsClient.CONFIG.checkServerUpdates)
                        .setDefaultValue(true)
                        .setTooltip(
                                //? if >=1.19 {
                                translatable("option.mine-lights.checkServerUpdates.tooltip")
                                //?} else if <1.19 {
                                /*new TranslatableText("option.mine-lights.checkServerUpdates.tooltip")
                                *///?}
                                //? if <1.16 {
                                /* .getString()
                                *///? }
                        )
                        .setSaveConsumer(newValue -> MineLightsClient.CONFIG.checkServerUpdates = newValue)
                        .build());
                serverManagement.addEntry(entryBuilder
                        .startBooleanToggle(
                                //? if >=1.19 {
                                translatable("option.mine-lights.notifyServerDownloadProgress")
                                //?} else if <1.19 {
                                /*new TranslatableText("option.mine-lights.notifyServerDownloadProgress")
                                *///?}
                                //? if <1.16 {
                                /* .getString()
                                *///? }
                                ,
                                MineLightsClient.CONFIG.notifyServerDownloadProgress)
                        .setDefaultValue(true)
                        .setTooltip(
                                //? if >=1.19 {
                                translatable("option.mine-lights.notifyServerDownloadProgress.tooltip")
                                //?} else if <1.19 {
                                /*new TranslatableText("option.mine-lights.notifyServerDownloadProgress.tooltip")
                                *///?}
                                //? if <1.16 {
                                /* .getString()
                                *///? }
                        )
                        .setSaveConsumer(newValue -> MineLightsClient.CONFIG.notifyServerDownloadProgress = newValue)
                        .build());
            }

            serverManagement.addEntry(entryBuilder.startTextDescription(
                    //? if >=1.19 {
                    literal("")
                    //?} else if <1.19 {
                    /*new LiteralText("")
                    *///?}
                    //? if <1.16 {
                    /* .getString()
                    *///? }
            ).build());
            serverManagement.addEntry(entryBuilder.startTextDescription(
                    //? if >=1.19 {
                    translatable("text.mine-lights.server.restart_from_tray")
                    //?} else if <1.19 {
                    /*new TranslatableText("text.mine-lights.server.restart_from_tray")
                    *///?}
                    //? if <1.16 {
                    /* .getString()
                    *///? }
            ).build());
            
        }
        
        ConfigCategory general = builder.getOrCreateCategory(
                //? if >=1.19 && <1.20 {
                literal(stableCategoryName("category.mine-lights.general"))
                //?} else if >=1.20 {
                /*translatable("category.mine-lights.general")
                *///?} else if <1.19 {
                /*new TranslatableText("category.mine-lights.general")
                *///?}
                //? if <1.16 {
                /* .getString()
                *///? }
        );

        general.addEntry(entryBuilder
                .startBooleanToggle(
                        //? if >=1.19 {
                        translatable("option.mine-lights.enableMod")
                        //?} else if <1.19 {
                        /*new TranslatableText("option.mine-lights.enableMod")
                        *///?}
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.enableMod)
                .setDefaultValue(true)
                //? if >=1.16 {
                .setTooltip(
                    //? if >=1.19 {
                    translatable("option.mine-lights.enableMod.tooltip")
                    //?} else if <1.19 {
                    /*new TranslatableText("option.mine-lights.enableMod.tooltip")
                    *///?}
                )
                //?}
                //? if <1.16 {
                /* .setTooltip(new TranslatableText("option.mine-lights.enableMod.tooltip").getString()) */
                //?}
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableMod = newValue)
                .build());

        general.addEntry(entryBuilder
                .startBooleanToggle(
                        //? if >=1.19 {
                        translatable("option.mine-lights.refresh_devices.label")
                        //?} else if <1.19 {
                        /*new TranslatableText("option.mine-lights.refresh_devices.label")
                        *///?}
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.refreshDevices)
                .setDefaultValue(false)
                //? if >=1.16 {
                .setTooltip(
                    //? if >=1.19 {
                    translatable("option.mine-lights.refresh_devices.tooltip")
                    //?} else if <1.19 {
                    /*new TranslatableText("option.mine-lights.refresh_devices.tooltip")
                    *///?}
                )
                //?}
                //? if <1.16 {
                /* .setTooltip(new TranslatableText("option.mine-lights.refresh_devices.tooltip").getString()) */
                //?}
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.refreshDevices = newValue)
                .build());

        ConfigCategory integrations = builder.getOrCreateCategory(
                //? if >=1.19 && <1.20 {
                literal(stableCategoryName("category.mine-lights.integrations"))
                //?} else if >=1.20 {
                /*translatable("category.mine-lights.integrations")
                *///?} else if <1.19 {
                /*new TranslatableText("category.mine-lights.integrations")
                *///?}
                //? if <1.16 {
                /* .getString()
                *///? }
        );

        integrations.addEntry(entryBuilder
                .startBooleanToggle(
                        //? if >=1.19 {
                        translatable("integration.mine-lights.corsair")
                        //?} else if <1.19 {
                        /*new TranslatableText("integration.mine-lights.corsair")
                        *///?}
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
                        //? if >=1.19 {
                        translatable("integration.mine-lights.asus")
                        //?} else if <1.19 {
                        /*new TranslatableText("integration.mine-lights.asus")
                        *///?}
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
                        //? if >=1.19 {
                        translatable("integration.mine-lights.logitech")
                        //?} else if <1.19 {
                        /*new TranslatableText("integration.mine-lights.logitech")
                        *///?}
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
                        //? if >=1.19 {
                        translatable("integration.mine-lights.razer")
                        //?} else if <1.19 {
                        /*new TranslatableText("integration.mine-lights.razer")
                        *///?}
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
                        //? if >=1.19 {
                        translatable("integration.mine-lights.wooting")
                        //?} else if <1.19 {
                        /*new TranslatableText("integration.mine-lights.wooting")
                        *///?}
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
                        //? if >=1.19 {
                        translatable("integration.mine-lights.steelseries")
                        //?} else if <1.19 {
                        /*new TranslatableText("integration.mine-lights.steelseries")
                        *///?}
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
                        //? if >=1.19 {
                        translatable("integration.mine-lights.msi")
                        //?} else if <1.19 {
                        /*new TranslatableText("integration.mine-lights.msi")
                        *///?}
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
                        //? if >=1.19 {
                        translatable("integration.mine-lights.novation")
                        //?} else if <1.19 {
                        /*new TranslatableText("integration.mine-lights.novation")
                        *///?}
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
                        //? if >=1.19 {
                        translatable("integration.mine-lights.picopi")
                        //?} else if <1.19 {
                        /*new TranslatableText("integration.mine-lights.picopi")
                        *///?}
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
                        //? if >=1.19 {
                        translatable("integration.mine-lights.openrgb")
                        //?} else if <1.19 {
                        /*new TranslatableText("integration.mine-lights.openrgb")
                        *///?}
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
                        //? if >=1.19 {
                        translatable("integration.mine-lights.yeelight")
                        //?} else if <1.19 {
                        /*new TranslatableText("integration.mine-lights.yeelight")
                        *///?}
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.enableYeelight)
                .setDefaultValue(true)
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableYeelight = newValue)
                .build());

        ConfigCategory devices = builder.getOrCreateCategory(
                //? if >=1.19 && <1.20 {
                literal(stableCategoryName("category.mine-lights.devices"))
                //?} else if >=1.20 {
                /*translatable("category.mine-lights.devices")
                *///?} else if <1.19 {
                /*new TranslatableText("category.mine-lights.devices")
                *///?}
                //? if <1.16 {
                /* .getString()
                *///? }
        );
        devices.addEntry(entryBuilder.startTextDescription(
                //? if >=1.19 {
                translatable("option.mine-lights.device.header")
                //?} else if <1.19 {
                /*new TranslatableText("option.mine-lights.device.header")
                *///?}
                //? if <1.16 {
                /* .getString()
                *///? }
        ).build());

        MineLightsClient.discoveredDevices.stream().sorted(Comparator.naturalOrder())
                .forEach(uniqueId -> {
                    boolean isEnabled = MineLightsClient.isDeviceEnabled(uniqueId);
                    String[] parts = uniqueId.split("\\|", 2);
                    String deviceSdk = parts.length > 1 ? parts[0] : "Unknown";
                    String deviceName = MineLightsClient.getDiscoveredDeviceDisplayName(uniqueId);
                    
                    //? if >=1.19 {
                    //? if >=26.1 {
                    Component label = literal(deviceName)
                            .append(gray(literal(" (" + deviceSdk + ")")));
                    //?} else {
                    /* Text label = literal(deviceName)
                            .append(gray(literal(" (" + deviceSdk + ")"))); */
                    //?}
                    //?} else if <1.19 {
                    /*Text label = new LiteralText(deviceName)
                            .append(new LiteralText(" (" + deviceSdk + ")")
                                    .formatted(Formatting.GRAY));
                    *///?}
                                    
                    devices.addEntry(entryBuilder
                            .startBooleanToggle(label
                            //? if <1.16 {
                            /* .getString()
                            *///? }
                            , isEnabled)
                            .setDefaultValue(true)
                            .setSaveConsumer(newValue -> MineLightsClient.setDeviceEnabled(uniqueId, newValue)).build());
                });

        if (!MineLightsClient.CONFIG.disabledDevices.isEmpty()) {
            devices.addEntry(entryBuilder.startTextDescription(
                    //? if >=1.19 {
                    literal("")
                    //?} else if <1.19 {
                    /*new LiteralText("")
                    *///?}
                    //? if <1.16 {
                    /* .getString()
                    *///? }
            ).build());

            devices.addEntry(entryBuilder
                    .startBooleanToggle(
                        //? if >=1.19 {
                        translatable("option.mine-lights.clear_disabled.label")
                        //?} else if <1.19 {
                        /*new TranslatableText("option.mine-lights.clear_disabled.label")
                        *///?}
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.clearDisabledDevices)
                    .setDefaultValue(false)
                    //? if >=1.16 {
                    .setTooltip(
                        //? if >=1.19 {
                        translatable("option.mine-lights.clear_disabled.tooltip")
                        //?} else if <1.19 {
                        /*new TranslatableText("option.mine-lights.clear_disabled.tooltip")
                        *///?}
                    )
                    //?}
                    //? if <1.16 {
                    /* .setTooltip(new TranslatableText("option.mine-lights.clear_disabled.tooltip").getString()) */
                    //?}
                    .setSaveConsumer(
                            newValue -> MineLightsClient.CONFIG.clearDisabledDevices = newValue)
                    .build());
        }

        ConfigCategory playerStatus = builder.getOrCreateCategory(
                //? if >=1.19 && <1.20 {
                literal(stableCategoryName("category.mine-lights.player_status"))
                //?} else if >=1.20 {
                /*translatable("category.mine-lights.player_status")
                *///?} else if <1.19 {
                /*new TranslatableText("category.mine-lights.player_status")
                *///?}
                //? if <1.16 {
                /* .getString()
                *///? }
        );

        playerStatus.addEntry(entryBuilder
                .startBooleanToggle(
                        //? if >=1.19 {
                        translatable("option.mine-lights.enableHealthBar.configured", keysLabel(MineLightsClient.CONFIG.healthBarKeys))
                        //?} else if <1.19 {
                        /*new TranslatableText("option.mine-lights.enableHealthBar.configured", keysLabel(MineLightsClient.CONFIG.healthBarKeys))
                        *///?}
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
                        //? if >=1.19 {
                        translatable("option.mine-lights.enableHungerBar.configured", keysLabel(MineLightsClient.CONFIG.hungerBarKeys))
                        //?} else if <1.19 {
                        /*new TranslatableText("option.mine-lights.enableHungerBar.configured", keysLabel(MineLightsClient.CONFIG.hungerBarKeys))
                        *///?}
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
                        //? if >=1.19 {
                        translatable("option.mine-lights.enableSaturationBar.configured", keysLabel(MineLightsClient.CONFIG.saturationBarKeys))
                        //?} else if <1.19 {
                        /*new TranslatableText("option.mine-lights.enableSaturationBar.configured", keysLabel(MineLightsClient.CONFIG.saturationBarKeys))
                        *///?}
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.enableSaturationBar)
                .setDefaultValue(true)
                //? if >=1.16 {
                .setTooltip(
                    //? if >=1.19 {
                    translatable("option.mine-lights.enableSaturationBar.tooltip", keysLabel(MineLightsClient.CONFIG.saturationBarKeys))
                    //?} else if <1.19 {
                    /*new TranslatableText("option.mine-lights.enableSaturationBar.tooltip", keysLabel(MineLightsClient.CONFIG.saturationBarKeys))
                    *///?}
                )
                //?}
                //? if <1.16 {
                /* .setTooltip(new TranslatableText("option.mine-lights.enableSaturationBar.tooltip", keysLabel(MineLightsClient.CONFIG.saturationBarKeys)).getString()) */
                //?}
                .setSaveConsumer(
                        newValue -> MineLightsClient.CONFIG.enableSaturationBar = newValue)
                .build());
        playerStatus.addEntry(entryBuilder
                .startBooleanToggle(
                        //? if >=1.19 {
                        translatable("option.mine-lights.enableExperienceBar.configured", keysLabel(MineLightsClient.CONFIG.experienceBarKeys))
                        //?} else if <1.19 {
                        /*new TranslatableText("option.mine-lights.enableExperienceBar.configured", keysLabel(MineLightsClient.CONFIG.experienceBarKeys))
                        *///?}
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.enableExperienceBar)
                .setDefaultValue(true)
                //? if >=1.16 {
                .setTooltip(
                    //? if >=1.19 {
                    translatable("option.mine-lights.experienceBarKeys.tooltip")
                    //?} else if <1.19 {
                    /*new TranslatableText("option.mine-lights.experienceBarKeys.tooltip")
                    *///?}
                )
                //?}
                //? if <1.16 {
                /* .setTooltip(new TranslatableText("option.mine-lights.experienceBarKeys.tooltip").getString()) */
                //?}
                .setSaveConsumer(
                        newValue -> MineLightsClient.CONFIG.enableExperienceBar = newValue)
                .build());
        //? if >=1.16 {
        addPlayerStatusExtras(playerStatus, entryBuilder);
        //?}

        //? if >=26.1 {
        playerStatus.addEntry(entryBuilder
                .startBooleanToggle(translatable("option.mine-lights.enableLocatorBar.configured", keysLabel(MineLightsClient.CONFIG.locatorBarKeys)),
                                MineLightsClient.CONFIG.enableLocatorBar)
                .setDefaultValue(true)
                .setTooltip(translatable("option.mine-lights.enableLocatorBar.tooltip", keysLabel(MineLightsClient.CONFIG.locatorBarKeys)))
                .setSaveConsumer(
                                newValue -> MineLightsClient.CONFIG.enableLocatorBar = newValue)
                .build());
        //?}
        
        playerStatus.addEntry(entryBuilder
                .startBooleanToggle(
                        //? if >=1.19 {
                        translatable("option.mine-lights.enableCompassEffect.configured", keysLabel(MineLightsClient.CONFIG.compassKeys))
                        //?} else if <1.19 {
                        /*new TranslatableText("option.mine-lights.enableCompassEffect.configured", keysLabel(MineLightsClient.CONFIG.compassKeys))
                        *///?}
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
                        //? if >=1.19 {
                        translatable("option.mine-lights.alwaysShowCompass")
                        //?} else if <1.19 {
                        /*new TranslatableText("option.mine-lights.alwaysShowCompass")
                        *///?}
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.alwaysShowCompass)
                .setDefaultValue(false)
                //? if >=1.16 {
                .setTooltip(
                    //? if >=1.19 {
                    translatable("option.mine-lights.alwaysShowCompass.tooltip")
                    //?} else if <1.19 {
                    /*new TranslatableText("option.mine-lights.alwaysShowCompass.tooltip")
                    *///?}
                )
                //?}
                //? if <1.16 {
                /* .setTooltip(new TranslatableText("option.mine-lights.alwaysShowCompass.tooltip").getString()) */
                //?}
                .setSaveConsumer(
                        newValue -> MineLightsClient.CONFIG.alwaysShowCompass = newValue)
                .build());

        //? if >=1.19 {
        CompassPriority initial = MineLightsClient.CONFIG.compassPriority != null
                ? MineLightsClient.CONFIG.compassPriority
                : CompassPriority.PRIORITY;

        playerStatus.addEntry(entryBuilder
                .startEnumSelector(translatable("option.mine-lights.compassPriority"),
                                CompassPriority.class,
                                initial)
                .setDefaultValue(CompassPriority.PRIORITY)
                .setEnumNameProvider((value) -> translatable("enum.mine-lights.compassPriority." + value.toString().toLowerCase()))
                .setTooltip(translatable("option.mine-lights.compassPriority.tooltip"))
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.compassPriority = newValue)
                .build());
        //?} else if >=1.16 {
        /*
        CompassPriority initial = MineLightsClient.CONFIG.compassPriority != null
                ? MineLightsClient.CONFIG.compassPriority
                : CompassPriority.PRIORITY;
        playerStatus.addEntry(entryBuilder
                .startEnumSelector(
                        new TranslatableText("option.mine-lights.compassPriority"),
                        CompassPriority.class,
                        initial)
                        .setDefaultValue(CompassPriority.PRIORITY)                
                        .setEnumNameProvider((value) -> {
                String key = "enum.mine-lights.compassPriority." + value.toString().toLowerCase();
                return new TranslatableText(key);
                })
                .setTooltip(
                new TranslatableText("option.mine-lights.compassPriority.tooltip")
                )
                .setSaveConsumer(
                        newValue -> MineLightsClient.CONFIG.compassPriority = newValue)
                .build());
         */
        //?}
        playerStatus.addEntry(entryBuilder
                .startBooleanToggle(
                        //? if >=1.19 {
                        translatable("option.mine-lights.enableLowHealthWarning")
                        //?} else if <1.19 {
                        /*new TranslatableText("option.mine-lights.enableLowHealthWarning")
                        *///?}
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
                        //? if >=1.19 {
                        translatable("option.mine-lights.highlightMovementKeys")
                        //?} else if <1.19 {
                        /*new TranslatableText("option.mine-lights.highlightMovementKeys")
                        *///?}
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.highlightMovementKeys)
                .setDefaultValue(true)
                //? if >=1.16 {
                .setTooltip(
                    //? if >=1.19 {
                    translatable("option.mine-lights.highlightMovementKeys.tooltip")
                    //?} else if <1.19 {
                    /*new TranslatableText("option.mine-lights.highlightMovementKeys.tooltip")
                    *///?}
                )
                //?}
                //? if <1.16 {
                /* .setTooltip(new TranslatableText("option.mine-lights.highlightMovementKeys.tooltip").getString()) */
                //?}
                .setSaveConsumer(
                        newValue -> MineLightsClient.CONFIG.highlightMovementKeys = newValue)
                .build());

        playerStatus.addEntry(entryBuilder
                .startBooleanToggle(
                        //? if >=1.19 {
                        translatable("option.mine-lights.pulseChatKey")
                        //?} else if <1.19 {
                        /*new TranslatableText("option.mine-lights.pulseChatKey")
                        *///?}
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.pulseChatKey)
                .setDefaultValue(true)
                //? if >=1.16 {
                .setTooltip(
                    //? if >=1.19 {
                    translatable("option.mine-lights.pulseChatKey.tooltip")
                    //?} else if <1.19 {
                    /*new TranslatableText("option.mine-lights.pulseChatKey.tooltip")
                    *///?}
                )
                //?}
                //? if <1.16 {
                /* .setTooltip(new TranslatableText("option.mine-lights.pulseChatKey.tooltip").getString()) */
                //?}
                .setSaveConsumer(
                        newValue -> MineLightsClient.CONFIG.pulseChatKey = newValue)
                .build());

        ConfigCategory environment = builder.getOrCreateCategory(
                //? if >=1.19 && <1.20 {
                literal(stableCategoryName("category.mine-lights.environment"))
                //?} else if >=1.20 {
                /*translatable("category.mine-lights.environment")
                *///?} else if <1.19 {
                /*new TranslatableText("category.mine-lights.environment")
                *///?}
                //? if <1.16 {
                /* .getString()
                *///? }
        );

        environment.addEntry(entryBuilder
                .startBooleanToggle(
                        //? if >=1.19 {
                        translatable("option.mine-lights.enableBiomeEffects")
                        //?} else if <1.19 {
                        /*new TranslatableText("option.mine-lights.enableBiomeEffects")
                        *///?}
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.enableBiomeEffects)
                .setDefaultValue(true)
                //? if >=1.16 {
                .setTooltip(
                    //? if >=1.19 {
                    translatable("option.mine-lights.enableBiomeEffects.tooltip")
                    //?} else if <1.19 {
                    /*new TranslatableText("option.mine-lights.enableBiomeEffects.tooltip")
                    *///?}
                )
                //?}
                //? if <1.16 {
                /* .setTooltip(new TranslatableText("option.mine-lights.enableBiomeEffects.tooltip").getString()) */
                //?}
                .setSaveConsumer(
                        newValue -> MineLightsClient.CONFIG.enableBiomeEffects = newValue)
                .build());
        
        //? if >=1.19 {
        DimmingMode initialDimming = MineLightsClient.CONFIG.dimmingMode != null
                ? MineLightsClient.CONFIG.dimmingMode
                : DimmingMode.LOCAL_LIGHT;

        environment.addEntry(entryBuilder
                .startEnumSelector(translatable("option.mine-lights.dimmingMode"),
                        DimmingMode.class,
                        initialDimming)
                .setDefaultValue(DimmingMode.NONE)
                .setEnumNameProvider((value) -> translatable("enum.mine-lights.dimmingMode." + value.toString().toLowerCase()))
                .setTooltip(translatable("option.mine-lights.dimmingMode.tooltip"))
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.dimmingMode = newValue)
                .build());
        //?} else if >=1.16 {
        /*
        DimmingMode initialDimming = MineLightsClient.CONFIG.dimmingMode != null
                ? MineLightsClient.CONFIG.dimmingMode
                : DimmingMode.NONE;

        environment.addEntry(entryBuilder
                .startEnumSelector(
                        new TranslatableText("option.mine-lights.dimmingMode"),
                        DimmingMode.class,
                        initialDimming)
                .setDefaultValue(DimmingMode.NONE)
                .setEnumNameProvider((value) -> {
                String key = "enum.mine-lights.dimmingMode." + value.toString().toLowerCase();
                return new TranslatableText(key);
                })
                .setTooltip(new TranslatableText("option.mine-lights.dimmingMode.tooltip"))
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.dimmingMode = newValue)
                .build());
        */
        //?}
        
        environment.addEntry(entryBuilder
                .startBooleanToggle(
                        //? if >=1.19 {
                        translatable("option.mine-lights.enableWeatherEffects")
                        //?} else if <1.19 {
                        /*new TranslatableText("option.mine-lights.enableWeatherEffects")
                        *///?}
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.enableWeatherEffects)
                .setDefaultValue(true)
                //? if >=1.16 {
                .setTooltip(
                    //? if >=1.19 {
                    translatable("option.mine-lights.enableWeatherEffects.tooltip")
                    //?} else if <1.19 {
                    /*new TranslatableText("option.mine-lights.enableWeatherEffects.tooltip")
                    *///?}
                )
                //?}
                //? if <1.16 {
                /* .setTooltip(new TranslatableText("option.mine-lights.enableWeatherEffects.tooltip").getString()) */
                //?}
                .setSaveConsumer(
                        newValue -> MineLightsClient.CONFIG.enableWeatherEffects = newValue)
                .build());
        environment.addEntry(entryBuilder
                .startBooleanToggle(
                        //? if >=1.19 {
                        translatable("option.mine-lights.mirrorRamRainDirection")
                        //?} else if <1.19 {
                        /*new TranslatableText("option.mine-lights.mirrorRamRainDirection")
                        *///?}
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.mirrorRamRainDirection)
                .setDefaultValue(false)
                //? if >=1.16 {
                .setTooltip(
                    //? if >=1.19 {
                    translatable("option.mine-lights.mirrorRamRainDirection.tooltip")
                    //?} else if <1.19 {
                    /*new TranslatableText("option.mine-lights.mirrorRamRainDirection.tooltip")
                    *///?}
                )
                //?}
                //? if <1.16 {
                /* .setTooltip(new TranslatableText("option.mine-lights.mirrorRamRainDirection.tooltip").getString()) */
                //?}
                .setSaveConsumer(newValue -> MineLightsClient.CONFIG.mirrorRamRainDirection = newValue)
                .build());
        //? if >=1.21.9 {
        environment.addEntry(entryBuilder
                .startBooleanToggle(
                        translatable("option.mine-lights.enableEndFlashEffect"),
                        MineLightsClient.CONFIG.enableEndFlashEffect)
                .setDefaultValue(true)
                .setTooltip(
                    translatable("option.mine-lights.enableEndFlashEffect.tooltip"))
                .setSaveConsumer(
                        newValue -> MineLightsClient.CONFIG.enableEndFlashEffect = newValue)
                .build());
        //?}
        environment.addEntry(entryBuilder
                .startBooleanToggle(
                        //? if >=1.19 {
                        translatable("option.mine-lights.enableOnFireEffect")
                        //?} else if <1.19 {
                        /*new TranslatableText("option.mine-lights.enableOnFireEffect")
                        *///?}
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
                        //? if >=1.19 {
                        translatable("option.mine-lights.enableInWaterEffect")
                        //?} else if <1.19 {
                        /*new TranslatableText("option.mine-lights.enableInWaterEffect")
                        *///?}
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.enableInWaterEffect)
                .setDefaultValue(true)
                //? if >=1.16 {
                .setTooltip(
                    //? if >=1.19 {
                    translatable("option.mine-lights.enableInWaterEffect.tooltip")
                    //?} else if <1.19 {
                    /*new TranslatableText("option.mine-lights.enableInWaterEffect.tooltip")
                    *///?}
                )
                //?}
                //? if <1.16 {
                /* .setTooltip(new TranslatableText("option.mine-lights.enableInWaterEffect.tooltip").getString()) */
                //?}
                .setSaveConsumer(
                        newValue -> MineLightsClient.CONFIG.enableInWaterEffect = newValue)
                .build());
        environment.addEntry(entryBuilder
                .startBooleanToggle(
                        //? if >=1.19 {
                        translatable("option.mine-lights.enablePortalEffects")
                        //?} else if <1.19 {
                        /*new TranslatableText("option.mine-lights.enablePortalEffects")
                        *///?}
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                        ,
                        MineLightsClient.CONFIG.enablePortalEffects)
                .setDefaultValue(true)
                .setSaveConsumer(
                        newValue -> MineLightsClient.CONFIG.enablePortalEffects = newValue)
                .build());

        //? if >=1.16 {
        addLayoutCategories(builder, entryBuilder);
        //?} else {
        /*addLegacyEffectCategories(builder, entryBuilder);
        *///?}

        ConfigCategory aboutCategory = builder
                .getOrCreateCategory(
                        //? if >=1.19 && <1.20 {
                        literal(stableCategoryName("category.mine-lights.about"))
                        //?} else if >=1.20 {
                        /*translatable("category.mine-lights.about")
                        *///?} else if <1.19 {
                        /*new TranslatableText("category.mine-lights.about")
                        *///?}
                        //? if <1.16 {
                        /* .getString()
                        *///? }
                );

        aboutCategory.addEntry(entryBuilder.startTextDescription(
                //? if >=1.19 {
                translatable("text.mine-lights.about.title")
                //?} else if <1.19 {
                /*new TranslatableText("text.mine-lights.about.title")
                *///?}
                //? if <1.16 {
                /* .getString()
                *///? }
        ).build());

        aboutCategory.addEntry(entryBuilder.startTextDescription(
                //? if >=1.19 {
                translatable("text.mine-lights.about.version", MineLightsClient.getModVersion())
                //?} else if <1.19 {
                /*new TranslatableText("text.mine-lights.about.version", MineLightsClient.getModVersion())
                *///?}
                //? if <1.16 {
                /* .getString()
                *///? }
        ).build());

        aboutCategory.addEntry(entryBuilder.startTextDescription(
                //? if >=1.19 {
                translatable("text.mine-lights.about.copyright")
                //?} else if <1.19 {
                /*new TranslatableText("text.mine-lights.about.copyright")
                *///?}
                //? if <1.16 {
                /* .getString()
                *///? }
        ).build());

        aboutCategory.addEntry(entryBuilder.startTextDescription(
                //? if >=1.19 {
                literal("")
                //?} else if <1.19 {
                /*new LiteralText("")
                *///?}
                //? if <1.16 {
                /* .getString()
                *///? }
        ).build());

        aboutCategory.addEntry(entryBuilder.startTextField(
                //? if >=1.19 {
                translatable("text.mine-lights.about.source_code")
                //?} else if <1.19 {
                /*new TranslatableText("text.mine-lights.about.source_code")
                *///?}
                //? if <1.16 {
                /* .getString()
                *///? }
                ,
                "https://github.com/megabytesme/MineLights").build());

        aboutCategory.addEntry(entryBuilder.startTextField(
                //? if >=1.19 {
                translatable("text.mine-lights.about.issues")
                //?} else if <1.19 {
                /*new TranslatableText("text.mine-lights.about.issues")
                *///?}
                //? if <1.16 {
                /* .getString()
                *///? }
                ,
                "https://github.com/megabytesme/MineLights/issues").build());

        aboutCategory.addEntry(entryBuilder.startTextDescription(
                //? if >=1.19 {
                literal("")
                //?} else if <1.19 {
                /*new LiteralText("")
                *///?}
                //? if <1.16 {
                /* .getString()
                *///? }
        ).build());

        aboutCategory.addEntry(entryBuilder.startTextDescription(
                //? if >=1.19 {
                translatable("text.mine-lights.about.support_intro")
                //?} else if <1.19 {
                /*new TranslatableText("text.mine-lights.about.support_intro")
                *///?}
                //? if <1.16 {
                /* .getString()
                *///? }
        ).build());

        aboutCategory.addEntry(entryBuilder.startTextField(
                //? if >=1.19 {
                translatable("text.mine-lights.about.kofi")
                //?} else if <1.19 {
                /*new TranslatableText("text.mine-lights.about.kofi")
                *///?}
                //? if <1.16 {
                /* .getString()
                *///? }
                ,
                "https://ko-fi.com/megabytesme").build());

        aboutCategory.addEntry(entryBuilder.startTextDescription(
                //? if >=1.19 {
                literal("")
                //?} else if <1.19 {
                /*new LiteralText("")
                *///?}
                //? if <1.16 {
                /* .getString()
                *///? }
        ).build());

        aboutCategory.addEntry(entryBuilder.startTextDescription(
                //? if >=1.19 {
                translatable("text.mine-lights.about.description")
                //?} else if <1.19 {
                /*new TranslatableText("text.mine-lights.about.description")
                *///?}
                //? if <1.16 {
                /* .getString()
                *///? }
        ).build());

        return builder.build();
    }

    private static String keysLabel(java.util.List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return "-";
        }
        return String.join(" ", keys);
    }

    //? if <1.16 {
    /*private static void addLegacyEffectCategories(ConfigBuilder builder, ConfigEntryBuilder entryBuilder) {
        ConfigCategory effects = builder.getOrCreateCategory(new TranslatableText("category.mine-lights.key_layout").getString());
        MineLightsConfig defaults = new MineLightsConfig();
        effects.addEntry(entryBuilder.startTextDescription(new TranslatableText("option.mine-lights.effectConfigHelp").getString()).build());
        for (String name : megabytesme.minelights.network.EffectSettings.NAMES) {
            if (!megabytesme.minelights.network.EffectSettings.isAvailable(name)) continue;
            effects.addEntry(entryBuilder.startStrField(new TranslatableText(megabytesme.minelights.network.EffectSettings.labelKey(name)).getString(),
                            megabytesme.minelights.network.EffectSettings.get(MineLightsClient.CONFIG, name))
                    .setDefaultValue(megabytesme.minelights.network.EffectSettings.get(defaults, name))
                    .setErrorSupplier(value -> {
                        try { megabytesme.minelights.network.EffectSettings.set(new MineLightsConfig(), name, value); return java.util.Optional.empty(); }
                        catch (IllegalArgumentException exception) { return java.util.Optional.of(exception.getMessage()); }
                    })
                    .setSaveConsumer(value -> {
                        boolean changed = !megabytesme.minelights.network.EffectSettings.get(MineLightsClient.CONFIG, name).equals(value);
                        megabytesme.minelights.network.EffectSettings.set(MineLightsClient.CONFIG, name, value);
                        if (changed && name.equals("led-overrides")) MineLightsClient.CONFIG.refreshDevices = true;
                    }).build());
        }
        effects.addEntry(entryBuilder.startBooleanToggle(new TranslatableText("option.mine-lights.enableAbsorptionOverlay").getString(), MineLightsClient.CONFIG.enableAbsorptionOverlay)
                .setDefaultValue(defaults.enableAbsorptionOverlay).setSaveConsumer(value -> MineLightsClient.CONFIG.enableAbsorptionOverlay = value).build());
        effects.addEntry(entryBuilder.startBooleanToggle(new TranslatableText("option.mine-lights.mergeSaturationIntoHunger").getString(), MineLightsClient.CONFIG.mergeSaturationIntoHunger)
                .setDefaultValue(defaults.mergeSaturationIntoHunger).setSaveConsumer(value -> MineLightsClient.CONFIG.mergeSaturationIntoHunger = value).build());
        effects.addEntry(entryBuilder.startBooleanToggle(new TranslatableText("option.mine-lights.restoreLightingOutsideWorld").getString(), MineLightsClient.CONFIG.restoreLightingOutsideWorld)
                .setDefaultValue(defaults.restoreLightingOutsideWorld).setSaveConsumer(value -> MineLightsClient.CONFIG.restoreLightingOutsideWorld = value).build());
    }
    *///?}

    //? if >=1.16 {
    private static void addLayoutCategories(ConfigBuilder builder, ConfigEntryBuilder entryBuilder) {
        MineLightsConfig defaults = new MineLightsConfig();
        ConfigCategory layout = builder.getOrCreateCategory(categoryName("category.mine-lights.key_layout"));
        layout.addEntry(description(entryBuilder, "text.mine-lights.key_layout.help"));

        addKeyList(layout, entryBuilder, "healthBarKeys", MineLightsClient.CONFIG.healthBarKeys,
                defaults.healthBarKeys, 24, v -> MineLightsClient.CONFIG.healthBarKeys = v);
        addKeyList(layout, entryBuilder, "hungerBarKeys", MineLightsClient.CONFIG.hungerBarKeys,
                defaults.hungerBarKeys, 24, v -> MineLightsClient.CONFIG.hungerBarKeys = v);
        addKeyList(layout, entryBuilder, "saturationBarKeys", MineLightsClient.CONFIG.saturationBarKeys,
                defaults.saturationBarKeys, 24, v -> MineLightsClient.CONFIG.saturationBarKeys = v);
        addKeyList(layout, entryBuilder, "experienceBarKeys", MineLightsClient.CONFIG.experienceBarKeys,
                defaults.experienceBarKeys, 24, v -> MineLightsClient.CONFIG.experienceBarKeys = v);
        //? if >=1.21.6 {
        addKeyList(layout, entryBuilder, "locatorBarKeys", MineLightsClient.CONFIG.locatorBarKeys,
                defaults.locatorBarKeys, 24, v -> MineLightsClient.CONFIG.locatorBarKeys = v);
        layout.addEntry(entryBuilder.startFloatField(translatable("option.mine-lights.locatorDotSize"),
                        MineLightsClient.CONFIG.locatorDotSize)
                .setDefaultValue(defaults.locatorDotSize)
                .setMin(0.5F)
                .setMax(5.0F)
                .setTooltip(translatable("option.mine-lights.locatorDotSize.tooltip"))
                .setSaveConsumer(v -> MineLightsClient.CONFIG.locatorDotSize = v)
                .build());
        layout.addEntry(description(entryBuilder, "option.mine-lights.locatorDotSize.description"));
        //?}
        addKeyList(layout, entryBuilder, "heartbeatRedKeys", MineLightsClient.CONFIG.heartbeatRedKeys,
                defaults.heartbeatRedKeys, 48, v -> MineLightsClient.CONFIG.heartbeatRedKeys = v);
        addKeyList(layout, entryBuilder, "heartbeatWhiteKeys", MineLightsClient.CONFIG.heartbeatWhiteKeys,
                defaults.heartbeatWhiteKeys, 48, v -> MineLightsClient.CONFIG.heartbeatWhiteKeys = v);
        addKeyList(layout, entryBuilder, "compassKeys", MineLightsClient.CONFIG.compassKeys,
                defaults.compassKeys, 16, v -> MineLightsClient.CONFIG.compassKeys = v);
        addKeyList(layout, entryBuilder, "compassCenterKey",
                java.util.Collections.singletonList(MineLightsClient.CONFIG.compassCenterKey),
                java.util.Collections.singletonList(defaults.compassCenterKey), 1,
                v -> MineLightsClient.CONFIG.compassCenterKey = v.isEmpty() ? "" : v.get(0));
        layout.addEntry(entryBuilder.startStrList(translatable("option.mine-lights.ledOverrides"),
                        new java.util.ArrayList<>(MineLightsClient.CONFIG.ledOverrides))
                .setDefaultValue(new java.util.ArrayList<>())
                .setExpanded(true)
                .setTooltip(translatable("option.mine-lights.ledOverrides.tooltip"))
                .setSaveConsumer(v -> {
                    if (!v.equals(MineLightsClient.CONFIG.ledOverrides)) {
                        MineLightsClient.CONFIG.ledOverrides = new java.util.ArrayList<>(v);
                        MineLightsClient.CONFIG.refreshDevices = true;
                    }
                })
                .build());
        layout.addEntry(description(entryBuilder, "option.mine-lights.ledOverrides.description"));

        ConfigCategory colors = builder.getOrCreateCategory(categoryName("category.mine-lights.colors"));
        colors.addEntry(entryBuilder.startBooleanToggle(translatable("option.mine-lights.restoreLightingOutsideWorld"),
                        MineLightsClient.CONFIG.restoreLightingOutsideWorld)
                .setDefaultValue(defaults.restoreLightingOutsideWorld)
                .setTooltip(translatable("option.mine-lights.restoreLightingOutsideWorld.tooltip"))
                .setSaveConsumer(v -> MineLightsClient.CONFIG.restoreLightingOutsideWorld = v)
                .build());
        colors.addEntry(entryBuilder.startStrField(translatable("option.mine-lights.outsideWorldProfile"),
                        MineLightsClient.CONFIG.outsideWorldProfile)
                .setDefaultValue(defaults.outsideWorldProfile)
                .setTooltip(translatable("option.mine-lights.outsideWorldProfile.tooltip"))
                .setSaveConsumer(v -> MineLightsClient.CONFIG.outsideWorldProfile = v)
                .build());
        colors.addEntry(description(entryBuilder, "text.mine-lights.colors.help"));
        colors.addEntry(colorField(entryBuilder, "healthColor", MineLightsClient.CONFIG.healthColor,
                defaults.healthColor, v -> MineLightsClient.CONFIG.healthColor = v));
        colors.addEntry(colorField(entryBuilder, "healthDimColor", MineLightsClient.CONFIG.healthDimColor,
                defaults.healthDimColor, v -> MineLightsClient.CONFIG.healthDimColor = v));
        colors.addEntry(colorField(entryBuilder, "absorptionColor", MineLightsClient.CONFIG.absorptionColor,
                defaults.absorptionColor, v -> MineLightsClient.CONFIG.absorptionColor = v));
        colors.addEntry(colorField(entryBuilder, "hungerColor", MineLightsClient.CONFIG.hungerColor,
                defaults.hungerColor, v -> MineLightsClient.CONFIG.hungerColor = v));
        colors.addEntry(colorField(entryBuilder, "hungerDimColor", MineLightsClient.CONFIG.hungerDimColor,
                defaults.hungerDimColor, v -> MineLightsClient.CONFIG.hungerDimColor = v));
        colors.addEntry(colorField(entryBuilder, "saturationColor", MineLightsClient.CONFIG.saturationColor,
                defaults.saturationColor, v -> MineLightsClient.CONFIG.saturationColor = v));
        colors.addEntry(colorField(entryBuilder, "saturationDimColor", MineLightsClient.CONFIG.saturationDimColor,
                defaults.saturationDimColor, v -> MineLightsClient.CONFIG.saturationDimColor = v));
        colors.addEntry(colorField(entryBuilder, "airColor", MineLightsClient.CONFIG.airColor,
                defaults.airColor, v -> MineLightsClient.CONFIG.airColor = v));
        colors.addEntry(colorField(entryBuilder, "airDimColor", MineLightsClient.CONFIG.airDimColor,
                defaults.airDimColor, v -> MineLightsClient.CONFIG.airDimColor = v));
        colors.addEntry(colorField(entryBuilder, "locatorBarColor", MineLightsClient.CONFIG.locatorBarColor,
                defaults.locatorBarColor, v -> MineLightsClient.CONFIG.locatorBarColor = v));
        colors.addEntry(colorField(entryBuilder, "experienceColor", MineLightsClient.CONFIG.experienceColor,
                defaults.experienceColor, v -> MineLightsClient.CONFIG.experienceColor = v));
        colors.addEntry(colorField(entryBuilder, "experienceDimColor", MineLightsClient.CONFIG.experienceDimColor,
                defaults.experienceDimColor, v -> MineLightsClient.CONFIG.experienceDimColor = v));
        colors.addEntry(colorField(entryBuilder, "heartbeatColor", MineLightsClient.CONFIG.heartbeatColor,
                defaults.heartbeatColor, v -> MineLightsClient.CONFIG.heartbeatColor = v));
    }

    private static void addPlayerStatusExtras(ConfigCategory playerStatus, ConfigEntryBuilder entryBuilder) {
        MineLightsConfig defaults = new MineLightsConfig();
        //? if >=1.21.6 && <26.1 {
        /*playerStatus.addEntry(entryBuilder.startBooleanToggle(
                        translatable("option.mine-lights.enableLocatorBar.configured", keysLabel(MineLightsClient.CONFIG.locatorBarKeys)),
                        MineLightsClient.CONFIG.enableLocatorBar)
                .setDefaultValue(true)
                .setTooltip(translatable("option.mine-lights.enableLocatorBar.tooltip", keysLabel(MineLightsClient.CONFIG.locatorBarKeys)))
                .setSaveConsumer(v -> MineLightsClient.CONFIG.enableLocatorBar = v)
                .build());
        *///?}
        //? if >=1.21.6 {
        ExperienceLocatorMode mode = MineLightsClient.CONFIG.experienceLocatorMode != null
                ? MineLightsClient.CONFIG.experienceLocatorMode
                : ExperienceLocatorMode.AUTO;
        playerStatus.addEntry(entryBuilder.startEnumSelector(translatable("option.mine-lights.experienceLocatorMode"),
                        ExperienceLocatorMode.class, mode)
                .setDefaultValue(defaults.experienceLocatorMode)
                .setEnumNameProvider(value -> translatable(
                        "enum.mine-lights.experienceLocatorMode." + value.toString().toLowerCase()))
                .setTooltip(translatable("option.mine-lights.experienceLocatorMode.tooltip"))
                .setSaveConsumer(v -> MineLightsClient.CONFIG.experienceLocatorMode = v)
                .build());
        //?}
        playerStatus.addEntry(entryBuilder.startBooleanToggle(translatable("option.mine-lights.mergeSaturationIntoHunger"),
                        MineLightsClient.CONFIG.mergeSaturationIntoHunger)
                .setDefaultValue(defaults.mergeSaturationIntoHunger)
                .setTooltip(translatable("option.mine-lights.mergeSaturationIntoHunger.tooltip"))
                .setSaveConsumer(v -> MineLightsClient.CONFIG.mergeSaturationIntoHunger = v)
                .build());
        playerStatus.addEntry(entryBuilder.startFloatField(translatable("option.mine-lights.saturationTintStrength"),
                        MineLightsClient.CONFIG.saturationTintStrength)
                .setDefaultValue(defaults.saturationTintStrength)
                .setMin(0.0F)
                .setMax(1.0F)
                .setTooltip(translatable("option.mine-lights.saturationTintStrength.tooltip"))
                .setSaveConsumer(v -> MineLightsClient.CONFIG.saturationTintStrength = v)
                .build());
        playerStatus.addEntry(entryBuilder.startBooleanToggle(translatable("option.mine-lights.enableAbsorptionOverlay"),
                        MineLightsClient.CONFIG.enableAbsorptionOverlay)
                .setDefaultValue(defaults.enableAbsorptionOverlay)
                .setTooltip(translatable("option.mine-lights.enableAbsorptionOverlay.tooltip"))
                .setSaveConsumer(v -> MineLightsClient.CONFIG.enableAbsorptionOverlay = v)
                .build());
        playerStatus.addEntry(entryBuilder.startFloatField(translatable("option.mine-lights.lowHealthThreshold"),
                        MineLightsClient.CONFIG.lowHealthThreshold)
                .setDefaultValue(defaults.lowHealthThreshold)
                .setMin(1.0F)
                .setMax(20.0F)
                .setTooltip(translatable("option.mine-lights.lowHealthThreshold.tooltip"))
                .setSaveConsumer(v -> MineLightsClient.CONFIG.lowHealthThreshold = v)
                .build());
    }

    //? if >=26.1 {
    private static MutableComponent categoryName(String key) {
        return translatable(key);
    }
    //?} else if >=1.19 && <1.20 {
    /*private static MutableText categoryName(String key) {
        return literal(stableCategoryName(key));
    }
    *///?} else {
    /*private static MutableText categoryName(String key) {
        return translatable(key);
    }
    *///?}

    private static me.shedaniel.clothconfig2.api.AbstractConfigListEntry<?> description(ConfigEntryBuilder entryBuilder,
            String key) {
        return entryBuilder.startTextDescription(gray(translatable(key))).build();
    }

    private static void addKeyList(ConfigCategory category, ConfigEntryBuilder entryBuilder, String option,
            java.util.List<String> current, java.util.List<String> defaults, int maxKeys,
            java.util.function.Consumer<java.util.List<String>> save) {
        //? if >=26.1 {
        category.addEntry(new KeyListEntry(translatable("option.mine-lights." + option),
                current, defaults, maxKeys,
                new Component[] { translatable("option.mine-lights." + option + ".tooltip") },
                v -> save.accept(new java.util.ArrayList<>(v))));
        //?} else {
        /*category.addEntry(new KeyListEntry(translatable("option.mine-lights." + option),
                current, defaults, maxKeys,
                new Text[] { translatable("option.mine-lights." + option + ".tooltip") },
                v -> save.accept(new java.util.ArrayList<>(v))));
        *///?}
        category.addEntry(description(entryBuilder, "option.mine-lights." + option + ".description"));
    }

    private static me.shedaniel.clothconfig2.api.AbstractConfigListEntry<?> colorField(ConfigEntryBuilder entryBuilder,
            String option, int current, int defaultValue, java.util.function.Consumer<Integer> save) {
        return entryBuilder.startColorField(translatable("option.mine-lights." + option), current)
                .setDefaultValue(defaultValue)
                .setTooltip(translatable("option.mine-lights." + option + ".tooltip"))
                .setSaveConsumer(save)
                .build();
    }
    //?}
}
