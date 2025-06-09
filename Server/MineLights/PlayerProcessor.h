#pragma once

#include <thread>
#include <memory>
#include <vector>
#include "ILightingController.h"

class PlayerProcessor {
public:
    PlayerProcessor(HWND hwnd);
    ~PlayerProcessor();

private:
    void UDPServerLoop();
    void HandshakeServerLoop();
    void CommandServerLoop();
    void DiscoveryBroadcastLoop();
    bool IsRunningAsAdmin() const;
    void TriggerRestart(bool asAdmin);

    std::vector<std::unique_ptr<ILightingController>> m_controllers;
    std::thread m_udpServerThread;
    std::thread m_handshakeServerThread;
    std::thread m_commandServerThread;
    std::thread m_discoveryThread;
    bool m_isRunning;
    HWND m_hwnd;
};