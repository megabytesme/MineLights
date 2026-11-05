package megabytesme.minelights.effects;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KeyNameStandardizer {
    private static final Map<String, String> EXACT_MATCH_MAP = new HashMap<>();
    private static final Map<String, String> SYMBOL_MAP = new HashMap<>();
    private static final Map<String, String> ALIAS_MAP = new HashMap<>();
    private static final Pattern TRAILING_NUMBER_PATTERN = Pattern.compile("\\d+$");

    static {
        EXACT_MATCH_MAP.put("MEDIA_PLAY_PAUSE", "MEDIA_PLAY_PAUSE");
        EXACT_MATCH_MAP.put("MEDIA_STOP", "MEDIA_STOP");
        EXACT_MATCH_MAP.put("MEDIA_NEXT", "MEDIA_NEXT");
        EXACT_MATCH_MAP.put("MEDIA_PREVIOUS", "MEDIA_PREVIOUS");
        EXACT_MATCH_MAP.put("MEDIA_MUTE", "MEDIA_MUTE");
        EXACT_MATCH_MAP.put("WINLOCK", "WINLOCK");

        SYMBOL_MAP.put("`", "BACKTICK");
        SYMBOL_MAP.put("-", "MINUS");
        SYMBOL_MAP.put("=", "EQUALS");
        SYMBOL_MAP.put("[", "LEFT_BRACKET");
        SYMBOL_MAP.put("]", "RIGHT_BRACKET");
        SYMBOL_MAP.put("\\", "BACKSLASH");
        SYMBOL_MAP.put("\\ (ANSI)", "BACKSLASH");
        SYMBOL_MAP.put("\\ (ISO)", "ISO_BACKSLASH");
        SYMBOL_MAP.put("#", "HASH");
        SYMBOL_MAP.put("# (ISO)", "HASH");
        SYMBOL_MAP.put(";", "SEMICOLON");
        SYMBOL_MAP.put("'", "APOSTROPHE");
        SYMBOL_MAP.put(",", "COMMA");
        SYMBOL_MAP.put(".", "PERIOD");
        SYMBOL_MAP.put("/", "SLASH");

        ALIAS_MAP.put("LEFT_CTRL", "LCTRL");
        ALIAS_MAP.put("RIGHT_CTRL", "RCTRL");
        ALIAS_MAP.put("LEFT_SHIFT", "LSHIFT");
        ALIAS_MAP.put("RIGHT_SHIFT", "RSHIFT");
        ALIAS_MAP.put("LEFT_ALT", "LALT");
        ALIAS_MAP.put("RIGHT_ALT", "RALT");
        ALIAS_MAP.put("LEFT_WIN", "LWIN");
        ALIAS_MAP.put("RIGHT_WIN", "RWIN");
        ALIAS_MAP.put("LEFT_SUPER", "LWIN");
        ALIAS_MAP.put("LEFT_FN", "FN");
        ALIAS_MAP.put("RIGHT_FN", "FN");
        ALIAS_MAP.put("CAPS_LOCK", "CAPSLOCK");
        ALIAS_MAP.put("NUM_LOCK", "NUMLOCK");
        ALIAS_MAP.put("SCROLL_LOCK", "SCROLLLOCK");
        ALIAS_MAP.put("PRINT_SCREEN", "PRINTSCREEN");
        ALIAS_MAP.put("UP_ARROW", "UP");
        ALIAS_MAP.put("DOWN_ARROW", "DOWN");
        ALIAS_MAP.put("LEFT_ARROW", "LEFT");
        ALIAS_MAP.put("RIGHT_ARROW", "RIGHT");
        ALIAS_MAP.put("GRAVE", "BACKTICK");
        ALIAS_MAP.put("GRAVE_ACCENT", "BACKTICK");
        ALIAS_MAP.put("ESC", "ESCAPE");
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

        if (SYMBOL_MAP.containsKey(upperName)) {
            return SYMBOL_MAP.get(upperName);
        }

        if (upperName.startsWith("NUMBER PAD ")) {
            return standardizeNumpad(upperName.substring(11).trim());
        }

        if (EXACT_MATCH_MAP.containsKey(upperName)) {
            return EXACT_MATCH_MAP.get(upperName);
        }

        Matcher matcher = TRAILING_NUMBER_PATTERN.matcher(upperName);
        String numberSuffix = "";
        if (matcher.find()) {
            numberSuffix = "_" + matcher.group(0);
        }

        if (upperName.contains("PROGRAMMABLE")) {
            return "G" + numberSuffix.replace("_", "");
        }
        if (upperName.contains("DRAM") || upperName.contains("CORSAIR PRO LED") || upperName.contains("RAM")) {
            if (upperName.contains("CORSAIR PRO LED") && !numberSuffix.isEmpty()) {
                int num = Integer.parseInt(numberSuffix.substring(1)) + 1;
                return "RAM_" + num;
            }
            return "RAM" + numberSuffix;
        }
        if (upperName.contains("LEDSTRIPE") || upperName.contains("UNDERGLOW")) {
            return "UNDERGLOW" + numberSuffix;
        }
        if (upperName.contains("CUSTOM") || upperName.contains("WHEEL")) {
            return "WHEEL" + numberSuffix;
        }
        if (upperName.contains("PLAYER")) {
            return "PLAYER" + numberSuffix;
        }

        String normalized = upperName.replaceAll("\\s+", "_")
                .replaceAll("[^A-Z0-9_]", "");
        return ALIAS_MAP.getOrDefault(normalized, normalized);
    }

    private static String standardizeNumpad(String key) {
        switch (key) {
            case "/":
                return "NUMPAD_DIVIDE";
            case "*":
                return "NUMPAD_MULTIPLY";
            case "-":
                return "NUMPAD_SUBTRACT";
            case "+":
                return "NUMPAD_ADD";
            case ".":
                return "NUMPAD_DECIMAL";
            case "ENTER":
                return "NUMPAD_ENTER";
            default:
                return "NUMPAD" + key.replaceAll("[^A-Z0-9]", "");
        }
    }
}
