package megabytesme.minelights.config;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.Minecraft;
//? if >=1.20 {
import net.minecraft.client.gui.GuiGraphics;
//?} else {
import com.mojang.blaze3d.vertex.PoseStack;
//?}
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
//? if >=1.19.4 && <=1.20.4 {
import net.minecraft.client.gui.narration.NarrationElementOutput;
//?}
//? if >=1.19.4 && <=1.20.4 {
import net.minecraft.client.gui.narration.NarratableEntry.NarrationPriority;
//?}
import net.minecraft.network.chat.Component;
//? if <1.19 {
import net.minecraft.network.chat.TextComponent;
//?}

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class LiveStatusEntry extends AbstractConfigListEntry<Component> {
    private final Supplier<Component> supplier;

    private static Component literal(String text) {
        //? if >=1.19 {
        return Component.literal(text);
        //?} else {
        /* return new TextComponent(text);
        *///?}
    }

    public LiveStatusEntry(String fieldName, Supplier<Component> supplier) {
        super(literal(fieldName), false);
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

    //? if <=1.20.4 {
    @Override
    public void setFocused(GuiEventListener listener) {
    }

    @Override
    public GuiEventListener getFocused() {
        return null;
    }

    @Override
    public boolean isDragging() {
        return false;
    }

    @Override
    public void setDragging(boolean dragging) {
    }
    //?}

    //? if >=1.19.4 && <=1.20.4 {
    @Override
    public NarrationPriority narrationPriority() {
        return NarrationPriority.NONE;
    }

    @Override
    public void updateNarration(NarrationElementOutput output) {
    }
    //?}

    @Override
    public List<? extends NarratableEntry> narratables() {
        return Collections.emptyList();
    }

    @Override
    //? if >=1.20 {
    public void render(GuiGraphics context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX,
                       int mouseY, boolean isHovered, float delta) {
        Component current = supplier.get();
        context.drawString(Minecraft.getInstance().font, current, x + 2, y + 2, 0xFFFFFF, false);
    }
    //?} else {
    /* public void render(PoseStack context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX,
                       int mouseY, boolean isHovered, float delta) {
        Component current = supplier.get();
        Minecraft.getInstance().font.draw(context, current, x + 2, y + 2, 0xFFFFFF);
    }
    *///?}
}
