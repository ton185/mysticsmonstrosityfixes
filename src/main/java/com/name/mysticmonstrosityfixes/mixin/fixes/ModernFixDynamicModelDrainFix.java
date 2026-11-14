package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.google.common.collect.Sets;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashSet;

/**
 * ModernFix's dynamic model loading drains the set of blockstate files still to be read by taking
 * its first entry on each pass: {@code set.iterator().next()}, then a remove. A hash set keeps no
 * link between its entries, so a fresh iterator has to scan the backing table from the start to
 * reach the first occupied bucket, and each removal moves that bucket further along. The drain
 * therefore pays a growing table walk per file instead of one step. That is the main thread
 * sitting in {@code HashMap$KeyIterator.next} and {@code HashSet.iterator} while the title screen
 * loads.
 *
 * <p>The same set with its entries chained in insertion order starts its iterator at the first
 * entry directly, so the drain stays a single pass. Membership checks, adds, and removes are
 * unchanged. Only ModernFix reads this field outside the constructor, and only through the
 * {@code Set} interface.
 */
@Mixin(ModelBakery.class)
public class ModernFixDynamicModelDrainFix {
    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/collect/Sets;newHashSet()Ljava/util/HashSet;",
                    ordinal = 0)
    )
    private HashSet<ResourceLocation> mysticmonstrosityfixes$orderPendingBlockStateFiles() {
        return Sets.newLinkedHashSet();
    }
}
