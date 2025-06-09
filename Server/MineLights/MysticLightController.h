#pragma once

#include "ILightingController.h"
#include <Windows.h>
#include <comutil.h>
#include <map>
#include <string>

typedef int(__cdecl* MLAPI_InitializeFunc)();
typedef int(__cdecl* MLAPI_GetDeviceInfoFunc)(SAFEARRAY**, SAFEARRAY**);
typedef int(__cdecl* MLAPI_GetDeviceNameExFunc)(BSTR, DWORD, BSTR*);
typedef int(__cdecl* MLAPI_GetLedInfoFunc)(BSTR, DWORD, BSTR*, SAFEARRAY**);
typedef int(__cdecl* MLAPI_GetLedNameFunc)(BSTR, SAFEARRAY**);
typedef int(__cdecl* MLAPI_SetLedStyleFunc)(BSTR, DWORD, BSTR);
typedef int(__cdecl* MLAPI_SetLedColorFunc)(BSTR, DWORD, DWORD, DWORD, DWORD);
typedef int(__cdecl* MLAPI_SetLedColorExFunc)(BSTR, DWORD, BSTR, DWORD, DWORD, DWORD, DWORD);
typedef int(__cdecl* MLAPI_ReleaseFunc)();

struct MysticLightLedInfo {
    _bstr_t deviceType;
    _bstr_t ledName;
    DWORD deviceIndex;
};

struct MysticLightSimpleDeviceInfo {
    _bstr_t deviceType;
    DWORD deviceIndex;
};

struct CachedColor {
    int r, g, b;
    bool operator==(const CachedColor& other) const {
        return r == other.r && g == other.g && b == other.b;
    }
};

class MysticLightController : public ILightingController {
public:
    MysticLightController();
    ~MysticLightController() override;

    bool Initialize() override;
    void Start() override;
    void Stop() override;
    void UpdateData(const std::vector<CorsairLedColor>& colors) override;
    std::map<std::string, CorsairLedId> GetNamedKeyMap() const override;
    std::vector<DeviceInfo> GetConnectedDevices() const override;

private:
    void RenderLoop();
    bool LoadMSISDK();
    void UnloadMSISDK();

    HMODULE m_hMsiSdk;
    MLAPI_InitializeFunc m_pfnInitialize;
    MLAPI_GetDeviceInfoFunc m_pfnGetDeviceInfo;
    MLAPI_GetDeviceNameExFunc m_pfnGetDeviceNameEx;
    MLAPI_GetLedInfoFunc m_pfnGetLedInfo;
    MLAPI_GetLedNameFunc m_pfnGetLedName;
    MLAPI_SetLedStyleFunc m_pfnSetLedStyle;
    MLAPI_SetLedColorFunc m_pfnSetLedColor;
    MLAPI_SetLedColorExFunc m_pfnSetLedColorEx;
    MLAPI_ReleaseFunc m_pfnRelease;

    mutable std::map<int, MysticLightLedInfo> m_ledIdMap;
    mutable std::map<int, MysticLightSimpleDeviceInfo> m_simpleDeviceMap;
    mutable std::vector<DeviceInfo> m_connectedDevices;
    mutable std::map<int, CachedColor> m_lastSentColors;

    bool m_isInitialized;

    static const int LED_ID_OFFSET = 3000;
};