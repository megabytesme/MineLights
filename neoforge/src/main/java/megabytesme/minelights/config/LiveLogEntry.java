package megabytesme.minelights.config;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class LiveLogEntry extends AbstractConfigListEntry<String> {
    private final List<String> logLines;
    private int scrollY = 0;
    private int lastEntryWidth = 0;
    private List<String> wrappedLinesCache = new ArrayList<>();

    public LiveLogEntry(String fieldName, List<String> logLines) {
        super(Component.literal(fieldName), false);
        this.logLines = logLines;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = Math.max(0, (wrappedLinesCache.size() * 10) - getItemHeight());
        scrollY -= (int) (verticalAmount * 10);
        scrollY = Mth.clamp(scrollY, 0, maxScroll);
        return true;
    }

    @Override
    public String getValue() {
        return "";
    }

    @Override
    public Optional<String> getDefaultValue() {
        return Optional.empty();
    }

    @Override
    public void save() {
    }

    @Override
    public boolean isRequiresRestart() {
        return false;
    }

    @Override
    public void setRequiresRestart(boolean requiresRestart) {
    }

    @Override
    public int getItemHeight() {
        return 120;
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return Collections.emptyList();
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
        return Collections.emptyList();
    }

    @Override
    public void render(GuiGraphics context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX,
                       int mouseY, boolean isHovered, float delta) {
        renderLog(context, x, y, entryWidth, entryHeight);
    }

    private void renderLog(GuiGraphics context, int x, int y, int entryWidth, int entryHeight) {
        if (entryWidth != lastEntryWidth) {
            lastEntryWidth = entryWidth;
            wrappedLinesCache.clear();
            synchronized (logLines) {
                for (String raw : logLines) {
                    wrappedLinesCache.addAll(wrapString(raw, entryWidth - 15));
                }
            }
        }

        int maxScroll = Math.max(0, (wrappedLinesCache.size() * 10) - entryHeight);
        scrollY = Mth.clamp(scrollY, 0, maxScroll);

        int startX = x + 2;
        for (int i = 0; i < wrappedLinesCache.size(); i++) {
            int lineY = y + 2 + (i * 10) - scrollY;
            if (lineY >= y && lineY < y + entryHeight - 5) {
                context.drawString(Minecraft.getInstance().font, wrappedLinesCache.get(i), startX, lineY, 0xFFFFFF, false);
            }
        }

        if (maxScroll > 0) {
            int scrollbarX = x + entryWidth - 6;
            int scrollbarHeight = entryHeight;
            int thumbHeight = Math.max(10, (int) ((scrollbarHeight / (float) (wrappedLinesCache.size() * 10)) * scrollbarHeight));
            int thumbY = y + (int) (((float) scrollY / maxScroll) * (scrollbarHeight - thumbHeight));

            context.fill(scrollbarX, y, scrollbarX + 5, y + scrollbarHeight, 0xFF000000);
            context.fill(scrollbarX, thumbY, scrollbarX + 5, thumbY + thumbHeight, 0xFF888888);
        }
    }

    private List<String> wrapString(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (maxWidth <= 10 || text.isEmpty()) {
            lines.add(text);
            return lines;
        }

        StringBuilder currentLine = new StringBuilder();
        String[] words = text.split(" ");
        for (String word : words) {
            String potentialLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            int width = Minecraft.getInstance().font.width(potentialLine);

            if (width > maxWidth && currentLine.length() > 0) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            } else {
                currentLine = new StringBuilder(potentialLine);
            }
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines;
    }
}
