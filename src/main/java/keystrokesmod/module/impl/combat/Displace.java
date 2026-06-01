package keystrokesmod.module.impl.combat;

import keystrokesmod.Raven;
import keystrokesmod.event.ClientRotationEvent;
import keystrokesmod.event.GameTickEvent;
import keystrokesmod.event.PostPlayerInputEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.helper.RotationHelper;
import keystrokesmod.lag.api.EnumLagDirection;
import keystrokesmod.lag.api.LagRequest;
import keystrokesmod.lag.timeout.ModuleBackedTimeout;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.ItemListSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.CombatTargeting;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Displace extends Module {
    private static final int DISPLACE_WINDOW_TICKS = 10;

    private final SliderSetting yawOffset;
    private final SliderSetting delay;
    private final SliderSetting direction;
    private final ButtonSetting findVoid;
    private final ButtonSetting blink;
    private final ButtonSetting hasKnockback;
    private final ButtonSetting itemWhitelistToggle;
    private final ItemListSetting itemWhitelist;

    private boolean displaceThisTick = false;
    private boolean active = false;
    private boolean hasKB = false;
    private boolean compensateNextTick = false;
    private boolean displaceLeft = false;
    private boolean wasDisplacingLastTick = false;
    private boolean releaseBlinkNextGameTick = false;
    private int tickCounter;
    private final Map<Integer, Integer> targetWindowStartTicks = new HashMap<Integer, Integer>();
    private LagRequest outboundBlink;

    private static final String[] DIRECTIONS = {"Left", "Right"};

    public Displace() {
        super("Displace", category.combat);
        this.registerSetting(yawOffset = new SliderSetting("Yaw offset", 90, 0, 180, 1));
        this.registerSetting(delay = new SliderSetting("Delay", "ms", 0.0D, 0.0D, 500.0D, 50.0D));
        this.registerSetting(direction = new SliderSetting("Direction", 0, DIRECTIONS));
        this.registerSetting(findVoid = new ButtonSetting("Find void", false));
        this.registerSetting(blink = new ButtonSetting("Blink", false));
        this.registerSetting(new DescriptionSetting("Item conditions"));
        this.registerSetting(hasKnockback = new ButtonSetting("Has knockback", false));
        this.registerSetting(itemWhitelistToggle = new ButtonSetting("Item whitelist", false));
        this.registerSetting(itemWhitelist = new ItemListSetting("Whitelisted items"));
    }

    @Override
    public void guiUpdate() {
        itemWhitelist.setVisible(itemWhitelistToggle.isToggled(), this);
    }

    @Override
    public String getInfo() {
        int ms = (int) Math.round(delay.getInput());
        return ms + "ms";
    }

    @Override
    public void onEnable() {
        displaceThisTick = false;
        active = false;
        hasKB = false;
        compensateNextTick = false;
        wasDisplacingLastTick = false;
        releaseBlinkNextGameTick = false;
        tickCounter = 0;
        targetWindowStartTicks.clear();
        releaseBlink();
    }

    @Override
    public void onDisable() {
        active = false;
        compensateNextTick = false;
        wasDisplacingLastTick = false;
        releaseBlinkNextGameTick = false;
        targetWindowStartTicks.clear();
        releaseBlink();
    }

    private static int msToTicks(double ms) {
        if (ms <= 0.0D) {
            return 0;
        }
        return (int) Math.ceil(ms / 50.0D);
    }

    private boolean anyMovementKey() {
        return mc.gameSettings.keyBindForward.isKeyDown()
                || mc.gameSettings.keyBindBack.isKeyDown()
                || mc.gameSettings.keyBindLeft.isKeyDown()
                || mc.gameSettings.keyBindRight.isKeyDown();
    }

    private boolean tryFindVoidDirection(EntityPlayer target) {
        double dx = target.posX - mc.thePlayer.posX;
        double dz = target.posZ - mc.thePlayer.posZ;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.001) return false;

        dx /= dist;
        dz /= dist;

        double rightX = -dz;
        double rightZ = dx;

        double eyeY = target.posY + (double) target.getEyeHeight();

        int leftVoidCount = 0;
        int rightVoidCount = 0;

        for (int i = 1; i <= 12; i++) {
            double off = i * 0.5;

            double rx = target.posX + rightX * off;
            double rz = target.posZ + rightZ * off;
            if (mc.theWorld.rayTraceBlocks(new Vec3(rx, eyeY, rz), new Vec3(rx, eyeY - 10, rz)) == null) {
                rightVoidCount++;
            }

            double lx = target.posX - rightX * off;
            double lz = target.posZ - rightZ * off;
            if (mc.theWorld.rayTraceBlocks(new Vec3(lx, eyeY, lz), new Vec3(lx, eyeY - 10, lz)) == null) {
                leftVoidCount++;
            }
        }

        if (leftVoidCount == 0 && rightVoidCount == 0) return false;

        if (leftVoidCount != rightVoidCount) {
            displaceLeft = leftVoidCount > rightVoidCount;
        }
        return true;
    }

    private void pruneTargetDelayStates() {
        if (mc.theWorld == null) {
            targetWindowStartTicks.clear();
            return;
        }

        Iterator<Map.Entry<Integer, Integer>> iterator = targetWindowStartTicks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Integer> entry = iterator.next();
            Entity entity = mc.theWorld.getEntityByID(entry.getKey());
            if (!(entity instanceof EntityPlayer) || entity.isDead || ((EntityPlayer) entity).deathTime != 0) {
                iterator.remove();
            }
        }
    }

    private boolean shouldDisplaceInCurrentWindow(EntityPlayer target, int currentTick) {
        if (target == null) {
            return true;
        }

        int targetId = target.getEntityId();
        Integer windowStartTick = targetWindowStartTicks.get(targetId);
        if (windowStartTick == null || currentTick - windowStartTick >= DISPLACE_WINDOW_TICKS) {
            targetWindowStartTicks.put(targetId, currentTick);
            return true;
        }

        int delayTicks = msToTicks(delay.getInput());
        if (delayTicks <= 0) {
            return true;
        }

        int elapsed = currentTick - windowStartTick;
        return elapsed >= delayTicks;
    }

    private void releaseBlink() {
        if (outboundBlink != null) {
            outboundBlink.getTimeout().forceTimeOut();
            outboundBlink = null;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onGameTick(GameTickEvent e) {
        if (releaseBlinkNextGameTick) {
            releaseBlink();
            releaseBlinkNextGameTick = false;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPostInput(PostPlayerInputEvent e) {
        if (!active) {
            compensateNextTick = false;
            return;
        }

        if (compensateNextTick && !displaceThisTick) {
            compensateNextTick = false;
            if (displaceLeft) {
                mc.thePlayer.movementInput.moveStrafe = -1;
            } else {
                mc.thePlayer.movementInput.moveStrafe = 1;
            }
            return;
        }

        if (!displaceThisTick || hasKB) return;
        if (!anyMovementKey()) return;

        mc.thePlayer.movementInput.moveForward = 1;
        compensateNextTick = true;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onSendPacket(SendPacketEvent e) {
        if (!blink.isToggled() || !active || !displaceThisTick || releaseBlinkNextGameTick) {
            return;
        }
        if (!(e.getPacket() instanceof C03PacketPlayer)) {
            return;
        }
        if (outboundBlink != null) {
            return;
        }

        outboundBlink = new LagRequest(EnumLagDirection.ONLY_OUTBOUND, new ModuleBackedTimeout(this));
        Raven.lagHandler.requestLag(outboundBlink);
        releaseBlinkNextGameTick = true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onClientRotation(ClientRotationEvent e) {
        if (!Utils.nullCheck()) {
            active = false;
            compensateNextTick = false;
            wasDisplacingLastTick = false;
            return;
        }

        tickCounter++;
        int currentTick = tickCounter;
        pruneTargetDelayStates();

        boolean passesItemCondition = true;
        if (hasKnockback.isToggled() || itemWhitelistToggle.isToggled()) {
            boolean kbPass = !hasKnockback.isToggled() || EnchantmentHelper.getKnockbackModifier(mc.thePlayer) > 0;
            boolean wlPass = !itemWhitelistToggle.isToggled() || itemWhitelist.matches(mc.thePlayer.getHeldItem());
            passesItemCondition = kbPass || wlPass;
        }
        if (!passesItemCondition) {
            active = false;
            displaceThisTick = false;
            compensateNextTick = false;
            wasDisplacingLastTick = false;
            return;
        }

        EntityPlayer target = null;
        boolean attacking = Mouse.isButtonDown(0)
                || (ModuleManager.killAura != null && ModuleManager.killAura.isEnabled() && KillAura.target != null);
        if (attacking) {
            target = CombatTargeting.findClosestTarget(9.0);
        }

        boolean hasKBEnchant = EnchantmentHelper.getKnockbackModifier(mc.thePlayer) > 0;
        active = target != null && (hasKBEnchant || anyMovementKey());
        if (!active) {
            displaceThisTick = false;
            compensateNextTick = false;
            wasDisplacingLastTick = false;
            return;
        }

        if (!findVoid.isToggled() || !tryFindVoidDirection(target)) {
            displaceLeft = direction.getInput() == 0;
        }

        hasKB = hasKBEnchant;
        displaceThisTick = !displaceThisTick;
        if (displaceThisTick && !shouldDisplaceInCurrentWindow(target, currentTick)) {
            displaceThisTick = false;
            compensateNextTick = false;
            wasDisplacingLastTick = false;
            return;
        }

        if (!displaceThisTick && wasDisplacingLastTick) {
            int key = mc.gameSettings.keyBindAttack.getKeyCode();
            if (key != 0) {
                KeyBinding.onTick(key);
            }
        }

        wasDisplacingLastTick = displaceThisTick;

        if (!displaceThisTick) return;

        float baseYaw = RotationUtils.serverRotations[0];
        float offset = (float) yawOffset.getInput();

        if (displaceLeft) {
            baseYaw -= offset;
        } else {
            baseYaw += offset;
        }

        e.yaw = baseYaw;
        RotationHelper.get().forceMovementFix = true;
    }
}
