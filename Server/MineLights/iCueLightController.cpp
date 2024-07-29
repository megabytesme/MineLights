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
    int hunger;
    std::string weather;
    std::string currentBlock;
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
    }

    closesocket(in);
    WSACleanup();
}

void worldLevelEffects() {
    while (true) {
        if (player.weather == "Rain") {

        }
        else {
            if (player.worldLevel == "overworld") {
                CorsairLedColor overworldColor = { CLI_Invalid, 0, 255, 0 };
                for (int i = 0; i < CLI_Last; i++)
                {
                    overworldColor.ledId = (CorsairLedId)i;
                    CorsairSetLedsColors(1, &overworldColor);
                }
            }
            else if (player.worldLevel == "the_nether") {
                CorsairLedColor netherColor = { CLI_Invalid, 255, 0, 0 };
                for (int i = 0; i < CLI_Last; i++)
                {
                    netherColor.ledId = (CorsairLedId)i;
                    CorsairSetLedsColors(1, &netherColor);
                }
            }
            else if (player.worldLevel == "the_end") {
                CorsairLedColor endColor = { CLI_Invalid, 128, 0, 128 };
                for (int i = 0; i < CLI_Last; i++)
                {
                    endColor.ledId = (CorsairLedId)i;
                    CorsairSetLedsColors(1, &endColor);
                }
            }
        }

    }
}

void weatherEffects() {
    while (true) {
        if (player.weather == "clear") {
            // Clear weather, no effects
        }
        else if (player.weather == "Rain") {
            // Create an alternating pattern
            for (int round = 0; round < 3; round++) {
                for (int i = 0; i < CLI_Last; i++) {
                    CorsairLedColor patternColor;
                    if (i % 2 == 0) {
                        // Even LEDs: Set to blue
                        patternColor = { CLI_Invalid, 0, 0, 255 };
                    }
                    else {
                        // Odd LEDs: Set to green
                        patternColor = { CLI_Invalid, 0, 255, 0 };
                    }
                    patternColor.ledId = static_cast<CorsairLedId>(i);
                    CorsairSetLedsColors(1, &patternColor);
                }
                std::this_thread::sleep_for(std::chrono::seconds(1));

                for (int i = 0; i < CLI_Last; i++) {
                    CorsairLedColor patternColor;
                    if (i % 2 == 0) {
                        // Even LEDs: Set to green
                        patternColor = { CLI_Invalid, 0, 255, 0 };
                    }
                    else {
                        // Odd LEDs: Set to blue
                        patternColor = { CLI_Invalid, 0, 0, 255 };
                    }
                    patternColor.ledId = static_cast<CorsairLedId>(i);
                    CorsairSetLedsColors(1, &patternColor);
                }
                std::this_thread::sleep_for(std::chrono::seconds(1));
            }
        }
        else if (player.weather == "Thunderstorm") {
            // Create an alternating pattern with random flashes
            for (int round = 0; round < 3; round++) {
                for (int i = 0; i < CLI_Last; i++) {
                    CorsairLedColor patternColor;
                    if (i % 2 == 0) {
                        // Even LEDs: Set to blue
                        patternColor = { CLI_Invalid, 0, 0, 255 };
                    }
                    else {
                        // Odd LEDs: Set to green
                        patternColor = { CLI_Invalid, 0, 255, 0 };
                    }
                    patternColor.ledId = static_cast<CorsairLedId>(i);
                    CorsairSetLedsColors(1, &patternColor);
                }
                std::this_thread::sleep_for(std::chrono::seconds(1));

                // Random flash
                if (rand() % 2 == 0) {
                    for (int i = 0; i < CLI_Last; i++) {
                        CorsairLedColor flashColor = { CLI_Invalid, 255, 255, 255 };
                        flashColor.ledId = static_cast<CorsairLedId>(i);
                        CorsairSetLedsColors(1, &flashColor);
                    }
                    std::this_thread::sleep_for(std::chrono::milliseconds(100));
                }

                for (int i = 0; i < CLI_Last; i++) {
                    CorsairLedColor patternColor;
                    if (i % 2 == 0) {
                        // Even LEDs: Set to green
                        patternColor = { CLI_Invalid, 0, 255, 0 };
                    }
                    else {
                        // Odd LEDs: Set to blue
                        patternColor = { CLI_Invalid, 0, 0, 255 };
                    }
                    patternColor.ledId = static_cast<CorsairLedId>(i);
                    CorsairSetLedsColors(1, &patternColor);
                }
                std::this_thread::sleep_for(std::chrono::seconds(1));
            }
        }
    }
}

iCueLightController::iCueLightController()
{       
    // initialize SDK
    CorsairPerformProtocolHandshake();
    CorsairRequestControl(CAM_ExclusiveLightingControl);

    CorsairLedColor netherPortalBase = { CLI_Invalid, 255, 0, 255 };
    CorsairLedColor mojangRed = { CLI_Invalid, 255, 0, 0 };

    for (int i = 0; i < CLI_Last; i++)
    {
        mojangRed.ledId = (CorsairLedId)i;
        CorsairSetLedsColors(1, &mojangRed);
    }

    // start thread to recieve UDP
    std::thread t1(receiveUDP);
    std::thread t2(worldLevelEffects);
    std::thread t3(weatherEffects);
    t1.join();
    t2.join();
    t3.join();
}
