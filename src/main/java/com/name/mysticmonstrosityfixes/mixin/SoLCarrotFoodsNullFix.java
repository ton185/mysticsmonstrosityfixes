package com.name.mysticmonstrosityfixes.mixin;

import com.cazsius.solcarrot.client.FoodItems;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Mixin(FoodItems.class)
public class SoLCarrotFoodsNullFix {
    @Shadow(remap = false)
    private static List<Item> foodsBeforeBlacklist;

    @Inject(at = @At("HEAD"), method = "applyBlacklist", remap = false)
    private static void nullCheck(CallbackInfo ci) {
        if (foodsBeforeBlacklist == null) {
            foodsBeforeBlacklist = ForgeRegistries.ITEMS.getValues().stream().filter(Item::isEdible).sorted(Comparator.comparing((food) -> I18n.get(food.getDescriptionId() + ".name", new Object[0]))).collect(Collectors.toList());
        }
    }
}
