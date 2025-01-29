package com.name.mysticmonstrosityfixes.mixin;

import dev.ftb.mods.ftblibrary.integration.forge.JEIIntegration;
import dev.ftb.mods.ftblibrary.ui.IScreenWrapper;
import dev.ftb.mods.ftblibrary.util.client.PositionedIngredient;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.input.ClickableIngredient;
import mezz.jei.common.util.ImmutableRect2i;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(JEIIntegration.class)
public class FTBLibraryNullFix {
    @Shadow
    public static IJeiRuntime runtime;

    @Inject(method = "getClickableIngredientUnderMouse", at = @At("HEAD"), cancellable = true)
    public void nullFix(double mouseX, double mouseY, CallbackInfoReturnable<Optional<IClickableIngredient<?>>> cir) {
        cir.cancel();
        Screen currentScreen = Minecraft.getInstance().screen;
        if (currentScreen instanceof IScreenWrapper wrapper) {
            if (wrapper.getGui().getIngredientUnderMouse().isPresent()) {
                PositionedIngredient underMouse = wrapper.getGui().getIngredientUnderMouse().get();
                Object typed = underMouse.ingredient();
                if(runtime != null) {
                    if (typed instanceof ItemStack stack) {
                        Optional<ITypedIngredient<ItemStack>> typed2 = runtime.getIngredientManager().createTypedIngredient(VanillaTypes.ITEM_STACK, stack);
                        typed2.ifPresent(itemStackITypedIngredient -> cir.setReturnValue(Optional.of(new ClickableIngredient<>(itemStackITypedIngredient, new ImmutableRect2i(underMouse.area())))));
                    } else {
                        typed = underMouse.ingredient();
                        if (typed instanceof FluidStack stack) {
                            Optional<ITypedIngredient<FluidStack>> typed2 = runtime.getIngredientManager().createTypedIngredient(ForgeTypes.FLUID_STACK, stack);
                            typed2.ifPresent(fluidStackITypedIngredient -> cir.setReturnValue(Optional.of(new ClickableIngredient<>(fluidStackITypedIngredient, new ImmutableRect2i(underMouse.area())))));
                        }
                    }
                } else {
                    cir.setReturnValue(Optional.empty());
                }
            }
        }
        cir.setReturnValue(Optional.empty());
    }
}
