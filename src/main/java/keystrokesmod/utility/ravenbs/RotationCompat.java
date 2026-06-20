package keystrokesmod.utility.ravenbs;

import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

public final class RotationCompat {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final double QUANTIZE = 0.00625;

    private RotationCompat() {
    }

    public static float wrapAngleDiff(float angle, float base) {
        return MathHelper.wrapAngleTo180_float(angle - base);
    }

    public static float quantizeAngle(float angle) {
        return (float) (Math.round(angle / QUANTIZE) * QUANTIZE);
    }

    public static float[] getRotationsTo(double relX, double relY, double relZ, float baseYaw, float basePitch) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
        double tx = mc.thePlayer.posX + relX;
        double ty = mc.thePlayer.posY + mc.thePlayer.getEyeHeight() + relY;
        double tz = mc.thePlayer.posZ + relZ;
        return RotationUtils.getRotationsFromEye(eye, tx, ty, tz);
    }

    public static MovingObjectPosition rayTrace(float yaw, float pitch, double reach, float partialTicks) {
        return RotationUtils.rayCastBlock(reach, yaw, pitch);
    }

    public static float clampAngle(float delta, float tolerance) {
        if (Math.abs(delta) <= tolerance) {
            return delta;
        }
        return delta > 0 ? tolerance : -tolerance;
    }
}
