#include <iostream>
#include <fstream>
#include <WS2tcpip.h>
#pragma comment (lib, "ws2_32.lib")
#include <bitset>
#pragma warning(disable: 4996)

#include "json.hpp"
#include "PlayerProcessor.h"
#include <thread>
#include "Player.h"
#include "LightingManager.h"

using json = nlohmann::json;

static void UDPServer() {
    WSADATA data;
    WORD version = MAKEWORD(2, 2);
    int wsOk = WSAStartup(version, &data);
    if (wsOk != 0)
    {
        throw wsOk;
        return;
    }

    SOCKET in = socket(AF_INET, SOCK_DGRAM, 0);
    sockaddr_in serverHint;
    serverHint.sin_addr.S_un.S_addr = ADDR_ANY;
    serverHint.sin_family = AF_INET;
    serverHint.sin_port = htons(63212);

    if (bind(in, (sockaddr*)&serverHint, sizeof(serverHint)) == SOCKET_ERROR)
    {
        throw WSAGetLastError();
        return;
    }

    sockaddr_in client{};
    int clientLength = sizeof(client);

    while (true) {
        char buf[1025];
        ZeroMemory(buf, sizeof(buf));
        int n = recvfrom(in, buf, sizeof(buf) - 1, 0, (sockaddr*)&client, &clientLength);
        if (n == SOCKET_ERROR) {
            throw WSAGetLastError();
            continue;
        }

        buf[n] = '\0';
        try {
            json receivedJson = json::parse(buf);

            player.inGame = receivedJson.value("inGame", false);
            player.health = receivedJson.value("health", 0.0f);
            player.hunger = receivedJson.value("hunger", 0.0f);
            player.experience = receivedJson.value("experience", 0.0f);
            player.weather = receivedJson.value("weather", "");
            player.currentBlock = receivedJson.value("currentBlock", "");
            player.currentBiome = receivedJson.value("currentBiome", "");
            player.isOnFire = receivedJson.value("isOnFire", false);
            player.isPoisoned = receivedJson.value("isPoisoned", false);
            player.isWithering = receivedJson.value("isWithering", false);
            player.isTakingDamage = receivedJson.value("isTakingDamage", false);
        }
        catch (const json::parse_error& e) {
            // ignore bad json
        }
    }

    closesocket(in);
    WSACleanup();
}

PlayerProcessor::PlayerProcessor() {
    udpServerThread = std::thread(UDPServer);
    lightingManager = std::make_unique<LightingManager>();
    udpServerThread.detach();
}

PlayerProcessor::~PlayerProcessor() {
}