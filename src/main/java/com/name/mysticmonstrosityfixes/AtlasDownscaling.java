package com.name.mysticmonstrosityfixes;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.name.mysticmonstrosityfixes.mixin.fixes.AtlasDownscalingNativeImageAccessor;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class AtlasDownscaling {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long MAX_DIVISOR_SHIFT = 40L;
    private static final ThreadLocal<MatchState> MATCH_STATE = ThreadLocal.withInitial(MatchState::new);

    private static volatile List<Rule> rules;

    private AtlasDownscaling() {
    }

    public static void invalidateRules() {
        synchronized (AtlasDownscaling.class) {
            rules = null;
        }
    }

    public static SpriteContents create(
            ResourceLocation name,
            FrameSize frameSize,
            NativeImage image,
            AnimationMetadataSection animation
    ) {
        Rule rule = ruleFor(name);
        if (rule == null) {
            return new SpriteContents(name, frameSize, image, animation, null);
        }
        long divisor = divisor(rule.factor());
        int width = (int) Math.max(1L, frameSize.width() / divisor);
        int height = (int) Math.max(1L, frameSize.height() / divisor);
        if (width == frameSize.width() && height == frameSize.height()) {
            return new SpriteContents(name, frameSize, image, animation, null);
        }
        FrameSize scaledSize = new FrameSize(width, height);
        NativeImage scaled;
        long started = System.nanoTime();
        try {
            scaled = downscale(image, frameSize, scaledSize);
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not downscale atlas sprite {} by 1/{}; keeping the {}x{} image",
                    name, divisor, frameSize.width(), frameSize.height(), exception);
            return new SpriteContents(name, frameSize, image, animation, null);
        } finally {
            AtlasDownscalingTiming.add(System.nanoTime() - started);
        }
        try {
            SpriteContents contents = new SpriteContents(name, scaledSize, scaled, animation, null);
            image.close();
            AtlasDownscalingLog.log(name, rule.label(), width, height);
            return contents;
        } catch (RuntimeException | Error exception) {
            scaled.close();
            throw exception;
        }
    }

    private static Rule ruleFor(ResourceLocation name) {
        List<Rule> current = rules();
        if (current.isEmpty()) {
            return null;
        }
        MatchState state = MATCH_STATE.get();
        state.update(current, name);
        Matcher[] matchers = state.matchers;
        for (int i = 0; i < matchers.length; i++) {
            if (matchers[i].reset().matches()) {
                return current.get(i);
            }
        }
        return null;
    }

    private static List<Rule> rules() {
        List<Rule> current = rules;
        if (current != null) {
            return current;
        }
        synchronized (AtlasDownscaling.class) {
            if (rules == null) {
                rules = parse(AtlasDownscalingEntries.read());
            }
            return rules;
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
            parsed.add(new Rule(pattern, factor, regex + "=" + factor));
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
        if (source.format() != NativeImage.Format.RGBA) {
            throw new IllegalStateException("Unsupported atlas sprite format " + source.format());
        }
        int columns = source.getWidth() / sourceFrame.width();
        int rows = source.getHeight() / sourceFrame.height();
        NativeImage target = new NativeImage(targetFrame.width() * columns, targetFrame.height() * rows, false);
        try {
            long sourcePixels = pixels(source);
            long targetPixels = pixels(target);
            int sourceWidth = source.getWidth();
            int targetWidth = target.getWidth();
            int[] horizontal = boundaries(sourceFrame.width(), targetFrame.width());
            int[] vertical = boundaries(sourceFrame.height(), targetFrame.height());
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < columns; column++) {
                    downscaleFrame(
                            sourcePixels,
                            sourceWidth,
                            column * sourceFrame.width(),
                            row * sourceFrame.height(),
                            targetPixels,
                            targetWidth,
                            column * targetFrame.width(),
                            row * targetFrame.height(),
                            targetFrame.width(),
                            targetFrame.height(),
                            horizontal,
                            vertical
                    );
                }
            }
            return target;
        } catch (RuntimeException | Error exception) {
            target.close();
            throw exception;
        }
    }

    private static long pixels(NativeImage image) {
        return ((AtlasDownscalingNativeImageAccessor) (Object) image).mysticmonstrosityfixes$pixels();
    }

    private static int[] boundaries(int sourceLength, int targetLength) {
        int[] bounds = new int[targetLength + 1];
        int previous = 0;
        for (int i = 1; i <= targetLength; i++) {
            int end = (int) ((long) i * sourceLength / targetLength);
            if (end <= previous) {
                end = previous + 1;
            }
            bounds[i] = end;
            previous = end;
        }
        return bounds;
    }

    private static void downscaleFrame(
            long sourcePixels,
            int sourceWidth,
            int sourceX,
            int sourceY,
            long targetPixels,
            int targetWidth,
            int targetX,
            int targetY,
            int targetFrameWidth,
            int targetFrameHeight,
            int[] horizontal,
            int[] vertical
    ) {
        long sourceRowStride = (long) sourceWidth * 4L;
        for (int y = 0; y < targetFrameHeight; y++) {
            int startY = vertical[y];
            int endY = vertical[y + 1];
            long targetRow = targetPixels + ((long) (targetY + y) * targetWidth + targetX) * 4L;
            long targetAddress = targetRow;
            long sourceRowBase = sourcePixels + ((long) (sourceY + startY) * sourceWidth + sourceX) * 4L;
            for (int x = 0; x < targetFrameWidth; x++) {
                int startX = horizontal[x];
                int endX = horizontal[x + 1];
                long alpha = 0L;
                long blue = 0L;
                long green = 0L;
                long red = 0L;
                long sourceRow = sourceRowBase + (long) startX * 4L;
                for (int sampleY = startY; sampleY < endY; sampleY++) {
                    long address = sourceRow;
                    for (int sampleX = startX; sampleX < endX; sampleX++) {
                        int pixel = MemoryUtil.memGetInt(address);
                        address += 4L;
                        int a = pixel >>> 24;
                        alpha += a;
                        blue += (long) (pixel >> 16 & 255) * a;
                        green += (long) (pixel >> 8 & 255) * a;
                        red += (long) (pixel & 255) * a;
                    }
                    sourceRow += sourceRowStride;
                }
                long samples = (long) (endX - startX) * (endY - startY);
                int a = (int) ((alpha + samples / 2L) / samples);
                int b = alpha == 0L ? 0 : (int) ((blue + alpha / 2L) / alpha);
                int g = alpha == 0L ? 0 : (int) ((green + alpha / 2L) / alpha);
                int r = alpha == 0L ? 0 : (int) ((red + alpha / 2L) / alpha);
                MemoryUtil.memPutInt(targetAddress, a << 24 | b << 16 | g << 8 | r);
                targetAddress += 4L;
            }
        }
    }

    private static final class MatchState {
        private final SpriteId spriteId = new SpriteId();
        private List<Rule> rules = List.of();
        private Matcher[] matchers = new Matcher[0];

        private void update(List<Rule> current, ResourceLocation name) {
            spriteId.update(name);
            if (rules != current) {
                Matcher[] updated = new Matcher[current.size()];
                for (int i = 0; i < updated.length; i++) {
                    updated[i] = current.get(i).pattern().matcher(spriteId);
                }
                matchers = updated;
                rules = current;
            }
        }
    }

    private static final class SpriteId implements CharSequence {
        private String namespace = "";
        private String path = "";
        private int colon;

        private void update(ResourceLocation name) {
            namespace = name.getNamespace();
            path = name.getPath();
            colon = namespace.length();
        }

        @Override
        public int length() {
            return colon + 1 + path.length();
        }

        @Override
        public char charAt(int index) {
            if (index < colon) {
                return namespace.charAt(index);
            }
            if (index == colon) {
                return ':';
            }
            return path.charAt(index - colon - 1);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return toString().subSequence(start, end);
        }

        @Override
        public String toString() {
            return namespace + ':' + path;
        }
    }

    private record Rule(Pattern pattern, int factor, String label) {
    }
}
