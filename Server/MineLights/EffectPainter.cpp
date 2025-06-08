#include "EffectPainter.h"
#include "Player.h"
#include "Biome.h"
#include <algorithm>
#include <chrono>
#include <vector>

static int lerp(int start, int end, float t) {
    t = std::max(0.0f, std::min(1.0f, t));
    return static_cast<int>(start + (end - start) * t);
}

static RGBColor determineBiomeColor(const std::string& biome) {
    for (const auto& b : biomes) {
        if (b.name == biome) {
            return { b.red, b.green, b.blue };
        }
    }
    return { 0, 0, 0 };
}

static RGBColor getBiomeRainColor(const std::string& biome) {
    for (const auto& b : biomes) {
        if (b.name == biome) {
            if (b.isSnowy) {
                return { 200, 200, 255 };
            }
            return { 0, 50, 200 };
        }
    }
    return { 0, 50, 200 };
}

static bool isPlayerInRainyBiome(const std::string& biome) {
    for (const auto& b : biomes) {
        if (b.name == biome) {
            return b.hasRain && !b.isSnowy;
        }
    }
    return false;
}

EffectPainter::EffectPainter(const std::vector<GenericLedId>& allKeys, const std::map<NamedKey, GenericLedId>& namedKeys)
    : allLedIds(allKeys), namedKeyMap(namedKeys) {
}

void EffectPainter::Paint(FrameState& state) {
    state.key_colors.clear();
    paintWorldEffects(state);
    paintExperienceBar(state);
    paintPlayerBars(state);
    paintHealthEffects(state);
    paintPlayerEffects(state);
}

void EffectPainter::paintWorldEffects(FrameState& state) {
    static bool rainPhase = false;
    static auto lastRainStep = std::chrono::steady_clock::now();
    static bool isFlashing = false;
    static auto flashStartTime = std::chrono::steady_clock::now();

    static RGBColor currentSmoothBiomeColor = { 0, 0, 0 };
    static RGBColor transitionStartColor = { 0, 0, 0 };
    static RGBColor targetBiomeColor = { 0, 0, 0 };
    static std::string lastKnownBiome = "";
    static auto transitionStartTime = std::chrono::steady_clock::now();
    const int transitionDurationMs = 750;

    if (!player.inGame) {
        state.background_color = { 255, 0, 0 };
        return;
    }

    auto now = std::chrono::steady_clock::now();

    if (player.weather == "Thunderstorm" && !isFlashing && (rand() % 200 < 1)) {
        isFlashing = true;
        flashStartTime = now;
    }
    if (isFlashing) {
        if (std::chrono::duration_cast<std::chrono::milliseconds>(now - flashStartTime).count() < 150) {
            state.background_color = { 255, 255, 255 };
            state.key_colors.clear();
            return;
        }
        else {
            isFlashing = false;
        }
    }

    if (player.currentBiome != lastKnownBiome && !player.currentBiome.empty()) {
        transitionStartColor = currentSmoothBiomeColor;
        targetBiomeColor = determineBiomeColor(player.currentBiome);
        lastKnownBiome = player.currentBiome;
        transitionStartTime = std::chrono::steady_clock::now();
        currentSmoothBiomeColor = transitionStartColor;
    }

    auto elapsedMs = std::chrono::duration_cast<std::chrono::milliseconds>(now - transitionStartTime).count();
    float t = static_cast<float>(elapsedMs) / transitionDurationMs;
    currentSmoothBiomeColor.r = lerp(transitionStartColor.r, targetBiomeColor.r, t);
    currentSmoothBiomeColor.g = lerp(transitionStartColor.g, targetBiomeColor.g, t);
    currentSmoothBiomeColor.b = lerp(transitionStartColor.b, targetBiomeColor.b, t);

    state.background_color = currentSmoothBiomeColor;

    if (player.isOnFire) {
        static std::vector<GenericLedId> cracklingKeys;
        static auto lastUpdate = std::chrono::steady_clock::now();
        state.background_color = { 255, 69, 0 };

        if (!allLedIds.empty() && std::chrono::duration_cast<std::chrono::milliseconds>(now - lastUpdate).count() > 100) {
            cracklingKeys.clear();
            int crackleCount = allLedIds.size() / 5;
            for (int i = 0; i < crackleCount; ++i) {
                cracklingKeys.push_back(allLedIds[rand() % allLedIds.size()]);
            }
            lastUpdate = now;
        }
        for (const auto& keyId : cracklingKeys) {
            state.key_colors[keyId] = { 200, 0, 0 };
        }
    }
    else if (player.currentBlock == "block.minecraft.nether_portal") {
        static std::vector<GenericLedId> twinklingKeys;
        static auto lastUpdate = std::chrono::steady_clock::now();
        state.background_color = { 128, 0, 128 };

        if (!allLedIds.empty() && std::chrono::duration_cast<std::chrono::milliseconds>(now - lastUpdate).count() > 500) {
            twinklingKeys.clear();
            int twinkleCount = allLedIds.size() / 5;
            for (int i = 0; i < twinkleCount; ++i) {
                twinklingKeys.push_back(allLedIds[rand() % allLedIds.size()]);
            }
            lastUpdate = now;
        }
        for (const auto& keyId : twinklingKeys) {
            state.key_colors[keyId] = { 50, 0, 100 };
        }
    }
    else if (player.currentBlock == "block.minecraft.end_portal") {
        static std::vector<GenericLedId> twinklingKeys;
        static auto lastUpdate = std::chrono::steady_clock::now();
        state.background_color = { 0, 0, 50 };

        if (!allLedIds.empty() && std::chrono::duration_cast<std::chrono::milliseconds>(now - lastUpdate).count() > 500) {
            twinklingKeys.clear();
            int twinkleCount = allLedIds.size() / 5;
            for (int i = 0; i < twinkleCount; ++i) {
                twinklingKeys.push_back(allLedIds[rand() % allLedIds.size()]);
            }
            lastUpdate = now;
        }
        for (const auto& keyId : twinklingKeys) {
            state.key_colors[keyId] = { 50, 50, 50 };
        }
    }
    else if ((player.weather == "Rain" || player.weather == "Thunderstorm") && isPlayerInRainyBiome(player.currentBiome)) {
        if (std::chrono::duration_cast<std::chrono::milliseconds>(now - lastRainStep).count() > 500) {
            rainPhase = !rainPhase;
            lastRainStep = now;
        }
        RGBColor rainColor = getBiomeRainColor(player.currentBiome);
        if (!allLedIds.empty()) {
            for (size_t i = 0; i < allLedIds.size(); ++i) {
                if (i % 2 == (rainPhase ? 0 : 1)) {
                    state.key_colors[allLedIds[i]] = rainColor;
                }
            }
        }
    }
}

void EffectPainter::paintExperienceBar(FrameState& state) {
    if (!player.inGame) return;
    const std::vector<NamedKey> leds = {
        NamedKey::N1, NamedKey::N2, NamedKey::N3, NamedKey::N4, NamedKey::N5,
        NamedKey::N6, NamedKey::N7, NamedKey::N8, NamedKey::N9, NamedKey::N0
    };
    int ledsToLight = static_cast<int>(player.experience * leds.size());
    for (size_t i = 0; i < leds.size(); i++) {
        if (namedKeyMap.count(leds[i])) {
            GenericLedId id = namedKeyMap.at(leds[i]);
            if (i < static_cast<size_t>(ledsToLight)) {
                state.key_colors[id] = { 0, 255, 0 };
            }
            else {
                state.key_colors[id] = { 10, 30, 10 };
            }
        }
    }
}

void EffectPainter::paintPlayerBars(FrameState& state) {
    if (!player.inGame) return;
    static bool wasTakingDamageLastFrame = false;
    static bool isDamageFlashActive = false;
    static auto damageFlashStartTime = std::chrono::steady_clock::now();
    const std::vector<NamedKey> healthLeds = { NamedKey::F1, NamedKey::F2, NamedKey::F3, NamedKey::F4 };
    const std::vector<NamedKey> hungerLeds = { NamedKey::F5, NamedKey::F6, NamedKey::F7, NamedKey::F8 };

    if (player.isTakingDamage && !wasTakingDamageLastFrame) {
        isDamageFlashActive = true;
        damageFlashStartTime = std::chrono::steady_clock::now();
    }
    wasTakingDamageLastFrame = player.isTakingDamage;

    if (isDamageFlashActive && std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - damageFlashStartTime).count() >= 120) {
        isDamageFlashActive = false;
    }

    const RGBColor healthDim = { 30, 0, 0 };
    const RGBColor healthFull = { 255, 0, 0 };
    for (size_t i = 0; i < healthLeds.size(); i++) {
        if (namedKeyMap.count(healthLeds[i])) {
            GenericLedId id = namedKeyMap.at(healthLeds[i]);
            RGBColor finalColor;
            if (isDamageFlashActive) {
                finalColor = { 255, 255, 255 };
            }
            else if (player.isWithering) {
                finalColor = { 43, 43, 43 };
            }
            else if (player.isPoisoned) {
                finalColor = { 148, 120, 24 };
            }
            else {
                float t = (player.health - (i * 5.0f)) / 5.0f;
                finalColor.r = lerp(healthDim.r, healthFull.r, t);
                finalColor.g = lerp(healthDim.g, healthFull.g, t);
                finalColor.b = lerp(healthDim.b, healthFull.b, t);
            }
            state.key_colors[id] = finalColor;
        }
    }

    const RGBColor hungerDim = { 30, 15, 0 };
    const RGBColor hungerFull = { 255, 165, 0 };
    for (size_t i = 0; i < hungerLeds.size(); i++) {
        if (namedKeyMap.count(hungerLeds[i])) {
            GenericLedId id = namedKeyMap.at(hungerLeds[i]);
            float t = (player.hunger - (i * 5.0f)) / 5.0f;
            RGBColor finalColor;
            finalColor.r = lerp(hungerDim.r, hungerFull.r, t);
            finalColor.g = lerp(hungerDim.g, hungerFull.g, t);
            finalColor.b = lerp(hungerDim.b, hungerFull.b, t);
            state.key_colors[id] = finalColor;
        }
    }
}

void EffectPainter::paintHealthEffects(FrameState& state) {
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
    if (!isLowHealthNow) return;

    const std::vector<NamedKey> heartRedKeys = { NamedKey::N4, NamedKey::N5, NamedKey::N6, NamedKey::N7, NamedKey::E, NamedKey::T, NamedKey::U, NamedKey::D, NamedKey::F, NamedKey::H, NamedKey::V, NamedKey::B, NamedKey::Space };
    const std::vector<NamedKey> heartWhiteKeys = { NamedKey::R, NamedKey::Y, NamedKey::G };

    auto now = std::chrono::steady_clock::now();
    if (std::chrono::duration_cast<std::chrono::milliseconds>(now - lastBeatStep).count() > 40) {
        if (isBeatingUp) {
            brightness += 0.1f;
            if (brightness >= 1.0f) { brightness = 1.0f; isBeatingUp = false; }
        }
        else {
            brightness -= 0.1f;
            if (brightness <= 0.1f) { brightness = 0.1f; isBeatingUp = true; }
        }
        lastBeatStep = now;
    }

    for (auto key : heartRedKeys) {
        if (namedKeyMap.count(key)) {
            state.key_colors[namedKeyMap.at(key)] = { static_cast<int>(255 * brightness), 0, 0 };
        }
    }
    for (auto key : heartWhiteKeys) {
        if (namedKeyMap.count(key)) {
            state.key_colors[namedKeyMap.at(key)] = { static_cast<int>(255 * brightness), static_cast<int>(255 * brightness), static_cast<int>(255 * brightness) };
        }
    }
}

void EffectPainter::paintPlayerEffects(FrameState& state) {
    if (!player.inGame) return;
    const std::vector<NamedKey> leds = { NamedKey::W, NamedKey::A, NamedKey::S, NamedKey::D, NamedKey::LeftControl, NamedKey::LeftShift, NamedKey::Space };
    RGBColor keyColor = { 255, 255, 255 };
    if (player.currentBlock == "block.minecraft.water") keyColor = { 0, 100, 255 };
    else if (player.currentBlock == "block.minecraft.lava" || player.currentBlock == "block.minecraft.fire") keyColor = { 255, 0, 0 };

    for (const auto& key : leds) {
        if (namedKeyMap.count(key)) {
            state.key_colors[namedKeyMap.at(key)] = keyColor;
        }
    }
}