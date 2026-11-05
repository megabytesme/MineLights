package megabytesme.minelights.effects;

import java.util.HashMap;
import java.util.Map;

public class KeyNames {
    private static final Map<Integer, String> GLFW_NAMES = new HashMap<>();

    static {
        for (int c = 'A'; c <= 'Z'; c++) {
            GLFW_NAMES.put(c, String.valueOf((char) c));
        }
        for (int c = '0'; c <= '9'; c++) {
            GLFW_NAMES.put(c, String.valueOf((char) c));
        }
        for (int i = 1; i <= 25; i++) {
            GLFW_NAMES.put(289 + i, "F" + i);
        }
        for (int i = 0; i <= 9; i++) {
            GLFW_NAMES.put(320 + i, "NUMPAD" + i);
        }

        GLFW_NAMES.put(32, "SPACE");
        GLFW_NAMES.put(39, "APOSTROPHE");
        GLFW_NAMES.put(44, "COMMA");
        GLFW_NAMES.put(45, "MINUS");
        GLFW_NAMES.put(46, "PERIOD");
        GLFW_NAMES.put(47, "SLASH");
        GLFW_NAMES.put(59, "SEMICOLON");
        GLFW_NAMES.put(61, "EQUALS");
        GLFW_NAMES.put(91, "LEFT_BRACKET");
        GLFW_NAMES.put(92, "BACKSLASH");
        GLFW_NAMES.put(93, "RIGHT_BRACKET");
        GLFW_NAMES.put(96, "BACKTICK");
        GLFW_NAMES.put(161, "ISO_BACKSLASH");
        GLFW_NAMES.put(162, "ISO_BACKSLASH");

        GLFW_NAMES.put(256, "ESCAPE");
        GLFW_NAMES.put(257, "ENTER");
        GLFW_NAMES.put(258, "TAB");
        GLFW_NAMES.put(259, "BACKSPACE");
        GLFW_NAMES.put(260, "INSERT");
        GLFW_NAMES.put(261, "DELETE");
        GLFW_NAMES.put(262, "RIGHT");
        GLFW_NAMES.put(263, "LEFT");
        GLFW_NAMES.put(264, "DOWN");
        GLFW_NAMES.put(265, "UP");
        GLFW_NAMES.put(266, "PAGE_UP");
        GLFW_NAMES.put(267, "PAGE_DOWN");
        GLFW_NAMES.put(268, "HOME");
        GLFW_NAMES.put(269, "END");
        GLFW_NAMES.put(280, "CAPSLOCK");
        GLFW_NAMES.put(281, "SCROLLLOCK");
        GLFW_NAMES.put(282, "NUMLOCK");
        GLFW_NAMES.put(283, "PRINTSCREEN");
        GLFW_NAMES.put(284, "PAUSE");

        GLFW_NAMES.put(330, "NUMPAD_DECIMAL");
        GLFW_NAMES.put(331, "NUMPAD_DIVIDE");
        GLFW_NAMES.put(332, "NUMPAD_MULTIPLY");
        GLFW_NAMES.put(333, "NUMPAD_SUBTRACT");
        GLFW_NAMES.put(334, "NUMPAD_ADD");
        GLFW_NAMES.put(335, "NUMPAD_ENTER");

        GLFW_NAMES.put(340, "LSHIFT");
        GLFW_NAMES.put(341, "LCTRL");
        GLFW_NAMES.put(342, "LALT");
        GLFW_NAMES.put(343, "LWIN");
        GLFW_NAMES.put(344, "RSHIFT");
        GLFW_NAMES.put(345, "RCTRL");
        GLFW_NAMES.put(346, "RALT");
        GLFW_NAMES.put(347, "RWIN");
        GLFW_NAMES.put(348, "MENU");
    }

    public static String fromGlfw(int keyCode) {
        return GLFW_NAMES.get(keyCode);
    }
}
