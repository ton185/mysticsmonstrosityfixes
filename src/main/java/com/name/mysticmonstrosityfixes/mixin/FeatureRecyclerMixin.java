package com.name.mysticmonstrosityfixes.mixin;

import com.mojang.logging.LogUtils;
import dev.corgitaco.featurerecycler.FeatureRecycler;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;
import java.util.function.Function;

@Mixin(value = FeatureRecycler.class, remap = false)
public abstract class FeatureRecyclerMixin {

    @Unique
    private static final Logger mystics_monstrosity_fixes$LOGGER = LogUtils.getLogger();

    @Inject(
        method = "recycle",
        at = @At("HEAD"),
        cancellable = true
    )
    private static <T extends Holder<Biome>> void fastRecycle(
        List<T> biomes,
        Function<T, List<HolderSet<PlacedFeature>>> toFeatureSetFunction,
        CallbackInfoReturnable<List<FeatureSorter.StepFeatureData>> cir
    ) {
        long startTime = System.currentTimeMillis();
        int crashesPrevented = 0;

        GenerationStep.Decoration[] steps = GenerationStep.Decoration.values();
        List<FeatureSorter.StepFeatureData> result = new ArrayList<>(steps.length);

        // === Step-by-step ===
        for (int stepIdx = 0; stepIdx < steps.length; stepIdx++) {

            Object2IntOpenHashMap<Holder<PlacedFeature>> idMap = new Object2IntOpenHashMap<>();
            idMap.defaultReturnValue(-1);

            List<Holder<PlacedFeature>> idToHolder = new ArrayList<>();
            List<IntSet> edges = new ArrayList<>();
            IntArrayList firstSeenBiome = new IntArrayList();

            // Allocate node
            java.util.function.IntSupplier newNode = () -> {
                edges.add(new IntOpenHashSet());
                firstSeenBiome.add(Integer.MAX_VALUE);
                return edges.size() - 1;
            };

            // === Build ordering constraints (exact semantic match) ===
            for (int biomeIdx = 0; biomeIdx < biomes.size(); biomeIdx++) {
                T biome = biomes.get(biomeIdx);
                List<HolderSet<PlacedFeature>> featureSets = toFeatureSetFunction.apply(biome);
                if (stepIdx >= featureSets.size()) continue;

                List<Holder<PlacedFeature>> list =
                    featureSets.get(stepIdx).stream().toList();

                for (int i = 0; i < list.size(); i++) {
                    Holder<PlacedFeature> a = list.get(i);
                    int finalBiomeIdx = biomeIdx;
                    int aId = idMap.computeIfAbsent(a, __ -> {
                        int id = newNode.getAsInt();
                        idToHolder.add(a);
                        firstSeenBiome.set(id, finalBiomeIdx);
                        return id;
                    });

                    for (int j = i + 1; j < list.size(); j++) {
                        Holder<PlacedFeature> b = list.get(j);
                        int finalBiomeIdx1 = biomeIdx;
                        int bId = idMap.computeIfAbsent(b, __ -> {
                            int id = newNode.getAsInt();
                            idToHolder.add(b);
                            firstSeenBiome.set(id, finalBiomeIdx1);
                            return id;
                        });

                        edges.get(aId).add(bId);
                    }
                }
            }

            int nodeCount = idToHolder.size();
            if (nodeCount == 0) {
                result.add(new FeatureSorter.StepFeatureData(List.of(), pf -> -1));
                continue;
            }

            // === Topological sort with same tie-breaking ===
            int[] indegree = new int[nodeCount];
            for (int u = 0; u < nodeCount; u++) {
                for (int v : edges.get(u)) indegree[v]++;
            }

            PriorityQueue<Integer> queue = new PriorityQueue<>(
                Comparator.comparingInt(firstSeenBiome::getInt)
            );

            for (int i = 0; i < nodeCount; i++) {
                if (indegree[i] == 0) queue.add(i);
            }

            IntArrayList topo = new IntArrayList(nodeCount);

            while (!queue.isEmpty()) {
                int u = queue.poll();
                topo.add(u);
                for (int v : edges.get(u)) {
                    if (--indegree[v] == 0) queue.add(v);
                }
            }

            // === Cycle fallback (matches original behavior: "first wins") ===
            if (topo.size() < nodeCount) {
                for (int i = 0; i < nodeCount; i++) {
                    if (indegree[i] > 0) {
                        topo.add(i);
                        crashesPrevented++;
                    }
                }
            }

            // === Build StepFeatureData ===
            List<PlacedFeature> ordered = new ArrayList<>(nodeCount);
            Object2IntOpenHashMap<PlacedFeature> indexMap = new Object2IntOpenHashMap<>();

            for (int i = 0; i < topo.size(); i++) {
                Holder<PlacedFeature> holder = idToHolder.get(topo.getInt(i));
                PlacedFeature pf = holder.value();
                ordered.add(pf);
                indexMap.put(pf, i);
            }

            result.add(new FeatureSorter.StepFeatureData(ordered, indexMap::getInt));
        }

        mystics_monstrosity_fixes$LOGGER.info("Finished recycling features. Took {}ms", System.currentTimeMillis() - startTime);
        if (crashesPrevented > 0) {
            mystics_monstrosity_fixes$LOGGER.info("Feature Recycler just prevented {} crashes!", crashesPrevented);
        }

        cir.setReturnValue(result);
    }
}
