package megabytesme.minelights.network;

import megabytesme.minelights.MineLightsClient;
import megabytesme.minelights.config.CompassPriority;
import megabytesme.minelights.config.DimmingMode;
import megabytesme.minelights.config.MineLightsConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Client-side chat commands for the settings and actions exposed by MineLights. */
public final class MineLightsCommands {
    private static final String[] SETTING_NAMES = {
            "enabled", "auto-start-server", "manual-server-management", "notify-missing-server",
            "check-server-updates", "corsair", "asus", "logitech", "razer", "wooting", "steelseries",
            "msi", "novation", "picopi", "openrgb", "yeelight", "health-bar", "hunger-bar",
            "saturation-bar", "experience-bar", "locator-bar", "compass-effect", "always-show-compass",
            "compass-priority", "low-health-warning", "biome-effects", "dimming-mode", "min-brightness",
            "weather-effects", "mirror-ram-rain-direction", "end-flash-effect", "fire-effect", "water-effect",
            "portal-effects", "movement-keys", "chat-key-pulse"
    };

    private MineLightsCommands() {
    }

    /** Handles slash-prefixed chat messages on versions where chat and commands share a method. */
    public static boolean handleSlashMessage(String message) {
        if (message == null || !message.trim().startsWith("/")) {
            return false;
        }
        return dispatch(message, true);
    }

    /** Handles input already routed through Minecraft's command packet method. */
    public static boolean handleCommand(String command) {
        return dispatch(command, false);
    }

    private static boolean dispatch(String input, boolean slashWasRequired) {
        if (input == null) {
            return false;
        }
        String normalized = input.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        } else if (slashWasRequired) {
            return false;
        }

        String[] args = normalized.isEmpty() ? new String[0] : normalized.split("\\s+");
        if (args.length == 0 || !"minelights".equalsIgnoreCase(args[0])) {
            return false;
        }

        if (MineLightsClient.CONFIG == null) {
            MineLightsClient.sendChatMessage("[MineLights] The mod is still starting; try that command again shortly.");
            return true;
        }

        try {
            execute(args);
        } catch (IllegalArgumentException exception) {
            MineLightsClient.sendChatMessage("[MineLights] " + exception.getMessage());
            MineLightsClient.sendChatMessage("[MineLights] Use /minelights help for command syntax.");
        } catch (RuntimeException exception) {
            MineLightsClient.LOGGER.error("Could not run MineLights chat command.", exception);
            MineLightsClient.sendChatMessage("[MineLights] The command failed. Check the game log for details.");
        }
        return true;
    }

    private static void execute(String[] args) {
        if (args.length == 1 || "help".equalsIgnoreCase(args[1])) {
            showHelp();
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if ("list".equals(action) || "status".equals(action)) {
            listSettings();
        } else if ("get".equals(action)) {
            if (args.length < 3 || "all".equalsIgnoreCase(args[2])) {
                listSettings();
            } else {
                String name = normalizeName(join(args, 2, args.length));
                MineLightsClient.sendChatMessage("[MineLights] " + name + " = " + getValue(name));
            }
        } else if ("set".equals(action)) {
            if (args.length < 4) {
                throw new IllegalArgumentException("Usage: /minelights set <setting> <value>");
            }
            String name = normalizeName(args[2]);
            setSetting(name, join(args, 3, args.length));
        } else if ("toggle".equals(action)) {
            if (args.length != 3) {
                throw new IllegalArgumentException("Usage: /minelights toggle <boolean-setting>");
            }
            String name = normalizeName(args[2]);
            Boolean current = getBoolean(name);
            if (current == null) {
                throw new IllegalArgumentException("'" + name + "' is not a boolean setting.");
            }
            setBoolean(name, !current.booleanValue());
            saveSetting(name);
            MineLightsClient.sendChatMessage("[MineLights] " + name + " = " + getValue(name));
        } else if ("devices".equals(action)) {
            if (args.length >= 3 && "clear".equalsIgnoreCase(args[2])) {
                clearDisabledDevices();
            } else {
                listDevices();
            }
        } else if ("device".equals(action)) {
            setDevice(args);
        } else if ("refresh".equals(action)) {
            MineLightsClient.refreshLightingManager();
            MineLightsClient.sendChatMessage("[MineLights] Device refresh requested.");
        } else if ("clear-disabled".equals(action) || "clear-disabled-devices".equals(action)) {
            clearDisabledDevices();
        } else if ("server".equals(action)) {
            serverAction(args, 2);
        } else if ("restart".equals(action) || "restart-admin".equals(action)
                || "check-update".equals(action) || "check-updates".equals(action)) {
            serverAction(args, 1);
        } else {
            throw new IllegalArgumentException("Unknown action '" + args[1] + "'.");
        }
    }

    private static void showHelp() {
        List<String> lines = new ArrayList<String>();
        lines.add("[MineLights] Client-side configuration commands:");
        lines.add("[MineLights] /minelights list | get <setting> | set <setting> <value> | toggle <boolean-setting>");
        lines.add("[MineLights] /minelights devices [clear] | device <number-or-id> <on|off> | refresh");
        lines.add("[MineLights] /minelights server <restart|restart-admin|check-update> | clear-disabled");
        lines.add("[MineLights] Dimming: local, sky, off. Brightness accepts 0–100 percent. Compass priority accepts priority, standard-first, lodestone-first, recovery-first.");
        MineLightsClient.sendChatMessages(lines);
    }

    private static void listSettings() {
        List<String> lines = new ArrayList<String>();
        lines.add("[MineLights] Current settings (use /minelights set <setting> <value>):");
        for (int i = 0; i < SETTING_NAMES.length; i += 4) {
            StringBuilder row = new StringBuilder("[MineLights] ");
            int end = Math.min(i + 4, SETTING_NAMES.length);
            for (int j = i; j < end; j++) {
                if (j > i) {
                    row.append(" | ");
                }
                String name = SETTING_NAMES[j];
                row.append(name).append('=').append(getValue(name));
            }
            lines.add(row.toString());
        }
        MineLightsClient.sendChatMessages(lines);
    }

    private static String getValue(String name) {
        Boolean booleanValue = getBoolean(name);
        if (booleanValue != null) {
            return booleanValue.booleanValue() ? "on" : "off";
        }
        MineLightsConfig config = MineLightsClient.CONFIG;
        if ("dimming-mode".equals(name)) {
            return config.dimmingMode == null ? "local" : config.dimmingMode.name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
        if ("min-brightness".equals(name)) {
            return Math.round(config.minBrightness * 100.0F) + "%";
        }
        if ("compass-priority".equals(name)) {
            return config.compassPriority == null ? "priority" : config.compassPriority.name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
        throw new IllegalArgumentException("Unknown setting '" + name + "'. Use /minelights list to see settings.");
    }

    private static void setSetting(String name, String rawValue) {
        Boolean booleanValue = parseBoolean(rawValue);
        if (getBoolean(name) != null) {
            if (booleanValue == null) {
                throw new IllegalArgumentException("Use on/off or true/false for '" + name + "'.");
            }
            setBoolean(name, booleanValue.booleanValue());
        } else if ("dimming-mode".equals(name)) {
            MineLightsClient.CONFIG.dimmingMode = parseDimmingMode(rawValue);
        } else if ("compass-priority".equals(name)) {
            MineLightsClient.CONFIG.compassPriority = parseCompassPriority(rawValue);
        } else if ("min-brightness".equals(name)) {
            MineLightsClient.CONFIG.minBrightness = parseBrightness(rawValue);
        } else {
            throw new IllegalArgumentException("Unknown setting '" + name + "'. Use /minelights list to see settings.");
        }

        MineLightsClient.saveConfig();
        if ("auto-start-server".equals(name)) {
            MineLightsClient.updateServerMonitorSetting();
        }
        if (isIntegrationSetting(name)) {
            MineLightsClient.refreshLightingManager();
        }
        MineLightsClient.sendChatMessage("[MineLights] " + name + " = " + getValue(name));
    }

    private static Boolean getBoolean(String name) {
        MineLightsConfig config = MineLightsClient.CONFIG;
        switch (name) {
            case "enabled": return Boolean.valueOf(config.enableMod);
            case "auto-start-server": return Boolean.valueOf(config.autoStartServer);
            case "manual-server-management": return Boolean.valueOf(config.manualServerManagement);
            case "notify-missing-server": return Boolean.valueOf(config.notifyMissingServer);
            case "check-server-updates": return Boolean.valueOf(config.checkServerUpdates);
            case "corsair": return Boolean.valueOf(config.enableCorsair);
            case "asus": return Boolean.valueOf(config.enableAsus);
            case "logitech": return Boolean.valueOf(config.enableLogitech);
            case "razer": return Boolean.valueOf(config.enableRazer);
            case "wooting": return Boolean.valueOf(config.enableWooting);
            case "steelseries": return Boolean.valueOf(config.enableSteelSeries);
            case "msi": return Boolean.valueOf(config.enableMsi);
            case "novation": return Boolean.valueOf(config.enableNovation);
            case "picopi": return Boolean.valueOf(config.enablePicoPi);
            case "openrgb": return Boolean.valueOf(config.enableOpenRgb);
            case "yeelight": return Boolean.valueOf(config.enableYeelight);
            case "health-bar": return Boolean.valueOf(config.enableHealthBar);
            case "hunger-bar": return Boolean.valueOf(config.enableHungerBar);
            case "saturation-bar": return Boolean.valueOf(config.enableSaturationBar);
            case "experience-bar": return Boolean.valueOf(config.enableExperienceBar);
            case "locator-bar": return Boolean.valueOf(config.enableLocatorBar);
            case "compass-effect": return Boolean.valueOf(config.enableCompassEffect);
            case "always-show-compass": return Boolean.valueOf(config.alwaysShowCompass);
            case "low-health-warning": return Boolean.valueOf(config.enableLowHealthWarning);
            case "biome-effects": return Boolean.valueOf(config.enableBiomeEffects);
            case "weather-effects": return Boolean.valueOf(config.enableWeatherEffects);
            case "mirror-ram-rain-direction": return Boolean.valueOf(config.mirrorRamRainDirection);
            case "end-flash-effect": return Boolean.valueOf(config.enableEndFlashEffect);
            case "fire-effect": return Boolean.valueOf(config.enableOnFireEffect);
            case "water-effect": return Boolean.valueOf(config.enableInWaterEffect);
            case "portal-effects": return Boolean.valueOf(config.enablePortalEffects);
            case "movement-keys": return Boolean.valueOf(config.highlightMovementKeys);
            case "chat-key-pulse": return Boolean.valueOf(config.pulseChatKey);
            default: return null;
        }
    }

    private static void setBoolean(String name, boolean value) {
        MineLightsConfig config = MineLightsClient.CONFIG;
        switch (name) {
            case "enabled": config.enableMod = value; break;
            case "auto-start-server": config.autoStartServer = value; break;
            case "manual-server-management": config.manualServerManagement = value; break;
            case "notify-missing-server": config.notifyMissingServer = value; break;
            case "check-server-updates": config.checkServerUpdates = value; break;
            case "corsair": config.enableCorsair = value; break;
            case "asus": config.enableAsus = value; break;
            case "logitech": config.enableLogitech = value; break;
            case "razer": config.enableRazer = value; break;
            case "wooting": config.enableWooting = value; break;
            case "steelseries": config.enableSteelSeries = value; break;
            case "msi": config.enableMsi = value; break;
            case "novation": config.enableNovation = value; break;
            case "picopi": config.enablePicoPi = value; break;
            case "openrgb": config.enableOpenRgb = value; break;
            case "yeelight": config.enableYeelight = value; break;
            case "health-bar": config.enableHealthBar = value; break;
            case "hunger-bar": config.enableHungerBar = value; break;
            case "saturation-bar": config.enableSaturationBar = value; break;
            case "experience-bar": config.enableExperienceBar = value; break;
            case "locator-bar": config.enableLocatorBar = value; break;
            case "compass-effect": config.enableCompassEffect = value; break;
            case "always-show-compass": config.alwaysShowCompass = value; break;
            case "low-health-warning": config.enableLowHealthWarning = value; break;
            case "biome-effects": config.enableBiomeEffects = value; break;
            case "weather-effects": config.enableWeatherEffects = value; break;
            case "mirror-ram-rain-direction": config.mirrorRamRainDirection = value; break;
            case "end-flash-effect": config.enableEndFlashEffect = value; break;
            case "fire-effect": config.enableOnFireEffect = value; break;
            case "water-effect": config.enableInWaterEffect = value; break;
            case "portal-effects": config.enablePortalEffects = value; break;
            case "movement-keys": config.highlightMovementKeys = value; break;
            case "chat-key-pulse": config.pulseChatKey = value; break;
            default: throw new IllegalArgumentException("Unknown boolean setting '" + name + "'.");
        }
    }

    private static boolean isIntegrationSetting(String name) {
        return "corsair".equals(name) || "asus".equals(name) || "logitech".equals(name)
                || "razer".equals(name) || "wooting".equals(name) || "steelseries".equals(name)
                || "msi".equals(name) || "novation".equals(name) || "picopi".equals(name)
                || "openrgb".equals(name) || "yeelight".equals(name);
    }

    private static void saveSetting(String name) {
        MineLightsClient.saveConfig();
        if ("auto-start-server".equals(name)) {
            MineLightsClient.updateServerMonitorSetting();
        }
        if (isIntegrationSetting(name)) {
            MineLightsClient.refreshLightingManager();
        }
    }

    private static String normalizeName(String value) {
        String name = value.replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                .toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
        while (name.contains("--")) {
            name = name.replace("--", "-");
        }
        if ("output".equals(name) || "lighting-output".equals(name) || "enabled".equals(name) || "enable-mod".equals(name)) return "enabled";
        if ("auto-start".equals(name) || "autostart".equals(name) || "server-autostart".equals(name)
                || "enable-server-autostart".equals(name)) return "auto-start-server";
        if ("manual-server".equals(name) || "manual-management".equals(name)) return "manual-server-management";
        if ("missing-server".equals(name) || "missing-server-notice".equals(name)
                || "notify-when-server-missing".equals(name)) return "notify-missing-server";
        if ("server-update-notices".equals(name) || "update-notices".equals(name)
                || "check-for-server-updates".equals(name)) return "check-server-updates";
        if ("open-rgb".equals(name)) return "openrgb";
        if (name.endsWith("-integration")) name = name.substring(0, name.length() - "-integration".length());
        if (name.startsWith("enable-") && getBoolean(name.substring("enable-".length())) != null) {
            name = name.substring("enable-".length());
        }
        if ("saturation-air-bar".equals(name) || "saturation".equals(name)) return "saturation-bar";
        if ("experience".equals(name)) return "experience-bar";
        if ("health".equals(name)) return "health-bar";
        if ("hunger".equals(name)) return "hunger-bar";
        if ("locator".equals(name)) return "locator-bar";
        if ("compass".equals(name)) return "compass-effect";
        if ("compass-priority-mode".equals(name) || "priority".equals(name)) return "compass-priority";
        if ("biome-lighting".equals(name) || "biome".equals(name)) return "biome-effects";
        if ("dimming".equals(name)) return "dimming-mode";
        if ("brightness".equals(name) || "minimum-brightness".equals(name)) return "min-brightness";
        if ("weather".equals(name)) return "weather-effects";
        if ("mirror-ram-rain".equals(name) || "ram-rain-direction".equals(name)) return "mirror-ram-rain-direction";
        if ("end-flash".equals(name)) return "end-flash-effect";
        if ("fire".equals(name)) return "fire-effect";
        if ("water".equals(name)) return "water-effect";
        if ("portal".equals(name)) return "portal-effects";
        if ("movement".equals(name) || "movement-highlight".equals(name)) return "movement-keys";
        if ("chat-pulse".equals(name)) return "chat-key-pulse";
        if ("low-health".equals(name)) return "low-health-warning";
        return name;
    }

    private static Boolean parseBoolean(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if ("on".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized) || "1".equals(normalized)) {
            return Boolean.TRUE;
        }
        if ("off".equals(normalized) || "false".equals(normalized) || "no".equals(normalized) || "0".equals(normalized)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static DimmingMode parseDimmingMode(String value) {
        String normalized = normalizeEnum(value);
        if ("off".equals(normalized) || "none".equals(normalized) || "disabled".equals(normalized)) {
            return DimmingMode.NONE;
        }
        if ("sky".equals(normalized) || "sky-light".equals(normalized)) {
            return DimmingMode.SKY_LIGHT;
        }
        if ("local".equals(normalized) || "local-light".equals(normalized)) {
            return DimmingMode.LOCAL_LIGHT;
        }
        throw new IllegalArgumentException("Dimming mode must be local, sky, or off.");
    }

    private static CompassPriority parseCompassPriority(String value) {
        String normalized = normalizeEnum(value);
        if ("inventory-order".equals(normalized) || "inventory".equals(normalized)) normalized = "priority";
        if ("standard".equals(normalized)) normalized = "standard-first";
        if ("lodestone".equals(normalized)) normalized = "lodestone-first";
        for (CompassPriority priority : CompassPriority.values()) {
            if (priority.name().toLowerCase(Locale.ROOT).replace('_', '-').equals(normalized)) {
                return priority;
            }
        }
        throw new IllegalArgumentException("Unknown compass priority. Available: " + enumValues(CompassPriority.values()) + ".");
    }

    private static String normalizeEnum(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
    }

    private static String enumValues(Enum<?>[] values) {
        List<String> names = new ArrayList<String>();
        for (Enum<?> value : values) {
            names.add(value.name().toLowerCase(Locale.ROOT).replace('_', '-'));
        }
        return join(names, ", ");
    }

    private static float parseBrightness(String value) {
        String normalized = value.trim();
        boolean percent = normalized.endsWith("%");
        if (percent) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        try {
            float brightness = Float.parseFloat(normalized);
            if (percent || brightness > 1.0F) {
                brightness /= 100.0F;
            }
            if (brightness < 0.0F || brightness > 1.0F || Float.isNaN(brightness)) {
                throw new NumberFormatException();
            }
            return brightness;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Minimum brightness must be between 0 and 100 percent.");
        }
    }

    private static void listDevices() {
        List<String> devices = getDeviceIds();
        if (devices.isEmpty()) {
            MineLightsClient.sendChatMessage("[MineLights] No devices are known yet. Use /minelights refresh, then try again.");
            return;
        }
        List<String> lines = new ArrayList<String>();
        lines.add("[MineLights] Devices (use /minelights device <number> <on|off>):");
        for (int i = 0; i < devices.size(); i++) {
            String id = devices.get(i);
            boolean enabled = !MineLightsClient.CONFIG.disabledDevices.contains(id);
            lines.add("[MineLights] " + (i + 1) + ". " + id + " = " + (enabled ? "on" : "off"));
        }
        MineLightsClient.sendChatMessages(lines);
    }

    private static void setDevice(String[] args) {
        if (args.length < 4) {
            throw new IllegalArgumentException("Usage: /minelights device <number-or-id> <on|off>");
        }
        String state = args[args.length - 1];
        Boolean enabled = parseBoolean(state);
        if (enabled == null) {
            throw new IllegalArgumentException("Device state must be on or off.");
        }
        String selector = join(args, 2, args.length - 1).trim();
        if (selector.length() >= 2 && selector.startsWith("\"") && selector.endsWith("\"")) {
            selector = selector.substring(1, selector.length() - 1);
        }
        List<String> ids = getDeviceIds();
        String id = selector;
        try {
            int index = Integer.parseInt(selector) - 1;
            if (index < 0 || index >= ids.size()) {
                throw new IllegalArgumentException("Device number is out of range. Use /minelights devices.");
            }
            id = ids.get(index);
        } catch (NumberFormatException ignored) {
            for (String candidate : ids) {
                if (candidate.equalsIgnoreCase(selector)) {
                    id = candidate;
                    break;
                }
            }
        }

        if (!ids.contains(id)) {
            throw new IllegalArgumentException("Unknown device '" + selector + "'. Use /minelights devices.");
        }
        if (MineLightsClient.CONFIG.disabledDevices == null) {
            MineLightsClient.CONFIG.disabledDevices = new ArrayList<String>();
        }
        String existing = findCaseInsensitive(MineLightsClient.CONFIG.disabledDevices, id);
        if (enabled.booleanValue()) {
            if (existing != null) MineLightsClient.CONFIG.disabledDevices.remove(existing);
        } else if (existing == null) {
            MineLightsClient.CONFIG.disabledDevices.add(id);
        }
        MineLightsClient.saveConfig();
        MineLightsClient.refreshLightingManager();
        MineLightsClient.sendChatMessage("[MineLights] Device " + id + " = " + (enabled.booleanValue() ? "on" : "off"));
    }

    private static List<String> getDeviceIds() {
        List<String> ids = new ArrayList<String>();
        synchronized (MineLightsClient.discoveredDevices) {
            ids.addAll(MineLightsClient.discoveredDevices);
        }
        if (MineLightsClient.CONFIG.disabledDevices == null) {
            MineLightsClient.CONFIG.disabledDevices = new ArrayList<String>();
        }
        for (String id : MineLightsClient.CONFIG.disabledDevices) {
            if (id != null && !containsCaseInsensitive(ids, id)) {
                ids.add(id);
            }
        }
        Collections.sort(ids, Comparator.naturalOrder());
        return ids;
    }

    private static boolean containsCaseInsensitive(List<String> values, String sought) {
        return findCaseInsensitive(values, sought) != null;
    }

    private static String findCaseInsensitive(List<String> values, String sought) {
        for (String value : values) {
            if (value.equalsIgnoreCase(sought)) return value;
        }
        return null;
    }

    private static void clearDisabledDevices() {
        if (MineLightsClient.CONFIG.disabledDevices == null) {
            MineLightsClient.CONFIG.disabledDevices = new ArrayList<String>();
        }
        MineLightsClient.CONFIG.disabledDevices.clear();
        MineLightsClient.saveConfig();
        MineLightsClient.refreshLightingManager();
        MineLightsClient.sendChatMessage("[MineLights] All disabled devices have been re-enabled.");
    }

    private static void serverAction(String[] args, int commandIndex) {
        if (args.length <= commandIndex) {
            throw new IllegalArgumentException("Usage: /minelights server <restart|restart-admin|check-update>");
        }
        String action = args[commandIndex].toLowerCase(Locale.ROOT);
        if ("restart".equals(action)) {
            if (!MineLightsClient.IS_WINDOWS) {
                throw new IllegalArgumentException("MineLights Server restart is available on Windows only.");
            }
            CommandClient.sendCommand("restart");
            MineLightsClient.sendChatMessage("[MineLights] MineLights Server restart requested.");
        } else if ("restart-admin".equals(action) || "restart-admin-as-administrator".equals(action)) {
            if (!MineLightsClient.IS_WINDOWS) {
                throw new IllegalArgumentException("MineLights Server restart is available on Windows only.");
            }
            CommandClient.sendCommand("restart_admin");
            MineLightsClient.sendChatMessage("[MineLights] MineLights Server administrator restart requested.");
        } else if ("check-update".equals(action) || "check-updates".equals(action)) {
            new Thread(MineLightsClient::checkForServerUpdate, "MineLights-Manual-Update-Check").start();
            MineLightsClient.sendChatMessage("[MineLights] MineLights Server update check requested.");
        } else {
            throw new IllegalArgumentException("Unknown server action '" + action + "'.");
        }
    }

    private static String join(String[] values, int start, int end) {
        StringBuilder result = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (i > start) result.append(' ');
            result.append(values[i]);
        }
        return result.toString();
    }

    private static String join(List<String> values, String delimiter) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) result.append(delimiter);
            result.append(value);
        }
        return result.toString();
    }
}
