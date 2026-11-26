package com.name.mysticmonstrosityfixes.vram_usage;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.BaseTSD.DWORD_PTR;
import com.sun.jna.platform.win32.Pdh;
import com.sun.jna.platform.win32.PdhUtil;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinNT.HANDLEByReference;
import com.sun.jna.platform.win32.WinReg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Windows VRAM provider. The GPU Adapter Memory performance object reports dedicated usage for
 * every WDDM adapter, so it works for NVIDIA, AMD, and Intel alike. The matching total comes from
 * the 64-bit memory size each display driver writes to the registry, which avoids the 4 GB cap of
 * Win32_VideoController.AdapterRAM. All native access is best-effort; any failure returns no data.
 */
final class WindowsVram {
    private static final String PERFORMANCE_OBJECT = "GPU Adapter Memory";
    private static final String USAGE_COUNTER = "Dedicated Usage";
    private static final String DISPLAY_CLASS_KEY =
            "SYSTEM\\CurrentControlSet\\Control\\Class\\{4d36e968-e325-11ce-bfc1-08002be10318}";
    private static final Pattern ADAPTER_KEY = Pattern.compile("\\d{4}");
    private static final long TOTAL_REFRESH_NANOS = 60_000_000_000L;
    // PERF_DETAIL_WIZARD
    private static final int PERF_DETAIL_WIZARD = 100;
    // PDH_CSTATUS_VALID_DATA and PDH_CSTATUS_NEW_DATA
    private static final int PDH_STATUS_VALID = 0;
    private static final int PDH_STATUS_NEW = 1;

    private static final Map<String, String> LOCALIZED_NAMES = new HashMap<>();
    private static long cachedTotal;
    private static long totalRefreshNanos;

    private WindowsVram() {
    }

    static VramUsage.Usage query() {
        long total = totalBytes();
        if (total <= 0L) {
            return null;
        }
        long used = usedBytes();
        if (used < 0L) {
            return null;
        }
        return new VramUsage.Usage(Math.min(used, total), total);
    }

    static long totalBytes() {
        long now = System.nanoTime();
        if (cachedTotal > 0L && now < totalRefreshNanos) {
            return cachedTotal;
        }
        totalRefreshNanos = now + TOTAL_REFRESH_NANOS;
        try {
            long total = 0L;
            for (String subkey : Advapi32Util.registryGetKeys(WinReg.HKEY_LOCAL_MACHINE, DISPLAY_CLASS_KEY)) {
                if (!ADAPTER_KEY.matcher(subkey).matches()) {
                    continue;
                }
                Map<String, Object> values = Advapi32Util.registryGetValues(
                        WinReg.HKEY_LOCAL_MACHINE, DISPLAY_CLASS_KEY + "\\" + subkey);
                Object value = values.get("HardwareInformation.qwMemorySize");
                if (value instanceof Number number) {
                    total += Math.max(0L, number.longValue());
                    continue;
                }
                value = values.get("HardwareInformation.MemorySize");
                if (value instanceof Number number) {
                    total += Integer.toUnsignedLong(number.intValue());
                }
            }
            cachedTotal = total;
        } catch (Throwable ignored) {
            cachedTotal = 0L;
        }
        return cachedTotal;
    }

    private static long usedBytes() {
        try {
            String object = localizedName(PERFORMANCE_OBJECT);
            String counter = localizedName(USAGE_COUNTER);
            if (object == null || counter == null) {
                return -1L;
            }
            PdhUtil.PdhEnumObjectItems items =
                    PdhUtil.PdhEnumObjectItems(null, null, object, PERF_DETAIL_WIZARD);
            if (items.getInstances().isEmpty()) {
                return -1L;
            }
            HANDLEByReference queryRef = new HANDLEByReference();
            if (Pdh.INSTANCE.PdhOpenQuery(null, new DWORD_PTR(0), queryRef) != 0) {
                return -1L;
            }
            HANDLE query = queryRef.getValue();
            try {
                List<HANDLE> counters = new ArrayList<>();
                for (String instance : items.getInstances()) {
                    HANDLEByReference counterRef = new HANDLEByReference();
                    String path = "\\" + object + "(" + instance + ")\\" + counter;
                    if (Pdh.INSTANCE.PdhAddCounter(query, path, new DWORD_PTR(0), counterRef) == 0) {
                        counters.add(counterRef.getValue());
                    }
                }
                if (counters.isEmpty() || Pdh.INSTANCE.PdhCollectQueryData(query) != 0) {
                    return -1L;
                }
                long used = 0L;
                boolean found = false;
                for (HANDLE handle : counters) {
                    Pdh.PDH_RAW_COUNTER raw = new Pdh.PDH_RAW_COUNTER();
                    if (Pdh.INSTANCE.PdhGetRawCounterValue(handle, null, raw) == 0
                            && (raw.CStatus == PDH_STATUS_VALID || raw.CStatus == PDH_STATUS_NEW)
                            && raw.FirstValue >= 0L) {
                        used += raw.FirstValue;
                        found = true;
                    }
                }
                return found ? used : -1L;
            } finally {
                Pdh.INSTANCE.PdhCloseQuery(query);
            }
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    /**
     * Resolves the localized performance object and counter names by their locale-independent
     * English names, so the counters can be read on non-English Windows installs.
     */
    private static String localizedName(String englishName) {
        if (LOCALIZED_NAMES.containsKey(englishName)) {
            return LOCALIZED_NAMES.get(englishName);
        }
        String name = null;
        try {
            int index = PdhUtil.PdhLookupPerfIndexByEnglishName(englishName);
            if (index > 0) {
                String localized = PdhUtil.PdhLookupPerfNameByIndex(null, index);
                if (localized != null && !localized.isBlank()) {
                    name = localized;
                }
            }
        } catch (Throwable ignored) {
        }
        if (name != null) {
            LOCALIZED_NAMES.put(englishName, name);
        }
        return name;
    }
}
