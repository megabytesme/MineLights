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
    std::string worldLevel;
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
        player.worldLevel = receivedJson["worldLevel"];
        player.health = receivedJson["health"];
        player.hunger = receivedJson["hunger"];
        player.weather = receivedJson["weather"];
        player.currentBlock = receivedJson["currentBlock"];
        player.currentBiome = receivedJson["currentBiome"];
    }

    closesocket(in);
    WSACleanup();
}

CorsairLedColor determineBiomeColor(const std::string& biome) {
    // Map of biomes to colors
    std::map<std::string, CorsairLedColor> netherBiomes;
    std::map<std::string, CorsairLedColor> overworldBiomes;
    std::map<std::string, CorsairLedColor> endBiomes;

    // Add biomes to respective maps
    netherBiomes["minecraft:nether_wastes"] = { CLI_Invalid, 230, 30, 5 };
    netherBiomes["minecraft:crimson_forest"] = { CLI_Invalid, 255, 20, 5 };
    netherBiomes["minecraft:warped_forest"] = { CLI_Invalid, 0, 128, 128 };
    netherBiomes["minecraft:soul_sand_valley"] = { CLI_Invalid, 194, 178, 128 };
    netherBiomes["minecraft:basalt_deltas"] = { CLI_Invalid, 128, 128, 128 };
    overworldBiomes["minecraft:plains"] = { CLI_Invalid, 124, 252, 0 };
    overworldBiomes["minecraft:forest"] = { CLI_Invalid, 34, 139, 34 };
    overworldBiomes["minecraft:mountains"] = { CLI_Invalid, 169, 169, 169 };
    overworldBiomes["minecraft:desert"] = { CLI_Invalid, 237, 201, 175 };
    overworldBiomes["minecraft:ocean"] = { CLI_Invalid, 0, 105, 148 };
    overworldBiomes["minecraft:jungle"] = { CLI_Invalid, 0, 100, 0 };
    overworldBiomes["minecraft:savanna"] = { CLI_Invalid, 244, 164, 96 };
    overworldBiomes["minecraft:badlands"] = { CLI_Invalid, 210, 105, 30 };
    overworldBiomes["minecraft:swamp"] = { CLI_Invalid, 32, 178, 170 };
    overworldBiomes["minecraft:taiga"] = { CLI_Invalid, 0, 128, 128 };
    overworldBiomes["minecraft:snowy_tundra"] = { CLI_Invalid, 255, 250, 250 };
    overworldBiomes["minecraft:mushroom_fields"] = { CLI_Invalid, 255, 0, 255 };
    overworldBiomes["minecraft:beach"] = { CLI_Invalid, 238, 214, 175 };
    overworldBiomes["minecraft:river"] = { CLI_Invalid, 0, 191, 255 };
    overworldBiomes["minecraft:dark_forest"] = { CLI_Invalid, 0, 100, 0 };
    overworldBiomes["minecraft:birch_forest"] = { CLI_Invalid, 255, 255, 255 };
    overworldBiomes["minecraft:swamp_hills"] = { CLI_Invalid, 32, 178, 170 };
    overworldBiomes["minecraft:taiga_hills"] = { CLI_Invalid, 0, 128, 128 };
    overworldBiomes["minecraft:snowy_taiga"] = { CLI_Invalid, 255, 250, 250 };
    overworldBiomes["minecraft:giant_tree_taiga"] = { CLI_Invalid, 34, 139, 34 };
    overworldBiomes["minecraft:wooded_mountains"] = { CLI_Invalid, 169, 169, 169 };
    overworldBiomes["minecraft:savanna_plateau"] = { CLI_Invalid, 244, 164, 96 };
    overworldBiomes["minecraft:shattered_savanna"] = { CLI_Invalid, 244, 164, 96 };
    overworldBiomes["minecraft:bamboo_jungle"] = { CLI_Invalid, 0, 100, 0 };
    overworldBiomes["minecraft:snowy_taiga_hills"] = { CLI_Invalid, 255, 250, 250 };
    overworldBiomes["minecraft:giant_spruce_taiga"] = { CLI_Invalid, 34, 139, 34 };
    overworldBiomes["minecraft:snowy_beach"] = { CLI_Invalid, 255, 250, 250 };
    overworldBiomes["minecraft:stone_shore"] = { CLI_Invalid, 169, 169, 169 };
    overworldBiomes["minecraft:warm_ocean"] = { CLI_Invalid, 0, 105, 148 };
    overworldBiomes["minecraft:lukewarm_ocean"] = { CLI_Invalid, 0, 105, 148 };
    overworldBiomes["minecraft:cold_ocean"] = { CLI_Invalid, 0, 105, 148 };
    overworldBiomes["minecraft:deep_ocean"] = { CLI_Invalid, 0, 0, 128 };
    overworldBiomes["minecraft:deep_lukewarm_ocean"] = { CLI_Invalid, 0, 0, 128 };
    overworldBiomes["minecraft:deep_cold_ocean"] = { CLI_Invalid, 0, 0, 128 };
    overworldBiomes["minecraft:deep_frozen_ocean"] = { CLI_Invalid, 173, 216, 230 };
    endBiomes["minecraft:the_end"] = { CLI_Invalid, 128, 0, 128 };
    endBiomes["minecraft:end_highlands"] = { CLI_Invalid, 128, 0, 128 };
    endBiomes["minecraft:end_midlands"] = { CLI_Invalid, 128, 0, 128 };
    endBiomes["minecraft:end_barrens"] = { CLI_Invalid, 128, 0, 128 };
    endBiomes["minecraft:small_end_islands"] = { CLI_Invalid, 128, 0, 128 };

    if (overworldBiomes.find(biome) != overworldBiomes.end()) {
        return overworldBiomes[biome];
    }
    else if (netherBiomes.find(biome) != netherBiomes.end()) {
        return netherBiomes[biome];
    }
    else if (endBiomes.find(biome) != endBiomes.end()) {
        return endBiomes[biome];
    }
    else {
        // Default color if biome is not found
        return { CLI_Invalid, 255, 255, 255 };
    }
}

void worldEffects() {
    while (true) {
        CorsairLedColor biomeColor = determineBiomeColor(player.currentBiome);

        // Set the LED colors to the biome color
        for (int i = 0; i < CLI_Last; i++) {
            biomeColor.ledId = (CorsairLedId)i;
            CorsairSetLedsColors(1, &biomeColor);
        }

        // Handle weather effects
        while (player.weather == "Rain" || player.weather == "Thunderstorm") {
            // Update the biome color again in case the biome has changed
            biomeColor = determineBiomeColor(player.currentBiome);

            // Create an alternating pattern
            for (int i = 0; i < CLI_Last; i++) {
                CorsairLedColor patternColor;
                if (i % 2 == 0) {
                    // Even LEDs: Set to blue
                    patternColor = { CLI_Invalid, 0, 0, 255 };
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
                    // Odd LEDs: Set to blue
                    patternColor = { CLI_Invalid, 0, 0, 255 };
                }
                patternColor.ledId = static_cast<CorsairLedId>(i);
                CorsairSetLedsColors(1, &patternColor);
            }
            std::this_thread::sleep_for(std::chrono::milliseconds(500));
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

iCueLightController::iCueLightController()
{       
    // initialize SDK
    CorsairPerformProtocolHandshake();
    CorsairRequestControl(CAM_ExclusiveLightingControl);

    CorsairLedColor mojangRed = { CLI_Invalid, 255, 0, 0 };

    for (int i = 0; i < CLI_Last; i++)
    {
        mojangRed.ledId = (CorsairLedId)i;
        CorsairSetLedsColors(1, &mojangRed);
    }

    // start thread to recieve UDP
    std::thread t1(receiveUDP);
    std::thread t2(worldEffects);
    std::thread t3(playerEffects);
    t1.join();
    t2.join();
    t3.join();
}
