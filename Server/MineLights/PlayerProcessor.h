#pragma once

#include <thread>
#include <memory>
#include "iCueLightController.h"

class PlayerProcessor {
public:
    PlayerProcessor();
    ~PlayerProcessor();

private:
    void UDPServerLoop();
    void SendHandshake();
    std::unique_ptr<iCueLightController> m_controller;
    std::thread m_udpServerThread;
    bool m_isRunning;
};