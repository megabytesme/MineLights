#define CORSAIR_LIGHTING_SDK_DISABLE_DEPRECATION_WARNINGS

#include <atomic>
#include <thread>
#include <string>
#include <cmath>
#include "iCueLightController.h"
#include "CUESDK.h"

#include <winsock2.h>
#include <ws2tcpip.h>
#include <iostream>
#include <sstream>
#include <json.hpp>

#pragma comment(lib, "Ws2_32.lib")

using namespace std;
using json = nlohmann::json;

void receiveUDP() {
    WSADATA wsaData;
    SOCKET sockfd;
    struct sockaddr_in serverAddr, clientAddr;
    char buffer[1024];
    int addrLen = sizeof(clientAddr);

    // Initialize Winsock
    if (WSAStartup(MAKEWORD(2, 2), &wsaData) != 0) {
        cerr << "WSAStartup failed" << endl;
        return;
    }

    // Create socket
    if ((sockfd = socket(AF_INET, SOCK_DGRAM, 0)) == INVALID_SOCKET) {
        cerr << "Socket creation failed: " << WSAGetLastError() << endl;
        WSACleanup();
        return;
    }

    // Bind the socket to port 9876
    memset(&serverAddr, 0, sizeof(serverAddr));
    serverAddr.sin_family = AF_INET;
    serverAddr.sin_addr.s_addr = INADDR_ANY;
    serverAddr.sin_port = htons(9876);

    while (true) {
        // Receive data
        int n = recvfrom(sockfd, buffer, sizeof(buffer), 0, (struct sockaddr*)&clientAddr, &addrLen);
        if (n == SOCKET_ERROR) {
            cerr << "Receive failed: " << WSAGetLastError() << endl;
            break;
        }
        char buffer[1025];

        // Process the received data
        try {
            json jsonData = json::parse(buffer);
            string key = jsonData["key"];
            string value = jsonData["value"];

            cout << "Received key: " << key << ", value: " << value << endl;


        }
        catch (json::parse_error& e) {
            cerr << "JSON parse error: " << e.what() << endl;
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
    thread t1(receiveUDP);
    t1.join();
}
