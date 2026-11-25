package com.name.mysticmonstrosityfixes.mixin;

import org.objectweb.asm.tree.ClassNode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reaches into Mixin's prepared configuration so a compatibility fix can inspect or adjust the
 * annotations of another mod's mixin before target classes are transformed. Everything is reflective
 * and best effort: a Mixin update or a missing internal simply makes the caller fall back.
 */
public final class MixinConfigAccess {

    private static final String MIXIN_CONFIG_SUFFIX = ".MixinConfig";

    private MixinConfigAccess() {
    }

    public static synchronized List<Object> configs() {
        List<Object> configs = new ArrayList<>();
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<String> names = new java.util.HashSet<>();
        try {
            Class<?> environment = Class.forName("org.spongepowered.asm.mixin.MixinEnvironment");
            Object current = environment.getMethod("getDefaultEnvironment").invoke(null);
            Object transformer = environment.getMethod("getActiveTransformer").invoke(current);
            if (transformer != null) {
                Object processor = field(transformer, "processor");
                addConfigs(configs, names, seen, field(processor, "configs"));
                addConfigs(configs, names, seen, field(processor, "pendingConfigs"));
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> handle = Class.forName("org.spongepowered.asm.mixin.transformer.Config");
            Object allConfigs = staticField(handle, "allConfigs");
            if (allConfigs instanceof Map<?, ?> map) {
                List<Object> resolved = new ArrayList<>();
                for (Object configHandle : map.values()) {
                    Object config = invoke(configHandle, "get");
                    if (config != null) {
                        resolved.add(config);
                    }
                }
                addConfigs(configs, names, seen, resolved);
            }
        } catch (Throwable ignored) {
        }
        return configs;
    }

    private static void addConfigs(
            List<Object> configs, Set<String> names, Set<Object> seen, Object value
    ) {
        if (!(value instanceof Iterable<?> iterable)) {
            return;
        }
        for (Object config : iterable) {
            if (config == null || !config.getClass().getName().endsWith(MIXIN_CONFIG_SUFFIX) || !seen.add(config)) {
                continue;
            }
            Object configuredName = invoke(config, "getName");
            if (configuredName instanceof String name && !names.add(name)) {
                continue;
            }
            configs.add(config);
        }
    }

    public static synchronized Object findMixin(String className) {
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Object config : configs()) {
            Object found = findMixin(config, className, seen);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static Object findMixin(Object config, String className, Set<Object> seen) {
        for (String fieldName : List.of("mixins", "pendingMixins")) {
            Object value = field(config, fieldName);
            if (value instanceof Iterable<?> iterable) {
                for (Object info : iterable) {
                    if (info == null || !seen.add(info)) {
                        continue;
                    }
                    if (className.equals(classNameOf(info))) {
                        return info;
                    }
                }
            }
        }
        Object mapping = field(config, "mixinMapping");
        if (mapping instanceof Map<?, ?> map) {
            for (Object value : map.values()) {
                if (!(value instanceof Iterable<?> iterable)) {
                    continue;
                }
                for (Object info : iterable) {
                    if (info == null || !seen.add(info)) {
                        continue;
                    }
                    if (className.equals(classNameOf(info))) {
                        return info;
                    }
                }
            }
        }
        return null;
    }

    public static synchronized Object configFor(Object mixinInfo) {
        for (Object config : configs()) {
            if (contains(config, mixinInfo)) {
                return config;
            }
        }
        return null;
    }

    private static boolean contains(Object config, Object mixinInfo) {
        for (String fieldName : List.of("mixins", "pendingMixins")) {
            Object value = field(config, fieldName);
            if (value instanceof Iterable<?> iterable) {
                for (Object info : iterable) {
                    if (info == mixinInfo) {
                        return true;
                    }
                }
            }
        }
        Object mapping = field(config, "mixinMapping");
        if (mapping instanceof Map<?, ?> map) {
            for (Object value : map.values()) {
                if (value instanceof Iterable<?> iterable) {
                    for (Object info : iterable) {
                        if (info == mixinInfo) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public static synchronized List<String> mixinClassNames(String configName) {
        Set<String> names = new java.util.LinkedHashSet<>();
        for (Object config : configs()) {
            Object configuredName = invoke(config, "getName");
            if (configName != null && !(configuredName instanceof String name && configName.equals(name))) {
                continue;
            }
            Object configuredPackage = invoke(config, "getMixinPackage");
            String mixinPackage = configuredPackage instanceof String value ? value : "";
            if (!mixinPackage.isEmpty() && !mixinPackage.endsWith(".")) {
                mixinPackage += ".";
            }
            for (String fieldName : List.of("mixinClasses", "mixinClassesClient", "mixinClassesServer")) {
                Object value = field(config, fieldName);
                if (value instanceof Iterable<?> iterable) {
                    for (Object element : iterable) {
                        if (!(element instanceof String simple) || simple.isEmpty()) {
                            continue;
                        }
                        names.add(!mixinPackage.isEmpty() && !simple.startsWith(mixinPackage)
                                ? mixinPackage + simple
                                : simple);
                    }
                }
            }
            for (String fieldName : List.of("mixins", "pendingMixins")) {
                Object value = field(config, fieldName);
                if (value instanceof Iterable<?> iterable) {
                    for (Object info : iterable) {
                        String name = classNameOf(info);
                        if (name != null) {
                            names.add(name);
                        }
                    }
                }
            }
        }
        return new ArrayList<>(names);
    }

    public static synchronized ClassNode classNode(Object mixinInfo) {
        Object state = field(mixinInfo, "state");
        if (state == null) {
            state = field(mixinInfo, "pendingState");
        }
        if (state == null) {
            return null;
        }
        Object node = field(state, "classNode");
        return node instanceof ClassNode classNode ? classNode : null;
    }

    /**
     * Asks the owning config's plugin whether it will really apply the mixin to the target. A null
     * result means the plugin hook could not be reached, so callers should keep their conservative
     * fallback.
     */
    public static synchronized Boolean targets(Object mixinInfo, String targetClassName) {
        Object config = configFor(mixinInfo);
        if (config == null) {
            return null;
        }
        Object value = field(config, "mixinMapping");
        if (!(value instanceof Map<?, ?> mapping) || mapping.isEmpty()) {
            return null;
        }
        Object list = mapping.get(targetClassName);
        if (list instanceof Iterable<?> iterable) {
            for (Object info : iterable) {
                if (info == mixinInfo) {
                    return true;
                }
            }
        }
        return false;
    }

    public static synchronized Boolean shouldApply(Object mixinInfo, String targetClassName) {
        Object config = configFor(mixinInfo);
        if (config == null) {
            return null;
        }
        Object plugin = invoke(config, "getPlugin");
        if (plugin == null) {
            return null;
        }
        try {
            for (Method method : plugin.getClass().getMethods()) {
                if (method.getName().equals("shouldApplyMixin") && method.getParameterCount() == 2) {
                    Object result = method.invoke(plugin, targetClassName, classNameOf(mixinInfo));
                    return result instanceof Boolean applies ? applies : null;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static synchronized boolean reloadMixin(Object mixinInfo, ClassNode patched) {
        try {
            for (Method method : mixinInfo.getClass().getDeclaredMethods()) {
                if (method.getName().equals("reloadMixin")
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == ClassNode.class) {
                    method.setAccessible(true);
                    method.invoke(mixinInfo, patched);
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /**
     * Removes a prepared mixin from its config. Mixin keeps one list for target lookup and one for
     * audit/reload, so both are cleaned along with the target mapping.
     */
    public static synchronized Object findConfig(String configName) {
        if (configName == null) {
            return null;
        }
        for (Object config : configs()) {
            if (configName.equals(configName(config))) {
                return config;
            }
        }
        try {
            Class<?> handle = Class.forName("org.spongepowered.asm.mixin.transformer.Config");
            Object allConfigs = staticField(handle, "allConfigs");
            if (allConfigs instanceof Map<?, ?> map) {
                Object configHandle = map.get(configName);
                if (configHandle != null) {
                    return invoke(configHandle, "get");
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static synchronized boolean removeMixinClass(Object config, String className) {
        if (config == null || className == null) {
            return false;
        }
        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        boolean stringsRemoved = false;
        for (String fieldName : List.of("mixinClasses", "mixinClassesClient", "mixinClassesServer")) {
            Object value = field(config, fieldName);
            if (value instanceof List<?> list) {
                stringsRemoved |= removeStringEntries(list, className, simpleName);
            }
        }
        Object mixinInfo = findMixin(config, className, Collections.newSetFromMap(new IdentityHashMap<>()));
        boolean infoRemoved = mixinInfo != null && removeFromConfig(config, mixinInfo);
        boolean prepared = Boolean.TRUE.equals(field(config, "prepared"));
        return infoRemoved || (stringsRemoved && !prepared);
    }

    private static boolean removeStringEntries(List<?> list, String className, String simpleName) {
        boolean removed = false;
        try {
            for (Iterator<?> iterator = list.iterator(); iterator.hasNext(); ) {
                Object element = iterator.next();
                if (element instanceof String entry
                        && (entry.equals(className) || entry.equals(simpleName) || entry.endsWith("." + simpleName))) {
                    iterator.remove();
                    removed = true;
                }
            }
        } catch (Throwable ignored) {
        }
        return removed;
    }

    public static synchronized boolean removeMixin(Object mixinInfo) {
        Object config = configFor(mixinInfo);
        return config != null && removeFromConfig(config, mixinInfo);
    }

    private static boolean removeFromConfig(Object config, Object mixinInfo) {
        boolean removed = false;
        try {
            for (Method method : config.getClass().getDeclaredMethods()) {
                if (method.getName().equals("removeMixin") && method.getParameterCount() == 1) {
                    method.setAccessible(true);
                    method.invoke(config, mixinInfo);
                    removed = true;
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
        removed |= removeFromList(config, "mixins", mixinInfo);
        removed |= removeFromList(config, "pendingMixins", mixinInfo);
        removed |= removeFromMapping(config, mixinInfo);
        return removed;
    }

    private static boolean removeFromList(Object owner, String fieldName, Object element) {
        Object value = field(owner, fieldName);
        if (!(value instanceof List<?> list)) {
            return false;
        }
        boolean removed = false;
        try {
            for (Iterator<?> iterator = list.iterator(); iterator.hasNext(); ) {
                if (iterator.next() == element) {
                    iterator.remove();
                    removed = true;
                }
            }
        } catch (Throwable ignored) {
        }
        if (!removed) {
            try {
                removed = list.remove(element);
            } catch (Throwable ignored) {
            }
        }
        return removed;
    }

    private static boolean removeFromMapping(Object owner, Object element) {
        Object value = field(owner, "mixinMapping");
        if (!(value instanceof Map<?, ?> map)) {
            return false;
        }
        boolean removed = false;
        try {
            for (Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator(); iterator.hasNext(); ) {
                Map.Entry<?, ?> entry = iterator.next();
                if (!(entry.getValue() instanceof List<?> list)) {
                    continue;
                }
                for (Iterator<?> listIterator = list.iterator(); listIterator.hasNext(); ) {
                    if (listIterator.next() == element) {
                        listIterator.remove();
                        removed = true;
                    }
                }
                if (list.isEmpty()) {
                    iterator.remove();
                }
            }
        } catch (Throwable ignored) {
        }
        if (!removed) {
            try {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getValue() instanceof List<?> list && list.remove(element)) {
                        removed = true;
                    }
                }
                map.entrySet().removeIf(entry -> entry.getValue() instanceof List<?> list && list.isEmpty());
            } catch (Throwable ignored) {
            }
        }
        return removed;
    }

    private static String configName(Object config) {
        Object name = invoke(config, "getName");
        return name instanceof String string ? string : null;
    }

    private static String classNameOf(Object mixinInfo) {
        Object name = invoke(mixinInfo, "getClassName");
        if (name instanceof String string) {
            return string;
        }
        Object field = field(mixinInfo, "className");
        return field instanceof String string ? string : null;
    }

    private static void addAll(List<Object> configs, Object value) {
        if (value instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                if (element != null && element.getClass().getName().endsWith(MIXIN_CONFIG_SUFFIX)) {
                    configs.add(element);
                }
            }
        }
    }

    private static Object invoke(Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            Method method = findMethod(target.getClass(), name, 0);
            if (method != null) {
                method.setAccessible(true);
                return method.invoke(target);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Method findMethod(Class<?> type, String name, int parameters) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == parameters) {
                    return method;
                }
            }
        }
        return null;
    }

    private static Object field(Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            Field field = findField(target.getClass(), name);
            if (field != null) {
                field.setAccessible(true);
                return field.get(target);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object staticField(Class<?> type, String name) {
        try {
            Field field = findField(type, name);
            if (field != null) {
                field.setAccessible(true);
                return field.get(null);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }
}
