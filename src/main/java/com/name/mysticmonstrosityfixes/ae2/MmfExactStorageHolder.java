package com.name.mysticmonstrosityfixes.ae2;

import com.extendedae_plus.util.storage.InfinityDataStorage;

/**
 * Resolves the storage currently backing an infinity BigInteger cell. The cell may not have a UUID
 * or a storage object until its first exact mutation.
 */
public interface MmfExactStorageHolder {

    InfinityDataStorage mmf$storage(boolean create);

    void mmf$recalculateItemCount();
}
