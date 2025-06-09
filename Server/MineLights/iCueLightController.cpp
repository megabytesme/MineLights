#define CORSAIR_LIGHTING_SDK_DISABLE_DEPRECATION_WARNINGS
#include <vector>
#include <map>
#include <string>
#include "CUESDK.h"
#include "iCueLightController.h"

bool iCueLightController::Initialize() {
    CorsairPerformProtocolHandshake();
    if (CorsairGetLastError() != CE_Success) {
        return false;
    }
    return CorsairRequestControl(CAM_ExclusiveLightingControl);
}

void iCueLightController::Render(const std::vector<CorsairLedColor>& colors) {
    if (!colors.empty()) {
        CorsairSetLedsColors(static_cast<int>(colors.size()), const_cast<CorsairLedColor*>(colors.data()));
    }
}

std::map<std::string, CorsairLedId> iCueLightController::GetNamedKeyMap() const {
    std::map<std::string, CorsairLedId> keyMap;
    keyMap["F1"] = CLK_F1; keyMap["F2"] = CLK_F2; keyMap["F3"] = CLK_F3;
    keyMap["F4"] = CLK_F4; keyMap["F5"] = CLK_F5; keyMap["F6"] = CLK_F6;
    keyMap["F7"] = CLK_F7; keyMap["F8"] = CLK_F8; keyMap["F9"] = CLK_F9;
    keyMap["F10"] = CLK_F10; keyMap["F11"] = CLK_F11; keyMap["F12"] = CLK_F12;
    keyMap["1"] = CLK_1; keyMap["2"] = CLK_2; keyMap["3"] = CLK_3; keyMap["4"] = CLK_4;
    keyMap["5"] = CLK_5; keyMap["6"] = CLK_6; keyMap["7"] = CLK_7; keyMap["8"] = CLK_8;
    keyMap["9"] = CLK_9; keyMap["0"] = CLK_0;
    keyMap["A"] = CLK_A; keyMap["B"] = CLK_B; keyMap["C"] = CLK_C; keyMap["D"] = CLK_D;
    keyMap["E"] = CLK_E; keyMap["F"] = CLK_F; keyMap["G"] = CLK_G; keyMap["H"] = CLK_H;
    keyMap["I"] = CLK_I; keyMap["J"] = CLK_J; keyMap["K"] = CLK_K; keyMap["L"] = CLK_L;
    keyMap["M"] = CLK_M; keyMap["N"] = CLK_N; keyMap["O"] = CLK_O; keyMap["P"] = CLK_P;
    keyMap["Q"] = CLK_Q; keyMap["R"] = CLK_R; keyMap["S"] = CLK_S; keyMap["T"] = CLK_T;
    keyMap["U"] = CLK_U; keyMap["V"] = CLK_V; keyMap["W"] = CLK_W; keyMap["X"] = CLK_X;
    keyMap["Y"] = CLK_Y; keyMap["Z"] = CLK_Z;
    keyMap["LSHIFT"] = CLK_LeftShift;   keyMap["RSHIFT"] = CLK_RightShift;
    keyMap["LCTRL"] = CLK_LeftCtrl;     keyMap["RCTRL"] = CLK_RightCtrl;
    keyMap["LALT"] = CLK_LeftAlt;       keyMap["RALT"] = CLK_RightAlt;
    keyMap["LGUI"] = CLK_LeftGui;       keyMap["RGUI"] = CLK_RightGui;
    keyMap["ENTER"] = CLK_Enter;
    keyMap["ESCAPE"] = CLK_Escape;
    keyMap["BACKSPACE"] = CLK_Backspace;
    keyMap["TAB"] = CLK_Tab;
    keyMap["SPACE"] = CLK_Space;
    keyMap["CAPS_LOCK"] = CLK_CapsLock;
    keyMap["MINUS"] = CLK_MinusAndUnderscore;
    keyMap["EQUAL"] = CLK_EqualsAndPlus;
    keyMap["LEFT_BRACKET"] = CLK_BracketLeft;
    keyMap["RIGHT_BRACKET"] = CLK_BracketRight;
    keyMap["BACKSLASH"] = CLK_Backslash;
    keyMap["SEMICOLON"] = CLK_SemicolonAndColon;
    keyMap["APOSTROPHE"] = CLK_ApostropheAndDoubleQuote;
    keyMap["GRAVE_ACCENT"] = CLK_GraveAccentAndTilde;
    keyMap["COMMA"] = CLK_CommaAndLessThan;
    keyMap["PERIOD"] = CLK_PeriodAndBiggerThan;
    keyMap["SLASH"] = CLK_SlashAndQuestionMark;
    keyMap["PRINT_SCREEN"] = CLK_PrintScreen;
    keyMap["SCROLL_LOCK"] = CLK_ScrollLock;
    keyMap["PAUSE"] = CLK_PauseBreak;
    keyMap["INSERT"] = CLK_Insert;
    keyMap["HOME"] = CLK_Home;
    keyMap["PAGE_UP"] = CLK_PageUp;
    keyMap["DELETE"] = CLK_Delete;
    keyMap["END"] = CLK_End;
    keyMap["PAGE_DOWN"] = CLK_PageDown;
    keyMap["UP"] = CLK_UpArrow;
    keyMap["DOWN"] = CLK_DownArrow;
    keyMap["LEFT"] = CLK_LeftArrow;
    keyMap["RIGHT"] = CLK_RightArrow;
    keyMap["NUM_LOCK"] = CLK_NumLock;
    keyMap["NUMPAD_DIVIDE"] = CLK_KeypadSlash;
    keyMap["NUMPAD_MULTIPLY"] = CLK_KeypadAsterisk;
    keyMap["NUMPAD_SUBTRACT"] = CLK_KeypadMinus;
    keyMap["NUMPAD_ADD"] = CLK_KeypadPlus;
    keyMap["NUMPAD_ENTER"] = CLK_KeypadEnter;
    keyMap["NUMPAD_1"] = CLK_Keypad1; keyMap["NUMPAD_2"] = CLK_Keypad2; keyMap["NUMPAD_3"] = CLK_Keypad3;
    keyMap["NUMPAD_4"] = CLK_Keypad4; keyMap["NUMPAD_5"] = CLK_Keypad5; keyMap["NUMPAD_6"] = CLK_Keypad6;
    keyMap["NUMPAD_7"] = CLK_Keypad7; keyMap["NUMPAD_8"] = CLK_Keypad8; keyMap["NUMPAD_9"] = CLK_Keypad9;
    keyMap["NUMPAD_0"] = CLK_Keypad0;
    keyMap["NUMPAD_DECIMAL"] = CLK_KeypadPeriodAndDelete;

    return keyMap;
}

std::vector<DeviceInfo> iCueLightController::GetConnectedDevices() const {
    std::vector<DeviceInfo> connectedDevices;
    const int deviceCount = CorsairGetDeviceCount();
    for (int i = 0; i < deviceCount; ++i) {
        CorsairDeviceInfo* deviceInfo = CorsairGetDeviceInfo(i);
        if (deviceInfo && deviceInfo->ledsCount > 0) {
            DeviceInfo newDevice;
            newDevice.sdk = "iCUE";
            newDevice.name = deviceInfo->model;

            CorsairLedPositions* ledPositions = CorsairGetLedPositionsByDeviceIndex(i);
            if (ledPositions) {
                newDevice.ledCount = ledPositions->numberOfLed;
                for (int j = 0; j < ledPositions->numberOfLed; ++j) {
                    newDevice.leds.push_back(ledPositions->pLedPosition[j].ledId);
                }
            }
            else {
                newDevice.ledCount = 0;
            }
            if (newDevice.ledCount > 0) {
                connectedDevices.push_back(newDevice);
            }
        }
    }
    return connectedDevices;
}