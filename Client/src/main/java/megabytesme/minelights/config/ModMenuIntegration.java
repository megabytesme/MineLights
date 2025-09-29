package megabytesme.minelights.config;

import io.github.prospector.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import megabytesme.minelights.CommandClient;
import megabytesme.minelights.MineLightsClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;

import java.util.Comparator;
import java.util.function.Function;

public class ModMenuIntegration implements ModMenuApi {

        @Override
        public String getModId() {
                return "mine-lights";
        }

        @Override
        public Function<Screen, Screen> getConfigScreenFactory() {
                return parent -> {

                        ConfigBuilder builder = ConfigBuilder.create()
                                        .setParentScreen(parent)
                                        .setTitle(new TranslatableText("title.mine-lights.config").getString());

                        builder.setSavingRunnable(() -> {
                                MineLightsClient.saveConfig();
                                boolean needsRefresh = false;
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
                                        } catch (InterruptedException ignored) {
                                        }
                                        MineLightsClient.refreshLightingManager();
                                }
                        });

                        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

                        ConfigCategory general = builder.getOrCreateCategory(
                                        new TranslatableText("category.mine-lights.general").getString());
                        general.addEntry(entryBuilder
                                        .startBooleanToggle(new TranslatableText("option.mine-lights.enableMod")
                                                        .getString(), MineLightsClient.CONFIG.enableMod)
                                        .setDefaultValue(true)
                                        .setTooltip(new TranslatableText("option.mine-lights.enableMod.tooltip")
                                                        .getString())
                                        .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableMod = newValue)
                                        .build());

                        if (MineLightsClient.IS_WINDOWS) {
                                general.addEntry(entryBuilder
                                                .startBooleanToggle(
                                                                new TranslatableText(
                                                                                "option.mine-lights.autoStartServer")
                                                                                .getString(),
                                                                MineLightsClient.CONFIG.autoStartServer)
                                                .setDefaultValue(true)
                                                .setTooltip(new TranslatableText(
                                                                "option.mine-lights.autoStartServer.tooltip")
                                                                .getString())
                                                .setSaveConsumer(
                                                                newValue -> MineLightsClient.CONFIG.autoStartServer = newValue)
                                                .build());
                        }

                        general.addEntry(entryBuilder.startTextDescription(new LiteralText("").getString()).build());

                        general.addEntry(entryBuilder
                                        .startBooleanToggle(
                                                        new TranslatableText("option.mine-lights.refresh_devices.label")
                                                                        .getString(),
                                                        MineLightsClient.CONFIG.refreshDevices)
                                        .setDefaultValue(false)
                                        .setTooltip(new TranslatableText("option.mine-lights.refresh_devices.tooltip")
                                                        .getString())
                                        .setSaveConsumer(newValue -> MineLightsClient.CONFIG.refreshDevices = newValue)
                                        .build());
                        general.addEntry(entryBuilder
                                        .startBooleanToggle(
                                                        new TranslatableText("option.mine-lights.restart.label")
                                                                        .getString(),
                                                        MineLightsClient.CONFIG.restartProxy)
                                        .setDefaultValue(false)
                                        .setTooltip(new TranslatableText("option.mine-lights.restart.tooltip")
                                                        .getString())
                                        .setSaveConsumer(newValue -> MineLightsClient.CONFIG.restartProxy = newValue)
                                        .build());
                        general.addEntry(entryBuilder
                                        .startBooleanToggle(
                                                        new TranslatableText("option.mine-lights.restart_admin.label")
                                                                        .getString(),
                                                        MineLightsClient.CONFIG.restartProxyAsAdmin)
                                        .setDefaultValue(false)
                                        .setTooltip(new TranslatableText("option.mine-lights.restart_admin.tooltip")
                                                        .getString())
                                        .setSaveConsumer(
                                                        newValue -> MineLightsClient.CONFIG.restartProxyAsAdmin = newValue)
                                        .build());

                        ConfigCategory integrations = builder.getOrCreateCategory(
                                        new TranslatableText("category.mine-lights.integrations").getString());
                        integrations.addEntry(entryBuilder
                                        .startBooleanToggle(
                                                        new TranslatableText("integration.mine-lights.corsair")
                                                                        .getString(),
                                                        MineLightsClient.CONFIG.enableCorsair)
                                        .setDefaultValue(true)
                                        .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableCorsair = newValue)
                                        .build());
                        integrations.addEntry(entryBuilder
                                        .startBooleanToggle(
                                                        new TranslatableText("integration.mine-lights.asus")
                                                                        .getString(),
                                                        MineLightsClient.CONFIG.enableAsus)
                                        .setDefaultValue(true)
                                        .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableAsus = newValue)
                                        .build());
                        integrations.addEntry(entryBuilder
                                        .startBooleanToggle(
                                                        new TranslatableText("integration.mine-lights.logitech")
                                                                        .getString(),
                                                        MineLightsClient.CONFIG.enableLogitech)
                                        .setDefaultValue(true)
                                        .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableLogitech = newValue)
                                        .build());
                        integrations.addEntry(entryBuilder
                                        .startBooleanToggle(
                                                        new TranslatableText("integration.mine-lights.razer")
                                                                        .getString(),
                                                        MineLightsClient.CONFIG.enableRazer)
                                        .setDefaultValue(true)
                                        .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableRazer = newValue)
                                        .build());
                        integrations.addEntry(entryBuilder
                                        .startBooleanToggle(
                                                        new TranslatableText("integration.mine-lights.wooting")
                                                                        .getString(),
                                                        MineLightsClient.CONFIG.enableWooting)
                                        .setDefaultValue(true)
                                        .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableWooting = newValue)
                                        .build());
                        integrations.addEntry(entryBuilder
                                        .startBooleanToggle(
                                                        new TranslatableText("integration.mine-lights.steelseries")
                                                                        .getString(),
                                                        MineLightsClient.CONFIG.enableSteelSeries)
                                        .setDefaultValue(true)
                                        .setSaveConsumer(
                                                        newValue -> MineLightsClient.CONFIG.enableSteelSeries = newValue)
                                        .build());
                        integrations.addEntry(entryBuilder
                                        .startBooleanToggle(
                                                        new TranslatableText("integration.mine-lights.msi").getString(),
                                                        MineLightsClient.CONFIG.enableMsi)
                                        .setDefaultValue(true)
                                        .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableMsi = newValue)
                                        .build());
                        integrations.addEntry(entryBuilder
                                        .startBooleanToggle(
                                                        new TranslatableText("integration.mine-lights.novation")
                                                                        .getString(),
                                                        MineLightsClient.CONFIG.enableNovation)
                                        .setDefaultValue(true)
                                        .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableNovation = newValue)
                                        .build());
                        integrations.addEntry(entryBuilder
                                        .startBooleanToggle(
                                                        new TranslatableText("integration.mine-lights.picopi")
                                                                        .getString(),
                                                        MineLightsClient.CONFIG.enablePicoPi)
                                        .setDefaultValue(true)
                                        .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enablePicoPi = newValue)
                                        .build());
                        integrations.addEntry(entryBuilder
                                        .startBooleanToggle(
                                                        new TranslatableText("integration.mine-lights.openrgb")
                                                                        .getString(),
                                                        MineLightsClient.CONFIG.enableOpenRgb)
                                        .setDefaultValue(true)
                                        .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableOpenRgb = newValue)
                                        .build());
                        integrations.addEntry(entryBuilder
                                        .startBooleanToggle(
                                                        new TranslatableText("integration.mine-lights.yeelight")
                                                                        .getString(),
                                                        MineLightsClient.CONFIG.enableYeelight)
                                        .setDefaultValue(true)
                                        .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableYeelight = newValue)
                                        .build());

                        ConfigCategory devices = builder.getOrCreateCategory(
                                        new TranslatableText("category.mine-lights.devices").getString());
                        devices.addEntry(entryBuilder.startTextDescription(
                                        new TranslatableText("option.mine-lights.device.header").getString()).build());
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
                                                                .startBooleanToggle(label.getString(), isEnabled)
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
                                devices.addEntry(entryBuilder.startTextDescription(new LiteralText("").getString())
                                                .build());
                                devices.addEntry(entryBuilder
                                                .startBooleanToggle(new TranslatableText(
                                                                "option.mine-lights.clear_disabled.label").getString(),
                                                                MineLightsClient.CONFIG.clearDisabledDevices)
                                                .setDefaultValue(false)
                                                .setTooltip(new TranslatableText(
                                                                "option.mine-lights.clear_disabled.tooltip")
                                                                .getString())
                                                .setSaveConsumer(
                                                                newValue -> MineLightsClient.CONFIG.clearDisabledDevices = newValue)
                                                .build());
                        }

                        ConfigCategory playerStatus = builder.getOrCreateCategory(
                                        new TranslatableText("category.mine-lights.player_status").getString());
                        playerStatus.addEntry(entryBuilder
                                        .startBooleanToggle(
                                                        new TranslatableText("option.mine-lights.enableHealthBar")
                                                                        .getString(),
                                                        MineLightsClient.CONFIG.enableHealthBar)
                                        .setDefaultValue(true)
                                        .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableHealthBar = newValue)
                                        .build());
                        playerStatus.addEntry(entryBuilder
                                        .startBooleanToggle(
                                                        new TranslatableText("option.mine-lights.enableHungerBar")
                                                                        .getString(),
                                                        MineLightsClient.CONFIG.enableHungerBar)
                                        .setDefaultValue(true)
                                        .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableHungerBar = newValue)
                                        .build());
                        playerStatus.addEntry(entryBuilder
                                        .startBooleanToggle(
                                                        new TranslatableText("option.mine-lights.enableSaturationBar")
                                                                        .getString(),
                                                        MineLightsClient.CONFIG.enableSaturationBar)
                                        .setDefaultValue(true)
                                        .setTooltip(new TranslatableText(
                                                        "option.mine-lights.enableSaturationBar.tooltip").getString())
                                        .setSaveConsumer(
                                                        newValue -> MineLightsClient.CONFIG.enableSaturationBar = newValue)
                                        .build());
                        playerStatus.addEntry(entryBuilder
                                        .startBooleanToggle(
                                                        new TranslatableText("option.mine-lights.enableExperienceBar")
                                                                        .getString(),
                                                        MineLightsClient.CONFIG.enableExperienceBar)
                                        .setDefaultValue(true)
                                        .setTooltip(new TranslatableText(
                                                        "option.mine-lights.enableExperienceBar.tooltip").getString())
                                        .setSaveConsumer(
                                                        newValue -> MineLightsClient.CONFIG.enableExperienceBar = newValue)
                                        .build());
                        playerStatus.addEntry(entryBuilder
                                        .startBooleanToggle(
                                                        new TranslatableText("option.mine-lights.enableCompassEffect")
                                                                        .getString(),
                                                        MineLightsClient.CONFIG.enableCompassEffect)
                                        .setDefaultValue(true)
                                        .setSaveConsumer(
                                                        newValue -> MineLightsClient.CONFIG.enableCompassEffect = newValue)
                                        .build());
                        playerStatus.addEntry(entryBuilder
                                        .startBooleanToggle(
                                                        new TranslatableText("option.mine-lights.alwaysShowCompass")
                                                                        .getString(),
                                                        MineLightsClient.CONFIG.alwaysShowCompass)
                                        .setDefaultValue(false)
                                        .setTooltip(new TranslatableText("option.mine-lights.alwaysShowCompass.tooltip")
                                                        .getString())
                                        .setSaveConsumer(
                                                        newValue -> MineLightsClient.CONFIG.alwaysShowCompass = newValue)
                                        .build());
                        playerStatus.addEntry(entryBuilder
                                        .startBooleanToggle(
                                                        new TranslatableText(
                                                                        "option.mine-lights.enableLowHealthWarning")
                                                                        .getString(),
                                                        MineLightsClient.CONFIG.enableLowHealthWarning)
                                        .setDefaultValue(true)
                                        .setSaveConsumer(
                                                        newValue -> MineLightsClient.CONFIG.enableLowHealthWarning = newValue)
                                        .build());
                        playerStatus.addEntry(entryBuilder
                                        .startBooleanToggle(
                                                        new TranslatableText("option.mine-lights.highlightMovementKeys")
                                                                        .getString(),
                                                        MineLightsClient.CONFIG.highlightMovementKeys)
                                        .setDefaultValue(true)
                                        .setTooltip(new TranslatableText(
                                                        "option.mine-lights.highlightMovementKeys.tooltip").getString())
                                        .setSaveConsumer(
                                                        newValue -> MineLightsClient.CONFIG.highlightMovementKeys = newValue)
                                        .build());

                        ConfigCategory environment = builder.getOrCreateCategory(
                                        new TranslatableText("category.mine-lights.environment").getString());
                        environment.addEntry(entryBuilder
                                        .startBooleanToggle(
                                                        new TranslatableText("option.mine-lights.enableBiomeEffects")
                                                                        .getString(),
                                                        MineLightsClient.CONFIG.enableBiomeEffects)
                                        .setDefaultValue(true)
                                        .setTooltip(new TranslatableText(
                                                        "option.mine-lights.enableBiomeEffects.tooltip").getString())
                                        .setSaveConsumer(
                                                        newValue -> MineLightsClient.CONFIG.enableBiomeEffects = newValue)
                                        .build());
                        environment.addEntry(entryBuilder
                                        .startBooleanToggle(
                                                        new TranslatableText("option.mine-lights.enableWeatherEffects")
                                                                        .getString(),
                                                        MineLightsClient.CONFIG.enableWeatherEffects)
                                        .setDefaultValue(true)
                                        .setTooltip(new TranslatableText(
                                                        "option.mine-lights.enableWeatherEffects.tooltip").getString())
                                        .setSaveConsumer(
                                                        newValue -> MineLightsClient.CONFIG.enableWeatherEffects = newValue)
                                        .build());
                        environment.addEntry(entryBuilder
                                        .startBooleanToggle(
                                                        new TranslatableText("option.mine-lights.enableOnFireEffect")
                                                                        .getString(),
                                                        MineLightsClient.CONFIG.enableOnFireEffect)
                                        .setDefaultValue(true)
                                        .setSaveConsumer(
                                                        newValue -> MineLightsClient.CONFIG.enableOnFireEffect = newValue)
                                        .build());
                        environment.addEntry(entryBuilder
                                        .startBooleanToggle(
                                                        new TranslatableText("option.mine-lights.enableInWaterEffect")
                                                                        .getString(),
                                                        MineLightsClient.CONFIG.enableInWaterEffect)
                                        .setDefaultValue(true)
                                        .setTooltip(new TranslatableText(
                                                        "option.mine-lights.enableInWaterEffect.tooltip").getString())
                                        .setSaveConsumer(
                                                        newValue -> MineLightsClient.CONFIG.enableInWaterEffect = newValue)
                                        .build());
                        environment.addEntry(entryBuilder
                                        .startBooleanToggle(
                                                        new TranslatableText("option.mine-lights.enablePortalEffects")
                                                                        .getString(),
                                                        MineLightsClient.CONFIG.enablePortalEffects)
                                        .setDefaultValue(true)
                                        .setSaveConsumer(
                                                        newValue -> MineLightsClient.CONFIG.enablePortalEffects = newValue)
                                        .build());

                        ConfigCategory aboutCategory = builder
                                        .getOrCreateCategory(
                                                        new TranslatableText("category.mine-lights.about").getString());

                        aboutCategory.addEntry(entryBuilder.startTextDescription(
                                        new TranslatableText("text.mine-lights.about.title").getString()).build());

                        aboutCategory.addEntry(entryBuilder.startTextDescription(
                                        new TranslatableText("text.mine-lights.about.version", "2.2.1").getString())
                                        .build());

                        aboutCategory.addEntry(entryBuilder.startTextDescription(
                                        new TranslatableText("text.mine-lights.about.copyright").getString()).build());

                        aboutCategory.addEntry(entryBuilder.startTextDescription("").build());

                        aboutCategory.addEntry(entryBuilder.startTextField(
                                        new TranslatableText("text.mine-lights.about.source_code").getString(),
                                        "https://github.com/megabytesme/MineLights").build());

                        aboutCategory.addEntry(entryBuilder.startTextField(
                                        new TranslatableText("text.mine-lights.about.issues").getString(),
                                        "https://github.com/megabytesme/MineLights/issues").build());

                        aboutCategory.addEntry(entryBuilder.startTextDescription("").build());

                        aboutCategory.addEntry(entryBuilder.startTextDescription(
                                        new TranslatableText("text.mine-lights.about.support_intro").getString())
                                        .build());

                        aboutCategory.addEntry(entryBuilder.startTextField(
                                        new TranslatableText("text.mine-lights.about.kofi").getString(),
                                        "https://ko-fi.com/megabytesme").build());

                        aboutCategory.addEntry(entryBuilder.startTextDescription("").build());

                        aboutCategory.addEntry(entryBuilder.startTextDescription(
                                        new TranslatableText("text.mine-lights.about.description").getString())
                                        .build());

                        return builder.build();
                };
        }
}