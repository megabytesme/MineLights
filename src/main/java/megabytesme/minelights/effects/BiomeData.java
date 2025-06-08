package megabytesme.minelights.effects;

import java.util.HashMap;
import java.util.Map;

public class BiomeData {
    private static final Map<String, BiomeInfo> biomes = new HashMap<>();

    static {
        addBiome("minecraft:plains", true, false, 124, 252, 0);
        addBiome("minecraft:forest", true, false, 34, 139, 34);
        addBiome("minecraft:desert", false, false, 255, 255, 100);
        addBiome("minecraft:ocean", true, false, 0, 105, 148);
        addBiome("minecraft:jungle", true, false, 0, 100, 0);
        addBiome("minecraft:savanna", false, false, 230, 255, 30);
        addBiome("minecraft:badlands", false, false, 255, 60, 30);
        addBiome("minecraft:swamp", true, false, 32, 80, 10);
        addBiome("minecraft:taiga", true, true, 0, 100, 40);
        addBiome("minecraft:mushroom_fields", true, false, 100, 60, 100);
        addBiome("minecraft:beach", true, false, 255, 255, 100);
        addBiome("minecraft:river", true, false, 0, 191, 255);
        addBiome("minecraft:dark_forest", true, false, 0, 50, 0);
        addBiome("minecraft:birch_forest", true, false, 30, 120, 20);
        addBiome("minecraft:snowy_taiga", true, true, 255, 250, 250);
        addBiome("minecraft:savanna_plateau", false, false, 230, 255, 30);
        addBiome("minecraft:bamboo_jungle", true, false, 0, 100, 0);
        addBiome("minecraft:snowy_beach", true, true, 255, 250, 250);
        addBiome("minecraft:stony_shore", true, false, 169, 169, 169);
        addBiome("minecraft:warm_ocean", true, false, 0, 105, 148);
        addBiome("minecraft:lukewarm_ocean", true, false, 0, 105, 148);
        addBiome("minecraft:cold_ocean", true, false, 0, 105, 148);
        addBiome("minecraft:deep_ocean", true, false, 0, 40, 128);
        addBiome("minecraft:deep_lukewarm_ocean", true, false, 0, 60, 128);
        addBiome("minecraft:deep_cold_ocean", true, false, 0, 0, 128);
        addBiome("minecraft:deep_frozen_ocean", true, true, 150, 150, 255);
        addBiome("minecraft:the_void", false, false, 10, 10, 10);
        addBiome("minecraft:sunflower_plains", true, false, 200, 252, 0);
        addBiome("minecraft:snowy_plains", true, true, 255, 250, 250);
        addBiome("minecraft:ice_spikes", true, true, 173, 216, 230);
        addBiome("minecraft:mangrove_swamp", true, false, 34, 139, 34);
        addBiome("minecraft:flower_forest", true, false, 200, 252, 0);
        addBiome("minecraft:old_growth_birch_forest", true, false, 20, 110, 10);
        addBiome("minecraft:old_growth_pine_taiga", true, false, 0, 100, 0);
        addBiome("minecraft:old_growth_spruce_taiga", true, false, 34, 139, 34);
        addBiome("minecraft:sparse_jungle", true, false, 0, 100, 0);
        addBiome("minecraft:eroded_badlands", false, false, 210, 105, 30);
        addBiome("minecraft:wooded_badlands", false, false, 244, 164, 96);
        addBiome("minecraft:meadow", true, false, 124, 252, 0);
        addBiome("minecraft:cherry_grove", true, false, 255, 120, 120);
        addBiome("minecraft:grove", true, false, 200, 255, 200);
        addBiome("minecraft:snowy_slopes", true, true, 255, 250, 250);
        addBiome("minecraft:frozen_peaks", true, true, 173, 216, 230);
        addBiome("minecraft:jagged_peaks", true, false, 200, 255, 200);
        addBiome("minecraft:stony_peaks", true, false, 50, 50, 50);
        addBiome("minecraft:frozen_river", true, true, 255, 250, 250);
        addBiome("minecraft:dripstone_caves", true, false, 200, 100, 40);
        addBiome("minecraft:lush_caves", true, false, 70, 50, 50);
        addBiome("minecraft:deep_dark", true, false, 10, 10, 60);
        addBiome("minecraft:nether_wastes", false, false, 230, 30, 5);
        addBiome("minecraft:crimson_forest", false, false, 255, 20, 5);
        addBiome("minecraft:warped_forest", false, false, 0, 128, 128);
        addBiome("minecraft:soul_sand_valley", false, false, 194, 178, 128);
        addBiome("minecraft:basalt_deltas", false, false, 128, 128, 128);
        addBiome("minecraft:end_barrens", false, false, 128, 128, 128);
        addBiome("minecraft:end_highlands", false, false, 128, 128, 128);
        addBiome("minecraft:end_midlands", false, false, 128, 128, 128);
        addBiome("minecraft:small_end_islands", false, false, 128, 128, 128);
        addBiome("minecraft:the_end", false, false, 128, 128, 128);
    }

    private static void addBiome(String name, boolean hasRain, boolean isSnowy, int r, int g, int b) {
        biomes.put(name, new BiomeInfo(hasRain, isSnowy, new RGBColorDto(r, g, b)));
    }

    public static RGBColorDto getBiomeColor(String biomeName) {
        return biomes.getOrDefault(biomeName, new BiomeInfo(true, false, new RGBColorDto(124, 252, 0))).color;
    }

    public static boolean isBiomeRainy(String biomeName) {
        BiomeInfo info = biomes.get(biomeName);
        return info != null && info.hasRain && !info.isSnowy;
    }

    public static RGBColorDto getBiomeRainColor(String biomeName) {
        BiomeInfo info = biomes.get(biomeName);
        if (info != null && info.isSnowy) {
            return new RGBColorDto(200, 200, 255);
        }
        return new RGBColorDto(0, 50, 200);
    }

    private static class BiomeInfo {
        boolean hasRain;
        boolean isSnowy;
        RGBColorDto color;

        BiomeInfo(boolean hasRain, boolean isSnowy, RGBColorDto color) {
            this.hasRain = hasRain;
            this.isSnowy = isSnowy;
            this.color = color;
        }
    }
}