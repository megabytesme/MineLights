#pragma once

#include "ILightingController.h"

class iCueLightController : public ILightingController {
public:
    bool Initialize() override;
    void Render(const std::vector<CorsairLedColor>& colors) override;
    std::map<std::string, CorsairLedId> GetNamedKeyMap() const override;
    std::vector<DeviceInfo> GetConnectedDevices() const override;
};