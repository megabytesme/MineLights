#include "PlayerProcessor.h"
#include "Logger.h"
#include "json.hpp"
#include <iostream>
#include <vector>
#include <string>
#include <WS2tcpip.h>
#pragma comment (lib, "ws2_32.lib")
#pragma warning(disable: 4996)

using json = nlohmann::json;

void PlayerProcessor::UDPServerLoop() {
    SOCKET inSocket = socket(AF_INET, SOCK_DGRAM, 0);
    if (inSocket == INVALID_SOCKET) {
        LOG("UDP socket creation failed.");
        return;
    }

    int bufferSize = 65536;
    if (setsockopt(inSocket, SOL_SOCKET, SO_RCVBUF, (char*)&bufferSize, sizeof(bufferSize)) == SOCKET_ERROR) {
        LOG("setsockopt for SO_RCVBUF failed with error: " + std::to_string(WSAGetLastError()));
        closesocket(inSocket);
        return;
    }

    sockaddr_in serverHint;
    serverHint.sin_addr.S_un.S_addr = ADDR_ANY;
    serverHint.sin_family = AF_INET;
    serverHint.sin_port = htons(63212);
    if (bind(inSocket, (sockaddr*)&serverHint, sizeof(serverHint)) == SOCKET_ERROR) {
        LOG("UDP bind failed with error: " + std::to_string(WSAGetLastError()));
        closesocket(inSocket);
        return;
    }

    LOG("UDP Server loop started on port 63212.");

    std::vector<char> buf(65536);

    while (m_isRunning) {
        sockaddr_in clientHint;
        int clientLength = sizeof(clientHint);
        int bytesIn = recvfrom(inSocket, buf.data(), buf.size(), 0, (sockaddr*)&clientHint, &clientLength);

        if (bytesIn == SOCKET_ERROR) {
            int errorCode = WSAGetLastError();
            LOG("recvfrom failed with error: " + std::to_string(errorCode));
            continue;
        }

        try {
            buf[bytesIn] = '\0';
            json frameJson = json::parse(buf.data());
            std::vector<CorsairLedColor> colors;
            if (frameJson.contains("led_colors")) {
                for (const auto& key : frameJson["led_colors"]) {
                    colors.push_back({
                        static_cast<CorsairLedId>(key.value("id", 0)),
                        key.value("r", 0), key.value("g", 0), key.value("b", 0)
                        });
                }
            }
            if (m_controller) {
                m_controller->Render(colors);
            }
        }
        catch (const json::parse_error& e) {
            LOG("JSON parse error: " + std::string(e.what()));
        }
    }
    LOG("UDP Server loop stopped.");
    closesocket(inSocket);
}

void PlayerProcessor::SendHandshake() {
    SOCKET connectSocket = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (connectSocket == INVALID_SOCKET) {
        LOG("!!! Handshake socket creation FAILED with error: " + std::to_string(WSAGetLastError()));
        return;
    }

    sockaddr_in clientService;
    clientService.sin_family = AF_INET;
    clientService.sin_port = htons(63211);
    inet_pton(AF_INET, "127.0.0.1", &clientService.sin_addr);

    if (connect(connectSocket, (SOCKADDR*)&clientService, sizeof(clientService)) == SOCKET_ERROR) {
        LOG("!!! Handshake connect FAILED with error: " + std::to_string(WSAGetLastError()));
        closesocket(connectSocket);
        return;
    }

    json handshake;
    handshake["source"] = "iCUE_Proxy";
    handshake["all_led_ids"] = m_controller->GetAllLedIds();
    handshake["key_map"] = m_controller->GetNamedKeyMap();
    std::string handshakeStr = handshake.dump();

    LOG("Attempting to send handshake. Size: " + std::to_string(handshakeStr.length()) + " bytes.");

    int bytesSent = send(connectSocket, handshakeStr.c_str(), (int)handshakeStr.length(), 0);

    if (bytesSent == SOCKET_ERROR) {
        LOG("!!! Handshake send FAILED with error: " + std::to_string(WSAGetLastError()));
    }
    else {
        LOG(">>> Handshake successfully sent (" + std::to_string(bytesSent) + " bytes).");
    }

    shutdown(connectSocket, SD_SEND);
    closesocket(connectSocket);
}

PlayerProcessor::PlayerProcessor() : m_isRunning(true) {
    WSADATA wsaData;
    WSAStartup(MAKEWORD(2, 2), &wsaData);

    m_controller = std::make_unique<iCueLightController>();
    if (m_controller->Initialize()) {
        std::this_thread::sleep_for(std::chrono::seconds(1));
        SendHandshake();
        m_udpServerThread = std::thread(&PlayerProcessor::UDPServerLoop, this);
    }
}

PlayerProcessor::~PlayerProcessor() {
    m_isRunning = false;
    if (m_udpServerThread.joinable()) {
        m_udpServerThread.detach();
    }
    WSACleanup();
}