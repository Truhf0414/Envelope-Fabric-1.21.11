package io.github.mortuusars.envelope.world.item;

import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.world.inventory.PaybackPackingMenu;
import io.github.mortuusars.envelope.world.item.component.PaybackSubject;
import io.github.mortuusars.envelope.lib.mortaar.Platform;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import net.minecraft.world.item.component.TooltipDisplay;

public class PaybackBoxItem extends Item {
    public PaybackBoxItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay tooltipDisplay, Consumer<Component> components, TooltipFlag flag) { // 1.21.11: Consumer + TooltipDisplay
        PaybackPackageItem.appendPaybackHoverText(stack, context, components, flag);
    }

    // --

    @Override
    public @NotNull InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (openPackingGui(player, hand, stack)) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.FAIL;
    }

    public boolean openPackingGui(Player player, InteractionHand hand, ItemStack stack) {
        @Nullable PaybackSubject subject = stack.get(Envelope.DataComponents.PAYBACK_SUBJECT);
        if (subject == null || subject.mail().isEmpty() || !subject.mail().has(Envelope.DataComponents.MAIL_PAYBACK_TAG)) {
            return false;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            stack.set(Envelope.DataComponents.OPEN, Unit.INSTANCE);
            Platform.openMenu(serverPlayer, new SimpleMenuProvider((id, inventory, pl) ->
                        new PaybackPackingMenu(id, inventory, hand), stack.getHoverName()),
                  buffer -> buffer.writeEnum(hand));
        }

        player.level().playSound(player, player, Envelope.SoundEvents.PAPER_USE.get(), SoundSource.PLAYERS, 0.6f, 0.95f);
        return true;
    }
}