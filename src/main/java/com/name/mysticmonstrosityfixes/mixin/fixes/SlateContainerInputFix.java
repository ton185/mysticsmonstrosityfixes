package com.name.mysticmonstrosityfixes.mixin.fixes;

import net.minecraft.client.gui.components.events.GuiEventListener;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Slate ports a container widget onto 1.20.1 but its mouse release, drag, key, and character
 * methods call the widget base instead of the focused child. A click can focus a text box or
 * slider, and the keystrokes and the rest of the drag never arrive.
 */
@Pseudo
@Mixin(targets = "dev.fallingcloud.slate.core.port.gui.AbstractContainerWidget", remap = false)
public abstract class SlateContainerInputFix {
    @Shadow(remap = false)
    @Nullable
    public abstract GuiEventListener m_7222_();

    @Nullable
    private GuiEventListener mysticmonstrosityfixes$focused() {
        GuiEventListener focused = this.m_7222_();
        return focused == null || focused == (Object) this ? null : focused;
    }

    @Inject(method = "m_6348_", at = @At("HEAD"), cancellable = true, remap = false)
    private void mysticmonstrosityfixes$mouseReleased(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        GuiEventListener focused = this.mysticmonstrosityfixes$focused();
        if (focused != null && focused.mouseReleased(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "m_7979_", at = @At("HEAD"), cancellable = true, remap = false)
    private void mysticmonstrosityfixes$mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY, CallbackInfoReturnable<Boolean> cir) {
        GuiEventListener focused = this.mysticmonstrosityfixes$focused();
        if (focused != null && focused.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            cir.setReturnValue(true);
        }
    }

    public boolean m_7933_(int keyCode, int scanCode, int modifiers) {
        GuiEventListener focused = this.mysticmonstrosityfixes$focused();
        return focused != null && focused.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean m_5534_(char codePoint, int modifiers) {
        GuiEventListener focused = this.mysticmonstrosityfixes$focused();
        return focused != null && focused.charTyped(codePoint, modifiers);
    }
}
