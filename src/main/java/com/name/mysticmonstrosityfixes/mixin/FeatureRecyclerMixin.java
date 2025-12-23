package com.name.mysticmonstrosityfixes.mixin;

import dev.corgitaco.featurerecycler.FeatureRecycler;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

@Mixin(value = FeatureRecycler.class, remap = false)
public abstract class FeatureRecyclerMixin {

    /**
     * @author Mysticpasta1, CorgiTaco
     * @reason Same functionality, parallelized per generation step
     */
    @Overwrite
    public static <T extends Holder<Biome>> List<FeatureSorter.StepFeatureData> recycle(
            List<T> biomes,
            Function<T, List<HolderSet<PlacedFeature>>> toFeatueSetFunction
    ) {
        long startTime = System.currentTimeMillis();
        FeatureRecycler.LOGGER.info("Starting feature recycler...");

        AtomicInteger crashesPrevented = new AtomicInteger();

        List<Map<T, List<Holder<PlacedFeature>>>> biomeTracker = new ArrayList<>();
        for (GenerationStep.Decoration ignored : GenerationStep.Decoration.values()) {
            biomeTracker.add(new Reference2ObjectLinkedOpenHashMap<>());
        }

        for (T biome : biomes) {
            List<HolderSet<PlacedFeature>> features = toFeatueSetFunction.apply(biome);
            for (int i = 0; i < features.size(); i++) {
                biomeTracker.get(i)
                        .put(biome, new ArrayList<>(features.get(i).stream().toList()));
            }
        }

        biomeTracker.parallelStream().forEach(featuresForBiomeStage -> {

            for (int biomeIdx = 0; biomeIdx < biomes.size(); biomeIdx++) {
                T biome = biomes.get(biomeIdx);
                List<Holder<PlacedFeature>> currentList =
                        featuresForBiomeStage.get(biome);

                if (currentList == null) continue;

                for (int currentHolderIndex = 0;
                     currentHolderIndex < currentList.size();
                     currentHolderIndex++) {

                    Holder<PlacedFeature> currentHolder =
                            currentList.get(currentHolderIndex);

                    for (int nextHolderIndex = currentHolderIndex + 1;
                         nextHolderIndex < currentList.size();
                         nextHolderIndex++) {

                        Holder<PlacedFeature> nextHolder =
                                currentList.get(nextHolderIndex);

                        int currentFeatureIDX = -1;
                        int nextFeatureIDX = -1;
                        Holder<Biome> biomeRuleSetter = null;

                        for (int previousBiomeIdx = 0;
                             previousBiomeIdx < biomeIdx - 1;
                             previousBiomeIdx++) {

                            T previousBiome = biomes.get(previousBiomeIdx);
                            List<Holder<PlacedFeature>> previousStage =
                                    featuresForBiomeStage.get(previousBiome);

                            if (previousStage == null) continue;

                            for (int k = 0; k < previousStage.size(); k++) {
                                Holder<PlacedFeature> h = previousStage.get(k);
                                if (h == currentHolder) currentFeatureIDX = k;
                                if (h == nextHolder) nextFeatureIDX = k;
                                if (currentFeatureIDX >= 0 && nextFeatureIDX >= 0)
                                    break;
                            }

                            biomeRuleSetter = previousBiome;
                            break;
                        }

                        if (nextFeatureIDX >= 0
                                && currentFeatureIDX > nextFeatureIDX) {

                            ResourceLocation currentBiomeLocation =
                                    biome.unwrapKey().map(ResourceKey::location).orElse(null);

                            ResourceLocation ruleSetterLocation =
                                    biomeRuleSetter.unwrapKey().map(ResourceKey::location).orElse(null);

                            String currentBiomeName =
                                    currentBiomeLocation == null ? "???" : currentBiomeLocation.toString();

                            String currentFeatureName =
                                    currentHolder.unwrapKey().map(ResourceKey::location)
                                            .map(ResourceLocation::toString).orElse("???");

                            String nextFeatureName =
                                    nextHolder.unwrapKey().map(ResourceKey::location)
                                            .map(ResourceLocation::toString).orElse("???");

                            String biomeRuleSetterName =
                                    ruleSetterLocation == null ? "???" : ruleSetterLocation.toString();

                            FeatureRecycler.LOGGER.warn(
                                    "Moved placed feature \"{}\" from index {} to index {} for biome \"{}\". Placed Feature index rules set by biome \"{}\".",
                                    currentFeatureName, currentHolderIndex, nextHolderIndex,
                                    currentBiomeName, biomeRuleSetterName
                            );
                            FeatureRecycler.LOGGER.warn(
                                    "Moved placed feature \"{}\" from index {} to index {} for biome \"{}\". Placed Feature index rules set by biome \"{}\".",
                                    nextFeatureName, nextHolderIndex, currentHolderIndex,
                                    currentBiomeName, biomeRuleSetterName
                            );
                            FeatureRecycler.LOGGER.warn(
                                    "Just prevented a crash between {} and {}! Please report the issues to their respective issue trackers.",
                                    currentBiomeLocation == null ? "???" : currentBiomeLocation.getNamespace(),
                                    ruleSetterLocation == null ? "???" : ruleSetterLocation.getNamespace()
                            );

                            crashesPrevented.incrementAndGet();
                            currentList.set(currentHolderIndex, nextHolder);
                            currentList.set(nextHolderIndex, currentHolder);
                        }
                    }
                }
            }
        });

        List<FeatureSorter.StepFeatureData> steps = new ArrayList<>();

        biomeTracker.forEach(stepData -> {
            List<PlacedFeature> organized = new ArrayList<>();
            Object2IntOpenHashMap<PlacedFeature> indexGetter =
                    new Object2IntOpenHashMap<>();

            int idx = 0;
            for (List<Holder<PlacedFeature>> value : stepData.values()) {
                for (Holder<PlacedFeature> holder : value) {
                    PlacedFeature pf = holder.value();
                    organized.add(pf);
                    indexGetter.put(pf, idx++);
                }
            }

            steps.add(new FeatureSorter.StepFeatureData(
                    organized,
                    indexGetter::getInt
            ));
        });

        FeatureRecycler.LOGGER.info(
                "Finished recycling features. Took {}ms",
                System.currentTimeMillis() - startTime
        );

        int prevented = crashesPrevented.get();
        if (prevented > 0) {
            FeatureRecycler.LOGGER.info(
                    "Feature Recycler just prevented {} crashes!",
                    prevented
            );
        }

        return steps;
    }
}

