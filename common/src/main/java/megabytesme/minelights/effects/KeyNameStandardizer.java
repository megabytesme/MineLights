package megabytesme.minelights.effects;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KeyNameStandardizer {
    private static final Map<String, String> EXACT_MATCH_MAP = new HashMap<>();
    private static final Pattern TRAILING_NUMBER_PATTERN = Pattern.compile("\\d+$");

    static {
        EXACT_MATCH_MAP.put("MEDIA_PLAY_PAUSE", "MEDIA_PLAY_PAUSE");
        EXACT_MATCH_MAP.put("MEDIA_STOP", "MEDIA_STOP");
        EXACT_MATCH_MAP.put("MEDIA_NEXT", "MEDIA_NEXT");
        EXACT_MATCH_MAP.put("MEDIA_PREVIOUS", "MEDIA_PREVIOUS");
        EXACT_MATCH_MAP.put("MEDIA_MUTE", "MEDIA_MUTE");
        EXACT_MATCH_MAP.put("WINLOCK", "WINLOCK");
    }

    public static String standardize(String originalName) {
        if (originalName == null || originalName.isEmpty()) {
            return "";
        }

        String upperName = originalName.toUpperCase().trim();

        upperName = upperName.replace("CONTROL", "CTRL");
        upperName = upperName.replace("WINDOWS", "WIN");

        if (upperName.startsWith("KEY: ")) {
            upperName = upperName.substring(5);
        }

        if (EXACT_MATCH_MAP.containsKey(upperName)) {
            return EXACT_MATCH_MAP.get(upperName);
        }

        Matcher matcher = TRAILING_NUMBER_PATTERN.matcher(upperName);
        String numberSuffix = "";
        if (matcher.find()) {
            numberSuffix = "_" + matcher.group(0);
        }

        if (upperName.contains("DRAM") || upperName.contains("CORSAIR PRO LED") || upperName.contains("RAM")) {
            if (upperName.contains("CORSAIR PRO LED")) {
                int num = Integer.parseInt(numberSuffix.substring(1)) + 1;
                return "RAM_" + num;
            }
            return "RAM" + numberSuffix;
        }
        if (upperName.contains("LEDSTRIPE") || upperName.contains("UNDERGLOW")) {
            return "UNDERGLOW" + numberSuffix;
        }
        if (upperName.contains("PROGRAMMABLE")) {
            return "G" + numberSuffix.replace("_", "");
        }
        if (upperName.contains("CUSTOM") || upperName.contains("WHEEL")) {
            return "WHEEL" + numberSuffix;
        }
        if (upperName.contains("PLAYER")) {
            return "PLAYER" + numberSuffix;
        }

        return upperName.replaceAll("\\s+", "_")
                .replaceAll("[^A-Z0-9_]", "");
    }
}