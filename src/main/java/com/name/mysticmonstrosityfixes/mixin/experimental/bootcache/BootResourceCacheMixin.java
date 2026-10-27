package com.name.mysticmonstrosityfixes.mixin.experimental.bootcache;

import com.name.mysticmonstrosityfixes.BootResourceCache;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Serves cached namespace lists, directory listings, and text bytes for mod jars and resource packs.
 */
@Mixin({FilePackResources.class, PathPackResources.class, VanillaPackResources.class})
public abstract class BootResourceCacheMixin {
    @Inject(method = "getNamespaces", at = @At("HEAD"), cancellable = true)
    private void mysticmonstrosityfixes$serveNamespaces(PackType type, CallbackInfoReturnable<Set<String>> callback) {
        String stamp = this.mysticmonstrosityfixes$stamp();
        if (stamp == null) {
            return;
        }
        Set<String> names = BootResourceCache.namespaces(stamp, type);
        if (names != null) {
            callback.setReturnValue(names);
        }
    }

    @Inject(method = "getNamespaces", at = @At("RETURN"))
    private void mysticmonstrosityfixes$storeNamespaces(PackType type, CallbackInfoReturnable<Set<String>> callback) {
        String stamp = this.mysticmonstrosityfixes$stamp();
        if (stamp != null && callback.getReturnValue() != null) {
            BootResourceCache.storeNamespaces(stamp, type, callback.getReturnValue());
        }
    }

    @Inject(method = "listResources", at = @At("HEAD"), cancellable = true)
    private void mysticmonstrosityfixes$serveListing(PackType type, String namespace, String prefix,
                                                     PackResources.ResourceOutput output, CallbackInfo callback) {
        String stamp = this.mysticmonstrosityfixes$stamp();
        if (stamp != null && BootResourceCache.serveListing(stamp, type, namespace, prefix, output)) {
            callback.cancel();
        }
    }

    @ModifyVariable(method = "listResources", at = @At("HEAD"), argsOnly = true)
    private PackResources.ResourceOutput mysticmonstrosityfixes$recordListing(PackResources.ResourceOutput output,
                                                                              PackType type, String namespace,
                                                                              String prefix,
                                                                              PackResources.ResourceOutput captured) {
        String stamp = this.mysticmonstrosityfixes$stamp();
        if (stamp == null) {
            return output;
        }
        BootResourceCache.beginListing(stamp, type, namespace, prefix);
        return (id, supplier) -> {
            BootResourceCache.noteListed(id);
            if (!BootResourceCache.cacheable(id)) {
                output.accept(id, supplier);
                return;
            }
            output.accept(id, mysticmonstrosityfixes$remember(stamp, type, id, supplier));
        };
    }

    @Inject(method = "listResources", at = @At("RETURN"))
    private void mysticmonstrosityfixes$finishListing(PackType type, String namespace, String prefix,
                                                      PackResources.ResourceOutput output, CallbackInfo callback) {
        BootResourceCache.finishListing();
    }

    @Inject(method = "getResource", at = @At("HEAD"), cancellable = true)
    private void mysticmonstrosityfixes$serveResource(PackType type, ResourceLocation id,
                                                      CallbackInfoReturnable<IoSupplier<InputStream>> callback) {
        String stamp = this.mysticmonstrosityfixes$stamp();
        if (stamp == null || !BootResourceCache.cacheable(id)) {
            return;
        }
        byte[] data = BootResourceCache.bytes(stamp, type, id);
        if (data != null) {
            callback.setReturnValue(() -> new ByteArrayInputStream(data));
        }
    }

    @Inject(method = "getResource", at = @At("RETURN"), cancellable = true)
    private void mysticmonstrosityfixes$recordResource(PackType type, ResourceLocation id,
                                                       CallbackInfoReturnable<IoSupplier<InputStream>> callback) {
        String stamp = this.mysticmonstrosityfixes$stamp();
        IoSupplier<InputStream> original = callback.getReturnValue();
        if (stamp == null || original == null || !BootResourceCache.cacheable(id)) {
            return;
        }
        if (BootResourceCache.peek(stamp, type, id) != null) {
            return;
        }
        callback.setReturnValue(mysticmonstrosityfixes$remember(stamp, type, id, original));
    }

    private static IoSupplier<InputStream> mysticmonstrosityfixes$remember(String stamp, PackType type,
                                                                           ResourceLocation id,
                                                                           IoSupplier<InputStream> original) {
        byte[][] held = new byte[1][];
        return () -> {
            if (held[0] == null) {
                try (InputStream in = original.get()) {
                    held[0] = in.readAllBytes();
                }
                BootResourceCache.putFile(stamp, type, id, held[0]);
            }
            return new ByteArrayInputStream(held[0]);
        };
    }

    private String mysticmonstrosityfixes$stamp() {
        String packId = ((PackResources) (Object) this).packId();
        if ((Object) this instanceof FilePackResources) {
            File file = ((FilePackResourcesAccessor) (Object) this).mysticmonstrosityfixes$file();
            if (BootResourceCache.worldOwned(file.getAbsolutePath())) {
                return null;
            }
            return BootResourceCache.packStamp(packId, file.length() + ":" + file.lastModified());
        }
        if ((Object) this instanceof PathPackResources) {
            Path root = ((PathPackResourcesAccessor) (Object) this).mysticmonstrosityfixes$root();
            if (BootResourceCache.worldOwned(root.toAbsolutePath().toString())) {
                return null;
            }
            long modified = 0L;
            try {
                modified = Files.getLastModifiedTime(root).toMillis();
            } catch (IOException ignored) {
                return null;
            }
            return BootResourceCache.packStamp(packId, root.toAbsolutePath().normalize() + ":" + modified);
        }
        return BootResourceCache.packStamp(packId, "vanilla");
    }
}
