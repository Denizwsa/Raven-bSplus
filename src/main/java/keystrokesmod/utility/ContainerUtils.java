package keystrokesmod.utility;

import net.minecraft.block.*;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;

public class ContainerUtils implements IMinecraftInstance {

    public static boolean canBePlaced(ItemBlock itemBlock) {
        Block block = itemBlock.getBlock();
        if (block == null) return false;
        return !(block instanceof BlockAir) && !BlockUtils.isInteractable(block)
                && block != Blocks.tnt && !(block instanceof BlockSkull)
                && !(block instanceof BlockLiquid) && !(block instanceof BlockCactus)
                && block.isFullBlock();
    }
}
