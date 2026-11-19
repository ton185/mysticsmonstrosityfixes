package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.name.mysticmonstrosityfixes.avaritia.FastRecipeMatcher;
import committee.nova.mods.avaritia.common.crafting.recipe.InfinityCatalystCraftRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Covers Re:Avaritia's infinity catalyst recipes, which extend the shapeless table recipe but override its
 * {@code matches} and call Forge's matcher from their own methods. Without this they keep the exponential search
 * that {@link AvaritiaShapelessRecipeHangFix} removes from the parent recipe.
 */
@Mixin(value = InfinityCatalystCraftRecipe.class, remap = false)
public class AvaritiaCatalystRecipeHangFix {

    @Redirect(
            method = "matchesSingularityRecipe(Lnet/minecraft/world/Container;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/common/util/RecipeMatcher;findMatches(Ljava/util/List;Ljava/util/List;)[I"),
            remap = false)
    private int[] mmf$findSingularityMatches(List<ItemStack> inputs, List<Ingredient> ingredients) {

        return FastRecipeMatcher.findMatches(inputs, ingredients);
    }

    @Redirect(
            method = "matchesStandardRecipe(Lnet/minecraft/world/Container;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/common/util/RecipeMatcher;findMatches(Ljava/util/List;Ljava/util/List;)[I"),
            remap = false)
    private int[] mmf$findStandardMatches(List<ItemStack> inputs, List<Ingredient> ingredients) {

        return FastRecipeMatcher.findMatches(inputs, ingredients);
    }
}
