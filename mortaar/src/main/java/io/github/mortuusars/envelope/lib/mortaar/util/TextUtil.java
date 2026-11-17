package io.github.mortuusars.envelope.lib.mortaar.util;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

public class TextUtil {
    public static FormattedText truncateStringToWidth(Font font, FormattedText text, int width) {
        if (font.width(text) <= width) {
            return text;
        }

        int ellipsisWidth = font.width("...");

        FormattedText truncatedText = font.substrByWidth(text, width - ellipsisWidth);

        // 1.21.11: StringSplitter#componentStyleAtWidth was removed; use the style of the last visible part instead.
        Style[] lastStyle = {Style.EMPTY};
        truncatedText.visit((partStyle, part) -> {
            if (!part.isEmpty()) lastStyle[0] = partStyle;
            return java.util.Optional.empty();
        }, Style.EMPTY);
        Style style = lastStyle[0];

        return FormattedText.composite(truncatedText, Component.literal("...").setStyle(style));
    }
}
