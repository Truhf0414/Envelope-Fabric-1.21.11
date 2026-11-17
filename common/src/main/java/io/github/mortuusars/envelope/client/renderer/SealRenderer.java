package io.github.mortuusars.envelope.client.renderer;

import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.world.item.component.seal.*;
import io.github.mortuusars.envelope.lib.mortaar.client.Minecrft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * 1.21.11: GUI drawing is deferred, so the shader color and blend state can no longer be changed around draw calls
 * (RenderSystem.setShaderColor/enableBlend are gone). Tints are passed as the color argument of each blit instead,
 * and GUI_TEXTURED already blends.
 */
public class SealRenderer {
    public static final Identifier IRON_DIE_SPRITE = Envelope.resource("seal/die/iron");
    public static final Identifier SEAL_GLINT_SPRITE = Envelope.resource("seal/glint");
    public static final Identifier LOCK_SPRITE = Envelope.resource("seal/lock");
    public static final Identifier LOCKED_OVERLAY_SPRITE = Envelope.resource("seal/locked_overlay");

    public void render(Seal seal, GuiGraphics guiGraphics, int x, int y) {
        SealMaterial material = seal.material().value();
        ShadingPalette colors = material.impressionPalette();

        Identifier materialTexture = material.sprite();
        Identifier impressionTexture = seal.impression().value().sprite();

        // Background
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, materialTexture, x, y, 32, 32);

        // Side
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, impressionTexture, x + 1, y + 2, 32, 32, colors.side().tint());
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, impressionTexture, x, y + 2, 32, 32, colors.side().tint());
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, impressionTexture, x - 1, y + 2, 32, 32, colors.side().tint());
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, impressionTexture, x + 1, y + 1, 32, 32, colors.side().tint());
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, impressionTexture, x, y + 1, 32, 32, colors.side().tint());
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, impressionTexture, x - 1, y + 1, 32, 32, colors.side().tint());
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, impressionTexture, x + 1, y, 32, 32, colors.side().tint());
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, impressionTexture, x, y, 32, 32, colors.side().tint());
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, impressionTexture, x - 1, y, 32, 32, colors.side().tint());
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, impressionTexture, x + 1, y - 1, 32, 32, colors.side().tint());
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, impressionTexture, x, y - 1, 32, 32, colors.side().tint());
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, impressionTexture, x - 1, y - 1, 32, 32, colors.side().tint());
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, impressionTexture, x, y - 2, 32, 32, colors.side().tint());

        // Shadow
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, impressionTexture, x, y + 1, 32, 32, colors.shadow().tint());

        // Highlight
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, impressionTexture, x, y - 1, 32, 32, colors.highlight().tint());

        // Base
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, impressionTexture, x, y, 32, 32, colors.base().tint());


        if (material.hasGlint()) {
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, SEAL_GLINT_SPRITE, 32, 32, 0, 0, x + 1, y + 1, 32, 32);
        }

        seal.lock().ifPresent(lock -> {
            if (lock.isLocked(Minecrft.level())) {
                // Heartbeat pulsing effect.
                // 1.21.11: upstream multiplied the shader color by up to 1.4 (brighter than the texture). Vertex colors
                // cannot exceed 1.0, so the pulse is drawn as a second, translucent pass of the overlay on each beat.
                double time = (Minecrft.level().getGameTime() + Minecrft.get().getDeltaTracker().getGameTimeDeltaPartialTick(true)) / 20.0;
                double beat = Math.pow((Math.sin(time * Math.PI * 2 * 1) + 1.0) * 0.5, 10);

                guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, LOCKED_OVERLAY_SPRITE, 34, 34,
                      0, 0, x - 1, y - 1, 34, 34);
                guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, LOCKED_OVERLAY_SPRITE, 34, 34,
                      0, 0, x - 1, y - 1, 34, 34, ARGB.white((float) Mth.lerp(beat, 0.0, 0.4)));

                guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, LOCK_SPRITE, 34, 34,
                      0, 0, x - 1, y - 1, 34, 34);
            }
        });
    }

    public void renderDie(SealSymbol impression, ShadingPalette colors, GuiGraphics guiGraphics, int x, int y) {
        // Background
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, IRON_DIE_SPRITE, x, y, 30, 30);

        Identifier impressionSprite = impression.sprite();

        // flipping the sprite on the X axis is a bit tricky, hopefully it'll not break

        y--;

        // Side
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, impressionSprite, -30, 30, -30, 0, x + 1, y + 1, 30, 30, colors.side().tint());
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, impressionSprite, -30, 30, -30, 0, x, y + 1, 30, 30, colors.side().tint());
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, impressionSprite, -30, 30, -30, 0, x - 1, y + 1, 30, 30, colors.side().tint());
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, impressionSprite, -30, 30, -30, 0, x + 1, y, 30, 30, colors.side().tint());
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, impressionSprite, -30, 30, -30, 0, x, y, 30, 30, colors.side().tint());
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, impressionSprite, -30, 30, -30, 0, x - 1, y, 30, 30, colors.side().tint());
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, impressionSprite, -30, 30, -30, 0, x + 1, y - 1, 30, 30, colors.side().tint());
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, impressionSprite, -30, 30, -30, 0, x, y - 1, 30, 30, colors.side().tint());
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, impressionSprite, -30, 30, -30, 0, x - 1, y - 1, 30, 30, colors.side().tint());
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, impressionSprite, -30, 30, -30, 0, x + 1, y - 2, 30, 30, colors.side().tint());
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, impressionSprite, -30, 30, -30, 0, x, y - 2, 30, 30, colors.side().tint());
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, impressionSprite, -30, 30, -30, 0, x - 1, y - 2, 30, 30, colors.side().tint());

        // Highlight
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, impressionSprite, -30, 30, -30, 0, x, y + 1, 30, 30, colors.highlight().tint());

        // Shadow
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, impressionSprite, -30, 30, -30, 0, x, y - 1, 30, 30, colors.shadow().tint());

        // Base
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, impressionSprite, -30, 30, -30, 0, x, y, 30, 30, colors.base().tint());

    }

    // Have you seen someone rendering textures this way? Now you have.
    // Patent Pending ™
}
