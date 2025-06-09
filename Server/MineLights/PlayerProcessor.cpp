#include "framework.h"
#include "PlayerProcessor.h"
#include "Logger.h"
#include "iCueLightController.h"
#include "MysticLightController.h"
#include "json.hpp"
#include <iostream>
#include <vector>
#include <string>
#include <algorithm>
#include <shellapi.h>
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
        catch (const json::parse_error& e) {}
    }
    closesocket(inSocket);
}

void PlayerProcessor::HandshakeServerLoop() {
    SOCKET listenSocket = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (listenSocket == INVALID_SOCKET) return;
    sockaddr_in serverAddr;
    serverAddr.sin_family = AF_INET;
    serverAddr.sin_addr.s_addr = INADDR_ANY;
    serverAddr.sin_port = htons(63211);
    if (bind(listenSocket, (SOCKADDR*)&serverAddr, sizeof(serverAddr)) == SOCKET_ERROR) {
        closesocket(listenSocket);
        return;
    }
    if (listen(listenSocket, SOMAXCONN) == SOCKET_ERROR) {
        closesocket(listenSocket);
        return;
    }
    LOG("[Handshake Server] Now listening for client connections on port 63211...");
    while (m_isRunning) {
        SOCKET clientSocket = accept(listenSocket, NULL, NULL);
        if (clientSocket == INVALID_SOCKET) continue;
        LOG("[Handshake Server] Client connected. Receiving configuration...");
        char buffer[4096] = { 0 };
        recv(clientSocket, buffer, sizeof(buffer), 0);

        std::vector<std::string> enabledIntegrations;
        try {
            json configJson = json::parse(buffer);
            if (configJson.contains("enabled_integrations")) {
                for (const auto& integration : configJson["enabled_integrations"]) {
                    enabledIntegrations.push_back(integration.get<std::string>());
                }
            }
        }
        catch (const std::exception& e) {}
        LOG("[Handshake Server] Gathering device info based on client config...");
        json handshake;
        json devicesArray = json::array();
        json keyMap = json::object();
        for (auto& controller : m_controllers) {
            bool isEnabled = std::find(enabledIntegrations.begin(), enabledIntegrations.end(), controller->GetSdkName()) != enabledIntegrations.end();
            if (!isEnabled) continue;
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
        send(clientSocket, handshakeStr.c_str(), static_cast<int>(handshakeStr.length()), 0);
        closesocket(clientSocket);
    }
    closesocket(listenSocket);
}

void PlayerProcessor::CommandServerLoop() {
    SOCKET listenSocket = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (listenSocket == INVALID_SOCKET) return;
    sockaddr_in serverAddr;
    serverAddr.sin_family = AF_INET;
    serverAddr.sin_addr.s_addr = INADDR_ANY;
    serverAddr.sin_port = htons(63213);
    if (bind(listenSocket, (SOCKADDR*)&serverAddr, sizeof(serverAddr)) == SOCKET_ERROR) {
        closesocket(listenSocket);
        return;
    }
    if (listen(listenSocket, SOMAXCONN) == SOCKET_ERROR) {
        closesocket(listenSocket);
        return;
    }
    LOG("[Command Server] Now listening for commands on port 63213...");
    while (m_isRunning) {
        SOCKET clientSocket = accept(listenSocket, NULL, NULL);
        if (clientSocket == INVALID_SOCKET) continue;
        char buffer[1024] = { 0 };
        recv(clientSocket, buffer, sizeof(buffer), 0);
        std::string command(buffer);
        if (command == "restart_admin") {
            LOG("[Command Server] Received restart_admin command.");
            TriggerRestart(true);
        }
        else if (command == "restart") {
            LOG("[Command Server] Received restart command.");
            TriggerRestart(false);
        }
        else if (command == "shutdown") {
            LOG("[Command Server] Received shutdown command.");
            PostMessage(m_hwnd, WM_CLOSE, 0, 0);
        }
        closesocket(clientSocket);
    }
    closesocket(listenSocket);
}

void PlayerProcessor::DiscoveryBroadcastLoop() {
    SOCKET broadcastSocket = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    if (broadcastSocket == INVALID_SOCKET) return;

    char broadcastPermission = '1';
    setsockopt(broadcastSocket, SOL_SOCKET, SO_BROADCAST, &broadcastPermission, sizeof(broadcastPermission));

    sockaddr_in broadcastAddr;
    broadcastAddr.sin_family = AF_INET;
    broadcastAddr.sin_port = htons(63214);
    broadcastAddr.sin_addr.s_addr = INADDR_BROADCAST;

    const char* message = "MINELIGHTS_PROXY_HELLO";
    LOG("[Discovery] Starting broadcast loop on port 63214.");

    while (m_isRunning) {
        sendto(broadcastSocket, message, strlen(message), 0, (sockaddr*)&broadcastAddr, sizeof(broadcastAddr));
        Sleep(5000);
    }
    closesocket(broadcastSocket);
}

void PlayerProcessor::TriggerRestart(bool asAdmin) {
    char szPath[MAX_PATH];
    GetModuleFileNameA(NULL, szPath, ARRAYSIZE(szPath));
    SHELLEXECUTEINFOA sei = { sizeof(sei) };
    sei.lpVerb = asAdmin ? "runas" : "open";
    sei.lpFile = szPath;
    sei.hwnd = NULL;
    sei.nShow = SW_SHOWNORMAL;
    if (ShellExecuteExA(&sei)) {
        PostMessage(m_hwnd, WM_CLOSE, 0, 0);
    }
    else {
        LOG("!!! Failed to trigger restart. Error code: " + std::to_string(GetLastError()));
    }
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

PlayerProcessor::PlayerProcessor(HWND hwnd) : m_isRunning(true), m_hwnd(hwnd) {
    WSADATA wsaData;
    WSAStartup(MAKEWORD(2, 2), &wsaData);

    auto icue_controller = std::make_unique<iCueLightController>();
    if (icue_controller->Initialize()) {
        LOG("iCUE Controller Initialized.");
        m_controllers.push_back(std::move(icue_controller));
    }
    else {
        LOG("iCUE Controller failed to initialize.");
    }

    if (IsRunningAsAdmin()) {
        LOG("Running with Administrator privileges. Initializing Mystic Light SDK...");
        auto mystic_controller = std::make_unique<MysticLightController>();
        if (mystic_controller->Initialize()) {
            LOG("Mystic Light Controller Initialized.");
            m_controllers.push_back(std::move(mystic_controller));
        }
        else {
            LOG("Mystic Light Controller failed to initialize.");
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
        m_commandServerThread = std::thread(&PlayerProcessor::CommandServerLoop, this);
        m_discoveryThread = std::thread(&PlayerProcessor::DiscoveryBroadcastLoop, this);
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
    if (m_handshakeServerThread.joinable()) m_handshakeServerThread.join();
    if (m_udpServerThread.joinable()) m_udpServerThread.join();
    if (m_commandServerThread.joinable()) m_commandServerThread.join();
    if (m_discoveryThread.joinable()) m_discoveryThread.join();
    WSACleanup();
}