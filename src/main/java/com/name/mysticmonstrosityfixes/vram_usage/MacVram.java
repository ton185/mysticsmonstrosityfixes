package com.name.mysticmonstrosityfixes.vram_usage;

import com.sun.jna.Pointer;
import com.sun.jna.platform.mac.CoreFoundation.CFDictionaryRef;
import com.sun.jna.platform.mac.CoreFoundation.CFMutableDictionaryRef;
import com.sun.jna.platform.mac.CoreFoundation.CFNumberRef;
import com.sun.jna.platform.mac.CoreFoundation.CFStringRef;
import com.sun.jna.platform.mac.IOKit.IOIterator;
import com.sun.jna.platform.mac.IOKit.IORegistryEntry;
import com.sun.jna.platform.mac.IOKitUtil;

import java.lang.management.ManagementFactory;

/**
 * macOS VRAM provider. Intel Macs report used and free VRAM in the IOAccelerator performance
 * dictionary, with the driver's VRAM property as a fallback total. Apple Silicon has no dedicated
 * VRAM, so its "In use system memory" counter is compared against physical memory. Every native
 * handle is released before returning, and any failure returns no data.
 */
final class MacVram {
    private static final String PERFORMANCE_STATISTICS = "PerformanceStatistics";
    private static final String USED_VRAM = "vramUsedBytes";
    private static final String FREE_VRAM = "vramFreeBytes";
    private static final String IN_USE_SYSTEM_MEMORY = "In use system memory";
    private static final String ALLOCATED_SYSTEM_MEMORY = "Alloc system memory";
    private static final String TOTAL_VRAM_MB = "VRAM,totalMB";
    private static final long MEGABYTE = 1024L * 1024L;

    private static long cachedPhysicalMemory;

    private MacVram() {
    }

    static VramUsage.Usage query() {
        try {
            return queryUnchecked();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static VramUsage.Usage queryUnchecked() {
        IOIterator iterator = IOKitUtil.getMatchingServices("IOAccelerator");
        if (iterator == null) {
            return null;
        }
        CFStringRef statisticsKey = CFStringRef.createCFString(PERFORMANCE_STATISTICS);
        CFStringRef usedKey = CFStringRef.createCFString(USED_VRAM);
        CFStringRef freeKey = CFStringRef.createCFString(FREE_VRAM);
        CFStringRef inUseKey = CFStringRef.createCFString(IN_USE_SYSTEM_MEMORY);
        CFStringRef allocatedKey = CFStringRef.createCFString(ALLOCATED_SYSTEM_MEMORY);
        CFStringRef totalMbKey = CFStringRef.createCFString(TOTAL_VRAM_MB);
        long bestUsed = 0L;
        long bestTotal = 0L;
        boolean found = false;
        try {
            IORegistryEntry service = iterator.next();
            while (service != null) {
                try {
                    CFMutableDictionaryRef properties = service.createCFProperties();
                    if (properties != null) {
                        try {
                            VramUsage.Usage usage = readCard(
                                    properties, statisticsKey, usedKey, freeKey, inUseKey, allocatedKey, totalMbKey);
                            if (usage != null && (!found || usage.totalBytes() > bestTotal)) {
                                bestUsed = usage.usedBytes();
                                bestTotal = usage.totalBytes();
                                found = true;
                            }
                        } finally {
                            properties.release();
                        }
                    }
                } finally {
                    service.release();
                }
                service = iterator.next();
            }
        } finally {
            iterator.release();
            statisticsKey.release();
            usedKey.release();
            freeKey.release();
            inUseKey.release();
            allocatedKey.release();
            totalMbKey.release();
        }
        return found ? new VramUsage.Usage(bestUsed, bestTotal) : null;
    }

    private static VramUsage.Usage readCard(
            CFDictionaryRef properties,
            CFStringRef statisticsKey,
            CFStringRef usedKey,
            CFStringRef freeKey,
            CFStringRef inUseKey,
            CFStringRef allocatedKey,
            CFStringRef totalMbKey
    ) {
        Long totalMb = number(properties, totalMbKey);
        Long statisticsUsed = null;
        Long statisticsFree = null;
        Long inUse = null;
        Long allocated = null;
        Pointer statisticsPointer = properties.getValue(statisticsKey);
        if (statisticsPointer != null) {
            CFDictionaryRef statistics = new CFDictionaryRef(statisticsPointer);
            statisticsUsed = number(statistics, usedKey);
            statisticsFree = number(statistics, freeKey);
            inUse = number(statistics, inUseKey);
            allocated = number(statistics, allocatedKey);
        }

        long used = firstNonNegative(statisticsUsed, inUse, allocated);
        long total;
        if (used >= 0L && statisticsFree != null && statisticsFree > 0L) {
            total = used + statisticsFree;
        } else if (used >= 0L && totalMb != null && totalMb > 0L) {
            total = totalMb * MEGABYTE;
        } else if (used >= 0L) {
            total = physicalMemory();
        } else if (statisticsFree != null && statisticsFree > 0L && totalMb != null && totalMb > 0L) {
            total = totalMb * MEGABYTE;
            used = total - statisticsFree;
        } else {
            return null;
        }
        if (used < 0L || total <= 0L) {
            return null;
        }
        return new VramUsage.Usage(Math.min(used, total), total);
    }

    private static Long number(CFDictionaryRef dictionary, CFStringRef key) {
        Pointer value = dictionary.getValue(key);
        return value == null ? null : new CFNumberRef(value).longValue();
    }

    private static long firstNonNegative(Long... values) {
        for (Long value : values) {
            if (value != null && value >= 0L) {
                return value;
            }
        }
        return -1L;
    }

    private static synchronized long physicalMemory() {
        if (cachedPhysicalMemory > 0L) {
            return cachedPhysicalMemory;
        }
        try {
            java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
            if (bean instanceof com.sun.management.OperatingSystemMXBean extended) {
                cachedPhysicalMemory = extended.getTotalMemorySize();
            }
        } catch (Throwable ignored) {
            cachedPhysicalMemory = 0L;
        }
        return cachedPhysicalMemory;
    }
}
