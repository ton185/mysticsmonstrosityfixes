package com.name.mysticmonstrosityfixes.mixin.loading;

import com.faktocraft.client.FaktocraftClient;
import com.name.mysticmonstrosityfixes.FaktocraftCoverHosts;
import com.faktocraft.client.model.CoverBakedModel;
import com.faktocraft.client.model.CoverQuads;
import com.faktocraft.common.cover.CoverSupport;
import com.faktocraft.common.registries.ModBlocks;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * Faktocraft wraps coverable block models by walking the bake registry. ModernFix does not pass
 * the raw model map: each mod gets an emulated registry whose {@code get} bakes the model. Doing
 * that for every coverable state took 14.25 seconds on this pack. Record the blocks here and let
 * {@link ModernFixCoverBakeFix} wrap a model the first time it is actually requested.
 */
@Mixin(value = FaktocraftClient.class, remap = false)
public class FaktocraftCoverBakeFix {
    @Inject(method = "onModifyBakingResult", at = @At("HEAD"), cancellable = true, remap = false)
    private static void mysticmonstrosityfixes$bakeCovers(ModelEvent.ModifyBakingResult event, CallbackInfo ci) {
        CoverQuads.clear();
        Map<String, Boolean> hosts = new HashMap<>();
        for (Block block : ForgeRegistries.BLOCKS) {
            if (block != ModBlocks.DRILLED_BLOCK && !CoverSupport.isCoverable(block)) {
                continue;
            }
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
            if (id != null) {
                hosts.put(id.getNamespace() + ":" + id.getPath(), block == ModBlocks.DRILLED_BLOCK);
            }
        }
        FaktocraftCoverHosts.HOSTS = hosts;

        Map<ResourceLocation, BakedModel> models = event.getModels();
        if (models.getClass().getName().startsWith("org.embeddedt.modernfix.")) {
            ci.cancel();
            return;
        }

        for (Block block : ForgeRegistries.BLOCKS) {
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
            if (id == null) {
                continue;
            }
            Boolean drilled = hosts.get(id.getNamespace() + ":" + id.getPath());
            if (drilled == null) {
                continue;
            }
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                ModelResourceLocation location = BlockModelShaper.stateToModelLocation(state);
                BakedModel existing = models.get(location);
                if (existing != null && !(existing instanceof CoverBakedModel)) {
                    models.put(location, new CoverBakedModel(existing, drilled));
                }
            }
        }
        ci.cancel();
    }
}
