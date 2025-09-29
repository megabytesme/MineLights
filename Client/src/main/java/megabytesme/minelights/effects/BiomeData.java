package megabytesme.minelights.effects;

import java.util.HashMap;
import java.util.Map;

public class BiomeData {
    private static final Map<String, BiomeInfo> biomes = new HashMap<>();

    static {
        addBiome("minecraft:plains", true, false, 124, 252, 0);
        addBiome("minecraft:sunflower_plains", true, false, 200, 252, 0);
        addBiome("minecraft:snowy_tundra", true, true, 255, 250, 250);
        addBiome("minecraft:ice_spikes", true, true, 173, 216, 230);
        addBiome("minecraft:desert", false, false, 255, 255, 100);
        addBiome("minecraft:desert_lakes", false, false, 255, 255, 100);
        addBiome("minecraft:desert_hills", false, false, 255, 255, 100);
        addBiome("minecraft:forest", true, false, 34, 139, 34);
        addBiome("minecraft:flower_forest", true, false, 200, 252, 0);
        addBiome("minecraft:wooded_hills", true, false, 34, 139, 34);
        addBiome("minecraft:mountains", true, false, 130, 130, 130);
        addBiome("minecraft:wooded_mountains", true, false, 130, 130, 130);
        addBiome("minecraft:gravelly_mountains", true, false, 130, 130, 130);
        addBiome("minecraft:modified_gravelly_mountains", true, false, 130, 130, 130);
        addBiome("minecraft:snowy_mountains", true, true, 255, 250, 250);
        addBiome("minecraft:taiga", true, true, 0, 100, 40);
        addBiome("minecraft:taiga_hills", true, true, 0, 100, 40);
        addBiome("minecraft:taiga_mountains", true, true, 0, 100, 40);
        addBiome("minecraft:snowy_taiga", true, true, 255, 250, 250);
        addBiome("minecraft:snowy_taiga_hills", true, true, 255, 250, 250);
        addBiome("minecraft:snowy_taiga_mountains", true, true, 255, 250, 250);
        addBiome("minecraft:giant_tree_taiga", true, false, 0, 100, 0);
        addBiome("minecraft:giant_tree_taiga_hills", true, false, 0, 100, 0);
        addBiome("minecraft:giant_spruce_taiga", true, false, 34, 139, 34);
        addBiome("minecraft:giant_spruce_taiga_hills", true, false, 34, 139, 34);
        addBiome("minecraft:swamp", true, false, 32, 80, 10);
        addBiome("minecraft:swamp_hills", true, false, 32, 80, 10);
        addBiome("minecraft:ocean", true, false, 0, 105, 148);
        addBiome("minecraft:deep_ocean", true, false, 0, 40, 128);
        addBiome("minecraft:warm_ocean", true, false, 0, 105, 148);
        addBiome("minecraft:deep_warm_ocean", true, false, 0, 60, 128);
        addBiome("minecraft:lukewarm_ocean", true, false, 0, 105, 148);
        addBiome("minecraft:deep_lukewarm_ocean", true, false, 0, 60, 128);
        addBiome("minecraft:cold_ocean", true, false, 0, 105, 148);
        addBiome("minecraft:deep_cold_ocean", true, false, 0, 0, 128);
        addBiome("minecraft:frozen_ocean", true, true, 150, 150, 255);
        addBiome("minecraft:deep_frozen_ocean", true, true, 150, 150, 255);
        addBiome("minecraft:jungle", true, false, 0, 100, 0);
        addBiome("minecraft:jungle_hills", true, false, 0, 100, 0);
        addBiome("minecraft:jungle_edge", true, false, 0, 100, 0);
        addBiome("minecraft:modified_jungle", true, false, 0, 100, 0);
        addBiome("minecraft:modified_jungle_edge", true, false, 0, 100, 0);
        addBiome("minecraft:bamboo_jungle", true, false, 0, 100, 0);
        addBiome("minecraft:bamboo_jungle_hills", true, false, 0, 100, 0);
        addBiome("minecraft:savanna", false, false, 230, 255, 30);
        addBiome("minecraft:savanna_plateau", false, false, 230, 255, 30);
        addBiome("minecraft:shattered_savanna", false, false, 230, 255, 30);
        addBiome("minecraft:shattered_savanna_plateau", false, false, 230, 255, 30);
        addBiome("minecraft:badlands", false, false, 255, 60, 30);
        addBiome("minecraft:eroded_badlands", false, false, 210, 105, 30);
        addBiome("minecraft:wooded_badlands_plateau", false, false, 244, 164, 96);
        addBiome("minecraft:modified_wooded_badlands_plateau", false, false, 244, 164, 96);
        addBiome("minecraft:badlands_plateau", false, false, 255, 60, 30);
        addBiome("minecraft:modified_badlands_plateau", false, false, 255, 60, 30);
        addBiome("minecraft:mushroom_fields", true, false, 100, 60, 100);
        addBiome("minecraft:mushroom_field_shore", true, false, 100, 60, 100);
        addBiome("minecraft:beach", true, false, 255, 255, 100);
        addBiome("minecraft:snowy_beach", true, true, 255, 250, 250);
        addBiome("minecraft:stone_shore", true, false, 169, 169, 169);
        addBiome("minecraft:river", true, false, 0, 191, 255);
        addBiome("minecraft:frozen_river", true, true, 255, 250, 250);
        addBiome("minecraft:dark_forest", true, false, 0, 50, 0);
        addBiome("minecraft:dark_forest_hills", true, false, 0, 50, 0);
        addBiome("minecraft:birch_forest", true, false, 30, 120, 20);
        addBiome("minecraft:birch_forest_hills", true, false, 30, 120, 20);
        addBiome("minecraft:tall_birch_forest", true, false, 30, 120, 20);
        addBiome("minecraft:tall_birch_hills", true, false, 30, 120, 20);
        addBiome("minecraft:mountain_edge", true, false, 130, 130, 130);
        addBiome("minecraft:nether", false, false, 230, 30, 5);
        addBiome("minecraft:the_end", false, false, 128, 128, 128);
        addBiome("minecraft:end_highlands", false, false, 128, 128, 128);
        addBiome("minecraft:end_midlands", false, false, 128, 128, 128);
        addBiome("minecraft:small_end_islands", false, false, 128, 128, 128);
        addBiome("minecraft:end_barrens", false, false, 128, 128, 128);
        addBiome("minecraft:the_void", false, false, 10, 10, 10);
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