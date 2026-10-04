package com.name.mysticmonstrosityfixes.mixin;

import com.supermartijn642.movingelevators.elevator.ElevatorCabinLevel;
import me.erykczy.colorfullighting.api.CLWrapperAttachments;
import me.erykczy.colorfullighting.common.BlockEntityNbtCache;
import me.erykczy.colorfullighting.common.ColoredLightEngine;
import me.erykczy.colorfullighting.common.accessors.LevelAccessor;
import me.erykczy.colorfullighting.common.accessors.mixin.LevelAttachments;
import me.erykczy.colorfullighting.compat.flywheel.FlywheelCompat;
import me.erykczy.colorfullighting.compat.valkyrienskies.VsCompat;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * The elevator cabin is a fake Level that only proxies block states out of the client side cage, so
 * Colorful Lighting has no light data of its own for it. Making it a CL wrapper level and pointing it
 * at the level it currently lives in lets CL unwrap it and fall back to that world's lighting.
 *
 * The interface defaults already delegate to the wrapped level, but CL's LevelMixin adds concrete
 * versions of these methods to Level which win over interface defaults, so they are declared here to
 * forward explicitly. These are merged rather than overwritten since the target class itself does
 * not declare them.
 */
@Mixin(value = ElevatorCabinLevel.class, remap = false)
public abstract class MovingElevatorsCabinCLCompat implements CLWrapperAttachments {

    @Shadow
    private Level level;

    public Level colorfullighting$getWrappedLevel() {
        return this.level;
    }

    public ColoredLightEngine colorfullighting$getEngine() {
        return ((LevelAttachments) this.level).colorfullighting$getEngine();
    }

    public VsCompat colorfullighting$getVSCompat() {
        return ((LevelAttachments) this.level).colorfullighting$getVSCompat();
    }

    public LevelAccessor colorfullighting$getAccessor() {
        return ((LevelAttachments) this.level).colorfullighting$getAccessor();
    }

    public BlockEntityNbtCache colorfullighting$getNbtCache() {
        return ((LevelAttachments) this.level).colorfullighting$getNbtCache();
    }

    public FlywheelCompat colorfullighting$getFlywheelCompat() {
        return ((LevelAttachments) this.level).colorfullighting$getFlywheelCompat();
    }
}