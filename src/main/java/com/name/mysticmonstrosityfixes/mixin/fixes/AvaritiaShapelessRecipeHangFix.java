package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.name.mysticmonstrosityfixes.avaritia.FastRecipeMatcher;
import committee.nova.mods.avaritia.common.crafting.recipe.ShapelessTableCraftingRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Replaces the exponential shapeless match in Re:Avaritia's tier crafting tables (including the 9x9 Extreme
 * Crafting Table) with the polynomial matcher in {@link FastRecipeMatcher}.
 * <p>
 * A watchdog crash on this pack spent 360 seconds in {@code RecipeMatcher.backtrack} under
 * {@code ShapelessTableCraftingRecipe.matches}, called from {@code TierCraftMenu.assembleCurrentResult} on every
 * slot change. The recipe only needs to know whether the stacks can be paired with the ingredients, so the
 * backtracking search is replaced with a maximum bipartite matching.
 */
@Mixin(value = ShapelessTableCraftingRecipe.class, remap = false)
public class AvaritiaShapelessRecipeHangFix {

    @Redirect(
            method = "matches(Lnet/minecraftforge/items/IItemHandler;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/common/util/RecipeMatcher;findMatches(Ljava/util/List;Ljava/util/List;)[I"),
            remap = false)
    private int[] mmf$findMatches(List<ItemStack> inputs, List<Ingredient> ingredients) {

        return FastRecipeMatcher.findMatches(inputs, ingredients);
    }
}
