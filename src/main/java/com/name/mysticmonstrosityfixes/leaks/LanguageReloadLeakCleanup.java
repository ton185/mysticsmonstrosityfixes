package com.name.mysticmonstrosityfixes.leaks;

import com.mojang.logging.LogUtils;
import com.name.mysticmonstrosityfixes.mixin.fixes.TranslatableContentsCacheAccessor;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.slf4j.Logger;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Releases the language a {@link TranslatableContents} cached the last time it was decomposed.
 * <p>
 * Each translated component stores the {@code Language} instance it last rendered with so it can
 * skip repeating the translation work. That cache survives a language or resource reload: chat
 * history, the HUD overlay message, the title and subtitle, and any component a mod keeps in a
 * static cache keep the previous {@code ClientLanguage}. The heap dump after several reloads
 * showed five stale {@code ClientLanguage} instances at roughly 24 MB each, retained only through
 * {@code decomposedWith}.
 * <p>
 * The components are registered here from their constructor through a weak reference, so a reload
 * can clear every component that is still alive, including components owned by mods, without knowing
 * where they are stored. Clearing {@code decomposedWith} makes the next render translate the
 * component again with the new language, and clearing the cached parts releases the strings built
 * from the old language immediately. Dead references are purged through the reference queue, so the
 * registry does not keep a component alive. The accessor is an optional mixin: if it is missing the
 * cleanup does nothing rather than touching the class.
 */
public final class LanguageReloadLeakCleanup {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ReferenceQueue<TranslatableContents> COLLECTED = new ReferenceQueue<>();
    private static final Set<WeakReference<TranslatableContents>> LIVE = new HashSet<>();

    private LanguageReloadLeakCleanup() {
    }

    public static void register(TranslatableContents contents) {

        if (contents == null) {
            return;
        }
        synchronized (LIVE) {
            purgeLocked();
            LIVE.add(new WeakReference<>(contents, COLLECTED));
        }
    }

    public static void onLanguageReload() {

        final List<WeakReference<TranslatableContents>> snapshot;
        synchronized (LIVE) {
            purgeLocked();
            snapshot = new ArrayList<>(LIVE);
        }

        int cleared = 0;
        for (WeakReference<TranslatableContents> reference : snapshot) {
            final TranslatableContents contents = reference.get();
            if (!(contents instanceof TranslatableContentsCacheAccessor accessor)) {
                continue;
            }
            try {
                // Clear the parts before the language. Decompose writes the language first and the
                // parts second, so this order cannot leave a component claiming the new language
                // while holding an empty part list if a reload ever races a render.
                accessor.mmf$setDecomposedParts(List.of());
                accessor.mmf$setDecomposedWith(null);
                cleared++;
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable throwable) {
                LOGGER.debug("Could not clear a cached translation", throwable);
            }
        }

        if (cleared > 0) {
            LOGGER.debug("Released {} cached translation(s) before a language reload", cleared);
        }
    }

    private static void purgeLocked() {

        Reference<? extends TranslatableContents> collected;
        while ((collected = COLLECTED.poll()) != null) {
            LIVE.remove(collected);
        }
    }
}
