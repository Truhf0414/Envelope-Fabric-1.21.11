package io.github.mortuusars.envelope.client.gui.screen;

import io.github.mortuusars.envelope.Config;
import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.lib.mortaar.client.Minecrft;
import io.github.mortuusars.envelope.network.packet.serverbound.ServerboundItemClosedPacket;
import io.github.mortuusars.envelope.world.item.LetterItem;
import io.github.mortuusars.envelope.world.item.component.LetterContent;
import io.github.mortuusars.envelope.lib.mortaar.util.ItemAndStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

public class LetterViewScreen extends Screen {
    public static final Identifier REGULAR_TEXTURE = Envelope.resource("textures/gui/letter.png");
    public static final Identifier TATTERED_TEXTURE = Envelope.resource("textures/gui/letter_tattered.png");
    public static final Identifier TATTERED_OVERLAY = Envelope.resource("textures/gui/letter_tattered_overlay.png");
    public static final Identifier SERVICE_TEXTURE = Envelope.resource("textures/gui/letter_service.png");

    protected final ItemAndStack<LetterItem> letter;
    protected final @Nullable InteractionHand hand;
    protected final boolean isTattered;
    protected final boolean isService;

    protected int imageWidth, imageHeight, leftPos, topPos;
    protected int maxTextWidth;
    protected int maxTextHeight;
    protected int maxTextLines;

    protected List<FormattedCharSequence> lines;

    public LetterViewScreen(ItemStack letter, @Nullable InteractionHand hand) {
        super(Component.empty());
        this.letter = new ItemAndStack<>(letter);
        this.hand = hand;
        this.isTattered = letter.is(Envelope.Items.TATTERED_LETTER.get());
        this.isService = letter.is(Envelope.Items.SERVICE_LETTER.get());
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
        maxTextWidth = 142;
        maxTextHeight = 144;
        maxTextLines = maxTextHeight / font.lineHeight;
        createLines(letter.getOrDefault(Envelope.DataComponents.LETTER_CONTENT, LetterContent.EMPTY).text());
    }

    protected void createLines(Component text) {
        lines = font.split(text, maxTextWidth);
        if (lines.size() > maxTextLines) {
            lines = new ArrayList<>(lines);
            int lastLineIndex = maxTextLines - 1;
            FormattedCharSequence ellipsis = CommonComponents.ELLIPSIS.getVisualOrderText();
            lines.set(lastLineIndex, FormattedCharSequence.composite(lines.get(lastLineIndex), ellipsis));
            lines.set(lastLineIndex + 1, FormattedCharSequence.composite(ellipsis, lines.get(lastLineIndex + 1)));
        }
    }

    // -- Render

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        final int x = leftPos + 17;
        final int y = topPos + 21;
        int textColor = isService ? 0xFF8A4F3C : 0xFF7B593D;

        for (int i = 0; i < Math.min(lines.size(), maxTextLines); i++) {
            guiGraphics.drawString(font, lines.get(i), x, y + i * font.lineHeight, textColor, false);
        }

        if (isTattered) {
            guiGraphics.pose().pushMatrix();
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, TATTERED_OVERLAY, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
            guiGraphics.pose().popMatrix();
        }

        @Nullable Style style = getComponentStyleAt(mouseX, mouseY);
        if (style != null && style.getHoverEvent() != null) {
            guiGraphics.renderComponentHoverEffect(font, style, mouseX, mouseY);
        } else {
            if (lines.size() > maxTextLines
                  && mouseX >= x && mouseX < x + maxTextWidth
                  && mouseY >= y && mouseY < y + maxTextHeight) {
                List<FormattedCharSequence> leftovers = lines.stream().skip(maxTextLines).toList();
                guiGraphics.setTooltipForNextFrame(font, leftovers, mouseX, mouseY);
            }
        }
    }

    // 1.21.11: a screen over the game (not a menu) says so; Screen#renderBackground then draws the transparent
    // background and the subtitles that the HUD defers to it. Same as vanilla's BookEditScreen and sign screens.
    @Override
    public boolean isInGameUi() {
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, getBackgroundTexture(), leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
    }

    public Identifier getBackgroundTexture() {
        if (isService) return SERVICE_TEXTURE;
        return isTattered ? TATTERED_TEXTURE : REGULAR_TEXTURE;
    }

    @Nullable
    public Style getComponentStyleAt(double mouseX, double mouseY) {
        if (lines.isEmpty()
              || mouseX < leftPos + 17 || mouseX >= leftPos + 17 + maxTextWidth
              || mouseY < topPos + 21 || mouseY >= topPos + 21 + maxTextHeight) {
            return null;
        }

        int x = (int) mouseX - (leftPos + 17);
        int y = (int) mouseY - (topPos + 21);

        int linesCount = Math.min(lines.size(), maxTextLines);
        if (y < font.lineHeight * linesCount + linesCount) {
            int clickedLine = y / font.lineHeight;
            if (clickedLine >= 0 && clickedLine < lines.size()) {
                FormattedCharSequence text = lines.get(clickedLine);
                return styleAtWidth(text, x);
            }

            return null;
        }

        return null;
    }

    @Override
    public void onClose() {
        super.onClose();
        if (hand != null) {
            int slot = this.hand == InteractionHand.MAIN_HAND ? Minecrft.player().getInventory().getSelectedSlot() : Inventory.SLOT_OFFHAND;
            new ServerboundItemClosedPacket(slot).sendToServer();
        }
    }

    // -- Input

    @Override
    public boolean keyPressed(KeyEvent event) { // 1.21.11: KeyEvent instead of raw ints
        int keyCode = event.key();
        if (Minecraft.getInstance().options.keyInventory.matches(event)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) { // 1.21.11: MouseButtonEvent
        double x = event.x();
        double y = event.y();
        int button = event.button();
        if (button == 0) {
            @Nullable Style style = getComponentStyleAt(x, y);
            if (handleComponentClicked(style)) {
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    /**
     * 1.21.11: StringSplitter#componentStyleAtWidth was removed. Same walk as 1.21.1: the style of the first
     * character that goes past the given width.
     */
    private @Nullable Style styleAtWidth(FormattedCharSequence text, int maxWidth) {
        Style[] result = new Style[1];
        int[] width = new int[1];
        text.accept((index, style, codePoint) -> {
            width[0] += font.width(FormattedCharSequence.codepoint(codePoint, style));
            if (width[0] > maxWidth) {
                result[0] = style;
                return false;
            }
            return true;
        });
        return result[0];
    }

    public boolean handleComponentClicked(@Nullable Style style) {
        if (style == null) {
            return false;
        }

        @Nullable ClickEvent clickEvent = style.getClickEvent();
        if (clickEvent == null) {
            return false;
        }

        // 1.21.11: Screen#handleComponentClicked was removed; screens call the default click handling themselves
        // (see vanilla BookViewScreen). Like 1.21.1, a shift-click is not treated as a click on the event.
        if (Minecraft.getInstance().hasShiftDown()) {
            return false;
        }

        defaultHandleGameClickEvent(clickEvent, Minecraft.getInstance(), this);
        if (clickEvent instanceof ClickEvent.RunCommand) {
            onClose();
        }

        return true;
    }
}
