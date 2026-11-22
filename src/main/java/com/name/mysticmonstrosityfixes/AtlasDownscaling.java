package com.name.mysticmonstrosityfixes;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class AtlasDownscaling {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long MAX_DIVISOR_SHIFT = 40L;

    private static volatile List<Rule> rules = List.of();
    private static volatile List<String> cachedEntries;
    private static boolean unavailableLogged;

    private AtlasDownscaling() {
    }

    public static SpriteContents create(
            ResourceLocation name,
            FrameSize frameSize,
            NativeImage image,
            AnimationMetadataSection animation
    ) {
        int factor = factorFor(name);
        if (factor < 1) {
            return new SpriteContents(name, frameSize, image, animation, null);
        }
        long divisor = divisor(factor);
        int width = (int) Math.max(1L, frameSize.width() / divisor);
        int height = (int) Math.max(1L, frameSize.height() / divisor);
        if (width == frameSize.width() && height == frameSize.height()) {
            return new SpriteContents(name, frameSize, image, animation, null);
        }
        FrameSize scaledSize = new FrameSize(width, height);
        NativeImage scaled;
        try {
            scaled = downscale(image, frameSize, scaledSize);
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not downscale atlas sprite {} by 1/{}; keeping the {}x{} image",
                    name, divisor, frameSize.width(), frameSize.height(), exception);
            return new SpriteContents(name, frameSize, image, animation, null);
        }
        try {
            SpriteContents contents = new SpriteContents(name, scaledSize, scaled, animation, null);
            image.close();
            LOGGER.debug("Downscaled atlas sprite {} from {}x{} to {}x{} (1/{})",
                    name, frameSize.width(), frameSize.height(), width, height, divisor);
            return contents;
        } catch (RuntimeException | Error exception) {
            scaled.close();
            throw exception;
        }
    }

    private static int factorFor(ResourceLocation name) {
        List<Rule> current = rules();
        if (current.isEmpty()) {
            return 0;
        }
        String id = name.toString();
        for (Rule rule : current) {
            if (rule.pattern().matcher(id).matches()) {
                return rule.factor();
            }
        }
        return 0;
    }

    private static List<Rule> rules() {
        List<String> entries;
        try {
            entries = new ArrayList<>(MysticMixinConfigSpec.ATLAS_DOWNSCALING_ENTRIES.get());
        } catch (RuntimeException exception) {
            if (!unavailableLogged) {
                unavailableLogged = true;
                LOGGER.warn("Atlas downscaling entries are unavailable; no sprites will be downscaled", exception);
            }
            return List.of();
        }
        List<String> known = cachedEntries;
        if (known != null && known.equals(entries)) {
            return rules;
        }
        synchronized (AtlasDownscaling.class) {
            if (cachedEntries != null && cachedEntries.equals(entries)) {
                return rules;
            }
            List<Rule> parsed = parse(entries);
            rules = parsed;
            cachedEntries = entries;
            return parsed;
        }
    }

    private static List<Rule> parse(List<String> entries) {
        List<Rule> parsed = new ArrayList<>(entries.size());
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            int separator = entry.lastIndexOf('=');
            if (separator <= 0 || separator == entry.length() - 1) {
                LOGGER.warn("Ignoring atlas downscaling entry \"{}\": expected \"<sprite id regex>=<factor>\"", entry);
                continue;
            }
            String regex = entry.substring(0, separator).trim();
            String factorText = entry.substring(separator + 1).trim();
            int factor;
            try {
                factor = Integer.parseInt(factorText);
            } catch (NumberFormatException exception) {
                LOGGER.warn("Ignoring atlas downscaling entry \"{}\": \"{}\" is not a whole-number factor",
                        entry, factorText);
                continue;
            }
            if (factor < 1) {
                LOGGER.warn("Ignoring atlas downscaling entry \"{}\": the factor must be at least 1", entry);
                continue;
            }
            Pattern pattern;
            try {
                pattern = Pattern.compile(regex);
            } catch (PatternSyntaxException exception) {
                LOGGER.warn("Ignoring atlas downscaling entry \"{}\": invalid regex: {}",
                        entry, exception.getDescription());
                continue;
            }
            parsed.add(new Rule(pattern, factor));
        }
        if (!parsed.isEmpty()) {
            LOGGER.info("Atlas downscaling is using {} rule(s)", parsed.size());
        }
        return List.copyOf(parsed);
    }

    private static long divisor(int factor) {
        return 1L << Math.min(factor, MAX_DIVISOR_SHIFT);
    }

    private static NativeImage downscale(NativeImage source, FrameSize sourceFrame, FrameSize targetFrame) {
        int columns = source.getWidth() / sourceFrame.width();
        int rows = source.getHeight() / sourceFrame.height();
        NativeImage target = new NativeImage(targetFrame.width() * columns, targetFrame.height() * rows, false);
        try {
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < columns; column++) {
                    downscaleFrame(
                            source,
                            sourceFrame,
                            targetFrame,
                            target,
                            column * sourceFrame.width(),
                            row * sourceFrame.height(),
                            column * targetFrame.width(),
                            row * targetFrame.height()
                    );
                }
            }
            return target;
        } catch (RuntimeException | Error exception) {
            target.close();
            throw exception;
        }
    }

    private static void downscaleFrame(
            NativeImage source,
            FrameSize sourceFrame,
            FrameSize targetFrame,
            NativeImage target,
            int sourceX,
            int sourceY,
            int targetX,
            int targetY
    ) {
        int sourceWidth = sourceFrame.width();
        int sourceHeight = sourceFrame.height();
        int targetWidth = targetFrame.width();
        int targetHeight = targetFrame.height();
        for (int y = 0; y < targetHeight; y++) {
            int startY = sourceY + (int) ((long) y * sourceHeight / targetHeight);
            int endY = sourceY + (int) Math.min(
                    sourceHeight,
                    Math.max(
                            (long) (y + 1) * sourceHeight / targetHeight,
                            (long) y * sourceHeight / targetHeight + 1L
                    )
            );
            for (int x = 0; x < targetWidth; x++) {
                int startX = sourceX + (int) ((long) x * sourceWidth / targetWidth);
                int endX = sourceX + (int) Math.min(
                        sourceWidth,
                        Math.max(
                                (long) (x + 1) * sourceWidth / targetWidth,
                                (long) x * sourceWidth / targetWidth + 1L
                        )
                );
                long alpha = 0L;
                long blue = 0L;
                long green = 0L;
                long red = 0L;
                for (int sampleY = startY; sampleY < endY; sampleY++) {
                    for (int sampleX = startX; sampleX < endX; sampleX++) {
                        int pixel = source.getPixelRGBA(sampleX, sampleY);
                        int a = pixel >>> 24;
                        alpha += a;
                        blue += (long) (pixel >> 16 & 255) * a;
                        green += (long) (pixel >> 8 & 255) * a;
                        red += (long) (pixel & 255) * a;
                    }
                }
                long samples = (long) (endX - startX) * (endY - startY);
                int a = (int) ((alpha + samples / 2L) / samples);
                int b = alpha == 0L ? 0 : (int) ((blue + alpha / 2L) / alpha);
                int g = alpha == 0L ? 0 : (int) ((green + alpha / 2L) / alpha);
                int r = alpha == 0L ? 0 : (int) ((red + alpha / 2L) / alpha);
                target.setPixelRGBA(targetX + x, targetY + y, a << 24 | b << 16 | g << 8 | r);
            }
        }
    }

    private record Rule(Pattern pattern, int factor) {
    }
}
