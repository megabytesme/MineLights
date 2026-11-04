package megabytesme.minelights;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import megabytesme.minelights.config.MineLightsConfig;
import megabytesme.minelights.config.SimpleJsonConfig;
import megabytesme.minelights.network.CommandClient;
import megabytesme.minelights.network.DiscoveryListener;
import megabytesme.minelights.network.UDPClient;
import megabytesme.minelights.runtime.LightingManager;
import net.minecraft.SharedConstants;
//? if >=26.1 {
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MineLightsClient implements ClientModInitializer {
    public static final Logger LOGGER = LogManager.getLogger("MineLights");
    public static final String MOD_ID = "minelights";
    public static final String MOD_VERSION = "2.3.8.5";
    private static Path configDir = Paths.get(".");
    private static String resolvedModVersion = MOD_VERSION;
    private static String resolvedModLoader = "fabric";
    public static MineLightsConfig CONFIG;
    private static SimpleJsonConfig CONFIG_MANAGER;
    private static LightingManager lightingManager;
    private static Thread lightingManagerThread;
    private static Thread discoveryThread;
    public static Thread serverMonitorThread;
    public static volatile boolean isManualRestart = false;

    public static final List<String> discoveredDevices = Collections.synchronizedList(new ArrayList<>());
    public static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    public static volatile boolean isProxyConnected = false;

    public static String getModVersion() {
        return resolvedModVersion != null ? resolvedModVersion : MOD_VERSION;
    }

    //? if >=1.16 && <1.21.6 {
    private static final Pattern MINECRAFT_VERSION_PATTERN =
            Pattern.compile("(?:^|\\D)(\\d+\\.\\d+(?:\\.\\d+)?(?:-pre\\d+)?)(?:$|\\D)");

    private static String getMinecraftVersionString() {
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
                        Matcher matcher = MINECRAFT_VERSION_PATTERN.matcher((String) value);
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
    }
    //?}

    //? if >=1.16 && <1.19 {
    private static String getMinecraftVersionName() {
        try {
            return SharedConstants.getGameVersion().getName();
        } catch (NoSuchMethodError missingVersionAccessor) {
            return getMinecraftVersionString();
        }
    }
    //?}

    //? if >=1.19 && <1.21.6 {
    private static String getMinecraftVersionId() {
        try {
            return SharedConstants.getGameVersion().getId();
        } catch (NoSuchMethodError missingVersionAccessor) {
            return getMinecraftVersionString();
        }
    }
    //?}

    public static CountDownLatch proxyDiscoveredLatch = new CountDownLatch(1);

    private static final AtomicBoolean hasPerformedServerCheck = new AtomicBoolean(false);
    private static final String GITHUB_API_URL = "https://api.github.com/repos/megabytesme/MineLights-Server/releases/latest";
    private static final String SERVER_RELEASES_URL = "https://github.com/megabytesme/MineLights-Server/releases";
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static final AtomicBoolean shutdownHookRegistered = new AtomicBoolean(false);

    private static final AtomicBoolean lightingInitialized = new AtomicBoolean(false);

    private static final String MODRINTH_PROJECT_ID = "minelights"; 
    private static final AtomicBoolean hasCheckedForUpdate = new AtomicBoolean(false);
    private static final AtomicReference<String> pendingUpdateVersion = new AtomicReference<>(null);
    private static final AtomicReference<String> pendingUpdateUrl = new AtomicReference<>(null);
    private static final AtomicBoolean pendingUpdateNotification = new AtomicBoolean(false);
    private static final AtomicBoolean hasCheckedForMissingServer = new AtomicBoolean(false);
    private static final ConcurrentLinkedQueue<List<String>> pendingServerMessages = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean serverDownloadInProgress = new AtomicBoolean(false);

    private static Process serverProcess = null;

    //? if >=26.1 {
    public static Minecraft getMinecraft() {
        return Minecraft.getInstance();
    }
    //?}

    @Override
    public void onInitializeClient() {
        String version = FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse(MOD_VERSION);

        init(FabricLoader.getInstance().getConfigDir(), version, "fabric");
        registerClientTickCallback();
    }

    private void registerClientTickCallback() {
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            Class<?> callbackType;
            Object event;
            try {
                Class<?> events = Class.forName(
                        "net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents", true, classLoader);
                callbackType = Class.forName(
                        "net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents$EndTick", true, classLoader);
                event = events.getField("END_CLIENT_TICK").get(null);
            } catch (ClassNotFoundException missingModernCallback) {
                callbackType = Class.forName(
                        "net.fabricmc.fabric.api.event.client.ClientTickCallback", true, classLoader);
                event = callbackType.getField("EVENT").get(null);
            }

            Method eventCallback = null;
            for (Method method : callbackType.getMethods()) {
                if (method.getParameterTypes().length == 1) {
                    eventCallback = method;
                    break;
                }
            }
            if (eventCallback == null) {
                throw new NoSuchMethodException("Client tick callback has no one-argument method");
            }
            Method clientTick = getClass().getMethod("onClientTick", eventCallback.getParameterTypes()[0]);
            Object listener = Proxy.newProxyInstance(callbackType.getClassLoader(), new Class<?>[]{callbackType},
                    (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            if ("equals".equals(method.getName())) return proxy == args[0];
                            if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                            if ("toString".equals(method.getName())) return "MineLightsClientTickListener";
                        }
                        if (args != null && args.length == 1) {
                            try {
                                clientTick.invoke(this, args[0]);
                            } catch (InvocationTargetException exception) {
                                throw exception.getCause();
                            }
                        }
                        return null;
                    });
            Class<?> eventType = Class.forName("net.fabricmc.fabric.api.event.Event", true, classLoader);
            eventType.getMethod("register", Object.class).invoke(event, listener);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to register the Fabric client tick callback", exception);
        }
    }

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

    public void init(Path configDir, String modVersion, String modLoader) {
        if (!initialized.compareAndSet(false, true)) {
            return;
        }

        MineLightsClient.configDir = configDir;
        MineLightsClient.resolvedModVersion = modVersion;
        MineLightsClient.resolvedModLoader = modLoader;

        if (shutdownHookRegistered.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "MineLights-Fabric-Shutdown"));
        }

        CONFIG_MANAGER = new SimpleJsonConfig("minelights");
        CONFIG = CONFIG_MANAGER.load(MineLightsConfig.class, new MineLightsConfig());

        if (IS_WINDOWS) {
            initializeServerConnection();
        }

        new Thread(() -> {
            try {
                if (proxyDiscoveredLatch.await(3, TimeUnit.SECONDS)) {
                    LOGGER.info("MineLights Server discovered via broadcast! Initializing connection.");
                } else if (!IS_WINDOWS) {
                    LOGGER.warn("MineLights Server not discovered via broadcast on non-Windows OS.");
                }
                triggerLightingInitialization();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "MineLights-Initializer-Waiter").start();
    }

    //? if >=26.1 {
    public void onClientTick(Minecraft client) {
    //?} else {
    /* public void onClientTick(MinecraftClient client) {
    *///?}
        if (IS_WINDOWS && (!CONFIG.manualServerManagement || CONFIG.checkServerUpdates)
                && hasPerformedServerCheck.compareAndSet(false, true)) {
            new Thread(MineLightsClient::checkForServerUpdate, "MineLights-Update-Check").start();
        }

        if (hasCheckedForUpdate.compareAndSet(false, true)) {
            new Thread(MineLightsClient::checkForUpdate, "MineLights-Modrinth-Update-Check").start();
        }

        flushPendingUpdateNotification();
        flushPendingServerNotification();
    }

    private static void queueUpdateNotification(String version, String url) {
        pendingUpdateVersion.set(version);
        pendingUpdateUrl.set(url);
        pendingUpdateNotification.set(true);
    }

    private static void flushPendingUpdateNotification() {
        if (!pendingUpdateNotification.get()) {
            return;
        }

        //? if >=26.1 {
        Minecraft client = Minecraft.getInstance();
        //?} else {
        /* MinecraftClient client = MinecraftClient.getInstance(); */
        //?}
        if (client.player == null || !pendingUpdateNotification.compareAndSet(true, false)) {
            return;
        }

        String version = pendingUpdateVersion.getAndSet(null);
        String url = pendingUpdateUrl.getAndSet(null);
        if (version == null || url == null) {
            return;
        }

        //? if <=1.15.2 {
        Text message = new net.minecraft.text.LiteralText("[MineLights] ").formatted(Formatting.GOLD)
                .append(new net.minecraft.text.LiteralText("A new version is available: ").formatted(Formatting.YELLOW))
                .append(new net.minecraft.text.LiteralText(version).formatted(Formatting.AQUA));
        Text link = new net.minecraft.text.LiteralText("[Click here to download]")
                .setStyle(new Style()
                        .setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                        .setHoverEvent(new net.minecraft.text.HoverEvent(net.minecraft.text.HoverEvent.Action.SHOW_TEXT,
                                new net.minecraft.text.LiteralText("Open Modrinth page")))
                        .setColor(Formatting.GREEN));
        client.player.sendMessage(message);
        client.player.sendMessage(link);
        //?} else if >=26.1 {
        MutableComponent message = Component.literal("[MineLights] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("A new version is available: ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(version).withStyle(ChatFormatting.AQUA));
        MutableComponent link = Component.literal("[Click here to download]")
                .setStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                        .withColor(ChatFormatting.GREEN));
        client.player.sendSystemMessage(message);
        client.player.sendSystemMessage(link);
        //?} else if >=1.21.5 {
        /* MutableText message = Text.literal("[MineLights] ").formatted(Formatting.GOLD)
                .append(Text.literal("A new version is available: ").formatted(Formatting.YELLOW))
                .append(Text.literal(version).formatted(Formatting.AQUA));
        MutableText link = Text.literal("[Click here to download]")
                .setStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                        .withColor(Formatting.GREEN));
        client.player.sendMessage(message, false);
        client.player.sendMessage(link, false); */
        //?} else if >=1.19 {
        /* MutableText message = Text.literal("[MineLights] ").formatted(Formatting.GOLD)
                .append(Text.literal("A new version is available: ").formatted(Formatting.YELLOW))
                .append(Text.literal(version).formatted(Formatting.AQUA));
        MutableText link = Text.literal("[Click here to download]")
                .setStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                        .withColor(Formatting.GREEN));
        client.player.sendMessage(message, false);
        client.player.sendMessage(link, false); */
        //?} else {
        /* MutableText message = new net.minecraft.text.LiteralText("[MineLights] ").formatted(Formatting.GOLD)
                .append(new net.minecraft.text.LiteralText("A new version is available: ").formatted(Formatting.YELLOW))
                .append(new net.minecraft.text.LiteralText(version).formatted(Formatting.AQUA));
        MutableText link = new net.minecraft.text.LiteralText("[Click here to download]")
                .setStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                        .withColor(Formatting.GREEN));
        client.player.sendMessage(message, false);
        client.player.sendMessage(link, false); */
        //?}
        LOGGER.info("Delivered queued update notification for version {}.", version);
    }

    public void shutdown() {
        if (lightingManagerThread != null)
            lightingManagerThread.interrupt();
        if (discoveryThread != null)
            discoveryThread.interrupt();
        if (serverMonitorThread != null)
            serverMonitorThread.interrupt();
        UDPClient.close();
        if (IS_WINDOWS) {
            CommandClient.sendCommandSync("shutdown");
            Process process = serverProcess;
            if (process != null && process.isAlive()) {
                try {
                    if (!process.waitFor(5, TimeUnit.SECONDS)) {
                        LOGGER.warn("MineLights server did not exit after shutdown; terminating its process.");
                        process.destroy();
                        if (!process.waitFor(2, TimeUnit.SECONDS)) {
                            LOGGER.warn("MineLights server did not stop after termination; forcing it to exit.");
                            process.destroyForcibly().waitFor(2, TimeUnit.SECONDS);
                        }
                    }
                    if (!process.isAlive()) {
                        LOGGER.info("MineLights server process stopped.");
                        serverProcess = null;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    process.destroy();
                    if (process.isAlive()) {
                        process.destroyForcibly();
                    }
                    serverProcess = null;
                }
            }
        }
    }

    //? if <= 1.15.2 {
    /* private static void checkForUpdate() {
        LOGGER.info("Checking for MineLights updates on Modrinth...");
        try {
            String currentVersion = resolvedModVersion;
            String gameVersion = SharedConstants.getGameVersion().getName();

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
                if (!currentVersion.equals(latestVersionNumber)) {
                    String modrinthUrl = "https://modrinth.com/mod/" + MODRINTH_PROJECT_ID + "/versions?version=" + gameVersion + "#download";
                    queueUpdateNotification(latestVersionNumber, modrinthUrl);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to check for mod updates", e);
        }
    }
    *///?} else if >= 1.16 && < 1.19 {
    /* private static void checkForUpdate() {
        LOGGER.info("Checking for MineLights updates on Modrinth...");
        try {
            String currentVersion = resolvedModVersion;
            String gameVersion = getMinecraftVersionName();

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
                if (!currentVersion.equals(latestVersionNumber)) {
                    String modrinthUrl = "https://modrinth.com/mod/" + MODRINTH_PROJECT_ID + "/versions?version=" + gameVersion + "#download";
                    queueUpdateNotification(latestVersionNumber, modrinthUrl);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to check for mod updates", e);
        }
    }
    *///?}
    //? if >= 1.19 && < 1.21.6 {
    /* private static void checkForUpdate() {
        LOGGER.info("Checking for MineLights updates on Modrinth...");
        try {
            String currentVersion = resolvedModVersion;
            String gameVersion = getMinecraftVersionId();

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
            JsonArray versions = JsonParser.parseString(json).getAsJsonArray();

            if (versions.size() > 0) {
                String latestVersionNumber = versions.get(0).getAsJsonObject().get("version_number").getAsString();
                if (!currentVersion.equals(latestVersionNumber)) {
                    String modrinthUrl = "https://modrinth.com/mod/" + MODRINTH_PROJECT_ID + "/versions?version=" + gameVersion + "#download";
                    queueUpdateNotification(latestVersionNumber, modrinthUrl);
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
            LOGGER.info("Current mod version: {}", currentVersion);

            //? if >=26.1 {
            String gameVersion = SharedConstants.getCurrentVersion().id();
            //?} else {
            /* String gameVersion = SharedConstants.getGameVersion().id(); */
            //?}
            LOGGER.info("Current Minecraft version: {}", gameVersion);

            String gameVersionsJson = "[\"" + gameVersion + "\"]";
            String loadersJson = "[\"" + resolvedModLoader + "\"]";

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

            JsonArray versions = JsonParser.parseString(json).getAsJsonArray();
            LOGGER.info("Parsed {} versions from Modrinth", versions.size());

            if (versions.size() > 0) {
                JsonObject latestVersion = versions.get(0).getAsJsonObject();
                String latestVersionNumber = latestVersion.get("version_number").getAsString();
                LOGGER.info("Latest version number from Modrinth: {}", latestVersionNumber);

                if (!currentVersion.equals(latestVersionNumber)) {
                    LOGGER.info("A new version of MineLights is available: {}", latestVersionNumber);
                    //? if >=26.1 {
                    String gameVersionId = SharedConstants.getCurrentVersion().id();
                    //?} else {
                    /* String gameVersionId = SharedConstants.getGameVersion().id(); */
                    //?}
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

        if (!CONFIG.manualServerManagement) {
            checkForServerUpdateAutomatically();
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

            if (release.expectedHash.isEmpty()) {
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
        try {
            ServerRelease release = getLatestServerRelease();
            if (release == null) {
                return;
            }

            Path serverExePath = getServerExePath();
            boolean needsUpdate = !Files.isRegularFile(serverExePath);
            if (!needsUpdate) {
                if (release.expectedHash.isEmpty()) {
                    LOGGER.warn("Could not verify MineLights Server from GitHub; assuming it is up to date.");
                    return;
                }
                needsUpdate = !sha256(serverExePath).equalsIgnoreCase(release.expectedHash);
            }
            if (!needsUpdate) {
                LOGGER.info("MineLights Server is up to date ({}).", release.version);
                return;
            }
            if (!serverDownloadInProgress.compareAndSet(false, true)) {
                return;
            }

            Thread downloadThread = new Thread(() -> {
                try {
                    LOGGER.info("Downloading MineLights Server {}.", release.version);
                    downloadAndInstallServer(release, serverExePath, true);
                } catch (Exception exception) {
                    LOGGER.error("Failed to download or install MineLights Server", exception);
                } finally {
                    serverDownloadInProgress.set(false);
                }
            }, "MineLights-Server-Auto-Updater");
            downloadThread.setDaemon(true);
            downloadThread.start();
        } catch (Exception e) {
            LOGGER.error("Failed to check for MineLights Server updates", e);
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
                return null;
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
                return null;
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
        Path temporaryPath = serverExePath.resolveSibling("MineLights.exe.new");
        try {
            downloadServerRelease(release, temporaryPath);

            if (Files.isRegularFile(serverExePath) && isServerRunning()) {
                LOGGER.info("Stopping MineLights Server before replacing its executable.");
                CommandClient.sendCommand("shutdown");
                Process process = serverProcess;
                if (process != null) {
                    try {
                        process.waitFor(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted while stopping MineLights Server", e);
                    }
                }
                long shutdownDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (isServerRunning() && System.nanoTime() < shutdownDeadline) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted while stopping MineLights Server", e);
                    }
                }
                if (isServerRunning()) {
                    throw new IOException("MineLights Server did not stop; its executable was left unchanged.");
                }
            }

            Files.createDirectories(serverExePath.getParent());
            try {
                Files.move(temporaryPath, serverExePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporaryPath, serverExePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            LOGGER.info("MineLights Server {} installed successfully.", release.version);
            lightingInitialized.set(false);
            if (restartAfterInstall) {
                isManualRestart = true;
                startServerProcess();
            }
        } finally {
            Files.deleteIfExists(temporaryPath);
        }
    }

    private static void downloadServerRelease(ServerRelease release, Path destination) throws IOException {
        Files.createDirectories(destination.getParent());
        HttpURLConnection connection = (HttpURLConnection) URI.create(release.downloadUrl).toURL().openConnection();
        connection.setRequestProperty("User-Agent", "MineLights-Mod/" + getModVersion());
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        try {
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("MineLights Server download responded with HTTP " + responseCode);
            }
            try (InputStream input = connection.getInputStream();
                    java.io.OutputStream output = Files.newOutputStream(destination)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
            }
            if (!release.expectedHash.isEmpty()
                    && !sha256(destination).equalsIgnoreCase(release.expectedHash)) {
                throw new IOException("MineLights Server SHA-256 did not match the release digest.");
            }
        } catch (Exception e) {
            Files.deleteIfExists(destination);
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException("Failed to download MineLights Server", e);
        } finally {
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
        pendingServerMessages.add(new ArrayList<>(messages));
    }

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
        //? if >=26.1 {
        Minecraft client = Minecraft.getInstance();
        //?} else {
        /* MinecraftClient client = MinecraftClient.getInstance(); */
        //?}
        if (client.player == null) {
            hasCheckedForMissingServer.set(false);
            return;
        }

        if (IS_WINDOWS && CONFIG.notifyMissingServer && CONFIG.manualServerManagement
                && hasCheckedForMissingServer.compareAndSet(false, true)
                && !Files.isRegularFile(getServerExePath()) && !isServerRunning()) {
            queueServerNotification(java.util.Arrays.asList(
                    "[MineLights] MineLights Server is not installed. Device lighting needs the server.",
                    "[MineLights] Download it from " + SERVER_RELEASES_URL,
                    "[MineLights] Put MineLights.exe at " + getServerInstallLocation() + " in your game directory."));
        }

        List<String> messages;
        while ((messages = pendingServerMessages.poll()) != null) {
            for (String text : messages) {
                //? if <=1.15.2 {
                client.player.sendMessage(new net.minecraft.text.LiteralText(text));
                //?} else if >=26.1 {
                client.player.sendSystemMessage(Component.literal(text));
                //?} else if >=1.19 {
                /* client.player.sendMessage(Text.literal(text), false); */
                //?} else {
                /* client.player.sendMessage(new net.minecraft.text.LiteralText(text)); */
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
        discoveryThread = new Thread(new DiscoveryListener(), "MineLights-Discovery");
        discoveryThread.setDaemon(true);
        discoveryThread.start();

        updateServerMonitorSetting();
    }

    /** Applies changes to the user-controlled automatic server start setting immediately. */
    public static synchronized void updateServerMonitorSetting() {
        if (!IS_WINDOWS || CONFIG == null) {
            return;
        }

        if (CONFIG.autoStartServer) {
            if (serverMonitorThread == null || !serverMonitorThread.isAlive()
                    || serverMonitorThread.isInterrupted()) {
                serverMonitorThread = new Thread(MineLightsClient::serverMonitorLoop, "MineLights-Server-Monitor");
                serverMonitorThread.setDaemon(true);
                serverMonitorThread.start();
            }
        } else if (serverMonitorThread != null) {
            serverMonitorThread.interrupt();
            serverMonitorThread = null;
        }
    }

    private static void serverMonitorLoop() {
        LOGGER.info("Starting MineLights Server monitor.");
        boolean hasConnected = false;
        long lastLaunchAttemptTime = 0;

        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (isServerRunning()) {
                    if (isManualRestart) {
                        LOGGER.info("New server process detected after manual restart. Forcing re-initialization...");
                        lightingInitialized.set(false);
                        isManualRestart = false;
                        hasConnected = false;
                    }

                    if (!hasConnected) {
                        triggerLightingInitialization();
                        hasConnected = true;
                    }

                    Thread.sleep(10000);
                    continue;
                }

                lightingInitialized.set(false);
                hasConnected = false;

                if (isManualRestart) {
                    LOGGER.info("Manual restart initiated. Waiting for new server process to start...");
                    Thread.sleep(8000);
                    continue;
                }

                Path serverExePath = getServerExePath();

                if (!Files.isRegularFile(serverExePath)) {
                    if (CONFIG.manualServerManagement) {
                        Thread.sleep(10000);
                        continue;
                    }
                    if (!serverDownloadInProgress.compareAndSet(false, true)) {
                        Thread.sleep(1000);
                        continue;
                    }
                    try {
                        ServerRelease release = getLatestServerRelease();
                        if (release == null) {
                            Thread.sleep(30000);
                        } else {
                            downloadAndInstallServer(release, serverExePath, true);
                        }
                    } catch (Exception e) {
                        LOGGER.error("Failed to automatically download MineLights Server", e);
                        Thread.sleep(30000);
                    } finally {
                        serverDownloadInProgress.set(false);
                    }
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

    public static boolean isServerRunning() {
        try (Socket ignored = new Socket("127.0.0.1", 63213)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void startServerProcess() {
        Path serverExePath = getServerExePath();

        if (!Files.exists(serverExePath)) {
            return;
        }

        try {
            LOGGER.info("Launching server from: {}", serverExePath.toAbsolutePath());
            ProcessBuilder pb = new ProcessBuilder(serverExePath.toAbsolutePath().toString());
            pb.directory(serverExePath.getParent().toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            serverProcess = process;
            startServerOutputDrain(process);
        } catch (IOException e) {
            LOGGER.warn("Failed to start MineLights.exe process: {}", e.getMessage());
            serverProcess = null;
        }
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
        if (lightingInitialized.compareAndSet(false, true)) {
            LOGGER.info("Primary initialization trigger. Starting lighting systems...");
            refreshLightingManager();
        } else {
            LOGGER.info("Initialization already in progress or complete. Skipping redundant start.");
        }
    }

    public static synchronized void refreshLightingManager() {
        LOGGER.info("Refreshing lighting manager...");

        if (lightingManagerThread != null && lightingManagerThread.isAlive()) {
            lightingManagerThread.interrupt();
            try {
                lightingManagerThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        discoveredDevices.clear();
        proxyDiscoveredLatch = new CountDownLatch(1);
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
