package com.name.mysticmonstrosityfixes.ae2;

import appeng.api.stacks.AEKey;
import com.extendedae_plus.util.storage.InfinityDataStorage;
import it.unimi.dsi.fastutil.objects.AbstractObject2ObjectMap;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectIterators;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Stable, live Object2ObjectMap view over ExtendedAE Plus 1.6.4's split long/BigInteger maps.
 * ACO's exact storage bridge compares map identity between its simulation and commit, and mutates the
 * map directly, so this wrapper must keep the same object for the inventory's lifetime and write
 * every change through to the backing InfinityDataStorage.
 */
public final class ExactAmountView extends AbstractObject2ObjectMap<AEKey, BigInteger> {

    private final MmfExactStorageHolder holder;

    public ExactAmountView(MmfExactStorageHolder holder) {
        this.holder = holder;
    }

    @Override
    public BigInteger get(Object key) {
        if (!(key instanceof AEKey aeKey)) {
            return null;
        }
        InfinityDataStorage storage = holder.mmf$storage(false);
        if (storage == null) {
            return null;
        }
        MmfInfinityStorageAccess access = (MmfInfinityStorageAccess) storage;
        if (access.mmf$longAmounts().containsKey(aeKey)) {
            long amount = access.mmf$longAmounts().getLong(aeKey);
            if (amount > 0) {
                return BigInteger.valueOf(amount);
            }
        }
        BigInteger amount = access.mmf$bigAmounts().get(aeKey);
        return amount != null && amount.signum() > 0 ? amount : null;
    }

    @Override
    public boolean containsKey(Object key) {
        return get(key) != null;
    }

    @Override
    public BigInteger put(AEKey key, BigInteger value) {
        BigInteger previous = get(key);
        InfinityDataStorage storage = holder.mmf$storage(true);
        if (storage == null || key == null) {
            return previous;
        }
        MmfInfinityStorageAccess access = (MmfInfinityStorageAccess) storage;
        if (value == null || value.signum() <= 0) {
            access.mmf$longAmounts().removeLong(key);
            access.mmf$bigAmounts().remove(key);
        } else if (value.bitLength() <= 63) {
            access.mmf$longAmounts().put(key, value.longValue());
            access.mmf$bigAmounts().remove(key);
        } else {
            access.mmf$bigAmounts().put(key, value);
            access.mmf$longAmounts().removeLong(key);
        }
        holder.mmf$recalculateItemCount();
        return previous;
    }

    @Override
    public BigInteger remove(Object key) {
        BigInteger previous = get(key);
        if (!(key instanceof AEKey aeKey)) {
            return previous;
        }
        InfinityDataStorage storage = holder.mmf$storage(false);
        if (storage == null) {
            return previous;
        }
        MmfInfinityStorageAccess access = (MmfInfinityStorageAccess) storage;
        access.mmf$longAmounts().removeLong(aeKey);
        access.mmf$bigAmounts().remove(aeKey);
        holder.mmf$recalculateItemCount();
        return previous;
    }

    @Override
    public void clear() {
        InfinityDataStorage storage = holder.mmf$storage(false);
        if (storage == null) {
            return;
        }
        MmfInfinityStorageAccess access = (MmfInfinityStorageAccess) storage;
        access.mmf$longAmounts().clear();
        access.mmf$bigAmounts().clear();
        holder.mmf$recalculateItemCount();
    }

    @Override
    public int size() {
        InfinityDataStorage storage = holder.mmf$storage(false);
        if (storage == null) {
            return 0;
        }
        MmfInfinityStorageAccess access = (MmfInfinityStorageAccess) storage;
        int size = 0;
        for (long value : access.mmf$longAmounts().values()) {
            if (value > 0) {
                size++;
            }
        }
        for (var entry : access.mmf$bigAmounts().entrySet()) {
            BigInteger value = entry.getValue();
            if (value != null && value.signum() > 0 && (!access.mmf$longAmounts().containsKey(entry.getKey())
                    || access.mmf$longAmounts().getLong(entry.getKey()) <= 0)) {
                size++;
            }
        }
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public ObjectSet<Object2ObjectMap.Entry<AEKey, BigInteger>> object2ObjectEntrySet() {
        return new AbstractObjectSet<>() {
            @Override
            public ObjectIterator<Object2ObjectMap.Entry<AEKey, BigInteger>> iterator() {
                List<Object2ObjectMap.Entry<AEKey, BigInteger>> entries = new ArrayList<>();
                InfinityDataStorage storage = holder.mmf$storage(false);
                if (storage != null) {
                    MmfInfinityStorageAccess access = (MmfInfinityStorageAccess) storage;
                    for (var entry : access.mmf$longAmounts().object2LongEntrySet()) {
                        long amount = entry.getLongValue();
                        if (amount > 0) {
                            entries.add(new BasicEntry<>(entry.getKey(), BigInteger.valueOf(amount)));
                        }
                    }
                    for (var entry : access.mmf$bigAmounts().object2ObjectEntrySet()) {
                        BigInteger amount = entry.getValue();
                        if (amount != null && amount.signum() > 0 && (!access.mmf$longAmounts().containsKey(entry.getKey())
                                || access.mmf$longAmounts().getLong(entry.getKey()) <= 0)) {
                            entries.add(new BasicEntry<>(entry.getKey(), amount));
                        }
                    }
                }
                return ObjectIterators.asObjectIterator(entries.iterator());
            }

            @Override
            public int size() {
                return ExactAmountView.this.size();
            }
        };
    }
}
