package io.github.mortuusars.envelope.integration.jei.extensions;

import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.world.item.crafting.SealStampDyeingRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.ingredient.ICraftingGridHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import io.github.mortuusars.envelope.integration.jei.util.JeiDisplays;

public class SealStampDyeingRecipeExtension implements ICraftingCategoryExtension<SealStampDyeingRecipe> {
    // 1.21.11 (JEI 27): extensions must also describe their inputs as slot displays (used for recipe lookups)
    @Override
    public List<SlotDisplay> getIngredients(RecipeHolder<SealStampDyeingRecipe> holder) {
        List<SlotDisplay> dyes = Envelope.Items.DYED_SEAL_STAMPS.keySet().stream()
              .<SlotDisplay>map(color -> new SlotDisplay.ItemSlotDisplay(DyeItem.byColor(color)))
              .toList();
        return List.of(new SlotDisplay.ItemSlotDisplay(Envelope.Items.SEAL_STAMP.get()), new SlotDisplay.Composite(dyes));
    }

    @Override
    public void setRecipe(RecipeHolder<SealStampDyeingRecipe> holder, IRecipeLayoutBuilder builder,
                          ICraftingGridHelper craftingGridHelper, IFocusGroup focuses) {



        List<ItemStack> stamps = List.of(new ItemStack(Envelope.Items.SEAL_STAMP.get()));
        List<ItemStack> dyes = Envelope.Items.DYED_SEAL_STAMPS.keySet().stream().map(DyeItem::byColor).map(ItemStack::new).toList();
        List<ItemStack> results = Envelope.Items.DYED_SEAL_STAMPS.values().stream().map(i -> new ItemStack(i.get())).toList();

        List<IRecipeSlotBuilder> inputSlots = craftingGridHelper.createAndSetInputs(builder, List.of(stamps, dyes), 0, 0);
        IRecipeSlotBuilder outputSlot = craftingGridHelper.createAndSetOutputs(builder, results);

        builder.createFocusLink(inputSlots.get(1), outputSlot);
    }
}
