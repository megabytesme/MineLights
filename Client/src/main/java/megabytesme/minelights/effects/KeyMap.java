package megabytesme.minelights.effects;

import java.util.Arrays;
import java.util.List;

public class KeyMap {
    public static List<String> getExperienceBar() {
        return Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "0");
    }

    public static List<String> getHealthBar() {
        return Arrays.asList("F1", "F2", "F3", "F4");
    }

    public static List<String> getHungerBar() {
        return Arrays.asList("F5", "F6", "F7", "F8");
    }

    public static List<String> getSaturationBar() {
        return List.of("F9", "F10", "F11", "F12");
    }

    public static List<String> getMovementKeys() {
        return Arrays.asList("W", "A", "S", "D", "LCTRL", "LSHIFT", "SPACE");
    }

    public static List<String> getHeartbeatRed() {
        return Arrays.asList("4", "5", "6", "7", "E", "T", "U", "D", "F", "H", "V", "B", "SPACE");
    }

    public static List<String> getHeartbeatWhite() {
        return Arrays.asList("R", "Y", "G");
    }
}