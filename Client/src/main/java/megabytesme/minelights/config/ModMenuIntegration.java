package megabytesme.minelights.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import megabytesme.minelights.MineLightsClient;
import net.minecraft.text.Text;

public class ModMenuIntegration implements ModMenuApi {

        @Override
        public ConfigScreenFactory<?> getModConfigScreenFactory() {
                return parent -> {
                        ConfigBuilder builder = ConfigBuilder.create()
                                        .setParentScreen(parent)
                                        .setTitle(Text.translatable("title.mine-lights.config"));

                        builder.setSavingRunnable(MineLightsClient::saveConfig);

                        ConfigCategory general = builder
                                        .getOrCreateCategory(Text.translatable("category.mine-lights.general"));
                        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

                        general.addEntry(entryBuilder
                                        .startBooleanToggle(Text.translatable("option.mine-lights.enableMod"),
                                                        MineLightsClient.CONFIG.enableMod)
                                        .setDefaultValue(true)
                                        .setTooltip(Text.translatable("option.mine-lights.enableMod.tooltip"))
                                        .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableMod = newValue)
                                        .build());

                        general.addEntry(entryBuilder
                                        .startBooleanToggle(Text.translatable("option.mine-lights.enableHealthBar"),
                                                        MineLightsClient.CONFIG.enableHealthBar)
                                        .setDefaultValue(true)
                                        .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableHealthBar = newValue)
                                        .build());

                        general.addEntry(entryBuilder
                                        .startBooleanToggle(Text.translatable("option.mine-lights.enableHungerBar"),
                                                        MineLightsClient.CONFIG.enableHungerBar)
                                        .setDefaultValue(true)
                                        .setSaveConsumer(newValue -> MineLightsClient.CONFIG.enableHungerBar = newValue)
                                        .build());

                        general.addEntry(entryBuilder
                                        .startBooleanToggle(Text.translatable("option.mine-lights.enableExperienceBar"),
                                                        MineLightsClient.CONFIG.enableExperienceBar)
                                        .setDefaultValue(true)
                                        .setSaveConsumer(
                                                        newValue -> MineLightsClient.CONFIG.enableExperienceBar = newValue)
                                        .build());

                        general.addEntry(entryBuilder
                                        .startBooleanToggle(
                                                        Text.translatable("option.mine-lights.enableLowHealthWarning"),
                                                        MineLightsClient.CONFIG.enableLowHealthWarning)
                                        .setDefaultValue(true)
                                        .setSaveConsumer(
                                                        newValue -> MineLightsClient.CONFIG.enableLowHealthWarning = newValue)
                                        .build());

                        general.addEntry(entryBuilder
                                        .startBooleanToggle(Text
                                                        .translatable("option.mine-lights.enableEnvironmentalEffects"),
                                                        MineLightsClient.CONFIG.enableEnvironmentalEffects)
                                        .setDefaultValue(true)
                                        .setTooltip(Text.translatable(
                                                        "option.mine-lights.enableEnvironmentalEffects.tooltip"))
                                        .setSaveConsumer(
                                                        newValue -> MineLightsClient.CONFIG.enableEnvironmentalEffects = newValue)
                                        .build());

                        return builder.build();
                };
        }
}