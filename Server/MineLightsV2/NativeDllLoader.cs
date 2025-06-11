using System.Reflection;
using RGB.NET.Devices.Corsair;
using RGB.NET.Devices.Msi;

public static class NativeDllLoader
{
    public static void LoadNativeSDKs()
    {
        try
        {
            Console.WriteLine("[Loader] All Embedded Resources:");
            var assembly = Assembly.GetExecutingAssembly();
            foreach (var name in assembly.GetManifestResourceNames())
            {
                Console.WriteLine($"[Loader] -> Found resource: {name}");
            }

            string tempPath = Path.Combine(Path.GetTempPath(), "MineLightsV2", "NativeLibs");
            Console.WriteLine($"[Loader] Using temporary path for native libs: {tempPath}");
            Directory.CreateDirectory(tempPath);

            Console.WriteLine("[Loader] Attempting to load Corsair SDK...");
            ExtractResource("MineLightsV2.resources.x64.iCUESDK.x64_2019.dll", Path.Combine(tempPath, "iCUESDK.x64_2019.dll"));
            CorsairDeviceProvider.PossibleX64NativePaths.Add(Path.Combine(tempPath, "iCUESDK.x64_2019.dll"));
            Console.WriteLine("[Loader] Corsair SDK path configured.");

            Console.WriteLine("[Loader] Attempting to load MSI SDK...");
            ExtractResource("MineLightsV2.resources.x64.MysticLight_SDK_x64.dll", Path.Combine(tempPath, "MysticLight_SDK_x64.dll"));
            MsiDeviceProvider.PossibleX64NativePaths.Add(Path.Combine(tempPath, "MysticLight_SDK_x64.dll"));
            Console.WriteLine("[Loader] MSI SDK path configured.");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[Loader] FATAL ERROR during native DLL loading: {ex.Message}");
            Console.WriteLine(ex.StackTrace);
        }
    }

    private static void ExtractResource(string resourceName, string outputPath)
    {
        if (File.Exists(outputPath))
        {
            Console.WriteLine($"[Loader] -> Native library '{Path.GetFileName(outputPath)}' already exists. Skipping extraction.");
            return;
        }

        Console.WriteLine($"[Loader] -> Attempting to extract '{resourceName}' to '{outputPath}'");
        Assembly assembly = Assembly.GetExecutingAssembly();
        using (Stream? stream = assembly.GetManifestResourceStream(resourceName))
        {
            if (stream == null) throw new FileNotFoundException($"Cannot find embedded resource: {resourceName}. Check the name and build action.");

            using (FileStream fileStream = new FileStream(outputPath, FileMode.Create))
            {
                stream.CopyTo(fileStream);
                Console.WriteLine($"[Loader] -> Successfully extracted '{Path.GetFileName(outputPath)}'.");
            }
        }
    }
}