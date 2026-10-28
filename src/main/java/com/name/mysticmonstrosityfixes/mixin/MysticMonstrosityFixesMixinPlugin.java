package com.name.mysticmonstrosityfixes.mixin;

import com.mojang.logging.LogUtils;
import com.name.mysticmonstrosityfixes.MysticMixinConfig;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 */
public class MysticMonstrosityFixesMixinPlugin implements IMixinConfigPlugin {
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
            Map.entry("FaktocraftCoverBakeFix", new String[]{
                    "com.faktocraft.client.FaktocraftClient",
                    "com.faktocraft.common.cover.CoverSupport",
                    "com.faktocraft.client.model.CoverBakedModel"}),
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
            Map.entry("MixinSoulslikeWeaponryLoadCheck", new String[]{
                    "net.soulsweaponry.util.WeaponUtil"}),
            Map.entry("MovingElevatorsCabinCLCompat", new String[]{
                    "com.supermartijn642.movingelevators.elevator.ElevatorCabinLevel",
                    "me.erykczy.colorfullighting.api.CLWrapperAttachments",
                    "me.erykczy.colorfullighting.common.ColoredLightEngine"}),
            Map.entry("RecipeManagerAccessor", new String[]{
                    "net.darkhax.botanypots.BotanyPotHelper"}),
            Map.entry("SplitModelHandlerAccessor", new String[]{
                    "com.immersiveconvergence.api.client.split.SplitModelHandler"}),
            Map.entry("SlateContainerInputFix", new String[]{
                    "dev.fallingcloud.slate.core.port.gui.AbstractContainerWidget"}),
            Map.entry("SlateStageClickFocusFix", new String[]{
                    "dev.fallingcloud.slate.core.stage.Stage"}),
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
        MysticMixinConfig.load();
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
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
                LogUtils.getLogger().info("{} is not loaded, skipping {}", required, mixinClassName);
                return false;
            }
        }
        return true;
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
        if (!this.mixinPackage.endsWith(".experimental")) {
            return Collections.emptyList();
        }
        if (!MysticMixinConfig.allow("com.name.mysticmonstrosityfixes.mixin.experimental.blockatlas.EmbeddiumShaderPageMixin")) {
            return Collections.emptyList();
        }
        if (!isClassPresent("me.jellysquid.mods.sodium.client.gl.shader.ShaderLoader")) {
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
