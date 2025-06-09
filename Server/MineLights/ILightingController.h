#pragma once

#include <vector>
#include <string>
#include <map>
#include <thread>
#include <mutex>
#include <condition_variable>
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
    virtual void Start() = 0;
    virtual void Stop() = 0;
    virtual void UpdateData(const std::vector<CorsairLedColor>& colors) = 0;
    virtual std::map<std::string, CorsairLedId> GetNamedKeyMap() const = 0;
    virtual std::vector<DeviceInfo> GetConnectedDevices() const = 0;

protected:
    std::thread m_renderThread;
    std::mutex m_mutex;
    std::condition_variable m_cv;
    std::vector<CorsairLedColor> m_colorBuffer;
    bool m_isRunning = false;
    bool m_newData = false;
};