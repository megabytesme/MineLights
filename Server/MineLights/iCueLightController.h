#pragma once

#include "ILightingController.h"
#include <unordered_set>

class iCueLightController : public ILightingController {
public:
    iCueLightController();
    ~iCueLightController() override;

    bool Initialize() override;
    void Start() override;
    void Stop() override;
    void UpdateData(const std::vector<CorsairLedColor>& colors) override;
    std::map<std::string, CorsairLedId> GetNamedKeyMap() const override;
    std::vector<DeviceInfo> GetConnectedDevices() const override;

private:
    void RenderLoop();
    mutable std::unordered_set<int> m_ownedLedIds;
};