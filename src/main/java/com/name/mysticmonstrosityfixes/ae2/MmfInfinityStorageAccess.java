package com.name.mysticmonstrosityfixes.ae2;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;

import java.math.BigInteger;

/**
 * Bridge added to InfinityDataStorage for the ACO 1.5.33 exact BigInteger access path.
 */
public interface MmfInfinityStorageAccess {

    Object2LongMap<AEKey> mmf$longAmounts();

    Object2ObjectMap<AEKey, BigInteger> mmf$bigAmounts();

    BigInteger mmf$mapTotal();

    void mmf$setExactItemCount(BigInteger total);

    void mmf$recalculateItemCount();
}
