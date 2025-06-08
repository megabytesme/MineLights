#define CORSAIR_LIGHTING_SDK_DISABLE_DEPRECATION_WARNINGS
#include "CUESDK.h"
#include "CorsairLedIdEnum.h"

#include <atomic>
#include <thread>
#include <string>
#include <cmath>
#include <cstdlib>
#include <ctime>
#include <chrono>
#include <vector>
#include <map>

#include "iCueLightController.h"
#include "Player.h"
#include "Biome.h"

CorsairLedColor determineBiomeColor(const std::string& biome) {
    for (const auto& b : biomes) {
        if (b.name == biome) {
            return { CLI_Invalid, b.red, b.green, b.blue };
        }
    }
    return { CLI_Invalid, 0, 0, 0 };
}

CorsairLedColor getBiomeRainColor(const std::string& biome) {
    for (const auto& b : biomes) {
        if (b.name == biome) {
            if (b.isSnowy) {
                return { CLI_Invalid, 200, 200, 255 };
            }
            return { CLI_Invalid, 0, 50, 200 };
        }
    }
    return { CLI_Invalid, 0, 50, 200 };
}

bool isPlayerInRainyBiome(const std::string& biome) {
    for (const auto& b : biomes) {
        if (b.name == biome) {
            return b.hasRain && !b.isSnowy;
        }
    }
    return false;
}

void paintWorldEffects(std::map<CorsairLedId, CorsairLedColor>& colors) {
    static bool rainPhase = false;
    static auto lastRainStep = std::chrono::steady_clock::now();
    static std::vector<CorsairLedId> cracklingFireKeys;
    static auto lastFireCrackleUpdate = std::chrono::steady_clock::now();
    static bool isFlashing = false;
    static auto flashStartTime = std::chrono::steady_clock::now();

    if (!player.inGame) {
        for (int i = 0; i < CLI_Last; i++) colors[(CorsairLedId)i] = { (CorsairLedId)i, 221, 26, 33 };
        return;
    }

    auto now = std::chrono::steady_clock::now();

    if (player.weather == "Thunderstorm" && !isFlashing && (rand() % 200 < 1)) {
        isFlashing = true;
        flashStartTime = now;
    }

    if (isFlashing) {
        if (std::chrono::duration_cast<std::chrono::milliseconds>(now - flashStartTime).count() < 150) {
            for (int i = 0; i < CLI_Last; i++) {
                colors[(CorsairLedId)i] = { (CorsairLedId)i, 255, 255, 255 };
            }
            return;
        }
        else {
            isFlashing = false;
        }
    }

    if (player.isOnFire) {
        CorsairLedColor baseColor = { CLI_Invalid, 255, 69, 0 };
        CorsairLedColor crackleColor = { CLI_Invalid, 200, 0, 0 };
        if (std::chrono::duration_cast<std::chrono::milliseconds>(now - lastFireCrackleUpdate).count() > 100) {
            cracklingFireKeys.clear();
            for (int i = 0; i < CLI_Last; ++i) {
                if (rand() % 5 == 0) cracklingFireKeys.push_back(static_cast<CorsairLedId>(i));
            }
            lastFireCrackleUpdate = now;
        }
        for (int i = 0; i < CLI_Last; ++i) colors[static_cast<CorsairLedId>(i)] = baseColor;
        for (const auto& ledId : cracklingFireKeys) colors[ledId] = crackleColor;
    }
    else if (player.currentBlock == "block.minecraft.nether_portal") {
        static std::vector<CorsairLedId> twinklingKeys;
        static auto lastTwinkleUpdate = std::chrono::steady_clock::now();
        CorsairLedColor baseColor = { CLI_Invalid, 128, 0, 128 };
        CorsairLedColor twinkleColor = { CLI_Invalid, 50, 0, 100 };
        if (std::chrono::duration_cast<std::chrono::milliseconds>(now - lastTwinkleUpdate).count() > 500) {
            twinklingKeys.clear();
            for (int i = 0; i < CLI_Last; ++i) {
                if (rand() % 10 < 2) twinklingKeys.push_back(static_cast<CorsairLedId>(i));
            }
            lastTwinkleUpdate = now;
        }
        for (int i = 0; i < CLI_Last; ++i) colors[static_cast<CorsairLedId>(i)] = baseColor;
        for (const auto& ledId : twinklingKeys) colors[ledId] = twinkleColor;
    }
    else if (player.currentBlock == "block.minecraft.end_portal") {
        static std::vector<CorsairLedId> twinklingKeys;
        static auto lastTwinkleUpdate = std::chrono::steady_clock::now();
        CorsairLedColor baseColor = { CLI_Invalid, 0, 0, 50 };
        CorsairLedColor twinkleColor = { CLI_Invalid, 50, 50, 50 };
        if (std::chrono::duration_cast<std::chrono::milliseconds>(now - lastTwinkleUpdate).count() > 500) {
            twinklingKeys.clear();
            for (int i = 0; i < CLI_Last; ++i) {
                if (rand() % 10 < 2) twinklingKeys.push_back(static_cast<CorsairLedId>(i));
            }
            lastTwinkleUpdate = now;
        }
        for (int i = 0; i < CLI_Last; ++i) colors[static_cast<CorsairLedId>(i)] = baseColor;
        for (const auto& ledId : twinklingKeys) colors[ledId] = twinkleColor;
    }
    else if ((player.weather == "Rain" || player.weather == "Thunderstorm") && isPlayerInRainyBiome(player.currentBiome)) {
        if (std::chrono::duration_cast<std::chrono::milliseconds>(now - lastRainStep).count() > 500) {
            rainPhase = !rainPhase;
            lastRainStep = now;
        }
        CorsairLedColor biomeColor = determineBiomeColor(player.currentBiome);
        CorsairLedColor rainColor = getBiomeRainColor(player.currentBiome);
        for (int i = 0; i < CLI_Last; i++) {
            colors[(CorsairLedId)i] = (i % 2 == (rainPhase ? 0 : 1)) ? rainColor : biomeColor;
        }
    }
    else {
        CorsairLedColor biomeColor = determineBiomeColor(player.currentBiome);
        for (int i = 0; i < CLI_Last; i++) {
            colors[(CorsairLedId)i] = biomeColor;
        }
    }
}

void paintExperienceBar(std::map<CorsairLedId, CorsairLedColor>& colors) {
    if (!player.inGame) return;
    const std::vector<CorsairLedId> leds = { CLK_1, CLK_2, CLK_3, CLK_4, CLK_5, CLK_6, CLK_7, CLK_8, CLK_9, CLK_0 };
    int ledsToLight = static_cast<int>(player.experience * leds.size());
    for (size_t i = 0; i < leds.size(); i++) {
        if (i < ledsToLight) colors[leds[i]] = { leds[i], 0, 255, 0 };
        else colors[leds[i]] = { leds[i], 10, 30, 10 };
    }
}

void paintPlayerBars(std::map<CorsairLedId, CorsairLedColor>& colors) {
    if (!player.inGame) return;
    static bool wasTakingDamageLastFrame = false;
    static bool isDamageFlashActive = false;
    static auto damageFlashStartTime = std::chrono::steady_clock::now();
    const std::vector<CorsairLedId> healthLeds = { CLK_F1, CLK_F2, CLK_F3, CLK_F4 };
    const std::vector<CorsairLedId> hungerLeds = { CLK_F5, CLK_F6, CLK_F7, CLK_F8 };

    if (player.isTakingDamage && !wasTakingDamageLastFrame) {
        isDamageFlashActive = true;
        damageFlashStartTime = std::chrono::steady_clock::now();
    }
    wasTakingDamageLastFrame = player.isTakingDamage;

    if (isDamageFlashActive) {
        if (std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - damageFlashStartTime).count() < 120) {
            for (const auto& led : healthLeds) {
                colors[led] = { led, 255, 255, 255 };
            }
        }
        else {
            isDamageFlashActive = false;
        }
    }

    if (!isDamageFlashActive) {
        for (size_t i = 0; i < healthLeds.size(); i++) {
            CorsairLedColor color = { healthLeds[i], 30, 0, 0 };
            if (player.isWithering) color = { healthLeds[i], 43, 43, 43 };
            else if (player.isPoisoned) color = { healthLeds[i], 148, 120, 24 };
            else if (player.health > i * 5) color = { healthLeds[i], 255, 0, 0 };
            colors[healthLeds[i]] = color;
        }
    }

    for (size_t i = 0; i < hungerLeds.size(); i++) {
        CorsairLedColor color = { hungerLeds[i], 30, 15, 0 };
        if (player.hunger > i * 5) {
            color = { hungerLeds[i], 255, 165, 0 };
        }
        colors[hungerLeds[i]] = color;
    }
}

void paintHealthEffects(std::map<CorsairLedId, CorsairLedColor>& colors) {
    static float brightness = 0.1f;
    static bool isBeatingUp = true;
    static auto lastBeatStep = std::chrono::steady_clock::now();
    static bool wasLowHealthLastFrame = false;

    bool isLowHealthNow = player.inGame && player.health < 10;

    if (isLowHealthNow && !wasLowHealthLastFrame) {
        brightness = 0.1f;
        isBeatingUp = true;
        lastBeatStep = std::chrono::steady_clock::now();
    }

    wasLowHealthLastFrame = isLowHealthNow;

    if (!isLowHealthNow) {
        return;
    }

    const std::vector<CorsairLedId> heartRedLeds = { CLK_4, CLK_5, CLK_6, CLK_7, CLK_E, CLK_T, CLK_U, CLK_D, CLK_F, CLK_H, CLK_V, CLK_B, CLK_Space };
    const std::vector<CorsairLedId> heartWhiteLeds = { CLK_R, CLK_Y, CLK_G };

    auto now = std::chrono::steady_clock::now();
    if (std::chrono::duration_cast<std::chrono::milliseconds>(now - lastBeatStep).count() > 40) {
        if (isBeatingUp) {
            brightness += 0.1f;
            if (brightness >= 1.0f) {
                brightness = 1.0f;
                isBeatingUp = false;
            }
        }
        else {
            brightness -= 0.1f;
            if (brightness <= 0.1f) {
                brightness = 0.1f;
                isBeatingUp = true;
            }
        }
        lastBeatStep = now;
    }

    for (auto led : heartRedLeds) {
        colors[led] = { led, static_cast<int>(255 * brightness), 0, 0 };
    }
    for (auto led : heartWhiteLeds) {
        colors[led] = { led, static_cast<int>(255 * brightness), static_cast<int>(255 * brightness), static_cast<int>(255 * brightness) };
    }
}

void paintPlayerEffects(std::map<CorsairLedId, CorsairLedColor>& colors) {
    if (!player.inGame) return;
    const std::vector<CorsairLedId> leds = { CLK_W, CLK_A, CLK_S, CLK_D, CLK_LeftCtrl, CLK_LeftShift, CLK_Space };
    CorsairLedColor keyColor = { CLI_Invalid, 255, 255, 255 };

    if (player.currentBlock == "block.minecraft.water") keyColor = { CLI_Invalid, 0, 100, 255 };
    else if (player.currentBlock == "block.minecraft.lava" || player.currentBlock == "block.minecraft.fire") keyColor = { CLI_Invalid, 255, 0, 0 };

    for (const auto& led : leds) colors[led] = { led, keyColor.r, keyColor.g, keyColor.b };
}

void runLightingLoop() {
    const int frame_duration_ms = 33;

    while (true) {
        auto frame_start = std::chrono::steady_clock::now();

        std::map<CorsairLedId, CorsairLedColor> finalColors;

        paintWorldEffects(finalColors);
        paintExperienceBar(finalColors);
        paintPlayerBars(finalColors);
        paintHealthEffects(finalColors);
        paintPlayerEffects(finalColors);

        std::vector<CorsairLedColor> colorsToSend;
        colorsToSend.reserve(finalColors.size());

        for (const auto& pair : finalColors) {
            CorsairLedId ledId = pair.first;
            const CorsairLedColor& color = pair.second;
            colorsToSend.push_back({ ledId, color.r, color.g, color.b });
        }

        if (!colorsToSend.empty()) {
            CorsairSetLedsColors(static_cast<int>(colorsToSend.size()), colorsToSend.data());
        }

        auto frame_end = std::chrono::steady_clock::now();
        auto frame_elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(frame_end - frame_start);
        if (frame_elapsed.count() < frame_duration_ms) {
            std::this_thread::sleep_for(std::chrono::milliseconds(frame_duration_ms - frame_elapsed.count()));
        }
    }
}

iCueLightController::iCueLightController()
{
    srand(static_cast<unsigned int>(time(nullptr)));

    CorsairPerformProtocolHandshake();
    if (CorsairGetLastError()) {
        return;
    }
    CorsairRequestControl(CAM_ExclusiveLightingControl);

    std::thread lightingThread(runLightingLoop);
    lightingThread.detach();
}