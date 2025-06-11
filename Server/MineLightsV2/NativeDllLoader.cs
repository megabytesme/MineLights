using System;
using System.IO;
using System.Reflection;
using RGB.NET.Devices.Corsair;
using RGB.NET.Devices.Logitech;
using RGB.NET.Devices.Msi;
using RGB.NET.Devices.Wooting;

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

            LoadSdk("Corsair", "MineLightsV2.resources.x64.iCUESDK.x64_2019.dll", "iCUESDK.x64_2019.dll", tempPath, CorsairDeviceProvider.PossibleX64NativePaths);
            LoadSdk("MSI", "MineLightsV2.resources.x64.MysticLight_SDK_x64.dll", "MysticLight_SDK_x64.dll", tempPath, MsiDeviceProvider.PossibleX64NativePaths);
            LoadSdk("Logitech", "MineLightsV2.resources.x64.LogitechLedEnginesWrapper.dll", "LogitechLedEnginesWrapper.dll", tempPath, LogitechDeviceProvider.PossibleX64NativePaths);
            LoadSdk("Wooting", "MineLightsV2.resources.x64.wooting-rgb-sdk.dll", "wooting-rgb-sdk.dll", tempPath, WootingDeviceProvider.PossibleX64NativePathsWindows);
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[Loader] FATAL ERROR: {ex.Message}");
            MessageBox.Show($"A critical error occurred while loading native libraries:\n\n{ex.Message}\n\nThe application will now exit.", "MineLights Server Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
            Environment.Exit(1);
        }
    }

    private static void LoadSdk(string name, string resourceName, string outputFileName, string tempPath, ICollection<string> possiblePaths)
    {
        Console.WriteLine($"[Loader] Preparing {name} SDK...");
        string dllPath = Path.Combine(tempPath, outputFileName);
        ExtractResource(resourceName, dllPath);
        possiblePaths.Add(dllPath);
        Console.WriteLine($"[Loader] {name} SDK path configured: {dllPath}");
    }

    private static void ExtractResource(string resourceName, string outputPath)
    {
        if (File.Exists(outputPath)) return;

        Assembly assembly = Assembly.GetExecutingAssembly();
        using (Stream? stream = assembly.GetManifestResourceStream(resourceName))
        {
            if (stream == null) throw new FileNotFoundException($"Cannot find embedded resource: {resourceName}.");

            using (FileStream fileStream = new FileStream(outputPath, FileMode.Create))
            {
                stream.CopyTo(fileStream);
                Console.WriteLine($"[Loader] -> Extracted '{Path.GetFileName(outputPath)}'.");
            }
        }
    }
}