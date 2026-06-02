package keystrokesmod.utility.ravenbs;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.potion.PotionEffect;
import java.util.ArrayList;

public final class ItemInventoryUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final ArrayList<Integer> SPECIAL_POTIONS = new ArrayList<>();

    static {
        SPECIAL_POTIONS.add(1);
        SPECIAL_POTIONS.add(3);
        SPECIAL_POTIONS.add(5);
        SPECIAL_POTIONS.add(6);
        SPECIAL_POTIONS.add(8);
        SPECIAL_POTIONS.add(10);
        SPECIAL_POTIONS.add(11);
        SPECIAL_POTIONS.add(12);
        SPECIAL_POTIONS.add(14);
        SPECIAL_POTIONS.add(21);
        SPECIAL_POTIONS.add(22);
    }

    private ItemInventoryUtil() {
    }

    public static boolean isBlock(ItemStack stack) {
        if (stack == null || stack.stackSize < 1) {
            return false;
        }
        Item item = stack.getItem();
        if (item instanceof ItemBlock) {
            return isContainerBlock((ItemBlock) item);
        }
        return false;
    }

    public static boolean isHoldingBlock() {
        return isBlock(mc.thePlayer.getHeldItem());
    }

    public static boolean isProjectile(ItemStack stack) {
        if (stack == null || stack.stackSize < 1) {
            return false;
        }
        Item item = stack.getItem();
        return item instanceof ItemEgg || item instanceof ItemSnowball;
    }

    public static boolean isNotSpecialItem(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        Item item = stack.getItem();
        if (item instanceof ItemPotion) {
            return ((ItemPotion) item).getEffects(stack).stream()
                    .map(PotionEffect::getPotionID)
                    .noneMatch(SPECIAL_POTIONS::contains);
        }
        if (item instanceof ItemEnderPearl) {
            return false;
        }
        if (item instanceof ItemFood) {
            return item == Items.spider_eye;
        }
        if (item instanceof ItemMonsterPlacer) {
            return false;
        }
        return item != Items.nether_star;
    }

    public static boolean isContainerBlock(ItemBlock itemBlock) {
        Block block = itemBlock.getBlock();
        if (BlockPlacementUtil.isInteractable(block)) {
            return false;
        }
        return BlockPlacementUtil.isSolid(block);
    }

    public static double getAttackBonus(ItemStack stack) {
        if (stack == null) {
            return 0.0;
        }
        double attack = 0.0;
        for (Object attr : stack.getAttributeModifiers().get("generic.attackDamage").toArray()) {
            attack += ((net.minecraft.entity.ai.attributes.AttributeModifier) attr).getAmount();
            break;
        }
        if (stack.isItemEnchanted()) {
            attack += EnchantmentHelper.getEnchantmentLevel(Enchantment.fireAspect.effectId, stack);
            attack += EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, stack) * 1.25;
        }
        return attack;
    }

    public static float getToolEfficiency(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemTool)) {
            return 1.0f;
        }
        float efficiency = ((ItemTool) stack.getItem()).getToolMaterial().getEfficiencyOnProperMaterial();
        if (efficiency > 1.0f) {
            int enchant = EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, stack);
            if (enchant > 0) {
                efficiency += enchant * enchant + 1;
            }
        }
        return efficiency;
    }

    public static double getArmorProtection(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemArmor)) {
            return 0.0;
        }
        double protection = ((ItemArmor) stack.getItem()).damageReduceAmount;
        if (stack.isItemEnchanted()) {
            protection += EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, stack) * 0.8;
        }
        return protection;
    }

    public static double getBowAttackBonus(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemBow)) {
            return 0.0;
        }
        double bonus = 2.0;
        if (stack.isItemEnchanted()) {
            int power = EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, stack);
            if (power > 0) {
                bonus += (power + 1) * 0.25;
            }
            bonus += EnchantmentHelper.getEnchantmentLevel(Enchantment.flame.effectId, stack) * 0.25;
            bonus += EnchantmentHelper.getEnchantmentLevel(Enchantment.infinity.effectId, stack) * 0.05;
        }
        return bonus;
    }

    public static int findSwordInInventorySlot(int startSlot, boolean checkDurability) {
        int bestSlot = -1;
        double best = 0.0;
        if (startSlot < 0) {
            return -1;
        }
        for (int i = 0; i < 36; i++) {
            int slot = (startSlot + i) % 36;
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack == null || !(stack.getItem() instanceof ItemSword)) {
                continue;
            }
            if (checkDurability && stack.isItemDamaged() && stack.getMaxDamage() - stack.getItemDamage() < 30) {
                continue;
            }
            double attack = getAttackBonus(stack);
            if (attack > best) {
                best = attack;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    public static int findBowInventorySlot(int startSlot, boolean checkDurability) {
        int bestSlot = -1;
        double best = 0.0;
        if (startSlot < 0) {
            return -1;
        }
        for (int i = 0; i < 36; i++) {
            int slot = (startSlot + i) % 36;
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack == null || !(stack.getItem() instanceof ItemBow)) {
                continue;
            }
            if (checkDurability && stack.isItemDamaged() && stack.getMaxDamage() - stack.getItemDamage() < 30) {
                continue;
            }
            double bonus = getBowAttackBonus(stack);
            if (bonus > best) {
                best = bonus;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    public static int findInventorySlot(String toolClass, int startSlot, boolean checkDurability) {
        int bestSlot = -1;
        float best = 1.0f;
        if (startSlot < 0) {
            return -1;
        }
        for (int i = 0; i < 36; i++) {
            int slot = (startSlot + i) % 36;
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack == null || !(stack.getItem() instanceof ItemTool)) {
                continue;
            }
            if (!stack.getItem().getToolClasses(stack).contains(toolClass)) {
                continue;
            }
            if (checkDurability && stack.isItemDamaged() && stack.getMaxDamage() - stack.getItemDamage() < 30) {
                continue;
            }
            float eff = getToolEfficiency(stack);
            if (eff > best) {
                best = eff;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    public static int findArmorInventorySlot(int armorType, boolean checkDurability) {
        int bestSlot = -1;
        double best = 0.0;
        for (int i = 0; i < 40; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack == null || !(stack.getItem() instanceof ItemArmor)) {
                continue;
            }
            if (((ItemArmor) stack.getItem()).armorType != armorType) {
                continue;
            }
            if (checkDurability && stack.isItemDamaged() && stack.getMaxDamage() - stack.getItemDamage() < 30) {
                continue;
            }
            double protection = getArmorProtection(stack);
            if (protection >= best) {
                best = protection;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    public static int findInventorySlot(int startSlot, ItemType itemType) {
        int bestSlot = -1;
        int maxStack = 0;
        if (startSlot < 0) {
            startSlot = 0;
        }
        for (int i = 0; i < 36; i++) {
            int slot = (startSlot + i) % 36;
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack == null || !itemType.contains(stack)) {
                continue;
            }
            if (stack.stackSize > maxStack) {
                maxStack = stack.stackSize;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    public static int countInventory(ItemType itemType) {
        int total = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && itemType.contains(stack)) {
                total += stack.stackSize;
            }
        }
        return total;
    }

    public enum ItemType {
        Block {
            @Override
            public boolean contains(ItemStack stack) {
                return ItemInventoryUtil.isBlock(stack);
            }
        },
        Projectile {
            @Override
            public boolean contains(ItemStack stack) {
                return ItemInventoryUtil.isProjectile(stack);
            }
        },
        FishRod {
            @Override
            public boolean contains(ItemStack stack) {
                return stack != null && stack.getItem() instanceof ItemFishingRod;
            }
        },
        GoldApple {
            @Override
            public boolean contains(ItemStack stack) {
                return stack != null && stack.getItem() instanceof ItemAppleGold;
            }
        };

        public abstract boolean contains(ItemStack stack);
    }
}
