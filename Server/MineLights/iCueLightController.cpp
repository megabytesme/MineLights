#define CORSAIR_LIGHTING_SDK_DISABLE_DEPRECATION_WARNINGS
#include "CUESDK.h"
#include "CorsairLedIdEnum.h"
#include "iCueLightController.h"

bool iCueLightController::Initialize() {
    CorsairPerformProtocolHandshake();
    if (CorsairGetLastError()) {
        return false;
    }
    CorsairRequestControl(CAM_ExclusiveLightingControl);
    return true;
}

void iCueLightController::Render(const FrameState& state) {
    std::vector<CorsairLedColor> colorsToSend;
    for (uint32_t i = 0; i < CLI_Last; ++i) {
        colorsToSend.push_back({
            static_cast<CorsairLedId>(i),
            state.background_color.r,
            state.background_color.g,
            state.background_color.b
            });
    }

    for (auto& led : colorsToSend) {
        if (state.key_colors.count(led.ledId)) {
            const auto& color = state.key_colors.at(led.ledId);
            led.r = color.r;
            led.g = color.g;
            led.b = color.b;
        }
    }

    if (!colorsToSend.empty()) {
        CorsairSetLedsColors(static_cast<int>(colorsToSend.size()), colorsToSend.data());
    }
}

std::vector<GenericLedId> iCueLightController::GetAllLedIds() const {
    std::vector<GenericLedId> ids;
    for (int i = 1; i < CLI_Last; ++i) {
        ids.push_back(static_cast<GenericLedId>(i));
    }
    return ids;
}

std::map<NamedKey, GenericLedId> iCueLightController::GetNamedKeyMap() const {
    std::map<NamedKey, GenericLedId> keyMap;
    keyMap[NamedKey::F1] = CLK_F1;
    keyMap[NamedKey::F2] = CLK_F2;
    keyMap[NamedKey::F3] = CLK_F3;
    keyMap[NamedKey::F4] = CLK_F4;
    keyMap[NamedKey::F5] = CLK_F5;
    keyMap[NamedKey::F6] = CLK_F6;
    keyMap[NamedKey::F7] = CLK_F7;
    keyMap[NamedKey::F8] = CLK_F8;
    keyMap[NamedKey::N1] = CLK_1;
    keyMap[NamedKey::N2] = CLK_2;
    keyMap[NamedKey::N3] = CLK_3;
    keyMap[NamedKey::N4] = CLK_4;
    keyMap[NamedKey::N5] = CLK_5;
    keyMap[NamedKey::N6] = CLK_6;
    keyMap[NamedKey::N7] = CLK_7;
    keyMap[NamedKey::N8] = CLK_8;
    keyMap[NamedKey::N9] = CLK_9;
    keyMap[NamedKey::N0] = CLK_0;
    keyMap[NamedKey::W] = CLK_W;
    keyMap[NamedKey::A] = CLK_A;
    keyMap[NamedKey::S] = CLK_S;
    keyMap[NamedKey::D] = CLK_D;
    keyMap[NamedKey::E] = CLK_E;
    keyMap[NamedKey::R] = CLK_R;
    keyMap[NamedKey::T] = CLK_T;
    keyMap[NamedKey::Y] = CLK_Y;
    keyMap[NamedKey::U] = CLK_U;
    keyMap[NamedKey::F] = CLK_F;
    keyMap[NamedKey::G] = CLK_G;
    keyMap[NamedKey::H] = CLK_H;
    keyMap[NamedKey::V] = CLK_V;
    keyMap[NamedKey::B] = CLK_B;
    keyMap[NamedKey::LeftControl] = CLK_LeftCtrl;
    keyMap[NamedKey::LeftShift] = CLK_LeftShift;
    keyMap[NamedKey::Space] = CLK_Space;
    return keyMap;
}