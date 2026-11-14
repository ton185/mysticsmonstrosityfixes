package com.name.mysticmonstrosityfixes.itemcollectors;

import net.minecraft.world.item.ItemStack;

public interface CollectorAccess {
    boolean mysticmonstrosityfixes$matchesFilter(ItemStack stack);

    ItemStack mysticmonstrosityfixes$collect(ItemStack stack);
}
