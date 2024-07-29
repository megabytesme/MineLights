#include <iostream>
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

#include <json.hpp>
#include "iCueLightController.h"

using json = nlohmann::json;

void receiveUDP() {
    // Initialise Winsock
    WSADATA data;
    WORD version = MAKEWORD(2, 2);

    // Start Winsock
    int wsOk = WSAStartup(version, &data);
    if (wsOk != 0)
    {
        // Broken, exit
        wsOk;
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
        WSAGetLastError();
        return;
    }

    // Create a sockaddr_in structure for the client
    sockaddr_in client;
    int clientLength = sizeof(client);

    // while loop
    while (true) {
        // Receive data
        char buf[1025];
        int n = recvfrom(in, buf, sizeof(buf), 0, (sockaddr*)&client, &clientLength);
        if (n == SOCKET_ERROR) {
            int result = WSAGetLastError();
            break;
        }

        // Process the received data
        try {
            json jsonData = json::parse(buf);
            std::string key = jsonData["key"];
            std::string value = jsonData["value"];

            std::cout << "Received key: " << key << ", value: " << value << std::endl;
        }
        catch (json::parse_error& e) {
            std::cerr << "JSON parse error: " << e.what() << std::endl;
        }
    }

    closesocket(in);
    WSACleanup();
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
    t1.join();
}
