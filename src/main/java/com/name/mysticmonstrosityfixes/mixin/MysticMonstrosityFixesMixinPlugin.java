package com.name.mysticmonstrosityfixes.mixin;

import com.mojang.logging.LogUtils;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Skips mixins whose target or referenced classes are not present, so that optional and
 * client-only mod dependencies do not hard-fail class transformation on a dedicated server.
 */
public class MysticMonstrosityFixesMixinPlugin implements IMixinConfigPlugin {

    private static final String MOVING_ELEVATORS = "MovingElevatorsCabinCLCompat";

    private static final String CL_WRAPPER_ATTACHMENTS =
            "me.erykczy.colorfullighting.api.CLWrapperAttachments";
    private static final String COLORED_LIGHT_ENGINE =
            "me.erykczy.colorfullighting.common.ColoredLightEngine";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName != null && mixinClassName.endsWith("." + MOVING_ELEVATORS)) {
            if (!isClassPresent(CL_WRAPPER_ATTACHMENTS) || !isClassPresent(COLORED_LIGHT_ENGINE)) {
                LogUtils.getLogger().info(
                        "Colorful Lighting is not loaded, skipping {}", mixinClassName);
                return false;
            }
        }
        return true;
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, MysticMonstrosityFixesMixinPlugin.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {
    }
}