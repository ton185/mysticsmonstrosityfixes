package com.name.mysticmonstrosityfixes.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wayoftime.bloodmagic.compat.CuriosCompat;
import top.theillusivec4.curios.api.CuriosApi;

@Mixin(CuriosCompat.class)
public class BloodMagicNullCapabilityFix {
    @Inject(at = @At("HEAD"), method = "getCuriosInventory", cancellable = true, remap = false)
    public void nullFix(Player player, CallbackInfoReturnable<NonNullList<ItemStack>> cir) {
        if (!CuriosApi.getCuriosInventory(player).isPresent()) {
            cir.setReturnValue(NonNullList.create());
            cir.cancel();
        }
    }
}
