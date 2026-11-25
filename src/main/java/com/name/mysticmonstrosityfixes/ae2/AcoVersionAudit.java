package com.name.mysticmonstrosityfixes.ae2;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * One-time diagnostic for the ACO version-audit bypass. The audit's AE2 version requirement is
 * skipped, so patch-level builds keep the compiled crafting graph enabled; an actually incompatible
 * build will fail later when that feature is used.
 */
public final class AcoVersionAudit {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile boolean notified;

    private AcoVersionAudit() {
    }

    public static void notifyBypassed() {
        if (!notified) {
            notified = true;
            LOGGER.info("Skipped ACO's AE2 experimental version audit; the compiled crafting graph stays enabled");
        }
    }
}
