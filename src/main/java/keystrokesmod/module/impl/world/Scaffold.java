package keystrokesmod.module.impl.world;

import keystrokesmod.event.ClientRotationEvent;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.event.RotationEvent;
import keystrokesmod.event.SafeWalkEvent;
import keystrokesmod.event.ScaffoldPlaceEvent;
import keystrokesmod.event.SprintEvent;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.combat.autoclicker.IAutoClicker;
import keystrokesmod.module.impl.world.scaffold.IScaffoldRotation;
import keystrokesmod.module.impl.world.scaffold.IScaffoldSchedule;
import keystrokesmod.module.impl.world.scaffold.IScaffoldSprint;
import keystrokesmod.module.impl.world.scaffold.rotation.BackwardsRotation;
import keystrokesmod.module.impl.world.scaffold.rotation.ConstantRotation;
import keystrokesmod.module.impl.world.scaffold.rotation.NoneRotation;
import keystrokesmod.module.impl.world.scaffold.rotation.PreciseRotation;
import keystrokesmod.module.impl.world.scaffold.rotation.StrictRotation;
import keystrokesmod.module.impl.world.scaffold.schedule.NormalSchedule;
import keystrokesmod.module.impl.world.scaffold.schedule.SimpleTellySchedule;
import keystrokesmod.module.impl.world.scaffold.schedule.TellySchedule;
import keystrokesmod.module.impl.world.scaffold.sprint.DisabledSprint;
import keystrokesmod.module.impl.world.scaffold.sprint.EdgeSprint;
import keystrokesmod.module.impl.world.scaffold.sprint.HypixelJump2Sprint;
import keystrokesmod.module.impl.world.scaffold.sprint.HypixelJump3Sprint;
import keystrokesmod.module.impl.world.scaffold.sprint.HypixelJumpSprint;
import keystrokesmod.module.impl.world.scaffold.sprint.HypixelSprint;
import keystrokesmod.module.impl.world.scaffold.sprint.JumpSprint;
import keystrokesmod.module.impl.world.scaffold.sprint.LegitSprint;
import keystrokesmod.module.impl.world.scaffold.sprint.OldIntaveSprint;
import keystrokesmod.module.impl.world.scaffold.sprint.SneakSprint;
import keystrokesmod.module.impl.world.scaffold.sprint.VanillaSprint;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.ContainerUtils;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.aim.AimSimulator;
import keystrokesmod.utility.aim.RotationData;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockCactus;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockSkull;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.*;

public class Scaffold extends IAutoClicker {
    private static final String[] rotationModes = new String[]{"None", "Backwards", "Strict", "Precise", "Constant"};
    private static final String[] sprintModes = new String[]{"Disabled", "Vanilla", "Edge", "Jump", "Hypixel Jump", "Hypixel Jump 2", "Hypixel Jump 3", "Hypixel", "Legit", "Sneak", "Old Intave"};
    private static final String[] precisionModes = new String[]{"Very low", "Low", "Moderate", "High", "Very high", "Unlimited"};
    private static final String[] schedules = new String[]{"Normal", "Telly", "Simple Telly"};

    private static final IScaffoldRotation[] rotationImpls = new IScaffoldRotation[]{
            new NoneRotation(),
            new BackwardsRotation(),
            new StrictRotation(),
            new PreciseRotation(),
            new ConstantRotation()
    };

    private static final IScaffoldSprint[] sprintImpls = new IScaffoldSprint[]{
            new DisabledSprint(),
            new VanillaSprint(),
            new EdgeSprint(),
            new JumpSprint(),
            new HypixelJumpSprint(),
            new HypixelJump2Sprint(),
            new HypixelJump3Sprint(),
            new HypixelSprint(),
            new LegitSprint(),
            new SneakSprint(),
            new OldIntaveSprint()
    };

    private static final IScaffoldSchedule[] scheduleImpls = new IScaffoldSchedule[]{
            new NormalSchedule(),
            new TellySchedule(),
            new SimpleTellySchedule()
    };

    private final SliderSetting rotationMode;
    private final SliderSetting sprintMode;
    private final SliderSetting schedule;
    private final SliderSetting motion;
    private final SliderSetting aimSpeed;
    private final SliderSetting strafe;
    private final SliderSetting precision;
    private final ButtonSetting moveFix;
    private final ButtonSetting safeWalk;
    private final ButtonSetting tower;
    private final ButtonSetting autoSwap;
    private final ButtonSetting useBiggestStack;
    private final ButtonSetting multiPlace;
    private final ButtonSetting fastOnRMB;
    private final ButtonSetting showBlockCount;
    private final ButtonSetting silentSwing;
    private final ButtonSetting noSwing;
    private final ButtonSetting expand;
    private final SliderSetting expandDistance;
    private final ButtonSetting polar;
    private final ButtonSetting esp;
    private final ButtonSetting raytrace;
    private final SliderSetting alpha;
    private final ButtonSetting outline;
    private final ButtonSetting shade;
    private final DescriptionSetting descRotation;
    private final DescriptionSetting descSprint;
    private final DescriptionSetting descMisc;
    private final DescriptionSetting descRender;

    public MovingObjectPosition placeBlock;
    public float placeYaw;
    public float placePitch = 85;
    public int offGroundTicks = 0;
    public int onGroundTicks = 0;
    public MovingObjectPosition rayCasted;
    private int lastSlot = -1;
    private boolean delay = false;
    private boolean place = false;
    private boolean noPlace = false;
    private final Map<BlockPos, Long> highlight = new HashMap<>();
    private int totalBlocksPlaced = 0;

    private IScaffoldRotation activeRotation = new NoneRotation();
    private IScaffoldSprint activeSprint = new DisabledSprint();
    private IScaffoldSchedule activeSchedule = new NormalSchedule();
    private Float lastYaw = null;
    private Float lastPitch = null;

    public Scaffold() {
        super("Scaffold", keystrokesmod.module.Module.category.world);
        this.registerSetting(descRotation = new DescriptionSetting("Rotation"));
        this.registerSetting(rotationMode = new SliderSetting("Mode", 1, rotationModes));
        this.registerSetting(aimSpeed = new SliderSetting("Aim speed", 20.0, 5.0, 20.0, 0.1));
        this.registerSetting(moveFix = new ButtonSetting("Move fix", false));
        this.registerSetting(strafe = new SliderSetting("Strafe", 0, 0, 90, 1));
        this.registerSetting(descSprint = new DescriptionSetting("Sprint"));
        this.registerSetting(sprintMode = new SliderSetting("Sprint", 1, sprintModes));
        this.registerSetting(motion = new SliderSetting("Motion", 1.0, 0.5, 1.2, 0.01));
        this.registerSetting(schedule = new SliderSetting("Schedule", 0, schedules));
        this.registerSetting(precision = new SliderSetting("Precision", 3, precisionModes));
        this.registerSetting(descMisc = new DescriptionSetting("Misc"));
        this.registerSetting(autoSwap = new ButtonSetting("Auto swap", true));
        this.registerSetting(useBiggestStack = new ButtonSetting("Use biggest stack", true));
        this.registerSetting(multiPlace = new ButtonSetting("Multi-place", false));
        this.registerSetting(fastOnRMB = new ButtonSetting("Fast on RMB", false));
        this.registerSetting(safeWalk = new ButtonSetting("Safe walk", true));
        this.registerSetting(tower = new ButtonSetting("Tower", false));
        this.registerSetting(silentSwing = new ButtonSetting("Silent swing", false));
        this.registerSetting(noSwing = new ButtonSetting("No swing", false));
        this.registerSetting(showBlockCount = new ButtonSetting("Show block count", true));
        this.registerSetting(expand = new ButtonSetting("Expand", false));
        this.registerSetting(expandDistance = new SliderSetting("Expand distance", 4.5, 0, 10, 0.1));
        this.registerSetting(polar = new ButtonSetting("Polar", false));
        this.registerSetting(descRender = new DescriptionSetting("Render"));
        this.registerSetting(esp = new ButtonSetting("ESP", false));
        this.registerSetting(raytrace = new ButtonSetting("Raytrace", false));
        this.registerSetting(alpha = new SliderSetting("Alpha", 200, 0, 255, 1));
        this.registerSetting(outline = new ButtonSetting("Outline", true));
        this.registerSetting(shade = new ButtonSetting("Shade", false));
        this.hidden = false;
    }

    public static boolean sprint() {
        if (ModuleManager.scaffold != null && ModuleManager.scaffold.isEnabled()
                && ModuleManager.scaffold.sprintMode.getInput() > 0
                && (!ModuleManager.scaffold.fastOnRMB.isToggled() || Mouse.isButtonDown(1))) {
            return true;
        }
        return false;
    }

    public static int getSlot() {
        int slot = -1;
        int highestStack = -1;
        for (int i = 0; i < 9; ++i) {
            ItemStack itemStack = mc.thePlayer.inventory.mainInventory[i];
            if (itemStack != null && itemStack.getItem() instanceof ItemBlock
                    && canBePlaced((ItemBlock) itemStack.getItem())
                    && itemStack.stackSize > 0) {
                if (itemStack.stackSize > highestStack) {
                    highestStack = itemStack.stackSize;
                    slot = i;
                }
            }
        }
        return slot;
    }

    private static boolean canBePlaced(ItemBlock itemBlock) {
        return ContainerUtils.canBePlaced(itemBlock);
    }

    public int getCurrentSlot() {
        int slot = lastSlot != -1 ? lastSlot : mc.thePlayer.inventory.currentItem;
        if (autoSwap.isToggled()) {
            if (useBiggestStack.isToggled()) {
                slot = getSlot();
            } else {
                ItemStack held = mc.thePlayer.inventory.getStackInSlot(slot);
                if (held == null || !(held.getItem() instanceof ItemBlock) || !canBePlaced((ItemBlock) held.getItem())) {
                    slot = getSlot();
                }
            }
        }
        return slot;
    }

    private void updateStrategies() {
        int rotIdx = Math.min((int) rotationMode.getInput(), rotationImpls.length - 1);
        int sprIdx = Math.min((int) sprintMode.getInput(), sprintImpls.length - 1);
        int schIdx = Math.min((int) schedule.getInput(), scheduleImpls.length - 1);
        if (activeRotation != rotationImpls[rotIdx]) {
            activeRotation.onDisable();
            activeRotation = rotationImpls[rotIdx];
            activeRotation.onEnable();
        }
        if (activeSprint != sprintImpls[sprIdx]) {
            activeSprint.onDisable();
            activeSprint = sprintImpls[sprIdx];
            activeSprint.onEnable();
        }
        if (activeSchedule != scheduleImpls[schIdx]) {
            activeSchedule.onDisable();
            activeSchedule = scheduleImpls[schIdx];
            activeSchedule.onEnable();
        }
    }

    @Override
    public void onEnable() {
        lastSlot = -1;
        placeBlock = null;
        delay = false;
        highlight.clear();
        offGroundTicks = 0;
        onGroundTicks = 0;
        noPlace = false;
        totalBlocksPlaced = 0;
        place = false;
        lastYaw = null;
        lastPitch = null;
        updateStrategies();
    }

    @Override
    public void onDisable() {
        placeBlock = null;
        if (lastSlot != -1) {
            mc.thePlayer.inventory.currentItem = lastSlot;
            lastSlot = -1;
        }
        delay = false;
        highlight.clear();
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        lastYaw = null;
        lastPitch = null;
        activeRotation.onDisable();
        activeSprint.onDisable();
        activeSchedule.onDisable();
    }

    @Override
    public String getInfo() {
        return sprintModes[(int) sprintMode.getInput()];
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPreUpdate(PreUpdateEvent event) {
        if (mc.thePlayer == null || mc.theWorld == null) return;
        updateStrategies();

        if (mc.thePlayer.onGround) {
            offGroundTicks = 0;
            onGroundTicks++;
        } else {
            offGroundTicks++;
            onGroundTicks = 0;
        }

        if (delay) {
            delay = false;
            return;
        }

        if (lastSlot == -1) {
            lastSlot = mc.thePlayer.inventory.currentItem;
        }

        int slot = getCurrentSlot();
        if (slot == -1) return;
        if (autoSwap.isToggled()) {
            mc.thePlayer.inventory.currentItem = slot;
        }
        if (slot != lastSlot) {
            mc.thePlayer.inventory.currentItem = slot;
        }

        ItemStack heldItem = mc.thePlayer.inventory.getStackInSlot(slot);
        if (heldItem == null || !(heldItem.getItem() instanceof ItemBlock)
                || !canBePlaced((ItemBlock) heldItem.getItem())) {
            if (esp.isToggled()) updateESP();
            return;
        }

        Vec3 targetVec3 = getPlacePossibility(0);
        if (targetVec3 == null) {
            if (esp.isToggled()) updateESP();
            return;
        }
        BlockPos targetPos = new BlockPos(targetVec3.xCoord, targetVec3.yCoord, targetVec3.zCoord);

        if (mc.thePlayer.onGround && Utils.isMoving() && motion.getInput() != 1.0 && !moveFix.isToggled()) {
            strafeMotion(motion.getInput());
        }

        rayCasted = null;
        float searchYaw = 25;
        switch ((int) precision.getInput()) {
            case 0: searchYaw = 35; break;
            case 1: searchYaw = 30; break;
            case 2: break;
            case 3: searchYaw = 15; break;
            case 4: searchYaw = 5; break;
            case 5: searchYaw = 360; break;
        }

        float[] targetRotation = RotationUtils.getRotations(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
        Vec3 eyePos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);

        if (expand.isToggled() && !(tower.isToggled() && Keyboard.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode()))) {
            BlockPos groundPos = new BlockPos(mc.thePlayer).down();
            long expDist = Math.round(expandDistance.getInput());
            for (double j = 0; j < expDist; j += 0.05) {
                BlockPos expPos = getExtendedPos(groundPos, mc.thePlayer.rotationYaw, j);
                if (expPos == null) continue;
                if (!BlockUtils.isReplaceable(expPos)) continue;

                MovingObjectPosition mop = getPlaceSide(expPos);
                if (mop == null) continue;

                double dist = mop.hitVec.distanceTo(eyePos);
                if (dist > expandDistance.getInput()) break;

                rayCasted = mop;
                placeYaw = getYawTo(mop.hitVec);
                placePitch = getPitchTo(mop.hitVec);
                break;
            }
            if (polar.isToggled() && rayCasted == null) {
            }
        }

        if (rayCasted == null) {
            for (int pass = 0; pass < 2 && rayCasted == null; pass++) {
                float[] searchPitch;
                if (pass == 1 && !BlockUtils.isReplaceable(new BlockPos(mc.thePlayer).down())) {
                    searchYaw = 180;
                    searchPitch = new float[]{65, 25};
                } else {
                    searchPitch = new float[]{78, 12};
                    if (pass == 1) break;
                }

                for (float checkYaw = -searchYaw; checkYaw <= searchYaw; checkYaw++) {
                    float fixedYaw = targetRotation[0] - checkYaw;
                    for (float checkPitch = searchPitch[1]; checkPitch >= -searchPitch[0]; checkPitch -= 5) {
                        float fixedPitch = MathHelper.clamp_float(targetRotation[1] + checkPitch, -90, 90);
                        MovingObjectPosition raycast = rayCastCustom(fixedYaw, fixedPitch);
                        if (raycast != null && raycast.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                            if (raycast.getBlockPos().equals(targetPos)) {
                                if (rayCasted == null) {
                                    rayCasted = raycast;
                                    placeYaw = fixedYaw;
                                    placePitch = fixedPitch;
                                    break;
                                }
                            }
                        }
                    }
                    if (rayCasted != null) break;
                }
            }
        }

        if (tower.isToggled() && mc.thePlayer.onGround && mc.thePlayer.isCollidedHorizontally
                && Keyboard.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode())) {
            mc.thePlayer.jump();
        }

        if (activeSchedule.noPlace()) {
            noPlace = true;
        } else {
            noPlace = false;
        }

        if (noPlace) return;

        if (placeBlock != null && fastOnRMB.isToggled() && !Mouse.isButtonDown(1)) return;

        if (rayCasted == null) {
            if (esp.isToggled()) updateESP();
            return;
        }

        placeBlock = rayCasted;
        // Apply rotation to player before placing so server receives correct rotation
        boolean forceStrict = strafe.getInput() > 0 || motion.getInput() != 1.0 || moveFix.isToggled();
        if (!activeSchedule.noRotation()) {
            RotationEvent rotEvent = new RotationEvent(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
            RotationData rotData = activeRotation.onRotation(placeYaw, placePitch, forceStrict, rotEvent);
            rotData = activeSprint.onFinalRotation(rotData);
            if (rotData != null) {
                boolean instant = Math.abs(aimSpeed.getInput() - aimSpeed.getMax()) < 0.001;
                float baseY = lastYaw != null ? lastYaw : mc.thePlayer.rotationYaw;
                float baseP = lastPitch != null ? lastPitch : mc.thePlayer.rotationPitch;
                float finalY = instant ? rotData.getYaw() : AimSimulator.rotMove(rotData.getYaw(), baseY, (float) aimSpeed.getInput());
                float finalP = instant ? rotData.getPitch() : AimSimulator.rotMove(rotData.getPitch(), baseP, (float) aimSpeed.getInput());
                mc.thePlayer.rotationYaw = finalY;
                mc.thePlayer.rotationPitch = finalP;
                mc.thePlayer.prevRotationYaw = finalY;
                mc.thePlayer.prevRotationPitch = finalP;
                RotationUtils.serverRotations[0] = finalY;
                RotationUtils.serverRotations[1] = finalP;
                lastYaw = finalY;
                lastPitch = finalP;
            }
        }
        if (multiPlace.isToggled()) {
            place(placeBlock, true);
        }
        place(placeBlock, false);
        place = false;

        if (esp.isToggled()) {
            highlight.put(placeBlock.getBlockPos().offset(placeBlock.sideHit), System.currentTimeMillis());
        }
    }

    @SubscribeEvent
    public void onClientRotation(ClientRotationEvent event) {
        if (!isEnabled() || mc.thePlayer == null) return;
        if (rayCasted == null) return;
        if (activeSchedule.noRotation()) return;
        boolean forceStrict = strafe.getInput() > 0 || motion.getInput() != 1.0 || moveFix.isToggled();
        float baseY = event.yaw != null ? event.yaw : (lastYaw != null ? lastYaw : mc.thePlayer.rotationYaw);
        float baseP = event.pitch != null ? event.pitch : (lastPitch != null ? lastPitch : mc.thePlayer.rotationPitch);
        RotationEvent rotEvent = new RotationEvent(baseY, baseP);
        RotationData data = activeRotation.onRotation(placeYaw, placePitch, forceStrict, rotEvent);
        data = activeSprint.onFinalRotation(data);
        if (data != null) {
            event.setYaw(data.getYaw());
            event.setPitch(data.getPitch());
        }
    }

    @SubscribeEvent
    public void onSafeWalk(SafeWalkEvent event) {
        if (!isEnabled()) return;
        event.setSafeWalk(safeWalk.isToggled());
    }

    @SubscribeEvent
    public void onSprint(SprintEvent event) {
        if (!isEnabled()) return;
        event.setSprint(activeSprint.isSprint());
        if (!activeSprint.isKeepY()) {
            event.setSprinting(false);
        }
    }

    @SubscribeEvent
    public void onScaffoldPlace(ScaffoldPlaceEvent event) {
        if (!isEnabled()) return;
    }

    private MovingObjectPosition rayCastCustom(float yaw, float pitch) {
        Vec3 eyePos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        Vec3 lookVec = getVectorForRotation(pitch, yaw);
        Vec3 reachVec = eyePos.addVector(lookVec.xCoord * 4.5, lookVec.yCoord * 4.5, lookVec.zCoord * 4.5);
        return mc.theWorld.rayTraceBlocks(eyePos, reachVec, false, false, true);
    }

    private Vec3 getVectorForRotation(float pitch, float yaw) {
        float f = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float f1 = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float f2 = -MathHelper.cos(-pitch * 0.017453292F);
        float f3 = MathHelper.sin(-pitch * 0.017453292F);
        return new Vec3(f1 * f2, f3, f * f2);
    }

    public Vec3 getPlacePossibility(double offsetY) {
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
            double d1 = (mc.thePlayer.posY - 1 + offsetY) - vec3.yCoord;
            double d2 = mc.thePlayer.posZ - vec3.zCoord;
            return MathHelper.sqrt_double(d0 * d0 + d1 * d1 + d2 * d2);
        }));

        return possibilities.get(0);
    }

    private void place(MovingObjectPosition block, boolean extra) {
        if (block == null) return;

        ScaffoldPlaceEvent ev = new ScaffoldPlaceEvent(block, extra);
        MinecraftForge.EVENT_BUS.post(ev);
        block = ev.getHitResult();
        if (block == null) return;

        if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld,
                mc.thePlayer.inventory.getCurrentItem(), block.getBlockPos(),
                block.sideHit, block.hitVec)) {
            if (!extra) {
                totalBlocksPlaced++;
                if (silentSwing.isToggled()) {
                    if (!noSwing.isToggled())
                        mc.thePlayer.sendQueue.addToSendQueue(new C0APacketAnimation());
                } else {
                    mc.thePlayer.swingItem();
                }
            }
        }
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
        return new BlockPos(pos.getX() + Math.round(dx), pos.getY(), pos.getZ() + Math.round(dz));
    }

    private float getYawTo(Vec3 vec) {
        double dx = vec.xCoord - mc.thePlayer.posX;
        double dz = vec.zCoord - mc.thePlayer.posZ;
        return (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
    }

    private float getPitchTo(Vec3 vec) {
        double dx = vec.xCoord - mc.thePlayer.posX;
        double dy = vec.yCoord - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double dz = vec.zCoord - mc.thePlayer.posZ;
        double dist = MathHelper.sqrt_double(dx * dx + dz * dz);
        return (float) -Math.toDegrees(Math.atan2(dy, dist));
    }

    private void strafeMotion(double speed) {
        double yaw = Math.toRadians(getMovementYaw());
        mc.thePlayer.motionX = -MathHelper.sin((float) yaw) * speed;
        mc.thePlayer.motionZ = MathHelper.cos((float) yaw) * speed;
    }

    private float getMovementYaw() {
        float yaw = mc.thePlayer.rotationYaw;
        if (mc.thePlayer.moveForward < 0) yaw += 180;
        float forward = 1;
        if (mc.thePlayer.moveForward < 0) forward = -0.5F;
        else if (mc.thePlayer.moveForward > 0) forward = 0.5F;
        if (mc.thePlayer.moveStrafing > 0) yaw -= 90 * forward;
        if (mc.thePlayer.moveStrafing < 0) yaw += 90 * forward;
        return yaw;
    }

    @SubscribeEvent
    public void onRender(net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent event) {
        if (event.phase != net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END) return;
        if (!Utils.nullCheck() || !showBlockCount.isToggled()) return;
        if (mc.currentScreen != null) return;

        net.minecraft.client.gui.ScaledResolution sr = new net.minecraft.client.gui.ScaledResolution(mc);
        int blocks = totalBlocks();
        String color = "\u00a7";
        if (blocks <= 5) color += "c";
        else if (blocks <= 15) color += "6";
        else if (blocks <= 25) color += "e";
        else color = "";
        mc.fontRendererObj.drawStringWithShadow(
                color + blocks + " \u00a7rblock" + (blocks == 1 ? "" : "s"),
                (float) sr.getScaledWidth() / 2 + 8,
                (float) sr.getScaledHeight() / 2 + 4,
                -1);
    }

    private void updateESP() {
        long now = System.currentTimeMillis();
        highlight.entrySet().removeIf(e -> now - e.getValue() > 750);

        if (esp.isToggled() && placeBlock != null) {
            BlockPos pos = placeBlock.getBlockPos();
            if (outline.isToggled()) {
                net.minecraft.client.renderer.GlStateManager.pushMatrix();
                net.minecraft.client.renderer.GlStateManager.enableBlend();
                net.minecraft.client.renderer.GlStateManager.disableDepth();
                net.minecraft.client.renderer.GlStateManager.disableTexture2D();
                net.minecraft.client.renderer.GlStateManager.disableLighting();
                net.minecraft.client.renderer.GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
                net.minecraft.client.renderer.GlStateManager.color(1.0F, 1.0F, 1.0F, 0.3F);
                double x = pos.getX() - mc.getRenderManager().viewerPosX;
                double y = pos.getY() - mc.getRenderManager().viewerPosY;
                double z = pos.getZ() - mc.getRenderManager().viewerPosZ;
                net.minecraft.client.renderer.RenderGlobal.drawSelectionBoundingBox(
                        new AxisAlignedBB(x, y, z, x + 1, y + 1, z + 1));
                net.minecraft.client.renderer.GlStateManager.enableLighting();
                net.minecraft.client.renderer.GlStateManager.enableTexture2D();
                net.minecraft.client.renderer.GlStateManager.enableDepth();
                net.minecraft.client.renderer.GlStateManager.disableBlend();
                net.minecraft.client.renderer.GlStateManager.popMatrix();
            }
        }
    }

    public int totalBlocks() {
        if (!Utils.nullCheck()) return 0;
        try {
            int total = 0;
            for (int i = 0; i < 9; ++i) {
                ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
                if (stack != null && stack.getItem() instanceof ItemBlock && stack.stackSize > 0) {
                    total += stack.stackSize;
                }
            }
            return total;
        } catch (Exception e) {
            return 0;
        }
    }

    public static boolean shouldScaffoldSafeWalk() {
        return ModuleManager.scaffold != null && ModuleManager.scaffold.isEnabled()
                && ModuleManager.scaffold.safeWalk.isToggled();
    }

    public boolean isDiagonalScaffoldEnabled() {
        return false;
    }

    public boolean isActivelyScaffolding() {
        return isEnabled() && placeBlock != null;
    }

    public boolean isTowering() {
        return isEnabled() && tower.isToggled()
                && mc.thePlayer.onGround
                && mc.thePlayer.isCollidedHorizontally
                && org.lwjgl.input.Keyboard.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode());
    }
}
