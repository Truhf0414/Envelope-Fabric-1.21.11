package io.github.mortuusars.envelope.integration.jei.extensions;

import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.lib.mortaar.client.Minecrft;
import io.github.mortuusars.envelope.world.item.crafting.AddressTagApplicationRecipe;
import io.github.mortuusars.envelope.world.item.mail.Mail;
import io.github.mortuusars.envelope.world.mail.address.type.ServiceAddress;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.ICraftingGridHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import io.github.mortuusars.envelope.integration.jei.util.JeiDisplays;

public class AddressTagApplicationRecipeExtension implements ICraftingCategoryExtension<AddressTagApplicationRecipe> {
    // 1.21.11 (JEI 27): extensions must also describe their inputs as slot displays (used for recipe lookups)
    @Override
    public List<SlotDisplay> getIngredients(RecipeHolder<AddressTagApplicationRecipe> holder) {
        return List.of(new SlotDisplay.TagSlotDisplay(Envelope.Tags.Items.MAILABLE),
              new SlotDisplay.ItemSlotDisplay(Envelope.Items.ADDRESS_TAG.get()));
    }

    @Override
    public void setRecipe(RecipeHolder<AddressTagApplicationRecipe> holder, IRecipeLayoutBuilder builder,
                          ICraftingGridHelper craftingGridHelper, IFocusGroup focuses) {
        List<ItemStack> mailableItems = JeiDisplays.itemsInTag(Envelope.Tags.Items.MAILABLE);

        ItemStack tag = new ItemStack(Envelope.Items.ADDRESS_TAG.get());
        tag.set(Envelope.DataComponents.ADDRESS_TAG_ADDRESS, ServiceAddress.getOrThrow(ServiceAddress.MAIL_SERVICE, Minecrft.registryAccess()));
        List<ItemStack> tagItems = List.of(tag);

        List<ItemStack> resultItems = mailableItems.stream()
              .map(stack -> {
                  ItemStack result = stack.copy();
                  Mail.setRecipient(result, tag.get(Envelope.DataComponents.ADDRESS_TAG_ADDRESS));
                  return result;
              })
              .toList();

        craftingGridHelper.createAndSetInputs(builder, VanillaTypes.ITEM_STACK, List.of(mailableItems, tagItems), 0, 0);
        craftingGridHelper.createAndSetOutputs(builder, VanillaTypes.ITEM_STACK, resultItems);
    }
}
