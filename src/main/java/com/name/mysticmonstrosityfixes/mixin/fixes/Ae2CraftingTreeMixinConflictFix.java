package com.name.mysticmonstrosityfixes.mixin.fixes;

import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingTreeNode;
import com.name.mysticmonstrosityfixes.ae2.CraftingTreePatternBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collection;

/**
 * Runs ACO's memoized pattern lookup and InsaneAE2's provider-card filter on the same crafting-tree
 * query. ACO merges its redirect handler into {@code CraftingTreeNode}; this bridge wraps the memo
 * helper call inside that handler, which stays stable even if ACO renames the handler.
 */
@Mixin(value = CraftingTreeNode.class, priority = 1_000_000, remap = false)
public abstract class Ae2CraftingTreeMixinConflictFix {

    @Redirect(
            method = "aco$reuseUnmodifiedPatternCandidates",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/syaru/ae2craftingoptimizer/optimization/CraftingCalculationMemo;patternCandidates(Ljava/lang/Object;Lappeng/api/networking/crafting/ICraftingService;Lappeng/api/stacks/AEKey;)Ljava/util/Collection;",
                    remap = false
            ),
            require = 0,
            expect = 0,
            remap = false
    )
    private Collection<?> mysticmonstrosityfixes$combineMemoizedCandidates(
            Object jobArgument, ICraftingService service, AEKey key
    ) {
        return CraftingTreePatternBridge.combine(jobArgument, service, key);
    }
}
