#include "MysticLightController.h"
#include "Logger.h"
#include <vector>
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
    if (!m_hMsiSdk) {
        return false;
    }

    m_pfnInitialize = (MLAPI_InitializeFunc)GetProcAddress(m_hMsiSdk, "MLAPI_Initialize");
    m_pfnGetDeviceInfo = (MLAPI_GetDeviceInfoFunc)GetProcAddress(m_hMsiSdk, "MLAPI_GetDeviceInfo");
    m_pfnGetDeviceNameEx = (MLAPI_GetDeviceNameExFunc)GetProcAddress(m_hMsiSdk, "MLAPI_GetDeviceNameEx");
    m_pfnGetLedName = (MLAPI_GetLedNameFunc)GetProcAddress(m_hMsiSdk, "MLAPI_GetLedName");
    m_pfnSetLedStyle = (MLAPI_SetLedStyleFunc)GetProcAddress(m_hMsiSdk, "MLAPI_SetLedStyle");
    m_pfnSetLedColorEx = (MLAPI_SetLedColorExFunc)GetProcAddress(m_hMsiSdk, "MLAPI_SetLedColorEx");
    m_pfnRelease = (MLAPI_ReleaseFunc)GetProcAddress(m_hMsiSdk, "MLAPI_Release");

    return m_pfnInitialize && m_pfnGetDeviceInfo && m_pfnGetDeviceNameEx && m_pfnGetLedName &&
        m_pfnSetLedStyle && m_pfnSetLedColorEx && m_pfnRelease;
}

void MysticLightController::UnloadMSISDK() {
    if (m_hMsiSdk) {
        FreeLibrary(m_hMsiSdk);
        m_hMsiSdk = nullptr;
    }
}

bool MysticLightController::Initialize() {
    if (!LoadMSISDK()) {
        return false;
    }
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
    LOG("[Mystic Light] Found " + std::to_string(deviceTypeCount) + " device types.");

    int currentLedId = LED_ID_OFFSET;

    for (long i = 0; i < deviceTypeCount; ++i) {
        BSTR bstrDevType;
        SafeArrayGetElement(pDevTypes, &i, &bstrDevType);
        _bstr_t devType(bstrDevType, false);

        BSTR bstrLedCount;
        SafeArrayGetElement(pLedCounts, &i, &bstrLedCount);
        int devInstanceCount = _wtoi(bstrLedCount);
        SysFreeString(bstrLedCount);

        for (DWORD j = 0; j < (DWORD)devInstanceCount; ++j) {
            BSTR bstrDevName = nullptr;
            result = m_pfnGetDeviceNameEx(devType, j, &bstrDevName);
            if (result != 0) {
                LOG("[Mystic Light] MLAPI_GetDeviceNameEx failed for type " + std::string((char*)devType) + ", index " + std::to_string(j) + " with error: " + std::to_string(result));
                continue;
            }
            _bstr_t deviceName(bstrDevName, false);
            LOG("[Mystic Light] Found device: " + std::string((char*)deviceName));

            result = m_pfnSetLedStyle(devType, j, _bstr_t(L"Direct"));
            if (result != 0) {
                LOG("[Mystic Light] MLAPI_SetLedStyle to 'Direct' failed for " + std::string((char*)deviceName) + " with error: " + std::to_string(result) + ". This device may not be controllable.");
                continue;
            }
            LOG("[Mystic Light] Successfully set " + std::string((char*)deviceName) + " to Direct control mode.");

            SAFEARRAY* pLedNames = SafeArrayCreateVector(VT_BSTR, 0, 0);
            result = m_pfnGetLedName(devType, &pLedNames);
            if (result != 0) {
                LOG("[Mystic Light] MLAPI_GetLedName failed for " + std::string((char*)deviceName) + " with error: " + std::to_string(result));
                SafeArrayDestroy(pLedNames);
                continue;
            }

            long led_lbound, led_ubound;
            SafeArrayGetLBound(pLedNames, 1, &led_lbound);
            SafeArrayGetUBound(pLedNames, 1, &led_ubound);
            long ledCount = led_ubound - led_lbound + 1;

            if (ledCount > 0) {
                DeviceInfo newDevice;
                newDevice.sdk = "MysticLight";
                newDevice.name = (char*)deviceName;
                newDevice.ledCount = ledCount;

                for (long k = 0; k < ledCount; ++k) {
                    BSTR bstrLedName;
                    SafeArrayGetElement(pLedNames, &k, &bstrLedName);

                    int generatedId = currentLedId++;
                    newDevice.leds.push_back(generatedId);

                    MysticLightLedInfo ledInfo;
                    ledInfo.deviceType = devType;
                    ledInfo.ledName = _bstr_t(bstrLedName, false);
                    ledInfo.deviceIndex = j;
                    m_ledIdMap[generatedId] = ledInfo;
                }
                m_connectedDevices.push_back(newDevice);
                LOG("[Mystic Light] Added " + std::string((char*)deviceName) + " with " + std::to_string(ledCount) + " LEDs to the device list.");
            }
            SafeArrayDestroy(pLedNames);
        }
    }

    SafeArrayDestroy(pDevTypes);
    SafeArrayDestroy(pLedCounts);

    return m_connectedDevices;
}


void MysticLightController::Render(const std::vector<CorsairLedColor>& colors) {
    if (!m_isInitialized) return;

    for (const auto& color : colors) {
        auto it = m_ledIdMap.find(color.ledId);
        if (it != m_ledIdMap.end()) {
            const auto& ledInfo = it->second;
            m_pfnSetLedColorEx(
                ledInfo.deviceType,
                ledInfo.deviceIndex,
                ledInfo.ledName,
                color.r, color.g, color.b, 1
            );
        }
    }
}

std::map<std::string, CorsairLedId> MysticLightController::GetNamedKeyMap() const {
    return {};
}