package megabytesme.minelights.network;

import megabytesme.minelights.config.ExperienceLocatorMode;
import megabytesme.minelights.config.MineLightsConfig;
import megabytesme.minelights.effects.KeyNameStandardizer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Command access to the configurable effect layout shared by every Java loader. */
final class EffectSettings {
    static final String[] NAMES = {
        "health-bar-keys", "hunger-bar-keys", "saturation-bar-keys", "experience-bar-keys",
        "locator-bar-keys", "heartbeat-red-keys", "heartbeat-white-keys", "compass-keys",
        "compass-center-key", "led-overrides", "outside-world-profile", "low-health-threshold",
        "saturation-tint-strength", "locator-dot-size", "experience-locator-mode",
        "health-color", "health-dim-color", "hunger-color", "hunger-dim-color",
        "saturation-color", "saturation-dim-color", "air-color", "air-dim-color",
        "experience-color", "experience-dim-color", "heartbeat-color", "absorption-color", "locator-bar-color"
    };

    private EffectSettings() {}

    static boolean contains(String name) { return Arrays.asList(NAMES).contains(name); }

    private static Field field(String name) {
        StringBuilder fieldName = new StringBuilder();
        boolean upper = false;
        for (char c : name.toCharArray()) {
            if (c == '-') upper = true;
            else { fieldName.append(upper ? Character.toUpperCase(c) : c); upper = false; }
        }
        try { return MineLightsConfig.class.getField(fieldName.toString()); }
        catch (NoSuchFieldException e) { throw new IllegalStateException("Unknown effect setting " + name, e); }
    }

    static String get(MineLightsConfig config, String name) {
        try {
            Object value = field(name).get(config);
            if (name.endsWith("-color")) return String.format(Locale.ROOT, "#%06X", ((Integer) value) & 0xFFFFFF);
            if (value instanceof List) return String.join(name.equals("led-overrides") ? "; " : ",", (List<String>) value);
            if (value instanceof Enum) return ((Enum<?>) value).name().toLowerCase(Locale.ROOT);
            return value == null ? "" : value.toString();
        } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }

    static void set(MineLightsConfig config, String name, String raw) {
        String value = raw.trim();
        Object parsed;
        if (name.endsWith("-color")) {
            if (!value.matches("#?[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Color must be a six-digit RGB value, e.g. #FF0080.");
            parsed = Integer.valueOf(value.replace("#", ""), 16);
        } else if (name.equals("experience-locator-mode")) {
            try { parsed = ExperienceLocatorMode.valueOf(value.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException e) { throw new IllegalArgumentException("Mode must be auto, experience, locator, or both."); }
        } else if (name.equals("low-health-threshold") || name.equals("saturation-tint-strength") || name.equals("locator-dot-size")) {
            float n;
            try { n = Float.parseFloat(value); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("Expected a number for " + name + "."); }
            float min = name.equals("locator-dot-size") ? 0.5F : 0F;
            float max = name.equals("saturation-tint-strength") ? 1F : name.equals("low-health-threshold") ? 20F : 10F;
            if (Float.isNaN(n) || Float.isInfinite(n) || n < min || n > max) throw new IllegalArgumentException(name + " must be between " + min + " and " + max + ".");
            parsed = Float.valueOf(n);
        } else if (name.endsWith("-keys") || name.equals("led-overrides")) {
            List<String> entries = new ArrayList<>();
            if (!value.isEmpty() && !value.equalsIgnoreCase("none")) {
                for (String entry : value.split(name.equals("led-overrides") ? ";" : ",")) {
                    entry = entry.trim();
                    if (!name.equals("led-overrides")) entry = KeyNameStandardizer.standardize(entry);
                    if (entry.isEmpty()) throw new IllegalArgumentException("List entries cannot be empty.");
                    if (name.equals("led-overrides") && !entry.matches(".+\\|[0-9]+=[^=]+")) throw new IllegalArgumentException("LED overrides use Device name|index=KEY, separated by semicolons.");
                    entries.add(entry);
                }
            }
            parsed = entries;
        } else if (name.equals("compass-center-key")) {
            parsed = value.equalsIgnoreCase("none") ? "" : KeyNameStandardizer.standardize(value);
        } else {
            parsed = value.equalsIgnoreCase("none") ? "" : value;
        }
        try { field(name).set(config, parsed); }
        catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }
}
