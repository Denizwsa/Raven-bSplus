package keystrokesmod.utility.ravenbs;

import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;

import java.util.List;

public final class PlayerMovementUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private PlayerMovementUtil() {
    }

    public static boolean isAirAbove() {
        BlockPos above = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY + 2, mc.thePlayer.posZ);
        return BlockUtils.replaceable(above);
    }

    public static boolean isAirBelow() {
        BlockPos below = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 2, mc.thePlayer.posZ);
        return BlockUtils.replaceable(below);
    }

    public static boolean isUsingItem() {
        return mc.thePlayer.isUsingItem();
    }

    public static boolean canMove(double motionX, double motionZ, double yOffset) {
        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox().offset(motionX, yOffset, motionZ);
        List<AxisAlignedBB> collisions = mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, box);
        return collisions.isEmpty();
    }
}
