package com.name.mysticmonstrosityfixes.mixin;

import com.name.mysticmonstrosityfixes.RegistrySizingCache;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.mehvahdjukaar.every_compat.EveryCompat;
import net.mehvahdjukaar.moonlight.api.misc.Registrator;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(value = EveryCompat.class, remap = false)
public abstract class EveryCompat_RegisterItems_PreSizeAndDryCountMixin {

    @Unique private static int mysticmonstrosityfixes$startSize;
    @Unique private static int mysticmonstrosityfixes$dryCount;

    @Inject(method = "registerItems",
            at = @At("HEAD"))
    private static void mysticmonstrosityfixes$preSizeAndDryCount(Registrator<Item> event, CallbackInfo ci) {
        final Registry<Item> reg = BuiltInRegistries.ITEM;
        mysticmonstrosityfixes$startSize = reg.size();

        final int cachedExpected = RegistrySizingCache.loadExpected();
        if (cachedExpected > 0) {
            try {
                ObjectArrayList<?> byId = mysticmonstrosityfixes$findByIdList(reg);
                if (byId != null) {
                    int want = Math.addExact(mysticmonstrosityfixes$startSize, cachedExpected);
                    byId.ensureCapacity(want);
                }
            } catch (Throwable ignored) {}
        }

        final AtomicInteger counter = new AtomicInteger();
        EveryCompat.forAllModules(m -> {
            try {
                m.registerItems((id, item) -> counter.incrementAndGet());
            } catch (Throwable ignored) {}
        });
        mysticmonstrosityfixes$dryCount = counter.get();
        if (mysticmonstrosityfixes$dryCount > 0) {
            RegistrySizingCache.saveExpected(mysticmonstrosityfixes$dryCount);
        }
    }

    @Inject(method = "registerItems",
            at = @At("RETURN"))
    private static void mysticmonstrosityfixes$updateCacheWithActuals(Registrator<Item> event, CallbackInfo ci) {
        final Registry<Item> reg = BuiltInRegistries.ITEM;
        int actuallyAdded = Math.max(0, reg.size() - mysticmonstrosityfixes$startSize);
        int next = Math.max(mysticmonstrosityfixes$dryCount, actuallyAdded);
        if (next > 0) RegistrySizingCache.saveExpected(next);
    }

    @Unique
    private static ObjectArrayList<?> mysticmonstrosityfixes$findByIdList(Registry<Item> reg) throws IllegalAccessException {
        for (String name : new String[]{"byId", "holdersById", "f_205714_", "f_257481_"}) {
            try {
                Field f = reg.getClass().getDeclaredField(name);
                f.setAccessible(true);
                Object obj = f.get(reg);
                if (obj instanceof ObjectArrayList<?> l) return l;
            } catch (NoSuchFieldException ignored) {}
        }
        for (Field f : reg.getClass().getDeclaredFields()) {
            if (ObjectArrayList.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                Object obj = f.get(reg);
                if (obj instanceof ObjectArrayList<?> l) {
                    if (l.size() == reg.size() || l.isEmpty() || (l.get(0) == null) || (l.get(0) instanceof Holder<?>)) {
                        return l;
                    }
                }
            }
        }
        return null;
    }
}
