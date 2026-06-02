package keystrokesmod.module.impl.player;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.utility.Utils;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Arrays;
import java.util.List;

public class InventoryManager extends Module {
    // --- Delay Ayarları ---
    private final SliderSetting minDelay;
    private final SliderSetting maxDelay;
    private final SliderSetting firstDelay;

    // --- Genel Ayarlar ---
    private final ButtonSetting dropTrash;
    private final ButtonSetting autoArmor;
    private final ButtonSetting armorDurabilityCheck;
    private final ButtonSetting cleanWeapons;
    private final ButtonSetting openInvOnly;

    // --- Slot Ayarları ---
    // 0 = None (atama yapma), 1 = Sword, 2 = Bow, 3 = Pickaxe, 4 = Axe, 5 = Shovel,
    // 6 = Blocks, 7 = Food, 8 = Potion, 9 = Golden Apple
    private final SliderSetting slot1Item;
    private final SliderSetting slot2Item;
    private final SliderSetting slot3Item;
    private final SliderSetting slot4Item;
    private final SliderSetting slot5Item;
    private final SliderSetting slot6Item;
    private final SliderSetting slot7Item;
    private final SliderSetting slot8Item;
    private final SliderSetting slot9Item;

    private long lastActionTime = 0;
    private long firstOpenTime = 0;
    private boolean inInv = false;

    // Item tipi isimleri (GUI'de gösterilmez, iç mantık için)
    private static final String[] ITEM_TYPE_NAMES = {
        "None", "Sword", "Bow", "Pickaxe", "Axe", "Shovel",
        "Blocks", "Food", "Potion", "GApple"
    };

    private final List<String> trashItems = Arrays.asList(
        "tnt", "stick", "egg", "string", "cake", "mushroom", "flint", "compass",
        "dyePowder", "feather", "bucket", "chest", "snow", "fish", "enchant", "exp", "shears",
        "anvil", "torch", "seeds", "leather", "reeds", "skull", "record", "snowball", "piston"
    );

    public InventoryManager() {
        super("InvManager", category.player);
        this.registerSetting(minDelay = new SliderSetting("Min Delay", 80.0, 0.0, 500.0, 5.0));
        this.registerSetting(maxDelay = new SliderSetting("Max Delay", 140.0, 0.0, 500.0, 5.0));
        this.registerSetting(firstDelay = new SliderSetting("First Delay", 250.0, 0.0, 500.0, 10.0));

        this.registerSetting(dropTrash = new ButtonSetting("Drop Trash", true));
        this.registerSetting(autoArmor = new ButtonSetting("Auto Armor", true));
        this.registerSetting(armorDurabilityCheck = new ButtonSetting("Armor Durability", true));
        this.registerSetting(cleanWeapons = new ButtonSetting("Clean Weapons", true));
        this.registerSetting(openInvOnly = new ButtonSetting("Open Inv Only", true));

        // Slot atamaları: 0=None 1=Sword 2=Bow 3=Pick 4=Axe 5=Shovel 6=Blocks 7=Food 8=Potion 9=GApple
        this.registerSetting(new DescriptionSetting("Slot Items (0=None 1=Sword 2=Bow 3=Pick 4=Axe 5=Shovel 6=Block 7=Food 8=Pot 9=Gap)"));
        this.registerSetting(slot1Item = new SliderSetting("Slot 1", 1.0, 0.0, 9.0, 1.0));
        this.registerSetting(slot2Item = new SliderSetting("Slot 2", 0.0, 0.0, 9.0, 1.0));
        this.registerSetting(slot3Item = new SliderSetting("Slot 3", 0.0, 0.0, 9.0, 1.0));
        this.registerSetting(slot4Item = new SliderSetting("Slot 4", 0.0, 0.0, 9.0, 1.0));
        this.registerSetting(slot5Item = new SliderSetting("Slot 5", 0.0, 0.0, 9.0, 1.0));
        this.registerSetting(slot6Item = new SliderSetting("Slot 6", 0.0, 0.0, 9.0, 1.0));
        this.registerSetting(slot7Item = new SliderSetting("Slot 7", 0.0, 0.0, 9.0, 1.0));
        this.registerSetting(slot8Item = new SliderSetting("Slot 8", 0.0, 0.0, 9.0, 1.0));
        this.registerSetting(slot9Item = new SliderSetting("Slot 9", 0.0, 0.0, 9.0, 1.0));
    }

    @Override
    public String getInfo() {
        int s1 = (int) slot1Item.getInput();
        return s1 > 0 && s1 < ITEM_TYPE_NAMES.length ? ITEM_TYPE_NAMES[s1] : "Custom";
    }

    private SliderSetting[] getSlotSettings() {
        return new SliderSetting[]{
            slot1Item, slot2Item, slot3Item, slot4Item, slot5Item,
            slot6Item, slot7Item, slot8Item, slot9Item
        };
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        if (!Utils.nullCheck()) return;
        if (mc.thePlayer.inventoryContainer == null) return;

        boolean isInvOpen = mc.currentScreen instanceof GuiInventory;

        if (openInvOnly.isToggled() && !isInvOpen) {
            inInv = false;
            return;
        }

        if (isInvOpen) {
            if (!inInv) {
                firstOpenTime = System.currentTimeMillis();
                inInv = true;
            }
            if (System.currentTimeMillis() - firstOpenTime < firstDelay.getInput()) {
                return;
            }
        }

        try {
            if (mc.thePlayer.inventoryContainer.getInventory() == null
                    || mc.thePlayer.inventoryContainer.inventorySlots == null
                    || mc.thePlayer.inventoryContainer.inventorySlots.size() < 45) {
                return;
            }
        } catch (Exception ex) {
            return;
        }

        if (mc.currentScreen == null || isInvOpen) {
            long delay = (long) (minDelay.getInput() + Math.random() * (maxDelay.getInput() - minDelay.getInput()));
            if (System.currentTimeMillis() - lastActionTime < delay) return;

            if (autoArmor.isToggled() && equipBestArmor()) {
                lastActionTime = System.currentTimeMillis();
                return;
            }

            if (sortSlotItems()) {
                lastActionTime = System.currentTimeMillis();
                return;
            }

            if (dropTrash.isToggled() && dropTrashItems()) {
                lastActionTime = System.currentTimeMillis();
                return;
            }

            if (cleanWeapons.isToggled() && dropBadWeapons()) {
                lastActionTime = System.currentTimeMillis();
                return;
            }
        }
    }

    // ==================== SLOT SORTING ====================

    /**
     * Her hotbar slotu için atanmış eşya tipini kontrol eder ve en iyisini o slota taşır.
     */
    private boolean sortSlotItems() {
        SliderSetting[] slotSettings = getSlotSettings();

        for (int hotbar = 0; hotbar < 9; hotbar++) {
            int itemType = (int) slotSettings[hotbar].getInput();
            if (itemType == 0) continue; // None — bu slotu atla

            int containerSlot = 36 + hotbar; // hotbar 0 → container 36, ..., hotbar 8 → container 44
            ItemStack currentInSlot = mc.thePlayer.inventoryContainer.getSlot(containerSlot).getHasStack()
                    ? mc.thePlayer.inventoryContainer.getSlot(containerSlot).getStack() : null;

            // Mevcut item zaten doğru tipte mi ve en iyi mi kontrol et
            if (currentInSlot != null && isItemOfType(currentInSlot, itemType)) {
                if (isBestOfType(currentInSlot, itemType)) continue; // Zaten en iyi, geç
            }

            // Tüm envanterde (non-hotbar + hotbar) bu tipteki en iyi itemi bul
            ItemStack bestItem = null;
            int bestSlot = -1;
            float bestScore = -1f;

            for (int i = 9; i < 45; i++) {
                if (i == containerSlot) continue;
                if (!mc.thePlayer.inventoryContainer.getSlot(i).getHasStack()) continue;
                ItemStack is = mc.thePlayer.inventoryContainer.getSlot(i).getStack();
                if (!isItemOfType(is, itemType)) continue;

                float score = getItemScore(is, itemType);
                if (score > bestScore) {
                    bestScore = score;
                    bestItem = is;
                    bestSlot = i;
                }
            }

            if (bestItem == null) continue;

            // Mevcut slottaki item bu tipte ise ve daha kötüyse swap yap
            float currentScore = (currentInSlot != null && isItemOfType(currentInSlot, itemType))
                    ? getItemScore(currentInSlot, itemType) : -1f;

            if (bestScore > currentScore) {
                swapSlots(bestSlot, containerSlot);
                return true;
            }
        }
        return false;
    }

    // ==================== ARMOR (Durability dahil) ====================

    private boolean equipBestArmor() {
        for (int type = 1; type < 5; type++) {
            ItemStack currentArmor = null;
            if (mc.thePlayer.inventoryContainer.getSlot(4 + type).getHasStack()) {
                currentArmor = mc.thePlayer.inventoryContainer.getSlot(4 + type).getStack();
            }

            // Durability check: mevcut zırh çok düşük dayanıklılıktaysa değiştir
            if (armorDurabilityCheck.isToggled() && currentArmor != null) {
                int maxDmg = currentArmor.getMaxDamage();
                int curDmg = currentArmor.getItemDamage();
                float durPercent = maxDmg > 0 ? (float)(maxDmg - curDmg) / maxDmg : 1f;
                // %10'un altında kalan zırhı "yok" say — yenisiyle değiştir
                if (durPercent < 0.10f) {
                    currentArmor = null; // Yeni zırh aramaya zorla
                }
            }

            ItemStack bestCandidate = null;
            int bestCandidateSlot = -1;
            float bestCandidateScore = (currentArmor != null) ? getArmorScore(currentArmor) : -1f;

            for (int i = 9; i < 45; i++) {
                if (!mc.thePlayer.inventoryContainer.getSlot(i).getHasStack()) continue;
                ItemStack is = mc.thePlayer.inventoryContainer.getSlot(i).getStack();
                if (!isArmorOfType(is, type)) continue;

                // Durability check: çok düşük dayanıklılıktaki zırhı aday olarak alma
                if (armorDurabilityCheck.isToggled()) {
                    int maxDmg = is.getMaxDamage();
                    int curDmg = is.getItemDamage();
                    float durPercent = maxDmg > 0 ? (float)(maxDmg - curDmg) / maxDmg : 1f;
                    if (durPercent < 0.05f) continue; // %5'in altındaki zırhı hiç giyme
                }

                float score = getArmorScore(is);
                if (score > bestCandidateScore) {
                    bestCandidateScore = score;
                    bestCandidate = is;
                    bestCandidateSlot = i;
                }
            }

            if (bestCandidate != null) {
                ItemStack equipped = mc.thePlayer.inventoryContainer.getSlot(4 + type).getHasStack()
                        ? mc.thePlayer.inventoryContainer.getSlot(4 + type).getStack() : null;
                if (equipped != null) {
                    drop(4 + type);
                    return true;
                }
                shiftClick(bestCandidateSlot);
                return true;
            }
        }
        return false;
    }

    /**
     * Zırh skoru: damageReduce + protection enchant + durability bonus
     */
    private float getArmorScore(ItemStack is) {
        float score = 0;
        if (is.getItem() instanceof ItemArmor) {
            score += ((ItemArmor) is.getItem()).damageReduceAmount;
        }
        score += EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, is) * 0.04f;

        // Durability bonus: daha dayanıklı zırh tercih edilir
        if (armorDurabilityCheck.isToggled() && is.getMaxDamage() > 0) {
            float durPercent = (float)(is.getMaxDamage() - is.getItemDamage()) / is.getMaxDamage();
            score += durPercent * 0.5f; // Dayanıklılık bonusu (max +0.5)
        }

        return score;
    }

    // ==================== TRASH & WEAPON ====================

    private boolean dropTrashItems() {
        for (int i = 9; i < 45; i++) {
            if (mc.thePlayer.inventoryContainer.getSlot(i).getHasStack()) {
                ItemStack is = mc.thePlayer.inventoryContainer.getSlot(i).getStack();
                if (isTrash(is)) {
                    drop(i);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean dropBadWeapons() {
        for (int i = 9; i < 45; i++) {
            if (mc.thePlayer.inventoryContainer.getSlot(i).getHasStack()) {
                ItemStack is = mc.thePlayer.inventoryContainer.getSlot(i).getStack();
                if (isBadWeapon(is)) {
                    drop(i);
                    return true;
                }
            }
        }
        return false;
    }

    // ==================== ITEM TYPE HELPERS ====================

    /**
     * Item'ın belirli bir tipe ait olup olmadığını kontrol eder.
     * 1=Sword, 2=Bow, 3=Pickaxe, 4=Axe, 5=Shovel, 6=Blocks, 7=Food, 8=Potion, 9=GApple
     */
    private boolean isItemOfType(ItemStack is, int type) {
        Item item = is.getItem();
        switch (type) {
            case 1: return item instanceof ItemSword;
            case 2: return item instanceof ItemBow;
            case 3: return item instanceof ItemPickaxe;
            case 4: return item instanceof ItemAxe;
            case 5: return item instanceof ItemSpade;
            case 6: return item instanceof ItemBlock;
            case 7: return item instanceof ItemFood && !(item instanceof ItemAppleGold);
            case 8: return item instanceof ItemPotion;
            case 9: return item instanceof ItemAppleGold;
            default: return false;
        }
    }

    /**
     * Item tipine göre skor hesaplar — en yüksek skor en iyi item.
     */
    private float getItemScore(ItemStack is, int type) {
        switch (type) {
            case 1: return getSwordScore(is);
            case 2: return getBowScore(is);
            case 3: return getToolScore(is, Enchantment.efficiency);
            case 4: return getToolScore(is, Enchantment.efficiency);
            case 5: return getToolScore(is, Enchantment.efficiency);
            case 6: return is.stackSize; // Daha çok blok = daha iyi
            case 7: return getFoodScore(is);
            case 8: return 1f; // Potionlar eşit
            case 9: return is.stackSize; // Daha çok GApple = daha iyi
            default: return 0f;
        }
    }

    /**
     * Kılıç skoru: base damage + sharpness + fire aspect
     */
    private float getSwordScore(ItemStack is) {
        float score = 0;
        if (is.getItem() instanceof ItemSword) {
            score += ((ItemSword) is.getItem()).getDamageVsEntity();
        }
        score += EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, is) * 1.25f;
        score += EnchantmentHelper.getEnchantmentLevel(Enchantment.fireAspect.effectId, is) * 0.5f;
        score += EnchantmentHelper.getEnchantmentLevel(Enchantment.knockback.effectId, is) * 0.1f;
        return score;
    }

    private float getBowScore(ItemStack is) {
        float score = 0;
        score += EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, is);
        score += EnchantmentHelper.getEnchantmentLevel(Enchantment.punch.effectId, is) * 0.1f;
        score += EnchantmentHelper.getEnchantmentLevel(Enchantment.flame.effectId, is) * 0.01f;
        return score;
    }

    private float getToolScore(ItemStack is, Enchantment ench) {
        float score = 0;
        if (is.getItem() instanceof ItemTool) {
            score += ((ItemTool) is.getItem()).getToolMaterial().getEfficiencyOnProperMaterial();
        }
        score += EnchantmentHelper.getEnchantmentLevel(ench.effectId, is) * 0.5f;
        return score;
    }

    private float getFoodScore(ItemStack is) {
        if (is.getItem() instanceof ItemFood) {
            return ((ItemFood) is.getItem()).getHealAmount(is) + is.stackSize * 0.01f;
        }
        return 0f;
    }

    /**
     * Bu item kendi tipindeki en iyi mi? (tüm envanterde kontrol)
     */
    private boolean isBestOfType(ItemStack is, int type) {
        float myScore = getItemScore(is, type);
        for (int i = 9; i < 45; i++) {
            if (!mc.thePlayer.inventoryContainer.getSlot(i).getHasStack()) continue;
            ItemStack other = mc.thePlayer.inventoryContainer.getSlot(i).getStack();
            if (other == is) continue;
            if (!isItemOfType(other, type)) continue;
            if (getItemScore(other, type) > myScore) return false;
        }
        return true;
    }

    // ==================== GENERAL HELPERS ====================

    private boolean isTrash(ItemStack is) {
        String name = is.getUnlocalizedName().toLowerCase();
        for (String trashItem : trashItems) {
            if (name.contains(trashItem)) return true;
        }
        return false;
    }

    private boolean isBadWeapon(ItemStack is) {
        if (is.getItem() instanceof ItemSword) return !isBestOfType(is, 1);
        if (is.getItem() instanceof ItemBow) return !isBestOfType(is, 2);
        return false;
    }

    /** Bu item bu tip zırh mı? (type: 1=helm, 2=chest, 3=leg, 4=boot) */
    private boolean isArmorOfType(ItemStack is, int type) {
        if (!(is.getItem() instanceof ItemArmor)) return false;
        String name = is.getUnlocalizedName().toLowerCase();
        switch (type) {
            case 1: return name.contains("helmet");
            case 2: return name.contains("chestplate");
            case 3: return name.contains("leggings");
            case 4: return name.contains("boots");
            default: return false;
        }
    }

    private void drop(int slot) {
        mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, slot, 1, 4, mc.thePlayer);
    }

    private void shiftClick(int slot) {
        mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, slot, 0, 1, mc.thePlayer);
    }

    /** İki inventory slotunu swap eder (pick-up + drop). */
    private void swapSlots(int fromSlot, int toSlot) {
        mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, fromSlot, 0, 0, mc.thePlayer);
        mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, toSlot, 0, 0, mc.thePlayer);
        mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, fromSlot, 0, 0, mc.thePlayer);
    }
}
