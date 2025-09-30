package megabytesme.minelights.config;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Element;
//? if >=1.16 {
import net.minecraft.client.util.math.MatrixStack;
//? }
import net.minecraft.text.LiteralText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class LiveLogEntry extends AbstractConfigListEntry<String> {
    private final List<String> logLines;

    public LiveLogEntry(String fieldName, List<String> logLines) {
        //? if >=1.16 {
        super(new LiteralText(fieldName), false);
        //? } else {
        /*
        super(new LiteralText(fieldName).getString(), false);
        */
        //? }
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

    //? if >=1.16 {
    @Override
    public void render(MatrixStack matrices, int index, int y, int x,
                    int entryWidth, int entryHeight,
                    int mouseX, int mouseY,
                    boolean isHovered, float delta) {
    //? } else {
    /*
    @Override
    public void render(int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean isHovered, float delta) {
    */
    //? }
        int lineY = y;
        int maxLines = 100;

        synchronized (logLines) {
            int shown = 0;
            for (String raw : logLines) {
                List<String> wrapped = wrapString(raw, entryWidth);
                for (String part : wrapped) {
                    if (shown >= maxLines) return;
                    //? if >=1.16 {
                    MinecraftClient.getInstance().textRenderer.draw(matrices, part, x, lineY, 0xFFFFFF);
                    //? } else {
                    /*
                    MinecraftClient.getInstance().textRenderer.draw(part, (float)x, (float)lineY, 0xFFFFFF);
                    */
                    //? }
                    lineY += 10;
                    shown++;
                }
            }
        }
    }

    private List<String> wrapString(String text, int maxWidth) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            //? if >=1.16 {
            if (tr.getWidth(current + word) > maxWidth) {
            //? } else {
            /*
            if (tr.getStringWidth(current + word) > maxWidth) {
            */
            //? }
                lines.add(current.toString());
                current = new StringBuilder();
            }
            if (current.length() > 0) current.append(" ");
            current.append(word);
        }
        if (current.length() > 0) lines.add(current.toString());
        return lines;
    }
}