package keystrokesmod.config;

import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.render.Animations;

/**
 * Holds first-person swing animation settings for Raven bS+.
 */
public final class AnimationConfig {
    public static AnimationMode mode = AnimationMode.VANILLA;
    public static int scale = 100;
    public static int swingSpeed = 6;
    public static boolean enabled = true;

    private AnimationConfig() {
    }

    public static void sync() {
        try {
            Animations animModule = ModuleManager.animations;
            if (animModule != null && animModule.isEnabled()) {
                enabled = true;
                AnimationMode[] modes = AnimationMode.values();
                if (animModule.mode.getValue() < modes.length) {
                    mode = modes[animModule.mode.getValue()];
                }
                scale = animModule.scale.getValue();
                swingSpeed = animModule.swingSpeed.getValue();
            } else {
                enabled = false;
            }
        } catch (Exception ignored) {
        }
    }

    public static AnimationMode getMode() {
        return mode;
    }

    public static void setMode(AnimationMode animationMode) {
        AnimationConfig.mode = animationMode;
    }

    public static int getScale() {
        return scale;
    }

    public static void setScale(int value) {
        scale = value;
    }

    public static int getSwingSpeed() {
        return swingSpeed;
    }

    public static void setSwingSpeed(int value) {
        swingSpeed = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }
}
