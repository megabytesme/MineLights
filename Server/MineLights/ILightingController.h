#pragma once

#include <vector>
#include <string>
#include <map>
#include "CUESDK.h"

class ILightingController {
public:
    virtual ~ILightingController() = default;
    virtual bool Initialize() = 0;
    virtual void Render(const std::vector<CorsairLedColor>& colors) = 0;
    virtual std::map<std::string, CorsairLedId> GetNamedKeyMap() const = 0;
    virtual std::vector<CorsairLedId> GetAllLedIds() const = 0;
};