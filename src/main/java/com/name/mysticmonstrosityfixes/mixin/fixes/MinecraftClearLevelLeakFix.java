package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.name.mysticmonstrosityfixes.leaks.MultiplayerSessionLeakCleanup;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clears the last static roots of a client session after the level is fully torn down.
 * <p>
 * {@code ClientPacketListener.close()} already replaces the closed session's recipe manager, but
 * the player and level are still set while {@code Minecraft.clearLevel} runs its transition ticks.
 * Blue Skies' ambient handler and Tetratic Combat's player map can therefore re-acquire the old
 * player during that window. The other remaining roots are TinyMultiBlockLib's
 * {@code FakeClientLevel.INSTANCE}, RSLS's {@code WorldRandomHolder.randoms} set (old fake levels
 * captured through FastChunkGen's {@code CheckedThreadLocalRandom.owner}), Cyclic's packager recipe
 * caches, AE2WTLib's weak player map, GeckoLib's synced animatable registry, and Smart Particles'
 * heap array. Each of those was reaching an old {@code ClientLevel} or {@code ClientPacketListener}
 * in the second dump.
 * <p>
 * This injects at the end of {@code Minecraft.clearLevel}, after {@code level} and {@code player}
 * are null, and after the integrated server has stopped. The preview renderer already returns early
 * when the level or player is null, so clearing the fake level here is safe. The method runs on
 * every disconnect/rejoin, so each old session gets its own final cleanup pass.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftClearLevelLeakFix {

    @Inject(method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("RETURN"))
    private void mmf$clearStaticSessionCaches(CallbackInfo ci) {

        MultiplayerSessionLeakCleanup.onLevelCleared();
    }
}
