package com.name.mysticmonstrosityfixes.mixin;

import com.mojang.logging.LogUtils;
import com.name.mysticmonstrosityfixes.MysticMixinConfig;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skips mixins whose targets are not present, so that optional and client-only mod dependencies do
 * not hard-fail class transformation on a dedicated server and do not make Mixin hunt for classes
 * that no mod in the pack provides.
 * <p>
 * Mixin asks this plugin whether a mixin applies before it resolves the declared target
 * ({@code MixinInfo#readDeclaredTargets} runs ahead of {@code ClassInfo#forName}), so declining here
 * saves the lookup as well as the "Error loading class" and "@Mixin target ... was not found"
 * warnings it produces. A failed lookup is not cheap: the framework falls back to scanning the
 * resources of every jar on the classpath.
 * <p>
 * Mod-targeted mixins are also checked against the installed target bytecode. A missing shadow,
 * accessor, invoker, overwrite, injector target method, or mod-owned {@code @At} member skips the
 * mixin with a warning instead of failing transformation. Validation failures are caught and also
 * skip the mixin, so a bad check cannot crash startup.
 */
public class MysticMonstrosityFixesMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
    private static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";
    private static final String ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
    private static final String INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";
    private static final String[] INJECTOR_ANNOTATIONS = {
            "Lorg/spongepowered/asm/mixin/injection/Inject;",
            "Lorg/spongepowered/asm/mixin/injection/Redirect;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyExpressionValue;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyReceiver;"
    };
    private static final String[] NON_MOD_PREFIXES = {
            "net.minecraft.",
            "net.minecraftforge.",
            "com.mojang.",
            "java.",
            "javax.",
            "org.spongepowered.",
            "com.name."
    };
    private static final Map<String, ClassNode> CLASS_CACHE = new HashMap<>();
    private static final Map<String, Set<String>> PRE_APPLY_METHODS = new ConcurrentHashMap<>();

    private String mixinPackage = "";

    /**
     * Mixin class simple name to the classes that must all exist for the mixin to be applied.
     * Mixins absent from this map have vanilla targets and always apply.
     */
    private static final Map<String, String[]> REQUIRED_CLASSES = Map.ofEntries(
            Map.entry("Ae2ltJeiRecipeTransferFix", new String[]{
                    "mezz.jei.gui.recipes.RecipeTransferButtonController",
                    "mezz.jei.gui.recipes.RecipesGui",
                    "com.moakiee.ae2lt.client.compat.JeiRecipeTransferMetadata",
                    "com.moakiee.ae2lt.client.tianshu.TianshuDirectUploadClient",
                    "com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu"}),
            Map.entry("AlexsMobsGhostlyPickaxeBakeFix", new String[]{
                    "com.github.alexthe666.alexsmobs.ClientProxy",
                    "com.github.alexthe666.alexsmobs.client.render.item.GhostlyPickaxeBakedModel"}),
            Map.entry("AxeSpellContainerFix", new String[]{
                    "com.example.chaotic_world_content.spells.AxeSpellContainerEvents"}),
            Map.entry("BielGGBrokenScriptRecursionFix", new String[]{
                    "com.example.chaotic_world_content.brokenscript.BrokenScriptCloneEvents"}),
            Map.entry("SwordSpellContainerFix", new String[]{
                    "com.example.chaotic_world_content.spells.SwordSpellContainerEvents"}),
            Map.entry("BloodMagicNullCapabilityFix", new String[]{
                    "wayoftime.bloodmagic.compat.CuriosCompat",
                    "top.theillusivec4.curios.api.CuriosApi"}),
            Map.entry("BotanyPotLagFix", new String[]{
                    "net.darkhax.botanypots.block.BlockEntityBotanyPot",
                    "net.darkhax.bookshelf.api.util.WorldHelper"}),
            Map.entry("BotanyPotLookupLagFix", new String[]{
                    "net.darkhax.botanypots.BotanyPotHelper",
                    "net.darkhax.botanypots.block.BlockEntityBotanyPot"}),
            Map.entry("CapsuleDisableRecallEnchantment", new String[]{
                    "capsule.enchantments.RecallEnchant"}),
            Map.entry("CapsulePreviewCrashFix", new String[]{
                    "capsule.client.render.CapsuleTemplateRenderer",
                    "capsule.client.render.FakeWorld",
                    "capsule.client.render.vbo.MultiVBORenderer",
                    "capsule.structure.CapsuleTemplate"}),
            Map.entry("DimensionIdManagementMixin", new String[]{
                    "qouteall.q_misc_util.dimension.DimensionIdManagement",
                    "qouteall.q_misc_util.dimension.DimensionIdRecord"}),
            Map.entry("DimensionIdRecordAccessor", new String[]{
                    "qouteall.q_misc_util.dimension.DimensionIdRecord"}),
            Map.entry("EidolonRepraisedHeartLagFix", new String[]{
                    "elucent.eidolon.client.ClientRegistry$EidolonHearts"}),
            Map.entry("EmbersMixin", new String[]{
                    "com.rekindled.embers.EmbersClientEvents",
                    "qouteall.imm_ptl.core.render.context_management.PortalRendering"}),
            Map.entry("EverlastingAbilitiesAttributeFix", new String[]{
                    "org.cyclops.everlastingabilities.ability.AbilityTypeAttributeModifier"}),
            Map.entry("EverlastingAbilitiesAttributeTweak", new String[]{
                    "org.cyclops.everlastingabilities.ability.AbilityTypeAttributeModifier"}),
            Map.entry("EverlastingAbilitiesCommandUncapTweak", new String[]{
                    "org.cyclops.everlastingabilities.command.CommandModifyAbilities"}),
            Map.entry("EverlastingAbilitiesTooltipTweak", new String[]{
                    "org.cyclops.everlastingabilities.client.gui.ContainerScreenAbilityContainer",
                    "org.cyclops.cyclopscore.client.gui.container.ContainerScreenExtended"}),
            Map.entry("EverlastingAbilitiesUncapTweak", new String[]{
                    "org.cyclops.everlastingabilities.api.capability.DefaultMutableAbilityStore"}),
            Map.entry("FaktocraftCoverBakeFix", new String[]{
                    "com.faktocraft.client.FaktocraftClient",
                    "com.faktocraft.common.cover.CoverSupport",
                    "com.faktocraft.client.model.CoverBakedModel"}),
            Map.entry("FLibPotionEffectRaceFix", new String[]{
                    "com.lothrazar.library.util.EntityUtil"}),
            Map.entry("GuiScaleClickFix", new String[]{
                    "com.misanthropy.gui_scale_additions.Client"}),
            Map.entry("FTBLibraryNullFix", new String[]{
                    "dev.ftb.mods.ftblibrary.integration.forge.JEIIntegration",
                    "dev.ftb.mods.ftblibrary.util.client.PositionedIngredient",
                    "mezz.jei.common.input.ClickableIngredient"}),
            Map.entry("HugeMushroomSpeciesMixin", new String[]{
                    "com.ferreusveritas.dynamictreesplus.tree.HugeMushroomSpecies",
                    "com.ferreusveritas.dynamictrees.tree.species.Species",
                    "com.ferreusveritas.dynamictrees.init.DTConfigs"}),
            Map.entry("ImmersiveConvergenceBakeFix", new String[]{
                    "com.immersiveconvergence.api.client.split.SplitModelHandler"}),
            Map.entry("IngredientAccessor", new String[]{
                    "net.darkhax.botanypots.BotanyPotHelper"}),
            Map.entry("InventoryPetsNbtNullFix", new String[]{
                    "com.inventorypets.pets.petChest"}),
            Map.entry("ItemCollectorBlockEntityMixin", new String[]{
                    "com.supermartijn642.itemcollectors.CollectorBlockEntity"}),
            Map.entry("JadeEnumPluginLoadFix", new String[]{
                    "snownee.jade.util.CommonProxy"}),
            Map.entry("JITLClientEssenceNullFix", new String[]{
                    "net.jitl.client.essence.ClientEssence",
                    "net.jitl.common.capability.essence.PlayerEssenceProvider"}),
            Map.entry("JITLDeathCrashFix", new String[]{
                    "net.jitl.client.stats.PacketPlayerStats",
                    "net.jitl.common.capability.stats.PlayerStatsProvider"}),
            Map.entry("JITLOfflineCrashFix", new String[]{
                    "net.jitl.client.ClientLoginChecker",
                    "net.jitl.core.helper.InternetHandler"}),
            Map.entry("KryptonReforgedVarIntFix", new String[]{
                    "me.steinborn.krypton.mod.shared.network.VarintByteDecoder",
                    "dev.tonimatas.packetfixer.util.Config"}),
            Map.entry("LodestoneMixin", new String[]{
                    "team.lodestar.lodestone.handlers.RenderHandler",
                    "qouteall.imm_ptl.core.render.context_management.PortalRendering"}),
            Map.entry("ModernFixCoverBakeFix", new String[]{
                    "org.embeddedt.modernfix.dynamicresources.DynamicBakedModelProvider",
                    "com.faktocraft.client.model.CoverBakedModel",
                    "com.faktocraft.common.cover.CoverSupport",
                    "com.faktocraft.common.registries.ModBlocks"}),
            Map.entry("ModernFixDynamicModelDrainFix", new String[]{
                    "org.embeddedt.modernfix.dynamicresources.DynamicBakedModelProvider"}),
            Map.entry("MixinSoulslikeWeaponryLoadCheck", new String[]{
                    "net.soulsweaponry.util.WeaponUtil"}),
            Map.entry("MinecontrollersSkullRegisterFix", new String[]{
                    "com.abadon.minecontrollers.MinecontrollersBlocks"}),
            Map.entry("MobEffectInstanceAccessor", new String[]{
                    "com.lothrazar.library.util.EntityUtil"}),
            Map.entry("MovingElevatorsCabinCLCompat", new String[]{
                    "com.supermartijn642.movingelevators.elevator.ElevatorCabinLevel",
                    "me.erykczy.colorfullighting.api.CLWrapperAttachments",
                    "me.erykczy.colorfullighting.common.ColoredLightEngine"}),
            Map.entry("RecipeManagerAccessor", new String[]{
                    "net.darkhax.botanypots.BotanyPotHelper"}),
            Map.entry("SplitModelHandlerAccessor", new String[]{
                    "com.immersiveconvergence.api.client.split.SplitModelHandler"}),
            Map.entry("SoLCarrotFoodsNullFix", new String[]{
                    "com.cazsius.solcarrot.client.FoodItems"}),
            Map.entry("SolarCraftShaderLoadCrashFix", new String[]{
                    "com.finderfeed.solarcraft.content.blocks.render.RuneEnergyPylonRenderer"}),
            Map.entry("VoltexCoverBakeFix", new String[]{
                    "com.jykito.industrialcore.client.ClientSetup",
                    "com.jykito.industrialcore.client.CoverModel",
                    "com.jykito.industrialcore.block.custom.cover.Covers"}),
            Map.entry("VoltexDynamicCoverFix", new String[]{
                    "org.embeddedt.modernfix.dynamicresources.DynamicBakedModelProvider",
                    "com.jykito.industrialcore.client.CoverModel",
                    "com.jykito.industrialcore.client.GlassCtmModel",
                    "com.jykito.industrialcore.block.custom.cover.Covers"}),
            Map.entry("SpeciesAccessor", new String[]{
                    "com.ferreusveritas.dynamictrees.tree.species.Species"})
    );

    @Override
    public void onLoad(String mixinPackage) {
        this.mixinPackage = mixinPackage == null ? "" : mixinPackage;
        try {
            MysticMixinConfig.load();
        } catch (Exception | LinkageError exception) {
            LOGGER.warn("Could not load Mystic Monstrosity Fixes mixin options; using defaults", exception);
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        try {
            if (!MysticMixinConfig.allow(mixinClassName)) {
                return false;
            }
            String mixinName = mixinClassName == null
                    ? null
                    : mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
            if (mixinName == null) {
                return true;
            }

            for (String required : REQUIRED_CLASSES.getOrDefault(mixinName, new String[0])) {
                if (!isClassPresent(required)) {
                    LOGGER.warn("{} is not loaded, skipping {}", required, mixinClassName);
                    return false;
                }
            }
            return membersMatch(mixinClassName, targetClassName);
        } catch (Exception | LinkageError exception) {
            LOGGER.warn("Could not evaluate mixin {} against {}; skipping mixin", mixinClassName, targetClassName, exception);
            return false;
        }
    }

    /**
     * Validates the hard member references of a mixin against the target class that is actually
     * installed. Mod classes are read as resources so this never loads the target early. If the
     * target, a shadowed member, an accessor/invoker member, an overwrite, or an injector target
     * method is gone, the mixin is skipped instead of failing transformation.
     */
    private static boolean membersMatch(String mixinClassName, String targetClassName) {

        try {
            return membersMatchUnchecked(mixinClassName, targetClassName);
        } catch (Exception | LinkageError exception) {
            LOGGER.warn("Could not validate mixin {} against {}; skipping mixin", mixinClassName, targetClassName, exception);
            return false;
        }
    }

    private static boolean membersMatchUnchecked(String mixinClassName, String targetClassName) {

        if (targetClassName == null || isNonModTarget(targetClassName)) {
            return true;
        }
        final ClassNode mixin = readClass(mixinClassName);
        if (mixin == null) {
            LOGGER.warn("Could not read mixin class {}, skipping {}", mixinClassName, mixinClassName);
            return false;
        }
        if (readClass(targetClassName) == null) {
            LOGGER.warn("Mixin target {} was not found, skipping {}", targetClassName, mixinClassName);
            return false;
        }

        for (FieldNode field : mixin.fields) {
            if (getAnnotation(field.visibleAnnotations, field.invisibleAnnotations, SHADOW) != null
                    && !hasField(targetClassName, field.name, field.desc, isStatic(field.access), new HashSet<>())) {
                return warnMissing(mixinClassName, targetClassName, "shadow field", field.name, field.desc);
            }
        }

        for (MethodNode method : mixin.methods) {
            if (getAnnotation(method.visibleAnnotations, method.invisibleAnnotations, SHADOW) != null
                    && !hasMethod(targetClassName, method.name, method.desc, isStatic(method.access), new HashSet<>())) {
                return warnMissing(mixinClassName, targetClassName, "shadow method", method.name, method.desc);
            }
            if (getAnnotation(method.visibleAnnotations, method.invisibleAnnotations, OVERWRITE) != null
                    && !hasMethod(targetClassName, method.name, method.desc, isStatic(method.access), new HashSet<>())) {
                return warnMissing(mixinClassName, targetClassName, "overwrite", method.name, method.desc);
            }

            final AnnotationNode accessor = getAnnotation(method.visibleAnnotations, method.invisibleAnnotations, ACCESSOR);
            if (accessor != null) {
                final String fieldName = stringValue(accessor, "value", method.name);
                final String fieldDesc = accessorFieldDescriptor(method);
                if (!hasField(targetClassName, fieldName, fieldDesc, isStatic(method.access), new HashSet<>())) {
                    return warnMissing(mixinClassName, targetClassName, "accessor field", fieldName, fieldDesc);
                }
            }

            final AnnotationNode invoker = getAnnotation(method.visibleAnnotations, method.invisibleAnnotations, INVOKER);
            if (invoker != null) {
                final String methodName = stringValue(invoker, "value", method.name);
                if (!hasMethod(targetClassName, methodName, method.desc, isStatic(method.access), new HashSet<>())) {
                    return warnMissing(mixinClassName, targetClassName, "invoker method", methodName, method.desc);
                }
            }

            for (String injector : INJECTOR_ANNOTATIONS) {
                final AnnotationNode annotation = getAnnotation(method.visibleAnnotations, method.invisibleAnnotations, injector);
                if (annotation != null) {
                    if (!injectorTargetsMatch(mixinClassName, targetClassName, annotation)) {
                        return false;
                    }
                    if (!atTargetsMatch(mixinClassName, targetClassName, annotation)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private static boolean injectorTargetsMatch(String mixinClassName, String targetClassName, AnnotationNode annotation) {

        for (String selector : strings(annotationValueObject(annotation, "method"))) {
            final String target = selector.trim();
            if (target.isEmpty() || "*".equals(target) || target.contains("*")) {
                continue;
            }
            String name = target;
            String descriptor = null;
            final int parenthesis = target.indexOf('(');
            if (parenthesis >= 0) {
                name = target.substring(0, parenthesis);
                descriptor = target.substring(parenthesis);
            }
            final int space = name.indexOf(' ');
            if (space >= 0) {
                name = name.substring(space + 1);
            }
            name = name.trim();
            if (!name.isEmpty() && !hasMethod(targetClassName, name, descriptor, null, new HashSet<>())) {
                return warnMissing(mixinClassName, targetClassName, "injector target method", name, descriptor);
            }
        }
        return true;
    }

    private static boolean isNonModTarget(String targetClassName) {

        for (String prefix : NON_MOD_PREFIXES) {
            if (targetClassName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks the member referenced by INVOKE/FIELD/NEW/CONSTANT injection points when the owner is
     * not a Minecraft/Forge class. Owners under the game/Forge namespace are skipped because their
     * names are refmapped; mod and library classes use stable names and can be verified directly.
     */
    private static boolean atTargetsMatch(String mixinClassName, String targetClassName, AnnotationNode injector) {

        for (AnnotationNode at : atNodes(injector)) {
            final String kind = stringValue(at, "value", "");
            final String reference = stringValue(at, "target", null);
            if (reference == null || reference.isEmpty() || !reference.startsWith("L")) {
                continue;
            }
            final int semicolon = reference.indexOf(';');
            if (semicolon < 0) {
                continue;
            }
            final String ownerInternal = reference.substring(1, semicolon);
            if (ownerInternal.startsWith("[")) {
                continue;
            }
            final String owner = ownerInternal.replace('/', '.');
            if (isNonModTarget(owner)) {
                continue;
            }
            final String rest = reference.substring(semicolon + 1);
            if (rest.isEmpty()) {
                if ("NEW".equals(kind) && readClass(owner) == null) {
                    return warnMissing(mixinClassName, targetClassName, "@At NEW class", owner, null);
                }
                continue;
            }
            if ("INVOKE".equals(kind) || "INVOKE_ASSIGN".equals(kind)) {
                final int parenthesis = rest.indexOf('(');
                final String name = parenthesis >= 0 ? rest.substring(0, parenthesis) : rest;
                final String descriptor = parenthesis >= 0 ? rest.substring(parenthesis) : null;
                if (!name.isEmpty() && !hasMethod(owner, name, descriptor, null, new HashSet<>())) {
                    return warnMissing(mixinClassName, targetClassName, "@At " + kind + " member", name, descriptor);
                }
            } else if ("FIELD".equals(kind) || "CONSTANT".equals(kind)) {
                final int colon = rest.indexOf(':');
                final String name = colon >= 0 ? rest.substring(0, colon) : rest;
                final String descriptor = colon >= 0 ? rest.substring(colon + 1) : null;
                if (!name.isEmpty() && !hasField(owner, name, descriptor, null, new HashSet<>())) {
                    return warnMissing(mixinClassName, targetClassName, "@At " + kind + " member", name, descriptor);
                }
            } else if ("NEW".equals(kind)) {
                final int parenthesis = rest.indexOf('(');
                final String name = parenthesis >= 0 ? rest.substring(0, parenthesis) : rest;
                final String descriptor = parenthesis >= 0 ? rest.substring(parenthesis) : null;
                if ("<init>".equals(name) && !hasMethod(owner, name, descriptor, null, new HashSet<>())) {
                    return warnMissing(mixinClassName, targetClassName, "@At NEW constructor", name, descriptor);
                }
            }
        }
        return true;
    }

    private static boolean hasField(String className, String name, String descriptor, Boolean staticMember, Set<String> seen) {

        if (className == null || !seen.add(className)) {
            return false;
        }
        final ClassNode node = readClass(className);
        if (node == null) {
            return false;
        }
        for (FieldNode field : node.fields) {
            if (field.name.equals(name)
                    && (descriptor == null || field.desc.equals(descriptor))
                    && (staticMember == null || staticMember == isStatic(field.access))) {
                return true;
            }
        }
        if (hasField(node.superName, name, descriptor, staticMember, seen)) {
            return true;
        }
        for (String itf : node.interfaces) {
            if (hasField(itf, name, descriptor, staticMember, seen)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMethod(String className, String name, String descriptor, Boolean staticMember, Set<String> seen) {

        if (className == null || !seen.add(className)) {
            return false;
        }
        final ClassNode node = readClass(className);
        if (node == null) {
            return false;
        }
        for (MethodNode method : node.methods) {
            if (method.name.equals(name)
                    && (descriptor == null || method.desc.equals(descriptor))
                    && (staticMember == null || staticMember == isStatic(method.access))) {
                return true;
            }
        }
        if (hasMethod(node.superName, name, descriptor, staticMember, seen)) {
            return true;
        }
        for (String itf : node.interfaces) {
            if (hasMethod(itf, name, descriptor, staticMember, seen)) {
                return true;
            }
        }
        return false;
    }

    private static String accessorFieldDescriptor(MethodNode method) {

        final Type[] arguments = Type.getArgumentTypes(method.desc);
        final Type returnType = Type.getReturnType(method.desc);
        if (arguments.length == 1 && returnType.getSort() == Type.VOID) {
            return arguments[0].getDescriptor();
        }
        if (arguments.length == 0 && returnType.getSort() != Type.VOID) {
            return returnType.getDescriptor();
        }
        return null;
    }

    private static boolean warnMissing(String mixinClassName, String targetClassName, String kind, String name, String descriptor) {

        LOGGER.warn("Mixin {} no longer matches {}: missing {} {}{}; skipping mixin",
                mixinClassName, targetClassName, kind, name, descriptor == null ? "" : descriptor);
        return false;
    }

    private static AnnotationNode getAnnotation(List<AnnotationNode> visible, List<AnnotationNode> invisible, String descriptor) {

        AnnotationNode annotation = findAnnotation(visible, descriptor);
        return annotation != null ? annotation : findAnnotation(invisible, descriptor);
    }

    private static AnnotationNode findAnnotation(List<AnnotationNode> annotations, String descriptor) {

        if (annotations != null) {
            for (AnnotationNode annotation : annotations) {
                if (descriptor.equals(annotation.desc)) {
                    return annotation;
                }
            }
        }
        return null;
    }

    private static Object annotationValueObject(AnnotationNode annotation, String key) {

        if (annotation.values != null) {
            for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
                if (key.equals(annotation.values.get(i))) {
                    return annotation.values.get(i + 1);
                }
            }
        }
        return null;
    }

    private static String stringValue(AnnotationNode annotation, String key, String fallback) {

        final Object value = annotationValueObject(annotation, key);
        if (value instanceof String string && !string.isEmpty()) {
            return string;
        }
        if (value instanceof List<?> list) {
            for (Object element : list) {
                if (element instanceof String string && !string.isEmpty()) {
                    return string;
                }
            }
        }
        return fallback;
    }

    private static List<String> strings(Object value) {

        if (value instanceof String string) {
            return List.of(string);
        }
        if (value instanceof List<?> list) {
            final List<String> strings = new java.util.ArrayList<>();
            for (Object element : list) {
                if (element instanceof String string) {
                    strings.add(string);
                }
            }
            return strings;
        }
        return Collections.emptyList();
    }

    private static boolean isStatic(int access) {

        return (access & Opcodes.ACC_STATIC) != 0;
    }

    private static ClassNode readClass(String className) {

        final ClassNode cached = CLASS_CACHE.get(className);
        if (cached != null) {
            return cached;
        }
        final ClassLoader loader = classLoader();
        if (loader == null) {
            return null;
        }
        try (InputStream stream = loader.getResourceAsStream(className.replace('.', '/') + ".class")) {
            if (stream == null) {
                return null;
            }
            final ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            CLASS_CACHE.put(className, node);
            return node;
        } catch (IOException | RuntimeException exception) {
            LOGGER.debug("Could not read {} while validating mixins", className, exception);
            return null;
        }
    }

    private static ClassLoader classLoader() {

        final ClassLoader context = Thread.currentThread().getContextClassLoader();
        return context != null ? context : MysticMonstrosityFixesMixinPlugin.class.getClassLoader();
    }

    /**
     * @return true if the class is on the classpath. The class is looked up as a resource rather
     *         than loaded, the same way Mixin resolves it, because loading a mixin target here would
     *         trip Mixin's "target was loaded too early" check.
     */
    private static boolean isClassPresent(String className) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = MysticMonstrosityFixesMixinPlugin.class.getClassLoader();
        }
        return loader != null && loader.getResource(className.replace('.', '/') + ".class") != null;
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
        try {
            if (!this.mixinPackage.endsWith(".experimental")) {
                return Collections.emptyList();
            }
            if (!MysticMixinConfig.allow("com.name.mysticmonstrosityfixes.mixin.experimental.blockatlas.EmbeddiumShaderPageMixin")) {
                return Collections.emptyList();
            }
            if (!isClassPresent("me.jellysquid.mods.sodium.client.gl.shader.ShaderLoader")) {
                LOGGER.warn("Embeddium/Sodium shader loader was not found; skipping experimental block atlas mixins");
                return Collections.emptyList();
            }
            return List.of(
                    "blockatlas.EmbeddiumBlockRendererPageMixin",
                    "blockatlas.EmbeddiumChunkBindMixin",
                    "blockatlas.EmbeddiumCompactVertexPageMixin",
                    "blockatlas.EmbeddiumVanillaLikeVertexPageMixin",
                    "blockatlas.EmbeddiumVanillaVertexFormatMixin",
                    "blockatlas.EmbeddiumShaderPageMixin",
                    "blockatlas.EmbeddiumShaderParserMixin");
        } catch (Exception | LinkageError exception) {
            LOGGER.warn("Could not determine optional experimental mixins; skipping them", exception);
            return Collections.emptyList();
        }
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {
        try {
            PRE_APPLY_METHODS.put(applyKey(targetClassName, mixinClassName), methodNames(targetClass));
        } catch (Exception | LinkageError exception) {
            LOGGER.warn("Could not snapshot {} before applying {}; injector audit disabled for this mixin",
                    targetClassName, mixinClassName, exception);
        }
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {
        warnAboutFailedInjectors(targetClassName, targetClass, mixinClassName);
    }

    /**
     * Mixin only adds a generated handler method when an injector actually finds its target and
     * injection points. With {@code defaultRequire: 0} a miss is non-fatal but silent, so compare
     * the target's method set after apply and warn for every injector callback without a handler.
     */
    private static void warnAboutFailedInjectors(String targetClassName, ClassNode targetClass, String mixinClassName) {

        try {
            final Set<String> before = PRE_APPLY_METHODS.remove(applyKey(targetClassName, mixinClassName));
            final ClassNode mixin = readClass(mixinClassName);
            if (before == null || mixin == null) {
                return;
            }
            final Set<String> added = methodNames(targetClass);
            added.removeAll(before);

            for (MethodNode method : mixin.methods) {
                final AnnotationNode injector = getInjectorAnnotation(method);
                if (injector == null) {
                    continue;
                }
                final Set<String> handlers = new HashSet<>();
                for (String addedName : added) {
                    if (addedName.equals(method.name) || addedName.endsWith("$" + method.name)) {
                        handlers.add(addedName);
                    }
                }
                if (handlers.isEmpty()) {
                    LOGGER.warn("Mixin {} injector {}{} did not apply to {}; the target method or @At no longer matches",
                            mixinClassName, method.name, method.desc, targetClassName);
                    continue;
                }

                final int expected = atNodes(injector).size();
                final int applied = countHandlerCalls(targetClass, handlers);
                if (expected > 0 && applied < expected) {
                    LOGGER.warn("Mixin {} injector {}{} applied {}/{} injection point(s) to {}; some @At targets no longer match",
                            mixinClassName, method.name, method.desc, applied, expected, targetClassName);
                }
            }
        } catch (Exception | LinkageError exception) {
            LOGGER.warn("Could not audit injectors of {} on {}", mixinClassName, targetClassName, exception);
        }
    }

    private static AnnotationNode getInjectorAnnotation(MethodNode method) {

        for (String injector : INJECTOR_ANNOTATIONS) {
            final AnnotationNode annotation = getAnnotation(method.visibleAnnotations, method.invisibleAnnotations, injector);
            if (annotation != null) {
                return annotation;
            }
        }
        return null;
    }

    private static int countHandlerCalls(ClassNode targetClass, Set<String> handlerNames) {

        int calls = 0;
        for (MethodNode method : targetClass.methods) {
            if (method.instructions == null) {
                continue;
            }
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode call && handlerNames.contains(call.name)) {
                    calls++;
                }
            }
        }
        return calls;
    }

    private static List<AnnotationNode> atNodes(AnnotationNode injector) {

        final Object atValue = annotationValueObject(injector, "at");
        final List<AnnotationNode> atNodes = new java.util.ArrayList<>();
        if (atValue instanceof AnnotationNode node) {
            atNodes.add(node);
        } else if (atValue instanceof List<?> list) {
            for (Object element : list) {
                if (element instanceof AnnotationNode node) {
                    atNodes.add(node);
                }
            }
        }
        return atNodes;
    }

    private static Set<String> methodNames(ClassNode classNode) {

        final Set<String> names = new HashSet<>();
        if (classNode != null) {
            for (MethodNode method : classNode.methods) {
                names.add(method.name);
            }
        }
        return names;
    }

    private static String applyKey(String targetClassName, String mixinClassName) {

        return targetClassName + "\u0000" + mixinClassName;
    }
}
