#include <iostream>
#include <fstream>
#include <WS2tcpip.h>
#pragma comment (lib, "ws2_32.lib")
#include <bitset>
// disable deprecation
#pragma warning(disable: 4996)

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

using json = nlohmann::json;

struct PlayerDto {
    bool inGame;
    float health;
    float hunger;
    std::string weather;
    std::string currentBlock;
    std::string currentBiome;
};

PlayerDto player;

void receiveUDP() {
    // Initialise Winsock
    WSADATA data;
    WORD version = MAKEWORD(2, 2);

    // Start Winsock
    int wsOk = WSAStartup(version, &data);
    if (wsOk != 0)
    {
        // Broken, exit
        throw wsOk;
        return;
    }

    // Create a hint structure for the server
    SOCKET in = socket(AF_INET, SOCK_DGRAM, 0);
    sockaddr_in serverHint;
    serverHint.sin_addr.S_un.S_addr = ADDR_ANY;
    serverHint.sin_family = AF_INET;
    serverHint.sin_port = htons(63212);

    // Bind the socket to an IP address and port
    if (bind(in, (sockaddr*)&serverHint, sizeof(serverHint)) == SOCKET_ERROR)
    {
        throw WSAGetLastError();
        return;
    }

    // Create a sockaddr_in structure for the client
    sockaddr_in client;
    int clientLength = sizeof(client);

    // while loop
    while (true) {
        // Receive data
        char buf[1025];
        ZeroMemory(buf, 1025);
        int n = recvfrom(in, buf, sizeof(buf), 0, (sockaddr*)&client, &clientLength);
        if (n == SOCKET_ERROR) {
            throw WSAGetLastError();
            continue;
        }

        // Null-terminate the received data
        buf[n] = '\0';

        // Parse the received data as JSON
        json receivedJson = json::parse(buf);

        // Process the received data and turn it into a "playerDto" object
        player.inGame = receivedJson["inGame"];
        player.health = receivedJson["health"];
        player.hunger = receivedJson["hunger"];
        player.weather = receivedJson["weather"];
        player.currentBlock = receivedJson["currentBlock"];
        player.currentBiome = receivedJson["currentBiome"];
    }

    closesocket(in);
    WSACleanup();
}

// Define the Biome structure with RGB color properties
struct Biome {
    std::string name;
    bool hasRain;
    bool isSnowy;
    int red;
    int green;
    int blue;
};

// List of biomes with their properties and RGB colors
std::vector<Biome> biomes = {
    {"minecraft:plains", true, false, 124, 252, 0},
    {"minecraft:forest", true, false, 34, 139, 34},
    {"minecraft:mountains", true, true, 169, 169, 169},
    {"minecraft:desert", false, false, 237, 201, 175},
    {"minecraft:ocean", true, false, 0, 105, 148},
    {"minecraft:jungle", true, false, 0, 100, 0},
    {"minecraft:savanna", false, false, 244, 164, 96},
    {"minecraft:badlands", false, false, 210, 105, 30},
    {"minecraft:swamp", true, false, 32, 178, 170},
    {"minecraft:taiga", true, true, 0, 128, 128},
    {"minecraft:snowy_tundra", true, true, 255, 250, 250},
    {"minecraft:mushroom_fields", true, false, 255, 0, 255},
    {"minecraft:beach", true, false, 238, 214, 175},
    {"minecraft:river", true, false, 0, 191, 255},
    {"minecraft:dark_forest", true, false, 0, 100, 0},
    {"minecraft:birch_forest", true, false, 255, 255, 255},
    {"minecraft:swamp_hills", true, false, 32, 178, 170},
    {"minecraft:taiga_hills", true, true, 0, 128, 128},
    {"minecraft:snowy_taiga", true, true, 255, 250, 250},
    {"minecraft:giant_tree_taiga", true, false, 34, 139, 34},
    {"minecraft:wooded_mountains", true, true, 169, 169, 169},
    {"minecraft:savanna_plateau", false, false, 244, 164, 96},
    {"minecraft:shattered_savanna", false, false, 244, 164, 96},
    {"minecraft:bamboo_jungle", true, false, 0, 100, 0},
    {"minecraft:snowy_taiga_hills", true, true, 255, 250, 250},
    {"minecraft:giant_spruce_taiga", true, false, 34, 139, 34},
    {"minecraft:snowy_beach", true, true, 255, 250, 250},
    {"minecraft:stone_shore", true, false, 169, 169, 169},
    {"minecraft:warm_ocean", true, false, 0, 105, 148},
    {"minecraft:lukewarm_ocean", true, false, 0, 105, 148},
    {"minecraft:cold_ocean", true, false, 0, 105, 148},
    {"minecraft:deep_ocean", true, false, 0, 0, 128},
    {"minecraft:deep_lukewarm_ocean", true, false, 0, 0, 128},
    {"minecraft:deep_cold_ocean", true, false, 0, 0, 128},
    {"minecraft:deep_frozen_ocean", true, true, 173, 216, 230},
    {"minecraft:the_void", false, false, 10, 10, 10},
    {"minecraft:sunflower_plains", true, false, 255, 223, 0},
    {"minecraft:snowy_plains", true, true, 255, 250, 250},
    {"minecraft:ice_spikes", true, true, 173, 216, 230},
    {"minecraft:mangrove_swamp", true, false, 34, 139, 34},
    {"minecraft:flower_forest", true, false, 255, 182, 193},
    {"minecraft:old_growth_birch_forest", true, false, 255, 255, 255},
    {"minecraft:old_growth_pine_taiga", true, false, 0, 100, 0},
    {"minecraft:old_growth_spruce_taiga", true, false, 34, 139, 34},
    {"minecraft:sparse_jungle", true, false, 0, 100, 0},
    {"minecraft:eroded_badlands", false, false, 210, 105, 30},
    {"minecraft:wooded_badlands", false, false, 244, 164, 96},
    {"minecraft:meadow", true, false, 124, 252, 0},
    {"minecraft:cherry_grove", true, false, 255, 182, 193},
    {"minecraft:grove", true, false, 0, 100, 0},
    {"minecraft:snowy_slopes", true, true, 255, 250, 250},
    {"minecraft:frozen_peaks", true, true, 173, 216, 230},
    {"minecraft:jagged_peaks", true, false, 0, 100, 0},
    {"minecraft:stony_peaks", true, false, 169, 169, 169},
    {"minecraft:frozen_river", true, true, 255, 250, 250},
    {"minecraft:stony_shore", true, false, 169, 169, 169},
    {"minecraft:dripstone_caves", true, false, 0, 100, 0},
    {"minecraft:lush_caves", true, false, 255, 182, 193},
    {"minecraft:deep_dark", true, false, 10, 10, 10},
    {"minecraft:nether_wastes", false, false, 230, 30, 5},
    {"minecraft:crimson_forest", false, false, 255, 20, 5},
    {"minecraft:warped_forest", false, false, 0, 128, 128},
    {"minecraft:soul_sand_valley", false, false, 194, 178, 128},
    {"minecraft:basalt_deltas", false, false, 128, 128, 128},
    {"minecraft:end_barrens", false, false, 128, 128, 128},
    {"minecraft:end_highlands", false, false, 128, 128, 128},
    {"minecraft:end_midlands", false, false, 128, 128, 128},
    {"minecraft:small_end_islands", false, false, 128, 128, 128},
    {"minecraft:the_end", false, false, 128, 128, 128}
};

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
                return { CLI_Invalid, 255, 255, 255 }; // White
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
            } else if (player.currentBlock == "block.minecraft.lava" || player.currentBlock == "block.minecraft.fire") {
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
            while (!isPlayerInSpecialBlock(player.currentBlock)) {
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
    const std::vector<CorsairLedId> redLeds = { CorsairLedId::CLK_4, CorsairLedId::CLK_5, CorsairLedId::CLK_6, CorsairLedId::CLK_7, CorsairLedId::CLK_E, CorsairLedId::CLK_T, CorsairLedId::CLK_U, CorsairLedId::CLK_D, CorsairLedId::CLK_H, CorsairLedId::CLK_V, CorsairLedId::CLK_B, CorsairLedId::CLK_Space };
    const std::vector<CorsairLedId> whiteLeds = { CorsairLedId::CLK_R, CorsairLedId::CLK_F, CorsairLedId::CLK_Y, CorsairLedId::CLK_G };

    while (true) {
        if (player.inGame) {
            if (player.health < 10) {
                // Heartbeat pattern: increase and decrease brightness
                for (float brightness = 0.0f; brightness <= 1.0f; brightness += 0.1f) {
                    for (auto led : redLeds) {
                        setLedColorWithBrightness(led, 255, 0, 0, brightness);
                    }
                    for (auto led : whiteLeds) {
                        setLedColorWithBrightness(led, 255, 255, 255, brightness);
                    }
                    std::this_thread::sleep_for(std::chrono::milliseconds(50));
                }
                for (float brightness = 1.0f; brightness >= 0.0f; brightness -= 0.1f) {
                    for (auto led : redLeds) {
                        setLedColorWithBrightness(led, 255, 0, 0, brightness);
                    }
                    for (auto led : whiteLeds) {
                        setLedColorWithBrightness(led, 255, 255, 255, brightness);
                    }
                    std::this_thread::sleep_for(std::chrono::milliseconds(50));
                }
            }
        }
    }
}

iCueLightController::iCueLightController()
{       
    // initialize SDK
    CorsairPerformProtocolHandshake();
    CorsairRequestControl(CAM_ExclusiveLightingControl);

    // start thread to recieve UDP
    std::thread t1(receiveUDP);
    std::thread t2(worldEffects);
    std::thread t3(playerEffects);
    t1.join();
    t2.join();
    t3.join();
}
