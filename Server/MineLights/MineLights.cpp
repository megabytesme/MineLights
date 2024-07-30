#include <Windows.h>
#include "Resource.h"
#include "iCueLightController.h"

// Define constants
#define IDM_EXIT 1001
#define TRAY_ICON_MESSAGE (WM_USER + 1)

// Declare global variables
NOTIFYICONDATA nid;

// Initialize the system tray icon
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

// Handle tray icon messages
LRESULT CALLBACK WndProc(HWND hWnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    switch (msg) {
    case TRAY_ICON_MESSAGE: // Tray icon clicked
        if (lParam == WM_RBUTTONDOWN) {
            // Show context menu
            HMENU hmenu = CreatePopupMenu();
            InsertMenu(hmenu, 0, MF_BYPOSITION | MF_STRING, IDM_EXIT, L"Exit");
            POINT pt;
            GetCursorPos(&pt);
            SetForegroundWindow(hWnd); // Necessary to ensure the menu is shown
            TrackPopupMenu(hmenu, TPM_LEFTALIGN | TPM_LEFTBUTTON | TPM_BOTTOMALIGN, pt.x, pt.y, 0, hWnd, NULL);
            DestroyMenu(hmenu);
        }
        break;

    case WM_COMMAND:
        if (LOWORD(wParam) == IDM_EXIT) {
            // Clean up and exit the application
            Shell_NotifyIcon(NIM_DELETE, &nid);
            PostQuitMessage(0);
        }
        break;
    }
    return DefWindowProc(hWnd, msg, wParam, lParam);
}

// Main function
int WINAPI WinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance, LPSTR lpCmdLine, int nCmdShow) {
    // Create a hidden window
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

    iCueLightController();

    // Message loop
    MSG msg;
    while (GetMessage(&msg, NULL, 0, 0)) {
        TranslateMessage(&msg);
        DispatchMessage(&msg);
    }

    return (int)msg.wParam;
}