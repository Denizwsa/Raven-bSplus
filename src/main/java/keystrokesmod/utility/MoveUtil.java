package keystrokesmod.utility;

import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;

public class MoveUtil implements IMinecraftInstance {

    public static double speed() {
        if (mc.thePlayer == null) return 0.0;
        return Math.sqrt(mc.thePlayer.motionX * mc.thePlayer.motionX + mc.thePlayer.motionZ * mc.thePlayer.motionZ);
    }

    public static void strafe(double speed) {
        if (mc.thePlayer == null) return;
        float yaw = mc.thePlayer.rotationYaw;
        double radians = Math.toRadians(yaw);
        mc.thePlayer.motionX = -Math.sin(radians) * speed;
        mc.thePlayer.motionZ = Math.cos(radians) * speed;
    }

    public static double direction() {
        if (mc.thePlayer == null) return 0.0;
        float forward = mc.thePlayer.moveForward;
        float strafe = mc.thePlayer.moveStrafing;
        if (forward == 0 && strafe == 0) {
            return mc.thePlayer.rotationYaw;
        }
        float yaw = mc.thePlayer.rotationYaw;
        if (forward < 0) yaw += 180.0f;
        float f = 1.0f;
        if (forward < 0) f = -0.5f;
        else if (forward > 0) f = 0.5f;
        if (strafe > 0) yaw -= 90.0f * f;
        if (strafe < 0) yaw += 90.0f * f;
        return MathHelper.wrapAngleTo180_double(yaw);
    }
}
