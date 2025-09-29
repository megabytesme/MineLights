package megabytesme.minelights;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
//? if >=1.15 {
/* import net.minecraft.client.util.math.MatrixStack;
*///?}
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class DownloadProgressScreen extends Screen {
    private final Screen parent;
    private final String tagName = "v2-server";
    private final Path destination;

    private enum Status {
        DOWNLOADING, SUCCESS, FAILED, STARTING
    }

    private final AtomicReference<Status> status = new AtomicReference<>(Status.STARTING);
    private final AtomicInteger progress = new AtomicInteger(0);
    private final AtomicReference<String> errorMessage = new AtomicReference<>("");
    private final AtomicReference<String> downloadSpeed = new AtomicReference<>("");
    private String statusMessage = "";
    private ButtonWidget closeButton;

    public DownloadProgressScreen(Screen parent, Path destination) {
        super(new LiteralText(""));
        this.parent = parent;
        this.destination = destination;
        new Thread(this::downloadAndStartServer, "MineLights-Downloader").start();
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        //? if >=1.16 {
        /* this.closeButton = new ButtonWidget(centerX - 100, this.height - 40, 200, 20,
            new TranslatableText("minelights.gui.button.close"), (button) -> this.onClose());
        this.closeButton.active = false;
        this.addButton(this.closeButton);
        *///?} else {
        /* this.closeButton = new ButtonWidget(centerX - 100, this.height - 40, 200, 20,
            new TranslatableText("minelights.gui.button.close").getString(), (button) -> this.onClose());
        this.closeButton.active = false;
        this.addButton(this.closeButton);
        *///?}
    }

    //? if <1.15 {
    /* @Override
    public void render(int mouseX, int mouseY, float delta) {
        this.renderBackground();
        super.render(mouseX, mouseY, delta);

        int centerX = this.width / 2;
        this.drawCenteredString(this.font,
            new TranslatableText("minelights.gui.download.progress_title").getString(),
            centerX, 40, 0xFFFFFF);
        this.drawCenteredString(this.font,
            new TranslatableText("minelights.gui.download.info").getString(),
            centerX, 65, 0xFFFFFF);
        this.drawCenteredString(this.font, this.statusMessage,
            centerX, this.height / 2 + 15, 0xFFFFFF);

        int barWidth = 300;
        int barHeight = 8;
        int barX = this.width / 2 - barWidth / 2;
        int barY = this.height / 2;
        int fillWidth = (int) (barWidth * (this.progress.get() / 100.0f));

        fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF303030);
        fill(barX, barY, barX + fillWidth, barY + barHeight, 0xFFFFFFFF);
    }
    *///?}
    //? if =1.15 {
    /* @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        super.render(matrices, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        this.drawCenteredString(this.font,
                new TranslatableText("minelights.gui.download.progress_title").getString(),
                centerX, 40, 0xFFFFFF);
        this.drawCenteredString(this.font,
                new TranslatableText("minelights.gui.download.info").getString(),
                centerX, 65, 0xFFFFFF);
        this.drawCenteredString(this.font, this.statusMessage,
                centerX, this.height / 2 + 15, 0xFFFFFF);

        int barWidth = 300;
        int barHeight = 8;
        int barX = this.width / 2 - barWidth / 2;
        int barY = this.height / 2;
        int fillWidth = (int) (barWidth * (this.progress.get() / 100.0f));

        fill(matrices, barX, barY, barX + barWidth, barY + barHeight, 0xFF303030);
        fill(matrices, barX, barY, barX + fillWidth, barY + barHeight, 0xFFFFFFFF);
    }
    *///?}
    //? if >=1.16 {
    /* @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        super.render(matrices, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        this.drawCenteredText(matrices, this.textRenderer,
                new TranslatableText("minelights.gui.download.progress_title"),
                centerX, 40, 0xFFFFFF);
        this.drawCenteredText(matrices, this.textRenderer,
                new TranslatableText("minelights.gui.download.info"),
                centerX, 65, 0xFFFFFF);
        this.drawCenteredString(matrices, this.textRenderer, this.statusMessage,
                centerX, this.height / 2 + 15, 0xFFFFFF);

        int barWidth = 300;
        int barHeight = 8;
        int barX = this.width / 2 - barWidth / 2;
        int barY = this.height / 2;
        int fillWidth = (int) (barWidth * (this.progress.get() / 100.0f));

        fill(matrices, barX, barY, barX + barWidth, barY + barHeight, 0xFF303030);
        fill(matrices, barX, barY, barX + fillWidth, barY + barHeight, 0xFFFFFFFF);
    }
    *///?}

    @Override
    public void onClose() {
        //? if >=1.15 {
        /* this.client.openScreen(this.parent);
        *///?} else {
        /* this.minecraft.openScreen(this.parent);
        *///?}
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

        this.statusMessage = String.format("%d%% (%s) — %s", progress.get(), speedString, eta);
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

    private String sha256(Path file) throws Exception {
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

    private static String readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[4096];
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return new String(buffer.toByteArray());
    }

    private void downloadAndStartServer() {
        try {
            status.set(Status.DOWNLOADING);
            URL apiUrl = URI.create("https://api.github.com/repos/megabytesme/MineLights/releases/tags/" + tagName)
                    .toURL();
            HttpURLConnection conn = (HttpURLConnection) apiUrl.openConnection();
            conn.setRequestProperty("Accept", "application/vnd.github+json");

            String json;
            try (InputStream in = conn.getInputStream()) {
                json = readAllBytes(in);
            }

            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            JsonArray assets = root.getAsJsonArray("assets");
            if (assets.size() == 0)
                throw new IOException("No assets found in release");

            JsonObject asset = assets.get(0).getAsJsonObject();
            String downloadUrl = asset.get("browser_download_url").getAsString();
            String expectedHash = asset.get("digest").getAsString().replace("sha256:", "");

            Files.createDirectories(destination.getParent());
            URL url = URI.create(downloadUrl).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "MineLights-Mod-Downloader/1.0");

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
                        updateStatusWidget(totalFileSize, totalBytesRead, currentSpeed);
                    }
                }
            }

            String actualHash = sha256(destination);
            if (!actualHash.equalsIgnoreCase(expectedHash)) {
                throw new IOException("Hash mismatch! Expected " + expectedHash + " but got " + actualHash);
            }

            status.set(Status.STARTING);
            this.statusMessage = new TranslatableText("minelights.gui.download.status.starting").getString();

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
                this.statusMessage = new TranslatableText("minelights.gui.download.status.success").getString();
            } else {
                throw new IOException("Server did not start within 10 seconds.");
            }

        } catch (Exception e) {
            MineLightsClient.LOGGER.error("Failed during download, verification, or server start", e);
            errorMessage.set(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            status.set(Status.FAILED);
            this.statusMessage = new TranslatableText("minelights.gui.download.status.failed", errorMessage.get())
                    .getString();
        } finally {
            //? if >=1.16 {
            /* if (this.client != null) {
             this.client.execute(() -> this.closeButton.active = true);
             }
            *///?} else {
            /* this.minecraft.execute(() -> this.closeButton.active = true);
            *///?}
        }
    }
}