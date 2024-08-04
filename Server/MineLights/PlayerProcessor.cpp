#include <iostream>
#include <fstream>
#include <WS2tcpip.h>
#pragma comment (lib, "ws2_32.lib")
#include <bitset>
// disable deprecation
#pragma warning(disable: 4996)

#include <json.hpp>
#include "PlayerProcessor.h"
#include <thread>
#include "Player.h"
#include "iCueLightController.h"
#include "MysticLight.h"

using json = nlohmann::json;

static void UDPServer() {
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
    sockaddr_in client{};
    int clientLength = sizeof(client);

    // while loop
    while (true) {
        // Receive data
        char buf[1025];
        ZeroMemory(buf, sizeof(buf));
        int n = recvfrom(in, buf, sizeof(buf) - 1, 0, (sockaddr*)&client, &clientLength);
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
        player.experience = receivedJson["experience"];
        player.weather = receivedJson["weather"];
        player.currentBlock = receivedJson["currentBlock"];
        player.currentBiome = receivedJson["currentBiome"];
        player.isOnFire = receivedJson["isOnFire"];
        player.isPoisoned = receivedJson["isPoisoned"];
        player.isWithering = receivedJson["isWithering"];
        player.isTakingDamage = receivedJson["isTakingDamage"];
    }

    closesocket(in);
    WSACleanup();
}

void RuniCueController() {
    iCueLightController();
}

void RunMysticLightController() {
    MysticLightController();
}

PlayerProcessor::PlayerProcessor() {
    std::thread t1(UDPServer);
    std::thread t2(RuniCueController);
    //std::thread t3(RunMysticLightController);
    t1.join();
    t2.join();
    //t3.join();
}