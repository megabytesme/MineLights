package megabytesme.minelights.network;

import java.util.HashSet;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import megabytesme.minelights.config.MineLightsConfig;

/** Per-client integration and migrated device exclusions, never server-wide configuration. */
public final class V2DevicePolicy {
    private static final String PREFIX = "@disabled-provider:";
    private static final String LEGACY_ENABLED = "@enabled-legacy:";
    private static final String[][] PROVIDERS = {{"Corsair", "corsair"}, {"Asus", "asus"},
            {"Logitech", "logitech"}, {"Razer", "razer"}, {"Wooting", "wooting"},
            {"SteelSeries", "steelseries"}, {"Msi", "msi"}, {"Novation", "novation"}, {"PicoPi", "picopi"}};
    private V2DevicePolicy() {}

    public static Set<String> exclusions(MineLightsConfig c) {
        Set<String> result = new HashSet<String>();
        if (c.disabledDevices != null) result.addAll(c.disabledDevices);
        if (c.enabledLegacyDevices != null) for (String id : c.enabledLegacyDevices) result.add(LEGACY_ENABLED + id);
        if (!c.enableCorsair) result.add(PREFIX + "corsair");
        if (!c.enableAsus) result.add(PREFIX + "asus");
        if (!c.enableLogitech) result.add(PREFIX + "logitech");
        if (!c.enableRazer) result.add(PREFIX + "razer");
        if (!c.enableWooting) result.add(PREFIX + "wooting");
        if (!c.enableSteelSeries) result.add(PREFIX + "steelseries");
        if (!c.enableMsi) result.add(PREFIX + "msi");
        if (!c.enableNovation) result.add(PREFIX + "novation");
        if (!c.enablePicoPi) result.add(PREFIX + "picopi");
        // A settings change must also reconcile direct outputs, which have no V2 lease.
        if (!c.enableOpenRgb) result.add("@direct-disabled:openrgb");
        if (!c.enableYeelight) result.add("@direct-disabled:yeelight");
        return result;
    }

    public static boolean deviceEnabled(MineLightsConfig c, String id, String legacyKey) {
        if (c.disabledDevices.contains(id)) return false;
        return legacyKey == null || !c.disabledDevices.contains(legacyKey) || c.enabledLegacyDevices.contains(id);
    }

    public static void setDeviceEnabled(MineLightsConfig c, String id, String legacyKey, boolean enabled) {
        c.disabledDevices.remove(id);
        c.enabledLegacyDevices.remove(id);
        if (enabled) {
            if (legacyKey != null && c.disabledDevices.contains(legacyKey)) c.enabledLegacyDevices.add(id);
        } else {
            c.disabledDevices.add(id);
        }
    }

    public static String canonicalProvider(String provider, String integration) {
        String value = (provider == null || provider.trim().isEmpty() ? integration : provider);
        value = value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (value.contains("corsair")) return "corsair";
        if (value.contains("asus") || value.contains("asustek")) return "asus";
        if (value.contains("logitech")) return "logitech";
        if (value.contains("razer")) return "razer";
        if (value.contains("wooting")) return "wooting";
        if (value.contains("steelseries")) return "steelseries";
        if (value.contains("msi") || value.contains("microstar")) return "msi";
        if (value.contains("novation")) return "novation";
        if (value.contains("picopi")) return "picopi";
        return value;
    }

    /** The server applies this interest only after authenticating the client. */
    public static List<String> requestedProviders(Set<String> disabled) {
        List<String> result = new ArrayList<String>();
        if (disabled.contains("*")) return result;
        for (String[] provider : PROVIDERS) if (!disabled.contains(PREFIX + provider[1])) result.add(provider[0]);
        return result;
    }

    public static String legacyKey(V2ClientConnection.DeviceDescriptor device) {
        // Older V2 servers may lack legacy_device_key; use the same best-effort key for policy and UI.
        return device.legacyDeviceKey == null || device.legacyDeviceKey.isEmpty()
                ? device.integration + "|" + device.name : device.legacyDeviceKey;
    }

    public static boolean allows(Set<String> disabled, String serverId, V2ClientConnection.DeviceDescriptor device) {
        String provider = canonicalProvider(device.provider, device.integration);
        String oldKey = legacyKey(device);
        return !disabled.contains("*") && !disabled.contains(PREFIX + provider)
                && (!disabled.contains(oldKey) || disabled.contains(LEGACY_ENABLED + "MineLights V2|" + serverId + "|" + device.deviceId))
                && !disabled.contains(serverId + "|" + device.deviceId)
                && !disabled.contains("MineLights V2|" + serverId + "|" + device.deviceId);
    }
}
