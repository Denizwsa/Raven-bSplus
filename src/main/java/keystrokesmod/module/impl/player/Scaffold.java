package keystrokesmod.module.impl.player;

import keystrokesmod.event.*;
import keystrokesmod.helper.RotationHelper;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.*;
import keystrokesmod.utility.ravenbs.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.potion.Potion;
import net.minecraft.util.*;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.*;

/**
 * Scaffold module - places blocks under player automatically
 * Features: rotation modes, tower, keep-y, multi-place, block counter, safe-walk
 * Based on Raven bS+ with optimizations from Neo-Raven-XD
 */
public class Scaffold extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    
    // Settings - Rotation modes
    private SliderSetting rotationMode;
    private SliderSetting fakeRotationMode;
    
    // Settings - Sprint/Motion modes
    private SliderSetting sprintMode;
    private SliderSetting motion;
    
    // Settings - Tower
    private SliderSetting tower;
    private ButtonSetting hyperTower;
    
    // Settings - Keep Y
    private SliderSetting keepY;
    private ButtonSetting keepYOnPress;
    private ButtonSetting disableWhileJumpActive;
    
    // Settings - Placement
    private SliderSetting multiPlace;
    private ButtonSetting autoSwap;
    private ButtonSetting silentSwing;
    
    // Settings - Utility
    private ButtonSetting safeWalk;
    private ButtonSetting blockCounter;
    private SliderSetting moveFix;
    
    // Rotation state
    private float pendingYaw;
    private float pendingPitch;
    private float pendingRenderYaw;
    private boolean applyRotations;
    
    // Placement state
    private int lastSlot = -1;
    private int blockCount = -1;
    private int rotationTick = 0;
    private boolean canRotate = false;
    
    // Tower state
    private int towerTick = 0;
    private int towerDelay = 0;
    private int stage = 0;
    private int startY = 256;
    private boolean shouldKeepY = false;
    private boolean towering = false;
    private EnumFacing targetFacing = null;
    
    // Rotation state
    private float yaw = -180.0F;
    private float pitch = 0.0F;

    private static final double[] PLACE_OFFSETS = new double[]{
            0.03125, 0.09375, 0.15625, 0.21875, 0.28125, 0.34375, 0.40625, 0.46875,
            0.53125, 0.59375, 0.65625, 0.71875, 0.78125, 0.84375, 0.90625, 0.96875
    };

    public Scaffold() {
        super("Scaffold", category.player, Keyboard.KEY_S);
        this.closetModule = true;
        registerSettings();
    }

    private void registerSettings() {
        // Rotation modes
        this.rotationMode = new SliderSetting("Rotation", 2, 
                new String[]{"NONE", "DEFAULT", "BACKWARDS", "SIDEWAYS", "GODBRIDGE", "SMOOTH"});
        this.registerSetting(rotationMode);
        
        this.fakeRotationMode = new SliderSetting("Fake Rotation", 0,
                new String[]{"NONE", "None", "Strict", "Smooth", "Spin", "Precise"});
        this.registerSetting(fakeRotationMode);
        
        // Sprint and motion
        this.sprintMode = new SliderSetting("Sprint", 0,
                new String[]{"NONE", "VANILLA", "FLOAT"});
        this.registerSetting(sprintMode);
        
        this.motion = new SliderSetting("Motion", "%", 100, 50, 150, 1);
        this.registerSetting(motion);
        
        // Tower modes
        this.tower = new SliderSetting("Tower", 0,
                new String[]{"NONE", "VANILLA", "EXTRA", "TELLY"});
        this.registerSetting(tower);
        
        this.hyperTower = new ButtonSetting("Hypixel Tower", false);
        this.registerSetting(hyperTower);
        
        // Keep Y
        this.keepY = new SliderSetting("Keep-Y", 0,
                new String[]{"NONE", "VANILLA", "EXTRA", "TELLY"});
        this.registerSetting(keepY);
        
        this.keepYOnPress = new ButtonSetting("Keep-Y on Press", false);
        this.registerSetting(keepYOnPress);
        
        this.disableWhileJumpActive = new ButtonSetting("No Keep-Y on Jump Potion", false);
        this.registerSetting(disableWhileJumpActive);
        
        // Placement
        this.multiPlace = new SliderSetting("Multi-Place", 0, 0, 4, 1);
        this.registerSetting(multiPlace);
        
        this.autoSwap = new ButtonSetting("Auto Swap", true);
        this.registerSetting(autoSwap);
        
        this.silentSwing = new ButtonSetting("Silent Swing", true);
        this.registerSetting(silentSwing);
        
        // Utility
        this.safeWalk = new ButtonSetting("Safe Walk", true);
        this.registerSetting(safeWalk);
        
        this.blockCounter = new ButtonSetting("Block Counter", true);
        this.registerSetting(blockCounter);
        
        this.moveFix = new SliderSetting("Move Fix", 0,
                new String[]{"NONE", "SILENT"});
        this.registerSetting(moveFix);
    }

    @Override
    public String getInfo() {
        int mode = (int) rotationMode.getInput();
        String[] rotationModes = {"NONE", "DEFAULT", "BACKWARDS", "SIDEWAYS", "GODBRIDGE", "SMOOTH"};
        if (mode >= 0 && mode < rotationModes.length) {
            return rotationModes[mode];
        }
        return "NONE";
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onClientRotation(ClientRotationEvent e) {
        if (!applyRotations) return;
        e.setYaw(pendingYaw);
        e.setPitch(pendingPitch);
        RotationHelper.get().forceMovementFix = moveFix.getInput() == 1;
        applyRotations = false;
    }

    public boolean isActivelyScaffolding() {
        return isEnabled() && canRotate;
    }

    public boolean isTowering() {
        return towering;
    }

    private BlockData getBlockData() {
        int startY = MathHelper.floor_double(mc.thePlayer.posY);
        BlockPos targetPos = new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                (this.stage != 0 && !this.shouldKeepY ? Math.min(startY, this.startY) : startY) - 1,
                MathHelper.floor_double(mc.thePlayer.posZ)
        );
        
        if (!BlockPlacementUtil.isReplaceable(targetPos)) {
            return null;
        }
        
        ArrayList<BlockPos> positions = new ArrayList<>();
        for (int x = -4; x <= 4; x++) {
            for (int y = -4; y <= 0; y++) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos pos = targetPos.add(x, y, z);
                    if (!BlockPlacementUtil.isReplaceable(pos)
                            && !BlockPlacementUtil.isInteractable(pos)
                            && !(mc.thePlayer.getDistance((double) pos.getX() + 0.5, (double) pos.getY() + 0.5, (double) pos.getZ() + 0.5)
                                    > (double) mc.playerController.getBlockReachDistance())
                            && (this.stage == 0 || this.shouldKeepY || pos.getY() < this.startY)) {
                        for (EnumFacing facing : EnumFacing.VALUES) {
                            if (facing != EnumFacing.DOWN) {
                                BlockPos blockPos = pos.offset(facing);
                                if (BlockPlacementUtil.isReplaceable(blockPos)) {
                                    positions.add(pos);
                                }
                            }
                        }
                    }
                }
            }
        }
        
        if (positions.isEmpty()) {
            return null;
        }
        
        positions.sort(Comparator.comparingDouble(o ->
                o.distanceSqToCenter((double) targetPos.getX() + 0.5, (double) targetPos.getY() + 0.5, (double) targetPos.getZ() + 0.5)));
        
        BlockPos blockPos = positions.get(0);
        EnumFacing facing = getBestFacing(blockPos, targetPos);
        return facing == null ? null : new BlockData(blockPos, facing);
    }

    private EnumFacing getBestFacing(BlockPos blockPos1, BlockPos blockPos3) {
        double offset = 0.0;
        EnumFacing enumFacing = null;
        for (EnumFacing facing : EnumFacing.VALUES) {
            if (facing != EnumFacing.DOWN) {
                BlockPos pos = blockPos1.offset(facing);
                if (pos.getY() <= blockPos3.getY()) {
                    double distance = pos.distanceSqToCenter((double) blockPos3.getX() + 0.5, (double) blockPos3.getY() + 0.5, (double) blockPos3.getZ() + 0.5);
                    if (enumFacing == null || distance < offset || distance == offset && facing == EnumFacing.UP) {
                        offset = distance;
                        enumFacing = facing;
                    }
                }
            }
        }
        return enumFacing;
    }

    private void place(BlockPos blockPos, EnumFacing enumFacing, Vec3 vec3) {
        if (ItemInventoryUtil.isHoldingBlock() && this.blockCount > 0) {
            if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem(), blockPos, enumFacing, vec3)) {
                if (mc.playerController.getCurrentGameType() != net.minecraft.world.WorldSettings.GameType.CREATIVE) {
                    this.blockCount--;
                }
                if (this.silentSwing.isToggled()) {
                    PacketHelper.sendPacket(new C0APacketAnimation());
                } else {
                    mc.thePlayer.swingItem();
                }
            }
        }
    }

    private EnumFacing yawToFacing(float yaw) {
        if (yaw < -135.0F || yaw > 135.0F) {
            return EnumFacing.NORTH;
        } else if (yaw < -45.0F) {
            return EnumFacing.EAST;
        } else {
            return yaw < 45.0F ? EnumFacing.SOUTH : EnumFacing.WEST;
        }
    }

    private double distanceToEdge(EnumFacing enumFacing) {
        switch (enumFacing) {
            case NORTH:
                return mc.thePlayer.posZ - Math.floor(mc.thePlayer.posZ);
            case EAST:
                return Math.ceil(mc.thePlayer.posX) - mc.thePlayer.posX;
            case SOUTH:
                return Math.ceil(mc.thePlayer.posZ) - mc.thePlayer.posZ;
            case WEST:
            default:
                return mc.thePlayer.posX - Math.floor(mc.thePlayer.posX);
        }
    }

    private float getSpeed() {
        if (!mc.thePlayer.onGround) {
            return (float) this.motion.getInput() / 100.0F;
        } else {
            return MovementUtil.getSpeedLevel() > 0
                    ? (float) this.motion.getInput() / 100.0F
                    : (float) this.motion.getInput() / 100.0F;
        }
    }

    private double getRandomOffset() {
        return 0.2155 - RandomHelper.nextDouble(1.0E-4, 9.0E-4);
    }

    private float getCurrentYaw() {
        return MovementUtil.adjustYaw(
                mc.thePlayer.rotationYaw, (float) MovementUtil.getForwardValue(), (float) MovementUtil.getLeftValue()
        );
    }

    private boolean isDiagonal(float yaw) {
        float absYaw = Math.abs(yaw % 90.0F);
        return absYaw > 20.0F && absYaw < 70.0F;
    }

    private boolean isToweringMode() {
        if (mc.thePlayer.onGround && MovementUtil.isForwardPressed() && !PlayerMovementUtil.isAirAbove()) {
            boolean keepY = this.keepY.getInput() == 3;
            boolean towerMode = this.tower.getInput() == 3;
            return keepY && this.stage > 0 || towerMode && mc.gameSettings.keyBindJump.isKeyDown();
        }
        return false;
    }

    private boolean shouldStopSprint() {
        if (this.isToweringMode()) {
            return false;
        } else {
            boolean stage = this.keepY.getInput() == 1 || this.keepY.getInput() == 2;
            return (!stage || this.stage <= 0) && this.sprintMode.getInput() == 0;
        }
    }

    private boolean canPlace() {
        if (ModuleManager.bedAura != null && ModuleManager.bedAura.isEnabled() && ModuleManager.bedAura.shouldOverrideMouseOver()) {
            return false;
        }
        return ModuleManager.longJump == null || !ModuleManager.longJump.isEnabled();
    }

    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent event) {
        onLivingUpdate();
        if (this.isEnabled()) {
            if (this.rotationTick > 0) {
                this.rotationTick--;
            }
            
            // Hypixel tower logic
             if (hyperTower.isToggled() && mc.thePlayer.motionY <= 0.0 &&
                    Math.sqrt(mc.thePlayer.motionX * mc.thePlayer.motionX + mc.thePlayer.motionZ * mc.thePlayer.motionZ) <= 0.02D && 
                    mc.thePlayer.motionY >= -0.09 && !isMoving() && Keyboard.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode())) {
                mc.thePlayer.motionY = -0.38;
            }
            
            if (mc.thePlayer.onGround) {
                if (this.stage > 0) {
                    this.stage--;
                }
                if (this.stage < 0) {
                    this.stage++;
                }
                if (this.stage == 0 && this.keepY.getInput() != 0 &&
                        (!this.keepYOnPress.isToggled() || PlayerMovementUtil.isUsingItem()) &&
                        (!this.disableWhileJumpActive.isToggled() || !mc.thePlayer.isPotionActive(Potion.jump)) &&
                        !mc.gameSettings.keyBindJump.isKeyDown()) {
                    this.stage = 1;
                }
                this.startY = this.shouldKeepY ? this.startY : MathHelper.floor_double(mc.thePlayer.posY);
                this.shouldKeepY = false;
                this.towering = false;
            }
            
            if (this.canPlace()) {
                ItemStack stack = mc.thePlayer.getHeldItem();
                int count = ItemInventoryUtil.isBlock(stack) ? stack.stackSize : 0;
                this.blockCount = Math.min(this.blockCount, count);
                
                if (this.blockCount <= 0) {
                    int slot = mc.thePlayer.inventory.currentItem;
                    if (this.blockCount == 0) {
                        slot--;
                    }
                    for (int i = slot; i > slot - 9; i--) {
                        int hotbarSlot = (i % 9 + 9) % 9;
                        ItemStack candidate = mc.thePlayer.inventory.getStackInSlot(hotbarSlot);
                        if (ItemInventoryUtil.isBlock(candidate)) {
                            mc.thePlayer.inventory.currentItem = hotbarSlot;
                            this.blockCount = candidate.stackSize;
                            break;
                        }
                    }
                }
                
                float currentYaw = this.getCurrentYaw();
                float yawDiffTo180 = RotationCompat.wrapAngleDiff(currentYaw - 180.0F, RotationUtils.serverRotations[0]);
                float diagonalYaw = this.isDiagonal(currentYaw)
                        ? yawDiffTo180
                        : RotationCompat.wrapAngleDiff(currentYaw - 135.0F * ((currentYaw + 180.0F) % 90.0F < 45.0F ? 1.0F : -1.0F), RotationUtils.serverRotations[0]);
                
                if (!this.canRotate) {
                    switch ((int) this.rotationMode.getInput()) {
                        case 1:
                            if (this.yaw == -180.0F && this.pitch == 0.0F) {
                                this.yaw = RotationCompat.quantizeAngle(diagonalYaw);
                                this.pitch = RotationCompat.quantizeAngle(85.0F);
                            } else {
                                this.yaw = RotationCompat.quantizeAngle(diagonalYaw);
                            }
                            break;
                        case 2:
                            if (this.yaw == -180.0F && this.pitch == 0.0F) {
                                this.yaw = RotationCompat.quantizeAngle(yawDiffTo180);
                                this.pitch = RotationCompat.quantizeAngle(85.0F);
                            } else {
                                this.yaw = RotationCompat.quantizeAngle(yawDiffTo180);
                            }
                            break;
                        case 3:
                            if (this.yaw == -180.0F && this.pitch == 0.0F) {
                                this.yaw = RotationCompat.quantizeAngle(diagonalYaw);
                                this.pitch = RotationCompat.quantizeAngle(85.0F);
                            } else {
                                this.yaw = RotationCompat.quantizeAngle(diagonalYaw);
                            }
                            break;
                        case 4: // God Bridge
                            float roundedYaw = Math.round(currentYaw / 45.0f) * 45.0f;
                            this.yaw = RotationCompat.quantizeAngle(roundedYaw);
                            if (this.pitch == 0.0F || !this.canRotate) {
                                float godBridgePitch = 79.3f;
                                this.pitch = RotationCompat.quantizeAngle(godBridgePitch);
                            }
                            break;
                        case 5: // Smooth
                            if (this.yaw == -180.0F && this.pitch == 0.0F) {
                                this.yaw = RotationCompat.quantizeAngle(diagonalYaw);
                                this.pitch = RotationCompat.quantizeAngle(85.0F);
                            } else {
                                float targetYaw = this.isDiagonal(currentYaw) ? diagonalYaw : yawDiffTo180;
                                float yawDiff = RotationCompat.wrapAngleDiff(targetYaw - this.yaw, RotationUtils.serverRotations[0]);
                                if (Math.abs(yawDiff) < 30) {
                                    this.yaw = targetYaw;
                                }
                            }
                            break;
                    }
                }
                
                BlockData blockData = this.getBlockData();
                Vec3 hitVec = null;
                
                if (blockData != null) {
                    double[] x = PLACE_OFFSETS;
                    double[] y = PLACE_OFFSETS;
                    double[] z = PLACE_OFFSETS;
                    
                    switch (blockData.facing()) {
                        case NORTH:
                            z = new double[]{0.0};
                            break;
                        case EAST:
                            x = new double[]{1.0};
                            break;
                        case SOUTH:
                            z = new double[]{1.0};
                            break;
                        case WEST:
                            x = new double[]{0.0};
                            break;
                        case DOWN:
                            y = new double[]{0.0};
                            break;
                        case UP:
                            y = new double[]{1.0};
                    }
                    
                    float bestYaw = -180.0F;
                    float bestPitch = 0.0F;
                    float bestDiff = 0.0F;
                    
                    for (double dx : x) {
                        for (double dy : y) {
                            for (double dz : z) {
                                double relX = (double) blockData.blockPos().getX() + dx - mc.thePlayer.posX;
                                double relY = (double) blockData.blockPos().getY() + dy - mc.thePlayer.posY - (double) mc.thePlayer.getEyeHeight();
                                double relZ = (double) blockData.blockPos().getZ() + dz - mc.thePlayer.posZ;
                                float baseYaw = RotationCompat.wrapAngleDiff(this.yaw, RotationUtils.serverRotations[0]);
                                float[] rotations = RotationCompat.getRotationsTo(relX, relY, relZ, baseYaw, this.pitch);
                                MovingObjectPosition mop = RotationCompat.rayTrace(rotations[0], rotations[1], mc.playerController.getBlockReachDistance(), 1.0F);
                                
                                if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK &&
                                        mop.getBlockPos().equals(blockData.blockPos()) && mop.sideHit == blockData.facing()) {
                                    float totalDiff = Math.abs(rotations[0] - baseYaw) + Math.abs(rotations[1] - this.pitch);
                                    if (bestYaw == -180.0F && bestPitch == 0.0F || totalDiff < bestDiff) {
                                        bestYaw = rotations[0];
                                        bestPitch = rotations[1];
                                        bestDiff = totalDiff;
                                        hitVec = mop.hitVec;
                                    }
                                }
                            }
                        }
                    }
                    
                    if (bestYaw != -180.0F || bestPitch != 0.0F) {
                        this.yaw = bestYaw;
                        this.pitch = bestPitch;
                        this.canRotate = true;
                    }
                }
                
                if (this.canRotate && MovementUtil.isForwardPressed() &&
                        Math.abs(MathHelper.wrapAngleTo180_float(yawDiffTo180 - this.yaw)) < 90.0F) {
                    switch ((int) this.rotationMode.getInput()) {
                        case 2:
                            this.yaw = RotationCompat.quantizeAngle(yawDiffTo180);
                            break;
                        case 3:
                            this.yaw = RotationCompat.quantizeAngle(diagonalYaw);
                    }
                }
                
                if (this.rotationMode.getInput() != 0) {
                    float targetYaw = this.yaw;
                    float targetPitch = this.pitch;
                    
                    if (this.towering && (mc.thePlayer.motionY > 0.0 || mc.thePlayer.posY > (double) (this.startY + 1))) {
                        float yawDiff = MathHelper.wrapAngleTo180_float(this.yaw - RotationUtils.serverRotations[0]);
                        if (Math.abs(yawDiff) > 45) {
                            float clampedYaw = RotationCompat.clampAngle(yawDiff, 45);
                            targetYaw = RotationCompat.quantizeAngle(RotationUtils.serverRotations[0] + clampedYaw);
                            this.rotationTick = Math.max(this.rotationTick, 1);
                        }
                    }
                    
                    if (this.isToweringMode()) {
                        float yawDelta = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - RotationUtils.serverRotations[0]);
                        targetYaw = RotationCompat.quantizeAngle(RotationUtils.serverRotations[0] + yawDelta * RandomHelper.nextFloat(0.98F, 0.99F));
                        targetPitch = RotationCompat.quantizeAngle(RandomHelper.nextFloat(30.0F, 80.0F));
                        this.rotationTick = 3;
                        this.towering = true;
                    }
                    
                    pendingYaw = targetYaw;
                    pendingPitch = targetPitch;
                    applyRotations = true;
                    
                    if (this.moveFix.getInput() == 1) {
                        pendingRenderYaw = targetYaw;
                    }
                }
                
                if (blockData != null && hitVec != null && this.rotationTick <= 0) {
                    this.place(blockData.blockPos(), blockData.facing(), hitVec);
                    
                    if (this.multiPlace.getInput() > 0) {
                        for (int i = 0; i < (int) this.multiPlace.getInput(); i++) {
                            blockData = this.getBlockData();
                            if (blockData == null) break;
                            
                            MovingObjectPosition mop = RotationCompat.rayTrace(this.yaw, this.pitch, mc.playerController.getBlockReachDistance(), 1.0F);
                            if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK &&
                                    mop.getBlockPos().equals(blockData.blockPos()) && mop.sideHit == blockData.facing()) {
                                this.place(blockData.blockPos(), blockData.facing(), mop.hitVec);
                            } else {
                                break;
                            }
                        }
                    }
                }
                
                if (this.targetFacing != null) {
                    if (this.rotationTick <= 0) {
                        int playerBlockX = MathHelper.floor_double(mc.thePlayer.posX);
                        int playerBlockY = MathHelper.floor_double(mc.thePlayer.posY);
                        int playerBlockZ = MathHelper.floor_double(mc.thePlayer.posZ);
                        BlockPos belowPlayer = new BlockPos(playerBlockX, playerBlockY - 1, playerBlockZ);
                        hitVec = BlockPlacementUtil.getHitVec(belowPlayer, this.targetFacing, this.yaw, this.pitch);
                        this.place(belowPlayer, this.targetFacing, hitVec);
                    }
                    this.targetFacing = null;
                } else if (this.keepY.getInput() == 2 && this.stage > 0 && !mc.thePlayer.onGround) {
                    int nextBlockY = MathHelper.floor_double(mc.thePlayer.posY + mc.thePlayer.motionY);
                    if (nextBlockY <= this.startY && mc.thePlayer.posY > (double) (this.startY + 1)) {
                        this.shouldKeepY = true;
                        blockData = this.getBlockData();
                        if (blockData != null && this.rotationTick <= 0) {
                            hitVec = BlockPlacementUtil.getHitVec(blockData.blockPos(), blockData.facing(), this.yaw, this.pitch);
                            this.place(blockData.blockPos(), blockData.facing(), hitVec);
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onStrafe(StrafeEvent event) {
        if (this.isEnabled()) {
            if (!mc.thePlayer.isCollidedHorizontally && mc.thePlayer.hurtTime <= 5 &&
                    !mc.thePlayer.isPotionActive(Potion.jump) && mc.gameSettings.keyBindJump.isKeyDown() &&
                    ItemInventoryUtil.isHoldingBlock()) {
                int yState = (int) (mc.thePlayer.posY % 1.0 * 100.0);
                
                switch ((int) this.tower.getInput()) {
                    case 1: // VANILLA TOWER
                        handleVanillaTower(event, yState);
                        break;
                    case 2: // EXTRA TOWER
                        handleExtraTower(event, yState);
                        break;
                    default:
                        this.towerTick = 0;
                        this.towerDelay = 0;
                }
            } else {
                this.towerTick = 0;
                this.towerDelay = 0;
            }
        }
    }

    private void handleVanillaTower(StrafeEvent event, int yState) {
        switch (this.towerTick) {
            case 0:
                if (mc.thePlayer.onGround) {
                    this.towerTick = 1;
                    mc.thePlayer.motionY = -0.0784000015258789;
                }
                return;
            case 1:
                if (yState == 0 && PlayerMovementUtil.isAirBelow()) {
                    this.startY = MathHelper.floor_double(mc.thePlayer.posY);
                    this.towerTick = 2;
                    mc.thePlayer.motionY = 0.42F;
                    if (MovementUtil.isForwardPressed()) {
                        MovementUtil.setSpeed(MovementUtil.getSpeed(), MovementUtil.getMoveYaw());
                    } else {
                        MovementUtil.setSpeed(0.0);
                        event.setForward(0.0F);
                        event.setStrafe(0.0F);
                    }
                } else {
                    this.towerTick = 0;
                }
                return;
            case 2:
                this.towerTick = 3;
                mc.thePlayer.motionY = 0.75 - mc.thePlayer.posY % 1.0;
                return;
            case 3:
                this.towerTick = 1;
                mc.thePlayer.motionY = 1.0 - mc.thePlayer.posY % 1.0;
                return;
            default:
                this.towerTick = 0;
        }
    }

    private void handleExtraTower(StrafeEvent event, int yState) {
        switch (this.towerTick) {
            case 0:
                if (mc.thePlayer.onGround) {
                    this.towerTick = 1;
                    mc.thePlayer.motionY = -0.0784000015258789;
                }
                return;
            case 1:
                if (yState == 0 && PlayerMovementUtil.isAirBelow()) {
                    this.startY = MathHelper.floor_double(mc.thePlayer.posY);
                    if (!MovementUtil.isForwardPressed()) {
                        this.towerDelay = 2;
                        MovementUtil.setSpeed(0.0);
                        event.setForward(0.0F);
                        event.setStrafe(0.0F);
                        EnumFacing facing = this.yawToFacing(MathHelper.wrapAngleTo180_float(this.yaw - 180.0F));
                        double distance = this.distanceToEdge(facing);
                        if (distance > 0.1) {
                            Vec3i directionVec = facing.getDirectionVec();
                            double offset = Math.min(this.getRandomOffset(), distance - 0.05);
                            double jitter = RandomHelper.nextDouble(0.02, 0.03);
                            AxisAlignedBB nextBox = mc.thePlayer.getEntityBoundingBox()
                                    .offset((double) directionVec.getX() * (offset - jitter), 0.0, (double) directionVec.getZ() * (offset - jitter));
                            if (mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, nextBox).isEmpty()) {
                                mc.thePlayer.motionY = -0.0784000015258789;
                                mc.thePlayer.setPosition(nextBox.minX + (nextBox.maxX - nextBox.minX) / 2.0, nextBox.minY,
                                        nextBox.minZ + (nextBox.maxZ - nextBox.minZ) / 2.0);
                            }
                        } else {
                            this.towerTick = 2;
                            this.targetFacing = facing;
                            mc.thePlayer.motionY = 0.42F;
                        }
                    } else {
                        this.towerTick = 2;
                        this.towerDelay++;
                        mc.thePlayer.motionY = 0.42F;
                        MovementUtil.setSpeed(MovementUtil.getSpeed(), MovementUtil.getMoveYaw());
                    }
                } else {
                    this.towerTick = 0;
                    this.towerDelay = 0;
                }
                return;
            case 2:
                this.towerTick = 3;
                mc.thePlayer.motionY = mc.thePlayer.motionY - RandomHelper.nextDouble(0.00101, 0.00109);
                return;
            case 3:
                if (this.towerDelay >= 4) {
                    this.towerTick = 4;
                    this.towerDelay = 0;
                } else {
                    this.towerTick = 1;
                    mc.thePlayer.motionY = 1.0 - mc.thePlayer.posY % 1.0;
                }
                return;
            case 4:
                this.towerTick = 5;
                return;
            case 5:
                if (!PlayerMovementUtil.isAirBelow()) {
                    this.towerTick = 0;
                } else {
                    this.towerTick = 1;
                    mc.thePlayer.motionY -= 0.08;
                    mc.thePlayer.motionY *= 0.98F;
                    mc.thePlayer.motionY -= 0.08;
                    mc.thePlayer.motionY *= 0.98F;
                }
                return;
            default:
                this.towerTick = 0;
                this.towerDelay = 0;
        }
    }

    @SubscribeEvent
    public void onMoveInput(PrePlayerInputEvent event) {
        if (this.isEnabled()) {
            if (this.moveFix.getInput() == 1 && RotationHelper.get().isActive() &&
                    MovementUtil.isForwardPressed()) {
                MovementUtil.fixStrafe(RotationUtils.serverRotations[0]);
            }
            if (mc.thePlayer.onGround && this.stage > 0 && MovementUtil.isForwardPressed()) {
                event.setJump(true);
            }
        }
    }

    private void onLivingUpdate() {
        if (this.isEnabled()) {
            float speed = this.getSpeed();
            if (speed != 1.0F) {
                if (mc.thePlayer.movementInput.moveForward != 0.0F && mc.thePlayer.movementInput.moveStrafe != 0.0F) {
                    mc.thePlayer.movementInput.moveForward = mc.thePlayer.movementInput.moveForward * (1.0F / (float) Math.sqrt(2.0));
                    mc.thePlayer.movementInput.moveStrafe = mc.thePlayer.movementInput.moveStrafe * (1.0F / (float) Math.sqrt(2.0));
                }
                mc.thePlayer.movementInput.moveForward *= speed;
                mc.thePlayer.movementInput.moveStrafe *= speed;
            }
            if (this.shouldStopSprint()) {
                mc.thePlayer.setSprinting(false);
            }
        }
    }

    public static boolean shouldScaffoldSafeWalk() {
        if (ModuleManager.scaffold == null || !ModuleManager.scaffold.isEnabled() || !ModuleManager.scaffold.safeWalk.isToggled()) {
            return false;
        }
        if (mc.thePlayer.onGround && mc.thePlayer.motionY <= 0.0 && PlayerMovementUtil.canMove(mc.thePlayer.motionX, mc.thePlayer.motionZ, -1.0)) {
            return true;
        }
        return false;
    }

    @SubscribeEvent
    public void onRender(net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent event) {
        if (event.phase != net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END || !this.isEnabled() || !this.blockCounter.isToggled()) {
            return;
        }
        
        int count = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.stackSize > 0) {
                if (stack.getItem() instanceof ItemBlock) {
                    Block block = ((ItemBlock) stack.getItem()).getBlock();
                    if (!BlockPlacementUtil.isInteractable(block) && BlockPlacementUtil.isSolid(block)) {
                        count += stack.stackSize;
                    }
                }
            }
        }
        
        float scale = 1.0f;
        net.minecraft.client.gui.ScaledResolution res = new net.minecraft.client.gui.ScaledResolution(mc);
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.scale(scale, scale, 0.0F);
        net.minecraft.client.renderer.GlStateManager.disableDepth();
        net.minecraft.client.renderer.GlStateManager.enableBlend();
        net.minecraft.client.renderer.GlStateManager.blendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);
        
        mc.fontRendererObj.drawString(
                String.format("%d block%s left", count, count != 1 ? "s" : ""),
                ((float) res.getScaledWidth() / 2.0F + (float) mc.fontRendererObj.FONT_HEIGHT * 1.5F) / scale,
                (float) res.getScaledHeight() / 2.0F / scale - (float) mc.fontRendererObj.FONT_HEIGHT / 2.0F + 1.0F,
                (count > 0 ? Color.WHITE.getRGB() : new Color(255, 85, 85).getRGB()) | -1090519040,
                true
        );
        
        net.minecraft.client.renderer.GlStateManager.disableBlend();
        net.minecraft.client.renderer.GlStateManager.enableDepth();
        net.minecraft.client.renderer.GlStateManager.popMatrix();
    }

    @SubscribeEvent
    public void onLeftClick(ClickMouseEvent event) {
        if (this.isEnabled()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRightClick(RightClickMouseEvent event) {
        if (this.isEnabled()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onSwap(SlotUpdateEvent event) {
        if (this.isEnabled()) {
            mc.thePlayer.inventory.currentItem = this.lastSlot;
            event.setCanceled(true);
        }
    }

    @Override
    public void onEnable() {
        if (mc.thePlayer != null) {
            this.lastSlot = mc.thePlayer.inventory.currentItem;
        } else {
            this.lastSlot = -1;
        }
        this.blockCount = -1;
        this.rotationTick = 3;
        this.yaw = -180.0F;
        this.pitch = 0.0F;
        this.canRotate = false;
        this.towerTick = 0;
        this.towerDelay = 0;
        this.towering = false;
    }

    @Override
    public void onDisable() {
        if (mc.thePlayer != null && this.lastSlot != -1) {
            mc.thePlayer.inventory.currentItem = this.lastSlot;
        }
    }

    public int getBlockCount() {
        return this.blockCount;
    }

    private boolean isMoving() {
        return Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode()) ||
                Keyboard.isKeyDown(mc.gameSettings.keyBindBack.getKeyCode()) ||
                Keyboard.isKeyDown(mc.gameSettings.keyBindLeft.getKeyCode()) ||
                Keyboard.isKeyDown(mc.gameSettings.keyBindRight.getKeyCode());
    }

    /**
     * Check if diagonal scaffolding is enabled
     * Diagonal mode is supported in rotation modes: DEFAULT (1), SIDEWAYS (3), GODBRIDGE (4), SMOOTH (5)
     */
    public boolean isDiagonalScaffoldEnabled() {
        int mode = (int) rotationMode.getInput();
        return mode == 1 || mode == 3 || mode == 4 || mode == 5;
    }

    public static class BlockData {
        private final BlockPos blockPos;
        private final EnumFacing facing;

        public BlockData(BlockPos blockPos, EnumFacing enumFacing) {
            this.blockPos = blockPos;
            this.facing = enumFacing;
        }

        public BlockPos blockPos() {
            return this.blockPos;
        }

        public EnumFacing facing() {
            return this.facing;
        }
    }
}

