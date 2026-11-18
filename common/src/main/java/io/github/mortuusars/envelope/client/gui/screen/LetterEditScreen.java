package io.github.mortuusars.envelope.client.gui.screen;

import io.github.mortuusars.envelope.Config;
import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.client.gui.widget.textbox.TextBox;
import io.github.mortuusars.envelope.client.gui.widget.textbox.text.FormattedString;
import io.github.mortuusars.envelope.lib.mortaar.client.Minecrft;
import io.github.mortuusars.envelope.network.packet.serverbound.ServerboundLetterEditPacket;
import io.github.mortuusars.envelope.world.item.LetterAndQuillItem;
import io.github.mortuusars.envelope.lib.mortaar.client.gui.Sprites;
import io.github.mortuusars.envelope.lib.mortaar.util.ItemAndStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

public class LetterEditScreen extends Screen {
    public static final Identifier TEXTURE = Envelope.resource("textures/gui/letter_and_quill.png");
    public static final WidgetSprites FOLD_BUTTON_SPRITES = Sprites.threeStates(Envelope.resource("letter_and_quill/fold_button"));

    protected final ItemAndStack<LetterAndQuillItem> letter;
    protected final InteractionHand hand;

    protected int imageWidth, imageHeight, leftPos, topPos;
    protected TextBox textBox;
    protected ImageButton foldButton;

    public LetterEditScreen(ItemStack letter, InteractionHand hand) {
        super(Component.empty());
        this.letter = new ItemAndStack<>(letter);
        this.hand = hand;
    }

    @Override
    public boolean isPauseScreen() {
        return Config.Server.LETTER_PAUSE.get();
    }

    @Override
    protected void init() {
        imageWidth = 176;
        imageHeight = 192;
        leftPos = (width - imageWidth) / 2;
        topPos = (height - imageHeight) / 2;

        textBox = addRenderableWidget(new TextBox(font, leftPos + 17, topPos + 21, 142, 144)
              .setFontColor(0xFF7B593D)
              .setFontUnfocusedColor(0xFF7B593D)
              .setSelectionColor(0xFF664488)
              .setSelectionUnfocusedColor(0xFF696170)
              .setHintColor(0xFFC2A57F)
              .setText(FormattedString.parse(letter.map(LetterAndQuillItem::getContent).text())));

        foldButton = addRenderableWidget(new ImageButton(
              leftPos + imageWidth + 5, topPos + 144,
              18, 18,
              FOLD_BUTTON_SPRITES,
              btn -> {
                  saveChanges(true);
                  Minecrft.get().setScreen(null);
              },
              Component.translatable("envelope.letter_and_quill.fold_button")));
        foldButton.setTooltip(Tooltip.create(Component.translatable("envelope.letter_and_quill.fold_button")
              .append(CommonComponents.NEW_LINE)
              .append(Component.translatable("envelope.letter_and_quill.fold_warning").withStyle(ChatFormatting.GRAY))));
    }

    // 1.21.11: see AddressTagScreen#setInitialFocus
    @Override
    protected void setInitialFocus() {
        setInitialFocus(textBox);
    }

    @Override
    public void resize(int width, int height) {
        // Prevent contents reset when window is resized
        FormattedString message = textBox.getEditor().getText();
        int messageCursorPos = textBox.getEditor().getCursorPos();
        super.resize(width, height);
        textBox.getEditor().setText(message);
        textBox.getEditor().setCursorPos(messageCursorPos, false);
    }

    // -- Render

    // 1.21.11: a screen over the game (not a menu) says so; Screen#renderBackground then draws the transparent
    // background and the subtitles that the HUD defers to it. Same as vanilla's BookEditScreen and sign screens.
    @Override
    public boolean isInGameUi() {
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
    }

    // -- Input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) { // 1.21.11: MouseButtonEvent
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        // Special handling for toolbar, without this clicks will go to another element under the cursor,
        // if toolbar is over that element.
        if (getFocused() instanceof TextBox box && box.formattingToolbarMouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) { // 1.21.11: KeyEvent instead of raw ints
        int keyCode = event.key();
        if (!(getFocused() instanceof TextBox)) {
            if (Minecraft.getInstance().options.keyInventory.matches(event)) {
                this.onClose();
                return true;
            }
        }

        return super.keyPressed(event);
    }

    @Override
    public void setFocused(@Nullable GuiEventListener focused) {
        @Nullable GuiEventListener lastFocused = getFocused();
        super.setFocused(focused);
        // Clear selection if focus changes:
        if (lastFocused != null && !lastFocused.equals(getFocused()) && lastFocused instanceof TextBox box) {
            box.getEditor().clearSelection();
            box.getDisplayCache().scheduleUpdate();
        }
    }

    // --

    @Override
    public void onClose() {
        super.onClose();
        saveChanges(false);
    }

    protected void saveChanges(boolean fold) {
        String text = textBox.getEditor().getText().toString();

        int slot = this.hand == InteractionHand.MAIN_HAND ? Minecrft.player().getInventory().getSelectedSlot() : Inventory.SLOT_OFFHAND;
        new ServerboundLetterEditPacket(slot, text, fold).sendToServer();
    }
}
