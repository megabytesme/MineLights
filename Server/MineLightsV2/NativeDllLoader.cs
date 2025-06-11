using System.IO;
using System.Reflection;
using RGB.NET.Devices.Corsair;
using RGB.NET.Devices.Msi;

public static class NativeDllLoader
{
    public static void LoadNativeSDKs()
    {
        string tempPath = Path.Combine(Path.GetTempPath(), "MineLightsV2", "NativeLibs");
        Directory.CreateDirectory(tempPath);

        ExtractResource("MineLightsV2.resources.x64.CUESDK.x64_2019.dll", Path.Combine(tempPath, "iCUESDK.x64_2019.dll"));
        ExtractResource("MineLightsV2.resources.x64.MysticLight_SDK_x64.dll", Path.Combine(tempPath, "MysticLight_SDK_x64.dll"));

        CorsairDeviceProvider.PossibleX64NativePaths.Add(tempPath);
        MsiDeviceProvider.PossibleX64NativePaths.Add(tempPath);
    }

    private static void ExtractResource(string resourceName, string outputPath)
    {
        if (File.Exists(outputPath)) return;

        using (Stream stream = Assembly.GetExecutingAssembly().GetManifestResourceStream(resourceName))
        {
            if (stream == null) throw new Exception($"Cannot find resource: {resourceName}");
            using (FileStream fileStream = new FileStream(outputPath, FileMode.Create))
            {
                stream.CopyTo(fileStream);
            }
        }
    }
}