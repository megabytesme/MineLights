#pragma once

#include <vector>
#include <string>
#include <map>
#include "CUESDK.h"

struct DeviceInfo {
    std::string sdk;
    std::string name;
    int ledCount;
    std::vector<int> leds;
};

class ILightingController {
public:
    virtual ~ILightingController() = default;
    virtual bool Initialize() = 0;
    virtual void Render(const std::vector<CorsairLedColor>& colors) = 0;
    virtual std::map<std::string, CorsairLedId> GetNamedKeyMap() const = 0;
    virtual std::vector<DeviceInfo> GetConnectedDevices() const = 0;
};