package io.github.mortuusars.envelope.integration.jei.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

/**
 * 1.21.11: helpers for what JEI extensions used to read straight from ingredients and registries
 * (Ingredient#getItems and Registry#getTag were removed).
 */
public final class JeiDisplays {
    private JeiDisplays() {
    }

    public static List<ItemStack> items(Ingredient ingredient) {
        return ingredient.items().map(ItemStack::new).toList();
    }

    public static List<ItemStack> itemsInTag(TagKey<Item> tag) {
        return BuiltInRegistries.ITEM.get(tag)
              .map(named -> named.stream().map(ItemStack::new).toList())
              .orElse(List.of());
    }
}
