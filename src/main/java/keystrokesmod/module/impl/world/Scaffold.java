package keystrokesmod.module.impl.world;

import keystrokesmod.event.PreMotionEvent;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.event.SafeWalkEvent;
import keystrokesmod.event.ScaffoldPlaceEvent;
import keystrokesmod.helper.RotationHelper;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.ContainerUtils;
import keystrokesmod.utility.RotationUtils;
import net.minecraft.block.Block;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Scaffold extends Module {
    private static final String[] rotationModes = new String[]{"None", "Snap", "Backwards", "Sideways"};

    private final SliderSetting mode;
    private final SliderSetting rotationSpeed;
    private final SliderSetting sprint;
    private final ButtonSetting autoSwap;
    private final ButtonSetting useBiggestStack;
    private final ButtonSetting safeWalk;
    private final ButtonSetting tower;
    private final ButtonSetting silentSwing;
    private final ButtonSetting showBlockCount;
    private final ButtonSetting esp;
    private final ButtonSetting raytrace;
    private final ButtonSetting expand;
    private final SliderSetting expandDistance;

    public MovingObjectPosition placeBlock;
    public float placeYaw;
    public float placePitch = 85f;
    public MovingObjectPosition rayCasted;
    private int lastSlot = -1;
    private Float lastYaw = null;
    private Float lastPitch = null;
    private final Map<BlockPos, Long> highlight = new HashMap<>();

    public Scaffold() {
        super("Scaffold", category.world);
        this.registerSetting(new DescriptionSetting("Rotation"));
        this.registerSetting(mode = new SliderSetting("Mode", 0, rotationModes));
        this.registerSetting(rotationSpeed = new SliderSetting("Speed", 5.0, 1.0, 10.0, 1.0));
        this.registerSetting(new DescriptionSetting("Sprint"));
        this.registerSetting(sprint = new SliderSetting("Sprint", 1, new String[]{"Off", "Vanilla", "Legit"}));
        this.registerSetting(new DescriptionSetting("Placement"));
        this.registerSetting(autoSwap = new ButtonSetting("Auto swap", true));
        this.registerSetting(useBiggestStack = new ButtonSetting("Use biggest stack", true));
        this.registerSetting(safeWalk = new ButtonSetting("Safe walk", true));
        this.registerSetting(tower = new ButtonSetting("Tower", false));
        this.registerSetting(silentSwing = new ButtonSetting("Silent swing", false));
        this.registerSetting(expand = new ButtonSetting("Expand", false));
        this.registerSetting(expandDistance = new SliderSetting("Expand distance", 4.5, 0, 10, 0.1));
        this.registerSetting(new DescriptionSetting("Render"));
        this.registerSetting(showBlockCount = new ButtonSetting("Show block count", true));
        this.registerSetting(esp = new ButtonSetting("ESP", false));
        this.registerSetting(raytrace = new ButtonSetting("Raytrace", false));
    }

    @Override
    public void onEnable() {
        placeBlock = null;
        rayCasted = null;
        lastSlot = -1;
        lastYaw = null;
        lastPitch = null;
        highlight.clear();
    }

    @Override
    public void onDisable() {
        placeBlock = null;
        rayCasted = null;
        if (lastSlot != -1 && mc.thePlayer != null) {
            mc.thePlayer.inventory.currentItem = lastSlot;
            lastSlot = -1;
        }
        lastYaw = null;
        lastPitch = null;
        highlight.clear();
    }

    @Override
    public String getInfo() {
        return rotationModes[(int) mode.getInput()];
    }

    public static int getSlot() {
        int slot = -1;
        int highestStack = -1;
        for (int i = 0; i < 9; ++i) {
            ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
            if (stack != null && stack.getItem() instanceof ItemBlock
                    && ContainerUtils.canBePlaced((ItemBlock) stack.getItem())
                    && stack.stackSize > 0) {
                if (stack.stackSize > highestStack) {
                    highestStack = stack.stackSize;
                    slot = i;
                }
            }
        }
        return slot;
    }

    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent event) {
        if (mc.thePlayer == null || mc.theWorld == null) return;

        if (lastSlot == -1) {
            lastSlot = mc.thePlayer.inventory.currentItem;
        }

        int slot = lastSlot;
        if (autoSwap.isToggled()) {
            if (useBiggestStack.isToggled()) {
                slot = getSlot();
            } else {
                ItemStack held = mc.thePlayer.inventory.getStackInSlot(slot);
                if (held == null || !(held.getItem() instanceof ItemBlock)
                        || !ContainerUtils.canBePlaced((ItemBlock) held.getItem())) {
                    slot = getSlot();
                }
            }
        }
        if (slot == -1) return;
        mc.thePlayer.inventory.currentItem = slot;

        ItemStack heldItem = mc.thePlayer.inventory.getStackInSlot(slot);
        if (heldItem == null || !(heldItem.getItem() instanceof ItemBlock)
                || !ContainerUtils.canBePlaced((ItemBlock) heldItem.getItem())) return;

        Vec3 target = getPlacePossibility();
        if (target == null) return;
        BlockPos targetPos = new BlockPos(target.xCoord, target.yCoord, target.zCoord);

        rayCasted = null;
        placeYaw = 0f;
        placePitch = 78f;

        float[] targetRotation = RotationUtils.getRotations(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);

        for (float dy = -25f; dy <= 25f && rayCasted == null; dy += 1f) {
            for (float dp = 12f; dp >= -78f && rayCasted == null; dp -= 5f) {
                float checkYaw = targetRotation[0] - dy;
                float checkPitch = MathHelper.clamp_float(targetRotation[1] + dp, -90f, 90f);
                MovingObjectPosition raycast = rayCastCustom(checkYaw, checkPitch);
                if (raycast != null && raycast.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                        && raycast.getBlockPos().equals(targetPos)) {
                    rayCasted = raycast;
                    placeYaw = checkYaw;
                    placePitch = checkPitch;
                }
            }
        }

        if (rayCasted == null) {
            if (expand.isToggled() && !(tower.isToggled() && Keyboard.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode()))) {
                BlockPos groundPos = new BlockPos(mc.thePlayer).down();
                double expDist = expandDistance.getInput();
                Vec3 eyePos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
                for (double j = 0; j < expDist; j += 0.1) {
                    BlockPos expPos = getExtendedPos(groundPos, mc.thePlayer.rotationYaw, j);
                    if (expPos == null || !BlockUtils.isReplaceable(expPos)) continue;
                    MovingObjectPosition mop = getPlaceSide(expPos);
                    if (mop == null) continue;
                    double dist = mop.hitVec.distanceTo(eyePos);
                    if (dist > expandDistance.getInput()) break;
                    rayCasted = mop;
                    placeYaw = getYawTo(mop.hitVec);
                    placePitch = getPitchTo(mop.hitVec);
                    break;
                }
            }
        }

        if (rayCasted == null) return;

        if (tower.isToggled() && mc.thePlayer.onGround && mc.thePlayer.isCollidedHorizontally
                && Keyboard.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode())) {
            mc.thePlayer.jump();
        }

        int rotMode = (int) mode.getInput();
        float speed = (float) rotationSpeed.getInput();

        if (rotMode == 1) {
            applySmoothedRotation(placeYaw, placePitch, speed);
        } else if (rotMode == 2 || rotMode == 3) {
            RotationHelper.get().setYaw(placeYaw);
            RotationHelper.get().setPitch(placePitch);
        }

        placeBlock = rayCasted;
        place(placeBlock);

        if (esp.isToggled()) {
            highlight.put(placeBlock.getBlockPos().offset(placeBlock.sideHit), System.currentTimeMillis());
        }

        handleSprintInPreUpdate();
    }

    @SubscribeEvent
    public void onPreMotion(PreMotionEvent e) {
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (placeBlock == null) return;
        int rotMode = (int) mode.getInput();
        if (rotMode == 2) {
            RotationUtils.setFakeRotations(placeYaw + 180f, placePitch);
        } else if (rotMode == 3) {
            RotationUtils.setFakeRotations(placeYaw + 90f, placePitch);
        }
        handleSprintInPreMotion(e);
    }

    @SubscribeEvent
    public void onSafeWalk(SafeWalkEvent e) {
        if (isEnabled() && safeWalk.isToggled()) e.setSafeWalk(true);
    }

    private void applySmoothedRotation(float targetYaw, float targetPitch, float speed) {
        float baseY = lastYaw != null ? lastYaw : mc.thePlayer.rotationYaw;
        float baseP = lastPitch != null ? lastPitch : mc.thePlayer.rotationPitch;
        float dy = ((targetYaw - baseY) % 360f + 540f) % 360f - 180f;
        float dp = targetPitch - baseP;
        float stepY = Math.abs(dy) * (speed / 10f);
        float stepP = Math.abs(dp) * (speed / 10f);
        if (stepY < 0.1f) stepY = Math.abs(dy);
        if (stepP < 0.1f) stepP = Math.abs(dp);
        float newY = baseY + Math.signum(dy) * Math.min(Math.abs(dy), stepY);
        float newP = MathHelper.clamp_float(baseP + Math.signum(dp) * Math.min(Math.abs(dp), stepP), -90f, 90f);
        RotationHelper.get().setYaw(newY);
        RotationHelper.get().setPitch(newP);
        lastYaw = newY;
        lastPitch = newP;
    }

    private float computeMovementYaw() {
        if (mc.thePlayer == null) return 0f;
        float forward = mc.thePlayer.movementInput.moveForward;
        float strafe = mc.thePlayer.movementInput.moveStrafe;
        if (forward == 0f && strafe == 0f) return mc.thePlayer.rotationYaw;
        float yaw = mc.thePlayer.rotationYaw;
        if (forward < 0f) yaw += 180f;
        if (strafe > 0f) yaw -= (forward > 0f ? 45f : 90f);
        if (strafe < 0f) yaw += (forward > 0f ? 45f : 90f);
        return yaw;
    }

    private boolean shouldOverrideSprint() {
        if (mc.thePlayer == null) return false;
        int s = (int) sprint.getInput();
        if (s == 0) return true;
        if (s == 2 && isActivelyScaffolding()) {
            float moveYaw = computeMovementYaw();
            float lookYaw = mc.thePlayer.rotationYaw;
            float diff = Math.abs(MathHelper.wrapAngleTo180_float(moveYaw - lookYaw));
            return diff > 60f;
        }
        return false;
    }

    private void handleSprintInPreUpdate() {
        if (shouldOverrideSprint()) {
            mc.thePlayer.setSprinting(false);
        }
    }

    private void handleSprintInPreMotion(PreMotionEvent e) {
        if (shouldOverrideSprint()) {
            e.setSprinting(false);
        }
    }

    private void place(MovingObjectPosition block) {
        if (block == null) return;
        ScaffoldPlaceEvent ev = new ScaffoldPlaceEvent(block, false);
        MinecraftForge.EVENT_BUS.post(ev);
        if (ev.isCanceled()) return;
        block = ev.getHitResult();
        if (block == null) return;

        if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld,
                mc.thePlayer.inventory.getCurrentItem(), block.getBlockPos(),
                block.sideHit, block.hitVec)) {
            if (silentSwing.isToggled()) {
                mc.thePlayer.sendQueue.addToSendQueue(new C0APacketAnimation());
            } else {
                mc.thePlayer.swingItem();
            }
        }
    }

    public Vec3 getPlacePossibility() {
        List<Vec3> possibilities = new ArrayList<>();
        int range = 5;
        for (int x = -range; x <= range; ++x) {
            for (int y = -range; y <= range; ++y) {
                for (int z = -range; z <= range; ++z) {
                    BlockPos pos = new BlockPos(mc.thePlayer.posX + x, mc.thePlayer.posY + y, mc.thePlayer.posZ + z);
                    Block block = mc.theWorld.getBlockState(pos).getBlock();
                    if (!block.getMaterial().isReplaceable()) {
                        for (int x2 = -1; x2 <= 1; x2 += 2) {
                            possibilities.add(new Vec3(mc.thePlayer.posX + x + x2, mc.thePlayer.posY + y, mc.thePlayer.posZ + z));
                        }
                        for (int y2 = -1; y2 <= 1; y2 += 2) {
                            possibilities.add(new Vec3(mc.thePlayer.posX + x, mc.thePlayer.posY + y + y2, mc.thePlayer.posZ + z));
                        }
                        for (int z2 = -1; z2 <= 1; z2 += 2) {
                            possibilities.add(new Vec3(mc.thePlayer.posX + x, mc.thePlayer.posY + y, mc.thePlayer.posZ + z + z2));
                        }
                    }
                }
            }
        }

        possibilities.removeIf(vec3 -> mc.thePlayer.getDistance(vec3.xCoord, vec3.yCoord, vec3.zCoord) > 5);
        if (possibilities.isEmpty()) return null;

        possibilities.sort(Comparator.comparingDouble(vec3 -> {
            double d0 = mc.thePlayer.posX - vec3.xCoord;
            double d1 = (mc.thePlayer.posY - 1) - vec3.yCoord;
            double d2 = mc.thePlayer.posZ - vec3.zCoord;
            return MathHelper.sqrt_double(d0 * d0 + d1 * d1 + d2 * d2);
        }));

        return possibilities.get(0);
    }

    private MovingObjectPosition rayCastCustom(float yaw, float pitch) {
        Vec3 eyePos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        float f = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float f1 = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float f2 = -MathHelper.cos(-pitch * 0.017453292F);
        float f3 = MathHelper.sin(-pitch * 0.017453292F);
        Vec3 lookVec = new Vec3(f1 * f2, f3, f * f2);
        Vec3 reachVec = eyePos.addVector(lookVec.xCoord * 4.5, lookVec.yCoord * 4.5, lookVec.zCoord * 4.5);
        return mc.theWorld.rayTraceBlocks(eyePos, reachVec, false, false, true);
    }

    private MovingObjectPosition getPlaceSide(BlockPos pos) {
        for (EnumFacing side : EnumFacing.VALUES) {
            BlockPos neighbor = pos.offset(side);
            if (!BlockUtils.isReplaceable(neighbor)) {
                Vec3 hitVec = new Vec3(neighbor.getX() + 0.5 + side.getFrontOffsetX() * 0.5,
                        neighbor.getY() + 0.5 + side.getFrontOffsetY() * 0.5,
                        neighbor.getZ() + 0.5 + side.getFrontOffsetZ() * 0.5);
                return new MovingObjectPosition(hitVec, side.getOpposite(), neighbor);
            }
        }
        return null;
    }

    private BlockPos getExtendedPos(BlockPos pos, float yaw, double distance) {
        double dx = -MathHelper.sin((float) Math.toRadians(yaw)) * distance;
        double dz = MathHelper.cos((float) Math.toRadians(yaw)) * distance;
        return new BlockPos(pos.getX() + (int) Math.round(dx), pos.getY(), pos.getZ() + (int) Math.round(dz));
    }

    private float getYawTo(Vec3 vec) {
        double dx = vec.xCoord - mc.thePlayer.posX;
        double dz = vec.zCoord - mc.thePlayer.posZ;
        return (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
    }

    private float getPitchTo(Vec3 vec) {
        double dx = vec.xCoord - mc.thePlayer.posX;
        double dy = vec.yCoord - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double dz = vec.zCoord - mc.thePlayer.posZ;
        double dist = MathHelper.sqrt_double(dx * dx + dz * dz);
        return (float) -Math.toDegrees(Math.atan2(dy, dist));
    }

    @SubscribeEvent
    public void onRenderTick(net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent event) {
        if (event.phase != net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (!showBlockCount.isToggled()) return;
        if (mc.currentScreen != null) return;

        ScaledResolution sr = new ScaledResolution(mc);
        int blocks = totalBlocks();
        String color = "§";
        if (blocks <= 5) color += "c";
        else if (blocks <= 15) color += "6";
        else if (blocks <= 25) color += "e";
        else color = "";
        mc.fontRendererObj.drawStringWithShadow(
                color + blocks + " §rblock" + (blocks == 1 ? "" : "s"),
                (float) sr.getScaledWidth() / 2 + 8,
                (float) sr.getScaledHeight() / 2 + 4,
                -1);
    }

    public int totalBlocks() {
        if (mc.thePlayer == null) return 0;
        try {
            int total = 0;
            for (int i = 0; i < 9; ++i) {
                ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
                if (stack != null && stack.getItem() instanceof ItemBlock
                        && ContainerUtils.canBePlaced((ItemBlock) stack.getItem())
                        && stack.stackSize > 0) {
                    total += stack.stackSize;
                }
            }
            return total;
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean isActivelyScaffolding() {
        return isEnabled() && placeBlock != null;
    }

    public boolean isTowering() {
        return isEnabled() && tower.isToggled()
                && mc.thePlayer != null
                && mc.thePlayer.onGround
                && mc.thePlayer.isCollidedHorizontally
                && Keyboard.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode());
    }

    public boolean isDiagonalScaffoldEnabled() {
        if (!isEnabled() || mc.thePlayer == null) return false;
        float forward = mc.thePlayer.movementInput.moveForward;
        float strafe = mc.thePlayer.movementInput.moveStrafe;
        return forward != 0.0f && strafe != 0.0f;
    }

    public static boolean shouldScaffoldSafeWalk() {
        Module scaffold = ModuleManager.getModule(Scaffold.class);
        if (scaffold == null || !scaffold.isEnabled()) return false;
        Scaffold sc = (Scaffold) scaffold;
        if (!sc.safeWalk.isToggled()) return false;
        if (mc.thePlayer == null || !mc.thePlayer.onGround) return false;
        return sc.isActivelyScaffolding();
    }
}
