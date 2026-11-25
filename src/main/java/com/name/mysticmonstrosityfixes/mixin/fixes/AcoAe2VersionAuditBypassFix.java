package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.name.mysticmonstrosityfixes.ae2.AcoVersionAudit;
import com.syaru.ae2craftingoptimizer.integration.ExperimentalCompatibilityValidator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * ACO's experimental audit only accepts exactly AE2 15.4.10 or UELM 15.5.0-uelm and otherwise stops
 * the server before the compiled crafting graph is used. The audit's AE2 version requirement is
 * skipped here so patch-level builds such as 15.4.12 keep the graph enabled; the other audit checks
 * still run, and a genuinely incompatible build will fail when the feature is used.
 * <p>
 * The version helper itself is loaded while ACO prepares its mixin config, before this config can
 * transform it, so the bypass is placed on the audit class that is loaded at server start.
 */
@Mixin(value = ExperimentalCompatibilityValidator.class, remap = false)
public abstract class AcoAe2VersionAuditBypassFix {

    @Inject(method = "requireSupportedAe2Version", at = @At("HEAD"), cancellable = true, remap = false)
    private static void mysticmonstrosityfixes$skipAe2VersionAudit(List<String> missing, CallbackInfo callback) {
        AcoVersionAudit.notifyBypassed();
        callback.cancel();
    }
}
