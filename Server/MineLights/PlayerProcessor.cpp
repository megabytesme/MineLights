#include "framework.h"
#include "PlayerProcessor.h"
#include "Logger.h"
#include "iCueLightController.h"
#include "MysticLightController.h"
#include "json.hpp"
#include <iostream>
#include <vector>
#include <string>
#pragma comment (lib, "ws2_32.lib")
#pragma warning(disable: 4996)

using json = nlohmann::json;

void PlayerProcessor::UDPServerLoop() {
    SOCKET inSocket = socket(AF_INET, SOCK_DGRAM, 0);
    if (inSocket == INVALID_SOCKET) {
        LOG("[UDP Server] socket creation failed.");
        return;
    }
    sockaddr_in serverHint;
    serverHint.sin_addr.S_un.S_addr = INADDR_ANY;
    serverHint.sin_family = AF_INET;
    serverHint.sin_port = htons(63212);
    if (bind(inSocket, (sockaddr*)&serverHint, sizeof(serverHint)) == SOCKET_ERROR) {
        LOG("[UDP Server] bind failed with error: " + std::to_string(WSAGetLastError()));
        closesocket(inSocket);
        return;
    }
    LOG("[UDP Server] Now listening for lighting data on port 63212.");
    std::vector<char> buf(65536);
    while (m_isRunning) {
        sockaddr_in clientHint;
        int clientLength = sizeof(clientHint);
        int bytesIn = recvfrom(inSocket, buf.data(), buf.size(), 0, (sockaddr*)&clientHint, &clientLength);
        if (bytesIn == SOCKET_ERROR) {
            if (m_isRunning) LOG("[UDP Server] recvfrom failed with error: " + std::to_string(WSAGetLastError()));
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
            if (!colors.empty()) {
                for (const auto& controller : m_controllers) {
                    controller->UpdateData(colors);
                }
            }
        }
        catch (const json::parse_error& e) {
            LOG("[UDP Server] JSON parse error: " + std::string(e.what()));
        }
    }
    closesocket(inSocket);
}

void PlayerProcessor::HandshakeServerLoop() {
    SOCKET listenSocket = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (listenSocket == INVALID_SOCKET) {
        LOG("!!! Handshake listen socket creation FAILED: " + std::to_string(WSAGetLastError()));
        return;
    }
    sockaddr_in serverAddr;
    serverAddr.sin_family = AF_INET;
    serverAddr.sin_addr.s_addr = INADDR_ANY;
    serverAddr.sin_port = htons(63211);
    if (bind(listenSocket, (SOCKADDR*)&serverAddr, sizeof(serverAddr)) == SOCKET_ERROR) {
        LOG("!!! Handshake bind FAILED: " + std::to_string(WSAGetLastError()));
        closesocket(listenSocket);
        return;
    }
    if (listen(listenSocket, SOMAXCONN) == SOCKET_ERROR) {
        LOG("!!! Handshake listen FAILED: " + std::to_string(WSAGetLastError()));
        closesocket(listenSocket);
        return;
    }
    LOG("[Handshake Server] Now listening for client connections on port 63211...");
    while (m_isRunning) {
        SOCKET clientSocket = accept(listenSocket, NULL, NULL);
        if (clientSocket == INVALID_SOCKET) {
            if (m_isRunning) LOG("!!! Handshake accept FAILED: " + std::to_string(WSAGetLastError()));
            continue;
        }
        LOG("[Handshake Server] Client connected. Gathering device info...");
        json handshake;
        json devicesArray = json::array();
        json keyMap = json::object();
        for (auto& controller : m_controllers) {
            auto connectedDevices = controller->GetConnectedDevices();
            for (const auto& device : connectedDevices) {
                json deviceObj;
                deviceObj["sdk"] = device.sdk;
                deviceObj["name"] = device.name;
                deviceObj["ledCount"] = device.ledCount;
                deviceObj["leds"] = device.leds;
                devicesArray.push_back(deviceObj);
            }
            auto controllerKeyMap = controller->GetNamedKeyMap();
            for (const auto& pair : controllerKeyMap) {
                keyMap[pair.first] = pair.second;
            }
        }
        handshake["devices"] = devicesArray;
        handshake["key_map"] = keyMap;
        std::string handshakeStr = handshake.dump();
        int bytesSent = send(clientSocket, handshakeStr.c_str(), static_cast<int>(handshakeStr.length()), 0);
        if (bytesSent == SOCKET_ERROR) {
            LOG("!!! Handshake send FAILED: " + std::to_string(WSAGetLastError()));
        }
        else {
            LOG("[Handshake Server] Handshake successfully sent to client (" + std::to_string(bytesSent) + " bytes).");
        }
        shutdown(clientSocket, SD_SEND);
        closesocket(clientSocket);
    }
    closesocket(listenSocket);
}

bool PlayerProcessor::IsRunningAsAdmin() const {
    BOOL fIsAdmin = FALSE;
    HANDLE hToken = NULL;
    if (OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &hToken)) {
        TOKEN_ELEVATION Elevation;
        DWORD cbSize = sizeof(TOKEN_ELEVATION);
        if (GetTokenInformation(hToken, TokenElevation, &Elevation, sizeof(Elevation), &cbSize)) {
            fIsAdmin = Elevation.TokenIsElevated;
        }
    }
    if (hToken) {
        CloseHandle(hToken);
    }
    return fIsAdmin;
}

PlayerProcessor::PlayerProcessor() : m_isRunning(true) {
    WSADATA wsaData;
    WSAStartup(MAKEWORD(2, 2), &wsaData);

    auto icue_controller = std::make_unique<iCueLightController>();
    if (icue_controller->Initialize()) {
        LOG("iCUE Controller Initialized.");
        m_controllers.push_back(std::move(icue_controller));
    }
    else {
        LOG("iCUE Controller failed to initialize (Is iCUE running with SDK enabled?).");
    }

    if (IsRunningAsAdmin()) {
        LOG("Running with Administrator privileges. Initializing Mystic Light SDK...");
        auto mystic_controller = std::make_unique<MysticLightController>();
        if (mystic_controller->Initialize()) {
            LOG("Mystic Light Controller Initialized.");
            m_controllers.push_back(std::move(mystic_controller));
        }
        else {
            LOG("Mystic Light Controller failed to initialize (Is MSI Center/Mystic Light installed?).");
        }
    }
    else {
        LOG("Not running as Administrator. Mystic Light SDK will not be initialized.");
    }

    if (!m_controllers.empty()) {
        for (const auto& controller : m_controllers) {
            controller->Start();
        }
        m_handshakeServerThread = std::thread(&PlayerProcessor::HandshakeServerLoop, this);
        m_udpServerThread = std::thread(&PlayerProcessor::UDPServerLoop, this);
    }
    else {
        LOG("No lighting controllers initialized.");
    }
}

PlayerProcessor::~PlayerProcessor() {
    m_isRunning = false;
    for (const auto& controller : m_controllers) {
        controller->Stop();
    }
    SOCKET shutdownSocket = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (shutdownSocket != INVALID_SOCKET) {
        sockaddr_in serverAddr;
        serverAddr.sin_family = AF_INET;
        serverAddr.sin_addr.s_addr = inet_addr("127.0.0.1");
        serverAddr.sin_port = htons(63211);
        connect(shutdownSocket, (SOCKADDR*)&serverAddr, sizeof(serverAddr));
        closesocket(shutdownSocket);
    }
    if (m_handshakeServerThread.joinable()) {
        m_handshakeServerThread.join();
    }
    if (m_udpServerThread.joinable()) {
        m_udpServerThread.join();
    }
    WSACleanup();
}