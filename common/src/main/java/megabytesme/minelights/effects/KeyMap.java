package megabytesme.minelights.effects;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        return Arrays.asList("F9", "F10", "F11", "F12");
    }

    public static List<String> getFunctionKeys() {
        return Arrays.asList("F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12");
    }

    public static List<String> getNumberRow() {
        return Arrays.asList("BACKTICK", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "MINUS", "EQUALS");
    }

    public static List<String> getTopAlphabetRow() {
        return Arrays.asList("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", "LEFT_BRACKET", "RIGHT_BRACKET");
    }

    public static List<String> getHomeAlphabetRow() {
        return Arrays.asList("A", "S", "D", "F", "G", "H", "J", "K", "L", "SEMICOLON", "APOSTROPHE", "HASH");
    }

    public static List<String> getBottomAlphabetRow() {
        return Arrays.asList("ISO_BACKSLASH", "Z", "X", "C", "V", "B", "N", "M", "COMMA", "PERIOD", "SLASH");
    }

    public static List<String> getAlphabetKeys() {
        return Stream.of(getTopAlphabetRow(), getHomeAlphabetRow(), getBottomAlphabetRow())
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    public static List<String> getModifierKeys() {
        return Arrays.asList("LCTRL", "LWIN", "LALT", "RALT", "FN", "MENU", "RCTRL", "LSHIFT", "RSHIFT", "CAPSLOCK",
                "TAB");
    }

    public static List<String> getNavigationBlock() {
        return Arrays.asList("INSERT", "HOME", "PAGE_UP", "DELETE", "END", "PAGE_DOWN");
    }

    public static List<String> getArrowKeys() {
        return Arrays.asList("UP", "LEFT", "DOWN", "RIGHT");
    }

    public static List<String> getNumpad() {
        return Arrays.asList(
                "NUMLOCK", "NUMPAD_DIVIDE", "NUMPAD_MULTIPLY", "NUMPAD_SUBTRACT",
                "NUMPAD7", "NUMPAD8", "NUMPAD9", "NUMPAD_ADD",
                "NUMPAD4", "NUMPAD5", "NUMPAD6",
                "NUMPAD1", "NUMPAD2", "NUMPAD3", "NUMPAD_ENTER",
                "NUMPAD0", "NUMPAD_DECIMAL");
    }

    public static List<String> getNumpadDirectional() {
        return Arrays.asList("NUMPAD8", "NUMPAD9", "NUMPAD6", "NUMPAD3", "NUMPAD2", "NUMPAD1", "NUMPAD4", "NUMPAD7");
    }

    public static String getNumpadCenter() {
        return "NUMPAD5";
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

    public static List<String> getFullKeyboard() {
        return Stream.of(
                getFunctionKeys(),
                getNumberRow(),
                getAlphabetKeys(),
                getModifierKeys(),
                getNavigationBlock(),
                getArrowKeys(),
                getNumpad(),
                Arrays.asList("ESCAPE", "PRINTSCREEN", "SCROLLLOCK", "PAUSE", "BACKSPACE", "ENTER", "SPACE"))
                .flatMap(List::stream).distinct().collect(Collectors.toList());
    }

    public static final List<List<String>> KEYBOARD_ROWS = Arrays.asList(
            getFunctionKeys(),
            getNumberRow(),
            getTopAlphabetRow(),
            getHomeAlphabetRow(),
            getBottomAlphabetRow(),
            Arrays.asList("LCTRL", "LWIN", "LALT", "SPACE", "RALT", "FN", "MENU", "RCTRL"));

    public static final List<String> G_KEYS_COLUMN = Arrays.asList("G1", "G2", "G3", "G4", "G5", "G6");
    public static final List<String> NAV_COLUMN_1 = Arrays.asList("PRINTSCREEN", "DELETE", "LEFT");
    public static final List<String> NAV_COLUMN_2 = Arrays.asList("SCROLLLOCK", "END", "DOWN");
    public static final List<String> NAV_COLUMN_3 = Arrays.asList("PAUSE", "PAGE_DOWN", "RIGHT");
    public static final List<String> NUMPAD_COLUMN_1 = Arrays.asList("NUMLOCK", "NUMPAD7", "NUMPAD4", "NUMPAD1");
    public static final List<String> NUMPAD_COLUMN_2 = Arrays.asList("NUMPAD_DIVIDE", "NUMPAD8", "NUMPAD5", "NUMPAD2",
            "NUMPAD0");
    public static final List<String> NUMPAD_COLUMN_3 = Arrays.asList("NUMPAD_MULTIPLY", "NUMPAD9", "NUMPAD6", "NUMPAD3",
            "NUMPAD_DECIMAL");
    public static final List<String> NUMPAD_COLUMN_4 = Arrays.asList("NUMPAD_SUBTRACT", "NUMPAD_ADD", "NUMPAD_ENTER");
    public static final List<List<String>> KEYBOARD_COLUMNS = Arrays.asList(
            G_KEYS_COLUMN,
            NAV_COLUMN_1,
            NAV_COLUMN_2,
            NAV_COLUMN_3,
            NUMPAD_COLUMN_1,
            NUMPAD_COLUMN_2,
            NUMPAD_COLUMN_3,
            NUMPAD_COLUMN_4);
}