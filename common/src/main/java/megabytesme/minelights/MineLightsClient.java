package megabytesme.minelights;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import megabytesme.minelights.config.MineLightsConfig;
import megabytesme.minelights.config.SimpleJsonConfig;
import megabytesme.minelights.network.CommandClient;
import megabytesme.minelights.network.DiscoveryListener;
import megabytesme.minelights.network.UDPClient;
import megabytesme.minelights.runtime.LightingManager;
import net.minecraft.SharedConstants;
//? if loader_neoforge || >=26.1 {
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class MineLightsClient {
    public static final Logger LOGGER = LogManager.getLogger("MineLights");
    public static final String MOD_ID = "minelights";
    public static final String MOD_VERSION = "2.3.7";
    private static Path configDir = Paths.get(".");
    private static String resolvedModVersion = MOD_VERSION;
    private static String resolvedModLoader = "fabric";
    public static final List<String> serverLogLines = Collections.synchronizedList(new ArrayList<>());
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

    public static CountDownLatch proxyDiscoveredLatch = new CountDownLatch(1);

    private static final AtomicBoolean hasPerformedServerCheck = new AtomicBoolean(false);
    private static final String GITHUB_API_URL = "https://api.github.com/repos/megabytesme/MineLights-Server/releases/latest";
    private boolean titleScreenHooked = false;
    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    private static final AtomicBoolean lightingInitialized = new AtomicBoolean(false);

    private static final String MODRINTH_PROJECT_ID = "minelights"; 
    private static final AtomicBoolean hasCheckedForUpdate = new AtomicBoolean(false);

    public enum DownloadStatus {
        IDLE,
        DOWNLOADING,
        VERIFYING,
        SUCCESS,
        FAILED
    }

    public static final AtomicReference<DownloadStatus> downloadStatus = new AtomicReference<>(DownloadStatus.IDLE);
    public static final AtomicInteger downloadProgress = new AtomicInteger(0);
    public static final AtomicReference<String> downloadError = new AtomicReference<>("");
    public static final AtomicLong downloadBytesSoFar = new AtomicLong(0);
    public static final AtomicLong downloadTotalBytes = new AtomicLong(0);
    public static final AtomicReference<String> downloadEta = new AtomicReference<>("");
    public static final AtomicReference<String> downloadSpeedMBps = new AtomicReference<>("");
    private static Process serverProcess = null;

    public void init(Path configDir, String modVersion, String modLoader) {
        if (!initialized.compareAndSet(false, true)) {
            return;
        }

        MineLightsClient.configDir = configDir;
        MineLightsClient.resolvedModVersion = modVersion;
        MineLightsClient.resolvedModLoader = modLoader;
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

    //? if loader_neoforge || >=26.1 {
    public void onClientTick(Minecraft client) {
    //?} else {
    /* public void onClientTick(MinecraftClient client) {
    *///?}
        //? if loader_neoforge || >=26.1 {
        if (client.screen instanceof TitleScreen && !titleScreenHooked) {
        //?} else {
        /* if (client.currentScreen instanceof TitleScreen && !titleScreenHooked) {
        *///?}
            titleScreenHooked = true;
            if (IS_WINDOWS && !hasPerformedServerCheck.getAndSet(true)) {
                new Thread(MineLightsClient::checkForServerUpdate, "MineLights-Update-Check").start();
            }
        }

        if (hasCheckedForUpdate.compareAndSet(false, true)) {
            new Thread(MineLightsClient::checkForUpdate, "MineLights-Modrinth-Update-Check").start();
        }
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
            CommandClient.sendCommand("shutdown");
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
    *///?} else if >= 1.16 && < 1.19 {
    /* private static void checkForUpdate() {
        LOGGER.info("Checking for MineLights updates on Modrinth...");
        try {
            String currentVersion = resolvedModVersion;
            String gameVersion = SharedConstants.getGameVersion().getName();

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
    //? if loader_neoforge && <1.21.6 {
    private static void checkForUpdate() {
        LOGGER.info("Checking for MineLights updates on Modrinth...");
        try {
            String currentVersion = resolvedModVersion;
            String gameVersion = SharedConstants.getCurrentVersion().getId();

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
                    Minecraft.getInstance().execute(() -> {
                        if (Minecraft.getInstance().player != null) {
                            String modrinthUrl = "https://modrinth.com/mod/" + MODRINTH_PROJECT_ID + "/versions?version=" + gameVersion + "#download";
                            MutableComponent message = Component.literal("[MineLights] ").withStyle(ChatFormatting.GOLD)
                                    .append(Component.literal("A new version is available: ").withStyle(ChatFormatting.YELLOW))
                                    .append(Component.literal(latestVersionNumber).withStyle(ChatFormatting.AQUA));
                            MutableComponent link = Component.literal("[Click here to download]")
                                    .setStyle(Style.EMPTY
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, modrinthUrl))
                                            .withColor(ChatFormatting.GREEN));
                            Minecraft.getInstance().player.displayClientMessage(message, false);
                            Minecraft.getInstance().player.displayClientMessage(link, false);
                        }
                    });
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
            String gameVersion = SharedConstants.getGameVersion().getId();

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
            LOGGER.info("Current mod version: {}", currentVersion);

            //? if loader_neoforge || >=26.1 {
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

                    //? if loader_neoforge || >=26.1 {
                    Minecraft.getInstance().execute(() -> {
                    //?} else {
                    /* MinecraftClient.getInstance().execute(() -> {
                    *///?}
                        LOGGER.info("Scheduling message send on client thread...");
                        //? if loader_neoforge || >=26.1 {
                        if (Minecraft.getInstance().player != null) {
                        //?} else {
                        /* if (MinecraftClient.getInstance().player != null) {
                        *///?}
                            LOGGER.info("Player is present, sending chat messages...");

                            //? if loader_neoforge || >=26.1 {
                            String gameVersionId = SharedConstants.getCurrentVersion().id();
                            //?} else {
                            /* String gameVersionId = SharedConstants.getGameVersion().id(); */
                            //?}
                            String modrinthUrl = String.format(
                                "https://modrinth.com/mod/%s/versions?version=%s#download",
                                MODRINTH_PROJECT_ID,
                                gameVersionId
                            );

                            //? if loader_neoforge || >=26.1 {
                            MutableComponent message = Component.literal("[MineLights] ").withStyle(ChatFormatting.GOLD)
                                    .append(Component.literal("A new version is available: ").withStyle(ChatFormatting.YELLOW))
                                    .append(Component.literal(latestVersionNumber).withStyle(ChatFormatting.AQUA));
                            //?} else {
                            /* MutableText message = Text.literal("[MineLights] ").formatted(Formatting.GOLD)
                                    .append(Text.literal("A new version is available: ").formatted(Formatting.YELLOW))
                                    .append(Text.literal(latestVersionNumber).formatted(Formatting.AQUA)); */
                            //?}

                            //? if loader_neoforge || >=26.1 {
                            MutableComponent link = Component.literal("[Click here to download]")
                                    .setStyle(Style.EMPTY
                                            .withClickEvent(new ClickEvent.OpenUrl(URI.create(modrinthUrl)))
                                            .withColor(ChatFormatting.GREEN));
                            //?} else {
                            /* MutableText link = Text.literal("[Click here to download]")
                                    .setStyle(Style.EMPTY
                                            .withClickEvent(new ClickEvent.OpenUrl(URI.create(modrinthUrl)))
                                            .withColor(Formatting.GREEN)); */
                            //?}

                            //? if >=26.1 {
                            Minecraft.getInstance().player.sendSystemMessage(message);
                            Minecraft.getInstance().player.sendSystemMessage(link);
                            //?} else if loader_neoforge {
                            Minecraft.getInstance().player.displayClientMessage(message, false);
                            Minecraft.getInstance().player.displayClientMessage(link, false);
                            //?} else {
                            /* MinecraftClient.getInstance().player.sendMessage(message, false);
                            MinecraftClient.getInstance().player.sendMessage(link, false); */
                            //?}
                        } else {
                            LOGGER.info("Player is null, cannot send chat messages.");
                        }
                    });
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
        if (downloadStatus.get() == DownloadStatus.DOWNLOADING) {
            return;
        }

        try {
            //? if loader_neoforge || >=26.1 {
            Path serverExePath = Minecraft.getInstance().gameDirectory.toPath().resolve("mods")
            //?} else {
            /* Path serverExePath = MinecraftClient.getInstance().runDirectory.toPath().resolve("mods") */
            //?}
                    .resolve("MineLights").resolve("MineLights.exe");
            URI apiUri = URI.create(GITHUB_API_URL);
            HttpURLConnection conn = (HttpURLConnection) apiUri.toURL().openConnection();
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            String json;
            try (InputStream in = conn.getInputStream()) {
                json = readAllBytes(in);
            }

            //? if <=1.21.2 {
            /* JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            *///?} else {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            //?}

            JsonArray assets = root.getAsJsonArray("assets");
            if (assets.size() == 0)
                return;

            JsonObject asset = assets.get(0).getAsJsonObject();
            String expectedHash = asset.has("digest") ? asset.get("digest").getAsString().replace("sha256:", "") : "";

            boolean needsDownload = false;
            if (!Files.exists(serverExePath)) {
                needsDownload = true;
            } else {
                String localHash = sha256(serverExePath);
                if (!expectedHash.isEmpty() && !localHash.equalsIgnoreCase(expectedHash)) {
                    LOGGER.info("MineLights.exe is outdated or corrupted. An update is required.");
                    needsDownload = true;
                } else if (expectedHash.isEmpty()) {
                    LOGGER.warn("Could not verify server hash from GitHub API, assuming it's up to date.");
                } else {
                    LOGGER.info("MineLights.exe is up to date.");
                }
            }

            if (needsDownload) {
                Path tempExePath = serverExePath.getParent().resolve("MineLights.exe.new");
                new Thread(() -> {
                    if (performServerDownload(tempExePath)) {
                        LOGGER.info("Shutting down MineLights server for update...");
                        CommandClient.sendCommand("shutdown");
                        if (serverProcess != null) {
                            try {
                                serverProcess.waitFor(5, TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }

                        try {
                            Files.deleteIfExists(serverExePath);
                            Files.move(tempExePath, serverExePath);
                            LOGGER.info("MineLights.exe updated successfully.");
                            lightingInitialized.set(false);
                            isManualRestart = true;
                            startServerProcess();
                        } catch (IOException e) {
                            LOGGER.error("Failed to replace MineLights.exe", e);
                        }
                    }
                }, "MineLights-Background-Downloader").start();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to check for server update", e);
            downloadError.set(e.getMessage());
            downloadStatus.set(DownloadStatus.FAILED);
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

    private static boolean performServerDownload(Path destination) {
        if (!downloadStatus.compareAndSet(DownloadStatus.IDLE, DownloadStatus.DOWNLOADING) &&
                !downloadStatus.compareAndSet(DownloadStatus.FAILED, DownloadStatus.DOWNLOADING) &&
                !downloadStatus.compareAndSet(DownloadStatus.SUCCESS, DownloadStatus.DOWNLOADING)) {
            return false;
        }

        downloadProgress.set(0);
        downloadError.set("");

        try {
            URL apiUrl = URI.create(GITHUB_API_URL).toURL();
            HttpURLConnection conn = (HttpURLConnection) apiUrl.openConnection();
            conn.setRequestProperty("Accept", "application/vnd.github+json");

            String json;
            try (InputStream in = conn.getInputStream()) {
                json = readAllBytes(in);
            }

            //? if <=1.21.2 {
             JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            //?} else {
            /*JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            *///?}
            JsonArray assets = root.getAsJsonArray("assets");
            if (assets.size() == 0)
                throw new IOException("No assets found in release");

            JsonObject asset = assets.get(0).getAsJsonObject();
            String downloadUrl = asset.get("browser_download_url").getAsString();
            String expectedHash = asset.has("digest") ? asset.get("digest").getAsString().replace("sha256:", "") : "";

            Files.createDirectories(destination.getParent());
            URL url = URI.create(downloadUrl).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "MineLights-Mod-Downloader/1.0");

            long totalFileSize = connection.getContentLengthLong();
            long totalBytesRead = 0;
            downloadTotalBytes.set(totalFileSize);
            downloadBytesSoFar.set(0);
            long startTime = System.nanoTime();

            try (InputStream inputStream = connection.getInputStream();
                FileOutputStream outputStream = new FileOutputStream(destination.toFile())) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                long lastBytesRead = 0;
                long lastTimeCheck = startTime;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                    downloadBytesSoFar.set(totalBytesRead);

                    if (totalFileSize > 0) {
                        downloadProgress.set((int) ((totalBytesRead * 100) / totalFileSize));
                    }

                    double secondsElapsed = (System.nanoTime() - startTime) / 1_000_000_000.0;
                    if (secondsElapsed > 0 && totalFileSize > 0) {
                        double bytesPerSecond = totalBytesRead / secondsElapsed;
                        long secondsRemaining = (long) ((totalFileSize - totalBytesRead) / bytesPerSecond);
                        downloadEta.set(formatEta(secondsRemaining));
                    }

                    long now = System.nanoTime();
                    double intervalSeconds = (now - lastTimeCheck) / 1_000_000_000.0;
                    if (intervalSeconds >= 1.0) {
                        long bytesInInterval = totalBytesRead - lastBytesRead;
                        double mbps = bytesInInterval / (1024.0 * 1024.0) / intervalSeconds;
                        downloadSpeedMBps.set(String.format("%.2f", mbps));
                        lastBytesRead = totalBytesRead;
                        lastTimeCheck = now;
                    }
                }
            }

            downloadStatus.set(DownloadStatus.VERIFYING);
            String actualHash = sha256(destination);
            if (!expectedHash.isEmpty() && !actualHash.equalsIgnoreCase(expectedHash)) {
                throw new IOException("Hash mismatch! Expected " + expectedHash + " but got " + actualHash);
            }

            LOGGER.info("Server executable downloaded successfully.");
            downloadStatus.set(DownloadStatus.SUCCESS);
            return true;

        } catch (Exception e) {
            LOGGER.error("Failed during background download or verification", e);
            downloadError.set(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            downloadStatus.set(DownloadStatus.FAILED);
            return false;
        }
    }

    private static String formatEta(long seconds) {
        long mins = seconds / 60;
        long secs = seconds % 60;
        return String.format("%dm %ds", mins, secs);
    }

    private void initializeServerConnection() {
        discoveryThread = new Thread(new DiscoveryListener(), "MineLights-Discovery");
        discoveryThread.setDaemon(true);
        discoveryThread.start();

        if (CONFIG.autoStartServer) {
            serverMonitorThread = new Thread(MineLightsClient::serverMonitorLoop, "MineLights-Server-Monitor");
            serverMonitorThread.setDaemon(true);
            serverMonitorThread.start();
        } else {
            new Thread(MineLightsClient::checkForServerUpdate, "MineLights-Initial-Check").start();
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

                //? if loader_neoforge || >=26.1 {
                Path serverExePath = Minecraft.getInstance().gameDirectory.toPath()
                //?} else {
                /* Path serverExePath = MinecraftClient.getInstance().runDirectory.toPath() */
                //?}
                    .resolve("mods").resolve("MineLights").resolve("MineLights.exe");

                if (!Files.exists(serverExePath)) {
                    if (downloadStatus.get() != DownloadStatus.DOWNLOADING) {
                        LOGGER.warn("MineLights.exe not found. Performing synchronous download.");
                        if (!performServerDownload(serverExePath)) {
                            LOGGER.error("Download failed. Will retry after 30 seconds.");
                            Thread.sleep(30000);
                        }
                    } else {
                        Thread.sleep(1000);
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
        //? if loader_neoforge || >=26.1 {
        Path serverExePath = Minecraft.getInstance().gameDirectory.toPath().resolve("mods").resolve("MineLights")
        //?} else {
        /* Path serverExePath = MinecraftClient.getInstance().runDirectory.toPath().resolve("mods").resolve("MineLights") */
        //?}
                .resolve("MineLights.exe");

        if (!Files.exists(serverExePath)) {
            return;
        }

        try {
            LOGGER.info("Launching server from: {}", serverExePath.toAbsolutePath());
            serverLogLines.clear();

            ProcessBuilder pb = new ProcessBuilder(serverExePath.toAbsolutePath().toString());
            pb.directory(serverExePath.getParent().toFile());
            pb.redirectErrorStream(true);
            serverProcess = pb.start();
            DateTimeFormatter tsFormat = DateTimeFormatter.ofPattern("HH:mm:ss");

            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(serverProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String ts = LocalTime.now().format(tsFormat);
                        String stamped = "[" + ts + "] " + line;

                        LOGGER.info("[MineLights.exe] {}", stamped);

                        synchronized (serverLogLines) {
                            serverLogLines.add(0, stamped);
                        }
                    }
                } catch (IOException e) {
                    LOGGER.error("Error reading server log", e);
                }
            }, "MineLights-Server-Log-Reader").start();
        } catch (IOException e) {
            LOGGER.warn("Failed to start MineLights.exe process: {}", e.getMessage());
            serverProcess = null;
        }
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
        lightingManagerThread.start();

        lightingInitialized.set(true);
    }

    public static void saveConfig() {
        CONFIG_MANAGER.save(CONFIG);
    }

    public static Path getConfigDir() {
        return configDir;
    }
}
