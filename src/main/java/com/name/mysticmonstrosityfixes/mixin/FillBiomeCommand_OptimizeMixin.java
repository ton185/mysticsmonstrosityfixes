// SPDX-License-Identifier: MIT
package com.name.mysticmonstrosityfixes.mixin;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Mixin(FillBiomeCommand.class)
public abstract class FillBiomeCommand_OptimizeMixin {
    
    @Unique
    private static final ThreadLocal<Set<ChunkAccess>> mysticsmonstrosityfixes$changedChunks =
            ThreadLocal.withInitial(ObjectOpenHashSet::new);
    
    @Inject(method = "fill(Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Holder$Reference;Ljava/util/function/Predicate;)I",
            at = @At("HEAD"))
    private static void mysticsmonstrosityfixes$onHead(CallbackInfoReturnable<Integer> cir) {
        mysticsmonstrosityfixes$changedChunks.get().clear();
    }

    @Inject(method = "fill(Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Holder$Reference;Ljava/util/function/Predicate;)I",
            at = @At("RETURN"))
    private static void mysticsmonstrosityfixes$onReturn(CallbackInfoReturnable<Integer> cir) {
        mysticsmonstrosityfixes$changedChunks.get().clear();
    }

    @Redirect(
            method = "fill(Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Holder$Reference;Ljava/util/function/Predicate;)I",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/ChunkAccess;fillBiomesFromNoise(Lnet/minecraft/world/level/biome/BiomeResolver;Lnet/minecraft/world/level/biome/Climate$Sampler;)V"
            )
    )
    private static void mysticsmonstrosityfixes$diffThenMaybeApply(ChunkAccess chunk,
                                                   BiomeResolver resolver,
                                                   Climate.Sampler sampler) {
        if (!mysticsmonstrosityfixes$wouldChangeAnyQuart(chunk, resolver, sampler)) {
            return;
        }

        mysticsmonstrosityfixes$changedChunks.get().add(chunk);
        chunk.fillBiomesFromNoise(resolver, sampler);
    }

    @Redirect(
            method = "fill(Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Holder$Reference;Ljava/util/function/Predicate;)I",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/ChunkAccess;setUnsaved(Z)V"
            )
    )
    private static void mysticsmonstrosityfixes$onlyMarkChanged(ChunkAccess chunk, boolean unsaved) {
        if (unsaved && mysticsmonstrosityfixes$changedChunks.get().contains(chunk)) {
            chunk.setUnsaved(true);
        }
    }

    @Redirect(
            method = "fill(Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Holder$Reference;Ljava/util/function/Predicate;)I",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkMap;resendBiomesForChunks(Ljava/util/List;)V"
            )
    )
    private static void mysticsmonstrosityfixes$resendOnlyChanged(ChunkMap map, List<ChunkAccess> all) {
        Set<ChunkAccess> changed = mysticsmonstrosityfixes$changedChunks.get();
        if (changed.isEmpty()) {
            return;
        }

        List<ChunkAccess> filtered = new ArrayList<>(Math.min(all.size(), changed.size()));
        for (ChunkAccess c : all) {
            if (changed.contains(c)) filtered.add(c);
        }

        if (!filtered.isEmpty()) {
            map.resendBiomesForChunks(filtered);
        }
    }

    @Unique
    private static boolean mysticsmonstrosityfixes$wouldChangeAnyQuart(ChunkAccess chunk, BiomeResolver resolver, Climate.Sampler sampler) {
        ChunkPos pos = chunk.getPos();

        int blockX0 = pos.getMinBlockX();
        int blockZ0 = pos.getMinBlockZ();
        int blockX1 = blockX0 + 15;
        int blockZ1 = blockZ0 + 15;

        int qx0 = QuartPos.fromBlock(blockX0);
        int qx1 = QuartPos.fromBlock(blockX1);
        int qz0 = QuartPos.fromBlock(blockZ0);
        int qz1 = QuartPos.fromBlock(blockZ1);

        int minY = chunk.getMinBuildHeight();
        int height = chunk.getHeight();
        int maxYBlock = minY + height - 1;

        int qy0 = QuartPos.fromBlock(minY);
        int qy1 = QuartPos.fromBlock(maxYBlock);

        for (int qx = qx0; qx <= qx1; qx++) {
            for (int qz = qz0; qz <= qz1; qz++) {
                for (int qy = qy0; qy <= qy1; qy++) {
                    var current = chunk.getNoiseBiome(qx, qy, qz);
                    var resolved = resolver.getNoiseBiome(qx, qy, qz, sampler);
                    if (!Objects.equals(resolved, current)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
