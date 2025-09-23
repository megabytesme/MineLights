package megabytesme.minelights;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.MultilineTextWidget;
import net.minecraft.text.Text;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class DownloadProgressScreen extends Screen {
    private final Screen parent;
    private final String tagName = "v2-server";
    private final Path destination;

    private enum Status {
        DOWNLOADING, SUCCESS, FAILED, STARTING
    }

    private final AtomicReference<Status> status = new AtomicReference<>(Status.DOWNLOADING);
    private final AtomicInteger progress = new AtomicInteger(0);
    private final AtomicReference<String> errorMessage = new AtomicReference<>("");
    private final AtomicReference<String> downloadSpeed = new AtomicReference<>("");
    private MultilineTextWidget statusWidget;
    private ButtonWidget closeButton;

    public DownloadProgressScreen(Screen parent, Path destination) {
        super(Text.empty());
        this.parent = parent;
        this.destination = destination;

        new Thread(this::downloadAndStartServer, "MineLights-Downloader").start();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int widgetWidth = 300;
        int widgetX = centerX - (widgetWidth / 2);

        MultilineTextWidget titleWidget = new MultilineTextWidget(widgetX, 40,
                Text.translatable("minelights.gui.download.progress_title"), this.textRenderer)
                .setMaxWidth(widgetWidth).setCentered(true);
        this.addDrawableChild(titleWidget);

        MultilineTextWidget infoWidget = new MultilineTextWidget(widgetX, 65,
                Text.translatable("minelights.gui.download.info"), this.textRenderer)
                .setMaxWidth(widgetWidth).setCentered(true);
        this.addDrawableChild(infoWidget);

        this.statusWidget = new MultilineTextWidget(widgetX, this.height / 2 + 15,
                Text.empty(), this.textRenderer)
                .setMaxWidth(widgetWidth).setCentered(true);
        this.addDrawableChild(this.statusWidget);

        this.closeButton = ButtonWidget
                .builder(Text.translatable("minelights.gui.button.close"), b -> this.close())
                .dimensions(centerX - 100, this.height - 40, 200, 20)
                .build();
        this.closeButton.active = false;
        this.addDrawableChild(this.closeButton);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int barWidth = 300;
        int barHeight = 8;
        int barX = this.width / 2 - barWidth / 2;
        int barY = this.height / 2;
        int fillWidth = (int) (barWidth * (this.progress.get() / 100.0f));

        context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF303030);
        context.fill(barX, barY, barX + fillWidth, barY + barHeight, 0xFFFFFFFF);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    private String formatSpeed(long bytesPerSecond) {
        if (bytesPerSecond < 1024)
            return String.format("%d B/s", bytesPerSecond);
        long kbps = bytesPerSecond / 1024;
        if (kbps < 1024)
            return String.format("%d KB/s", kbps);
        return String.format("%.2f MB/s", kbps / 1024.0);
    }

    private String formatETA(long seconds) {
        if (seconds < 0)
            return "Calculating...";
        long mins = seconds / 60;
        long secs = seconds % 60;
        return String.format("%dm %ds remaining", mins, secs);
    }

    private void updateStatusWidget(long totalFileSize, long totalBytesRead, long speed) {
        long remainingBytes = totalFileSize - totalBytesRead;
        long etaSeconds = speed > 0 ? remainingBytes / speed : -1;
        String eta = formatETA(etaSeconds);
        String speedString = downloadSpeed.get();
        if (speedString == null || speedString.isEmpty())
            speedString = "0 B/s";

        statusWidget.setMessage(Text.literal(
                String.format("%d%% (%s) — %s", progress.get(), speedString, eta)));
    }

    private String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream fis = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void downloadAndStartServer() {
        try {
            URL apiUrl = URI.create("https://api.github.com/repos/megabytesme/MineLights/releases/tags/" + tagName)
                    .toURL();
            HttpURLConnection conn = (HttpURLConnection) apiUrl.openConnection();
            conn.setRequestProperty("Accept", "application/vnd.github+json");

            String json;
            try (InputStream in = conn.getInputStream()) {
                json = new String(in.readAllBytes());
            }

            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray assets = root.getAsJsonArray("assets");
            if (assets.isEmpty())
                throw new IOException("No assets found in release");

            JsonObject asset = assets.get(0).getAsJsonObject();
            String downloadUrl = asset.get("browser_download_url").getAsString();
            String expectedHash = asset.get("digest").getAsString().replace("sha256:", "");

            Files.createDirectories(destination.getParent());
            URL url = URI.create(downloadUrl).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "MineLights-Mod-Downloader/1.0");
            connection.connect();

            long totalFileSize = connection.getContentLengthLong();
            long totalBytesRead = 0;
            long lastTime = System.currentTimeMillis();
            long lastBytes = 0;

            try (InputStream inputStream = connection.getInputStream();
                    FileOutputStream outputStream = new FileOutputStream(destination.toFile())) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                    if (totalFileSize > 0)
                        progress.set((int) ((totalBytesRead * 100) / totalFileSize));

                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastTime >= 500) {
                        long currentSpeed = ((totalBytesRead - lastBytes) * 1000) / (currentTime - lastTime);
                        downloadSpeed.set(formatSpeed(currentSpeed));
                        lastTime = currentTime;
                        lastBytes = totalBytesRead;

                        final long fTotalFileSize = totalFileSize;
                        final long fTotalBytesRead = totalBytesRead;
                        final long fSpeed = currentSpeed;

                        MinecraftClient.getInstance()
                                .execute(() -> updateStatusWidget(fTotalFileSize, fTotalBytesRead, fSpeed));
                    }
                }
            }

            String actualHash = sha256(destination);
            if (!actualHash.equalsIgnoreCase(expectedHash)) {
                throw new IOException("Hash mismatch! Expected " + expectedHash + " but got " + actualHash);
            }

            status.set(Status.STARTING);
            MinecraftClient.getInstance().execute(
                    () -> statusWidget.setMessage(Text.translatable("minelights.gui.download.status.starting")));

            if (MineLightsClient.serverMonitorThread != null)
                MineLightsClient.serverMonitorThread.interrupt();

            try {
                ProcessBuilder pb = new ProcessBuilder(destination.toAbsolutePath().toString());
                pb.directory(destination.getParent().toFile());
                pb.start();
            } catch (IOException e) {
                throw new IOException("Failed to start downloaded process", e);
            }

            boolean serverStarted = false;
            for (int i = 0; i < 20; i++) {
                if (MineLightsClient.isServerRunning()) {
                    serverStarted = true;
                    break;
                }
                Thread.sleep(500);
            }

            if (serverStarted) {
                MineLightsClient.LOGGER.info("Server is running. Refreshing device list.");
                MineLightsClient.refreshLightingManager();
                status.set(Status.SUCCESS);
                MinecraftClient.getInstance().execute(
                        () -> statusWidget.setMessage(Text.translatable("minelights.gui.download.status.success")));
            } else {
                throw new IOException("Server did not start within 10 seconds.");
            }

        } catch (Exception e) {
            MineLightsClient.LOGGER.error("Failed during download, verification, or server start", e);
            errorMessage.set(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            status.set(Status.FAILED);
            MinecraftClient.getInstance().execute(() -> statusWidget.setMessage(Text.translatable(
                    "minelights.gui.download.status.failed",
                    errorMessage.get())));
        } finally {
            MinecraftClient.getInstance().execute(() -> closeButton.active = true);
        }
    }
}