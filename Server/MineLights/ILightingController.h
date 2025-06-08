#pragma once
#include "LightingCommon.h"

class ILightingController {
public:
    virtual ~ILightingController() = default;
    virtual bool Initialize() = 0;
    virtual void Render(const FrameState& state) = 0;

    virtual std::vector<GenericLedId> GetAllLedIds() const = 0;
    virtual std::map<NamedKey, GenericLedId> GetNamedKeyMap() const = 0;
};