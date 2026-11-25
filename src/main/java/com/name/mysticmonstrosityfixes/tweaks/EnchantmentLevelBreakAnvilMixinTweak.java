package com.name.mysticmonstrosityfixes.tweaks;

import com.mojang.logging.LogUtils;
import com.name.mysticmonstrosityfixes.MysticMixinConfig;
import com.name.mysticmonstrosityfixes.mixin.MixinConfigAccess;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;

import java.util.List;

/**
 * Removes Enchantment Level Break's mixin from the anvil menu before AnvilMenu is transformed, so its
 * custom enchantment combining rule is not installed. Other Enchantment Level Break mixins stay.
 */
public final class EnchantmentLevelBreakAnvilMixinTweak {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MIXIN = "com.prizowo.enchantmentlevelbreak.mixin.AnvilMenuMixin";
    private static final String INJECTION_PACKAGE = "/injection/";
    private static final String MIXIN_EXTRAS_INJECTOR_PACKAGE = "/mixinextras/injector/";
    private static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";
    private static final String OPTION = "mixin.tweaks.disable_enchantmentlevelbreak_anvil_mixin";
    private static volatile boolean installed;

    private EnchantmentLevelBreakAnvilMixinTweak() {
    }

    public static boolean installIfEnabled() {
        try {
            if (!MysticMixinConfig.isEnabled(OPTION)) {
                return false;
            }
            return install();
        } catch (Throwable throwable) {
            LOGGER.warn("Could not disable Enchantment Level Break's anvil mixin", throwable);
            return false;
        }
    }

    public static synchronized boolean install() {
        if (installed) {
            return true;
        }
        if (!isClassPresent(MIXIN)) {
            installed = true;
            return false;
        }
        Object mixinInfo = MixinConfigAccess.findMixin(MIXIN);
        if (mixinInfo == null) {
            LOGGER.debug("Enchantment Level Break's anvil mixin is not prepared yet; the tweak will retry");
            return false;
        }
        if (MixinConfigAccess.removeMixin(mixinInfo)) {
            LOGGER.info("Disabled Enchantment Level Break's anvil mixin");
            installed = true;
            return true;
        }
        ClassNode node = MixinConfigAccess.classNode(mixinInfo);
        if (node == null) {
            return false;
        }
        boolean removed = false;
        for (MethodNode method : node.methods) {
            if (hasAnnotation(method.visibleAnnotations, OVERWRITE) || hasAnnotation(method.invisibleAnnotations, OVERWRITE)) {
                removed = true;
            }
            removed |= removeInjectors(method.visibleAnnotations);
            removed |= removeInjectors(method.invisibleAnnotations);
        }
        if (removed) {
            node.methods.removeIf(method -> hasAnnotation(method.visibleAnnotations, OVERWRITE)
                    || hasAnnotation(method.invisibleAnnotations, OVERWRITE));
            LOGGER.warn("Could not remove Enchantment Level Break's anvil mixin; removed its injections instead");
        }
        installed = true;
        return true;
    }

    private static boolean removeInjectors(List<AnnotationNode> annotations) {
        if (annotations == null) {
            return false;
        }
        return annotations.removeIf(annotation -> annotation.desc != null
                && (annotation.desc.contains(INJECTION_PACKAGE) || annotation.desc.contains(MIXIN_EXTRAS_INJECTOR_PACKAGE)));
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

    private static boolean isClassPresent(String className) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = EnchantmentLevelBreakAnvilMixinTweak.class.getClassLoader();
        }
        return loader != null && loader.getResource(className.replace('.', '/') + ".class") != null;
    }
}
