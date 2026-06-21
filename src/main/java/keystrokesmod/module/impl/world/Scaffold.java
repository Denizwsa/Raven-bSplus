package keystrokesmod.module.impl.world;

import keystrokesmod.event.ClientRotationEvent;
import keystrokesmod.event.PrePlayerInputEvent;
import keystrokesmod.event.StrafeEvent;
import keystrokesmod.helper.RotationHelper;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.GroupSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBush;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class Scaffold extends Module {
    private static final EnumFacing[] SEARCH_FACES = {
            EnumFacing.UP, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST
    };
    private static final EnumFacing[][] DIAGONAL_FACES = {
            {EnumFacing.NORTH, EnumFacing.EAST},
            {EnumFacing.NORTH, EnumFacing.WEST},
            {EnumFacing.SOUTH, EnumFacing.EAST},
            {EnumFacing.SOUTH, EnumFacing.WEST}
    };

    private final GroupSetting modeGroup;
    private final SliderSetting mode;
    private static final String[] MODES = {"Normal", "Telly", "GodBridge"};

    private final GroupSetting eagleGroup;
    private final SliderSetting eagleMode;
    private static final String[] EAGLE_MODES = {"Normal", "Silent", "Off"};
    private final SliderSetting eagleAngle;
    private final ButtonSetting eagleSprint;

    private final GroupSetting towerGroup;
    private final SliderSetting towerMode;
    private static final String[] TOWER_MODES = {"None", "Jump", "Motion", "MotionTP", "Packet", "Teleport", "ConstantMotion", "AAC3.3.9", "AAC3.6.4", "Vulcan"};
    private final SliderSetting towerMotion;
    private final SliderSetting towerDelay;

    private final GroupSetting autoBlockGroup;
    private final SliderSetting autoBlockMode;
    private static final String[] AUTOBLOCK_MODES = {"Off", "Pick", "Spoof", "Switch"};

    private final GroupSetting rotationGroup;
    private final SliderSetting rotationMode;
    private static final String[] ROTATION_MODES = {"Off", "Normal", "Stabilized", "ReverseYaw"};
    private final SliderSetting rotationSpeed;

    private final GroupSetting miscGroup;
    private final ButtonSetting sprint;
    private final ButtonSetting swing;
    private final ButtonSetting sameY;
    private final ButtonSetting jumpOnUserInput;
    private final ButtonSetting down;
    private final ButtonSetting autoJump;
    private final ButtonSetting allowClutch;
    private final ButtonSetting blockSafe;
    private final ButtonSetting autoF5;
    private final ButtonSetting keepRotation;
    private final ButtonSetting rotateOnEnable;
    private final ButtonSetting multiPlace;
    private final ButtonSetting moveFix;
    private final SliderSetting timer;
    private final SliderSetting speedModifier;
    private final SliderSetting slowSpeed;
    private final ButtonSetting slow;
    private final ButtonSetting slowGround;
    private final SliderSetting placeDelay;

    private final GroupSetting zitterGroup;
    private final SliderSetting zitterMode;
    private static final String[] ZITTER_MODES = {"Off", "Teleport", "Smooth"};
    private final SliderSetting zitterSpeed;
    private final SliderSetting zitterStrength;

    private BlockPos targetPos;
    private EnumFacing targetFacing;
    private Vec3 targetHitVec;
    private boolean shouldRotate;
    private float[] lastRotation;

    private int launchY;
    private boolean eagleSneaking;

    private int tellyTicksUntilJump;
    private int tellyJumpTicks;
    private int tellyBlocksUntilAxisChange;
    private int tellyHorizontalPlacements;
    private int tellyVerticalPlacements;

    private int towerTicks;
    private double towerJumpGround;

    private long lastPlaceTime;

    public Scaffold() {
        super("Scaffold", category.world);

        modeGroup = new GroupSetting("Scaffold Mode");
        this.registerSetting(modeGroup);
        this.registerSetting(mode = new SliderSetting(modeGroup, "Mode", 0, MODES));

        eagleGroup = new GroupSetting("Eagle");
        this.registerSetting(eagleGroup);
        this.registerSetting(eagleMode = new SliderSetting(eagleGroup, "Mode", 0, EAGLE_MODES));
        this.registerSetting(eagleAngle = new SliderSetting(eagleGroup, "Edge angle", "°", 0, 0, 90, 1));
        this.registerSetting(eagleSprint = new ButtonSetting(eagleGroup, "Eagle sprint", false));

        towerGroup = new GroupSetting("Tower");
        this.registerSetting(towerGroup);
        this.registerSetting(towerMode = new SliderSetting(towerGroup, "Mode", 0, TOWER_MODES));
        this.registerSetting(towerMotion = new SliderSetting(towerGroup, "Motion", 0.42, 0.1, 2.0, 0.01));
        this.registerSetting(towerDelay = new SliderSetting(towerGroup, "Delay", 0, 0, 10, 1));

        autoBlockGroup = new GroupSetting("Auto Block");
        this.registerSetting(autoBlockGroup);
        this.registerSetting(autoBlockMode = new SliderSetting(autoBlockGroup, "Mode", 0, AUTOBLOCK_MODES));

        rotationGroup = new GroupSetting("Rotation");
        this.registerSetting(rotationGroup);
        this.registerSetting(rotationMode = new SliderSetting(rotationGroup, "Mode", 1, ROTATION_MODES));
        this.registerSetting(rotationSpeed = new SliderSetting(rotationGroup, "Speed", "%", 80, 10, 100, 5));

        miscGroup = new GroupSetting("Misc");
        this.registerSetting(miscGroup);
        this.registerSetting(sprint = new ButtonSetting(miscGroup, "Sprint", false));
        this.registerSetting(swing = new ButtonSetting(miscGroup, "Swing", true));
        this.registerSetting(sameY = new ButtonSetting(miscGroup, "Same Y", false));
        this.registerSetting(jumpOnUserInput = new ButtonSetting(miscGroup, "Jump on input", true));
        this.registerSetting(down = new ButtonSetting(miscGroup, "Down", true));
        this.registerSetting(autoJump = new ButtonSetting(miscGroup, "Auto jump", false));
        this.registerSetting(allowClutch = new ButtonSetting(miscGroup, "Allow clutch", true));
        this.registerSetting(blockSafe = new ButtonSetting(miscGroup, "Block safe", false));
        this.registerSetting(autoF5 = new ButtonSetting(miscGroup, "Auto F5", false));
        this.registerSetting(keepRotation = new ButtonSetting(miscGroup, "Keep rotation", true));
        this.registerSetting(rotateOnEnable = new ButtonSetting(miscGroup, "Rotate on enable", true));
        this.registerSetting(multiPlace = new ButtonSetting(miscGroup, "Multi place", true));
        this.registerSetting(moveFix = new ButtonSetting(miscGroup, "Move fix", true));
        this.registerSetting(timer = new SliderSetting(miscGroup, "Timer", 1.0, 0.1, 10.0, 0.1));
        this.registerSetting(speedModifier = new SliderSetting(miscGroup, "Speed modifier", 1.0, 0.0, 2.0, 0.05));
        this.registerSetting(slow = new ButtonSetting(miscGroup, "Slow", false));
        this.registerSetting(slowGround = new ButtonSetting(miscGroup, "Slow ground only", false));
        this.registerSetting(slowSpeed = new SliderSetting(miscGroup, "Slow speed", 0.6, 0.1, 1.0, 0.05));
        this.registerSetting(placeDelay = new SliderSetting(miscGroup, "Place delay", "ms", 0, 0, 500, 10));

        zitterGroup = new GroupSetting("Zitter");
        this.registerSetting(zitterGroup);
        this.registerSetting(zitterMode = new SliderSetting(zitterGroup, "Mode", 0, ZITTER_MODES));
        this.registerSetting(zitterSpeed = new SliderSetting(zitterGroup, "Speed", 0.15, 0.05, 0.5, 0.01));
        this.registerSetting(zitterStrength = new SliderSetting(zitterGroup, "Strength", 0.05, 0.0, 0.3, 0.01));

        this.closetModule = true;
    }

    @Override
    public void onEnable() {
        targetPos = null;
        targetFacing = null;
        targetHitVec = null;
        launchY = mc.thePlayer != null ? (int) Math.floor(mc.thePlayer.posY) : 0;
        eagleSneaking = false;
        tellyBlocksUntilAxisChange = 0;
        tellyTicksUntilJump = 0;
        tellyJumpTicks = 3;
        tellyHorizontalPlacements = 1;
        tellyVerticalPlacements = 1;
        towerTicks = 0;
        lastPlaceTime = 0;
        shouldRotate = false;
        lastRotation = null;

        if (rotateOnEnable.isToggled() && mc.thePlayer != null) {
            findInitialTarget();
        }
    }

    private void findInitialTarget() {
        BlockPos below = getPlacementPos();
        if (below == null || !BlockUtils.replaceable(below)) return;

        for (EnumFacing side : SEARCH_FACES) {
            BlockPos neighbor = below.offset(side);
            if (!canClickBlock(neighbor)) continue;

            EnumFacing actualSide = side.getOpposite();
            Vec3 hitVec = BlockUtils.getFaceCenter(neighbor, actualSide);

            targetPos = neighbor;
            targetFacing = actualSide;
            targetHitVec = hitVec;
            shouldRotate = true;
            return;
        }
    }

    @Override
    public void onDisable() {
        if (!Utils.isBindDown(mc.gameSettings.keyBindSneak)) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
        }
        if (eagleSneaking) {
            mc.thePlayer.sendQueue.addToSendQueue(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SNEAKING));
            eagleSneaking = false;
        }
        if (autoF5.isToggled()) {
            mc.gameSettings.thirdPersonView = 0;
        }
        ((IAccessorMinecraft) mc).getTimer().timerSpeed = 1.0f;
        targetPos = null;
        shouldRotate = false;
        lastRotation = null;
    }

    @Override
    public String getInfo() {
        return MODES[(int) mode.getInput()];
    }

    @SubscribeEvent
    public void onPrePlayerInput(PrePlayerInputEvent e) {
        if (!Utils.nullCheck()) return;

        ((IAccessorMinecraft) mc).getTimer().timerSpeed = (float) timer.getInput();

        handleTower();

        if (slow.isToggled() && (!slowGround.isToggled() || mc.thePlayer.onGround)) {
            mc.thePlayer.motionX *= slowSpeed.getInput();
            mc.thePlayer.motionZ *= slowSpeed.getInput();
        }

        handleEagle(e);

        if (!allowClutch.isToggled() && !mc.thePlayer.onGround && !isTellyMode()) return;

        if (sprint.isToggled() && mc.thePlayer.onGround) {
            mc.thePlayer.setSprinting(true);
        }

        update();
    }

    @SubscribeEvent
    public void onStrafe(StrafeEvent e) {
        if (!Utils.nullCheck() || !mc.thePlayer.onGround) return;

        if (autoJump.isToggled() && Utils.isMoving()) {
            mc.thePlayer.jump();
        }

        if (getModeName("Mode").equals("Telly") && mc.thePlayer.onGround && Utils.isMoving()) {
            handleTellyJump();
        }
    }

    @SubscribeEvent
    public void onClientRotation(ClientRotationEvent e) {
        if (!Utils.nullCheck() || getRotationMode().equals("Off")) return;

        if (shouldRotate && targetPos != null && targetFacing != null) {
            float[] raw = RotationUtils.getRotationsToBlock(targetPos, targetFacing);
            float baseYaw = e.yaw != null ? e.yaw : mc.thePlayer.rotationYaw;
            float basePitch = e.pitch != null ? e.pitch : mc.thePlayer.rotationPitch;

            float[] fixed = RotationUtils.fixRotation(raw[0], raw[1], baseYaw, basePitch);
            float yaw = fixed[0];
            float pitch = fixed[1];

            float maxChange = (float) (rotationSpeed.getInput() / 100.0f * 180.0f);
            float yawDelta = MathHelper.wrapAngleTo180_float(yaw - baseYaw);
            float pitchDelta = pitch - basePitch;
            if (Math.abs(yawDelta) > maxChange) {
                yaw = baseYaw + Math.signum(yawDelta) * maxChange;
            }
            if (Math.abs(pitchDelta) > maxChange) {
                pitch = basePitch + Math.signum(pitchDelta) * maxChange;
            }

            String rMode = getRotationMode();
            if (rMode.equals("Stabilized")) {
                yaw = (float) (Math.round(yaw / 45.0) * 45.0);
            } else if (rMode.equals("ReverseYaw")) {
                yaw += 180;
            }

            if (moveFix.isToggled()) {
                RotationHelper.get().forceMovementFix = true;
            }

            lastRotation = new float[]{yaw, pitch};
            e.setYaw(yaw);
            e.setPitch(pitch);
        } else if (keepRotation.isToggled() && lastRotation != null) {
            e.setYaw(lastRotation[0]);
            e.setPitch(lastRotation[1]);
        }
    }

    private void update() {
        if (mc.currentScreen != null || mc.thePlayer.capabilities.isFlying) return;

        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemBlock)) {
            if (getAutoBlockMode().equals("Off")) return;
            int slot = findBlockInHotbar();
            if (slot == -1) return;
            if (getAutoBlockMode().equals("Pick")) {
                mc.thePlayer.inventory.currentItem = slot;
            }
        }

        findPlacementTarget();
    }

    private void findPlacementTarget() {
        BlockPos below = getPlacementPos();
        if (below == null) return;

        if (!BlockUtils.replaceable(below)) {
            if (autoF5.isToggled()) mc.gameSettings.thirdPersonView = 0;
            if (!keepRotation.isToggled()) {
                targetPos = null;
                targetFacing = null;
                shouldRotate = false;
                lastRotation = null;
            }
            return;
        }

        if (blockSafe.isToggled()) {
            BlockPos feetPos = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 0.5, mc.thePlayer.posZ);
            if (BlockUtils.replaceable(feetPos)) return;
        }

        if (autoF5.isToggled() && mc.gameSettings.thirdPersonView != 1) {
            mc.gameSettings.thirdPersonView = 1;
        }

        double maxReach = mc.playerController.getBlockReachDistance();

        PlaceResult best = null;
        double bestDiff = Double.MAX_VALUE;

        for (EnumFacing side : SEARCH_FACES) {
            BlockPos neighbor = below.offset(side);
            if (!canClickBlock(neighbor)) continue;

            BlockPos actualClick = neighbor;
            EnumFacing actualSide = side.getOpposite();

            Vec3 hitVec = BlockUtils.getFaceCenter(neighbor, actualSide);
            float[] rots = RotationUtils.getRotationsToBlock(neighbor, actualSide, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
            float yaw = rots[0];
            float pitch = rots[1];

            MovingObjectPosition mop = RotationUtils.rayCastBlock(maxReach, yaw, pitch);
            if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                actualClick = mop.getBlockPos();
                actualSide = mop.sideHit;
                hitVec = mop.hitVec;
            }

            float[] currentRots = RotationUtils.getRotations(actualClick, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
            double diff = Math.abs(MathHelper.wrapAngleTo180_float(currentRots[0] - mc.thePlayer.rotationYaw))
                    + Math.abs(MathHelper.wrapAngleTo180_float(currentRots[1] - mc.thePlayer.rotationPitch));

            if (diff < bestDiff) {
                bestDiff = diff;
                best = new PlaceResult(actualClick, actualSide, hitVec, yaw, pitch);
            }
        }

        if (multiPlace.isToggled()) {
            for (EnumFacing[] diag : DIAGONAL_FACES) {
                BlockPos neighbor = below.offset(diag[0]).offset(diag[1]);
                if (!canClickBlock(neighbor)) continue;

                EnumFacing actualSide = diag[0].getOpposite();
                Vec3 hitVec = BlockUtils.getFaceCenter(neighbor, actualSide);
                float[] rots = RotationUtils.getRotationsToBlock(neighbor, actualSide, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
                float yaw = rots[0];
                float pitch = rots[1];

                MovingObjectPosition mop = RotationUtils.rayCastBlock(maxReach, yaw, pitch);
                if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                    actualSide = mop.sideHit;
                    hitVec = mop.hitVec;
                }

                float[] currentRots = RotationUtils.getRotations(neighbor, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
                double diff = Math.abs(MathHelper.wrapAngleTo180_float(currentRots[0] - mc.thePlayer.rotationYaw))
                        + Math.abs(MathHelper.wrapAngleTo180_float(currentRots[1] - mc.thePlayer.rotationPitch));

                if (diff < bestDiff) {
                    bestDiff = diff;
                    best = new PlaceResult(neighbor, actualSide, hitVec, yaw, pitch);
                }
            }
        }

        if (best != null) {
            targetPos = best.pos;
            targetFacing = best.facing;
            targetHitVec = best.hitVec;
            shouldRotate = true;

            if (mc.thePlayer.onGround || isTellyMode()) {
                placeBlock();
            }
        } else if (!keepRotation.isToggled()) {
            targetPos = null;
            targetFacing = null;
            shouldRotate = false;
            lastRotation = null;
        }
    }

    private BlockPos getPlacementPos() {
        if (isDownKeyDown()) {
            if (Math.abs(mc.thePlayer.posY - Math.floor(mc.thePlayer.posY) - 0.5) <= 1E-3) {
                return new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 0.6, mc.thePlayer.posZ);
            }
            return new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 0.6, mc.thePlayer.posZ).down();
        }

        if (sameY.isToggled() && launchY <= (int) Math.floor(mc.thePlayer.posY)) {
            return new BlockPos(mc.thePlayer.posX, launchY - 1, mc.thePlayer.posZ);
        }

        if (Math.abs(mc.thePlayer.posY - Math.floor(mc.thePlayer.posY) - 0.5) <= 1E-3) {
            return new BlockPos(mc.thePlayer);
        }

        return new BlockPos(mc.thePlayer).down();
    }

    private void placeBlock() {
        if (targetPos == null || targetFacing == null) return;

        if (System.currentTimeMillis() - lastPlaceTime < placeDelay.getInput()) return;

        ItemStack stack = mc.thePlayer.getHeldItem();

        if (stack == null || !(stack.getItem() instanceof ItemBlock)) {
            if (getAutoBlockMode().equals("Off")) return;
            int found = findBlockInHotbar();
            if (found == -1) return;
            stack = mc.thePlayer.inventory.getStackInSlot(found);
            if (getAutoBlockMode().equals("Spoof")) {
                int prevSlot = mc.thePlayer.inventory.currentItem;
                mc.thePlayer.inventory.currentItem = found;
                if (doPlaceBlock(stack)) {
                    mc.thePlayer.inventory.currentItem = prevSlot;
                } else {
                    mc.thePlayer.inventory.currentItem = prevSlot;
                }
                return;
            }
            if (!getAutoBlockMode().equals("Pick")) {
                mc.thePlayer.inventory.currentItem = found;
            }
        }

        if (stack == null || stack.stackSize <= 0) return;

        doPlaceBlock(stack);
    }

    private boolean doPlaceBlock(ItemStack stack) {
        if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, stack, targetPos, targetFacing, targetHitVec)) {
            lastPlaceTime = System.currentTimeMillis();

            if (swing.isToggled()) {
                mc.thePlayer.swingItem();
            } else {
                mc.getNetHandler().addToSendQueue(new C0APacketAnimation());
            }

            if (speedModifier.getInput() != 1.0 && mc.thePlayer.onGround) {
                mc.thePlayer.motionX *= speedModifier.getInput();
                mc.thePlayer.motionZ *= speedModifier.getInput();
            }

            if (isTellyMode()) updateTellyCounters();

            return true;
        }
        return false;
    }

    private void handleEagle(PrePlayerInputEvent e) {
        if (getEagleMode().equals("Off") || isDownKeyDown() || isGodBridgeActive()) return;

        boolean onEdge = isOnEdge();
        boolean shouldEagle = (mc.thePlayer.onGround || (jumpOnUserInput.isToggled() && !e.isJump())) && onEdge;

        if (getEagleMode().equals("Silent")) {
            if (eagleSneaking != shouldEagle) {
                mc.getNetHandler().addToSendQueue(new C0BPacketEntityAction(
                        mc.thePlayer, shouldEagle
                        ? C0BPacketEntityAction.Action.START_SNEAKING
                        : C0BPacketEntityAction.Action.STOP_SNEAKING
                ));
                eagleSneaking = shouldEagle;
            }
        } else {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), shouldEagle);
            if (eagleSprint.isToggled() && shouldEagle) {
                mc.thePlayer.setSprinting(true);
            }
        }
    }

    private boolean isOnEdge() {
        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox();
        double feetX = (box.minX + box.maxX) / 2.0;
        double feetZ = (box.minZ + box.maxZ) / 2.0;

        BlockPos below = new BlockPos(feetX, box.minY - 0.01, feetZ);
        if (!BlockUtils.replaceable(below)) return false;

        double maxDist = 0.0;
        for (double dx = -0.3; dx <= 0.3; dx += 0.3) {
            for (double dz = -0.3; dz <= 0.3; dz += 0.3) {
                BlockPos check = new BlockPos(feetX + dx, box.minY - 0.01, feetZ + dz);
                if (!BlockUtils.replaceable(check)) {
                    double dist = Math.max(Math.abs(dx), Math.abs(dz));
                    if (dist > maxDist) maxDist = dist;
                }
            }
        }

        double angle = Math.toDegrees(Math.atan2(maxDist, 1.0));
        return angle <= eagleAngle.getInput();
    }

    private void handleTower() {
        if (getTowerMode().equals("None")) return;
        if (!mc.gameSettings.keyBindJump.isKeyDown()) return;

        mc.thePlayer.motionX = 0;
        mc.thePlayer.motionZ = 0;

        towerTicks++;

        switch (getTowerMode()) {
            case "Jump":
                if (mc.thePlayer.onGround) {
                    mc.thePlayer.jump();
                }
                break;
            case "Motion":
                if (mc.thePlayer.onGround) {
                    mc.thePlayer.motionY = towerMotion.getInput();
                } else if (mc.thePlayer.motionY < 0.1) {
                    mc.thePlayer.motionY = -0.3;
                }
                break;
            case "MotionTP":
                if (mc.thePlayer.onGround) {
                    mc.thePlayer.motionY = 0.42;
                } else if (mc.thePlayer.motionY < 0.23) {
                    mc.thePlayer.setPosition(mc.thePlayer.posX, Math.floor(mc.thePlayer.posY), mc.thePlayer.posZ);
                }
                break;
            case "Packet":
                if (mc.thePlayer.onGround && towerTicks >= towerDelay.getInput() + 1) {
                    mc.getNetHandler().addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(mc.thePlayer.posX, mc.thePlayer.posY + 0.42, mc.thePlayer.posZ, false));
                    mc.getNetHandler().addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(mc.thePlayer.posX, mc.thePlayer.posY + 0.753, mc.thePlayer.posZ, false));
                    mc.thePlayer.setPosition(mc.thePlayer.posX, mc.thePlayer.posY + 1.0, mc.thePlayer.posZ);
                    towerTicks = 0;
                }
                break;
            case "Teleport":
                if ((mc.thePlayer.onGround) && towerTicks >= towerDelay.getInput() + 1) {
                    mc.thePlayer.setPosition(mc.thePlayer.posX, mc.thePlayer.posY + 1.0, mc.thePlayer.posZ);
                    towerTicks = 0;
                }
                break;
            case "ConstantMotion":
                if (mc.thePlayer.onGround) {
                    towerJumpGround = mc.thePlayer.posY;
                    mc.thePlayer.motionY = towerMotion.getInput();
                }
                if (mc.thePlayer.posY > towerJumpGround + 0.79) {
                    mc.thePlayer.setPosition(mc.thePlayer.posX, Math.floor(mc.thePlayer.posY), mc.thePlayer.posZ);
                    mc.thePlayer.motionY = towerMotion.getInput();
                    towerJumpGround = mc.thePlayer.posY;
                }
                break;
            case "AAC3.3.9":
                if (mc.thePlayer.onGround) {
                    mc.thePlayer.motionY = 0.4001;
                }
                if (mc.thePlayer.motionY < 0) {
                    mc.thePlayer.motionY -= 0.00000945;
                }
                break;
            case "AAC3.6.4":
                if (towerTicks % 4 == 1) {
                    mc.thePlayer.motionY = 0.4195464;
                } else if (towerTicks % 4 == 0) {
                    mc.thePlayer.motionY = -0.5;
                }
                break;
            case "Vulcan":
                if (towerTicks % 10 == 0) {
                    mc.thePlayer.motionY = -0.1;
                    return;
                }
                if (towerTicks % 2 == 0) {
                    mc.thePlayer.motionY = 0.7;
                } else {
                    mc.thePlayer.motionY = Utils.isMoving() ? 0.42 : 0.6;
                }
                break;
        }
    }

    private void handleTellyJump() {
        tellyTicksUntilJump++;
        if (tellyTicksUntilJump >= tellyJumpTicks) {
            mc.thePlayer.jump();
            tellyTicksUntilJump = 0;
            tellyJumpTicks = 3 + (int) (Math.random() * 3);
        }
    }

    private void updateTellyCounters() {
        if (tellyBlocksUntilAxisChange > tellyHorizontalPlacements + tellyVerticalPlacements) {
            tellyBlocksUntilAxisChange = 0;
            tellyHorizontalPlacements = 1 + (int) (Math.random() * 2);
            tellyVerticalPlacements = 1 + (int) (Math.random() * 2);
            return;
        }
        tellyBlocksUntilAxisChange++;
    }

    private boolean isDownKeyDown() {
        return down.isToggled() && Utils.isBindDown(mc.gameSettings.keyBindSneak)
                && !getModeName("Mode").equals("GodBridge") && !isTellyMode();
    }

    private boolean isTellyMode() {
        return getModeName("Mode").equals("Telly");
    }

    private boolean isGodBridgeActive() {
        return getModeName("Mode").equals("GodBridge");
    }

    private boolean canClickBlock(BlockPos pos) {
        if (mc.theWorld == null || pos == null) return false;
        Block block = BlockUtils.getBlock(pos);
        return block != null && block != Blocks.air && !(block instanceof BlockBush) && !BlockUtils.isFluid(block);
    }

    private int findBlockInHotbar() {
        int bestSlot = -1;
        int bestCount = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack == null || stack.stackSize <= 0) continue;
            if (!(stack.getItem() instanceof ItemBlock)) continue;
            Block block = ((ItemBlock) stack.getItem()).block;
            if (block instanceof BlockBush) continue;
            if (stack.stackSize > bestCount) {
                bestCount = stack.stackSize;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private String getModeName(String key) {
        int idx = (int) mode.getInput();
        if (idx >= 0 && idx < MODES.length) return MODES[idx];
        return "Normal";
    }

    private String getEagleMode() {
        int idx = (int) eagleMode.getInput();
        if (idx >= 0 && idx < EAGLE_MODES.length) return EAGLE_MODES[idx];
        return "Normal";
    }

    private String getRotationMode() {
        int idx = (int) rotationMode.getInput();
        if (idx >= 0 && idx < ROTATION_MODES.length) return ROTATION_MODES[idx];
        return "Off";
    }

    private String getAutoBlockMode() {
        int idx = (int) autoBlockMode.getInput();
        if (idx >= 0 && idx < AUTOBLOCK_MODES.length) return AUTOBLOCK_MODES[idx];
        return "Off";
    }

    private String getTowerMode() {
        int idx = (int) towerMode.getInput();
        if (idx >= 0 && idx < TOWER_MODES.length) return TOWER_MODES[idx];
        return "None";
    }

    private static class PlaceResult {
        final BlockPos pos;
        final EnumFacing facing;
        final Vec3 hitVec;
        final float yaw;
        final float pitch;

        PlaceResult(BlockPos pos, EnumFacing facing, Vec3 hitVec, float yaw, float pitch) {
            this.pos = pos;
            this.facing = facing;
            this.hitVec = hitVec;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }
}
