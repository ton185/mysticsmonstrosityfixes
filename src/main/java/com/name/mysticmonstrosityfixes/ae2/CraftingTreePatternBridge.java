package com.name.mysticmonstrosityfixes.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

/**
 * Runs the ACO memoized lookup and InsaneAE2's provider-card filter for the bridge mixin. This class
 * is only loaded when the bridge is actually applied to {@code CraftingTreeNode}, after AE2 and the
 * two addons have loaded.
 */
public final class CraftingTreePatternBridge {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ACO_MEMO = "com.syaru.ae2craftingoptimizer.optimization.CraftingCalculationMemo";

    private static volatile Method memoMethod;
    private static volatile boolean memoResolved;

    private CraftingTreePatternBridge() {
    }

    @SuppressWarnings("unchecked")
    public static Collection<IPatternDetails> combine(Object jobArgument, ICraftingService service, AEKey key) {
        Collection<IPatternDetails> candidates = candidates(jobArgument, service, key);
        if (jobArgument == null || candidates.isEmpty()) {
            return candidates;
        }
        try {
            return InsanePatternFilter.filter(jobArgument, service, candidates);
        } catch (Throwable throwable) {
            LOGGER.debug("Could not apply InsaneAE2's provider-card filter; keeping ACO's memoized candidates", throwable);
            return candidates;
        }
    }

    @SuppressWarnings("unchecked")
    private static Collection<IPatternDetails> candidates(
            Object job, ICraftingService service, AEKey key
    ) {
        Method method = memoMethod();
        if (method != null && job != null) {
            try {
                Object result = method.invoke(null, job, service, key);
                if (result instanceof Collection<?> collection) {
                    return (Collection<IPatternDetails>) collection;
                }
            } catch (Throwable throwable) {
                LOGGER.debug("ACO's crafting calculation memoizer failed; querying AE2 directly", throwable);
            }
        }
        try {
            Collection<IPatternDetails> result = service.getCraftingFor(key);
            return result == null ? List.of() : result;
        } catch (Throwable throwable) {
            LOGGER.debug("Could not query AE2 for crafting patterns", throwable);
            return List.of();
        }
    }

    private static Method memoMethod() {
        if (memoResolved) {
            return memoMethod;
        }
        synchronized (CraftingTreePatternBridge.class) {
            if (memoResolved) {
                return memoMethod;
            }
            try {
                Class<?> memo = classFor(ACO_MEMO);
                Method resolved = memo.getMethod("patternCandidates", Object.class, ICraftingService.class, AEKey.class);
                try {
                    resolved.setAccessible(true);
                } catch (Throwable ignored) {
                }
                memoMethod = resolved;
            } catch (Throwable throwable) {
                LOGGER.debug("ACO's crafting calculation memoizer is unavailable", throwable);
                memoMethod = null;
            }
            memoResolved = true;
            return memoMethod;
        }
    }

    private static Class<?> classFor(String name) throws ClassNotFoundException {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        if (context != null) {
            try {
                return Class.forName(name, false, context);
            } catch (ClassNotFoundException ignored) {
            }
        }
        return Class.forName(name, false, CraftingTreePatternBridge.class.getClassLoader());
    }
}
