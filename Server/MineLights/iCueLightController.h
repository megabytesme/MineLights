#pragma once

#include "ILightingController.h"
#include <unordered_set>
#include <map>

class iCueLightController : public ILightingController {
public:
    iCueLightController();
    ~iCueLightController() override;

    std::string GetSdkName() const override { return "iCUE"; }
    bool Initialize() override;
    void Start() override;
    void Stop() override;
    void UpdateData(const std::vector<CorsairLedColor>& colors) override;
    std::map<std::string, CorsairLedId> GetNamedKeyMap() const override;
    std::vector<DeviceInfo> GetConnectedDevices() const override;

private:
    void RenderLoop();
    mutable std::unordered_set<int> m_ownedLedIds;
    mutable std::map<int, CachedColor> m_lastSentColors;
};