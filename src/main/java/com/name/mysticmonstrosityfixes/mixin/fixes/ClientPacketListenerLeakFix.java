package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.name.mysticmonstrosityfixes.leaks.MultiplayerSessionLeakCleanup;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.common.crafting.conditions.ICondition;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Releases the recipe graph owned by a closing client connection.
 * <p>
 * A {@code ClientPacketListener} owns a full {@link RecipeManager}; in this pack FastSuite replaces
 * it with an {@code AuxRecipeManager} that also caches per-type recipe lists. With roughly 50k
 * recipes per session that graph is close to a gigabyte. Vanilla normally lets the whole listener
 * become garbage, so the recipes go with it. Several mods in this pack instead keep the old
 * {@code LocalPlayer} (and therefore its {@code connection}) in static fields after leaving a
 * multiplayer server, so every reconnect retained the previous session's recipes. The heap dump
 * after three reconnects shows 160k {@code ShapedRecipe} instances and about 3 GB of item stacks,
 * ingredients, and recipe maps.
 * <p>
 * {@link ClientPacketListener#close()} only clears the level. Replacing the manager once the
 * connection is closed makes the recipe graph unreachable even while another mod still pins the
 * listener, and it drops FastSuite's cached lists without depending on FastSuite internals. Known
 * static player caches are cleared here as a best effort, and all remaining session roots are
 * cleared again after the level teardown by {@link MultiplayerSessionLeakCleanup} and the
 * {@link MinecraftClearLevelLeakFix} mixin.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerLeakFix {

    @Shadow
    @Final
    @Mutable
    private RecipeManager recipeManager;

    @Inject(method = "close", at = @At("RETURN"))
    private void mmf$releaseRecipeManager(CallbackInfo ci) {

        // close() runs for every connection: once per disconnect/rejoin. Each session owns a new
        // ClientPacketListener and RecipeManager, so this replacement has to happen every time.
        this.recipeManager = new RecipeManager(ICondition.IContext.EMPTY);
        MultiplayerSessionLeakCleanup.onConnectionClosed();
    }
}
