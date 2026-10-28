package com.name.mysticmonstrosityfixes.mixin.fixes;

import com.mojang.logging.LogUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * A stage option is pressed on click and focused only if the release is still over that option.
 * A hover miss on the way up drops the selection. Focus the pressed option when the click lands.
 * The release still runs the option once, when it is still under the cursor.
 */
@Pseudo
@Mixin(targets = "dev.fallingcloud.slate.core.stage.Stage", remap = false)
public class SlateStageClickFocusFix {
    private static Field pressedField;
    private static Method focusMethod;
    private static boolean lookupFailed;

    @Inject(method = "mouseClicked", at = @At("RETURN"), remap = false)
    private void mysticmonstrosityfixes$keepClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != 0 || !Boolean.TRUE.equals(cir.getReturnValue()) || lookupFailed) {
            return;
        }
        try {
            if (pressedField == null) {
                Class<?> owner = this.getClass();
                while (owner != null && pressedField == null) {
                    try {
                        pressedField = owner.getDeclaredField("pressed");
                    } catch (NoSuchFieldException ignored) {
                        owner = owner.getSuperclass();
                    }
                }
                if (pressedField == null) {
                    lookupFailed = true;
                    return;
                }
                pressedField.setAccessible(true);
                focusMethod = pressedField.getDeclaringClass().getDeclaredMethod("focus", pressedField.getType());
                focusMethod.setAccessible(true);
            }
            Object pressed = pressedField.get(this);
            if (pressed != null) {
                focusMethod.invoke(this, pressed);
            }
        } catch (ReflectiveOperationException exception) {
            lookupFailed = true;
            LogUtils.getLogger().warn("Could not keep the clicked Slate stage option focused", exception);
        }
    }
}
