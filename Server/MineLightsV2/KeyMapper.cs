using RGB.NET.Core;

public static class KeyMapper
{
    private static readonly Dictionary<LedId, string> _map = new Dictionary<LedId, string>
    {
        { LedId.Keyboard_A, "A" }, { LedId.Keyboard_B, "B" }, { LedId.Keyboard_C, "C" },
        { LedId.Keyboard_D, "D" }, { LedId.Keyboard_E, "E" }, { LedId.Keyboard_F, "F" },
        { LedId.Keyboard_G, "G" }, { LedId.Keyboard_H, "H" }, { LedId.Keyboard_I, "I" },
        { LedId.Keyboard_J, "J" }, { LedId.Keyboard_K, "K" }, { LedId.Keyboard_L, "L" },
        { LedId.Keyboard_M, "M" }, { LedId.Keyboard_N, "N" }, { LedId.Keyboard_O, "O" },
        { LedId.Keyboard_P, "P" }, { LedId.Keyboard_Q, "Q" }, { LedId.Keyboard_R, "R" },
        { LedId.Keyboard_S, "S" }, { LedId.Keyboard_T, "T" }, { LedId.Keyboard_U, "U" },
        { LedId.Keyboard_V, "V" }, { LedId.Keyboard_W, "W" }, { LedId.Keyboard_X, "X" },
        { LedId.Keyboard_Y, "Y" }, { LedId.Keyboard_Z, "Z" },
        { LedId.Keyboard_1, "1" }, { LedId.Keyboard_2, "2" }, { LedId.Keyboard_3, "3" },
        { LedId.Keyboard_4, "4" }, { LedId.Keyboard_5, "5" }, { LedId.Keyboard_6, "6" },
        { LedId.Keyboard_7, "7" }, { LedId.Keyboard_8, "8" }, { LedId.Keyboard_9, "9" },
        { LedId.Keyboard_0, "0" },
        { LedId.Keyboard_F1, "F1" }, { LedId.Keyboard_F2, "F2" }, { LedId.Keyboard_F3, "F3" },
        { LedId.Keyboard_F4, "F4" }, { LedId.Keyboard_F5, "F5" }, { LedId.Keyboard_F6, "F6" },
        { LedId.Keyboard_F7, "F7" }, { LedId.Keyboard_F8, "F8" }, { LedId.Keyboard_F9, "F9" },
        { LedId.Keyboard_F10, "F10" }, { LedId.Keyboard_F11, "F11" }, { LedId.Keyboard_F12, "F12" },
        { LedId.Keyboard_LeftShift, "LSHIFT" }, { LedId.Keyboard_RightShift, "RSHIFT" },
        { LedId.Keyboard_LeftCtrl, "LCTRL" }, { LedId.Keyboard_RightCtrl, "RCTRL" },
        { LedId.Keyboard_LeftAlt, "LALT" }, { LedId.Keyboard_RightAlt, "RALT" },
        { LedId.Keyboard_LeftGui, "LWIN" }, { LedId.Keyboard_RightGui, "RWIN" },
        { LedId.Keyboard_Space, "SPACE" }, { LedId.Keyboard_Enter, "ENTER" },
        { LedId.Keyboard_Escape, "ESCAPE" }, { LedId.Keyboard_Backspace, "BACKSPACE" },
        { LedId.Keyboard_Tab, "TAB" }, { LedId.Keyboard_CapsLock, "CAPSLOCK" },
        { LedId.Keyboard_NumLock, "NUMLOCK" },
        { LedId.Keyboard_Num1, "NUMPAD1" }, { LedId.Keyboard_Num2, "NUMPAD2" }, { LedId.Keyboard_Num3, "NUMPAD3" },
        { LedId.Keyboard_Num4, "NUMPAD4" }, { LedId.Keyboard_Num5, "NUMPAD5" }, { LedId.Keyboard_Num6, "NUMPAD6" },
        { LedId.Keyboard_Num7, "NUMPAD7" }, { LedId.Keyboard_Num8, "NUMPAD8" }, { LedId.Keyboard_Num9, "NUMPAD9" },
        { LedId.Keyboard_Num0, "NUMPAD0" }, { LedId.Keyboard_NumSlash, "NUMPAD_DIVIDE" },
        { LedId.Keyboard_NumAsterisk, "NUMPAD_MULTIPLY" }, { LedId.Keyboard_NumMinus, "NUMPAD_SUBTRACT" },
        { LedId.Keyboard_NumPlus, "NUMPAD_ADD" }, { LedId.Keyboard_NumEnter, "NUMPAD_ENTER" },
        { LedId.Keyboard_NumPeriodAndDelete, "NUMPAD_DECIMAL" }, { LedId.Keyboard_ArrowUp, "UP" },
        { LedId.Keyboard_ArrowDown, "DOWN" }, { LedId.Keyboard_ArrowLeft, "LEFT" },
        { LedId.Keyboard_ArrowRight, "RIGHT" }, { LedId.Keyboard_Insert, "INSERT" },
        { LedId.Keyboard_Delete, "DELETE" }, { LedId.Keyboard_Home, "HOME" },
        { LedId.Keyboard_End, "END" }, { LedId.Keyboard_PageUp, "PAGE_UP" },
        { LedId.Keyboard_PageDown, "PAGE_DOWN" }, { LedId.Keyboard_PrintScreen, "PRINT_SCREEN" },
        { LedId.Keyboard_ScrollLock, "SCROLL_LOCK" }, { LedId.Keyboard_PauseBreak, "PAUSE" }
    };
    public static string? GetFriendlyName(LedId ledId) => _map.TryGetValue(ledId, out var name) ? name : null;
}