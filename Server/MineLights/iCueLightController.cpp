#define CORSAIR_LIGHTING_SDK_DISABLE_DEPRECATION_WARNINGS
#include <vector>
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
    keyMap["F1"] = CLK_F1; keyMap["F2"] = CLK_F2; keyMap["F3"] = CLK_F3; keyMap["F4"] = CLK_F4;
    keyMap["F5"] = CLK_F5; keyMap["F6"] = CLK_F6; keyMap["F7"] = CLK_F7; keyMap["F8"] = CLK_F8;
    keyMap["1"] = CLK_1; keyMap["2"] = CLK_2; keyMap["3"] = CLK_3; keyMap["4"] = CLK_4;
    keyMap["5"] = CLK_5; keyMap["6"] = CLK_6; keyMap["7"] = CLK_7; keyMap["8"] = CLK_8;
    keyMap["9"] = CLK_9; keyMap["0"] = CLK_0;
    keyMap["W"] = CLK_W; keyMap["A"] = CLK_A; keyMap["S"] = CLK_S; keyMap["D"] = CLK_D;
    keyMap["LCTRL"] = CLK_LeftCtrl; keyMap["LSHIFT"] = CLK_LeftShift; keyMap["SPACE"] = CLK_Space;
    keyMap["Q"] = CLK_Q; keyMap["E"] = CLK_E; keyMap["R"] = CLK_R; keyMap["T"] = CLK_T;
    keyMap["Y"] = CLK_Y; keyMap["U"] = CLK_U; keyMap["F"] = CLK_F; keyMap["G"] = CLK_G;
    keyMap["H"] = CLK_H; keyMap["V"] = CLK_V; keyMap["B"] = CLK_B;
    return keyMap;
}

std::vector<CorsairLedId> iCueLightController::GetAllLedIds() const {
    std::vector<CorsairLedId> ids;
    for (int i = 1; i < CLI_Last; ++i) {
        ids.push_back(static_cast<CorsairLedId>(i));
    }
    return ids;
}