#pragma once
#include <vector>
#include <memory>
#include <thread>
#include "ILightingController.h"
#include "EffectPainter.h"

class LightingManager {
public:
    LightingManager();
    ~LightingManager();

private:
    void runLoop();

    std::vector<std::unique_ptr<ILightingController>> controllers;
    std::unique_ptr<EffectPainter> painter;
    std::thread workerThread;
};