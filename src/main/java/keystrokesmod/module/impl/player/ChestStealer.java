package keystrokesmod.module.impl.player;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.Utils;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.ContainerChest;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChestStealer extends Module {
    private SliderSetting minDelay;
    private SliderSetting maxDelay;
    private SliderSetting firstDelay;
    private ButtonSetting autoClose;
    private ButtonSetting randomizeSlots;
    private ButtonSetting checkName;

    private long lastClickTime = 0;
    private long firstOpenTime = 0;
    private boolean inChest = false;

    public ChestStealer() {
        super("ChestStealer", category.player);
        this.registerSetting(minDelay = new SliderSetting("Min Delay", 80.0, 0.0, 300.0, 5.0));
        this.registerSetting(maxDelay = new SliderSetting("Max Delay", 130.0, 0.0, 300.0, 5.0));
        this.registerSetting(firstDelay = new SliderSetting("First Delay", 250.0, 0.0, 500.0, 10.0));
        this.registerSetting(autoClose = new ButtonSetting("Auto Close", true));
        this.registerSetting(randomizeSlots = new ButtonSetting("Randomize Slots", true));
        this.registerSetting(checkName = new ButtonSetting("Check Name (Hypixel)", true));
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        if (!Utils.nullCheck()) return;

        if (mc.currentScreen instanceof GuiChest) {
            if (!inChest) {
                firstOpenTime = System.currentTimeMillis();
                inChest = true;
            }

            if (System.currentTimeMillis() - firstOpenTime < firstDelay.getInput()) {
                return; // Hypixel "insan mısın" check'i için ilk açılışta bekle
            }

            GuiChest guiChest = (GuiChest) mc.currentScreen;
            ContainerChest containerChest = (ContainerChest) mc.thePlayer.openContainer;
            String name = containerChest.getLowerChestInventory().getName();
            
            // Hypixel'de menülerden eşya çalmayı engellemek için isim kontrolü
            if (checkName.isToggled()) {
                String lowerName = name.toLowerCase();
                if (!lowerName.equals("chest") && !lowerName.equals("large chest") && !lowerName.equals("low") && !lowerName.contains("loot")) {
                    return;
                }
            }

            List<Integer> slots = new ArrayList<>();
            int inventoryRows = containerChest.getLowerChestInventory().getSizeInventory() / 9;
            for (int i = 0; i < inventoryRows * 9; i++) {
                if (containerChest.getSlot(i).getHasStack()) {
                    slots.add(i);
                }
            }

            if (slots.isEmpty()) {
                if (autoClose.isToggled()) {
                    long delay = (long) (minDelay.getInput() + Math.random() * (maxDelay.getInput() - minDelay.getInput()));
                    if (System.currentTimeMillis() - lastClickTime >= delay) {
                        mc.thePlayer.closeScreen();
                    }
                }
                return;
            }

            if (randomizeSlots.isToggled()) {
                Collections.shuffle(slots);
            }

            long delay = (long) (minDelay.getInput() + Math.random() * (maxDelay.getInput() - minDelay.getInput()));
            if (System.currentTimeMillis() - lastClickTime >= delay) {
                int slot = slots.get(0);
                mc.playerController.windowClick(containerChest.windowId, slot, 0, 1, mc.thePlayer);
                lastClickTime = System.currentTimeMillis();
            }
        } else {
            inChest = false;
        }
    }
}
