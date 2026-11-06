package megabytesme.minelights;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import megabytesme.minelights.config.MineLightsConfig;
import megabytesme.minelights.config.SimpleJsonConfig;
import megabytesme.minelights.network.V2Endpoint;
import megabytesme.minelights.network.V2ClientConnection;
import megabytesme.minelights.network.ServerDownloadNotificationState;
import megabytesme.minelights.runtime.LightingManager;
//? if loader_forge && <=1.12.2 {
//?} else if loader_forge && <1.17 {
import net.minecraft.util.SharedConstants;
//?} else {
import net.minecraft.SharedConstants;
//?}
//? if loader_forge && <1.17 {
import net.minecraft.client.Minecraft;
//? if loader_forge && <=1.8.9 {
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
//?} else if loader_forge && <=1.13.2 {
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.util.text.TextComponentString;
//?} else {
import net.minecraft.client.gui.screen.MainMenuScreen;
import net.minecraft.util.text.StringTextComponent;
//?}
//? if loader_forge && <=1.8.9 {
//?} else {
import net.minecraft.util.text.event.ClickEvent;
//? if (loader_forge && <=1.15.2) || (loader_forge && 1.16.1) {
import net.minecraft.util.text.ITextComponent;
//?} else {
import net.minecraft.util.text.IFormattableTextComponent;
//?}
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;
//?}
//?} else if loader_forge || loader_neoforge || >=26.1 {
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
//? if <1.19 {
import net.minecraft.network.chat.TextComponent;
//?}
//?} else {
/* import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.ClickEvent;
//? if >1.15.2 {
import net.minecraft.text.MutableText;
//?}
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
*///?}
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class MineLightsClient {
    public static final Logger LOGGER = LogManager.getLogger("MineLights");
    public static final String MOD_ID = "minelights";
    public static final String MOD_VERSION = "2.4.0";
    private static Path configDir = Paths.get(".");
    private static volatile String resolvedModVersion;
    private static volatile String resolvedModLoader;
    public static MineLightsConfig CONFIG;
    private static SimpleJsonConfig CONFIG_MANAGER;
    private static LightingManager lightingManager;
    private static Thread lightingManagerThread;
    public static Thread serverMonitorThread;

    public static final List<String> discoveredDevices = Collections.synchronizedList(new ArrayList<>());
    public static final java.util.concurrent.ConcurrentMap<String, String> discoveredDeviceNames =
            new java.util.concurrent.ConcurrentHashMap<String, String>();

    public static final java.util.concurrent.ConcurrentMap<String, String> discoveredLegacyDeviceKeys =
            new java.util.concurrent.ConcurrentHashMap<String, String>();

    public static boolean isDeviceEnabled(String id) {
        return megabytesme.minelights.network.V2DevicePolicy.deviceEnabled(CONFIG, id, discoveredLegacyDeviceKeys.get(id));
    }

    public static void setDeviceEnabled(String id, boolean enabled) {
        megabytesme.minelights.network.V2DevicePolicy.setDeviceEnabled(CONFIG, id, discoveredLegacyDeviceKeys.get(id), enabled);
    }

    public static String getDiscoveredDeviceDisplayName(String uniqueId) {
        String displayName = discoveredDeviceNames.get(uniqueId);
        if (displayName != null && !displayName.trim().isEmpty()) {
            return displayName;
        }
        int separator = uniqueId == null ? -1 : uniqueId.indexOf('|');
        return separator >= 0 ? uniqueId.substring(separator + 1) : uniqueId;
    }
    public static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    public static volatile boolean isProxyConnected = false;

    public static String getModVersion() {
        return resolvedModVersion != null ? resolvedModVersion : MOD_VERSION;
    }

    //? if loader_forge {
    public static Minecraft getMinecraft() {
        //? if loader_forge && <=1.12.2 {
        return Minecraft.getMinecraft();
        //?} else {
        return Minecraft.getInstance();
        //?}
    }
    //?} else if loader_neoforge || >=26.1 {
    /*public static Minecraft getMinecraft() {
        return Minecraft.getInstance();
    }
    *///?} else {
    /*public static MinecraftClient getMinecraft() {
        return MinecraftClient.getInstance();
    }
    *///?}

    private static final Pattern MINECRAFT_VERSION_PATTERN = Pattern.compile("(?:^|\\D)(\\d+\\.\\d+(?:\\.\\d+)?(?:-pre\\d+)?)(?:$|\\D)");

    private static String getMinecraftVersionString() {
        //? if loader_forge && 1.7.2 {
        return "1.7.2";
        //?} else if loader_forge && <=1.7.10 {
        return "1.7.10";
        //?} else if loader_forge && <=1.13.2 {
        return getMinecraft().getVersion();
        //?} else {
        for (Method versionAccessor : SharedConstants.class.getDeclaredMethods()) {
            if (!Modifier.isStatic(versionAccessor.getModifiers()) || versionAccessor.getParameterCount() != 0
                    || versionAccessor.getReturnType().isPrimitive() || versionAccessor.getReturnType() == String.class) {
                continue;
            }

            try {
                versionAccessor.setAccessible(true);
                Object gameVersion = versionAccessor.invoke(null);
                if (gameVersion == null) {
                    continue;
                }
                for (Method stringAccessor : gameVersion.getClass().getMethods()) {
                    if (stringAccessor.getParameterCount() != 0 || stringAccessor.getReturnType() != String.class) {
                        continue;
                    }
                    Object value = stringAccessor.invoke(gameVersion);
                    if (value instanceof String) {
                        String versionString = (String) value;
                        Matcher matcher = MINECRAFT_VERSION_PATTERN.matcher(versionString);
                        if (matcher.find()) {
                            return matcher.group(1);
                        }
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // SharedConstants changed its version API between Minecraft releases.
            }
        }
        throw new IllegalStateException("Could not determine the Minecraft version from SharedConstants.");
        //?}
    }

    private static String normalizeModVersion(String version) {
        if (version == null) {
            return "";
        }
        int buildMetadataIndex = version.indexOf('+');
        return buildMetadataIndex < 0 ? version : version.substring(0, buildMetadataIndex);
    }

    private static boolean isNewerModVersion(String latestVersion, String currentVersion) {
        String latest = normalizeModVersion(latestVersion);
        String current = normalizeModVersion(currentVersion);
        if (latest.equals(current)) {
            return false;
        }
        String[] latestParts = latest.split("\\.");
        String[] currentParts = current.split("\\.");
        for (int i = 0; i < Math.max(latestParts.length, currentParts.length); i++) {
            try {
                int latestPart = i < latestParts.length ? Integer.parseInt(latestParts[i]) : 0;
                int currentPart = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
                if (latestPart != currentPart) {
                    return latestPart > currentPart;
                }
            } catch (NumberFormatException unknownVersionFormat) {
                return !latest.equals(current);
            }
        }
        return false;
    }

    //? if loader_forge || loader_neoforge || >=26.1 {
    //? if loader_forge && <1.17 {
    //? if loader_forge && <=1.8.9 {
    private static IChatComponent literalComponent(String text) {
        return new ChatComponentText(text);
    }
    //?} else if loader_forge && <=1.13.2 {
    private static ITextComponent literalComponent(String text) {
        return new TextComponentString(text);
    }
    //?} else if <=1.15.2 || 1.16.1 {
    private static ITextComponent literalComponent(String text) {
        return new StringTextComponent(text);
    }
    //?} else {
    private static IFormattableTextComponent literalComponent(String text) {
        return new StringTextComponent(text);
    }
    //?}
    //?} else {
    private static MutableComponent literalComponent(String text) {
        //? if loader_forge && <1.19 {
        return new TextComponent(text);
        //?} else {
        /* return Component.literal(text);
        *///?}
    }
    //?}
    //?}


    // 0 = no check, 1 = notice-only, 2 = automatic install permitted.
    private static final java.util.concurrent.atomic.AtomicInteger serverCheckMode = new java.util.concurrent.atomic.AtomicInteger();
    private static final String GITHUB_API_URL = "https://api.github.com/repos/megabytesme/MineLights-Server/releases/latest";
    private static final String SERVER_RELEASES_URL = "https://github.com/megabytesme/MineLights-Server/releases";
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static final AtomicBoolean shutdownHookRegistered = new AtomicBoolean(false);
    private static final AtomicBoolean shutdownStarted = new AtomicBoolean(false);

    private static final AtomicBoolean lightingInitialized = new AtomicBoolean(false);

    private static final String MODRINTH_PROJECT_ID = "minelights"; 
    private static final AtomicBoolean hasCheckedForUpdate = new AtomicBoolean(false);
    private static final AtomicReference<String> pendingUpdateVersion = new AtomicReference<>(null);
    private static final AtomicReference<String> pendingUpdateUrl = new AtomicReference<>(null);
    private static final AtomicBoolean pendingUpdateNotification = new AtomicBoolean(false);
    private static final AtomicBoolean hasCheckedForMissingServer = new AtomicBoolean(false);
    private static final ConcurrentLinkedQueue<List<PendingServerMessage>> pendingServerMessages = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean serverDownloadInProgress = new AtomicBoolean(false);
    private static final AtomicBoolean automaticDownloadStarted = new AtomicBoolean(false);
    private static final ServerDownloadNotificationState serverDownloadNotificationState =
            new ServerDownloadNotificationState();
    private static volatile boolean clientInWorld;
    private static final Object automaticUpdateLock = new Object();
    private static final Object automaticUpdateExecution = new Object();
    private static volatile CountDownLatch automaticUpdateDecision;
    private static volatile long automaticUpdateStartedNanos;
    private static final java.util.concurrent.atomic.AtomicLong automaticDownloadProgressNanos = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicReference<HttpURLConnection> activeAutomaticDownload = new java.util.concurrent.atomic.AtomicReference<HttpURLConnection>();

    private static Process serverProcess = null;
    private static String serverLaunchId = null;
    private static final AtomicBoolean manualStopRequested = new AtomicBoolean(false);
    private static final AtomicBoolean legacyServerNoticeSent = new AtomicBoolean(false);
    private static long legacyOnlySince;


    private static final class ServerRelease {
        private final String version;
        private final String changelog;
        private final String downloadUrl;
        private final String expectedHash;

        private ServerRelease(String version, String changelog, String downloadUrl, String expectedHash) {
            this.version = version;
            this.changelog = changelog;
            this.downloadUrl = downloadUrl;
            this.expectedHash = expectedHash;
        }
    }

    private static final class PendingServerMessage {
        private final String text;
        private final String translationKey;
        private final Object[] arguments;

        private PendingServerMessage(String text, String translationKey, Object[] arguments) {
            this.text = text;
            this.translationKey = translationKey;
            this.arguments = arguments;
        }

        private static PendingServerMessage literal(String text) {
            return new PendingServerMessage(text, null, new Object[0]);
        }

        private static PendingServerMessage translated(String key, Object... arguments) {
            return new PendingServerMessage(null, key, arguments == null ? new Object[0] : arguments);
        }
    }

    public void init(Path configDir, String modVersion, String modLoader) {
        if (!initialized.compareAndSet(false, true)) {
            return;
        }

        MineLightsClient.configDir = configDir;
        MineLightsClient.resolvedModVersion = modVersion;
        MineLightsClient.resolvedModLoader = modLoader;
        if ("forge".equalsIgnoreCase(modLoader) || "neoforge".equalsIgnoreCase(modLoader)
                || "quilt".equalsIgnoreCase(modLoader)) {
            preloadShutdownClasses();
        }
        if (shutdownHookRegistered.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "MineLights-Shutdown"));
        }
        CONFIG_MANAGER = new SimpleJsonConfig("minelights");
        CONFIG = CONFIG_MANAGER.load(MineLightsConfig.class, new MineLightsConfig());
        CONFIG.normalizeCollections();

        if (IS_WINDOWS) {
            initializeServerConnection();
        }

        new Thread(() -> {
            try {
                Thread.sleep(3000);
                triggerLightingInitialization();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "MineLights-Initializer-Waiter").start();
    }

    private static void preloadShutdownClasses() {
        ClassLoader classLoader = MineLightsClient.class.getClassLoader();
        String[] classNames = {
                "megabytesme.minelights.rgb.YeelightController$YeelightDevice"
        };
        for (String className : classNames) {
            try {
                Class.forName(className, false, classLoader);
            } catch (ClassNotFoundException | LinkageError e) {
                LOGGER.warn("Could not preload shutdown class {}.", className, e);
            }
        }
    }

    //? if loader_neoforge || >=26.1 {
    public void onClientTick(Minecraft client) {
    //?} else {
    /* public void onClientTick(MinecraftClient client) {
    *///?}
        //? if loader_forge && <=1.9.4 {
        clientInWorld = client.thePlayer != null && client.theWorld != null;
        //?} else if loader_forge && >=1.14.4 && <1.16.1 {
        clientInWorld = client.player != null && client.level != null;
        //?} else if loader_forge && >=1.16.2 && <1.17 {
        clientInWorld = client.player != null && client.level != null;
        //?} else if loader_forge && <1.17 {
        clientInWorld = client.player != null && client.world != null;
        //?} else {
        clientInWorld = client.player != null && client.level != null;
        //?}
        reconcileLightingState();
        int checkMode = !IS_WINDOWS || !CONFIG.enableMod ? 0
                : automaticServerInstallAllowed() ? 2 : CONFIG.checkServerUpdates ? 1 : 0;
        int previousCheckMode = serverCheckMode.getAndSet(checkMode);
        if (checkMode != 2) resetAutomaticUpdateWhenDisallowed();
        if (checkMode == 2 && checkMode != previousCheckMode) {
            scheduleAutomaticUpdate(false);
        } else if (checkMode == 1 && checkMode != previousCheckMode) {
            new Thread(MineLightsClient::checkForServerUpdate, "MineLights-Update-Check").start();
        }

        if (hasCheckedForUpdate.compareAndSet(false, true)) {
            new Thread(MineLightsClient::checkForUpdate, "MineLights-Modrinth-Update-Check").start();
        }

        flushPendingUpdateNotification();
        flushPendingServerNotification();
    }

    public void shutdown() {
        if (!shutdownStarted.compareAndSet(false, true)) {
            return;
        }
        cancelAutomaticDownload();
        Process process;
        String launchId;
        synchronized (MineLightsClient.class) { process = serverProcess; launchId = serverLaunchId; }
        if (IS_WINDOWS && process != null && process.isAlive() && launchId != null) {
            String result = lightingManager == null ? "unavailable" : lightingManager.requestServerShutdown(null, launchId);
            if ("unavailable".equals(result)) result = requestLocalShutdownWithTemporarySession(launchId);
            LOGGER.info("accepted".equals(result) ? "MineLights Server accepted the shutdown request." :
                    "MineLights Server remains running (other clients may be connected or the server does not support safe shutdown).");
        }
        if (lightingManagerThread != null)
            lightingManagerThread.interrupt();
        if (serverMonitorThread != null)
            serverMonitorThread.interrupt();
        serverProcess = null;
    }

    private static String requestLocalShutdownWithTemporarySession(String expectedLaunchId) {
        if (CONFIG == null) return "unavailable";
        try {
            long deadline = System.currentTimeMillis() + 2000;
            V2Endpoint endpoint;
            while ((endpoint = V2Endpoint.readLocal()) == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(100);
            }
            if (endpoint == null) return "unavailable";
            String clientId;
            String clientSecret;
            synchronized (SimpleJsonConfig.configLock()) {
                CONFIG_MANAGER.ensureCredentialStorageAvailable();
                if (CONFIG.v2ClientId == null || CONFIG.v2ClientId.trim().isEmpty()) CONFIG.v2ClientId = V2ClientConnection.newClientId();
                if (CONFIG.v2ClientSecret == null || CONFIG.v2ClientSecret.isEmpty()) CONFIG.v2ClientSecret = V2ClientConnection.randomSecret();
                saveConfig();
                clientId = CONFIG.v2ClientId;
                clientSecret = CONFIG.v2ClientSecret;
            }
            try (V2ClientConnection connection = V2ClientConnection.connectLocal(endpoint,
                    clientId, clientSecret, V2Endpoint.readLocalEnrollmentKey(),
                    "minelights-java", getModVersion(), Collections.singleton("*"))) {
                if (expectedLaunchId != null && !connection.matchesLaunchId(expectedLaunchId)) return "not_owned";
                return connection.requestServerShutdown();
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            LOGGER.warn("Could not request shutdown of the locally started server: {}", e.getMessage());
            return "unavailable";
        }
    }

    private static String requestRemoteShutdownWithTemporarySession(String serverId) {
        megabytesme.minelights.network.RemoteServerProfile selected = null;
        synchronized (SimpleJsonConfig.configLock()) {
            if (CONFIG == null || CONFIG.remoteServers == null) return "unavailable";
            for (megabytesme.minelights.network.RemoteServerProfile profile : CONFIG.remoteServers) {
                if (profile != null && serverId.equals(profile.serverId)
                        && profile.clientId != null && profile.clientSecret != null) { selected = profile; break; }
            }
        }
        if (selected != null) {
            megabytesme.minelights.network.RemoteServerProfile profile = selected;
            try (V2ClientConnection connection = V2ClientConnection.connectRemote(profile,
                    profile.clientId, "minelights-java", getModVersion(), Collections.singleton("*"))) {
                return connection.requestServerShutdown();
            } catch (IOException e) {
                LOGGER.warn("Could not request shutdown of the paired server: {}", e.getMessage());
                return "unavailable";
            }
        }
        return "unavailable";
    }

    public static void requestServerShutdown(final String serverId) {
        new Thread(() -> {
            LightingManager manager = lightingManager;
            if (serverId == null) manualStopRequested.set(true);
            String result = manager == null ? "unavailable" : manager.requestServerShutdown(serverId);
            if ("unavailable".equals(result)) result = serverId == null ?
                    requestLocalShutdownWithTemporarySession(null) : requestRemoteShutdownWithTemporarySession(serverId);
            if (serverId == null && !"accepted".equals(result) && !"pending".equals(result)) manualStopRequested.set(false);
            String message = "accepted".equals(result) ? "Server accepted the stop request." :
                "pending".equals(result) ? "Other clients are connected; the server will stop if they disconnect shortly." :
                "legacy_enabled".equals(result) ? "Server remains running while legacy local access is enabled; disable it or exit via the server tray." :
                "other_clients".equals(result) ? "Server remains running because other clients are connected." :
                "Server stop request unavailable or unsupported.";
            sendChatMessage("[MineLights] " + message);
        }, "MineLights-Server-Stop").start();
    }

    //? if <= 1.15.2 && !loader_forge {
    /* private static void checkForUpdate() {
        LOGGER.info("Checking for MineLights updates on Modrinth...");
        try {
            String currentVersion = resolvedModVersion;
            String gameVersion = getMinecraftVersionString();

            String urlString = String.format(
                    "https://api.modrinth.com/v2/project/%s/version?game_versions=%s&loaders=%s",
                    MODRINTH_PROJECT_ID,
                    URLEncoder.encode("[\"" + gameVersion + "\"]", "UTF-8"),
                    URLEncoder.encode("[\"" + resolvedModLoader + "\"]", "UTF-8"));

            HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() != 200) { return; }

            String json;
            try (InputStream in = conn.getInputStream()) { json = readAllBytes(in); }
            JsonArray versions = new JsonParser().parse(json).getAsJsonArray();

            if (versions.size() > 0) {
                String latestVersionNumber = versions.get(0).getAsJsonObject().get("version_number").getAsString();
                if (isNewerModVersion(latestVersionNumber, currentVersion)) {
                    MinecraftClient.getInstance().execute(() -> {
                        if (MinecraftClient.getInstance().player != null) {
                            String modrinthUrl = "https://modrinth.com/mod/" + MODRINTH_PROJECT_ID + "/versions?version=" + gameVersion + "#download";
                            Text message = new net.minecraft.text.LiteralText("[MineLights] ").formatted(Formatting.GOLD)
                                    .append(new net.minecraft.text.LiteralText("A new version is available: ").formatted(Formatting.YELLOW))
                                    .append(new net.minecraft.text.LiteralText(latestVersionNumber).formatted(Formatting.AQUA));
                            Text link = new net.minecraft.text.LiteralText("[Click here to download]")
                                    .setStyle(new Style()
                                            .setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, modrinthUrl))
                                            .setHoverEvent(new net.minecraft.text.HoverEvent(net.minecraft.text.HoverEvent.Action.SHOW_TEXT, new net.minecraft.text.LiteralText("Open Modrinth page")))
                                            .setColor(Formatting.GREEN)); // FIXED: Use .setColor() for old versions
                            MinecraftClient.getInstance().player.sendMessage(message);
                            MinecraftClient.getInstance().player.sendMessage(link);
                        }
                    });
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to check for mod updates", e);
        }
    }
    *///?} else if >= 1.16 && < 1.19 && !loader_forge {
    /* private static void checkForUpdate() {
        LOGGER.info("Checking for MineLights updates on Modrinth...");
        try {
            String currentVersion = resolvedModVersion;
            String gameVersion = getMinecraftVersionString();

            String urlString = String.format(
                    "https://api.modrinth.com/v2/project/%s/version?game_versions=%s&loaders=%s",
                    MODRINTH_PROJECT_ID,
                    URLEncoder.encode("[\"" + gameVersion + "\"]", StandardCharsets.UTF_8.toString()),
                    URLEncoder.encode("[\"" + resolvedModLoader + "\"]", StandardCharsets.UTF_8.toString()));

            HttpURLConnection conn = (HttpURLConnection) URI.create(urlString).toURL().openConnection();
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() != 200) { return; }

            String json;
            try (InputStream in = conn.getInputStream()) { json = readAllBytes(in); }
            JsonArray versions = new JsonParser().parse(json).getAsJsonArray();

            if (versions.size() > 0) {
                String latestVersionNumber = versions.get(0).getAsJsonObject().get("version_number").getAsString();
                if (isNewerModVersion(latestVersionNumber, currentVersion)) {
                    MinecraftClient.getInstance().execute(() -> {
                        if (MinecraftClient.getInstance().player != null) {
                            String modrinthUrl = "https://modrinth.com/mod/" + MODRINTH_PROJECT_ID + "/versions?version=" + gameVersion + "#download";
                            MutableText message = new net.minecraft.text.LiteralText("[MineLights] ").formatted(Formatting.GOLD)
                                    .append(new net.minecraft.text.LiteralText("A new version is available: ").formatted(Formatting.YELLOW))
                                    .append(new net.minecraft.text.LiteralText(latestVersionNumber).formatted(Formatting.AQUA));
                            MutableText link = new net.minecraft.text.LiteralText("[Click here to download]")
                                    .setStyle(Style.EMPTY
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, modrinthUrl))
                                            .withColor(Formatting.GREEN));
                            MinecraftClient.getInstance().player.sendMessage(message, false);
                            MinecraftClient.getInstance().player.sendMessage(link, false);
                        }
                    });
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to check for mod updates", e);
        }
    }
    *///?}
    //? if (loader_neoforge || loader_forge) && <1.21.6 {
    private static void checkForUpdate() {
        LOGGER.info("Checking for MineLights updates on Modrinth...");
        try {
            String currentVersion = resolvedModVersion;
            String gameVersion = getMinecraftVersionString();

            String gameVersionsJson = "[\"" + gameVersion + "\"]";
            String loadersJson = "[\"" + resolvedModLoader + "\"]";
            String urlString = String.format(
                    "https://api.modrinth.com/v2/project/%s/version?game_versions=%s&loaders=%s",
                    MODRINTH_PROJECT_ID,
                    URLEncoder.encode(gameVersionsJson, StandardCharsets.UTF_8.toString()),
                    URLEncoder.encode(loadersJson, StandardCharsets.UTF_8.toString()));

            HttpURLConnection conn = (HttpURLConnection) URI.create(urlString).toURL().openConnection();
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() != 200) { return; }

            String json;
            try (InputStream in = conn.getInputStream()) { json = readAllBytes(in); }
            JsonArray versions = new JsonParser().parse(json).getAsJsonArray();

            if (versions.size() > 0) {
                String latestVersionNumber = versions.get(0).getAsJsonObject().get("version_number").getAsString();
                if (isNewerModVersion(latestVersionNumber, currentVersion)) {
                    String modrinthUrl = "https://modrinth.com/mod/" + MODRINTH_PROJECT_ID + "/versions?version=" + gameVersion + "#download";
                    queueUpdateNotification(latestVersionNumber, modrinthUrl);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to check for mod updates", e);
        }
    }
    //?} else if >= 1.19 && < 1.21.6 {
    /* private static void checkForUpdate() {
        LOGGER.info("Checking for MineLights updates on Modrinth...");
        try {
            String currentVersion = resolvedModVersion;
            String gameVersion = getMinecraftVersionString();

            String gameVersionsJson = "[\"" + gameVersion + "\"]";
            String loadersJson = "[\"" + resolvedModLoader + "\"]";
            String urlString = String.format(
                    "https://api.modrinth.com/v2/project/%s/version?game_versions=%s&loaders=%s",
                    MODRINTH_PROJECT_ID,
                    URLEncoder.encode(gameVersionsJson, StandardCharsets.UTF_8.toString()),
                    URLEncoder.encode(loadersJson, StandardCharsets.UTF_8.toString()));

            HttpURLConnection conn = (HttpURLConnection) URI.create(urlString).toURL().openConnection();
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() != 200) { return; }

            String json;
            try (InputStream in = conn.getInputStream()) { json = readAllBytes(in); }
            JsonArray versions = new JsonParser().parse(json).getAsJsonArray();

            if (versions.size() > 0) {
                String latestVersionNumber = versions.get(0).getAsJsonObject().get("version_number").getAsString();
                if (isNewerModVersion(latestVersionNumber, currentVersion)) {
                    MinecraftClient.getInstance().execute(() -> {
                        if (MinecraftClient.getInstance().player != null) {
                            String modrinthUrl = "https://modrinth.com/mod/" + MODRINTH_PROJECT_ID + "/versions?version=" + gameVersion + "#download";
                            MutableText message = Text.literal("[MineLights] ").formatted(Formatting.GOLD)
                                    .append(Text.literal("A new version is available: ").formatted(Formatting.YELLOW))
                                    .append(Text.literal(latestVersionNumber).formatted(Formatting.AQUA));
                            MutableText link = Text.literal("[Click here to download]")
                                    .setStyle(Style.EMPTY
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, modrinthUrl))
                                            .withColor(Formatting.GREEN));
                            MinecraftClient.getInstance().player.sendMessage(message, false);
                            MinecraftClient.getInstance().player.sendMessage(link, false);
                        }
                    });
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to check for mod updates", e);
        }
    }
    *///?}
    //? if >= 1.21.6 {
    private static void checkForUpdate() {
        LOGGER.info("Starting update check thread...");

        try {
            String currentVersion = resolvedModVersion;
            String currentLoader = resolvedModLoader;
            if (currentVersion == null || currentLoader == null) {
                LOGGER.warn("Skipping MineLights update check because the client has not been initialized yet.");
                return;
            }
            LOGGER.info("Current mod version: {}", currentVersion);

            String gameVersion = getMinecraftVersionString();
            LOGGER.info("Current Minecraft version: {}", gameVersion);

            String gameVersionsJson = "[\"" + gameVersion + "\"]";
            String loadersJson = "[\"" + currentLoader + "\"]";

            String urlString = String.format(
                    "https://api.modrinth.com/v2/project/%s/version?game_versions=%s&loaders=%s",
                    MODRINTH_PROJECT_ID,
                    URLEncoder.encode(gameVersionsJson, StandardCharsets.UTF_8),
                    URLEncoder.encode(loadersJson, StandardCharsets.UTF_8)
            );
            LOGGER.info("Constructed URL: {}", urlString);

            URL url = URI.create(urlString).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            LOGGER.info("Sending request to Modrinth...");
            int responseCode = conn.getResponseCode();
            LOGGER.info("Received response code: {}", responseCode);

            if (responseCode != 200) {
                LOGGER.warn("Failed to check for updates. Modrinth API responded with code: {}", responseCode);
                return;
            }

            String json;
            try (InputStream in = conn.getInputStream()) {
                LOGGER.info("Reading response body...");
                json = readAllBytes(in);
            }
            LOGGER.info("Response body length: {}", json.length());

            JsonArray versions = new JsonParser().parse(json).getAsJsonArray();
            LOGGER.info("Parsed {} versions from Modrinth", versions.size());

            if (versions.size() > 0) {
                JsonObject latestVersion = versions.get(0).getAsJsonObject();
                String latestVersionNumber = latestVersion.get("version_number").getAsString();
                LOGGER.info("Latest version number from Modrinth: {}", latestVersionNumber);

                if (isNewerModVersion(latestVersionNumber, currentVersion)) {
                    LOGGER.info("A new version of MineLights is available: {}", latestVersionNumber);
                    String gameVersionId = getMinecraftVersionString();
                    String modrinthUrl = String.format(
                        "https://modrinth.com/mod/%s/versions?version=%s#download",
                        MODRINTH_PROJECT_ID,
                        gameVersionId
                    );
                    queueUpdateNotification(latestVersionNumber, modrinthUrl);
                } else {
                    LOGGER.info("MineLights is up to date.");
                }
            } else {
                LOGGER.info("No versions returned from Modrinth.");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to check for mod updates", e);
        }
    }
    //?}

    private static void queueUpdateNotification(String latestVersionNumber, String modrinthUrl) {
        pendingUpdateVersion.set(latestVersionNumber);
        pendingUpdateUrl.set(modrinthUrl);
        pendingUpdateNotification.set(true);
    }

    private static void flushPendingUpdateNotification() {
        if (!pendingUpdateNotification.get()) {
            return;
        }

        //? if loader_forge || loader_neoforge || >=26.1 {
        Minecraft client = MineLightsClient.getMinecraft();
        //? if loader_forge && <=1.9.4 {
        if (client.thePlayer == null) {
            return;
        }
        //?} else {
        if (client.player == null) {
            return;
        }
        //?}
        //?} else {
        /* MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        } */
        //?}

        if (!pendingUpdateNotification.compareAndSet(true, false)) {
            return;
        }

        String latestVersionNumber = pendingUpdateVersion.getAndSet(null);
        String modrinthUrl = pendingUpdateUrl.getAndSet(null);
        if (latestVersionNumber == null || modrinthUrl == null) {
            return;
        }

        //? if loader_forge || loader_neoforge || >=26.1 {
        //? if loader_forge && <=1.9.4 {
        //? if loader_forge && <=1.8.9 {
        IChatComponent message = new ChatComponentText(
                "[MineLights] New version available: " + latestVersionNumber + " - " + modrinthUrl);
        client.thePlayer.addChatMessage(message);
        //?} else {
        ITextComponent message = new TextComponentString(
                "[MineLights] New version available: " + latestVersionNumber + " - " + modrinthUrl);
        client.thePlayer.addChatMessage(message);
        //?}
        //?} else if loader_forge && <=1.13.2 {
        ITextComponent message = new TextComponentString(
                "[MineLights] New version available: " + latestVersionNumber + " - " + modrinthUrl);
        client.player.sendMessage(message);
        //?} else if loader_forge && <=1.15.2 {
        ITextComponent message = new StringTextComponent(
                "[MineLights] New version available: " + latestVersionNumber + " - " + modrinthUrl);
        client.player.sendMessage(message);
        //?} else if loader_forge && 1.16.1 {
        ITextComponent message = new StringTextComponent(
                "[MineLights] New version available: " + latestVersionNumber + " - " + modrinthUrl);
        client.player.sendMessage(message, java.util.UUID.randomUUID());
        //?} else if loader_forge && <1.17 {
        IFormattableTextComponent message = literalComponent("[MineLights] ").withStyle(TextFormatting.GOLD)
                .append(literalComponent("New version available: ").withStyle(TextFormatting.YELLOW))
                .append(literalComponent(latestVersionNumber).withStyle(TextFormatting.AQUA));
        message.setStyle(message.getStyle().withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, modrinthUrl)));
        client.player.sendMessage(message, java.util.UUID.randomUUID());
        //?} else if >=26.1 {
        MutableComponent message = literalComponent("[MineLights] ").withStyle(ChatFormatting.GOLD)
                .append(literalComponent("New version available: ").withStyle(ChatFormatting.YELLOW))
                .append(literalComponent(latestVersionNumber).withStyle(ChatFormatting.AQUA))
                .withStyle(style -> style.withClickEvent(new ClickEvent.OpenUrl(URI.create(modrinthUrl))));
        client.player.sendSystemMessage(message);
        //?} else if >=1.21.5 {
        MutableComponent message = literalComponent("[MineLights] ").withStyle(ChatFormatting.GOLD)
                .append(literalComponent("New version available: ").withStyle(ChatFormatting.YELLOW))
                .append(literalComponent(latestVersionNumber).withStyle(ChatFormatting.AQUA))
                .withStyle(style -> style.withClickEvent(new ClickEvent.OpenUrl(URI.create(modrinthUrl))));
        client.player.displayClientMessage(message, false);
        //?} else {
        MutableComponent message = literalComponent("[MineLights] ").withStyle(ChatFormatting.GOLD)
                .append(literalComponent("New version available: ").withStyle(ChatFormatting.YELLOW))
                .append(literalComponent(latestVersionNumber).withStyle(ChatFormatting.AQUA));
        message.setStyle(message.getStyle().withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, modrinthUrl)));
        client.player.displayClientMessage(message, false);
        //?}
        //?} else {
        /* MutableText message = Text.literal("[MineLights] ").formatted(Formatting.GOLD)
                .append(Text.literal("New version available: ").formatted(Formatting.YELLOW))
                .append(Text.literal(latestVersionNumber).formatted(Formatting.AQUA));
        message.setStyle(message.getStyle().withClickEvent(new ClickEvent.OpenUrl(URI.create(modrinthUrl))));
        client.player.sendMessage(message, false); */
        //?}

        LOGGER.info("Delivered queued update notification for version {}.", latestVersionNumber);
    }

    private static String readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[4096];
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return new String(buffer.toByteArray());
    }

    public static void checkForServerUpdate() {
        if (!IS_WINDOWS) {
            return;
        }

        if (automaticServerInstallAllowed()) {
            scheduleAutomaticUpdate(true);
            return;
        }

        Path serverExePath = getServerExePath();
        if (!Files.isRegularFile(serverExePath)) {
            LOGGER.info("MineLights Server is not installed; skipping the update check.");
            return;
        }

        try {
            ServerRelease release = getLatestServerRelease();
            if (release == null) {
                queueServerNotification(Collections.singletonList(
                        "[MineLights] Could not check for server updates. See " + SERVER_RELEASES_URL));
                return;
            }

            if (!megabytesme.minelights.network.ServerReleaseIntegrity.isValidDigest(release.expectedHash)) {
                LOGGER.warn("MineLights Server release does not publish a SHA-256 digest; skipping update notice.");
                queueServerNotification(Collections.singletonList(
                        "[MineLights] Could not verify the latest server release. See " + SERVER_RELEASES_URL));
                return;
            }

            String localHash = sha256(serverExePath);
            if (localHash.equalsIgnoreCase(release.expectedHash)) {
                LOGGER.info("MineLights Server is up to date ({}).", release.version);
                queueServerNotification(Collections.singletonList(
                        "[MineLights] MineLights Server is up to date (" + release.version + ")."));
                return;
            }

            List<String> messages = new ArrayList<>();
            messages.add("[MineLights] MineLights Server " + release.version + " is available. Update it manually:");
            messages.add("[MineLights] " + SERVER_RELEASES_URL);
            messages.add("[MineLights] Close Minecraft, then replace " + getServerInstallLocation() + ".");
            appendServerChangelog(messages, release.changelog);
            queueServerNotification(messages);
            LOGGER.info("MineLights Server {} is available; queued a manual update notice.", release.version);
        } catch (Exception e) {
            LOGGER.error("Failed to check for MineLights Server updates", e);
            queueServerNotification(Collections.singletonList(
                    "[MineLights] Could not check for server updates. See " + SERVER_RELEASES_URL));
        }
    }

    private static void checkForServerUpdateAutomatically() {
        boolean downloadAttempted = false;
        try {
            if (!automaticServerInstallAllowed()) return;
            ServerRelease release = getLatestServerRelease();
            if (release == null) {
                return;
            }

            megabytesme.minelights.network.ServerReleaseIntegrity.requireValidDigest(release.expectedHash);

            Path serverExePath = getServerExePath();
            boolean needsUpdate = !Files.isRegularFile(serverExePath);
            if (!needsUpdate) {
                needsUpdate = !sha256(serverExePath).equalsIgnoreCase(release.expectedHash);
            }
            if (!needsUpdate) {
                LOGGER.info("MineLights Server is up to date ({}).", release.version);
                return;
            }
            if (!serverDownloadInProgress.compareAndSet(false, true)) {
                return;
            }

            try {
                LOGGER.info("Downloading MineLights Server {}.", release.version);
                automaticDownloadProgressNanos.set(System.nanoTime());
                automaticDownloadStarted.set(false);
                downloadAttempted = true;
                downloadAndInstallServer(release, serverExePath, true);
            } finally {
                automaticDownloadProgressNanos.set(0);
                automaticDownloadStarted.set(false);
                serverDownloadInProgress.set(false);
                serverDownloadNotificationState.reset();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to check for MineLights Server updates", e);
            String failureKey = serverDownloadNotificationState.failed(
                    CONFIG != null && CONFIG.notifyServerDownloadProgress, downloadAttempted);
            if (failureKey != null) {
                queueLocalizedServerNotification(failureKey);
            }
        }
    }

    private static ServerRelease getLatestServerRelease() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(GITHUB_API_URL).toURL().openConnection();
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "MineLights-Mod/" + getModVersion());
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);

        try {
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("GitHub API responded with HTTP " + responseCode);
            }

            JsonObject release = new JsonParser().parse(readAllBytes(connection.getInputStream())).getAsJsonObject();
            JsonArray assets = release.getAsJsonArray("assets");
            if (assets == null || assets.size() == 0) {
                LOGGER.warn("MineLights Server release metadata has no assets.");
                throw new IOException("MineLights Server release metadata has no assets.");
            }

            JsonObject selectedAsset = null;
            for (int i = 0; i < assets.size(); i++) {
                JsonObject asset = assets.get(i).getAsJsonObject();
                String name = asset.has("name") ? asset.get("name").getAsString() : "";
                if ("MineLights.exe".equalsIgnoreCase(name)) {
                    selectedAsset = asset;
                    break;
                }
                if (selectedAsset == null && name.toLowerCase().endsWith(".exe")) {
                    selectedAsset = asset;
                }
            }
            if (selectedAsset == null || !selectedAsset.has("browser_download_url")) {
                LOGGER.warn("MineLights Server release metadata has no executable asset.");
                throw new IOException("MineLights Server release metadata has no executable asset.");
            }

            String version = release.has("tag_name") ? release.get("tag_name").getAsString() : "the latest release";
            String changelog = release.has("body") && !release.get("body").isJsonNull()
                    ? release.get("body").getAsString() : "";
            String hash = selectedAsset.has("digest") && !selectedAsset.get("digest").isJsonNull()
                    ? selectedAsset.get("digest").getAsString().replace("sha256:", "") : "";
            String downloadUrl = selectedAsset.get("browser_download_url").getAsString();
            return new ServerRelease(version, changelog, downloadUrl, hash);
        } finally {
            connection.disconnect();
        }
    }

    private static void downloadAndInstallServer(ServerRelease release, Path serverExePath,
            boolean restartAfterInstall) throws IOException {
        megabytesme.minelights.network.ServerReleaseIntegrity.requireValidDigest(release.expectedHash);
        if (!automaticServerInstallAllowed()) {
            throw new IOException("Automatic server installation is disabled in MineLights settings.");
        }
        if (isServerRunning()) {
            throw new IOException("MineLights Server is running as a shared service and was left untouched. Stop it from its tray UI before updating.");
        }
        Path temporaryPath = serverExePath.resolveSibling("MineLights.exe.new");
        try {
            downloadServerRelease(release, temporaryPath);

            if (!automaticServerInstallAllowed()) {
                throw new IOException("Automatic server installation was disabled during the download.");
            }

            if (isServerRunning()) {
                throw new IOException("MineLights Server started during the download and was left untouched. Stop it from its tray UI before updating.");
            }

            Files.createDirectories(serverExePath.getParent());
            if (!automaticServerInstallAllowed()) {
                throw new IOException("Automatic server installation was disabled before replacement.");
            }
            try {
                Files.move(temporaryPath, serverExePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                if (!automaticServerInstallAllowed()) {
                    throw new IOException("Automatic server installation was disabled before replacement.");
                }
                Files.move(temporaryPath, serverExePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            LOGGER.info("MineLights Server {} installed successfully.", release.version);
            lightingInitialized.set(false);
            if (restartAfterInstall) {
                String finishedKey = serverDownloadNotificationState.finished(
                        CONFIG.notifyServerDownloadProgress, startServerProcessAndWaitForV2());
                if (finishedKey != null) queueLocalizedServerNotification(finishedKey);
            }
        } finally {
            Files.deleteIfExists(temporaryPath);
        }
    }

    private static void downloadServerRelease(ServerRelease release, Path destination) throws IOException {
        megabytesme.minelights.network.ServerReleaseIntegrity.requireValidDigest(release.expectedHash);
        if (!automaticServerInstallAllowed()) {
            throw new IOException("Automatic server installation is disabled in MineLights settings.");
        }
        Files.createDirectories(destination.getParent());
        HttpURLConnection connection = (HttpURLConnection) URI.create(release.downloadUrl).toURL().openConnection();
        activeAutomaticDownload.set(connection);
        connection.setRequestProperty("User-Agent", "MineLights-Mod/" + getModVersion());
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        try {
            if (!automaticServerInstallAllowed()) throw new IOException("Automatic server installation was disabled before download.");
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("MineLights Server download responded with HTTP " + responseCode);
            }
            try (InputStream input = connection.getInputStream();
                    java.io.OutputStream output = Files.newOutputStream(destination)) {
                automaticDownloadProgressNanos.set(System.nanoTime());
                String startedKey = serverDownloadNotificationState.beginTransfer(
                        CONFIG != null && CONFIG.notifyServerDownloadProgress, clientInWorld);
                if (startedKey != null) queueLocalizedServerNotification(startedKey);
                automaticDownloadStarted.set(true);
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    automaticDownloadProgressNanos.set(System.nanoTime());
                    if (!automaticServerInstallAllowed()) {
                        throw new IOException("Automatic server installation was disabled during the download.");
                    }
                    output.write(buffer, 0, bytesRead);
                }
            }
            automaticDownloadStarted.set(false);
            megabytesme.minelights.network.ServerReleaseIntegrity.verify(destination, release.expectedHash);
        } catch (Exception e) {
            Files.deleteIfExists(destination);
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException("Failed to download MineLights Server", e);
        } finally {
            activeAutomaticDownload.compareAndSet(connection, null);
            connection.disconnect();
        }
    }

    private static void appendServerChangelog(List<String> messages, String changelog) {
        if (changelog == null || changelog.trim().isEmpty()) {
            messages.add("[MineLights] No release notes were provided.");
            return;
        }

        int added = 0;
        for (String line : changelog.split("\\R")) {
            String clean = line.replaceAll("[\\p{Cntrl}]", "").trim();
            clean = clean.replaceFirst("^(#{1,6}\\s*|[-*+]\\s*)", "");
            if (clean.isEmpty()) {
                continue;
            }
            if (clean.length() > 150) {
                clean = clean.substring(0, 147) + "...";
            }
            messages.add("[MineLights] Change: " + clean);
            if (++added == 5) {
                messages.add("[MineLights] Full release notes: " + SERVER_RELEASES_URL);
                return;
            }
        }
        if (added == 0) {
            messages.add("[MineLights] No release notes were provided.");
        }
    }

    private static void queueServerNotification(List<String> messages) {
        List<PendingServerMessage> queued = new ArrayList<>(messages.size());
        for (String message : messages) queued.add(PendingServerMessage.literal(message));
        pendingServerMessages.add(queued);
    }

    private static void queueLocalizedServerNotification(String key, Object... arguments) {
        pendingServerMessages.add(Collections.singletonList(PendingServerMessage.translated(key, arguments)));
    }

    //? if loader_forge || loader_neoforge || >=26.1 {
    private static void sendLocalizedServerMessage(Minecraft client, PendingServerMessage message) {
        //? if loader_forge && <=1.8.9 {
        client.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentTranslation(
                message.translationKey, message.arguments));
        //?} else if loader_forge && <=1.9.4 {
        client.thePlayer.addChatMessage(new net.minecraft.util.text.TextComponentTranslation(
                message.translationKey, message.arguments));
        //?} else if loader_forge && <=1.13.2 {
        client.player.sendMessage(new net.minecraft.util.text.TextComponentTranslation(
                message.translationKey, message.arguments));
        //?} else if loader_forge && <1.17 {
        net.minecraft.util.text.TranslationTextComponent component = new net.minecraft.util.text.TranslationTextComponent(
                message.translationKey, message.arguments);
        //? if loader_forge && >=1.16.1 {
        client.player.sendMessage(component, java.util.UUID.randomUUID());
        //?} else {
        client.player.sendMessage(component);
        //?}
        //?} else if loader_forge && <1.19 {
        client.player.displayClientMessage(new net.minecraft.network.chat.TranslatableComponent(
                message.translationKey, message.arguments), false);
        //?} else if loader_forge || loader_neoforge || >=26.1 {
        //? if >=26.1 {
        client.player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                message.translationKey, message.arguments));
        //?} else {
        client.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                message.translationKey, message.arguments), false);
        //?}
        //?}
    }
    //?} else {
    /* private static void sendLocalizedServerMessage(MinecraftClient client, PendingServerMessage message) {
        //? if >=1.19 {
        client.player.sendMessage(Text.translatable(message.translationKey, message.arguments), false);
        //?} else {
        client.player.sendMessage(new net.minecraft.text.TranslatableText(message.translationKey, message.arguments), false);
        //?}
    } */
    //?}

    public static void sendChatMessage(String message) {
        if (message != null && !message.trim().isEmpty()) {
            queueServerNotification(Collections.singletonList(message));
        }
    }

    public static void sendChatMessages(List<String> messages) {
        if (messages != null && !messages.isEmpty()) {
            queueServerNotification(messages);
        }
    }

    private static void flushPendingServerNotification() {
        //? if loader_forge || loader_neoforge || >=26.1 {
        Minecraft client = MineLightsClient.getMinecraft();
        //? if loader_forge && <=1.9.4 {
        if (client.thePlayer == null) {
            hasCheckedForMissingServer.set(false);
            return;
        }
        //?} else {
        if (client.player == null) {
            hasCheckedForMissingServer.set(false);
            return;
        }
        //?}
        //?} else {
        /* MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            hasCheckedForMissingServer.set(false);
            return;
        } */
        //?}

        if (IS_WINDOWS && CONFIG.notifyMissingServer
                && CONFIG.manualServerManagement
                && hasCheckedForMissingServer.compareAndSet(false, true)
                && !Files.isRegularFile(getServerExePath()) && !isServerRunning()) {
            queueServerNotification(java.util.Arrays.asList(
                    "[MineLights] MineLights Server is not installed. Device lighting needs the server.",
                    "[MineLights] Download it from " + SERVER_RELEASES_URL,
                    "[MineLights] Put MineLights.exe at " + getServerInstallLocation() + " in your game directory."));
        }

        if (CONFIG.notifyServerDownloadProgress && serverDownloadInProgress.get()
                && automaticDownloadStarted.get()) {
            String progressKey = serverDownloadNotificationState.inProgress(
                    CONFIG.notifyServerDownloadProgress, automaticDownloadStarted.get(), clientInWorld);
            if (progressKey != null) queueLocalizedServerNotification(progressKey);
        }

        List<PendingServerMessage> messages;
        while ((messages = pendingServerMessages.poll()) != null) {
            for (PendingServerMessage message : messages) {
                if (message.translationKey != null) {
                    if (!CONFIG.notifyServerDownloadProgress
                            && (message.translationKey.startsWith("message.minelights.server.download.")
                            || "message.minelights.server.update_check_failed".equals(message.translationKey))) {
                        continue;
                    }
                    sendLocalizedServerMessage(client, message);
                    continue;
                }
                String text = message.text;
                //? if loader_forge || loader_neoforge || >=26.1 {
                //? if loader_forge && <=1.8.9 {
                client.thePlayer.addChatMessage(new ChatComponentText(text));
                //?} else if loader_forge && <=1.9.4 {
                client.thePlayer.addChatMessage(new TextComponentString(text));
                //?} else if loader_forge && <=1.13.2 {
                client.player.sendMessage(new TextComponentString(text));
                //?} else if loader_forge && <=1.15.2 {
                client.player.sendMessage(new StringTextComponent(text));
                //?} else if loader_forge && 1.16.1 {
                client.player.sendMessage(new StringTextComponent(text), java.util.UUID.randomUUID());
                //?} else if loader_forge && <1.17 {
                client.player.sendMessage(literalComponent(text), java.util.UUID.randomUUID());
                //?} else if >=26.1 {
                client.player.sendSystemMessage(literalComponent(text));
                //?} else if >=1.21.5 {
                client.player.displayClientMessage(literalComponent(text), false);
                //?} else {
                client.player.displayClientMessage(literalComponent(text), false);
                //?}
                //?} else {
                /* client.player.sendMessage(Text.literal(text), false); */
                //?}
            }
        }
    }

    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream fis = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        return bytesToHex(digest.digest());
    }

    private void initializeServerConnection() {
        updateServerMonitorSetting();
    }

    private static synchronized void reconcileLightingState() {
        if (CONFIG == null || !CONFIG.enableMod || shutdownStarted.get()) {
            if (lightingManager != null) lightingManager.requestStop();
            if (lightingManagerThread != null) lightingManagerThread.interrupt();
            lightingInitialized.set(false);
            isProxyConnected = false;
            return;
        }
        if (!lightingInitialized.get() && (lightingManagerThread == null || !lightingManagerThread.isAlive())) {
            triggerLightingInitialization();
        }
    }

    private static boolean automaticServerStartAllowed() {
        return CONFIG != null && CONFIG.mayAutomaticallyStartServer(shutdownStarted.get());
    }

    private static boolean automaticServerInstallAllowed() {
        return CONFIG != null && CONFIG.mayAutomaticallyInstallServer(shutdownStarted.get(), manualStopRequested.get());
    }

    private static void resetAutomaticUpdateWhenDisallowed() {
        if (!automaticServerInstallAllowed()) synchronized (automaticUpdateLock) {
            if (!automaticServerInstallAllowed()) {
                automaticUpdateDecision = null;
                cancelAutomaticDownload();
            }
        }
    }

    private static void cancelAutomaticDownload() {
        final HttpURLConnection connection = activeAutomaticDownload.getAndSet(null);
        if (connection == null) return;
        Thread cancel = new Thread(connection::disconnect, "MineLights-Download-Cancel");
        cancel.setDaemon(true);
        cancel.start();
    }

    private static CountDownLatch scheduleAutomaticUpdate(boolean force) {
        synchronized (automaticUpdateLock) {
            if (!automaticServerInstallAllowed()) { automaticUpdateDecision = null; return null; }
            if (automaticUpdateDecision != null && (!force || automaticUpdateDecision.getCount() != 0)) return automaticUpdateDecision;
            final CountDownLatch decision = new CountDownLatch(1);
            automaticUpdateDecision = decision;
            automaticUpdateStartedNanos = System.nanoTime();
            Thread worker = new Thread(() -> {
                try {
                    synchronized (automaticUpdateExecution) {
                        if (automaticUpdateDecision == decision && automaticServerInstallAllowed()) checkForServerUpdateAutomatically();
                    }
                } finally { decision.countDown(); }
            }, "MineLights-Server-Auto-Updater");
            worker.setDaemon(true);
            worker.start();
            return decision;
        }
    }

    private static void awaitInitialAutomaticUpdate() throws InterruptedException {
        CountDownLatch decision = scheduleAutomaticUpdate(false);
        if (decision == null) return;
        long metadataDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (automaticServerInstallAllowed() && decision.getCount() != 0) {
            if (decision.await(250, TimeUnit.MILLISECONDS)) return;
            long now = System.nanoTime();
            long progress = automaticDownloadProgressNanos.get();
            boolean activeTransfer = serverDownloadInProgress.get() && progress != 0
                    && now - progress < TimeUnit.SECONDS.toNanos(35);
            if (now >= metadataDeadline && !activeTransfer) return;
        }
    }

    /** Applies changes to the user-controlled automatic server start setting immediately. */
    public static synchronized void updateServerMonitorSetting() {
        manualStopRequested.set(false);
        if (!IS_WINDOWS || CONFIG == null) {
            return;
        }

        if (serverMonitorThread == null || !serverMonitorThread.isAlive()
                || serverMonitorThread.isInterrupted()) {
            serverMonitorThread = new Thread(MineLightsClient::serverMonitorLoop, "MineLights-Server-Monitor");
            serverMonitorThread.setDaemon(true);
            serverMonitorThread.start();
        }
    }

    private static void serverMonitorLoop() {
        LOGGER.info("Starting MineLights Server monitor.");
        boolean hasConnected = false;
        long lastLaunchAttemptTime = 0;
        long nextV2InitializationRetry = 0;

        while (!Thread.currentThread().isInterrupted()) {
            try {
                warnIfOldServerOnly();
                if (!automaticServerStartAllowed()) {
                    resetAutomaticUpdateWhenDisallowed();
                    Thread.sleep(1000);
                    continue;
                }
                if (isServerRunning()) {
                    if (!hasConnected) {
                        triggerLightingInitialization();
                        hasConnected = true;
                        nextV2InitializationRetry = System.currentTimeMillis() + 5000;
                    } else if (isV2ServerRunning() && !isProxyConnected
                            && System.currentTimeMillis() >= nextV2InitializationRetry) {
                        LOGGER.info("MineLights Server V2 is ready; retrying lighting initialization.");
                        lightingInitialized.set(false);
                        triggerLightingInitialization();
                        nextV2InitializationRetry = System.currentTimeMillis() + 5000;
                    }

                    Thread.sleep(isV2ServerRunning() && !isProxyConnected ? 1000 : 10000);
                    continue;
                }

                isProxyConnected = false;
                lightingInitialized.set(false);
                hasConnected = false;
                nextV2InitializationRetry = 0;

                if (manualStopRequested.get()) {
                    Thread.sleep(1000);
                    continue;
                }
                Path serverExePath = getServerExePath();

                awaitInitialAutomaticUpdate();
                resetAutomaticUpdateWhenDisallowed();
                if (!automaticServerStartAllowed() || manualStopRequested.get()) continue;
                if (isServerRunning()) continue;

                if (!Files.isRegularFile(serverExePath)) {
                    synchronized (automaticUpdateLock) {
                        if (automaticUpdateDecision != null && automaticUpdateDecision.getCount() == 0
                                && System.nanoTime() - automaticUpdateStartedNanos >= TimeUnit.SECONDS.toNanos(30)) {
                            automaticUpdateDecision = null;
                        }
                    }
                    Thread.sleep(1000);
                    continue;
                }

                if (serverProcess == null || !serverProcess.isAlive()) {
                    if (System.currentTimeMillis() - lastLaunchAttemptTime > 10000) {
                        LOGGER.info("Server process is not active. Attempting to launch MineLights.exe...");
                        startServerProcess();
                        lastLaunchAttemptTime = System.currentTimeMillis();
                    }
                } else {
                    LOGGER.info("Waiting for running server process to become responsive...");
                }

                Thread.sleep(5000);

            } catch (InterruptedException e) {
                LOGGER.info("Server monitor shutting down.");
                break;
            }
        }
    }

    private static void warnIfOldServerOnly() {
        if (!IS_WINDOWS || CONFIG == null || !CONFIG.enableMod || legacyServerNoticeSent.get()) return;
        if (isV2ServerRunning() || !megabytesme.minelights.network.ServerProcessDiscovery.isRunning()) {
            legacyOnlySince = 0;
        } else if (legacyOnlySince == 0) {
            legacyOnlySince = System.currentTimeMillis();
        } else if (System.currentTimeMillis() - legacyOnlySince >= 30000
                && legacyServerNoticeSent.compareAndSet(false, true)) {
            sendChatMessage("[MineLights] A MineLights Server process is running without a V2 endpoint. If it is an older server, exit it from its tray and update to MineLights Server 2.4.0 or newer.");
        }
    }

    public static boolean isServerRunning() {
        return isV2ServerRunning() || (IS_WINDOWS
                && megabytesme.minelights.network.ServerProcessDiscovery.isRunning());
    }

    private static boolean isV2ServerRunning() {
        try {
            V2Endpoint endpoint = V2Endpoint.readLocal();
            if (endpoint == null) {
                return false;
            }
            try (Socket socket = new Socket()) {
                socket.connect(new java.net.InetSocketAddress(endpoint.host, endpoint.port), 1000);
                return true;
            }
        } catch (IOException e) {
            return false;
        }
    }

    private static synchronized boolean startServerProcess() {
        if (!automaticServerStartAllowed() || manualStopRequested.get()) return false;
        Path serverExePath = getServerExePath();

        if (!Files.exists(serverExePath)) {
            return false;
        }

        try {
            LOGGER.info("Launching server from: {}", serverExePath.toAbsolutePath());
            ProcessBuilder pb = new ProcessBuilder(serverExePath.toAbsolutePath().toString(), "--client-started");
            String launchId = java.util.UUID.randomUUID().toString();
            pb.environment().put("MINELIGHTS_LAUNCH_ID", launchId);
            pb.directory(serverExePath.getParent().toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            serverProcess = process;
            serverLaunchId = launchId;
            startServerOutputDrain(process);
            return process.isAlive();
        } catch (IOException e) {
            LOGGER.warn("Failed to start MineLights.exe process: {}", e.getMessage());
            serverProcess = null;
            return false;
        }
    }

    /** Confirms the V2 listener is reachable before reporting a successful server start. */
    private static boolean startServerProcessAndWaitForV2() {
        if (Thread.currentThread().isInterrupted() || !startServerProcess()) return false;
        final Process process;
        synchronized (MineLightsClient.class) {
            process = serverProcess;
        }
        if (process == null) return false;

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            if (isServerReadyForDownloadNotice(process)) return true;
            if (!isServerStartupAllowed(process)) return false;
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return isServerReadyForDownloadNotice(process);
    }

    private static boolean isServerReadyForDownloadNotice(Process process) {
        if (!isServerStartupAllowed(process) || !isV2ServerRunning()) return false;
        return isServerStartupAllowed(process);
    }

    private static boolean isServerStartupAllowed(Process process) {
        return !Thread.currentThread().isInterrupted() && process.isAlive()
                && automaticServerStartAllowed() && !manualStopRequested.get();
    }

    private static void startServerOutputDrain(Process process) {
        Thread outputDrainThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.info("[MineLights.exe] {}", line);
                }
            } catch (IOException e) {
                if (process.isAlive()) {
                    LOGGER.error("Error reading MineLights.exe output", e);
                }
            }
        }, "MineLights-Server-Output-Drain");
        outputDrainThread.setDaemon(true);
        outputDrainThread.start();
    }

    public static synchronized void triggerLightingInitialization() {
        if (CONFIG == null || !CONFIG.enableMod || shutdownStarted.get()) return;
        if (lightingInitialized.compareAndSet(false, true)) {
            LOGGER.info("Primary initialization trigger. Starting lighting systems...");
            refreshLightingManager();
        } else {
            LOGGER.info("Initialization already in progress or complete. Skipping redundant start.");
        }
    }

    public static synchronized void refreshLightingManager() {
        if (CONFIG == null || !CONFIG.enableMod || shutdownStarted.get()) {
            reconcileLightingState();
            return;
        }
        LOGGER.info("Refreshing lighting manager...");

        if (lightingManagerThread != null && lightingManagerThread.isAlive()) {
            if (lightingManager != null) lightingManager.requestRefreshStop();
            lightingManagerThread.interrupt();
            try {
                lightingManagerThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (lightingManagerThread.isAlive()) {
                lightingInitialized.set(false);
                return;
            }
        }

        discoveredDevices.clear();
        discoveredDeviceNames.clear();
        discoveredLegacyDeviceKeys.clear();
        isProxyConnected = false;
        lightingManager = new LightingManager();
        lightingManagerThread = new Thread(lightingManager, "MineLights-MainLoop");
        lightingManagerThread.setDaemon(true);
        lightingManagerThread.start();

        lightingInitialized.set(true);
    }

    public static void saveConfig() {
        CONFIG_MANAGER.save(CONFIG);
    }

    private static Path getServerExePath() {
        return getGameDir().resolve("mods").resolve("MineLights").resolve("MineLights.exe");
    }

    private static String getServerInstallLocation() {
        try {
            return getGameDir().relativize(getServerExePath()).toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            return getServerExePath().toAbsolutePath().toString();
        }
    }

    private static Path getGameDir() {
        Path parent = configDir.getParent();
        return parent != null ? parent : configDir.toAbsolutePath();
    }

    public static Path getConfigDir() {
        return configDir;
    }
}
