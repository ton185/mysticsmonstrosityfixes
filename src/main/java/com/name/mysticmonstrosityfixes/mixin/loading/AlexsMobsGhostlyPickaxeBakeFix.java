package com.name.mysticmonstrosityfixes.mixin.loading;

import com.github.alexthe666.alexsmobs.ClientProxy;
import com.github.alexthe666.alexsmobs.client.render.item.GhostlyPickaxeBakedModel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Alex's Mobs wraps the ghostly pickaxe by scanning every baked-model key for one item id.
 * This boot that scan was 3.758 seconds. Look the item model up directly.
 */
@Mixin(value = ClientProxy.class, remap = false)
public class AlexsMobsGhostlyPickaxeBakeFix {
    @Inject(method = "onBakingCompleted", at = @At("HEAD"), cancellable = true, remap = false)
    private static void mysticmonstrosityfixes$wrapGhostlyPickaxe(ModelEvent.ModifyBakingResult event, CallbackInfo ci) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("alexsmobs", "ghostly_pickaxe");
        if (ForgeRegistries.ITEMS.containsKey(id)) {
            Map<ResourceLocation, BakedModel> models = event.getModels();
            ModelResourceLocation location = new ModelResourceLocation(id, "inventory");
            BakedModel existing = models.get(location);
            if (existing != null && !(existing instanceof GhostlyPickaxeBakedModel)) {
                models.put(location, new GhostlyPickaxeBakedModel(existing));
            }
        }
        ci.cancel();
    }
}
