#pragma once
#include "ILightingController.h"
#include "CUESDK.h"

class iCueLightController : public ILightingController {
public:
    bool Initialize() override;
    void Render(const FrameState& state) override;
    std::vector<GenericLedId> GetAllLedIds() const override;
    std::map<NamedKey, GenericLedId> GetNamedKeyMap() const override;
};