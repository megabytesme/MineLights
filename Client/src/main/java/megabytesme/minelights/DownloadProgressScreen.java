package megabytesme.minelights;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.io.FileOutputStream;
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
        DOWNLOADING, SUCCESS, FAILED
    }

    private final AtomicReference<Status> status = new AtomicReference<>(Status.DOWNLOADING);
    private final AtomicInteger progress = new AtomicInteger(0);
    private final AtomicReference<String> errorMessage = new AtomicReference<>("");

    private ButtonWidget closeButton;

    public DownloadProgressScreen(Screen parent, String downloadUrl, Path destination) {
        super(Text.translatable("minelights.gui.download.progress_title"));
        this.parent = parent;
        this.downloadUrl = downloadUrl;
        this.destination = destination;

        new Thread(this::downloadFile, "MineLights-Downloader").start();
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

        Text statusText;
        if (status.get() == Status.DOWNLOADING) {
            statusText = Text.translatable("minelights.gui.download.status.downloading", progress.get());
        } else if (status.get() == Status.SUCCESS) {
            statusText = Text.translatable("minelights.gui.download.status.success");
        } else {
            statusText = Text.translatable("minelights.gui.download.status.failed", errorMessage.get());
        }
        context.drawCenteredTextWithShadow(this.textRenderer, statusText, this.width / 2, this.height / 2 - 10,
                0xFFFFFF);

        int barWidth = 200;
        int barHeight = 8;
        int barX = this.width / 2 - barWidth / 2;
        int barY = this.height / 2 + 4;

        int fillWidth = (int) (barWidth * (this.progress.get() / 100.0f));

        context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF303030);
        context.fill(barX, barY, barX + fillWidth, barY + barHeight, 0xFFFFFFFF);
    }

    private void downloadFile() {
        try {
            Files.createDirectories(destination.getParent());

            URL url = URI.create(downloadUrl).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "MineLights-Mod-Downloader/1.0");
            connection.connect();

            long totalFileSize = connection.getContentLengthLong();
            long totalBytesRead = 0;

            try (InputStream inputStream = connection.getInputStream();
                    FileOutputStream outputStream = new FileOutputStream(destination.toFile())) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;

                    if (totalFileSize > 0) {
                        progress.set((int) ((totalBytesRead * 100) / totalFileSize));
                    }
                }
            }

            status.set(Status.SUCCESS);
        } catch (Exception e) {
            MineLightsClient.LOGGER.error("Failed to download server executable", e);
            errorMessage.set(e.getClass().getSimpleName());
            status.set(Status.FAILED);
        } finally {
            this.closeButton.active = true;
        }
    }
}