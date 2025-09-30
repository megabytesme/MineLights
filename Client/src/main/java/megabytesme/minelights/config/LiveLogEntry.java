package megabytesme.minelights.config;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.Element;
//? if >=1.16.3 {
import net.minecraft.client.gui.Selectable;
//?}
//? if >=1.15 {
import net.minecraft.client.util.math.MatrixStack;
//?}
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.MathHelper;

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
        //? if >=1.16 {
        super(new LiteralText(fieldName), false);
        //?}
        //? if <1.16 {
        /*
        super(fieldName, false);
        */
        //?}
        this.logLines = logLines;
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int maxScroll = Math.max(0, (wrappedLinesCache.size() * 10) - getItemHeight());
        scrollY -= (int) (amount * 10);
        scrollY = MathHelper.clamp(scrollY, 0, maxScroll);
        return true;
    }

    @Override
    public String getValue() { return ""; }

    @Override
    public Optional<String> getDefaultValue() { return Optional.empty(); }

    @Override
    public void save() {}

    @Override
    public boolean isRequiresRestart() { return false; }

    @Override
    public void setRequiresRestart(boolean requiresRestart) {}

    @Override
    public int getItemHeight() { return 120; }

    @Override
    public List<? extends Element> children() { return Collections.emptyList(); }

    //? if >=1.16.3 {
    @Override
    public List<? extends Selectable> narratables() {
        return Collections.emptyList();
    }
    //?}

    //? if >=1.16 {
    @Override
    public void render(MatrixStack matrices, int index, int y, int x,
                       int entryWidth, int entryHeight,
                       int mouseX, int mouseY,
                       boolean isHovered, float delta) {
        renderLog(matrices, x, y, entryWidth, entryHeight);
    }
    //?}

    //? if <=1.15 {
    /*
    @Override
    public void render(int index, int y, int x,
                       int entryWidth, int entryHeight,
                       int mouseX, int mouseY,
                       boolean isHovered, float delta) {
        renderLog(null, x, y, entryWidth, entryHeight);
    }
    */
    //?}

    private void renderLog(Object matrices, int x, int y, int entryWidth, int entryHeight) {
        if (entryWidth != lastEntryWidth) {
            lastEntryWidth = entryWidth;
            wrappedLinesCache.clear();
            synchronized (logLines) {
                for (String raw : logLines) {
                    wrappedLinesCache.addAll(wrapString(raw, entryWidth - 15));
                }
            }
        }

        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        int maxScroll = Math.max(0, (wrappedLinesCache.size() * 10) - entryHeight);
        scrollY = MathHelper.clamp(scrollY, 0, maxScroll);

        int startX = x + 2;
        for (int i = 0; i < wrappedLinesCache.size(); i++) {
            int lineY = y + 2 + (i * 10) - scrollY;
            if (lineY >= y && lineY < y + entryHeight - 5) {
                String part = wrappedLinesCache.get(i);
                //? if >=1.16 {
                textRenderer.draw((MatrixStack) matrices, part, startX, lineY, 0xFFFFFF);
                //?}
                //? if <1.16 {
                /*
                textRenderer.draw(part, (float) startX, (float) lineY, 0xFFFFFF);
                */
                //?}
            }
        }

        if (maxScroll > 0) {
            int scrollbarX = x + entryWidth - 6;
            int scrollbarHeight = entryHeight;
            int thumbHeight = Math.max(10, (int) ((scrollbarHeight / (float) (wrappedLinesCache.size() * 10)) * scrollbarHeight));
            int thumbY = y + (int) (((float) scrollY / maxScroll) * (scrollbarHeight - thumbHeight));

            //? if >=1.16 {
            DrawableHelper.fill((MatrixStack) matrices, scrollbarX, y, scrollbarX + 5, y + scrollbarHeight, 0xFF000000);
            DrawableHelper.fill((MatrixStack) matrices, scrollbarX, thumbY, scrollbarX + 5, thumbY + thumbHeight, 0xFF888888);
            //?}
            //? if <=1.15 {
            /*
            DrawableHelper.fill(scrollbarX, y, scrollbarX + 5, y + scrollbarHeight, 0xFF000000);
            DrawableHelper.fill(scrollbarX, thumbY, scrollbarX + 5, thumbY + thumbHeight, 0xFF888888);
            */
            //?}
        }
    }

    private List<String> wrapString(String text, int maxWidth) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        List<String> lines = new ArrayList<>();
        if (maxWidth <= 10 || text.isEmpty()) {
            lines.add(text);
            return lines;
        }

        StringBuilder currentLine = new StringBuilder();
        String[] words = text.split(" ");
        for (String word : words) {
            String potentialLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            
            //? if >=1.16 {
            int width = tr.getWidth(potentialLine);
            //?}
            //? if <1.16 {
            /*
            int width = tr.getStringWidth(potentialLine);
            */
            //?}

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