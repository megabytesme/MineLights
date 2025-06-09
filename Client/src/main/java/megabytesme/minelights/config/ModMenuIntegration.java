package megabytesme.minelights.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import megabytesme.minelights.CommandClient;
import megabytesme.minelights.MineLightsClient;
import net.minecraft.text.Text;
import java.util.Comparator;

public class ModMenuIntegration implements ModMenuApi {

        @Override
        public ConfigScreenFactory<?> getModConfigScreenFactory() {
                return parent -> {
                        ConfigBuilder builder = ConfigBuilder.create()
                                        .setParentScreen(parent)
                                        .setTitle(Text.translatable("title.mine-lights.config"));

                        builder.setSavingRunnable(() -> {
                                MineLightsClient.saveConfig();

                                boolean needsRefresh = false;

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
                                        } catch (InterruptedException e) {
                                        }
                                }

                                MineLightsClient.refreshLightingManager();
                        });

                        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

                        ConfigCategory general = builder
                                        .getOrCreateCategory(Text.translatable("category.mine-lights.general"));
                        general.addEntry(entryBuilder
                                        .startBooleanToggle(Text.translatable("option.mine-lights.enableMod"),
                                                        MineLightsClient.CONFIG.enableMod)
                                        .setDefaultValue(true)
                                        .setTooltip(Text.translatable("option.mine-lights.enableMod.tooltip"))
                                        .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableMod = newValue)
                                        .build());

                        if (MineLightsClient.IS_WINDOWS) {
                                general.addEntry(entryBuilder.startTextDescription(Text.literal("")).build());
                                general.addEntry(entryBuilder
                                                .startBooleanToggle(
                                                                Text.translatable("option.mine-lights.restart.label"),
                                                                MineLightsClient.CONFIG.restartProxy)
                                                .setDefaultValue(false)
                                                .setTooltip(Text.translatable("option.mine-lights.restart.tooltip"))
                                                .setSaveConsumer(
                                                                newValue -> MineLightsClient.CONFIG.restartProxy = newValue)
                                                .build());
                                general.addEntry(entryBuilder
                                                .startBooleanToggle(Text
                                                                .translatable("option.mine-lights.restart_admin.label"),
                                                                MineLightsClient.CONFIG.restartProxyAsAdmin)
                                                .setDefaultValue(false)
                                                .setTooltip(Text.translatable(
                                                                "option.mine-lights.restart_admin.tooltip"))
                                                .setSaveConsumer(
                                                                newValue -> MineLightsClient.CONFIG.restartProxyAsAdmin = newValue)
                                                .build());
                        }

                        ConfigCategory integrations = builder
                                        .getOrCreateCategory(Text.translatable("category.mine-lights.integrations"));
                        integrations.addEntry(entryBuilder
                                        .startBooleanToggle(Text.translatable("option.mine-lights.enableOpenRgb"),
                                                        MineLightsClient.CONFIG.enableOpenRgb)
                                        .setDefaultValue(true)
                                        .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableOpenRgb = newValue)
                                        .build());

                        if (MineLightsClient.IS_WINDOWS) {
                                integrations.addEntry(entryBuilder
                                                .startBooleanToggle(
                                                                Text.translatable("option.mine-lights.enableIcueProxy"),
                                                                MineLightsClient.CONFIG.enableIcueProxy)
                                                .setDefaultValue(true)
                                                .setSaveConsumer(
                                                                newValue -> MineLightsClient.CONFIG.enableIcueProxy = newValue)
                                                .build());

                                integrations.addEntry(entryBuilder
                                                .startBooleanToggle(Text.translatable(
                                                                "option.mine-lights.enableMysticLightProxy"),
                                                                MineLightsClient.CONFIG.enableMysticLightProxy)
                                                .setDefaultValue(true)
                                                .setSaveConsumer(
                                                                newValue -> MineLightsClient.CONFIG.enableMysticLightProxy = newValue)
                                                .build());
                        }

                        ConfigCategory devices = builder
                                        .getOrCreateCategory(Text.translatable("category.mine-lights.devices"));
                        devices.addEntry(entryBuilder
                                        .startTextDescription(Text.translatable("option.mine-lights.device.header"))
                                        .build());

                        MineLightsClient.discoveredDevices.stream()
                                        .sorted(Comparator.naturalOrder())
                                        .forEach(uniqueId -> {
                                                boolean isEnabled = !MineLightsClient.CONFIG.disabledDevices
                                                                .contains(uniqueId);
                                                String[] parts = uniqueId.split("\\|", 2);
                                                String deviceName = parts.length > 1 ? parts[1] : uniqueId;
                                                String deviceSdk = parts[0];

                                                devices.addEntry(entryBuilder.startBooleanToggle(Text
                                                                .literal(deviceName)
                                                                .append(Text.literal(" (" + deviceSdk + ")")
                                                                                .styled(s -> s.withColor(0xAAAAAA))),
                                                                isEnabled)
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
                                                                })
                                                                .build());
                                        });

                        if (!MineLightsClient.CONFIG.disabledDevices.isEmpty()) {
                                devices.addEntry(entryBuilder.startTextDescription(Text.literal("")).build());
                                devices.addEntry(entryBuilder
                                                .startBooleanToggle(Text.translatable(
                                                                "option.mine-lights.clear_disabled.label"),
                                                                MineLightsClient.CONFIG.clearDisabledDevices)
                                                .setDefaultValue(false)
                                                .setTooltip(Text.translatable(
                                                                "option.mine-lights.clear_disabled.tooltip"))
                                                .setSaveConsumer(
                                                                newValue -> MineLightsClient.CONFIG.clearDisabledDevices = newValue)
                                                .build());
                        }

                        ConfigCategory playerStatus = builder
                                        .getOrCreateCategory(Text.translatable("category.mine-lights.player_status"));
                        playerStatus.addEntry(entryBuilder
                                        .startBooleanToggle(Text.translatable("option.mine-lights.enableHealthBar"),
                                                        MineLightsClient.CONFIG.enableHealthBar)
                                        .setDefaultValue(true)
                                        .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableHealthBar = newValue)
                                        .build());
                        playerStatus.addEntry(entryBuilder
                                        .startBooleanToggle(Text.translatable("option.mine-lights.enableHungerBar"),
                                                        MineLightsClient.CONFIG.enableHungerBar)
                                        .setDefaultValue(true)
                                        .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableHungerBar = newValue)
                                        .build());
                        playerStatus.addEntry(entryBuilder
                                        .startBooleanToggle(Text.translatable("option.mine-lights.enableExperienceBar"),
                                                        MineLightsClient.CONFIG.enableExperienceBar)
                                        .setDefaultValue(true)
                                        .setSaveConsumer(
                                                        newValue -> MineLightsClient.CONFIG.enableExperienceBar = newValue)
                                        .build());
                        playerStatus.addEntry(entryBuilder
                                        .startBooleanToggle(
                                                        Text.translatable("option.mine-lights.enableLowHealthWarning"),
                                                        MineLightsClient.CONFIG.enableLowHealthWarning)
                                        .setDefaultValue(true)
                                        .setSaveConsumer(
                                                        newValue -> MineLightsClient.CONFIG.enableLowHealthWarning = newValue)
                                        .build());
                        playerStatus.addEntry(entryBuilder
                                        .startBooleanToggle(
                                                        Text.translatable("option.mine-lights.highlightMovementKeys"),
                                                        MineLightsClient.CONFIG.highlightMovementKeys)
                                        .setDefaultValue(true)
                                        .setTooltip(Text.translatable(
                                                        "option.mine-lights.highlightMovementKeys.tooltip"))
                                        .setSaveConsumer(
                                                        newValue -> MineLightsClient.CONFIG.highlightMovementKeys = newValue)
                                        .build());

                        ConfigCategory environment = builder
                                        .getOrCreateCategory(Text.translatable("category.mine-lights.environment"));
                        environment.addEntry(entryBuilder
                                        .startBooleanToggle(Text.translatable("option.mine-lights.enableBiomeEffects"),
                                                        MineLightsClient.CONFIG.enableBiomeEffects)
                                        .setDefaultValue(true)
                                        .setTooltip(Text.translatable("option.mine-lights.enableBiomeEffects.tooltip"))
                                        .setSaveConsumer(
                                                        newValue -> MineLightsClient.CONFIG.enableBiomeEffects = newValue)
                                        .build());
                        environment.addEntry(entryBuilder
                                        .startBooleanToggle(
                                                        Text.translatable("option.mine-lights.enableWeatherEffects"),
                                                        MineLightsClient.CONFIG.enableWeatherEffects)
                                        .setDefaultValue(true)
                                        .setTooltip(Text.translatable(
                                                        "option.mine-lights.enableWeatherEffects.tooltip"))
                                        .setSaveConsumer(
                                                        newValue -> MineLightsClient.CONFIG.enableWeatherEffects = newValue)
                                        .build());
                        environment.addEntry(entryBuilder
                                        .startBooleanToggle(Text.translatable("option.mine-lights.enableOnFireEffect"),
                                                        MineLightsClient.CONFIG.enableOnFireEffect)
                                        .setDefaultValue(true)
                                        .setSaveConsumer(
                                                        newValue -> MineLightsClient.CONFIG.enableOnFireEffect = newValue)
                                        .build());
                        environment.addEntry(entryBuilder
                                        .startBooleanToggle(Text.translatable("option.mine-lights.enableInWaterEffect"),
                                                        MineLightsClient.CONFIG.enableInWaterEffect)
                                        .setDefaultValue(true)
                                        .setTooltip(Text.translatable("option.mine-lights.enableInWaterEffect.tooltip"))
                                        .setSaveConsumer(
                                                        newValue -> MineLightsClient.CONFIG.enableInWaterEffect = newValue)
                                        .build());
                        environment.addEntry(entryBuilder
                                        .startBooleanToggle(Text.translatable("option.mine-lights.enablePortalEffects"),
                                                        MineLightsClient.CONFIG.enablePortalEffects)
                                        .setDefaultValue(true)
                                        .setSaveConsumer(
                                                        newValue -> MineLightsClient.CONFIG.enablePortalEffects = newValue)
                                        .build());

                        return builder.build();
                };
        }
}