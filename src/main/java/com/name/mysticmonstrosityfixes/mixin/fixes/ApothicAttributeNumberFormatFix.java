package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.name.mysticmonstrosityfixes.apothic.ApothicAttributeValueFormat;
import dev.shadowsoffire.attributeslib.api.IFormattableAttribute;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.text.DecimalFormat;

@Pseudo
@Mixin(targets = "dev.shadowsoffire.attributeslib.client.AttributesGui", remap = false)
public class ApothicAttributeNumberFormatFix {
    private static final String VALUE_COMPONENT =
            "Ldev/shadowsoffire/attributeslib/api/IFormattableAttribute;toValueComponent(Lnet/minecraft/world/entity/ai/attributes/AttributeModifier$Operation;DLnet/minecraft/world/item/TooltipFlag;)Lnet/minecraft/network/chat/MutableComponent;";
    private static final String MODIFIER_COMPONENT =
            "Ldev/shadowsoffire/attributeslib/api/IFormattableAttribute;toComponent(Lnet/minecraft/world/entity/ai/attributes/AttributeModifier;Lnet/minecraft/world/item/TooltipFlag;)Lnet/minecraft/network/chat/MutableComponent;";

    @Redirect(method = "renderEntry", at = @At(value = "INVOKE", target = VALUE_COMPONENT), remap = false)
    private MutableComponent mmf$formatEntryValue(
            IFormattableAttribute attribute, AttributeModifier.Operation operation, double value, TooltipFlag flag
    ) {
        return ApothicAttributeValueFormat.formatComponent(attribute.toValueComponent(operation, value, flag));
    }

    @Redirect(method = "renderTooltip", at = @At(value = "INVOKE", target = VALUE_COMPONENT), remap = false)
    private MutableComponent mmf$formatTooltipValue(
            IFormattableAttribute attribute, AttributeModifier.Operation operation, double value, TooltipFlag flag
    ) {
        return ApothicAttributeValueFormat.formatComponent(attribute.toValueComponent(operation, value, flag));
    }

    @Redirect(method = "renderTooltip", at = @At(value = "INVOKE", target = MODIFIER_COMPONENT), remap = false)
    private MutableComponent mmf$formatTooltipModifier(
            IFormattableAttribute attribute, AttributeModifier modifier, TooltipFlag flag
    ) {
        return ApothicAttributeValueFormat.formatComponent(attribute.toComponent(modifier, flag));
    }

    @Redirect(
            method = "buildFormula",
            at = @At(value = "INVOKE", target = "Ljava/text/DecimalFormat;format(D)Ljava/lang/String;"),
            remap = false
    )
    private static String mmf$formatFormulaValue(DecimalFormat format, double value) {
        return ApothicAttributeValueFormat.formatNumber(value);
    }
}
