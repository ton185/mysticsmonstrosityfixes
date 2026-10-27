package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.moakiee.ae2lt.client.compat.JeiRecipeTransferMetadata;
import com.moakiee.ae2lt.client.tianshu.TianshuDirectUploadClient;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.gui.recipes.RecipeTransferButtonController;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * JEI 15.74 moved the transfer button onto {@link RecipeTransferButtonController}. Lightning Tech
 * still mixes into the old class, so a Tianshu pattern transfer never records the recipe or keeps
 * the recipe screen open. This repeats that mixin on the class JEI uses now.
 */
@Mixin(value = RecipeTransferButtonController.class, remap = false)
public abstract class Ae2ltJeiRecipeTransferFix {
    @Accessor(value = "recipeLayout", remap = false)
    protected abstract IRecipeLayoutDrawable<?> mysticmonstrosityfixes$recipeLayout();

    @Inject(method = "transferRecipe(ZZ)Z", at = {@At("HEAD"), @At("RETURN")}, remap = false)
    private void mysticmonstrosityfixes$clearRecipeTransferMetadata(boolean maxTransfer, boolean simulate,
                                                                    CallbackInfoReturnable<Boolean> callback) {
        JeiRecipeTransferMetadata.clear();
    }

    @Inject(
            method = "transferRecipe(ZZ)Z",
            at = @At(
                    value = "FIELD",
                    target = "Lmezz/jei/gui/recipes/RecipeTransferButtonController;recipeLayout:Lmezz/jei/api/gui/IRecipeLayoutDrawable;",
                    remap = false
            ),
            remap = false
    )
    private void mysticmonstrosityfixes$beginRecipeTransferMetadata(boolean maxTransfer, boolean simulate,
                                                                    CallbackInfoReturnable<Boolean> callback) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof TianshuPatternEncodingTermMenu menu) {
            JeiRecipeTransferMetadata.begin(menu, this.mysticmonstrosityfixes$recipeLayout());
        }
    }

    @Redirect(
            method = "transferRecipe(ZZ)Z",
            at = @At(value = "INVOKE", target = "Ljava/lang/Runnable;run()V", remap = false),
            remap = false
    )
    private void mysticmonstrosityfixes$keepRecipePageForDirectUpload(Runnable onSuccessfulTransfer) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        Screen screen = minecraft.screen;
        if (screen instanceof RecipesGui
                && player != null
                && player.containerMenu instanceof TianshuPatternEncodingTermMenu menu
                && TianshuDirectUploadClient.holdRecipeScreen(menu, screen)) {
            return;
        }
        onSuccessfulTransfer.run();
    }
}
