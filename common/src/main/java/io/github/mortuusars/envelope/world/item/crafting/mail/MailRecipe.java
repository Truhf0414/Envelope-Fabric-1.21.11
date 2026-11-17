package io.github.mortuusars.envelope.world.item.crafting.mail;

import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.world.mail.address.type.ServiceAddress;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

// 1.21.11: Recipe no longer exposes ingredients/result (getIngredients, getResultItem, canCraftInDimensions,
// getToastSymbol, getRemainingItems were removed in 1.21.2). Mail recipes keep ingredients and result as their own API,
// because mail crafting, the Equine Assurance Bureau and JEI read them.
public interface MailRecipe extends Recipe<MailRecipeInput> {
    ServiceAddress getAddress();

    NonNullList<Ingredient> getIngredients();

    ItemStack getResultItem(HolderLookup.Provider registries);

    @Override
    RecipeSerializer<? extends MailRecipe> getSerializer();

    @Override
    default @NotNull RecipeType<MailRecipe> getType() {
        return Envelope.RecipeTypes.MAILING.get();
    }

    // Mail recipes are special (never in the recipe book), so nothing reads placement info or the book category:
    // RecipeManager only uses recipeBookCategory() for recipes that return a display() entry, and display() is empty.
    @Override
    default @NotNull PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    default @NotNull RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    @Override
    default boolean isSpecial() {
        return true;
    }

    /**
     * Whether the crafting should only be done one time per delivery.<br>
     * Some recipes (and their side effects) could get out of control when multiple is allowed (like letter broadcasting).
     */
    default boolean isOneCraftPerDelivery() {
        return false;
    }

    default Optional<Component> getInfo() {
        return Optional.empty();
    }

    default float getExperience() {
        return 0f;
    }

    default int getExperiencePoints() {
        float xp = getExperience();
        int points = Mth.floor(xp);
        float remainder = Mth.frac(xp);
        if (remainder != 0.0F && Math.random() < remainder) {
            points++;
        }
        return points;
    }

    // --

    @Override
    default boolean matches(MailRecipeInput input, Level level) {
        if (input.ingredientCount() != getIngredients().size()) {
            return false;
        }
        return input.size() == 1 && getIngredients().size() == 1
              ? getIngredients().getFirst().test(input.getItem(0))
              : input.stackedContents().canCraft(getIngredients(), null); // 1.21.11: canCraft(Recipe) reads placementInfo, which mail recipes don't have
    }

    /**
     * Regular result assemble method. Does not mutate world state. Can be called as many times as needed.
     */
    @Override
    default @NotNull ItemStack assemble(MailRecipeInput input, HolderLookup.Provider registries) {
        return getResultItem(registries).copy();
    }

    /**
     * Special assemble method, that allows performing extra actions, that could potentially mutate the world state.<br>
     * - This method should be called only once per craft, when the crafting is guaranteed.<br>
     * - Should return the same item "shape" (type, count), as {@link MailRecipe#assemble(MailRecipeInput, HolderLookup.Provider)},
     * to make sure the crafting works properly. Components can be different.
     */
    default @NotNull ItemStack assembleWithSideEffects(MailRecipeInput input, HolderLookup.Provider registries) {
        return assemble(input, registries);
    }

    /**
     * 1.21.11: Recipe#getRemainingItems was removed. Same behaviour as the old default: each input item leaves its crafting remainder.
     */
    default NonNullList<ItemStack> getRemainingItems(MailRecipeInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int i = 0; i < remaining.size(); i++) {
            remaining.set(i, input.getItem(i).getItem().getCraftingRemainder());
        }
        return remaining;
    }
}
