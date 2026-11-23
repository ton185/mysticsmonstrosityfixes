package com.name.mysticmonstrosityfixes.apothic;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class ApothicAttributeValueFormat {
    private static final String VALUE_PREFIX = "attributeslib.value.";
    private static final double MILLION = 1_000_000.0D;
    private static final double BILLION = 1_000_000_000.0D;
    private static final double TRILLION = 1_000_000_000_000.0D;
    private static final double SCIENTIFIC_CUTOFF = 1_000_000_000_000_000.0D;
    private static final String[] SUFFIXES = {"M", "B", "T"};
    private static final ThreadLocal<DecimalFormat> PLAIN_FORMAT = ThreadLocal.withInitial(
            () -> new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.getDefault(Locale.Category.FORMAT))));
    private static final ThreadLocal<DecimalFormat> COMPACT_FORMAT = ThreadLocal.withInitial(
            () -> new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT)));

    private ApothicAttributeValueFormat() {
    }

    public static MutableComponent formatComponent(MutableComponent component) {
        if (component == null) {
            return null;
        }
        MutableComponent formatted = component;
        ComponentContents contents = component.getContents();
        if (contents instanceof TranslatableContents translatable) {
            Object[] args = translatable.getArgs();
            Object[] formattedArgs = formatArgs(translatable.getKey(), args);
            if (formattedArgs != args) {
                formatted = MutableComponent.create(new TranslatableContents(
                        translatable.getKey(), translatable.getFallback(), formattedArgs));
                formatted.setStyle(component.getStyle());
            }
        }
        if (!component.getSiblings().isEmpty()) {
            MutableComponent withSiblings = formatted == component ? component.copy() : formatted;
            withSiblings.getSiblings().clear();
            for (Component sibling : component.getSiblings()) {
                withSiblings.append(sibling instanceof MutableComponent mutable
                        ? formatComponent(mutable)
                        : sibling);
            }
            withSiblings.setStyle(component.getStyle());
            formatted = withSiblings;
        }
        return formatted;
    }

    public static String formatNumber(double value) {
        if (!Double.isFinite(value)) {
            return PLAIN_FORMAT.get().format(value);
        }
        double absolute = Math.abs(value);
        if (absolute >= SCIENTIFIC_CUTOFF) {
            return formatScientific(value);
        }
        if (absolute >= MILLION) {
            return formatSuffixed(value);
        }
        return PLAIN_FORMAT.get().format(value);
    }

    private static String formatSuffixed(double value) {
        double absolute = Math.abs(value);
        int suffix = absolute >= TRILLION ? 2 : absolute >= BILLION ? 1 : 0;
        String text = COMPACT_FORMAT.get().format(value / suffixDivisor(suffix));
        if (Math.abs(Double.parseDouble(text)) < 1000.0D) {
            return text + SUFFIXES[suffix];
        }
        suffix++;
        if (suffix >= SUFFIXES.length) {
            return formatScientific(value);
        }
        text = COMPACT_FORMAT.get().format(value / suffixDivisor(suffix));
        return Math.abs(Double.parseDouble(text)) < 1000.0D
                ? text + SUFFIXES[suffix]
                : formatScientific(value);
    }

    private static String formatScientific(double value) {
        int exponent = (int) Math.floor(Math.log10(Math.abs(value)));
        String mantissa = COMPACT_FORMAT.get().format(value / Math.pow(10.0D, exponent));
        if (Math.abs(Double.parseDouble(mantissa)) >= 10.0D) {
            exponent++;
            mantissa = COMPACT_FORMAT.get().format(value / Math.pow(10.0D, exponent));
        }
        return mantissa + "e" + exponent;
    }

    private static double suffixDivisor(int suffix) {
        return switch (suffix) {
            case 0 -> MILLION;
            case 1 -> BILLION;
            default -> TRILLION;
        };
    }

    private static Object[] formatArgs(String key, Object[] args) {
        if (key.startsWith(VALUE_PREFIX) && args.length == 1 && args[0] instanceof String text) {
            String formatted = formatNumberText(text);
            return formatted.equals(text) ? args : new Object[]{formatted};
        }
        Object[] formatted = args;
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof MutableComponent child) {
                MutableComponent replacement = formatComponent(child);
                if (replacement != child) {
                    if (formatted == args) {
                        formatted = args.clone();
                    }
                    formatted[i] = replacement;
                }
            }
        }
        return formatted;
    }

    private static String formatNumberText(String text) {
        try {
            return formatNumber(Double.parseDouble(text));
        } catch (NumberFormatException exception) {
            return text;
        }
    }
}
