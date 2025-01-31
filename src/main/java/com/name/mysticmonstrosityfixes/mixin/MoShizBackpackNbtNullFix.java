package com.name.mysticmonstrosityfixes.mixin;

import com.ProfitOrange.MoShiz.container.backpack.AbstractBackpackContainer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractBackpackContainer.class)
public class MoShizBackpackNbtNullFix {
    @Redirect(method = "removed", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getTag()Lnet/minecraft/nbt/CompoundTag;"))
    public CompoundTag redirectToGetOrCreate(ItemStack instance) {
        return instance.getOrCreateTag();
    }
}
