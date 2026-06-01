package keystrokesmod.module.impl.other;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import net.minecraft.item.ItemStack;

public class SlotHandler extends Module {

    public SlotHandler() {
        super("Slot Handler", category.other);
        this.registerSetting(new DescriptionSetting("Internal slot utility"));
        this.ignoreOnSave = true;
        this.hidden = true;
    }

    public static int getCurrentSlot() {
        if (mc.thePlayer == null) return -1;
        return mc.thePlayer.inventory.currentItem;
    }

    public static void setCurrentSlot(int slot) {
        if (mc.thePlayer == null) return;
        if (slot < 0 || slot > 8) return;
        if (mc.thePlayer.inventory.currentItem == slot) return;
        mc.thePlayer.inventory.currentItem = slot;
    }

    public static ItemStack getHeldItem() {
        if (mc.thePlayer == null) return null;
        return mc.thePlayer.inventory.getStackInSlot(getCurrentSlot());
    }
}
