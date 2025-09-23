package megabytesme.minelights;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class DownloadProgressScreen extends Screen {
    private final Screen parent;
    private final String downloadUrl;
    private final Path destination;

    private enum Status {
        DOWNLOADING, SUCCESS, FAILED, STARTING
    }

    private final AtomicReference<Status> status = new AtomicReference<>(Status.DOWNLOADING);
    private final AtomicInteger progress = new AtomicInteger(0);
    private final AtomicReference<String> errorMessage = new AtomicReference<>("");
    private final AtomicReference<String> downloadSpeed = new AtomicReference<>("");

    private ButtonWidget closeButton;

    public DownloadProgressScreen(Screen parent, String downloadUrl, Path destination) {
        super(Text.empty());
        this.parent = parent;
        this.downloadUrl = downloadUrl;
        this.destination = destination;

        new Thread(this::downloadAndStartServer, "MineLights-Downloader").start();
    }

    @Override
    protected void init() {
        this.closeButton = ButtonWidget
                .builder(Text.translatable("minelights.gui.button.close"), button -> this.close())
                .dimensions(this.width / 2 - 100, this.height - 40, 200, 20)
                .build();

        this.closeButton.active = false;
        this.addDrawableChild(this.closeButton);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("minelights.gui.download.progress_title"), this.width / 2, 20, 0xFFFFFF);

        Text infoText = Text.translatable("minelights.gui.download.info");
        context.drawCenteredTextWithShadow(this.textRenderer, infoText, this.width / 2, this.height / 2 - 35, 0xA0A0A0);

        Text statusText;
        if (status.get() == Status.DOWNLOADING) {
            statusText = Text.translatable("minelights.gui.download.status.downloading_speed", progress.get(),
                    downloadSpeed.get());
        } else if (status.get() == Status.STARTING) {
            statusText = Text.translatable("minelights.gui.download.status.starting");
        } else if (status.get() == Status.SUCCESS) {
            statusText = Text.translatable("minelights.gui.download.status.success");
        } else {
            statusText = Text.translatable("minelights.gui.download.status.failed", errorMessage.get());
        }
        context.drawCenteredTextWithShadow(this.textRenderer, statusText, this.width / 2, this.height / 2 - 18,
                0xFFFFFF);

        int barWidth = 200;
        int barHeight = 8;
        int barX = this.width / 2 - barWidth / 2;
        int barY = this.height / 2;

        int fillWidth = (int) (barWidth * (this.progress.get() / 100.0f));

        context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF303030);
        context.fill(barX, barY, barX + fillWidth, barY + barHeight, 0xFFFFFFFF);
    }

    private String formatSpeed(long bytesPerSecond) {
        if (bytesPerSecond < 1024)
            return String.format("%d B/s", bytesPerSecond);
        long kbps = bytesPerSecond / 1024;
        if (kbps < 1024)
            return String.format("%d KB/s", kbps);
        return String.format("%.2f MB/s", kbps / 1024.0);
    }

    private void downloadAndStartServer() {
        try {
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
                        long speed = ((totalBytesRead - lastBytes) * 1000) / (currentTime - lastTime);
                        downloadSpeed.set(formatSpeed(speed));
                        lastTime = currentTime;
                        lastBytes = totalBytesRead;
                    }
                }
            }

            status.set(Status.STARTING);
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
            } else {
                throw new IOException("Server did not start within 10 seconds.");
            }

        } catch (Exception e) {
            MineLightsClient.LOGGER.error("Failed during download or server start", e);
            errorMessage.set(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            status.set(Status.FAILED);
        } finally {
            this.closeButton.active = true;
        }
    }
}