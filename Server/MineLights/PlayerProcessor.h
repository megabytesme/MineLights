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
    void SendHandshake();

    std::vector<std::unique_ptr<ILightingController>> m_controllers;
    std::thread m_udpServerThread;
    bool m_isRunning;
};