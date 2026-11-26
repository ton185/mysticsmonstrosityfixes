package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.illusivesoulworks.polymorph.common.crafting.RecipeSelection;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * FastRecipes replaces the recipe manager with its index-backed subclass and overrides both
 * {@code getRecipeFor} overloads. Polymorph hooks those methods on {@link
 * net.minecraft.world.item.crafting.RecipeManager}, but a subclass override never calls the transformed
 * superclass method, so the hooks are bypassed: the block keeps FastRecipes' first indexed result, no
 * recipe list is built for the client, and selecting another recipe does nothing.
 * <p>
 * These are Polymorph's own {@code MixinRecipeManager} hooks applied to FastRecipeManager instead. Calling
 * {@link RecipeSelection#getBlockEntityRecipe} builds and sends the full list of matching recipes through
 * Polymorph's normal recipe data. A transient crafting container is routed through its owning menu's
 * player data, which covers menus whose recipe lookup FastRecipes bypasses directly. That includes the
 * FastWorkbench crafting table and AE2-style terminals: Polymorph's FastBench integration mixin is
 * declared at JAVA_16 in its integration config while the mixin class is Java 17, so Mixin rejects it
 * and those screens fall back to {@code getRecipeFor} with a transient crafting container. Lookups for
 * containers that Polymorph does not handle still fall through to FastRecipes' fast index.
 */
@Mixin(targets = "dev.sixik.fastrecipes.FastRecipeManager", remap = false)
public abstract class FastRecipesPolymorphCompatFix {

    @Inject(
            method = "m_220248_(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private <C extends Container, T extends Recipe<C>> void mmf$getSelectedRecipeById(
            RecipeType<T> recipeType,
            C inventory,
            Level level,
            ResourceLocation resourceLocation,
            CallbackInfoReturnable<Optional<Pair<ResourceLocation, T>>> callback
    ) {

        if (inventory instanceof BlockEntity blockEntity) {
            RecipeSelection.getBlockEntityRecipe(recipeType, inventory, level, blockEntity)
                    .ifPresent(recipe -> callback.setReturnValue(Optional.of(Pair.of(resourceLocation, recipe))));
            return;
        }
        if (inventory instanceof TransientCraftingContainer craftingContainer) {
            AbstractContainerMenu menu = ((TransientCraftingContainerAccessor) craftingContainer).mmf$getMenu();
            Player player = mmf$findPlayer(menu);
            if (player != null && player.containerMenu == menu) {
                callback.setReturnValue(RecipeSelection.getPlayerRecipe(menu, recipeType, inventory, level, player)
                        .map(recipe -> Pair.of(resourceLocation, recipe)));
            }
        }
    }

    @Inject(
            method = "m_44015_(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private <C extends Container, T extends Recipe<C>> void mmf$getSelectedRecipe(
            RecipeType<T> recipeType,
            C inventory,
            Level level,
            CallbackInfoReturnable<Optional<T>> callback
    ) {

        if (inventory instanceof BlockEntity blockEntity) {
            RecipeSelection.getBlockEntityRecipe(recipeType, inventory, level, blockEntity)
                    .ifPresent(recipe -> callback.setReturnValue(Optional.of(recipe)));
            return;
        }
        if (inventory instanceof TransientCraftingContainer craftingContainer) {
            AbstractContainerMenu menu = ((TransientCraftingContainerAccessor) craftingContainer).mmf$getMenu();
            Player player = mmf$findPlayer(menu);
            if (player != null && player.containerMenu == menu) {
                callback.setReturnValue(RecipeSelection.getPlayerRecipe(menu, recipeType, inventory, level, player));
            }
        }
    }

    private static Player mmf$findPlayer(AbstractContainerMenu menu) {
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory inventory) {
                return inventory.player;
            }
        }
        return null;
    }
}
