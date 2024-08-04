// cuesdk includes
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

#include <json.hpp>
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
                return { CLI_Invalid, 100, 100, 100 }; // White
            }
            else if (b.hasRain) {
                return { CLI_Invalid, 0, 0, 255 }; // Blue
            }
            else {
                return determineBiomeColor(b.name);
            }
        }
    }
}

bool isPlayerInRainyBiome(const std::string& biome) {
	for (const auto& b : biomes) {
		if (b.name == biome) {
			return b.hasRain;
		}
	}

	return false;
}

bool isPlayerInSpecialBlock(const std::string& blockName) {
    if (blockName == "block.minecraft.nether_portal" || blockName == "block.minecraft.end_portal" || blockName == "block.minecraft.lava" || blockName == "block.minecraft.fire") {
        return true;
    } else {
        return false;
    }
}

void worldEffects() {
    while (true) {
        if (!player.inGame) {
			CorsairLedColor mojangRed = { CLI_Invalid, 255, 0, 0 };

			for (int i = 0; i < CLI_Last; i++)
			{
				mojangRed.ledId = (CorsairLedId)i;
				CorsairSetLedsColors(1, &mojangRed);
			}
			continue;
        } else {
            if (player.currentBlock == "block.minecraft.nether_portal") {
                for (int i = 0; i < CLI_Last; i++) {
                    CorsairLedColor color;
                    if (rand() % 10 < 2) { // 20% chance to twinkle
                        color = { CLI_Invalid, 50, 0, 100 }; // Deeper purple color
                    } else {
                        color = { CLI_Invalid, 128, 0, 128 }; // Nether portal color
                    }
                    color.ledId = static_cast<CorsairLedId>(i);
                    CorsairSetLedsColors(1, &color);
                }
            } else if (player.currentBlock == "block.minecraft.end_portal") {
                for (int i = 0; i < CLI_Last; i++) {
                    CorsairLedColor color;
                    if (rand() % 10 < 2) { // 20% chance to twinkle
                        color = { CLI_Invalid, 50, 50, 50 }; // Low-intensity white color
                    } else {
                        color = { CLI_Invalid, 0, 0, 50 }; // Very dark blue color
                    }
                    color.ledId = static_cast<CorsairLedId>(i);
                    CorsairSetLedsColors(1, &color);
                }
            } else if (player.isOnFire) {
                for (int i = 0; i < CLI_Last; i++) {
                    CorsairLedColor color;
                    if (rand() % 10 < 2) { // 20% chance to twinkle
                        color = { CLI_Invalid, 200, 0, 0 }; // Deeper red color
                    }
                    else {
                        color = { CLI_Invalid, 255, 69, 0 }; // Lava/fire color
                    }
                    color.ledId = static_cast<CorsairLedId>(i);
                    CorsairSetLedsColors(1, &color);
                }
            }
            while (!isPlayerInSpecialBlock(player.currentBlock) && player.inGame) {
                CorsairLedColor biomeColor = determineBiomeColor(player.currentBiome);

                // Set the LED colors to the biome color
                for (int i = 0; i < CLI_Last; i++) {
                    biomeColor.ledId = (CorsairLedId)i;
                    CorsairSetLedsColors(1, &biomeColor);
                }

                // Handle weather effects
                while ((player.weather == "Rain" || player.weather == "Thunderstorm") && !isPlayerInSpecialBlock(player.currentBlock) && isPlayerInRainyBiome(player.currentBiome)) {
                    // Update the biome color again in case the biome has changed
                    biomeColor = determineBiomeColor(player.currentBiome);

                    // Create an alternating pattern
                    for (int i = 0; i < CLI_Last; i++) {
                        CorsairLedColor patternColor;
                        if (i % 2 == 0) {
                            // Even LEDs: Set to rain color
                            patternColor = getBiomeRainColor(player.currentBiome);
                        }
                        else {
                            // Odd LEDs: Set to biome color
                            patternColor = biomeColor;
                        }
                        patternColor.ledId = static_cast<CorsairLedId>(i);
                        CorsairSetLedsColors(1, &patternColor);
                    }
                    std::this_thread::sleep_for(std::chrono::milliseconds(500));

                    // Random flash if thunderstorm
                    if (player.weather == "Thunderstorm") {
                        if (rand() % 2 == 0) {
                            for (int i = 0; i < CLI_Last; i++) {
                                CorsairLedColor flashColor = { CLI_Invalid, 255, 255, 255 };
                                flashColor.ledId = static_cast<CorsairLedId>(i);
                                CorsairSetLedsColors(1, &flashColor);
                            }
                            std::this_thread::sleep_for(std::chrono::milliseconds(50));
                        }
                    }

                    // Continue the alternating pattern
                    for (int i = 0; i < CLI_Last; i++) {
                        CorsairLedColor patternColor;
                        if (i % 2 == 0) {
                            // Even LEDs: Set to biome color
                            patternColor = biomeColor;
                        }
                        else {
                            // Odd LEDs: Set to rain color
                            patternColor = getBiomeRainColor(player.currentBiome);
                        }
                        patternColor.ledId = static_cast<CorsairLedId>(i);
                        CorsairSetLedsColors(1, &patternColor);
                    }
                    std::this_thread::sleep_for(std::chrono::milliseconds(500));
                }
            }
        }
    }
}

void setLedColorWithBrightness(CorsairLedId ledId, int r, int g, int b, float brightness) {
    CorsairLedColor color = { ledId, static_cast<int>(r * brightness), static_cast<int>(g * brightness), static_cast<int>(b * brightness) };
    CorsairSetLedsColors(1, &color);
}

void playerEffects() {
    const std::vector<CorsairLedId> movementLeds = { CorsairLedId::CLK_W, CorsairLedId::CLK_A, CorsairLedId::CLK_S, CorsairLedId::CLK_D, CorsairLedId::CLK_LeftCtrl, CorsairLedId::CLK_LeftShift, CorsairLedId::CLK_Space };

    while (true) {
        if (player.inGame) {
            if (player.currentBlock == "block.minecraft.water") {
                for (auto led : movementLeds) {
                    setLedColorWithBrightness(led, 0, 0, 255, 1.0f);
                }
            } else if (player.currentBlock == "block.minecraft.lava" || player.currentBlock == "block.minecraft.fire") {
                for (auto led : movementLeds) {
					setLedColorWithBrightness(led, 255, 0, 0, 1.0f);
				}
			} else {
				for (auto led : movementLeds) {
					setLedColorWithBrightness(led, 255, 255, 255, 1.0f);
				}
			}
        }
    }
}

void healthEffects() {
    const std::vector<CorsairLedId> heartRedLeds = { CorsairLedId::CLK_4, CorsairLedId::CLK_5, CorsairLedId::CLK_6, CorsairLedId::CLK_7, CorsairLedId::CLK_E, CorsairLedId::CLK_T, CorsairLedId::CLK_U, CorsairLedId::CLK_D, CorsairLedId::CLK_H, CorsairLedId::CLK_V, CorsairLedId::CLK_B, CorsairLedId::CLK_Space };
    const std::vector<CorsairLedId> heartWhiteLeds = { CorsairLedId::CLK_R, CorsairLedId::CLK_F, CorsairLedId::CLK_Y, CorsairLedId::CLK_G };

	while (true) {
		if (player.inGame) {
            if (player.health < 10) {
                // Heartbeat pattern: increase and decrease brightness
                for (float brightness = 0.0f; brightness <= 1.0f; brightness += 0.1f) {
                    for (auto led : heartRedLeds) {
                        setLedColorWithBrightness(led, 255, 0, 0, brightness);
                    }
                    for (auto led : heartWhiteLeds) {
                        setLedColorWithBrightness(led, 255, 255, 255, brightness);
                    }
                    std::this_thread::sleep_for(std::chrono::milliseconds(50));
                }
                for (float brightness = 1.0f; brightness >= 0.0f; brightness -= 0.1f) {
                    for (auto led : heartRedLeds) {
                        setLedColorWithBrightness(led, 255, 0, 0, brightness);
                    }
                    for (auto led : heartWhiteLeds) {
                        setLedColorWithBrightness(led, 255, 255, 255, brightness);
                    }
                    std::this_thread::sleep_for(std::chrono::milliseconds(50));
                }
            }
		}
	}
}

void playerBarEffects() {
	const std::vector<CorsairLedId> healthLeds = { CorsairLedId::CLK_F1, CorsairLedId::CLK_F2, CorsairLedId::CLK_F3, CorsairLedId::CLK_F4 };
    const std::vector<CorsairLedId> hungerLeds = { CorsairLedId::CLK_F5, CorsairLedId::CLK_F6, CorsairLedId::CLK_F7, CorsairLedId::CLK_F8 };

	while (true) {
        if (player.inGame) {
            for (int i = 0; i < 4; i++) {
				CorsairLedColor healthColor = { healthLeds[i], 30, 0, 0 };
				CorsairLedColor hungerColor = { hungerLeds[i], 30, 15, 0 };

				if (player.health > i * 5) {
                    if (player.isPoisoned) {
                        healthColor = { healthLeds[i], 148, 120, 24 };
                    } else {
                        healthColor = { healthLeds[i], 255, 0, 0 };
                    }
				}

				if (player.hunger > i * 5) {
					hungerColor = { hungerLeds[i], 255, 165, 0 };
				}

                if (player.isWithering) {
                    healthColor = { healthLeds[i], 43, 43, 43 };
                }

				CorsairSetLedsColors(1, &healthColor);
				CorsairSetLedsColors(1, &hungerColor);
			}
        }
	}
}

void experienceBar() {
    const std::vector<CorsairLedId> experienceLeds = { CorsairLedId::CLK_1, CorsairLedId::CLK_2, CorsairLedId::CLK_3, CorsairLedId::CLK_4, CorsairLedId::CLK_5, CorsairLedId::CLK_6, CorsairLedId::CLK_7, CorsairLedId::CLK_8, CorsairLedId::CLK_9, CorsairLedId::CLK_0 };

    while (true) {
        if (player.inGame) {
            int ledsToLight = static_cast<int>(player.experience * experienceLeds.size());

            for (int i = 0; i < experienceLeds.size(); i++) {
                CorsairLedColor experienceColor = { experienceLeds[i], 0, 0, 0 };

                if (i < ledsToLight) {
                    experienceColor = { experienceLeds[i], 0, 255, 0 };
                }

                CorsairSetLedsColors(1, &experienceColor);
            }
        }
    }
}

iCueLightController::iCueLightController()
{       
    // initialize SDK
    CorsairPerformProtocolHandshake();
    CorsairRequestControl(CAM_ExclusiveLightingControl);

    std::thread t1(worldEffects);
    std::thread t2(playerEffects);
    std::thread t3(healthEffects);
    std::thread t4(playerBarEffects);
    std::thread t5(experienceBar);
    t1.join();
    t2.join();
    t3.join();
    t4.join();
    t5.join();
}
