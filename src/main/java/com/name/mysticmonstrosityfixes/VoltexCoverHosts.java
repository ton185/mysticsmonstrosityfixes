package com.name.mysticmonstrosityfixes;

import com.jykito.industrialcore.block.ModBlocks;
import com.jykito.industrialcore.block.custom.cover.Covers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.Set;

/**
 * Voltex blocks whose baked models get a cover or connected-glass wrapper. Built once during the
 * bake event so the dynamic lookup does not walk the block registry.
 */
public final class VoltexCoverHosts {
    private static volatile Set<String> covers = Set.of();
    private static volatile Set<String> glass = Set.of();

    private VoltexCoverHosts() {
    }

    public static void rebuild() {
        Set<String> nextCovers = new HashSet<>();
        for (Block block : ForgeRegistries.BLOCKS) {
            if (block.defaultBlockState().hasProperty(Covers.COVERED)) {
                add(nextCovers, block);
            }
        }
        Set<String> nextGlass = new HashSet<>();
        add(nextGlass, ModBlocks.STRUCTURAL_GLASS.get());
        add(nextGlass, ModBlocks.STRUCTURAL_GLASS_ADVANCED.get());
        covers = nextCovers;
        glass = nextGlass;
    }

    public static boolean isCover(String namespace, String path) {
        return covers.contains(namespace + ":" + path);
    }

    public static boolean isGlass(String namespace, String path) {
        return glass.contains(namespace + ":" + path);
    }

    private static void add(Set<String> ids, Block block) {
        if (block == null) {
            return;
        }
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        if (id != null) {
            ids.add(id.getNamespace() + ":" + id.getPath());
        }
    }
}
