#pragma once
#include <thread>
#include <memory>

class LightingManager;

class PlayerProcessor {
public:
    PlayerProcessor();
    ~PlayerProcessor();

private:
    std::thread udpServerThread;
    std::unique_ptr<LightingManager> lightingManager;
};