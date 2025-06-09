package megabytesme.minelights.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import megabytesme.minelights.MineLightsClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class ModMenuIntegration implements ModMenuApi {

        @Override
        public ConfigScreenFactory<?> getModConfigScreenFactory() {
                return parent -> {
                        ConfigBuilder builder = ConfigBuilder.create()
                                        .setParentScreen(parent)
                                        .setTitle(Text.translatable("title.mine-lights.config"));

                        builder.setSavingRunnable(() -> {
                                MineLightsClient.saveConfig();
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

                        general.addEntry(entryBuilder.startTextDescription(Text.literal("")).build());

                        general.addEntry(entryBuilder
                                        .startBooleanToggle(Text.translatable("option.mine-lights.refresh.label"),
                                                        MineLightsClient.CONFIG.forceRefresh)
                                        .setDefaultValue(false)
                                        .setTooltip(Text.translatable("option.mine-lights.refresh.tooltip"))
                                        .setSaveConsumer(newValue -> {
                                                if (newValue) {
                                                        MineLightsClient.refreshLightingManager();
                                                        if (MinecraftClient.getInstance().player != null) {
                                                                MinecraftClient.getInstance().player.sendMessage(
                                                                                Text.translatable(
                                                                                                "message.mine-lights.refresh.feedback"),
                                                                                false);
                                                        }
                                                        MineLightsClient.CONFIG.forceRefresh = false;
                                                }
                                        })
                                        .build());

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

                        playerStatus.addEntry(entryBuilder.startBooleanToggle(
                                        Text.translatable("option.mine-lights.enableLowHealthWarning"),
                                        MineLightsClient.CONFIG.enableLowHealthWarning)
                                        .setDefaultValue(true)
                                        .setSaveConsumer(
                                                        newValue -> MineLightsClient.CONFIG.enableLowHealthWarning = newValue)
                                        .build());
                        playerStatus.addEntry(entryBuilder.startBooleanToggle(
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

                        environment.addEntry(entryBuilder.startBooleanToggle(
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