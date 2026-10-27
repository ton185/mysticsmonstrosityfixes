package com.name.mysticmonstrosityfixes.mixin.fixes;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Constructor;

/**
 * Jade loads {@code @WailaPlugin} classes with {@code getDeclaredConstructor()} and
 * {@code newInstance()}. Applied Quantum Inscriber's plugin is an enum, so it has no
 * zero-argument constructor and Jade logs "Error loading plugin". Enum plugins already
 * have a single {@code INSTANCE}; use that.
 */
@Mixin(targets = "snownee.jade.util.CommonProxy", remap = false)
public class JadeEnumPluginLoadFix {
    @Redirect(
            method = "loadComplete",
            at = @At(value = "INVOKE", target = "Ljava/lang/Class;getDeclaredConstructor([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;"),
            remap = false)
    private Constructor<?> constructorOrEnum(Class<?> type, Class<?>[] parameterTypes) throws NoSuchMethodException {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor;
        } catch (NoSuchMethodException exception) {
            if (type.isEnum()) {
                Constructor<?>[] constructors = type.getDeclaredConstructors();
                if (constructors.length > 0) {
                    constructors[0].setAccessible(true);
                    return constructors[0];
                }
            }
            throw exception;
        }
    }

    @Redirect(
            method = "loadComplete",
            at = @At(value = "INVOKE", target = "Ljava/lang/reflect/Constructor;newInstance([Ljava/lang/Object;)Ljava/lang/Object;"),
            remap = false)
    private Object instanceOrEnum(Constructor<?> constructor, Object[] arguments) throws ReflectiveOperationException {
        Class<?> type = constructor.getDeclaringClass();
        if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            if (constants != null && constants.length > 0) {
                return constants[0];
            }
        }
        return constructor.newInstance(arguments);
    }
}
