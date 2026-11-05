package megabytesme.minelights.config;

//? if >=26.1 {
import com.mojang.blaze3d.platform.InputConstants;
import me.shedaniel.clothconfig2.api.ModifierKeyCode;
import me.shedaniel.clothconfig2.gui.AbstractConfigScreen;
import me.shedaniel.clothconfig2.gui.entries.KeyCodeEntry;
import me.shedaniel.clothconfig2.gui.entries.TooltipListEntry;
import megabytesme.minelights.effects.KeyNames;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Picks keys by pressing them, using a hidden KeyCodeEntry as the screen's focused binding. */
public class KeyListEntry extends TooltipListEntry<List<String>> {
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private final List<String> original;
    private final List<String> defaults;
    private final List<String> keys;
    private final int maxKeys;
    private final Button addButton;
    private final Button undoButton;
    private final Button clearButton;
    private final Button resetButton;
    private final List<Button> widgets;
    private final CaptureSlot captureSlot;
    private boolean capturing = false;
    private Component notice = null;

    public KeyListEntry(Component fieldName, List<String> value, List<String> defaults, int maxKeys,
            Component[] tooltip, Consumer<List<String>> saveConsumer) {
        super(fieldName, () -> Optional.of(tooltip));
        this.original = new ArrayList<>(value);
        this.defaults = new ArrayList<>(defaults);
        this.keys = new ArrayList<>(value);
        this.maxKeys = maxKeys;
        this.saveCallback = saveConsumer;

        this.captureSlot = new CaptureSlot(fieldName);
        this.addButton = Button.builder(Component.empty(), b -> {
            capturing = true;
            notice = null;
            listen();
        }).bounds(0, 0, 76, 20).build();
        this.undoButton = Button.builder(Component.translatable("text.mine-lights.keys.undo"), b -> {
            if (!keys.isEmpty()) {
                keys.remove(keys.size() - 1);
            }
            notice = null;
        }).bounds(0, 0, 40, 20).build();
        this.clearButton = Button.builder(Component.translatable("text.mine-lights.keys.clear"), b -> {
            keys.clear();
            notice = null;
        }).bounds(0, 0, 40, 20).build();
        this.resetButton = Button.builder(Component.translatable("text.mine-lights.keys.reset"), b -> {
            keys.clear();
            keys.addAll(this.defaults);
            capturing = false;
            notice = null;
        }).bounds(0, 0, 40, 20).build();
        this.widgets = Arrays.asList(addButton, undoButton, clearButton, resetButton);
    }

    @Override
    public List<String> getValue() {
        return new ArrayList<>(keys);
    }

    @Override
    public Optional<List<String>> getDefaultValue() {
        return Optional.of(new ArrayList<>(defaults));
    }

    @Override
    public boolean isEdited() {
        return super.isEdited() || !keys.equals(original);
    }

    @Override
    public int getItemHeight() {
        return 46;
    }

    private void listen() {
        AbstractConfigScreen screen = getConfigScreen();
        if (screen != null) {
            screen.setFocusedBinding(captureSlot);
        }
    }

    private void onKeyPicked(ModifierKeyCode picked) {
        if (picked == null || picked.isUnknown()) {
            capturing = false;
            return;
        }
        InputConstants.Key key = picked.getKeyCode();
        //? if >=26.3 {
        String name = key.getType() == InputConstants.Type.KEYBOARD ? KeyNames.fromGlfw(key.getValue()) : null;
        //?} else {
        /*String name = key.getType() == InputConstants.Type.KEYSYM ? KeyNames.fromGlfw(key.getValue()) : null;
        *///?}
        if (name == null) {
            notice = Component.translatable("text.mine-lights.keys.unknown", key.getDisplayName().getString())
                    .withStyle(ChatFormatting.RED);
        } else if (maxKeys == 1) {
            keys.clear();
            keys.add(name);
            capturing = false;
            notice = null;
        } else if (keys.contains(name)) {
            notice = Component.translatable("text.mine-lights.keys.duplicate", name).withStyle(ChatFormatting.YELLOW);
        } else {
            keys.add(name);
            notice = null;
            if (keys.size() >= maxKeys) {
                capturing = false;
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int index, int y, int x, int entryWidth,
            int entryHeight, int mouseX, int mouseY, boolean isHovered, float delta) {
        super.extractRenderState(context, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, delta);
        AbstractConfigScreen screen = getConfigScreen();
        if (capturing && screen != null && screen.getFocusedBinding() == null) {
            listen();
        }
        if (capturing && screen != null && screen.getFocusedBinding() != captureSlot) {
            capturing = false;
        }
        Font font = Minecraft.getInstance().font;

        boolean editable = isEditable();
        addButton.active = editable;
        undoButton.active = editable && !keys.isEmpty();
        clearButton.active = editable && !keys.isEmpty();
        resetButton.active = editable && !keys.equals(defaults);
        addButton.setMessage(capturing
                ? Component.translatable("text.mine-lights.keys.listening").withStyle(ChatFormatting.YELLOW)
                : Component.translatable(maxKeys == 1 ? "text.mine-lights.keys.set" : "text.mine-lights.keys.add"));

        int buttonX = x + entryWidth;
        for (int i = widgets.size() - 1; i >= 0; i--) {
            Button widget = widgets.get(i);
            buttonX -= widget.getWidth() + 2;
            widget.setX(buttonX);
            widget.setY(y);
            widget.extractRenderState(context, mouseX, mouseY, delta);
        }

        context.text(font, getDisplayedFieldName(), x, y + 6, getPreferredTextColor());

        Component line;
        if (notice != null) {
            line = notice;
        } else if (capturing) {
            line = Component.translatable("text.mine-lights.keys.capturing", keysText()).withStyle(ChatFormatting.YELLOW);
        } else if (keys.isEmpty()) {
            line = Component.translatable("text.mine-lights.keys.none").withStyle(ChatFormatting.DARK_GRAY);
        } else {
            line = Component.literal(keysText()).withStyle(ChatFormatting.AQUA);
        }
        String shown = font.plainSubstrByWidth(line.getString(), entryWidth);
        context.text(font, Component.literal(shown).setStyle(line.getStyle()), x, y + 28, TEXT_COLOR);
    }

    private String keysText() {
        return String.join("  ", keys);
    }

    private class CaptureSlot extends KeyCodeEntry {
        CaptureSlot(Component fieldName) {
            super(fieldName, ModifierKeyCode.unknown(), Component.empty(), ModifierKeyCode::unknown, v -> {
            }, Optional::empty, false);
            setAllowModifiers(false);
            setAllowMouse(false);
        }

        @Override
        public void setValue(ModifierKeyCode value) {
            super.setValue(ModifierKeyCode.unknown());
            onKeyPicked(value);
        }
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return widgets;
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
        return widgets;
    }
}
//?} else if >=1.16 {
/*import me.shedaniel.clothconfig2.api.ModifierKeyCode;
import me.shedaniel.clothconfig2.gui.AbstractConfigScreen;
import me.shedaniel.clothconfig2.gui.entries.KeyCodeEntry;
import me.shedaniel.clothconfig2.gui.entries.TooltipListEntry;
import megabytesme.minelights.effects.KeyNames;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
//? if >=1.20 {
import net.minecraft.client.gui.DrawContext;
//?} else {
import net.minecraft.client.util.math.MatrixStack;
//?}
import net.minecraft.client.gui.Element;
//? if >=1.16.3 {
import net.minecraft.client.gui.Selectable;
//?}
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.InputUtil;
//? if <1.19 {
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
//?}
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/^* Picks keys by pressing them, using a hidden KeyCodeEntry as the screen's focused binding. ^/
public class KeyListEntry extends TooltipListEntry<List<String>> {
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private final List<String> original;
    private final List<String> defaults;
    private final List<String> keys;
    private final int maxKeys;
    private final ButtonWidget addButton;
    private final ButtonWidget undoButton;
    private final ButtonWidget clearButton;
    private final ButtonWidget resetButton;
    private final List<ButtonWidget> widgets;
    private final CaptureSlot captureSlot;
    //? if <1.19 {
    private final Consumer<List<String>> saveConsumer;
    //?}
    private boolean capturing = false;
    private Text notice = null;

    public KeyListEntry(Text fieldName, List<String> value, List<String> defaults, int maxKeys, Text[] tooltip,
            Consumer<List<String>> saveConsumer) {
        super(fieldName, () -> Optional.of(tooltip));
        this.original = new ArrayList<>(value);
        this.defaults = new ArrayList<>(defaults);
        this.keys = new ArrayList<>(value);
        this.maxKeys = maxKeys;
        //? if >=1.19 {
        this.saveCallback = saveConsumer;
        //?} else {
        this.saveConsumer = saveConsumer;
        //?}

        this.captureSlot = new CaptureSlot(fieldName);
        this.addButton = button(literal(""), 76, b -> {
            capturing = true;
            notice = null;
            listen();
        });
        this.undoButton = button(translatable("text.mine-lights.keys.undo"), 40, b -> {
            if (!keys.isEmpty()) {
                keys.remove(keys.size() - 1);
            }
            notice = null;
        });
        this.clearButton = button(translatable("text.mine-lights.keys.clear"), 40, b -> {
            keys.clear();
            notice = null;
        });
        this.resetButton = button(translatable("text.mine-lights.keys.reset"), 40, b -> {
            keys.clear();
            keys.addAll(this.defaults);
            capturing = false;
            notice = null;
        });
        this.widgets = Arrays.asList(addButton, undoButton, clearButton, resetButton);
    }

    private static ButtonWidget button(Text message, int width, ButtonWidget.PressAction onPress) {
        //? if >=1.19.3 {
        return ButtonWidget.builder(message, onPress).dimensions(0, 0, width, 20).build();
        //?} else {
        return new ButtonWidget(0, 0, width, 20, message, onPress);
        //?}
    }

    private static MutableText translatable(String key, Object... args) {
        //? if >=1.19 {
        return Text.translatable(key, args);
        //?} else {
        return new TranslatableText(key, args);
        //?}
    }

    private static MutableText literal(String text) {
        //? if >=1.19 {
        return Text.literal(text);
        //?} else {
        return new LiteralText(text);
        //?}
    }

    @Override
    public List<String> getValue() {
        return new ArrayList<>(keys);
    }

    @Override
    public Optional<List<String>> getDefaultValue() {
        return Optional.of(new ArrayList<>(defaults));
    }

    @Override
    public boolean isEdited() {
        return super.isEdited() || !keys.equals(original);
    }

    //? if <1.19 {
    @Override
    public void save() {
        saveConsumer.accept(getValue());
    }
    //?}

    @Override
    public int getItemHeight() {
        return 46;
    }

    private void listen() {
        AbstractConfigScreen screen = getConfigScreen();
        if (screen != null) {
            screen.setFocusedBinding(captureSlot);
        }
    }

    private void onKeyPicked(ModifierKeyCode picked) {
        if (picked == null || picked.isUnknown()) {
            capturing = false;
            return;
        }
        InputUtil.Key key = picked.getKeyCode();
        String name = key.getCategory() == InputUtil.Type.KEYSYM ? KeyNames.fromGlfw(key.getCode()) : null;
        if (name == null) {
            notice = translatable("text.mine-lights.keys.unknown", key.getLocalizedText().getString())
                    .formatted(Formatting.RED);
        } else if (maxKeys == 1) {
            keys.clear();
            keys.add(name);
            capturing = false;
            notice = null;
        } else if (keys.contains(name)) {
            notice = translatable("text.mine-lights.keys.duplicate", name).formatted(Formatting.YELLOW);
        } else {
            keys.add(name);
            notice = null;
            if (keys.size() >= maxKeys) {
                capturing = false;
            }
        }
    }

    @Override
    //? if >=1.20 {
    public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX,
            int mouseY, boolean isHovered, float delta) {
    //?} else {
    public void render(MatrixStack context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX,
            int mouseY, boolean isHovered, float delta) {
    //?}
        super.render(context, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, delta);
        AbstractConfigScreen screen = getConfigScreen();
        if (capturing && screen != null && screen.getFocusedBinding() == null) {
            listen();
        }
        if (capturing && screen != null && screen.getFocusedBinding() != captureSlot) {
            capturing = false;
        }
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        boolean editable = isEditable();
        addButton.active = editable;
        undoButton.active = editable && !keys.isEmpty();
        clearButton.active = editable && !keys.isEmpty();
        resetButton.active = editable && !keys.equals(defaults);
        addButton.setMessage(capturing
                ? translatable("text.mine-lights.keys.listening").formatted(Formatting.YELLOW)
                : translatable(maxKeys == 1 ? "text.mine-lights.keys.set" : "text.mine-lights.keys.add"));

        int buttonX = x + entryWidth;
        for (int i = widgets.size() - 1; i >= 0; i--) {
            ButtonWidget widget = widgets.get(i);
            buttonX -= widget.getWidth() + 2;
            //? if >=1.19.3 {
            widget.setX(buttonX);
            widget.setY(y);
            //?} else {
            widget.x = buttonX;
            widget.y = y;
            //?}
            widget.render(context, mouseX, mouseY, delta);
        }

        Text line;
        if (notice != null) {
            line = notice;
        } else if (capturing) {
            line = translatable("text.mine-lights.keys.capturing", keysText()).formatted(Formatting.YELLOW);
        } else if (keys.isEmpty()) {
            line = translatable("text.mine-lights.keys.none").formatted(Formatting.DARK_GRAY);
        } else {
            line = literal(keysText()).formatted(Formatting.AQUA);
        }
        Text shown = literal(textRenderer.trimToWidth(line.getString(), entryWidth)).setStyle(line.getStyle());
        //? if >=1.20 {
        context.drawTextWithShadow(textRenderer, getDisplayedFieldName(), x, y + 6, getPreferredTextColor());
        context.drawTextWithShadow(textRenderer, shown, x, y + 28, TEXT_COLOR);
        //?} else {
        textRenderer.drawWithShadow(context, getDisplayedFieldName(), x, y + 6, getPreferredTextColor());
        textRenderer.drawWithShadow(context, shown, x, y + 28, TEXT_COLOR);
        //?}
    }

    private String keysText() {
        return String.join("  ", keys);
    }

    private class CaptureSlot extends KeyCodeEntry {
        CaptureSlot(Text fieldName) {
            super(fieldName, ModifierKeyCode.unknown(), literal(""), ModifierKeyCode::unknown, v -> {
            }, Optional::empty, false);
            setAllowModifiers(false);
            setAllowMouse(false);
        }

        @Override
        public void setValue(ModifierKeyCode value) {
            super.setValue(ModifierKeyCode.unknown());
            onKeyPicked(value);
        }
    }

    @Override
    public List<? extends Element> children() {
        return widgets;
    }

    //? if >=1.16.3 {
    @Override
    public List<? extends Selectable> narratables() {
        return widgets;
    }
    //?}
}
*///?}
