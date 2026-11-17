package io.github.mortuusars.envelope.integration.jei.extensions;

import io.github.mortuusars.envelope.world.item.crafting.ComponentTransferringRecipe;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.ICraftingGridHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import io.github.mortuusars.envelope.integration.jei.util.JeiDisplays;

public class ComponentTransferringRecipeExtension implements ICraftingCategoryExtension<ComponentTransferringRecipe> {
    // 1.21.11 (JEI 27): extensions must also describe their inputs as slot displays (used for recipe lookups)
    @Override
    public List<SlotDisplay> getIngredients(RecipeHolder<ComponentTransferringRecipe> holder) {
        ComponentTransferringRecipe recipe = holder.value();
        List<SlotDisplay> displays = new ArrayList<>();
        displays.add(recipe.getSourceIngredient().display());
        for (Ingredient ingredient : recipe.getIngredients()) {
            displays.add(ingredient.display());
        }
        return displays;
    }

    @Override
    public void setRecipe(RecipeHolder<ComponentTransferringRecipe> holder, IRecipeLayoutBuilder builder,
                          ICraftingGridHelper craftingGridHelper, IFocusGroup focuses) {
        ComponentTransferringRecipe recipe = holder.value();

        List<List<ItemStack>> inputs = new ArrayList<>();

        inputs.add(JeiDisplays.items(recipe.getSourceIngredient())); // 1.21.11: Ingredient#getItems was removed
        for (Ingredient ingredient : recipe.getIngredients()) {
            inputs.add(JeiDisplays.items(ingredient));
        }

        craftingGridHelper.createAndSetInputs(builder, VanillaTypes.ITEM_STACK, inputs, 0, 0);
        craftingGridHelper.createAndSetOutputs(builder, VanillaTypes.ITEM_STACK, List.of(recipe.getResult()));
    }
}
