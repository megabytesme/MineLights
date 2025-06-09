#include "MysticLightController.h"
#include "Logger.h"
#include <vector>
#include <string>
#include <atlbase.h>

MysticLightController::MysticLightController() : m_hMsiSdk(nullptr), m_isInitialized(false) {}

MysticLightController::~MysticLightController() {
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

std::vector<DeviceInfo> MysticLightController::GetConnectedDevices() const {
    if (!m_isInitialized) return {};
    if (!m_connectedDevices.empty()) return m_connectedDevices;

    LOG("[Mystic Light] Getting device info...");
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
            LOG("[Mystic Light] Found device: " + std::string((char*)deviceName));

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
                        newDevice.name = (char*)deviceName;
                        newDevice.ledCount = ledCount;
                        for (long k = 0; k < ledCount; ++k) {
                            BSTR bstrLedName;
                            SafeArrayGetElement(pLedNames, &k, &bstrLedName);
                            MysticLightLedInfo ledInfo = { devType, _bstr_t(bstrLedName, false), j };
                            m_ledIdMap[currentLedId] = ledInfo;
                            newDevice.leds.push_back(currentLedId++);
                        }
                        m_connectedDevices.push_back(newDevice);
                        LOG("[Mystic Light] SUCCESS: Added Direct control device '" + newDevice.name + "' with " + std::to_string(newDevice.ledCount) + " LEDs.");
                    }
                }
                SafeArrayDestroy(pLedNames);

            }
            else if (supportsNoAnimation) {
                if (m_pfnSetLedStyle(devType, j, _bstr_t(L"NoAnimation")) == 0) {
                    DeviceInfo newDevice;
                    newDevice.sdk = "MysticLight";
                    newDevice.name = (char*)deviceName;
                    newDevice.ledCount = 1;

                    int generatedId = currentLedId++;
                    newDevice.leds.push_back(generatedId);

                    MysticLightSimpleDeviceInfo simpleInfo = { devType, j };
                    m_simpleDeviceMap[generatedId] = simpleInfo;

                    m_connectedDevices.push_back(newDevice);
                    LOG("[Mystic Light] SUCCESS: Added Fallback 'Static (NoAnimation)' device '" + newDevice.name + "' as a single light.");
                }
                else {
                    LOG("[Mystic Light] INFO: Device '" + std::string((char*)deviceName) + "' supports NoAnimation but failed to set style.");
                }

            }
            else if (supportsBreathing) {
                if (m_pfnSetLedStyle(devType, j, _bstr_t(L"Breathing")) == 0) {
                    DeviceInfo newDevice;
                    newDevice.sdk = "MysticLight";
                    newDevice.name = (char*)deviceName;
                    newDevice.ledCount = 1;

                    int generatedId = currentLedId++;
                    newDevice.leds.push_back(generatedId);

                    MysticLightSimpleDeviceInfo simpleInfo = { devType, j };
                    m_simpleDeviceMap[generatedId] = simpleInfo;

                    m_connectedDevices.push_back(newDevice);
                    LOG("[Mystic Light] SUCCESS: Added Fallback 'Breathing' device '" + newDevice.name + "' as a single light.");
                }
                else {
                    LOG("[Mystic Light] INFO: Device '" + std::string((char*)deviceName) + "' supports Breathing but failed to set style.");
                }
            }
            else {
                LOG("[Mystic Light] INFO: Device '" + std::string((char*)deviceName) + "' does not support any controllable modes and will be skipped.");
            }
        }
    }
    SafeArrayDestroy(pDevTypes);
    SafeArrayDestroy(pLedCounts);
    return m_connectedDevices;
}

void MysticLightController::Render(const std::vector<CorsairLedColor>& colors) {
    if (!m_isInitialized) return;

    for (const auto& color : colors) {
        auto simple_it = m_simpleDeviceMap.find(color.ledId);
        if (simple_it != m_simpleDeviceMap.end()) {
            const auto& deviceInfo = simple_it->second;
            m_pfnSetLedColor(deviceInfo.deviceType, deviceInfo.deviceIndex, color.r, color.g, color.b);
            continue;
        }

        auto it = m_ledIdMap.find(color.ledId);
        if (it != m_ledIdMap.end()) {
            const auto& ledInfo = it->second;
            m_pfnSetLedColorEx(ledInfo.deviceType, ledInfo.deviceIndex, ledInfo.ledName, color.r, color.g, color.b, 1);
        }
    }
}

std::map<std::string, CorsairLedId> MysticLightController::GetNamedKeyMap() const {
    return {};
}