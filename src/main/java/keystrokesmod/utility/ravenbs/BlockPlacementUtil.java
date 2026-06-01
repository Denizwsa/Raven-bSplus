package keystrokesmod.utility.ravenbs;

import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

public final class BlockPlacementUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private BlockPlacementUtil() {
    }

    public static boolean isReplaceable(BlockPos pos) {
        return BlockUtils.replaceable(pos);
    }

    public static boolean isInteractable(Block block) {
        return BlockUtils.isInteractable(block);
    }

    public static boolean isInteractable(BlockPos pos) {
        return BlockUtils.isInteractable(BlockUtils.getBlock(pos));
    }

    public static boolean isSolid(Block block) {
        return block != null && block.isFullBlock();
    }

    public static Vec3 getClickVec(BlockPos blockPos, EnumFacing facing) {
        return BlockUtils.getFaceCenter(blockPos, facing.getOpposite());
    }

    public static Vec3 getHitVec(BlockPos blockPos, EnumFacing facing, float yaw, float pitch) {
        MovingObjectPosition mop = RotationUtils.rayCastBlock(mc.playerController.getBlockReachDistance(), yaw, pitch);
        if (mop != null && mop.getBlockPos().equals(blockPos) && mop.sideHit == facing) {
            return mop.hitVec;
        }
        return getClickVec(blockPos, facing);
    }
}
