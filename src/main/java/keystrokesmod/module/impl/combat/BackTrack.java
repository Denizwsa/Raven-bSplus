package keystrokesmod.module.impl.combat;

import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.ravenbs.BooleanProperty;
import keystrokesmod.module.setting.ravenbs.FloatProperty;
import keystrokesmod.module.setting.ravenbs.IntProperty;
import keystrokesmod.module.setting.ravenbs.ModeProperty;
import keystrokesmod.utility.PacketUtils;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.ravenbs.TimerHelper;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.*;
import net.minecraft.network.status.client.C01PacketPing;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

public class BackTrack extends Module {

    public final BooleanProperty legit = new BooleanProperty("Legit", false);
    public final BooleanProperty releaseOnHit = new BooleanProperty("Release on hit", true, () -> legit.getValue());
    public final IntProperty delay = new IntProperty("Delay", 400, 0, 1000);
    public final FloatProperty hitRange = new FloatProperty("Range", 3.0f, 3.0f, 10.0f);
    public final BooleanProperty onlyIfNeeded = new BooleanProperty("Only if needed", true);
    public final BooleanProperty esp = new BooleanProperty("ESP", true);
    public final ModeProperty espMode = new ModeProperty("ESP mode", 0, new String[]{"Hitbox", "None"});

    private final Queue<Packet<?>> incomingPackets = new LinkedList<>();
    private final Queue<Packet<?>> outgoingPackets = new LinkedList<>();
    private final Map<Integer, Vec3> realPositions = new HashMap<>();
    private final TimerHelper timer = new TimerHelper();

    private EntityLivingBase target;
    private Vec3 lastRealPos;

    public BackTrack() {
        super("BackTrack", category.combat);
        legit.register(this);
        releaseOnHit.register(this);
        delay.register(this);
        hitRange.register(this);
        onlyIfNeeded.register(this);
        esp.register(this);
        espMode.register(this);
        this.closetModule = true;
    }

    @Override
    public void onEnable() {
        incomingPackets.clear();
        outgoingPackets.clear();
        realPositions.clear();
        lastRealPos = null;
        timer.reset();
        target = null;
    }

    @Override
    public void onDisable() {
        releaseAll();
        incomingPackets.clear();
        outgoingPackets.clear();
        realPositions.clear();
        lastRealPos = null;
        target = null;
    }

    @SubscribeEvent
    public void onReceivePacket(ReceivePacketEvent event) {
        if (!isEnabled() || !Utils.nullCheck()) {
            return;
        }
        if (ModuleManager.scaffold != null && ModuleManager.scaffold.isEnabled()) {
            releaseAll();
            incomingPackets.clear();
            outgoingPackets.clear();
            return;
        }
        handleIncoming(event);
    }

    @SubscribeEvent
    public void onSendPacket(SendPacketEvent event) {
        if (!isEnabled() || !Utils.nullCheck()) {
            return;
        }
        if (ModuleManager.scaffold != null && ModuleManager.scaffold.isEnabled()) {
            return;
        }
        if (!legit.getValue()) {
            return;
        }
        handleOutgoing(event);
    }

    private void handleIncoming(ReceivePacketEvent event) {
        Packet<?> packet = event.getPacket();

        if (packet instanceof S14PacketEntity) {
            S14PacketEntity p = (S14PacketEntity) packet;
            Entity e = p.getEntity(mc.theWorld);
            if (e == null) {
                return;
            }
            int id = e.getEntityId();
            Vec3 pos = realPositions.getOrDefault(id, new Vec3(0, 0, 0));
            realPositions.put(id, pos.addVector(p.func_149062_c() / 32.0, p.func_149061_d() / 32.0, p.func_149064_e() / 32.0));
        }

        if (packet instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport p = (S18PacketEntityTeleport) packet;
            realPositions.put(p.getEntityId(), new Vec3(p.getX() / 32.0, p.getY() / 32.0, p.getZ() / 32.0));
        }

        if (shouldQueue()) {
            if (blockIncoming(packet)) {
                incomingPackets.add(packet);
                event.setCanceled(true);
            }
        } else {
            releaseIncoming();
        }
    }

    private void handleOutgoing(SendPacketEvent event) {
        Packet<?> packet = event.getPacket();
        if (shouldQueue()) {
            if (blockOutgoing(packet)) {
                outgoingPackets.add(packet);
                event.setCanceled(true);
            }
        } else {
            releaseOutgoing();
        }
    }

    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        if (!isEnabled() || !Utils.nullCheck()) {
            return;
        }

        EntityLivingBase newTarget = getClosestEntity();
        if (newTarget != target) {
            releaseAll();
            lastRealPos = null;
        }
        target = newTarget;

        if (target == null) {
            return;
        }

        Vec3 real = realPositions.get(target.getEntityId());
        if (real == null) {
            return;
        }

        double distReal = mc.thePlayer.getDistance(real.xCoord, real.yCoord, real.zCoord);
        double distCurrent = mc.thePlayer.getDistanceToEntity(target);

        if (mc.thePlayer.maxHurtTime > 0 && mc.thePlayer.hurtTime == mc.thePlayer.maxHurtTime) {
            releaseAll();
        }

        if (distReal > hitRange.getValue() || timer.hasTimePassed(delay.getValue())) {
            releaseAll();
        }

        if (onlyIfNeeded.getValue()) {
            if (distCurrent <= distReal) {
                releaseAll();
            }
            if (lastRealPos != null) {
                double lastDist = mc.thePlayer.getDistance(lastRealPos.xCoord, lastRealPos.yCoord, lastRealPos.zCoord);
                if (distReal < lastDist) {
                    releaseAll();
                }
            }
        }

        if (legit.getValue() && releaseOnHit.getValue() && target.hurtTime == 1) {
            releaseAll();
        }

        lastRealPos = real;
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (!esp.getValue() || espMode.getValue() != 0 || target == null) {
            return;
        }
        Vec3 real = realPositions.get(target.getEntityId());
        if (real == null) {
            return;
        }

        double x = real.xCoord - mc.getRenderManager().viewerPosX;
        double y = real.yCoord - mc.getRenderManager().viewerPosY;
        double z = real.zCoord - mc.getRenderManager().viewerPosZ;

        AxisAlignedBB box = new AxisAlignedBB(
                x - target.width / 2,
                y,
                z - target.width / 2,
                x + target.width / 2,
                y + target.height,
                z + target.width / 2
        );

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        RenderUtils.drawBoundingBox(box, 1f, 0f, 0f, 0.6f);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    private boolean shouldQueue() {
        if (target == null) {
            return false;
        }
        Vec3 real = realPositions.get(target.getEntityId());
        if (real == null) {
            return false;
        }
        if (!onlyIfNeeded.getValue()) {
            double distReal = mc.thePlayer.getDistance(real.xCoord, real.yCoord, real.zCoord);
            double distCurrent = mc.thePlayer.getDistanceToEntity(target);
            return distReal + 0.15 < distCurrent && !timer.hasTimePassed(delay.getValue());
        }
        double distReal = mc.thePlayer.getDistance(real.xCoord, real.yCoord, real.zCoord);
        double distCurrent = mc.thePlayer.getDistanceToEntity(target);
        return distReal < distCurrent;
    }

    private void releaseIncoming() {
        if (mc.getNetHandler() == null) {
            return;
        }
        while (!incomingPackets.isEmpty()) {
            Packet<?> packet = incomingPackets.poll();
            if (packet != null) {
                try {
                    ((Packet<net.minecraft.network.INetHandler>) packet).processPacket(mc.getNetHandler());
                } catch (Exception ex) {
                    PacketUtils.receivePacketNoEvent(packet);
                }
            }
        }
        timer.reset();
    }

    private void releaseOutgoing() {
        while (!outgoingPackets.isEmpty()) {
            Packet<?> packet = outgoingPackets.poll();
            if (packet != null) {
                PacketUtils.sendPacketNoEvent(packet);
            }
        }
        timer.reset();
    }

    private void releaseAll() {
        releaseIncoming();
        releaseOutgoing();
    }

    private boolean blockIncoming(Packet<?> p) {
        if (!onlyIfNeeded.getValue()) {
            if (p instanceof S12PacketEntityVelocity || p instanceof S27PacketExplosion) {
                return false;
            }
            return p instanceof S14PacketEntity
                    || p instanceof S18PacketEntityTeleport
                    || p instanceof S19PacketEntityHeadLook
                    || p instanceof S0FPacketSpawnMob;
        }
        return p instanceof S12PacketEntityVelocity
                || p instanceof S27PacketExplosion
                || p instanceof S14PacketEntity
                || p instanceof S18PacketEntityTeleport
                || p instanceof S19PacketEntityHeadLook
                || p instanceof S0FPacketSpawnMob;
    }

    private boolean blockOutgoing(Packet<?> p) {
        return p instanceof C03PacketPlayer
                || p instanceof C02PacketUseEntity
                || p instanceof C0APacketAnimation
                || p instanceof C0BPacketEntityAction
                || p instanceof C08PacketPlayerBlockPlacement
                || p instanceof C07PacketPlayerDigging
                || p instanceof C09PacketHeldItemChange
                || p instanceof C00PacketKeepAlive
                || p instanceof C01PacketPing;
    }

    private EntityLivingBase getClosestEntity() {
        EntityLivingBase closest = null;
        double closestDist = Double.MAX_VALUE;
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity instanceof EntityLivingBase && entity != mc.thePlayer) {
                double dist = mc.thePlayer.getDistanceToEntity(entity);
                if (dist < closestDist && dist <= hitRange.getValue()) {
                    closestDist = dist;
                    closest = (EntityLivingBase) entity;
                }
            }
        }
        return closest;
    }
}
