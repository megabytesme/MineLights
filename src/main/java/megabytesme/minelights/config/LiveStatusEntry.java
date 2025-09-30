package megabytesme.minelights.config;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class LiveStatusEntry extends AbstractConfigListEntry<Text> {
    private final Supplier<Text> supplier;

    public LiveStatusEntry(String fieldName, Supplier<Text> supplier) {
        super(new LiteralText(fieldName), false);
        this.supplier = supplier;
    }

    @Override
    public Text getValue() {
        return supplier.get();
    }

    @Override
    public Optional<Text> getDefaultValue() {
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
        return 12;
    }

    @Override
    public List<? extends Element> children() {
        return Collections.emptyList();
    }

    @Override
    public void render(MatrixStack matrices, int index, int y, int x,
                       int entryWidth, int entryHeight,
                       int mouseX, int mouseY,
                       boolean isHovered, float delta) {
        Text current = supplier.get();
        MinecraftClient.getInstance().textRenderer.draw(matrices, current, x, y, 0xFFFFFF);
    }
}