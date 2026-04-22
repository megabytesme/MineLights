package megabytesme.minelights.config;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class LiveStatusEntry extends AbstractConfigListEntry<Component> {
    private final Supplier<Component> supplier;

    public LiveStatusEntry(String fieldName, Supplier<Component> supplier) {
        super(Component.literal(fieldName), false);
        this.supplier = supplier;
    }

    @Override
    public Component getValue() {
        return supplier.get();
    }

    @Override
    public Optional<Component> getDefaultValue() {
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
        Component current = supplier.get();
        context.drawString(Minecraft.getInstance().font, current, x + 2, y + 2, 0xFFFFFF, false);
    }
}
