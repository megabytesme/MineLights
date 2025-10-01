package megabytesme.minelights.effects;

import java.util.HashMap;
import java.util.Map;

public class KeyNameStandardizer {
    private static final Map<String, String> EXACT_MATCH_MAP = new HashMap<>();
    static {
        EXACT_MATCH_MAP.put("MEDIA_PLAY_PAUSE", "MEDIA_PLAY_PAUSE");
        EXACT_MATCH_MAP.put("MEDIA_STOP", "MEDIA_STOP");
        EXACT_MATCH_MAP.put("MEDIA_NEXT", "MEDIA_NEXT");
        EXACT_MATCH_MAP.put("MEDIA_PREVIOUS", "MEDIA_PREVIOUS");
        EXACT_MATCH_MAP.put("MEDIA_MUTE", "MEDIA_MUTE");
        EXACT_MATCH_MAP.put("WINLOCK", "WINLOCK");
    }

    public static String standardize(String originalName) {
        String upperName = originalName.toUpperCase();

        if (EXACT_MATCH_MAP.containsKey(upperName)) {
            return EXACT_MATCH_MAP.get(upperName);
        }

        if (upperName.startsWith("LEDSTRIPE")) {
            return "UNDERGLOW_" + upperName.substring(9);
        }
        if (upperName.startsWith("PROGRAMMABLE")) {
            return "G" + upperName.substring(12);
        }
        if (upperName.startsWith("CUSTOM")) {
            return "WHEEL_" + upperName.substring(6);
        }
        if (upperName.startsWith("DRAM")) {
            return "RAM_" + upperName.substring(4);
        }
        if (upperName.startsWith("KEY: ")) {
            upperName = upperName.substring(5);
        }
        if (upperName.startsWith("CORSAIR PRO LED ")) {
            return "RAM_" + (Integer.parseInt(upperName.substring(16)) + 1);
        }

        return upperName.replaceAll("\\s+", "_")
                        .replaceAll("[^A-Z0-9_]", "");
    }
}