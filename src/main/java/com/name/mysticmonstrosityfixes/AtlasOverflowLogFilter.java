package com.name.mysticmonstrosityfixes;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;

/**
 * Drops ModernFix's expected "Requested atlas size … exceeds maximum …" line while a block-atlas
 * page split is running. The stitcher still throws, and that throw is what opens the next page.
 * Log4j decides that call with the four-argument filter, which does not go through {@link #filter(LogEvent)}.
 */
public final class AtlasOverflowLogFilter extends AbstractFilter {
    @Override
    public Filter.Result filter(LogEvent event) {
        if (event.getMessage() == null) {
            return Filter.Result.NEUTRAL;
        }
        Filter.Result pattern = Filter.Result.NEUTRAL;
        try {
            pattern = denyOverflow(event.getMessage().getFormat());
        } catch (RuntimeException ignored) {
            pattern = Filter.Result.NEUTRAL;
        }
        if (pattern == Filter.Result.DENY) {
            return pattern;
        }
        return denyOverflow(event.getMessage().getFormattedMessage());
    }

    @Override
    public Filter.Result filter(Logger logger, Level level, Marker marker, String message, Object... params) {
        return denyOverflow(message);
    }

    private static Filter.Result denyOverflow(String text) {
        if (!BlockAtlasPages.isPaging() || text == null) {
            return Filter.Result.NEUTRAL;
        }
        if (text.startsWith("Requested atlas size ") && text.contains(" exceeds maximum of ")) {
            return Filter.Result.DENY;
        }
        return Filter.Result.NEUTRAL;
    }
}
