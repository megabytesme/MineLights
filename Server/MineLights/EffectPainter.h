#pragma once
#include "LightingCommon.h"

class EffectPainter {
public:
    EffectPainter(const std::vector<GenericLedId>& allKeys, const std::map<NamedKey, GenericLedId>& namedKeys);
    void Paint(FrameState& state);

private:
    void paintWorldEffects(FrameState& state);
    void paintExperienceBar(FrameState& state);
    void paintPlayerBars(FrameState& state);
    void paintHealthEffects(FrameState& state);
    void paintPlayerEffects(FrameState& state);

    std::vector<GenericLedId> allLedIds;
    std::map<NamedKey, GenericLedId> namedKeyMap;
};