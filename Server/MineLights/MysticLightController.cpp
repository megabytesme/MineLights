#include "MysticLightController.h"
#include "Logger.h"
#include <vector>
#include <string>
#include <atlbase.h>
#include <unordered_map>

MysticLightController::MysticLightController() : m_hMsiSdk(nullptr), m_isInitialized(false) {}

MysticLightController::~MysticLightController() {
    Stop();
    if (m_isInitialized && m_pfnRelease) {
        m_pfnRelease();
    }
    UnloadMSISDK();
}

bool MysticLightController::LoadMSISDK() {
    m_hMsiSdk = LoadLibraryA("MysticLight_SDK_x64.dll");
    if (!m_hMsiSdk) return false;

    m_pfnInitialize = (MLAPI_InitializeFunc)GetProcAddress(m_hMsiSdk, "MLAPI_Initialize");
    m_pfnGetDeviceInfo = (MLAPI_GetDeviceInfoFunc)GetProcAddress(m_hMsiSdk, "MLAPI_GetDeviceInfo");
    m_pfnGetDeviceNameEx = (MLAPI_GetDeviceNameExFunc)GetProcAddress(m_hMsiSdk, "MLAPI_GetDeviceNameEx");
    m_pfnGetLedInfo = (MLAPI_GetLedInfoFunc)GetProcAddress(m_hMsiSdk, "MLAPI_GetLedInfo");
    m_pfnGetLedName = (MLAPI_GetLedNameFunc)GetProcAddress(m_hMsiSdk, "MLAPI_GetLedName");
    m_pfnSetLedStyle = (MLAPI_SetLedStyleFunc)GetProcAddress(m_hMsiSdk, "MLAPI_SetLedStyle");
    m_pfnSetLedColor = (MLAPI_SetLedColorFunc)GetProcAddress(m_hMsiSdk, "MLAPI_SetLedColor");
    m_pfnSetLedColorEx = (MLAPI_SetLedColorExFunc)GetProcAddress(m_hMsiSdk, "MLAPI_SetLedColorEx");
    m_pfnRelease = (MLAPI_ReleaseFunc)GetProcAddress(m_hMsiSdk, "MLAPI_Release");

    return m_pfnInitialize && m_pfnGetDeviceInfo && m_pfnGetDeviceNameEx && m_pfnGetLedInfo &&
        m_pfnGetLedName && m_pfnSetLedStyle && m_pfnSetLedColor && m_pfnSetLedColorEx && m_pfnRelease;
}

void MysticLightController::UnloadMSISDK() {
    if (m_hMsiSdk) {
        FreeLibrary(m_hMsiSdk);
        m_hMsiSdk = nullptr;
    }
}

bool MysticLightController::Initialize() {
    if (!LoadMSISDK()) return false;
    int result = m_pfnInitialize();
    if (result != 0) {
        LOG("MLAPI_Initialize failed with error code: " + std::to_string(result));
        return false;
    }
    m_isInitialized = true;
    return true;
}

void MysticLightController::Start() {
    if (m_isRunning) return;
    m_isRunning = true;
    m_renderThread = std::thread(&MysticLightController::RenderLoop, this);
}

void MysticLightController::Stop() {
    if (!m_isRunning) return;
    m_isRunning = false;
    m_cv.notify_one();
    if (m_renderThread.joinable()) {
        m_renderThread.join();
    }
}

void MysticLightController::UpdateData(const std::vector<CorsairLedColor>& colors) {
    {
        std::lock_guard<std::mutex> lock(m_mutex);
        m_colorBuffer = colors;
        m_newData = true;
    }
    m_cv.notify_one();
}

void MysticLightController::RenderLoop() {
    while (m_isRunning) {
        std::vector<CorsairLedColor> colorsToRender;
        {
            std::unique_lock<std::mutex> lock(m_mutex);
            m_cv.wait(lock, [this] { return !m_isRunning || m_newData; });
            if (!m_isRunning) break;

            colorsToRender.swap(m_colorBuffer);
            m_newData = false;
        }

        if (!m_isInitialized || colorsToRender.empty()) continue;

        std::unordered_map<DWORD, std::vector<CorsairLedColor>> simpleDeviceBatches;
        std::unordered_map<DWORD, std::vector<CorsairLedColor>> perLedDeviceBatches;

        for (const auto& color : colorsToRender) {
            auto simple_it = m_simpleDeviceMap.find(color.ledId);
            if (simple_it != m_simpleDeviceMap.end()) {
                simpleDeviceBatches[simple_it->second.deviceIndex].push_back(color);
                continue;
            }
            auto it = m_ledIdMap.find(color.ledId);
            if (it != m_ledIdMap.end()) {
                perLedDeviceBatches[it->second.deviceIndex].push_back(color);
            }
        }

        for (const auto& batch : simpleDeviceBatches) {
            const auto& color = batch.second[0];
            CachedColor newColor = { color.r, color.g, color.b };
            if (!m_lastSentColors.count(color.ledId) || !(m_lastSentColors[color.ledId] == newColor)) {
                const auto& deviceInfo = m_simpleDeviceMap[color.ledId];
                m_pfnSetLedColor(deviceInfo.deviceType, deviceInfo.deviceIndex, color.r, color.g, color.b);
                m_lastSentColors[color.ledId] = newColor;
            }
        }

        for (const auto& batch : perLedDeviceBatches) {
            for (const auto& color : batch.second) {
                CachedColor newColor = { color.r, color.g, color.b };
                if (!m_lastSentColors.count(color.ledId) || !(m_lastSentColors[color.ledId] == newColor)) {
                    const auto& ledInfo = m_ledIdMap[color.ledId];
                    m_pfnSetLedColorEx(ledInfo.deviceType, ledInfo.deviceIndex, ledInfo.ledName, color.r, color.g, color.b, 1);
                    m_lastSentColors[color.ledId] = newColor;
                }
            }
        }
    }
}

std::vector<DeviceInfo> MysticLightController::GetConnectedDevices() const {
    if (!m_isInitialized) return {};
    if (!m_connectedDevices.empty()) return m_connectedDevices;
    LOG("[Mystic Light] Starting device discovery...");
    m_ledIdMap.clear();
    m_simpleDeviceMap.clear();
    m_lastSentColors.clear();
    SAFEARRAY* pDevTypes = SafeArrayCreateVector(VT_BSTR, 0, 0);
    SAFEARRAY* pLedCounts = SafeArrayCreateVector(VT_BSTR, 0, 0);
    int result = m_pfnGetDeviceInfo(&pDevTypes, &pLedCounts);
    if (result != 0) {
        LOG("[Mystic Light] MLAPI_GetDeviceInfo failed with error: " + std::to_string(result));
        SafeArrayDestroy(pDevTypes);
        SafeArrayDestroy(pLedCounts);
        return {};
    }
    long lbound, ubound;
    SafeArrayGetLBound(pDevTypes, 1, &lbound);
    SafeArrayGetUBound(pDevTypes, 1, &ubound);
    long deviceTypeCount = ubound - lbound + 1;
    if (deviceTypeCount == 0) {
        LOG("[Mystic Light] No devices found.");
        return {};
    }
    LOG("[Mystic Light] Found " + std::to_string(deviceTypeCount) + " potential device types. Checking for compatibility...");
    int currentLedId = LED_ID_OFFSET;
    for (long i = 0; i < deviceTypeCount; ++i) {
        BSTR bstrDevType;
        SafeArrayGetElement(pDevTypes, &i, &bstrDevType);
        _bstr_t devType(bstrDevType, false);
        BSTR bstrDevCount;
        SafeArrayGetElement(pLedCounts, &i, &bstrDevCount);
        int devInstanceCount = _wtoi(bstrDevCount);
        SysFreeString(bstrDevCount);
        for (DWORD j = 0; j < (DWORD)devInstanceCount; ++j) {
            BSTR bstrDevName = nullptr;
            if (m_pfnGetDeviceNameEx(devType, j, &bstrDevName) != 0) continue;
            _bstr_t deviceName(bstrDevName, false);
            std::string sDeviceName = (char*)deviceName;
            BSTR pLedAreaName = nullptr;
            SAFEARRAY* pLedStyles = SafeArrayCreateVector(VT_BSTR, 0, 0);
            bool supportsDirect = false;
            bool supportsNoAnimation = false;
            bool supportsBreathing = false;
            if (m_pfnGetLedInfo(devType, 0, &pLedAreaName, &pLedStyles) == 0) {
                long style_lbound, style_ubound;
                SafeArrayGetLBound(pLedStyles, 1, &style_lbound);
                SafeArrayGetUBound(pLedStyles, 1, &style_ubound);
                for (long s_idx = style_lbound; s_idx <= style_ubound; ++s_idx) {
                    BSTR bstrStyle;
                    SafeArrayGetElement(pLedStyles, &s_idx, &bstrStyle);
                    if (wcscmp(bstrStyle, L"Direct") == 0) supportsDirect = true;
                    if (wcscmp(bstrStyle, L"NoAnimation") == 0) supportsNoAnimation = true;
                    if (wcscmp(bstrStyle, L"Breathing") == 0) supportsBreathing = true;
                    SysFreeString(bstrStyle);
                }
            }
            SafeArrayDestroy(pLedStyles);
            SysFreeString(pLedAreaName);
            if (supportsDirect) {
                m_pfnSetLedStyle(devType, j, _bstr_t(L"Direct"));
                SAFEARRAY* pLedNames = SafeArrayCreateVector(VT_BSTR, 0, 0);
                if (m_pfnGetLedName(devType, &pLedNames) == 0) {
                    long led_lbound, led_ubound;
                    SafeArrayGetLBound(pLedNames, 1, &led_lbound);
                    SafeArrayGetUBound(pLedNames, 1, &led_ubound);
                    long ledCount = led_ubound - lbound + 1;
                    if (ledCount > 0) {
                        DeviceInfo newDevice;
                        newDevice.sdk = "MysticLight";
                        newDevice.name = sDeviceName;
                        newDevice.ledCount = ledCount;
                        for (long k = 0; k < ledCount; ++k) {
                            BSTR bstrLedName;
                            SafeArrayGetElement(pLedNames, &k, &bstrLedName);
                            MysticLightLedInfo ledInfo = { devType, _bstr_t(bstrLedName, false), j };
                            m_ledIdMap[currentLedId] = ledInfo;
                            newDevice.leds.push_back(currentLedId++);
                        }
                        m_connectedDevices.push_back(newDevice);
                        LOG("[Mystic Light] -> SUCCESS: Added '" + newDevice.name + "' with per-LED control (" + std::to_string(newDevice.ledCount) + " LEDs).");
                    }
                }
                SafeArrayDestroy(pLedNames);
            }
            else if (supportsNoAnimation) {
                if (m_pfnSetLedStyle(devType, j, _bstr_t(L"NoAnimation")) == 0) {
                    DeviceInfo newDevice;
                    newDevice.sdk = "MysticLight";
                    newDevice.name = sDeviceName;
                    newDevice.ledCount = 1;
                    int generatedId = currentLedId++;
                    newDevice.leds.push_back(generatedId);
                    MysticLightSimpleDeviceInfo simpleInfo = { devType, j };
                    m_simpleDeviceMap[generatedId] = simpleInfo;
                    m_connectedDevices.push_back(newDevice);
                    LOG("[Mystic Light] -> SUCCESS: Added '" + newDevice.name + "' with Static color mode (1 LED).");
                }
            }
            else if (supportsBreathing) {
                if (m_pfnSetLedStyle(devType, j, _bstr_t(L"Breathing")) == 0) {
                    DeviceInfo newDevice;
                    newDevice.sdk = "MysticLight";
                    newDevice.name = sDeviceName;
                    newDevice.ledCount = 1;
                    int generatedId = currentLedId++;
                    newDevice.leds.push_back(generatedId);
                    MysticLightSimpleDeviceInfo simpleInfo = { devType, j };
                    m_simpleDeviceMap[generatedId] = simpleInfo;
                    m_connectedDevices.push_back(newDevice);
                    LOG("[Mystic Light] -> SUCCESS: Added '" + newDevice.name + "' with Breathing color mode (1 LED).");
                }
            }
            else {
                LOG("[Mystic Light] -> INFO: Skipping '" + sDeviceName + "' (no compatible control modes found).");
            }
        }
    }
    SafeArrayDestroy(pDevTypes);
    SafeArrayDestroy(pLedCounts);
    return m_connectedDevices;
}

std::map<std::string, CorsairLedId> MysticLightController::GetNamedKeyMap() const {
    return {};
}