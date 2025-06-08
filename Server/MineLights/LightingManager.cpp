#include "LightingManager.h"
#include "iCueLightController.h"
#include <chrono>

LightingManager::LightingManager() {
    auto iCue = std::make_unique<iCueLightController>();
    if (iCue->Initialize()) {
        controllers.push_back(std::move(iCue));
    }

    if (!controllers.empty()) {
        std::vector<GenericLedId> allLedIds;
        std::map<NamedKey, GenericLedId> namedKeyMap;

        for (const auto& controller : controllers) {
            auto cIds = controller->GetAllLedIds();
            allLedIds.insert(allLedIds.end(), cIds.begin(), cIds.end());

            auto cMap = controller->GetNamedKeyMap();
            namedKeyMap.insert(cMap.begin(), cMap.end());
        }

        painter = std::make_unique<EffectPainter>(allLedIds, namedKeyMap);
        workerThread = std::thread(&LightingManager::runLoop, this);
    }
}

LightingManager::~LightingManager() {
    if (workerThread.joinable()) {
        workerThread.detach();
    }
}

void LightingManager::runLoop() {
    const int frame_duration_ms = 33;
    while (true) {
        auto frame_start = std::chrono::steady_clock::now();

        FrameState currentState;
        painter->Paint(currentState);

        for (const auto& controller : controllers) {
            controller->Render(currentState);
        }

        auto frame_end = std::chrono::steady_clock::now();
        auto frame_elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(frame_end - frame_start);
        if (frame_elapsed.count() < frame_duration_ms) {
            std::this_thread::sleep_for(std::chrono::milliseconds(frame_duration_ms - frame_elapsed.count()));
        }
    }
}