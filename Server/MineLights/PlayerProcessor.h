#pragma once

#include <thread>
#include <memory>
#include <vector>
#include "ILightingController.h"

class PlayerProcessor {
public:
    PlayerProcessor();
    ~PlayerProcessor();

private:
    void UDPServerLoop();
    void HandshakeServerLoop();
    bool IsRunningAsAdmin() const;

    std::vector<std::unique_ptr<ILightingController>> m_controllers;
    std::thread m_udpServerThread;
    std::thread m_handshakeServerThread;
    bool m_isRunning;
};