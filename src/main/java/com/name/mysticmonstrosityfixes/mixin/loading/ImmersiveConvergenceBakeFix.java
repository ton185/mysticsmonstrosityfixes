package com.name.mysticmonstrosityfixes.mixin.loading;

import com.immersiveconvergence.api.client.split.SplitModelHandler;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Immersive Convergence finds its split-model blocks by walking every baked-model key. This boot
 * that walk was 5.185 seconds. The machines are already registered, so hand the listener just
 * those block-state models.
 */
@Mixin(value = SplitModelHandler.class, remap = false)
public class ImmersiveConvergenceBakeFix {
    @Redirect(
            method = "onModifyBakingResult",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;keySet()Ljava/util/Set;"),
            remap = false
    )
    private static Set<ResourceLocation> mysticmonstrosityfixes$machineModels(Map<ResourceLocation, BakedModel> models) {
        Set<ResourceLocation> keys = new HashSet<>();
        for (ResourceLocation id : SplitModelHandlerAccessor.mysticmonstrosityfixes$machines().keySet()) {
            if (!ForgeRegistries.BLOCKS.containsKey(id)) {
                continue;
            }
            Block block = ForgeRegistries.BLOCKS.getValue(id);
            if (block == null) {
                continue;
            }
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                keys.add(BlockModelShaper.stateToModelLocation(state));
            }
        }
        return keys;
    }
}
