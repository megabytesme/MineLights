package megabytesme.minelights.effects;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class BiomeData {
    private static final Map<String, BiomeInfo> biomes = new HashMap<>();

    static {
        addBiome("BADLANDS", false, false, 204, 85, 0);
        addBiome("BADLANDS_PLATEAU", false, false, 204, 85, 0);
        addBiome("BAMBOO_JUNGLE", true, false, 44, 122, 29);
        addBiome("BAMBOO_JUNGLE_HILLS", true, false, 44, 122, 29);
        addBiome("BASALT_DELTAS", false, false, 64, 54, 54);
        addBiome("BEACH", true, false, 200, 200, 122);
        addBiome("BIRCH_FOREST", true, false, 78, 170, 96);
        addBiome("BIRCH_FOREST_HILLS", true, false, 78, 170, 96);
        addBiome("CHERRY_GROVE", true, false, 255, 112, 216);
        addBiome("COLD_OCEAN", true, false, 48, 94, 128);
        addBiome("CRIMSON_FOREST", false, false, 221, 8, 8);
        addBiome("DARK_FOREST", true, false, 42, 67, 36);
        addBiome("DARK_FOREST_HILLS", true, false, 42, 67, 36);
        addBiome("DEEP_COLD_OCEAN", true, false, 5, 46, 73);
        addBiome("DEEP_DARK", false, false, 10, 10, 20);
        addBiome("DEEP_FROZEN_OCEAN", true, true, 116, 150, 182);
        addBiome("DEEP_LUKEWARM_OCEAN", true, false, 17, 32, 61);
        addBiome("DEEP_OCEAN", true, false, 10, 19, 36);
        addBiome("DEEP_WARM_OCEAN", true, false, 22, 41, 79);
        addBiome("DEFAULT", false, false, 255, 0, 255);
        addBiome("DESERT", false, false, 255, 204, 132);
        addBiome("DESERT_HILLS", false, false, 255, 204, 132);
        addBiome("DESERT_LAKES", false, false, 255, 204, 132);
        addBiome("DRIPSTONE_CAVES", false, false, 152, 106, 69);
        addBiome("END_BARRENS", false, false, 244, 244, 178);
        addBiome("END_HIGHLANDS", false, false, 177, 149, 200);
        addBiome("END_MIDLANDS", false, false, 192, 166, 211);
        addBiome("ERODED_BADLANDS", false, false, 204, 85, 0);
        addBiome("FLOWER_FOREST", true, false, 34, 139, 34);
        addBiome("FOREST", true, false, 34, 139, 34);
        addBiome("FROZEN_OCEAN", true, true, 169, 208, 245);
        addBiome("FROZEN_PEAKS", true, true, 213, 226, 231);
        addBiome("FROZEN_RIVER", true, true, 176, 224, 230);
        addBiome("GIANT_SPRUCE_TAIGA", true, false, 74, 68, 33);
        addBiome("GIANT_SPRUCE_TAIGA_HILLS", true, false, 74, 68, 33);
        addBiome("GIANT_TREE_TAIGA", true, false, 74, 68, 33);
        addBiome("GIANT_TREE_TAIGA_HILLS", true, false, 74, 68, 33);
        addBiome("GRAVELLY_MOUNTAINS", true, false, 134, 134, 134);
        addBiome("GROVE", true, true, 196, 196, 196);
        addBiome("ICE_SPIKES", true, true, 173, 216, 230);
        addBiome("JAGGED_PEAKS", true, true, 221, 221, 221);
        addBiome("JUNGLE", true, false, 72, 156, 44);
        addBiome("JUNGLE_EDGE", true, false, 86, 157, 50);
        addBiome("JUNGLE_HILLS", true, false, 72, 156, 44);
        addBiome("LUKEWARM_OCEAN", true, false, 43, 65, 109);
        addBiome("LUSH_CAVES", false, false, 76, 122, 62);
        addBiome("MANGROVE_SWAMP", true, false, 89, 103, 72);
        addBiome("MEADOW", true, false, 65, 134, 101);
        addBiome("MODIFIED_BADLANDS_PLATEAU", false, false, 204, 85, 0);
        addBiome("MODIFIED_GRAVELLY_MOUNTAINS", true, false, 134, 134, 134);
        addBiome("MODIFIED_JUNGLE", true, false, 27, 153, 27);
        addBiome("MODIFIED_JUNGLE_EDGE", true, false, 27, 153, 27);
        addBiome("MODIFIED_WOODED_BADLANDS_PLATEAU", false, false, 204, 85, 0);
        addBiome("MOUNTAIN_EDGE", true, false, 87, 115, 107);
        addBiome("MOUNTAINS", true, false, 34, 139, 34);
        addBiome("MUSHROOM_FIELD_SHORE", true, false, 117, 81, 128);
        addBiome("MUSHROOM_FIELDS", true, false, 156, 110, 170);
        addBiome("NETHER", false, false, 191, 59, 59);
        addBiome("NETHER_WASTES", false, false, 191, 59, 59);
        addBiome("OCEAN", true, false, 36, 53, 91);
        addBiome("OLD_GROWTH_BIRCH_FOREST", true, false, 136, 255, 136);
        addBiome("OLD_GROWTH_PINE_TAIGA", true, false, 74, 68, 33);
        addBiome("OLD_GROWTH_SPRUCE_TAIGA", true, false, 74, 68, 33);
        addBiome("PALE_GARDEN", true, false, 170, 170, 170);
        addBiome("PLAINS", true, false, 130, 159, 66);
        addBiome("RIVER", true, false, 79, 148, 205);
        addBiome("SAVANNA", false, false, 130, 145, 63);
        addBiome("SAVANNA_PLATEAU", false, false, 130, 145, 63);
        addBiome("SHATTERED_SAVANNA", false, false, 130, 145, 63);
        addBiome("SHATTERED_SAVANNA_PLATEAU", false, false, 130, 145, 63);
        addBiome("SMALL_END_ISLANDS", false, false, 128, 0, 128);
        addBiome("SNOWY_BEACH", true, true, 200, 200, 122);
        addBiome("SNOWY_MOUNTAINS", true, true, 224, 224, 224);
        addBiome("SNOWY_PLAINS", true, true, 238, 238, 238);
        addBiome("SNOWY_SLOPES", true, true, 238, 238, 238);
        addBiome("SNOWY_TAIGA", true, true, 224, 224, 224);
        addBiome("SNOWY_TAIGA_HILLS", true, true, 255, 255, 255);
        addBiome("SNOWY_TAIGA_MOUNTAINS", true, true, 255, 255, 255);
        addBiome("SNOWY_TUNDRA", true, true, 224, 224, 224);
        addBiome("SOUL_SAND_VALLEY", false, false, 77, 58, 48);
        addBiome("SPARSE_JUNGLE", true, false, 86, 157, 50);
        addBiome("STONE_SHORE", true, false, 134, 134, 134);
        addBiome("STONY_PEAKS", true, false, 134, 134, 134);
        addBiome("STONY_SHORE", true, false, 134, 134, 134);
        addBiome("SUNFLOWER_PLAINS", true, false, 130, 159, 66);
        addBiome("SWAMP", true, false, 89, 103, 72);
        addBiome("SWAMP_HILLS", true, false, 85, 102, 68);
        addBiome("TAIGA", true, true, 11, 106, 95);
        addBiome("TAIGA_HILLS", true, true, 11, 106, 95);
        addBiome("TAIGA_MOUNTAINS", true, true, 11, 106, 95);
        addBiome("TALL_BIRCH_FOREST", true, false, 78, 170, 96);
        addBiome("TALL_BIRCH_HILLS", true, false, 78, 170, 96);
        addBiome("THE_END", false, false, 128, 0, 128);
        addBiome("THE_VOID", false, false, 0, 0, 0);
        addBiome("WARM_OCEAN", true, false, 59, 88, 146);
        addBiome("WARPED_FOREST", false, false, 73, 144, 123);
        addBiome("WINDSWEPT_FOREST", true, false, 71, 97, 89);
        addBiome("WINDSWEPT_GRAVELLY_HILLS", true, false, 134, 134, 134);
        addBiome("WINDSWEPT_HILLS", true, false, 87, 115, 107);
        addBiome("WINDSWEPT_SAVANNA", false, false, 130, 145, 63);
        addBiome("WOODED_BADLANDS", false, false, 204, 85, 0);
        addBiome("WOODED_BADLANDS_PLATEAU", false, false, 204, 85, 0);
        addBiome("WOODED_HILLS", true, false, 34, 139, 34);
        addBiome("WOODED_MOUNTAINS", true, false, 34, 139, 34);
    }

    private static void addBiome(String name, boolean hasRain, boolean isSnowy, int r, int g, int b) {
        biomes.put(name, new BiomeInfo(hasRain, isSnowy, new RGBColorDto(r, g, b)));
    }

    private static String normaliseKey(String biomeName) {
        if (biomeName == null) return "";
        int colonIndex = biomeName.indexOf(':');
        if (colonIndex != -1) {
            biomeName = biomeName.substring(colonIndex + 1);
        }

        return biomeName.trim().toUpperCase(Locale.ROOT);
    }

    private static BiomeInfo findBiomeInfo(String biomeName) {
        String key = normaliseKey(biomeName);
        return biomes.get(key);
    }

    public static RGBColorDto getBiomeColor(String biomeName) {
        BiomeInfo info = findBiomeInfo(biomeName);
        return info != null ? info.color : new RGBColorDto(124, 252, 0);
    }

    public static boolean isBiomeRainy(String biomeName) {
        BiomeInfo info = findBiomeInfo(biomeName);
        return info != null && info.hasRain && !info.isSnowy;
    }

    public static RGBColorDto getBiomeRainColor(String biomeName) {
        BiomeInfo info = findBiomeInfo(biomeName);
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