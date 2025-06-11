public class ServerConfig
{
    public List<string> EnabledIntegrations { get; set; }
    public List<string> DisabledDevices { get; set; } = new();
}