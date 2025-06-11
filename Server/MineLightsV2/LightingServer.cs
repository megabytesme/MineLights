using System.Diagnostics;
using System.Net;
using System.Net.Sockets;
using System.Text;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using RGB.NET.Core;
using RGB.NET.Devices.Corsair;
using RGB.NET.Devices.Msi;

public static class KeyMapper
{
    private static readonly Dictionary<LedId, string> _map = new Dictionary<LedId, string>
    {
        { LedId.Keyboard_A, "A" }, { LedId.Keyboard_B, "B" }, { LedId.Keyboard_C, "C" },
        { LedId.Keyboard_D, "D" }, { LedId.Keyboard_E, "E" }, { LedId.Keyboard_F, "F" },
        { LedId.Keyboard_G, "G" }, { LedId.Keyboard_H, "H" }, { LedId.Keyboard_I, "I" },
        { LedId.Keyboard_J, "J" }, { LedId.Keyboard_K, "K" }, { LedId.Keyboard_L, "L" },
        { LedId.Keyboard_M, "M" }, { LedId.Keyboard_N, "N" }, { LedId.Keyboard_O, "O" },
        { LedId.Keyboard_P, "P" }, { LedId.Keyboard_Q, "Q" }, { LedId.Keyboard_R, "R" },
        { LedId.Keyboard_S, "S" }, { LedId.Keyboard_T, "T" }, { LedId.Keyboard_U, "U" },
        { LedId.Keyboard_V, "V" }, { LedId.Keyboard_W, "W" }, { LedId.Keyboard_X, "X" },
        { LedId.Keyboard_Y, "Y" }, { LedId.Keyboard_Z, "Z" },
        { LedId.Keyboard_1, "1" }, { LedId.Keyboard_2, "2" }, { LedId.Keyboard_3, "3" },
        { LedId.Keyboard_4, "4" }, { LedId.Keyboard_5, "5" }, { LedId.Keyboard_6, "6" },
        { LedId.Keyboard_7, "7" }, { LedId.Keyboard_8, "8" }, { LedId.Keyboard_9, "9" },
        { LedId.Keyboard_0, "0" },
        { LedId.Keyboard_F1, "F1" }, { LedId.Keyboard_F2, "F2" }, { LedId.Keyboard_F3, "F3" },
        { LedId.Keyboard_F4, "F4" }, { LedId.Keyboard_F5, "F5" }, { LedId.Keyboard_F6, "F6" },
        { LedId.Keyboard_F7, "F7" }, { LedId.Keyboard_F8, "F8" }, { LedId.Keyboard_F9, "F9" },
        { LedId.Keyboard_F10, "F10" }, { LedId.Keyboard_F11, "F11" }, { LedId.Keyboard_F12, "F12" },
        { LedId.Keyboard_LeftShift, "LSHIFT" }, { LedId.Keyboard_RightShift, "RSHIFT" },
        { LedId.Keyboard_LeftCtrl, "LCTRL" }, { LedId.Keyboard_RightCtrl, "RCTRL" },
        { LedId.Keyboard_LeftAlt, "LALT" }, { LedId.Keyboard_RightAlt, "RALT" },
        { LedId.Keyboard_LeftGui, "LWIN" }, { LedId.Keyboard_RightGui, "RWIN" },
        { LedId.Keyboard_Space, "SPACE" }, { LedId.Keyboard_Enter, "ENTER" },
        { LedId.Keyboard_Escape, "ESCAPE" }, { LedId.Keyboard_Backspace, "BACKSPACE" },
        { LedId.Keyboard_Tab, "TAB" }, { LedId.Keyboard_CapsLock, "CAPSLOCK" },
        { LedId.Keyboard_NumLock, "NUMLOCK" },
        { LedId.Keyboard_Num1, "NUMPAD1" }, { LedId.Keyboard_Num2, "NUMPAD2" }, { LedId.Keyboard_Num3, "NUMPAD3" },
        { LedId.Keyboard_Num4, "NUMPAD4" }, { LedId.Keyboard_Num5, "NUMPAD5" }, { LedId.Keyboard_Num6, "NUMPAD6" },
        { LedId.Keyboard_Num7, "NUMPAD7" }, { LedId.Keyboard_Num8, "NUMPAD8" }, { LedId.Keyboard_Num9, "NUMPAD9" },
        { LedId.Keyboard_Num0, "NUMPAD0" }, { LedId.Keyboard_NumSlash, "NUMPAD_DIVIDE" },
        { LedId.Keyboard_NumAsterisk, "NUMPAD_MULTIPLY" }, { LedId.Keyboard_NumMinus, "NUMPAD_SUBTRACT" },
        { LedId.Keyboard_NumPlus, "NUMPAD_ADD" }, { LedId.Keyboard_NumEnter, "NUMPAD_ENTER" },
        { LedId.Keyboard_NumPeriodAndDelete, "NUMPAD_DECIMAL" }, { LedId.Keyboard_ArrowUp, "UP" },
        { LedId.Keyboard_ArrowDown, "DOWN" }, { LedId.Keyboard_ArrowLeft, "LEFT" },
        { LedId.Keyboard_ArrowRight, "RIGHT" }, { LedId.Keyboard_Insert, "INSERT" },
        { LedId.Keyboard_Delete, "DELETE" }, { LedId.Keyboard_Home, "HOME" },
        { LedId.Keyboard_End, "END" }, { LedId.Keyboard_PageUp, "PAGE_UP" },
        { LedId.Keyboard_PageDown, "PAGE_DOWN" }, { LedId.Keyboard_PrintScreen, "PRINT_SCREEN" },
        { LedId.Keyboard_ScrollLock, "SCROLL_LOCK" }, { LedId.Keyboard_PauseBreak, "PAUSE" }
    };
    public static string? GetFriendlyName(LedId ledId) => _map.TryGetValue(ledId, out var name) ? name : null;
}


public class LightingServer
{
    private volatile bool _isRunning = false;
    private readonly RGBSurface _surface;
    private readonly Dictionary<int, Led> _ledIdMap = new Dictionary<int, Led>();
    private readonly Dictionary<string, int> _keyMap = new Dictionary<string, int>();
    private Thread? _handshakeThread, _udpListenerThread, _commandListenerThread, _discoveryThread;
    private readonly Action _shutdownAction;

    public LightingServer(Action shutdownAction)
    {
        _shutdownAction = shutdownAction;
        _surface = new RGBSurface();
    }

    public void Start()
    {
        _isRunning = true;

        try
        {
            if (IsRunningAsAdmin())
            {
                Console.WriteLine("[Server] Initializing MSI Device Provider...");
                MsiDeviceProvider.Instance.Initialize();
                Console.WriteLine("[Server] MSI Provider Initialized.");
            }
            else
            {
                Console.WriteLine("[Server] Not running as admin, skipping MSI provider.");
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[Server] FATAL ERROR during MSI initialization: {ex.Message}");
        }

        try
        {
            Console.WriteLine("[Server] Initializing Corsair Device Provider...");
            CorsairDeviceProvider.Instance.Initialize(throwExceptions: true);
            Console.WriteLine("[Server] Corsair Provider Initialized.");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[Server] FATAL ERROR during Corsair initialization: {ex.Message}");
        }

        Console.WriteLine("[Server] Loading all initialized providers into the surface...");
        _surface.Load(MsiDeviceProvider.Instance);
        _surface.Load(CorsairDeviceProvider.Instance);

        Console.WriteLine($"[RGB.NET] Final device count after loading: {_surface.Devices.Count()}");

        BuildIdMaps();

        _handshakeThread = new Thread(HandshakeServerLoop) { IsBackground = true };
        _udpListenerThread = new Thread(UdpServerLoop) { IsBackground = true };
        _commandListenerThread = new Thread(CommandServerLoop) { IsBackground = true };
        _discoveryThread = new Thread(DiscoveryBroadcastLoop) { IsBackground = true };
        _handshakeThread.Start();
        _udpListenerThread.Start();
        _commandListenerThread.Start();
        _discoveryThread.Start();
    }

    public void Stop()
    {
        _isRunning = false;
        _surface.Dispose();
    }

    private void BuildIdMaps()
    {
        int currentLedId = 0;
        foreach (var device in _surface.Devices)
        {
            foreach (var led in device)
            {
                _ledIdMap.Add(currentLedId, led);
                string? friendlyName = KeyMapper.GetFriendlyName(led.Id);
                if (friendlyName != null && !_keyMap.ContainsKey(friendlyName))
                {
                    _keyMap.Add(friendlyName, currentLedId);
                }
                currentLedId++;
            }
        }
        Console.WriteLine($"[MineLights] Mapped {_ledIdMap.Count} LEDs and {_keyMap.Count} named keys.");
    }

    private void HandshakeServerLoop()
    {
        var listener = new TcpListener(IPAddress.Any, 63211);
        listener.Start();
        Console.WriteLine("[Server] Handshake server listening on port 63211.");
        while (_isRunning)
        {
            try
            {
                using (var client = listener.AcceptTcpClient())
                using (var stream = client.GetStream())
                {
                    var devicesArray = new JArray();
                    foreach (var device in _surface.Devices)
                    {
                        var deviceLeds = device.Select(led => _ledIdMap.FirstOrDefault(x => x.Value == led).Key).Where(k => k != 0 || _ledIdMap.Count == 1).ToArray();
                        if (!deviceLeds.Any()) continue;
                        var deviceObj = new JObject
                        {
                            ["sdk"] = device.DeviceInfo.Manufacturer,
                            ["name"] = device.DeviceInfo.Model,
                            ["ledCount"] = deviceLeds.Length,
                            ["leds"] = new JArray(deviceLeds)
                        };
                        devicesArray.Add(deviceObj);
                    }
                    var keyMapJson = new JObject();
                    foreach (var pair in _keyMap)
                    {
                        keyMapJson.Add(pair.Key, pair.Value);
                    }
                    var response = new JObject
                    {
                        ["devices"] = devicesArray,
                        ["key_map"] = keyMapJson
                    };
                    byte[] data = Encoding.UTF8.GetBytes(response.ToString(Formatting.None));
                    stream.Write(data, 0, data.Length);
                }
            }
            catch (Exception ex) { if (_isRunning) Console.WriteLine($"[Handshake ERROR] {ex.Message}"); }
        }
        listener.Stop();
    }

    private void UdpServerLoop()
    {
        using (var udpClient = new UdpClient(63212))
        {
            Console.WriteLine("[Server] UDP lighting listener on port 63212.");
            var from = new IPEndPoint(0, 0);
            while (_isRunning)
            {
                try
                {
                    byte[] recvBuffer = udpClient.Receive(ref from);
                    string jsonString = Encoding.UTF8.GetString(recvBuffer);
                    JObject? frameData = JObject.Parse(jsonString);
                    if (frameData?["led_colors"] is JArray colors)
                    {
                        foreach (var item in colors)
                        {
                            int id = item.Value<int>("id");
                            int r = item.Value<int>("r");
                            int g = item.Value<int>("g");
                            int b = item.Value<int>("b");
                            if (_ledIdMap.TryGetValue(id, out Led led))
                            {
                                led.Color = new RGB.NET.Core.Color((byte)r, (byte)g, (byte)b);
                            }
                        }
                        _surface.Update();
                    }
                }
                catch { }
            }
        }
    }

    private void CommandServerLoop()
    {
        var listener = new TcpListener(IPAddress.Any, 63213);
        listener.Start();
        Console.WriteLine("[Server] Command server listening on port 63213.");
        while (_isRunning)
        {
            try
            {
                using (var client = listener.AcceptTcpClient())
                using (var stream = client.GetStream())
                {
                    var buffer = new byte[1024];
                    int bytesRead = stream.Read(buffer, 0, buffer.Length);
                    string command = Encoding.UTF8.GetString(buffer, 0, bytesRead);
                    if (command == "restart_admin") TriggerRestart(true);
                    else if (command == "restart") TriggerRestart(false);
                    else if (command == "shutdown") _shutdownAction?.Invoke();
                }
            }
            catch (Exception ex) { if (_isRunning) Console.WriteLine($"[Command ERROR] {ex.Message}"); }
        }
        listener.Stop();
    }

    private void DiscoveryBroadcastLoop()
    {
        using (var client = new UdpClient { EnableBroadcast = true })
        {
            var endpoint = new IPEndPoint(IPAddress.Broadcast, 63214);
            byte[] message = Encoding.UTF8.GetBytes("MINELIGHTS_PROXY_HELLO");
            Console.WriteLine("[Server] Discovery broadcast started on port 63214.");
            while (_isRunning)
            {
                try
                {
                    client.Send(message, message.Length, endpoint);
                    Thread.Sleep(5000);
                }
                catch { }
            }
        }
    }

    private void TriggerRestart(bool asAdmin)
    {
        try
        {
            var process = new Process
            {
                StartInfo =
                {
                    FileName = Application.ExecutablePath,
                    UseShellExecute = true,
                    Verb = asAdmin ? "runas" : "open"
                }
            };
            process.Start();
            _shutdownAction?.Invoke();
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[Restart ERROR] {ex.Message}");
        }
    }

    private bool IsRunningAsAdmin()
    {
        try
        {
            using (var identity = System.Security.Principal.WindowsIdentity.GetCurrent())
            {
                var principal = new System.Security.Principal.WindowsPrincipal(identity);
                return principal.IsInRole(System.Security.Principal.WindowsBuiltInRole.Administrator);
            }
        }
        catch { return false; }
    }
}