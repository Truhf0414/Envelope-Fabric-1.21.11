package io.github.mortuusars.envelope.lib.mortaar.serialization;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;

public interface Codecs {
    Codec<Integer> HEX_COLOR = Codec.STRING.comapFlatMap(
          hex -> {
              try {
                  int color = (int) Long.parseLong(hex.replace("#", ""), 16);
                  return DataResult.success(color);
              } catch (NumberFormatException e) {
                  return DataResult.error(() -> "Invalid color value: " + hex);
              }
          },
          color -> "#" + String.format("%08X", color));

    Codec<Float> NON_NEGATIVE_FLOAT = Codec.FLOAT
          .validate(value -> value.compareTo(0.0f) >= 0.0f && value.compareTo(1.0f) <= 0
                ? DataResult.success(value)
                : DataResult.error(() -> "Value must be within 0.0 to 1.0 range."));

    static Codec<NonNullList<Ingredient>> recipeIngredients(int size, RecipeType<?> type) {
        return Ingredient.CODEC // 1.21.11: CODEC is non-empty; CODEC_NONEMPTY removed
              .listOf()
              .flatXmap(
                    list -> {
                        Ingredient[] ingredients = list.stream().filter(ingredient -> !ingredient.isEmpty()).toArray(Ingredient[]::new);
                        if (ingredients.length == 0) {
                            return DataResult.error(() -> "No ingredients for " + type.toString() + " recipe");
                        } else {
                            return ingredients.length > size
                                  ? DataResult.error(() -> "Too many ingredients for " + type.toString() + " recipe")
                                  : DataResult.success(toNonNullList(ingredients));
                        }
                    },
                    DataResult::success
              );
    }

    // 1.21.11: Ingredient.EMPTY no longer exists, so the list cannot be built with NonNullList.of(EMPTY, ...).
    private static NonNullList<Ingredient> toNonNullList(Ingredient[] ingredients) {
        NonNullList<Ingredient> list = NonNullList.create();
        list.addAll(java.util.Arrays.asList(ingredients));
        return list;
    }
}
