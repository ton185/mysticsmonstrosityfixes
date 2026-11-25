package com.name.mysticmonstrosityfixes.ae2;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;

/**
 * Captures the loader that can see every mod's class resources. Mod construction changes the
 * thread-context loader, so the Mixin plugin captures the game loader once and later ACO/ExtendedAE
 * probes reuse it.
 */
public final class ModResourceLoader {

    private static volatile ClassLoader loader;

    private ModResourceLoader() {
    }

    public static void capture(ClassLoader candidate) {
        if (candidate != null) {
            loader = candidate;
        }
    }

    public static ClassLoader get() {
        ClassLoader captured = loader;
        if (captured != null) {
            return captured;
        }
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        return context != null ? context : ModResourceLoader.class.getClassLoader();
    }

    public static boolean hasClass(String className) {
        ClassLoader current = get();
        return current != null && current.getResource(className.replace('.', '/') + ".class") != null;
    }

    public static ClassNode readClass(String className, boolean code) {
        ClassLoader current = get();
        if (current == null) {
            return null;
        }
        try (InputStream stream = current.getResourceAsStream(className.replace('.', '/') + ".class")) {
            if (stream == null) {
                return null;
            }
            ClassNode node = new ClassNode();
            int flags = ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES;
            if (!code) {
                flags |= ClassReader.SKIP_CODE;
            }
            new ClassReader(stream).accept(node, flags);
            return node;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }
}
