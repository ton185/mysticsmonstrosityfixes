package com.name.mysticmonstrosityfixes.ae2;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Detects the ExtendedAE Plus 1.6.4+ split long/BigInteger storage shape that ACO 1.5.33's mixins do
 * not understand. Detection is bytecode based so it works before any of the target classes load.
 */
public final class ExtendedAePlusCompat {

    private static final String DATA_STORAGE = "com.extendedae_plus.util.storage.InfinityDataStorage";
    private static final String BIG_CELL_INVENTORY = "com.extendedae_plus.api.storage.InfinityBigIntegerCellInventory";

    private static volatile Boolean splitStorage;

    private ExtendedAePlusCompat() {
    }

    public static boolean isPresent() {
        return ModResourceLoader.hasClass(DATA_STORAGE) && ModResourceLoader.hasClass(BIG_CELL_INVENTORY);
    }

    public static boolean isSplitStorageShape() {
        Boolean cached = splitStorage;
        if (cached != null) {
            return cached;
        }
        Boolean value = detectSplitStorage();
        if (value == null) {
            return false;
        }
        splitStorage = value;
        return value;
    }

    private static Boolean detectSplitStorage() {
        ClassNode storage = ModResourceLoader.readClass(DATA_STORAGE, false);
        ClassNode inventory = ModResourceLoader.readClass(BIG_CELL_INVENTORY, false);
        if (storage == null || inventory == null) {
            return null;
        }
        boolean hasLong = hasField(storage, "longAmounts");
        boolean hasBig = hasField(storage, "bigAmounts");
        boolean hasCombined = hasField(storage, "amounts");
        boolean hasSetItemCount = hasMethod(storage, "setItemCount", "(Ljava/math/BigInteger;)V");
        boolean hasCellMap = hasMethod(inventory, "getCellStoredMap", null);
        boolean hasStorageAccess = hasMethod(inventory, "getExistingCellStorage",
                "()Lcom/extendedae_plus/util/storage/InfinityDataStorage;");
        boolean hasWritableStorage = hasMethod(inventory, "getWritableCellStorage",
                "()Lcom/extendedae_plus/util/storage/InfinityDataStorage;");
        boolean hasSave = hasMethod(inventory, "saveChanges", "()V");
        boolean hasAssignUuid = hasMethod(inventory, "assignNewUUID", "()Ljava/util/UUID;");
        boolean hasUuid = hasMethod(inventory, "hasUUID", "()Z")
                && hasMethod(inventory, "getUUID", "()Ljava/util/UUID;");
        boolean hasMutations = hasMethod(inventory, "insert",
                "(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;Lappeng/api/networking/security/IActionSource;)J")
                && hasMethod(inventory, "extract",
                "(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;Lappeng/api/networking/security/IActionSource;)J");
        return hasLong && hasBig && !hasCombined && hasSetItemCount && !hasCellMap
                && hasStorageAccess && hasWritableStorage && hasSave && hasAssignUuid && hasUuid && hasMutations;
    }

    private static boolean hasField(ClassNode node, String name) {
        for (FieldNode field : node.fields) {
            if (name.equals(field.name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMethod(ClassNode node, String name, String descriptor) {
        for (MethodNode method : node.methods) {
            if (name.equals(method.name) && (descriptor == null || descriptor.equals(method.desc))) {
                return true;
            }
        }
        return false;
    }
}
