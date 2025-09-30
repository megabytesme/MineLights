package megabytesme.minelights.config;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Element;
//? if >=1.16.3 {
import net.minecraft.client.gui.Selectable;
//?}
//? if >=1.16 {
import net.minecraft.client.util.math.MatrixStack;
//?}
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class LiveLogEntry extends AbstractConfigListEntry<String> {
    private final List<String> logLines;

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
        renderLog(matrices, x, y, entryWidth);
    }
    //?}

    //? if <1.16 {
    /*
    @Override
    public void render(int index, int y, int x,
                       int entryWidth, int entryHeight,
                       int mouseX, int mouseY,
                       boolean isHovered, float delta) {
        renderLog(null, x, y, entryWidth);
    }
    */
    //?}

    private void renderLog(Object matrices, int x, int y, int entryWidth) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        int maxLines = 50;
        int startX = x + 2;
        int startY = y + 2;

        synchronized (logLines) {
            int lineCount = 0;
            for (int i = logLines.size() - 1; i >= 0 && lineCount < maxLines; i--) {
                String raw = logLines.get(i);
                List<String> wrapped = wrapString(raw, entryWidth - 10);

                for (int j = 0; j < wrapped.size() && lineCount < maxLines; j++) {
                    String part = wrapped.get(j);
                    int currentY = startY + (lineCount * 10);
                    //? if >=1.16 {
                    textRenderer.draw((MatrixStack) matrices, part, startX, currentY, 0xFFFFFF);
                    //? }
                    //? if <1.16 {
                    /*
                    textRenderer.draw(part, (float) startX, (float) currentY, 0xFFFFFF);
                    */
                    //? }
                    lineCount++;
                }
            }
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