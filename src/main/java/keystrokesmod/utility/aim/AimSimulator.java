package keystrokesmod.utility.aim;

import net.minecraft.util.MathHelper;

public class AimSimulator {

    public static float rotMove(float target, float current, float speed) {
        float diff = MathHelper.wrapAngleTo180_float(target - current);
        float maxStep = Math.abs(diff) * (speed / 20.0f);
        if (maxStep < 0.1f) return target;
        if (diff > maxStep) return current + maxStep;
        if (diff < -maxStep) return current - maxStep;
        return target;
    }
}
