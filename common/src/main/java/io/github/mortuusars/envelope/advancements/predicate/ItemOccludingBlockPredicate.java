package io.github.mortuusars.envelope.advancements.predicate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.predicates.DataComponentPredicate;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

public class ItemOccludingBlockPredicate implements DataComponentPredicate {
    public static final ItemOccludingBlockPredicate INSTANCE = new ItemOccludingBlockPredicate();
    public static final Codec<ItemOccludingBlockPredicate> CODEC = MapCodec.unit(INSTANCE).codec();

    private ItemOccludingBlockPredicate() {}

    @Override
    public boolean matches(DataComponentGetter components) {
        // 1.21.11: ItemSubPredicate was replaced by DataComponentPredicate, which receives a DataComponentGetter.
        // Vanilla ItemPredicate#test passes the ItemStack itself, so the item can still be inspected.
        if (!(components instanceof ItemStack stack)) {
            return false;
        }
        // This is a crude way to check for at least somewhat heavy blocks. This is probably the best we can do without having access to level and position.
        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock().defaultBlockState().canOcclude();
    }
}
