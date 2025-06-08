#include <winsock2.h>
#include <Windows.h>
#include "Resource.h"
#include <thread>
#include <memory>
#include "PlayerProcessor.h" 

#define IDM_EXIT 1001
#define TRAY_ICON_MESSAGE (WM_USER + 1)

NOTIFYICONDATA nid;

void InitTrayIcon(HWND hWnd) {
    nid.cbSize = sizeof(NOTIFYICONDATA);
    nid.hWnd = hWnd;
    nid.uID = 1;
    nid.uFlags = NIF_ICON | NIF_MESSAGE | NIF_TIP;
    nid.uCallbackMessage = TRAY_ICON_MESSAGE;
    nid.hIcon = LoadIcon(GetModuleHandle(nullptr), MAKEINTRESOURCE(IDI_ICON1));
    lstrcpy(nid.szTip, L"MineLights Helper");
    Shell_NotifyIcon(NIM_ADD, &nid);
}

LRESULT CALLBACK WndProc(HWND hWnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    switch (msg) {
    case TRAY_ICON_MESSAGE:
        if (lParam == WM_RBUTTONDOWN) {
            HMENU hmenu = CreatePopupMenu();
            InsertMenu(hmenu, 0, MF_BYPOSITION | MF_STRING, IDM_EXIT, L"Exit");
            POINT pt;
            GetCursorPos(&pt);
            SetForegroundWindow(hWnd);
            TrackPopupMenu(hmenu, TPM_LEFTALIGN | TPM_LEFTBUTTON | TPM_BOTTOMALIGN, pt.x, pt.y, 0, hWnd, NULL);
            DestroyMenu(hmenu);
        }
        break;

    case WM_COMMAND:
        if (LOWORD(wParam) == IDM_EXIT) {
            Shell_NotifyIcon(NIM_DELETE, &nid);
            PostQuitMessage(0);
        }
        break;
    }
    return DefWindowProc(hWnd, msg, wParam, lParam);
}

int WINAPI WinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance, LPSTR lpCmdLine, int nCmdShow) {
    std::unique_ptr<PlayerProcessor> playerProcessor;

    WNDCLASS wc = { 0 };
    wc.lpfnWndProc = WndProc;
    wc.hInstance = hInstance;
    wc.lpszClassName = L"MineLightsClass";
    RegisterClass(&wc);

    HWND hWnd = CreateWindow(L"MineLightsClass", L"MineLights Helper", 0, 0, 0, 0, 0, HWND_MESSAGE, NULL, hInstance, NULL);
    if (!hWnd) {
        return 0;
    }

    InitTrayIcon(hWnd);

    playerProcessor = std::make_unique<PlayerProcessor>();

    MSG msg;
    while (GetMessage(&msg, NULL, 0, 0)) {
        TranslateMessage(&msg);
        DispatchMessage(&msg);
    }

    return (int)msg.wParam;
}