package keystrokesmod.module.impl.combat;

import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.movement.LongJump;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.Utils;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;

public class Velocity extends Module {
    public static SliderSetting horizontal;
    public static SliderSetting vertical;
    private SliderSetting chance;
    private ButtonSetting onlyWhileTargeting;
    private ButtonSetting disableS;
    private SliderSetting mode;
    private ButtonSetting grimC07Always;
    private ButtonSetting grimC07OnlyBreakAir;
    private SliderSetting grimC07PauseTime;
    public boolean disable;

    // Grim state
    private boolean hasReceivedVelocity;
    private int grimCancelTransactions;
    private int grimUpdates;

    public Velocity() {
        super("Velocity", category.combat, 0);
        this.registerSetting(new DescriptionSetting("Modify knockback taken"));
        this.registerSetting(horizontal = new SliderSetting("Horizontal", "%", 95.0D, 0.0D, 100.0D, 1.0D));
        this.registerSetting(vertical = new SliderSetting("Vertical", "%", 100.0D, 0.0D, 100.0D, 1.0D));
        this.registerSetting(chance = new SliderSetting("Chance", "%", 100.0D, 0.0D, 100.0D, 1.0D));
        this.registerSetting(onlyWhileTargeting = new ButtonSetting("Only while targeting", false));
        this.registerSetting(disableS = new ButtonSetting("Disable while holding S", false));
        this.registerSetting(mode = new SliderSetting("Mode", 0, new String[]{"Normal", "Grim", "GrimC07", "GrimDamage"}));
        this.registerSetting(grimC07Always = new ButtonSetting("GrimC07-Always", true));
        this.registerSetting(grimC07OnlyBreakAir = new ButtonSetting("GrimC07-OnlyBreakAir", true));
        this.registerSetting(grimC07PauseTime = new SliderSetting("GrimC07-PauseTime", 50, 0, 5000, 10));
        this.closetModule = true;
    }

    @Override
    public void onEnable() {
        hasReceivedVelocity = false;
        grimCancelTransactions = 0;
        grimUpdates = 0;
    }

    @Override
    public void onDisable() {
        hasReceivedVelocity = false;
        grimCancelTransactions = 0;
        grimUpdates = 0;
    }

    @Override
    public String getInfo() {
        int modeIdx = (int) mode.getInput();
        String modeName;
        switch (modeIdx) {
            case 1: modeName = "Grim"; break;
            case 2: modeName = "GrimC07"; break;
            case 3: modeName = "GrimDamage"; break;
            default: modeName = "Normal"; break;
        }
        return modeName + " " + (int) horizontal.getInput() + "%" + " " + (int) vertical.getInput() + "%";
    }

    @SubscribeEvent
    public void onReceivePacket(ReceivePacketEvent e) {
        if (!Utils.nullCheck() || LongJump.stopVelocity || disable) {
            return;
        }

        if (e.getPacket() instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity packet = (S12PacketEntityVelocity) e.getPacket();
            if (packet.getEntityID() != mc.thePlayer.getEntityId()) {
                return;
            }

            if (mc.thePlayer.maxHurtTime <= 0 || mc.thePlayer.hurtTime != mc.thePlayer.maxHurtTime) {
                return;
            }

            if (onlyWhileTargeting.isToggled() && (mc.objectMouseOver == null || mc.objectMouseOver.entityHit == null)) {
                return;
            }
            if (disableS.isToggled() && Keyboard.isKeyDown(mc.gameSettings.keyBindBack.getKeyCode())) {
                return;
            }
            if (chance.getInput() == 0) {
                return;
            }
            if (chance.getInput() != 100) {
                double ch = Math.random();
                if (ch >= chance.getInput() / 100.0D) {
                    return;
                }
            }

            int modeIdx = (int) mode.getInput();

            if (modeIdx == 0) {
                // Normal mode - reduce velocity like before
                handleNormalVelocity(e, packet);
            } else if (modeIdx == 1) {
                // Grim mode - cancel packet and desync transactions
                handleGrimVelocity(e, packet);
            } else if (modeIdx == 2) {
                // GrimC07 mode - cancel and send digging packets
                handleGrimC07Velocity(e, packet);
            } else if (modeIdx == 3) {
                // GrimDamage mode - self-attack for natural velocity
                handleGrimDamageVelocity(e, packet);
            }
        }
        else if (e.getPacket() instanceof S27PacketExplosion && !disable) {
            int modeIdx = (int) mode.getInput();
            if (modeIdx == 0) {
                handleNormalExplosion(e, (S27PacketExplosion) e.getPacket());
            } else {
                // For Grim modes, just cancel explosions
                e.setCanceled(true);
            }
        }
        else if (e.getPacket() instanceof S32PacketConfirmTransaction) {
            // Cancel transactions when in Grim mode to desync
            if (grimCancelTransactions > 0) {
                e.setCanceled(true);
                grimCancelTransactions--;
            }
        }
    }

    @SubscribeEvent
    public void onSendPacket(SendPacketEvent e) {
        if (!Utils.nullCheck() || LongJump.stopVelocity || disable) {
            return;
        }
    }

    private void handleNormalVelocity(ReceivePacketEvent e, S12PacketEntityVelocity packet) {
        e.setCanceled(true);
        if (horizontal.getInput() != 100.0D) {
            mc.thePlayer.motionX *= horizontal.getInput() / 100;
            mc.thePlayer.motionZ *= horizontal.getInput() / 100;
        }
        if (vertical.getInput() != 100.0D) {
            mc.thePlayer.motionY *= vertical.getInput() / 100;
        }
    }

    private void handleNormalExplosion(ReceivePacketEvent e, S27PacketExplosion packet) {
        e.setCanceled(true);
        if (horizontal.getInput() != 100.0D) {
            mc.thePlayer.motionX += packet.func_149149_c() * horizontal.getInput() / 100.0;
            mc.thePlayer.motionZ += packet.func_149147_e() * horizontal.getInput() / 100.0;
        }
        if (vertical.getInput() != 100.0D) {
            mc.thePlayer.motionY += packet.func_149144_d() * vertical.getInput() / 100.0;
        }
    }

    private void handleGrimVelocity(ReceivePacketEvent e, S12PacketEntityVelocity packet) {
        e.setCanceled(true);
        hasReceivedVelocity = true;
        grimCancelTransactions = 6;
        grimUpdates = 0;

        // Apply reduced velocity like normal
        if (horizontal.getInput() != 100.0D) {
            mc.thePlayer.motionX *= horizontal.getInput() / 100;
            mc.thePlayer.motionZ *= horizontal.getInput() / 100;
        }
        if (vertical.getInput() != 100.0D) {
            mc.thePlayer.motionY *= vertical.getInput() / 100;
        }
    }

    private void handleGrimC07Velocity(ReceivePacketEvent e, S12PacketEntityVelocity packet) {
        e.setCanceled(true);
        hasReceivedVelocity = true;

        // Send digging packets to create fake ground blocks
        BlockPos pos = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 0.5, mc.thePlayer.posZ);
        mc.thePlayer.sendQueue.addToSendQueue(new C07PacketPlayerDigging(
                Action.STOP_DESTROY_BLOCK,
                pos,
                EnumFacing.UP
        ));

        if (grimC07Always.isToggled()) {
            BlockPos posUp = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY + 1, mc.thePlayer.posZ);
            if (!grimC07OnlyBreakAir.isToggled() || mc.theWorld.isAirBlock(posUp)) {
                mc.thePlayer.sendQueue.addToSendQueue(new C07PacketPlayerDigging(
                        Action.STOP_DESTROY_BLOCK,
                        posUp,
                        EnumFacing.UP
                ));
            }
        }

        // Apply reduced velocity
        if (horizontal.getInput() != 100.0D) {
            mc.thePlayer.motionX *= horizontal.getInput() / 100;
            mc.thePlayer.motionZ *= horizontal.getInput() / 100;
        }
        if (vertical.getInput() != 100.0D) {
            mc.thePlayer.motionY *= vertical.getInput() / 100;
        }
    }

    private void handleGrimDamageVelocity(ReceivePacketEvent e, S12PacketEntityVelocity packet) {
        e.setCanceled(true);

        // Self-attack to apply velocity naturally
        if (mc.thePlayer.hurtTime == 9) {
            // Send attack packets to self
            for (int i = 0; i < 12; i++) {
                mc.thePlayer.sendQueue.addToSendQueue(new net.minecraft.network.play.client.C02PacketUseEntity(
                        mc.thePlayer,
                        net.minecraft.network.play.client.C02PacketUseEntity.Action.ATTACK
                ));
                mc.thePlayer.sendQueue.addToSendQueue(new net.minecraft.network.play.client.C0APacketAnimation());
            }

            // Apply reduced velocity
            double factor = 0.07776;
            mc.thePlayer.motionX *= factor;
            mc.thePlayer.motionZ *= factor;
        } else {
            // Apply normal reduction
            if (horizontal.getInput() != 100.0D) {
                mc.thePlayer.motionX *= horizontal.getInput() / 100;
                mc.thePlayer.motionZ *= horizontal.getInput() / 100;
            }
            if (vertical.getInput() != 100.0D) {
                mc.thePlayer.motionY *= vertical.getInput() / 100;
            }
        }
    }
}
