package com.name.mysticmonstrosityfixes.mixin.fixes;

import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.common.crafting.conditions.ICondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Makes the Forge condition context stored on {@link RecipeManager} writable so
 * {@link FastRecipesConditionContextFix} can give FastRecipes' replacement manager the server's real context.
 */
@Mixin(RecipeManager.class)
public interface RecipeManagerConditionContextAccessor {

    @Accessor(value = "context", remap = false)
    @Mutable
    void mysticmonstrosityfixes$setConditionContext(ICondition.IContext context);
}
