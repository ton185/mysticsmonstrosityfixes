package com.name.mysticmonstrosityfixes.mixin.fixes;

import appeng.api.stacks.AEKey;
import com.extendedae_plus.util.storage.InfinityDataStorage;
import com.name.mysticmonstrosityfixes.ae2.MmfInfinityStorageAccess;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.math.BigInteger;

/**
 * Restores the total-count maintenance ACO 1.5.33's old InfinityDataStorage consistency mixin provided,
 * adapted to ExtendedAE Plus 1.6.4's split long/BigInteger maps.
 */
@Mixin(value = InfinityDataStorage.class, priority = 500, remap = false)
public abstract class InfinityDataStorageExactTotalFix implements MmfInfinityStorageAccess {

    @Shadow
    @Final
    public Object2LongMap<AEKey> longAmounts;

    @Shadow
    @Final
    public Object2ObjectMap<AEKey, BigInteger> bigAmounts;

    @Shadow
    private void setItemCount(BigInteger total) {
        throw new AssertionError();
    }

    @Override
    public Object2LongMap<AEKey> mmf$longAmounts() {
        return longAmounts;
    }

    @Override
    public Object2ObjectMap<AEKey, BigInteger> mmf$bigAmounts() {
        return bigAmounts;
    }

    @Override
    public BigInteger mmf$mapTotal() {
        BigInteger total = BigInteger.ZERO;
        for (long amount : longAmounts.values()) {
            if (amount > 0) {
                total = total.add(BigInteger.valueOf(amount));
            }
        }
        for (BigInteger amount : bigAmounts.values()) {
            if (amount != null && amount.signum() > 0) {
                total = total.add(amount);
            }
        }
        return total;
    }

    @Override
    public void mmf$setExactItemCount(BigInteger total) {
        setItemCount(total == null || total.signum() < 0 ? BigInteger.ZERO : total);
    }

    @Override
    public void mmf$recalculateItemCount() {
        setItemCount(mmf$mapTotal());
    }
}
