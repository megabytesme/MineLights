using System.Diagnostics;
using System.Net;
using System.Net.Sockets;
using System.Text;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using RGB.NET.Core;
using RGB.NET.Devices.Asus;
using RGB.NET.Devices.Corsair;
using RGB.NET.Devices.Logitech;
using RGB.NET.Devices.Msi;
using RGB.NET.Devices.Novation;
using RGB.NET.Devices.PicoPi;
using RGB.NET.Devices.Razer;
using RGB.NET.Devices.SteelSeries;
using RGB.NET.Devices.Wooting;

public class LightingServer
{
    private volatile bool _isRunning = false;
    private readonly RGBSurface _surface;
    private readonly Dictionary<int, Led> _ledIdMap = new Dictionary<int, Led>();
    private readonly Dictionary<string, int> _keyMap = new Dictionary<string, int>();
    private Thread? _handshakeThread, _udpListenerThread, _commandListenerThread, _discoveryThread;
    private readonly Action _shutdownAction;
    private readonly object _deviceLock = new object();

    private ServerConfig _config;
    private readonly string _configPath;
    private bool _isModControlActive = true;

    private readonly List<IRGBDeviceProvider> _allProviders;

    public LightingServer(Action shutdownAction)
    {
        _shutdownAction = shutdownAction;
        _surface = new RGBSurface();
        _configPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "config.json");

        _allProviders = new List<IRGBDeviceProvider>
        {
            MsiDeviceProvider.Instance, CorsairDeviceProvider.Instance,
            LogitechDeviceProvider.Instance, AsusDeviceProvider.Instance,
            RazerDeviceProvider.Instance, WootingDeviceProvider.Instance,
            SteelSeriesDeviceProvider.Instance, NovationDeviceProvider.Instance,
            PicoPiDeviceProvider.Instance
        };

        _config = LoadConfig();
    }

    private ServerConfig LoadConfig()
    {
        try
        {
            if (File.Exists(_configPath))
            {
                Console.WriteLine($"[Config] Loading configuration from {_configPath}");
                string json = File.ReadAllText(_configPath);
                var config = JsonConvert.DeserializeObject<ServerConfig>(json) ?? new ServerConfig();

                if (config.EnabledIntegrations == null || !config.EnabledIntegrations.Any())
                {
                    Console.WriteLine("[Config] No integrations specified, enabling defaults.");
                    config.EnabledIntegrations = new List<string>
                {
                    "Corsair", "Logitech", "Asus", "Razer",
                    "Wooting", "SteelSeries", "Msi"
                };
                }
                return config;
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[Config] Error loading config, using defaults. Error: {ex.Message}");
        }

        Console.WriteLine("[Config] No config file found, creating a default one.");
        return new ServerConfig
        {
            EnabledIntegrations = new List<string>
        {
            "Corsair", "Logitech", "Asus", "Razer",
            "Wooting", "SteelSeries", "Msi"
        }
        };
    }

    private void SaveConfig()
    {
        try
        {
            string json = JsonConvert.SerializeObject(_config, Formatting.Indented);
            File.WriteAllText(_configPath, json);
            Console.WriteLine($"[Config] Configuration saved to {_configPath}");
        }
        catch (Exception ex) { Console.WriteLine($"[Config] Error saving config: {ex.Message}"); }
    }

    public void Start()
    {
        _isRunning = true;
        ReconfigureSurfaceFromConfig();

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
        lock (_deviceLock)
        {
            var devicesToDetach = _surface.Devices.ToList();
            if (devicesToDetach.Any())
                _surface.Detach(devicesToDetach);

            foreach (var provider in _allProviders)
                if (provider.IsInitialized)
                    provider.Dispose();
        }
        _surface.Dispose();
    }

    private void ReconfigureSurfaceFromConfig()
    {
        lock (_deviceLock)
        {
            Console.WriteLine("[Server] Configuring surface based on settings...");

            var currentDevices = _surface.Devices.ToList();
            if (currentDevices.Any())
                _surface.Detach(currentDevices);

            foreach (var provider in _allProviders)
            {
                string providerName = provider.GetType().Name.Replace("DeviceProvider", "");
                bool shouldBeEnabled = _config.EnabledIntegrations.Contains(providerName);

                if (shouldBeEnabled && !provider.IsInitialized)
                {
                    if (provider is MsiDeviceProvider && !IsRunningAsAdmin())
                    {
                        Console.WriteLine($"[Server] -> Skipping {providerName}: Not running as admin.");
                        continue;
                    }
                    try
                    {
                        Console.WriteLine($"[Server] Initializing {providerName}...");
                        provider.Initialize(throwExceptions: true);
                    }
                    catch (Exception ex)
                    {
                        Console.WriteLine($"[Server] -> WARNING: Initializing {providerName} failed. Error: {ex.Message}");
                    }
                }
                else if (!shouldBeEnabled && provider.IsInitialized)
                {
                    Console.WriteLine($"[Server] Disposing disabled provider: {providerName}");
                    provider.Dispose();
                }
            }

            var devicesToAttach = _allProviders
                .Where(p => p.IsInitialized)
                .SelectMany(p => p.Devices)
                .Where(d => !_config.DisabledDevices.Contains($"{d.DeviceInfo.Manufacturer}|{d.DeviceInfo.Model}"))
                .ToList();

            if (devicesToAttach.Any())
            {
                _surface.Attach(devicesToAttach);
            }

            Console.WriteLine($"[RGB.NET] Final device count: {_surface.Devices.Count()}");
            BuildIdMaps();
        }
    }

    private void BuildIdMaps()
    {
        _ledIdMap.Clear();
        _keyMap.Clear();
        int currentLedId = 0;
        foreach (var device in _surface.Devices.OrderBy(d => d.DeviceInfo.DeviceType))
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
        Console.WriteLine($"[Server] Mapped {_ledIdMap.Count} LEDs and {_keyMap.Count} named keys.");
    }

    private void HandshakeServerLoop()
    {
        var listener = new TcpListener(IPAddress.Any, 63211);
        try
        {
            listener.Start();
            while (_isRunning)
            {
                using var client = listener.AcceptTcpClient();
                using var stream = client.GetStream();

                var reader = new BinaryReader(stream, Encoding.UTF8, leaveOpen: true);
                int clientConfigLength = IPAddress.NetworkToHostOrder(reader.ReadInt32());
                byte[] clientConfigBytes = reader.ReadBytes(clientConfigLength);
                string clientConfigJson = Encoding.UTF8.GetString(clientConfigBytes);
                JObject clientConfig = JObject.Parse(clientConfigJson);

                _config.EnabledIntegrations = clientConfig["enabled_integrations"]?.ToObject<List<string>>() ?? _config.EnabledIntegrations;
                _config.DisabledDevices = clientConfig["disabled_devices"]?.ToObject<List<string>>() ?? _config.DisabledDevices;
                SaveConfig();
                ReconfigureSurfaceFromConfig();

                lock (_deviceLock)
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
                    foreach (var pair in _keyMap) { keyMapJson.Add(pair.Key, pair.Value); }
                    var response = new JObject { ["devices"] = devicesArray, ["key_map"] = keyMapJson };
                    byte[] dataBytes = Encoding.UTF8.GetBytes(response.ToString(Formatting.None));
                    int networkOrderLength = IPAddress.HostToNetworkOrder(dataBytes.Length);
                    byte[] lengthBytes = BitConverter.GetBytes(networkOrderLength);

                    stream.Write(lengthBytes, 0, 4);
                    stream.Write(dataBytes, 0, dataBytes.Length);
                    stream.Flush();
                }
            }
        }
        catch (SocketException) { }
        finally { listener.Stop(); }
    }

    private void UdpServerLoop()
    {
        using var udpClient = new UdpClient(63212);
        var from = new IPEndPoint(0, 0);
        while (_isRunning)
        {
            try
            {
                byte[] recvBuffer = udpClient.Receive(ref from);
                string jsonString = Encoding.UTF8.GetString(recvBuffer);
                JObject? frameData = JObject.Parse(jsonString);

                bool isModEnabled = frameData?["inGame"]?.Value<bool>() ?? false;
                if (!isModEnabled)
                {
                    if (_isModControlActive) _isModControlActive = false;
                    continue;
                }

                _isModControlActive = true;

                if (frameData?["led_colors"] is JArray colors)
                {
                    lock (_deviceLock)
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
                        if (_isModControlActive) _surface.Update();
                    }
                }
            }
            catch { }
        }
    }

    private void CommandServerLoop()
    {
        var listener = new TcpListener(IPAddress.Any, 63213);
        try
        {
            listener.Start();
            while (_isRunning)
            {
                using var client = listener.AcceptTcpClient();
                using var stream = client.GetStream();
                var buffer = new byte[1024];
                int bytesRead = stream.Read(buffer, 0, buffer.Length);
                string command = Encoding.UTF8.GetString(buffer, 0, bytesRead);
                if (command == "restart_admin") TriggerRestart(true);
                else if (command == "restart") TriggerRestart(false);
                else if (command == "shutdown") _shutdownAction?.Invoke();
            }
        }
        catch (SocketException) { }
        finally { listener.Stop(); }
    }

    private void DiscoveryBroadcastLoop()
    {
        using var client = new UdpClient { EnableBroadcast = true };
        var endpoint = new IPEndPoint(IPAddress.Broadcast, 63214);
        byte[] message = Encoding.UTF8.GetBytes("MINELIGHTS_PROXY_HELLO");
        while (_isRunning)
        {
            try
            {
                client.Send(message, message.Length, endpoint);
                Thread.Sleep(5000);
            }
            catch (SocketException) { break; }
        }
    }

    private void TriggerRestart(bool asAdmin)
    {
        try
        {
            Process.Start(new ProcessStartInfo(Application.ExecutablePath) { UseShellExecute = true, Verb = asAdmin ? "runas" : "open" });
            _shutdownAction?.Invoke();
        }
        catch (Exception ex) { Console.WriteLine($"[Restart ERROR] {ex.Message}"); }
    }

    private bool IsRunningAsAdmin()
    {
        try
        {
            using var identity = System.Security.Principal.WindowsIdentity.GetCurrent();
            var principal = new System.Security.Principal.WindowsPrincipal(identity);
            return principal.IsInRole(System.Security.Principal.WindowsBuiltInRole.Administrator);
        }
        catch { return false; }
    }
}