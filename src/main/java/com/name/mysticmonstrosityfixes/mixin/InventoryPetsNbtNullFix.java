package com.name.mysticmonstrosityfixes.mixin;

import com.inventorypets.pets.petChest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(petChest.class)
public class InventoryPetsNbtNullFix {
    @Redirect(at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/CompoundTag;get(Ljava/lang/String;)Lnet/minecraft/nbt/Tag;"), method = "lambda$readShareTag$2")
    private static Tag getNbtSafely(CompoundTag nbt, String key) {
        if (!nbt.contains(key)) {
            CompoundTag tag = new CompoundTag();
            nbt.put(key, tag);
        }
        return nbt.get(key);
    }
}
