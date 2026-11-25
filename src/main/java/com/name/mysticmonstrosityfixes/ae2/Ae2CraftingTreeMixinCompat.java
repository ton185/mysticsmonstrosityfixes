package com.name.mysticmonstrosityfixes.ae2;

import com.mojang.logging.LogUtils;
import com.name.mysticmonstrosityfixes.MysticMixinConfig;
import com.name.mysticmonstrosityfixes.mixin.MixinConfigAccess;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Detects and prepares the crafting-tree redirect shared by AE2 Crafting Optimizer and Insane AE2
 * Addons. The bridge mixin wraps ACO's required redirect and re-applies InsaneAE2's provider-card
 * filter, so the two no longer compete for the same injected instruction.
 * <p>
 * This class intentionally avoids AE2 types: it runs while Mixin is still selecting configs, before
 * game classes should be resolved. The bridge mixin loads the AE2-aware helper only when it is
 * applied to {@code CraftingTreeNode}.
 */
public final class Ae2CraftingTreeMixinCompat {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ACO_MIXIN = "com.syaru.ae2craftingoptimizer.mixin.CraftingTreeCalculationMemoMixin";
    private static final String INSANE_MIXIN = "net.oktawia.insaneae2addons.mixins.MixinCraftingTreeNode";
    private static final String ACO_CONFIG = "ae2_crafting_optimizer.mixins.json";
    private static final String INSANE_CONFIG = "mixins.insaneae2addons.json";
    private static final String ACO_PACKAGE = "com.syaru.ae2craftingoptimizer.";
    private static final String INSANE_PACKAGE = "net.oktawia.insaneae2addons.";
    private static final String TARGET = "appeng.crafting.CraftingTreeNode";
    private static final String CRAFTING_SERVICE = "appeng/api/networking/crafting/ICraftingService";
    private static final String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
    private static final String UNIQUE = "Lorg/spongepowered/asm/mixin/Unique;";
    private static final String OPTION = "mixin.fixes.ae2_crafting_tree_mixin_conflict";

    private static final Map<String, ClassNode> CLASS_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile Boolean conflictPresent;
    private static volatile boolean installed;
    private static volatile boolean namesResolved;
    private static volatile String acoMixinName;
    private static volatile String insaneMixinName;
    private static volatile ClassLoader resourceLoader;

    private Ae2CraftingTreeMixinCompat() {
    }

    public static void setResourceLoader(ClassLoader loader) {
        if (loader != null) {
            resourceLoader = loader;
        }
    }

    private static ClassLoader loader() {
        ClassLoader captured = resourceLoader;
        if (captured != null) {
            return captured;
        }
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        return context != null ? context : Ae2CraftingTreeMixinCompat.class.getClassLoader();
    }

    public static boolean installIfEnabled() {
        try {
            if (!MysticMixinConfig.isEnabled(OPTION)) {
                LOGGER.info("AE2 crafting tree compatibility fix is disabled by {}", OPTION);
                return false;
            }
            if (!AcoCompat.isLegacy()) {
                if (AcoCompat.isPresent()) {
                    LOGGER.info("Skipping AE2/InsaneAE2 crafting tree patching for ACO 2.x");
                } else {
                    LOGGER.debug("ACO is not installed; skipping AE2/InsaneAE2 crafting tree patching");
                }
                return false;
            }
            return install();
        } catch (Throwable throwable) {
            LOGGER.warn("Could not prepare the AE2 crafting tree compatibility bridge", throwable);
            return false;
        }
    }

    public static synchronized boolean install() {
        if (installed) {
            return true;
        }
        resolveNames();
        boolean conflict = isConflictPresent();
        if (!conflict) {
            conflict = assumeKnownConflict();
        }
        LOGGER.info("AE2 crafting tree compatibility check: conflict={}, aco={}, insane={}", conflict, acoMixinName, insaneMixinName);
        if (!conflict) {
            return false;
        }
        String acoName = acoMixinName;
        String insaneName = insaneMixinName;
        if (acoName == null || insaneName == null) {
            LOGGER.warn("AE2 crafting tree compatibility could not identify both conflicting mixins");
            return false;
        }
        Object aco = MixinConfigAccess.findMixin(acoName);
        Object insane = MixinConfigAccess.findMixin(insaneName);
        LOGGER.info("AE2 crafting tree mixin handles: aco={}, insane={}", aco, insane);
        if (aco == null || insane == null) {
            if (removeConflictingMixinFromConfigs(acoName, insaneName)) {
                return true;
            }
            LOGGER.warn("AE2 crafting tree mixins {}/{} are not prepared yet; the bridge will retry", acoName, insaneName);
            return false;
        }
        Boolean targets = MixinConfigAccess.targets(aco, TARGET);
        Boolean applies = MixinConfigAccess.shouldApply(aco, TARGET);
        boolean bridgeable = canBridge();
        LOGGER.info("AE2 crafting tree compatibility state: targets={}, pluginApplies={}, bridgeable={}", targets, applies, bridgeable);
        if (Boolean.FALSE.equals(targets) && Boolean.FALSE.equals(applies)) {
            LOGGER.warn("ACO's plugin and target map both currently keep its crafting tree mixin off {}; the redirect conflict is "
                    + "still present, so the bridge is detaching InsaneAE2's competing redirect to prevent the known crash", TARGET);
        }
        if (!bridgeable) {
            if (detachAcoRedirect(aco)) {
                LOGGER.warn("ACO's crafting tree handler no longer matches the bridge; kept InsaneAE2's redirect and detached "
                        + "only ACO's competing redirect so its provider-card filter still runs");
                installed = true;
                return true;
            }
            if (MixinConfigAccess.removeMixin(aco)) {
                LOGGER.warn("ACO's crafting tree handler no longer matches the bridge; removed only that ACO mixin so "
                        + "InsaneAE2's provider-card filter keeps running without an injection conflict");
                installed = true;
                return true;
            }
        }
        if (MixinConfigAccess.classNode(insane) == null) {
            if (removeConflictingMixinFromConfigs(acoName, insaneName)) {
                return true;
            }
            LOGGER.warn("InsaneAE2's crafting tree mixin class node is not available yet; the bridge will retry");
            return false;
        }
        if (detachInsaneRedirect(insane)) {
            LOGGER.info("Kept ACO's crafting tree memoizer ({}) and moved InsaneAE2's provider-card filter ({}) into the compatibility bridge",
                    acoName, insaneName);
            installed = true;
            return true;
        }
        if (MixinConfigAccess.removeMixin(insane)) {
            LOGGER.warn("Could not detach InsaneAE2's crafting tree redirect ({}); removed that mixin instead. "
                    + "The compatibility bridge still applies the provider-card filter.", insaneName);
            installed = true;
            return true;
        }
        if (removeConflictingMixinFromConfigs(acoName, insaneName)) {
            return true;
        }
        if (MixinConfigAccess.removeMixin(aco)) {
            LOGGER.warn("Could not detach InsaneAE2's crafting tree redirect ({}); removed ACO's crafting tree mixin ({}) instead. "
                    + "InsaneAE2's own redirect keeps working and the server no longer aborts.", insaneName, acoName);
            installed = true;
            return true;
        }
        LOGGER.warn("Could not resolve the AE2 crafting tree redirect conflict; Mixin's internal layout may have changed");
        return false;
    }

    private static boolean assumeKnownConflict() {
        if (acoMixinName == null && isClassPresent(ACO_MIXIN)) {
            acoMixinName = ACO_MIXIN;
        }
        if (insaneMixinName == null && isClassPresent(INSANE_MIXIN)) {
            insaneMixinName = INSANE_MIXIN;
        }
        if (acoMixinName == null || insaneMixinName == null) {
            return false;
        }
        Object aco = MixinConfigAccess.findMixin(acoMixinName);
        Object insane = MixinConfigAccess.findMixin(insaneMixinName);
        if (aco == null || insane == null) {
            LOGGER.warn("Mixin metadata is not ready; trusting the present ACO/InsaneAE2 classes for the known crafting tree conflict");
            return true;
        }
        ClassNode acoNode = MixinConfigAccess.classNode(aco);
        ClassNode insaneNode = MixinConfigAccess.classNode(insane);
        if (acoNode != null && insaneNode != null) {
            boolean parsed = conflict(acoNode) != null && conflict(insaneNode) != null;
            if (parsed) {
                LOGGER.warn("Had to re-read the ACO/InsaneAE2 crafting tree redirects from Mixin's loaded mixin classes");
            }
            return parsed;
        }
        LOGGER.warn("Could not read the crafting tree mixin annotations; trusting the known ACO/InsaneAE2 redirect conflict");
        return true;
    }

    private static boolean removeConflictingMixinFromConfigs(String acoName, String insaneName) {
        if (canBridge()) {
            if (removeMixinFromConfig(INSANE_CONFIG, insaneName, "InsaneAE2")) {
                return true;
            }
            if (removeMixinFromConfig(ACO_CONFIG, acoName, "ACO")) {
                return true;
            }
        } else {
            if (removeMixinFromConfig(ACO_CONFIG, acoName, "ACO")) {
                return true;
            }
            if (removeMixinFromConfig(INSANE_CONFIG, insaneName, "InsaneAE2")) {
                return true;
            }
        }
        return false;
    }

    private static boolean removeMixinFromConfig(String configName, String mixinName, String label) {
        try {
            Object config = MixinConfigAccess.findConfig(configName);
            if (config == null || !MixinConfigAccess.removeMixinClass(config, mixinName)) {
                return false;
            }
            LOGGER.warn("Could not resolve {}'s crafting tree MixinInfo; removed only that mixin from {} instead. "
                    + "The other provider-card or memoization path still runs without an injection conflict.", label, configName);
            installed = true;
            return true;
        } catch (Throwable throwable) {
            LOGGER.warn("Could not remove {}'s crafting tree mixin from {}", label, configName, throwable);
            return false;
        }
    }

    private static boolean detachInsaneRedirect(Object insane) {
        ClassNode node = MixinConfigAccess.classNode(insane);
        if (node == null) {
            return false;
        }
        if (removeConflicts(node)) {
            LOGGER.info("Kept ACO's crafting tree memoizer and moved InsaneAE2's provider-card filter into the compatibility bridge");
            return true;
        }
        ClassNode copy = new ClassNode();
        node.accept(copy);
        if (!removeConflicts(copy) || !MixinConfigAccess.reloadMixin(insane, copy)) {
            return false;
        }
        LOGGER.info("Kept ACO's crafting tree memoizer and moved InsaneAE2's provider-card filter into the compatibility bridge");
        return true;
    }

    private static boolean detachAcoRedirect(Object aco) {
        ClassNode node = MixinConfigAccess.classNode(aco);
        if (node == null) {
            return false;
        }
        if (removeConflicts(node)) {
            return true;
        }
        ClassNode copy = new ClassNode();
        node.accept(copy);
        return removeConflicts(copy) && MixinConfigAccess.reloadMixin(aco, copy);
    }

    public static boolean canBridge() {
        resolveNames();
        String name = acoMixinName;
        if (name == null) {
            return false;
        }
        ClassNode node = readClass(name);
        if (node == null) {
            return false;
        }
        for (MethodNode method : node.methods) {
            if (!"aco$reuseUnmodifiedPatternCandidates".equals(method.name)
                    || !"(Lappeng/api/networking/crafting/ICraftingService;Lappeng/api/stacks/AEKey;)Ljava/util/Collection;".equals(method.desc)
                    || method.instructions == null) {
                continue;
            }
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call
                        && call.getOpcode() == Opcodes.INVOKESTATIC
                        && "com/syaru/ae2craftingoptimizer/optimization/CraftingCalculationMemo".equals(call.owner)
                        && "patternCandidates".equals(call.name)
                        && "(Ljava/lang/Object;Lappeng/api/networking/crafting/ICraftingService;Lappeng/api/stacks/AEKey;)Ljava/util/Collection;".equals(call.desc)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasAnnotation(MethodNode method, String descriptor) {
        return hasAnnotation(method.visibleAnnotations, descriptor) || hasAnnotation(method.invisibleAnnotations, descriptor);
    }

    private static boolean hasAnnotation(List<AnnotationNode> annotations, String descriptor) {
        if (annotations == null) {
            return false;
        }
        for (AnnotationNode annotation : annotations) {
            if (descriptor.equals(annotation.desc)) {
                return true;
            }
        }
        return false;
    }

    private static boolean removeConflicts(ClassNode node) {
        boolean removed = false;
        for (MethodNode method : node.methods) {
            for (AnnotationNode redirect : annotations(method, REDIRECT)) {
                List<String> methods = names(annotationValue(redirect, "method"));
                if (!methods.isEmpty() && !methods.contains("buildChildPatterns")) {
                    continue;
                }
                for (AnnotationNode at : atNodes(redirect)) {
                    String value = string(annotationValue(at, "value"), "");
                    String target = string(annotationValue(at, "target"), "");
                    if ("INVOKE".equals(value)
                            && target.contains(CRAFTING_SERVICE)
                            && target.contains("getCraftingFor")) {
                        removed |= removeAnnotation(method, redirect);
                        break;
                    }
                }
            }
        }
        return removed;
    }

    public static boolean isConflictPresent() {
        Boolean cached = conflictPresent;
        if (cached != null) {
            return cached;
        }
        synchronized (Ae2CraftingTreeMixinCompat.class) {
            if (conflictPresent != null) {
                return conflictPresent;
            }
            resolveNames();
            if (acoMixinName != null && insaneMixinName != null && isClassPresent(TARGET)) {
                conflictPresent = true;
                return true;
            }
            return false;
        }
    }

    private static void resolveNames() {
        if (namesResolved) {
            return;
        }
        synchronized (Ae2CraftingTreeMixinCompat.class) {
            if (namesResolved) {
                return;
            }
            try {
                if (acoMixinName == null) {
                    acoMixinName = firstConflict(ACO_CONFIG, ACO_PACKAGE, ACO_MIXIN);
                }
                if (insaneMixinName == null) {
                    insaneMixinName = firstConflict(INSANE_CONFIG, INSANE_PACKAGE, INSANE_MIXIN);
                }
                if (acoMixinName != null && insaneMixinName != null) {
                    namesResolved = true;
                }
            } catch (Throwable throwable) {
                LOGGER.debug("Could not inspect AE2 crafting tree mixins", throwable);
            }
        }
    }

    private static String firstConflict(String configName, String packagePrefix, String fallback) {
        for (String name : MixinConfigAccess.mixinClassNames(configName)) {
            if (conflict(readClass(name)) != null) {
                return name;
            }
        }
        for (String name : MixinConfigAccess.mixinClassNames(null)) {
            if (name.startsWith(packagePrefix) && conflict(readClass(name)) != null) {
                return name;
            }
        }
        return conflict(readClass(fallback)) != null ? fallback : null;
    }

    private static Conflict conflict(ClassNode node) {
        if (node == null) {
            return null;
        }
        for (MethodNode method : node.methods) {
            for (AnnotationNode redirect : annotations(method, REDIRECT)) {
                List<String> methods = names(annotationValue(redirect, "method"));
                if (!methods.isEmpty() && !methods.contains("buildChildPatterns")) {
                    continue;
                }
                for (AnnotationNode at : atNodes(redirect)) {
                    String value = string(annotationValue(at, "value"), "");
                    String target = string(annotationValue(at, "target"), "");
                    if ("INVOKE".equals(value)
                            && target.contains(CRAFTING_SERVICE)
                            && target.contains("getCraftingFor")) {
                        return new Conflict(method, redirect);
                    }
                }
            }
        }
        return null;
    }

    private static boolean removeAnnotation(MethodNode method, AnnotationNode annotation) {
        boolean removed = remove(method.visibleAnnotations, annotation);
        removed |= remove(method.invisibleAnnotations, annotation);
        return removed;
    }

    private static boolean remove(List<AnnotationNode> annotations, AnnotationNode annotation) {
        if (annotations == null) {
            return false;
        }
        return annotations.removeIf(candidate -> candidate == annotation);
    }

    private static List<AnnotationNode> annotations(MethodNode method, String descriptor) {
        List<AnnotationNode> found = new ArrayList<>();
        annotations(method.visibleAnnotations, descriptor, found);
        annotations(method.invisibleAnnotations, descriptor, found);
        return found;
    }

    private static void annotations(List<AnnotationNode> source, String descriptor, List<AnnotationNode> target) {
        if (source == null) {
            return;
        }
        for (AnnotationNode annotation : source) {
            if (descriptor.equals(annotation.desc)) {
                target.add(annotation);
            }
        }
    }

    private static List<AnnotationNode> atNodes(AnnotationNode annotation) {
        Object value = annotationValue(annotation, "at");
        if (value instanceof AnnotationNode node) {
            return List.of(node);
        }
        if (value instanceof List<?> list) {
            List<AnnotationNode> nodes = new ArrayList<>();
            for (Object element : list) {
                if (element instanceof AnnotationNode node) {
                    nodes.add(node);
                }
            }
            return nodes;
        }
        return Collections.emptyList();
    }

    private static Object annotationValue(AnnotationNode annotation, String key) {
        if (annotation != null && annotation.values != null) {
            for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
                if (key.equals(annotation.values.get(i))) {
                    return annotation.values.get(i + 1);
                }
            }
        }
        return null;
    }

    private static List<String> names(Object value) {
        if (value instanceof String string) {
            return List.of(string);
        }
        if (value instanceof List<?> list) {
            List<String> strings = new ArrayList<>();
            for (Object element : list) {
                if (element instanceof String string) {
                    strings.add(string);
                }
            }
            return strings;
        }
        return Collections.emptyList();
    }

    private static String string(Object value, String fallback) {
        if (value instanceof String string) {
            return string;
        }
        return fallback;
    }

    private static boolean isClassPresent(String className) {
        ClassLoader loader = loader();
        return loader != null && loader.getResource(className.replace('.', '/') + ".class") != null;
    }

    private static ClassNode readClass(String className) {
        ClassNode cached = CLASS_CACHE.get(className);
        if (cached != null) {
            return cached;
        }
        ClassLoader loader = loader();
        if (loader == null) {
            return null;
        }
        try (InputStream stream = loader.getResourceAsStream(className.replace('.', '/') + ".class")) {
            if (stream == null) {
                return null;
            }
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            CLASS_CACHE.put(className, node);
            return node;
        } catch (IOException | RuntimeException exception) {
            LOGGER.debug("Could not read {} while inspecting the crafting tree mixins", className, exception);
            return null;
        }
    }

    private record Conflict(MethodNode method, AnnotationNode annotation) {
    }
}
