package com.name.mysticmonstrosityfixes;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Locale;
import java.util.concurrent.atomic.LongAdder;

public final class AtlasDownscalingTiming {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final LongAdder NANOS = new LongAdder();

    private AtlasDownscalingTiming() {
    }

    public static void add(long nanos) {
        NANOS.add(nanos);
    }

    public static void reset() {
        NANOS.reset();
    }

    public static void log() {
        long nanos = NANOS.sumThenReset();
        if (nanos <= 0L) {
            return;
        }
        String millis = String.format(
                Locale.getDefault(Locale.Category.FORMAT), "%,.2f", nanos / 1_000_000.0);
        LOGGER.info("MMF: Atlas downscaling took {} ms", millis);
    }
}
