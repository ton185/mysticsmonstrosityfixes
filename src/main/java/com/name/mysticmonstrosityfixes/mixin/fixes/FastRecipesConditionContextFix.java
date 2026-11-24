package com.name.mysticmonstrosityfixes.mixin.fixes;

import net.minecraft.server.ReloadableServerResources;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.common.crafting.conditions.ICondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FastRecipes replaces the recipe manager with its index-backed subclass at the end of
 * {@link ReloadableServerResources}' constructor. That subclass calls the deprecated no-argument
 * {@link RecipeManager} constructor, which stores {@link ICondition.IContext#EMPTY} instead of the context
 * Forge built from the server's tag manager. Forge and KubeJS then evaluate recipe conditions against empty
 * tags, so {@code forge:tag_empty} always succeeds and conditional recipes such as Iron Furnaces' copper
 * furnace are silently skipped.
 * <p>
 * This runs after FastRecipes' own tail injector (priority 500) and before KubeJS captures the context
 * (default priority 1000), so the real context is in place before any recipe processing starts. The fast
 * recipe index, the async loader setting, and the replacement manager itself are left untouched.
 */
@Mixin(value = ReloadableServerResources.class, priority = 750)
public abstract class FastRecipesConditionContextFix {
    private static final String FAST_RECIPE_MANAGER = "dev.sixik.fastrecipes.FastRecipeManager";

    @Shadow(remap = false)
    public abstract ICondition.IContext getConditionContext();

    @Shadow
    public abstract RecipeManager getRecipeManager();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void mysticmonstrosityfixes$restoreConditionContext(CallbackInfo callback) {
        RecipeManager manager = this.getRecipeManager();
        if (manager != null && FAST_RECIPE_MANAGER.equals(manager.getClass().getName())) {
            ((RecipeManagerConditionContextAccessor) manager)
                    .mysticmonstrosityfixes$setConditionContext(this.getConditionContext());
        }
    }
}
