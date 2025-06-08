#pragma once
#include <map>
#include <vector>

struct RGBColor {
    int r = 0, g = 0, b = 0;
};

using GenericLedId = uint32_t;

enum class NamedKey {
    // Function Keys
    F1, F2, F3, F4, F5, F6, F7, F8,
    // Number Row
    N1, N2, N3, N4, N5, N6, N7, N8, N9, N0,
    // Movement
    W, A, S, D, LeftControl, LeftShift, Space,
    // Heartbeat Effect
    E, R, T, Y, U, F, G, H, V, B
};

// Represents the complete state for a single frame of lighting
struct FrameState {
    RGBColor background_color;
    std::map<GenericLedId, RGBColor> key_colors;
};