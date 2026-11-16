package com.name.mysticmonstrosityfixes.tweaks;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.cyclops.everlastingabilities.ability.AbilityTypeAttributeModifier;
import org.cyclops.everlastingabilities.api.IAbilityType;

import java.util.List;
import java.util.Map;

/**
 * Shared rules for the Everlasting Abilities uncap tweak. Abilities with a max level of one are
 * left capped because extra levels do nothing for them. Resistance is left capped because higher
 * levels were too strong. Abilities that already report a negative max level are treated as
 * uncapped so their tooltip hides the infinity marker as well.
 */
public final class EverlastingAbilitiesUncapper {
    private static final String RESISTANCE_TRANSLATION_KEY = "ability.everlastingabilities.resistance";
    private static final String LEVEL_TRANSLATION_KEY = "general.everlastingabilities.level";
    private static final String UNCAPPED_LEVEL_TRANSLATION_KEY = "general.mysticmonstrosityfixes.level";

    private EverlastingAbilitiesUncapper() {
    }

    public static boolean isUncapped(IAbilityType abilityType) {
        int maxLevel = abilityType.getMaxLevel();
        return maxLevel < 0 || (maxLevel > 1
                && !RESISTANCE_TRANSLATION_KEY.equals(abilityType.getTranslationKey()));
    }

    public static int maxLevelOrInfinitySafe(IAbilityType abilityType) {
        return isUncapped(abilityType) ? Integer.MAX_VALUE : abilityType.getMaxLevelInfinitySafe();
    }

    /**
     * Returns the attribute modifier for a level, creating and caching one when an uncapped ability
     * is above the level its modifier map was built for.
     */
    public static AttributeModifier modifierForLevel(
            AbilityTypeAttributeModifier abilityType, int level, Map<Integer, AttributeModifier> modifiers
    ) {
        AttributeModifier modifier = modifiers.get(level);
        if (modifier == null && isUncapped(abilityType)) {
            modifier = new AttributeModifier(
                    "everlastingabilities:modifier_" + abilityType.getTranslationKey() + "_" + level,
                    abilityType.getAmountFactor() * level,
                    abilityType.getOperation());
            modifiers.put(level, modifier);
        }
        return modifier;
    }

    /**
     * Replaces the "Level: x / max" tooltip line with "Level: x" for uncapped abilities. The
     * ability name is already in the list when the level line is added, so Resistance can be
     * detected before its level line is touched.
     */
    public static Component rewriteLevelLine(List<Component> lines, Component levelLine) {
        if (lines == null || !(levelLine.getContents() instanceof TranslatableContents contents)
                || !LEVEL_TRANSLATION_KEY.equals(contents.getKey())) {
            return levelLine;
        }
        for (Component line : lines) {
            if (line.getContents() instanceof TranslatableContents existing
                    && RESISTANCE_TRANSLATION_KEY.equals(existing.getKey())) {
                return levelLine;
            }
        }
        Object[] args = contents.getArgs();
        if (args.length < 2 || !(args[0] instanceof Integer level) || !isUncappedMaxValue(args[1])) {
            return levelLine;
        }
        return Component.translatable(UNCAPPED_LEVEL_TRANSLATION_KEY, level);
    }

    private static boolean isUncappedMaxValue(Object maxLevel) {
        return "Inf".equals(maxLevel) || (maxLevel instanceof Integer value && value > 1);
    }
}
