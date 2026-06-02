package keystrokesmod.utility.ravenbs;

import keystrokesmod.helper.RotationHelper;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;

public final class MovementUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private MovementUtil() {
    }

    public static int getSpeedLevel() {
        return Utils.getSpeedAmplifier();
    }

    public static double getForwardValue() {
        return mc.thePlayer.movementInput.moveForward;
    }

    public static double getLeftValue() {
        return mc.thePlayer.movementInput.moveStrafe;
    }

    public static boolean isForwardPressed() {
        return Utils.isBindDown(mc.gameSettings.keyBindForward);
    }

    public static float adjustYaw(float yaw, float forward, float strafe) {
        if (forward < 0.0f) {
            yaw += 180.0f;
        }
        float factor = forward < 0.0f ? -0.5f : forward > 0.0f ? 0.5f : 1.0f;
        if (strafe > 0.0f) {
            yaw -= 90.0f * factor;
        } else if (strafe < 0.0f) {
            yaw += 90.0f * factor;
        }
        return yaw;
    }

    public static double getSpeed() {
        return Utils.getHorizontalSpeed();
    }

    public static void setSpeed(double speed) {
        Utils.setSpeed(speed);
    }

    public static void setSpeed(double speed, float yaw) {
        double rad = Math.toRadians(yaw);
        mc.thePlayer.motionX = -Math.sin(rad) * speed;
        mc.thePlayer.motionZ = Math.cos(rad) * speed;
    }

    public static float getMoveYaw() {
        return Utils.getDirection() * (180f / (float) Math.PI);
    }

    public static void fixStrafe(float yaw) {
        float forward = mc.thePlayer.movementInput.moveForward;
        float strafe = mc.thePlayer.movementInput.moveStrafe;
        if (forward == 0 && strafe == 0) {
            return;
        }
        double angle = Math.toRadians(yaw);
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);
        float moveForward = (float) (forward * cos + strafe * sin);
        float moveStrafe = (float) (strafe * cos - forward * sin);
        mc.thePlayer.movementInput.moveForward = MathHelper.clamp_float(moveForward, -1f, 1f);
        mc.thePlayer.movementInput.moveStrafe = MathHelper.clamp_float(moveStrafe, -1f, 1f);
    }
}
