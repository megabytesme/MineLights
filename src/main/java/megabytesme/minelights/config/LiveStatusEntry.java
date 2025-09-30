package megabytesme.minelights.config;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
//? if >=1.16.3 {
import net.minecraft.client.gui.Selectable;
//?}
//? if >=1.16 {
import net.minecraft.client.util.math.MatrixStack;
//?}
//? if <1.19 {
/*import net.minecraft.text.LiteralText;
*///?}
import net.minecraft.text.Text;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class LiveStatusEntry extends AbstractConfigListEntry<Text> {
    private final Supplier<Text> supplier;

    public LiveStatusEntry(String fieldName, Supplier<Text> supplier) {
        //? if >=1.19 {
        super(Text.literal(fieldName), false);
        //?}
        //? if >=1.16 {
        /*super(new LiteralText(fieldName), false);
        *///?}
        //? if <1.16 {
        /*
        super(fieldName, false);
        */
        //?}
        this.supplier = supplier;
    }

    @Override
    public Text getValue() { return supplier.get(); }

    @Override
    public Optional<Text> getDefaultValue() { return Optional.empty(); }

    @Override
    public void save() {}

    @Override
    public boolean isRequiresRestart() { return false; }

    @Override
    public void setRequiresRestart(boolean requiresRestart) {}

    @Override
    public int getItemHeight() { return 12; }

    @Override
    public List<? extends Element> children() {
        return Collections.emptyList();
    }

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
        Text current = supplier.get();
        MinecraftClient.getInstance().textRenderer.draw(matrices, current, x + 2, y + 2, 0xFFFFFF);
    }
    //?}
    
    //? if <1.16 {
    /*
    @Override
    public void render(int index, int y, int x,
                       int entryWidth, int entryHeight,
                       int mouseX, int mouseY,
                       boolean isHovered, float delta) {
        Text current = supplier.get();
        MinecraftClient.getInstance().textRenderer.draw(current.asFormattedString(), (float) x + 2, (float) y + 2, 0xFFFFFF);
    }
    */
    //?}
}