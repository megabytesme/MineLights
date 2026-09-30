package megabytesme.minelights;

//? if loader_forge && <=1.7.10 {
import cpw.mods.fml.client.IModGuiFactory;
//?} else {
import net.minecraftforge.fml.client.IModGuiFactory;
//?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import java.util.Set;

public final class MineLightsLegacyGuiFactory implements IModGuiFactory {
    @Override
    public void initialize(Minecraft minecraftInstance) {
    }

    //? if loader_forge && <=1.11.2 {
    @Override
    public Class<? extends GuiScreen> mainConfigGuiClass() {
        return MineLightsLegacyConfigScreen.class;
    }

    @Override
    public RuntimeOptionGuiHandler getHandlerFor(RuntimeOptionCategoryElement element) {
        return null;
    }
    //?}
    //? if loader_forge && >=1.11.2 {
    @Override
    public boolean hasConfigGui() {
        return true;
    }

    @Override
    public GuiScreen createConfigGui(GuiScreen parentScreen) {
        return new MineLightsLegacyConfigScreen(parentScreen);
    }
    //?}

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null;
    }
}
