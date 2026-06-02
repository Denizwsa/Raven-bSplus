package keystrokesmod.module.impl.player;

import keystrokesmod.event.PreSlotScrollEvent;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.InventoryItemListSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.ItemSearchIndex;
import keystrokesmod.utility.Utils;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Inventory extends Module {
    private static final int HOTBAR_SIZE = InventoryPlayer.getHotbarSize();
    private static final double QUALITY_EPSILON = 1.0E-6D;

    private static final Comparator<PlannedAction> ACTION_COMPARATOR = new Comparator<PlannedAction>() {
        @Override
        public int compare(PlannedAction first, PlannedAction second) {
            int comparison = Integer.compare(first.clickCount, second.clickCount);
            if (comparison != 0) {
                return comparison;
            }

            comparison = Boolean.compare(second.completesTarget, first.completesTarget);
            if (comparison != 0) {
                return comparison;
            }

            comparison = Integer.compare(first.priorityIndex, second.priorityIndex);
            if (comparison != 0) {
                return comparison;
            }

            comparison = Integer.compare(first.displacesCorrectHotbar, second.displacesCorrectHotbar);
            if (comparison != 0) {
                return comparison;
            }

            comparison = Integer.compare(second.resultingSize, first.resultingSize);
            if (comparison != 0) {
                return comparison;
            }

            comparison = Integer.compare(first.targetHotbarSlot, second.targetHotbarSlot);
            if (comparison != 0) {
                return comparison;
            }

            comparison = Integer.compare(first.primarySourceIndex, second.primarySourceIndex);
            if (comparison != 0) {
                return comparison;
            }

            return Integer.compare(first.type.ordinal(), second.type.ordinal());
        }
    };

    private final SliderSetting targetCPS;
    private final ButtonSetting disableWhenComplete;
    private final ButtonSetting stackItems;
    private final InventoryItemListSetting items;

    private PlannedAction currentAction;
    private int cursorRecoveryInventoryIndex = -1;
    private double windowClickBudget;
    private boolean sessionOpen;
    private SessionState sessionState = SessionState.ACTIVE;
    public Inventory() {
        super("Inventory", category.player);
        this.registerSetting(targetCPS = new SliderSetting("Target CPS", 10.0, 1.0, 20.0, 0.5));
        this.registerSetting(disableWhenComplete = new ButtonSetting("Disable when complete", false));
        this.registerSetting(stackItems = new ButtonSetting("Stack items", false));
        this.registerSetting(items = new InventoryItemListSetting("Items"));
        this.closetModule = true;
    }

    @Override
    public void onEnable() {
        resetRuntimeState();
    }

    @Override
    public void onDisable() {
        if (isManagedInventoryOpen() && ownsCarriedStack()) {
            recoverCarriedStackImmediately(InventorySnapshot.capture(), false);
        }
        resetRuntimeState();
    }

    @Override
    public void guiButtonToggled(ButtonSetting buttonSetting) {
        if (buttonSetting != disableWhenComplete || !isManagedInventoryOpen()) {
            return;
        }

        if (!disableWhenComplete.isToggled()) {
            if (sessionState == SessionState.COMPLETE_LATCHED) {
                sessionState = SessionState.ACTIVE;
            }
            return;
        }

        if (currentAction == null && cursorRecoveryInventoryIndex < 0) {
            InventorySnapshot snapshot = InventorySnapshot.capture();
            SnapshotContext context = SnapshotContext.create(snapshot, resolveAssignments(snapshot));
            if (findBestSortingAction(context) == null) {
                sessionState = SessionState.COMPLETE_LATCHED;
            }
        }
    }

    @SubscribeEvent
    public void onSlotScroll(PreSlotScrollEvent event) {
        if (shouldCancelManualInventoryInput()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent event) {
        if (!Utils.nullCheck()) {
            closeSession();
            return;
        }

        if (!isManagedInventoryOpen()) {
            closeSession();
            return;
        }

        InventorySnapshot snapshot = InventorySnapshot.capture();
        if (!sessionOpen) {
            openSession(snapshot);
        }

        if (sessionState == SessionState.COMPLETE_LATCHED) {
            return;
        }

        int clickBudget = consumeWindowClickBudget();
        if (clickBudget <= 0) {
            return;
        }

        while (clickBudget > 0) {
            SlotAssignment[] assignments = resolveAssignments(snapshot);
            SnapshotContext context = SnapshotContext.create(snapshot, assignments);
            if (currentAction == null) {
                if (snapshot.carried != null) {
                    currentAction = buildRecoveryAction(context, PlanType.RECOVER_CURSOR, false);
                    if (currentAction == null) {
                        sessionState = SessionState.ACTIVE;
                        return;
                    }
                    sessionState = SessionState.EXECUTING;
                }
                else {
                    cursorRecoveryInventoryIndex = -1;
                    currentAction = findBestSortingAction(context);
                    if (currentAction == null) {
                        sessionState = disableWhenComplete.isToggled() ? SessionState.COMPLETE_LATCHED : SessionState.ACTIVE;
                        return;
                    }
                    sessionState = SessionState.EXECUTING;
                }
            }

            if (!executeCurrentStep(context)) {
                return;
            }

            clickBudget--;
            snapshot = InventorySnapshot.capture();
        }
    }

    public boolean shouldCancelManualInventoryInput() {
        return isEnabled()
            && isManagedInventoryOpen()
            && sessionState == SessionState.EXECUTING
            && currentAction != null;
    }

    public void handlePreInventoryClose(String source) {
        if (!isEnabled() || !Utils.nullCheck() || !isManagedInventoryOpen() || !ownsCarriedStack()) {
            return;
        }

        InventorySnapshot snapshot = InventorySnapshot.capture();
        if (snapshot.carried == null) {
            return;
        }

        recoverCarriedStackImmediately(snapshot, true);
    }

    private void resetRuntimeState() {
        currentAction = null;
        cursorRecoveryInventoryIndex = -1;
        windowClickBudget = 0.0;
        sessionOpen = false;
        sessionState = SessionState.ACTIVE;
    }

    private void openSession(InventorySnapshot snapshot) {
        sessionOpen = true;
        currentAction = null;
        cursorRecoveryInventoryIndex = -1;
        windowClickBudget = 0.0;
        sessionState = SessionState.ACTIVE;

        if (disableWhenComplete.isToggled()) {
            SnapshotContext context = SnapshotContext.create(snapshot, resolveAssignments(snapshot));
            if (findBestSortingAction(context) == null) {
                sessionState = SessionState.COMPLETE_LATCHED;
            }
        }
    }

    private void closeSession() {
        sessionOpen = false;
        currentAction = null;
        cursorRecoveryInventoryIndex = -1;
        windowClickBudget = 0.0;
        sessionState = SessionState.ACTIVE;
    }

    private boolean ownsCarriedStack() {
        return currentAction != null || cursorRecoveryInventoryIndex >= 0;
    }

    private int consumeWindowClickBudget() {
        windowClickBudget += Math.max(0.0, targetCPS.getInput()) / 20.0;
        int clicks = Math.min(1, (int) windowClickBudget);
        if (clicks <= 0) {
            return 0;
        }

        windowClickBudget -= clicks;
        windowClickBudget = Math.min(windowClickBudget, 1.0);
        return clicks;
    }

    private boolean executeCurrentStep(SnapshotContext context) {
        if (currentAction == null) {
            return false;
        }

        PlannedClick step = currentAction.peek();
        if (step == null) {
            currentAction = null;
            sessionState = SessionState.ACTIVE;
            return false;
        }

        if (!step.validator.isValid(context, this)) {
            currentAction = null;
            sessionState = SessionState.ACTIVE;

            if (context.snapshot.carried != null && cursorRecoveryInventoryIndex >= 0) {
                PlannedAction recoveryAction = buildRecoveryAction(context, PlanType.RECOVER_CURSOR, false);
                if (recoveryAction != null) {
                    currentAction = recoveryAction;
                    sessionState = SessionState.EXECUTING;
                    return executeCurrentStep(context);
                }
            }

            if (context.snapshot.carried == null) {
                cursorRecoveryInventoryIndex = -1;
            }
            return false;
        }

        step.beforeExecute.run(this);
        click(step.slotId, step.button, step.mode);
        step.afterExecute.run(this);
        currentAction.advance();

        if (currentAction.isComplete()) {
            currentAction = null;
            if (sessionState != SessionState.COMPLETE_LATCHED) {
                sessionState = SessionState.ACTIVE;
            }
        }

        return true;
    }

    private PlannedAction findBestSortingAction(SnapshotContext context) {
        PlannedAction bestAction = null;

        for (SlotAssignment assignment : context.assignments) {
            if (assignment == null) {
                continue;
            }

            ItemStack targetStack = context.snapshot.getSlot(assignment.hotbarSlot);
            boolean targetMatchesRule = isAssignedTargetSatisfied(context, assignment, targetStack);

            if (targetMatchesRule && isPartialStack(targetStack)) {
                PlannedAction mergeAction = buildMergeAction(context, assignment, targetStack);
                bestAction = pickBetterAction(bestAction, mergeAction);

                PlannedAction pickupAllAction = buildPickupAllAction(context, assignment, targetStack);
                bestAction = pickBetterAction(bestAction, pickupAllAction);
            }
            else if (!targetMatchesRule) {
                PlannedAction placementAction = buildPlacementAction(context, assignment);
                PlannedAction pickupAllPlacementAction = buildPickupAllPlacementAction(context, assignment, targetStack);

                if (pickupAllPlacementAction != null && (placementAction == null || pickupAllPlacementAction.resultingSize > placementAction.resultingSize)) {
                    bestAction = pickBetterAction(bestAction, pickupAllPlacementAction);
                }
                else if (placementAction != null) {
                    bestAction = pickBetterAction(bestAction, placementAction);
                }
            }
        }

        if (bestAction != null || !stackItems.isToggled()) {
            return bestAction;
        }

        return buildUnconfiguredStackAction(context);
    }

    private PlannedAction buildPlacementAction(SnapshotContext context, SlotAssignment assignment) {
        PlacementSource bestSource = findBestPlacementSource(context, assignment, assignment.hotbarSlot);

        if (bestSource == null) {
            return null;
        }

        final int sourceInventoryIndex = bestSource.inventoryIndex;
        final SlotAssignment selectedAssignment = assignment;
        List<PlannedClick> steps = new ArrayList<PlannedClick>(1);
        steps.add(new PlannedClick(
            toContainerSlot(sourceInventoryIndex),
            selectedAssignment.hotbarSlot,
            2,
            new StepValidator() {
                @Override
                public boolean isValid(SnapshotContext current, Inventory module) {
                    return current.snapshot.carried == null
                        && ItemSearchIndex.matches(selectedAssignment.storageId, current.snapshot.getSlot(sourceInventoryIndex))
                        && (sourceInventoryIndex >= HOTBAR_SIZE || !module.isCorrectHotbarSlot(current, sourceInventoryIndex));
                }
            },
            NO_OP,
            NO_OP
        ));

        PlannedAction action = new PlannedAction(
            PlanType.PLACE_TO_HOTBAR,
            1,
            assignment.hotbarSlot,
            assignment.priorityIndex,
            bestSource.resultingSize,
            true,
            0,
            sourceInventoryIndex,
            steps
        );
        return action;
    }

    private PlannedAction buildMergeAction(SnapshotContext context, SlotAssignment assignment, ItemStack targetStack) {
        MergePlanData mergePlan = buildMergePlanData(context, assignment, targetStack);
        if (mergePlan == null) {
            return null;
        }

        final int targetHotbarSlot = assignment.hotbarSlot;
        final String storageId = assignment.storageId;
        List<PlannedClick> steps = new ArrayList<PlannedClick>(mergePlan.clickCount);

        for (MergeUse mergeUse : mergePlan.uses) {
            final int sourceInventoryIndex = mergeUse.source.inventoryIndex;

            if (mergeUse.source.type == MergeSourceType.MAIN_INVENTORY) {
                steps.add(new PlannedClick(
                    toContainerSlot(sourceInventoryIndex),
                    0,
                    1,
                    new StepValidator() {
                        @Override
                        public boolean isValid(SnapshotContext current, Inventory module) {
                            ItemStack sourceStack = current.snapshot.getSlot(sourceInventoryIndex);
                            ItemStack currentTarget = current.snapshot.getSlot(targetHotbarSlot);
                            return current.snapshot.carried == null
                                && ItemSearchIndex.matches(storageId, currentTarget)
                                && canStacksMerge(sourceStack, currentTarget)
                                && isPartialStack(currentTarget)
                                && isQuickMoveMergeSafe(current.snapshot, targetHotbarSlot, sourceStack, currentTarget);
                        }
                    },
                    NO_OP,
                    NO_OP
                ));
                continue;
            }

            steps.add(new PlannedClick(
                toContainerSlot(sourceInventoryIndex),
                0,
                0,
                new StepValidator() {
                    @Override
                    public boolean isValid(SnapshotContext current, Inventory module) {
                        ItemStack sourceStack = current.snapshot.getSlot(sourceInventoryIndex);
                        ItemStack currentTarget = current.snapshot.getSlot(targetHotbarSlot);
                        return current.snapshot.carried == null
                            && ItemSearchIndex.matches(storageId, currentTarget)
                            && canStacksMerge(sourceStack, currentTarget)
                            && isPartialStack(currentTarget)
                            && !module.isCorrectHotbarSlot(current, sourceInventoryIndex);
                    }
                },
                new StepHook() {
                    @Override
                    public void run(Inventory module) {
                        module.cursorRecoveryInventoryIndex = sourceInventoryIndex;
                    }
                },
                NO_OP
            ));
            steps.add(new PlannedClick(
                toContainerSlot(targetHotbarSlot),
                0,
                0,
                new StepValidator() {
                    @Override
                    public boolean isValid(SnapshotContext current, Inventory module) {
                        ItemStack currentTarget = current.snapshot.getSlot(targetHotbarSlot);
                        return current.snapshot.carried != null
                            && ItemSearchIndex.matches(storageId, currentTarget)
                            && canStacksMerge(current.snapshot.carried, currentTarget)
                            && isPartialStack(currentTarget);
                    }
                },
                NO_OP,
                mergeUse.returnsRemainder ? NO_OP : new StepHook() {
                    @Override
                    public void run(Inventory module) {
                        module.cursorRecoveryInventoryIndex = -1;
                    }
                }
            ));

            if (mergeUse.returnsRemainder) {
                steps.add(new PlannedClick(
                    toContainerSlot(sourceInventoryIndex),
                    0,
                    0,
                    new StepValidator() {
                        @Override
                        public boolean isValid(SnapshotContext current, Inventory module) {
                            return current.snapshot.carried != null && canDepositToSlot(current.snapshot, sourceInventoryIndex);
                        }
                    },
                    NO_OP,
                    new StepHook() {
                        @Override
                        public void run(Inventory module) {
                            module.cursorRecoveryInventoryIndex = -1;
                        }
                    }
                ));
            }
        }

        PlannedAction action = new PlannedAction(
            PlanType.MERGE_TO_TARGET,
            mergePlan.clickCount,
            assignment.hotbarSlot,
            assignment.priorityIndex,
            mergePlan.resultingSize,
            mergePlan.resultingSize >= targetStack.getMaxStackSize(),
            0,
            mergePlan.primarySourceIndex,
            steps
        );
        return action;
    }

    private PlannedAction buildPickupAllAction(SnapshotContext context, SlotAssignment assignment, ItemStack targetStack) {
        int resultingSize = getPickupAllResultingSize(context, assignment.hotbarSlot, targetStack);
        if (resultingSize <= targetStack.stackSize) {
            return null;
        }

        final int targetHotbarSlot = assignment.hotbarSlot;
        final String storageId = assignment.storageId;
        List<PlannedClick> steps = new ArrayList<PlannedClick>(3);
        steps.add(new PlannedClick(
            toContainerSlot(targetHotbarSlot),
            0,
            0,
            new StepValidator() {
                @Override
                public boolean isValid(SnapshotContext current, Inventory module) {
                    ItemStack currentTarget = current.snapshot.getSlot(targetHotbarSlot);
                    return current.snapshot.carried == null
                        && ItemSearchIndex.matches(storageId, currentTarget)
                        && isPartialStack(currentTarget)
                        && module.getPickupAllResultingSize(current, targetHotbarSlot, currentTarget) > currentTarget.stackSize;
                }
            },
            new StepHook() {
                @Override
                public void run(Inventory module) {
                    module.cursorRecoveryInventoryIndex = targetHotbarSlot;
                }
            },
            NO_OP
        ));
        steps.add(new PlannedClick(
            toContainerSlot(targetHotbarSlot),
            0,
            6,
            new StepValidator() {
                @Override
                public boolean isValid(SnapshotContext current, Inventory module) {
                    return current.snapshot.carried != null
                        && current.snapshot.getSlot(targetHotbarSlot) == null
                        && module.getPickupAllResultingSize(current, targetHotbarSlot, current.snapshot.carried) > current.snapshot.carried.stackSize;
                }
            },
            NO_OP,
            NO_OP
        ));
        steps.add(new PlannedClick(
            toContainerSlot(targetHotbarSlot),
            0,
            0,
            new StepValidator() {
                @Override
                public boolean isValid(SnapshotContext current, Inventory module) {
                    return current.snapshot.carried != null && canDepositToSlot(current.snapshot, targetHotbarSlot);
                }
            },
            NO_OP,
            new StepHook() {
                @Override
                public void run(Inventory module) {
                    module.cursorRecoveryInventoryIndex = -1;
                }
            }
        ));

        PlannedAction action = new PlannedAction(
            PlanType.PICKUP_ALL_TO_TARGET,
            3,
            assignment.hotbarSlot,
            assignment.priorityIndex,
            resultingSize,
            resultingSize >= targetStack.getMaxStackSize(),
            0,
            assignment.hotbarSlot,
            steps
        );
        return action;
    }

    private PlannedAction buildPickupAllPlacementAction(SnapshotContext context, SlotAssignment assignment, ItemStack targetStack) {
        PlacementSource bestSource = null;

        for (int inventoryIndex = 0; inventoryIndex < InventorySnapshot.INVENTORY_SIZE; inventoryIndex++) {
            if (inventoryIndex == assignment.hotbarSlot) {
                continue;
            }

            ItemStack sourceStack = context.snapshot.getSlot(inventoryIndex);
            if (!ItemSearchIndex.matches(assignment.storageId, sourceStack)) {
                continue;
            }

            if (inventoryIndex < HOTBAR_SIZE && isCorrectHotbarSlot(context, inventoryIndex)) {
                continue;
            }

            int resultingSize = getPickupAllResultingSize(context, inventoryIndex, sourceStack);
            if (resultingSize <= sourceStack.stackSize) {
                continue;
            }

            PlacementSource candidate = new PlacementSource(
                inventoryIndex,
                ItemSearchIndex.getMatchQuality(assignment.storageId, sourceStack),
                inventoryIndex < HOTBAR_SIZE ? 1 : 0,
                resultingSize
            );
            if (bestSource == null || candidate.isBetterThan(bestSource)) {
                bestSource = candidate;
            }
        }

        if (bestSource == null) {
            return null;
        }

        final int sourceInventoryIndex = bestSource.inventoryIndex;
        final int targetHotbarSlot = assignment.hotbarSlot;
        final String storageId = assignment.storageId;
        final boolean targetOccupiedByDifferentItem = targetStack != null && !ItemSearchIndex.matches(storageId, targetStack);
        List<PlannedClick> steps = new ArrayList<PlannedClick>(targetOccupiedByDifferentItem ? 4 : 3);

        steps.add(new PlannedClick(
            toContainerSlot(sourceInventoryIndex),
            0,
            0,
            new StepValidator() {
                @Override
                public boolean isValid(SnapshotContext current, Inventory module) {
                    return current.snapshot.carried == null
                        && ItemSearchIndex.matches(storageId, current.snapshot.getSlot(sourceInventoryIndex))
                        && (sourceInventoryIndex >= HOTBAR_SIZE || !module.isCorrectHotbarSlot(current, sourceInventoryIndex));
                }
            },
            new StepHook() {
                @Override
                public void run(Inventory module) {
                    module.cursorRecoveryInventoryIndex = sourceInventoryIndex;
                }
            },
            NO_OP
        ));
        steps.add(new PlannedClick(
            toContainerSlot(sourceInventoryIndex),
            0,
            6,
            new StepValidator() {
                @Override
                public boolean isValid(SnapshotContext current, Inventory module) {
                    return current.snapshot.carried != null
                        && ItemSearchIndex.matches(storageId, current.snapshot.carried)
                        && current.snapshot.getSlot(sourceInventoryIndex) == null
                        && module.getPickupAllResultingSize(current, sourceInventoryIndex, current.snapshot.carried) > current.snapshot.carried.stackSize;
                }
            },
            NO_OP,
            NO_OP
        ));
        steps.add(new PlannedClick(
            toContainerSlot(targetHotbarSlot),
            0,
            0,
            new StepValidator() {
                @Override
                public boolean isValid(SnapshotContext current, Inventory module) {
                    if (current.snapshot.carried == null || !ItemSearchIndex.matches(storageId, current.snapshot.carried)) {
                        return false;
                    }

                    ItemStack currentTarget = current.snapshot.getSlot(targetHotbarSlot);
                    if (currentTarget == null) {
                        return true;
                    }

                    if (ItemSearchIndex.matches(storageId, currentTarget)) {
                        return canStacksMerge(current.snapshot.carried, currentTarget) && isPartialStack(currentTarget);
                    }

                    return true;
                }
            },
            NO_OP,
            targetOccupiedByDifferentItem ? NO_OP : new StepHook() {
                @Override
                public void run(Inventory module) {
                    module.cursorRecoveryInventoryIndex = -1;
                }
            }
        ));

        if (targetOccupiedByDifferentItem) {
            steps.add(new PlannedClick(
                toContainerSlot(sourceInventoryIndex),
                0,
                0,
                new StepValidator() {
                    @Override
                    public boolean isValid(SnapshotContext current, Inventory module) {
                        return current.snapshot.carried != null && canDepositToSlot(current.snapshot, sourceInventoryIndex);
                    }
                },
                NO_OP,
                new StepHook() {
                    @Override
                    public void run(Inventory module) {
                        module.cursorRecoveryInventoryIndex = -1;
                    }
                }
            ));
        }

        PlannedAction action = new PlannedAction(
            PlanType.PICKUP_ALL_TO_TARGET,
            steps.size(),
            assignment.hotbarSlot,
            assignment.priorityIndex,
            bestSource.resultingSize,
            bestSource.resultingSize >= context.snapshot.getSlot(sourceInventoryIndex).getMaxStackSize(),
            0,
            sourceInventoryIndex,
            steps
        );
        return action;
    }

    private PlannedAction buildUnconfiguredStackAction(SnapshotContext context) {
        PlannedAction bestAction = null;

        for (int targetInventoryIndex = 0; targetInventoryIndex < InventorySnapshot.INVENTORY_SIZE; targetInventoryIndex++) {
            ItemStack targetStack = context.snapshot.getSlot(targetInventoryIndex);
            if (!isPartialStack(targetStack) || isManagedStack(targetStack)) {
                continue;
            }

            PlannedAction pickupAllAction = buildUnconfiguredPickupAllAction(context, targetInventoryIndex, targetStack);
            bestAction = pickBetterAction(bestAction, pickupAllAction);
        }

        return bestAction;
    }

    private PlannedAction buildUnconfiguredPickupAllAction(SnapshotContext context, int targetInventoryIndex, ItemStack targetStack) {
        int resultingSize = getUnconfiguredPickupAllResultingSize(context, targetInventoryIndex, targetStack);
        if (resultingSize <= targetStack.stackSize) {
            return null;
        }

        final int selectedTargetInventoryIndex = targetInventoryIndex;
        List<PlannedClick> steps = new ArrayList<PlannedClick>(3);
        steps.add(new PlannedClick(
            toContainerSlot(selectedTargetInventoryIndex),
            0,
            0,
            new StepValidator() {
                @Override
                public boolean isValid(SnapshotContext current, Inventory module) {
                    ItemStack currentTarget = current.snapshot.getSlot(selectedTargetInventoryIndex);
                    return current.snapshot.carried == null
                        && isPartialStack(currentTarget)
                        && !module.isManagedStack(currentTarget)
                        && module.getUnconfiguredPickupAllResultingSize(current, selectedTargetInventoryIndex, currentTarget) > currentTarget.stackSize;
                }
            },
            new StepHook() {
                @Override
                public void run(Inventory module) {
                    module.cursorRecoveryInventoryIndex = selectedTargetInventoryIndex;
                }
            },
            NO_OP
        ));
        steps.add(new PlannedClick(
            toContainerSlot(selectedTargetInventoryIndex),
            0,
            6,
            new StepValidator() {
                @Override
                public boolean isValid(SnapshotContext current, Inventory module) {
                    return current.snapshot.carried != null
                        && !module.isManagedStack(current.snapshot.carried)
                        && current.snapshot.getSlot(selectedTargetInventoryIndex) == null
                        && module.getUnconfiguredPickupAllResultingSize(current, selectedTargetInventoryIndex, current.snapshot.carried) > current.snapshot.carried.stackSize;
                }
            },
            NO_OP,
            NO_OP
        ));
        steps.add(new PlannedClick(
            toContainerSlot(selectedTargetInventoryIndex),
            0,
            0,
            new StepValidator() {
                @Override
                public boolean isValid(SnapshotContext current, Inventory module) {
                    return current.snapshot.carried != null && canDepositToSlot(current.snapshot, selectedTargetInventoryIndex);
                }
            },
            NO_OP,
            new StepHook() {
                @Override
                public void run(Inventory module) {
                    module.cursorRecoveryInventoryIndex = -1;
                }
            }
        ));

        PlannedAction action = new PlannedAction(
            PlanType.STACK_UNCONFIGURED_ITEMS,
            3,
            selectedTargetInventoryIndex,
            Integer.MAX_VALUE,
            resultingSize,
            resultingSize >= targetStack.getMaxStackSize(),
            0,
            selectedTargetInventoryIndex,
            steps
        );
        return action;
    }

    private PlannedAction buildRecoveryAction(SnapshotContext context, PlanType type, boolean allowDrop) {
        RecoveryPlanData recoveryPlan = simulateRecoveryPlan(context.snapshot, cursorRecoveryInventoryIndex, allowDrop);
        if (recoveryPlan == null) {
            return null;
        }

        List<PlannedClick> steps = new ArrayList<PlannedClick>(recoveryPlan.depositIndices.size() + (recoveryPlan.willDrop ? 1 : 0));
        for (int i = 0; i < recoveryPlan.depositIndices.size(); i++) {
            final int depositInventoryIndex = recoveryPlan.depositIndices.get(i);
            final boolean clearsCursor = !recoveryPlan.willDrop && i == recoveryPlan.depositIndices.size() - 1;
            steps.add(new PlannedClick(
                toContainerSlot(depositInventoryIndex),
                0,
                0,
                new StepValidator() {
                    @Override
                    public boolean isValid(SnapshotContext current, Inventory module) {
                        return current.snapshot.carried != null && canDepositToSlot(current.snapshot, depositInventoryIndex);
                    }
                },
                new StepHook() {
                    @Override
                    public void run(Inventory module) {
                        module.cursorRecoveryInventoryIndex = depositInventoryIndex;
                    }
                },
                clearsCursor ? new StepHook() {
                    @Override
                    public void run(Inventory module) {
                        module.cursorRecoveryInventoryIndex = -1;
                    }
                } : NO_OP
            ));
        }

        if (recoveryPlan.willDrop) {
            steps.add(new PlannedClick(
                -999,
                0,
                0,
                new StepValidator() {
                    @Override
                    public boolean isValid(SnapshotContext current, Inventory module) {
                        return current.snapshot.carried != null;
                    }
                },
                NO_OP,
                new StepHook() {
                    @Override
                    public void run(Inventory module) {
                        module.cursorRecoveryInventoryIndex = -1;
                    }
                }
            ));
        }

        PlannedAction action = new PlannedAction(
            type,
            steps.size(),
            Integer.MAX_VALUE,
            Integer.MAX_VALUE,
            0,
            false,
            0,
            recoveryPlan.depositIndices.isEmpty() ? Integer.MAX_VALUE : recoveryPlan.depositIndices.get(0),
            steps
        );
        return action;
    }

    private MergePlanData buildMergePlanData(SnapshotContext context, SlotAssignment assignment, ItemStack targetStack) {
        int room = getRemainingRoom(targetStack);
        if (room <= 0) {
            return null;
        }

        List<MergeSource> sources = collectMergeSources(context, assignment.hotbarSlot, targetStack);
        if (sources.isEmpty()) {
            return null;
        }

        MergePath[] bestPaths = new MergePath[room + 1];
        bestPaths[0] = new MergePath(0, new ArrayList<MergeUse>());

        for (MergeSource source : sources) {
            MergePath[] nextPaths = new MergePath[room + 1];
            System.arraycopy(bestPaths, 0, nextPaths, 0, bestPaths.length);

            for (int added = 0; added <= room; added++) {
                MergePath path = bestPaths[added];
                if (path == null || added >= room) {
                    continue;
                }

                int effectiveAdded = Math.min(room - added, source.stackSize);
                if (effectiveAdded <= 0) {
                    continue;
                }

                boolean returnsRemainder = source.type == MergeSourceType.HOTBAR && source.stackSize > effectiveAdded;
                int extraClicks = source.type == MergeSourceType.MAIN_INVENTORY ? 1 : (returnsRemainder ? 3 : 2);
                MergeUse mergeUse = new MergeUse(source, effectiveAdded, returnsRemainder);
                MergePath candidate = path.append(mergeUse, extraClicks);
                int newAdded = added + effectiveAdded;
                if (isBetterMergePath(candidate, nextPaths[newAdded])) {
                    nextPaths[newAdded] = candidate;
                }
            }

            bestPaths = nextPaths;
        }

        for (int added = room; added > 0; added--) {
            MergePath bestPath = bestPaths[added];
            if (bestPath != null) {
                return new MergePlanData(
                    targetStack.stackSize + added,
                    bestPath.cost,
                    bestPath.uses,
                    bestPath.uses.isEmpty() ? Integer.MAX_VALUE : bestPath.uses.get(0).source.inventoryIndex
                );
            }
        }

        return null;
    }

    private boolean isBetterMergePath(MergePath candidate, MergePath existing) {
        if (candidate == null) {
            return false;
        }

        if (existing == null) {
            return true;
        }

        int comparison = Integer.compare(candidate.cost, existing.cost);
        if (comparison != 0) {
            return comparison < 0;
        }

        comparison = Integer.compare(candidate.uses.size(), existing.uses.size());
        if (comparison != 0) {
            return comparison < 0;
        }

        for (int index = 0; index < candidate.uses.size() && index < existing.uses.size(); index++) {
            MergeUse candidateUse = candidate.uses.get(index);
            MergeUse existingUse = existing.uses.get(index);

            comparison = Integer.compare(candidateUse.source.type.ordinal(), existingUse.source.type.ordinal());
            if (comparison != 0) {
                return comparison < 0;
            }

            comparison = Integer.compare(candidateUse.effectiveAdded, existingUse.effectiveAdded);
            if (comparison != 0) {
                return comparison > 0;
            }

            comparison = Integer.compare(candidateUse.source.inventoryIndex, existingUse.source.inventoryIndex);
            if (comparison != 0) {
                return comparison < 0;
            }
        }

        return false;
    }

    private List<MergeSource> collectMergeSources(SnapshotContext context, int targetHotbarSlot, ItemStack targetStack) {
        List<MergeSource> sources = new ArrayList<MergeSource>();

        for (int inventoryIndex = 0; inventoryIndex < InventorySnapshot.INVENTORY_SIZE; inventoryIndex++) {
            if (inventoryIndex == targetHotbarSlot) {
                continue;
            }

            ItemStack sourceStack = context.snapshot.getSlot(inventoryIndex);
            if (!canStacksMerge(sourceStack, targetStack)) {
                continue;
            }

            if (inventoryIndex < HOTBAR_SIZE) {
                if (!isCorrectHotbarSlot(context, inventoryIndex)) {
                    sources.add(new MergeSource(inventoryIndex, sourceStack.stackSize, MergeSourceType.HOTBAR));
                }
                continue;
            }

            if (isQuickMoveMergeSafe(context.snapshot, targetHotbarSlot, sourceStack, targetStack)) {
                sources.add(new MergeSource(inventoryIndex, sourceStack.stackSize, MergeSourceType.MAIN_INVENTORY));
            }
        }

        sources.sort(new Comparator<MergeSource>() {
            @Override
            public int compare(MergeSource first, MergeSource second) {
                int comparison = Integer.compare(first.type.ordinal(), second.type.ordinal());
                if (comparison != 0) {
                    return comparison;
                }

                comparison = Integer.compare(second.stackSize, first.stackSize);
                if (comparison != 0) {
                    return comparison;
                }

                return Integer.compare(first.inventoryIndex, second.inventoryIndex);
            }
        });
        return sources;
    }

    private int getPickupAllResultingSize(SnapshotContext context, int targetHotbarSlot, ItemStack targetStack) {
        if (!isPartialStack(targetStack)) {
            return 0;
        }

        int mergedSize = targetStack.stackSize;
        for (int inventoryIndex = 0; inventoryIndex < InventorySnapshot.INVENTORY_SIZE && mergedSize < targetStack.getMaxStackSize(); inventoryIndex++) {
            if (inventoryIndex == targetHotbarSlot) {
                continue;
            }

            ItemStack sourceStack = context.snapshot.getSlot(inventoryIndex);
            if (!canStacksMerge(sourceStack, targetStack)) {
                continue;
            }

            if (inventoryIndex < HOTBAR_SIZE && isCorrectHotbarSlot(context, inventoryIndex)) {
                return 0;
            }

            mergedSize = Math.min(targetStack.getMaxStackSize(), mergedSize + sourceStack.stackSize);
        }

        return mergedSize;
    }

    private int getUnconfiguredPickupAllResultingSize(SnapshotContext context, int targetInventoryIndex, ItemStack targetStack) {
        if (!isPartialStack(targetStack) || isManagedStack(targetStack)) {
            return 0;
        }

        int mergedSize = targetStack.stackSize;
        for (int inventoryIndex = 0; inventoryIndex < InventorySnapshot.INVENTORY_SIZE && mergedSize < targetStack.getMaxStackSize(); inventoryIndex++) {
            if (inventoryIndex == targetInventoryIndex) {
                continue;
            }

            ItemStack sourceStack = context.snapshot.getSlot(inventoryIndex);
            if (sourceStack == null || isManagedStack(sourceStack) || !isPartialStack(sourceStack) || !canStacksMerge(sourceStack, targetStack)) {
                continue;
            }

            mergedSize = Math.min(targetStack.getMaxStackSize(), mergedSize + sourceStack.stackSize);
        }

        return mergedSize;
    }

    private void recoverCarriedStackImmediately(InventorySnapshot snapshot, boolean allowDrop) {
        if (!(mc.thePlayer.openContainer instanceof ContainerPlayer) || snapshot.carried == null) {
            currentAction = null;
            if (allowDrop) {
                cursorRecoveryInventoryIndex = -1;
            }
            if (sessionState != SessionState.COMPLETE_LATCHED) {
                sessionState = SessionState.ACTIVE;
            }
            return;
        }

        int preferredIndex = cursorRecoveryInventoryIndex;
        int remainingAttempts = InventorySnapshot.INVENTORY_SIZE + 2;
        while (snapshot.carried != null && remainingAttempts-- > 0) {
            int depositIndex = findRecoveryDepositIndex(snapshot, preferredIndex);
            if (depositIndex < 0) {
                break;
            }

            cursorRecoveryInventoryIndex = depositIndex;
            click(toContainerSlot(depositIndex), 0, 0);
            preferredIndex = -1;
            snapshot = InventorySnapshot.capture();
        }

        if (snapshot.carried != null && allowDrop) {
            click(-999, 0, 0);
            snapshot = InventorySnapshot.capture();
        }

        currentAction = null;
        if (snapshot.carried == null || allowDrop) {
            cursorRecoveryInventoryIndex = -1;
        }
        if (sessionState != SessionState.COMPLETE_LATCHED) {
            sessionState = SessionState.ACTIVE;
        }
    }

    private RecoveryPlanData simulateRecoveryPlan(InventorySnapshot snapshot, int preferredIndex, boolean allowDrop) {
        if (snapshot.carried == null) {
            return null;
        }

        SimulatedInventory simulated = new SimulatedInventory(snapshot);
        List<Integer> depositIndices = new ArrayList<Integer>();
        int remainingAttempts = InventorySnapshot.INVENTORY_SIZE + 1;
        int preferredRecoveryIndex = preferredIndex;

        while (simulated.getCarried() != null && remainingAttempts-- > 0) {
            int depositIndex = findRecoveryDepositIndex(simulated, preferredRecoveryIndex);
            if (depositIndex < 0) {
                break;
            }

            depositIndices.add(depositIndex);
            simulated.deposit(depositIndex);
            preferredRecoveryIndex = -1;
        }

        if (simulated.getCarried() != null && !allowDrop) {
            return null;
        }

        boolean willDrop = simulated.getCarried() != null;
        if (depositIndices.isEmpty() && !willDrop) {
            return null;
        }

        return new RecoveryPlanData(depositIndices, willDrop);
    }

    private int findRecoveryDepositIndex(RecoveryView view, int preferredIndex) {
        if (preferredIndex >= 0 && canDepositToSlot(view, preferredIndex)) {
            return preferredIndex;
        }

        for (int inventoryIndex = HOTBAR_SIZE; inventoryIndex < InventorySnapshot.INVENTORY_SIZE; inventoryIndex++) {
            if (canMergeIntoSlot(view, inventoryIndex)) {
                return inventoryIndex;
            }
        }

        for (int inventoryIndex = 0; inventoryIndex < HOTBAR_SIZE; inventoryIndex++) {
            if (canMergeIntoSlot(view, inventoryIndex)) {
                return inventoryIndex;
            }
        }

        for (int inventoryIndex = HOTBAR_SIZE; inventoryIndex < InventorySnapshot.INVENTORY_SIZE; inventoryIndex++) {
            if (view.getSlot(inventoryIndex) == null) {
                return inventoryIndex;
            }
        }

        for (int inventoryIndex = 0; inventoryIndex < HOTBAR_SIZE; inventoryIndex++) {
            if (view.getSlot(inventoryIndex) == null) {
                return inventoryIndex;
            }
        }

        return -1;
    }

    private SlotAssignment[] resolveAssignments(InventorySnapshot snapshot) {
        SlotAssignment[] assignments = new SlotAssignment[HOTBAR_SIZE];
        List<String> orderedItems = items.getItems();

        for (int priorityIndex = 0; priorityIndex < orderedItems.size(); priorityIndex++) {
            String storageId = orderedItems.get(priorityIndex);
            Integer assignedSlot = items.getAssignedSlot(storageId);
            if (assignedSlot == null) {
                continue;
            }

            int hotbarSlot = assignedSlot - 1;
            if (hotbarSlot < 0 || hotbarSlot >= HOTBAR_SIZE || assignments[hotbarSlot] != null) {
                continue;
            }

            if (hasMatchingStack(snapshot, storageId)) {
                assignments[hotbarSlot] = new SlotAssignment(hotbarSlot, storageId, priorityIndex);
            }
        }

        return assignments;
    }

    private boolean hasMatchingStack(InventorySnapshot snapshot, String storageId) {
        for (int inventoryIndex = 0; inventoryIndex < InventorySnapshot.INVENTORY_SIZE; inventoryIndex++) {
            if (ItemSearchIndex.matches(storageId, snapshot.getSlot(inventoryIndex))) {
                return true;
            }
        }
        return false;
    }

    private boolean isCorrectHotbarSlot(SnapshotContext context, int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot >= HOTBAR_SIZE) {
            return false;
        }

        SlotAssignment assignment = context.assignments[hotbarSlot];
        return assignment != null && ItemSearchIndex.matches(assignment.storageId, context.snapshot.getSlot(hotbarSlot));
    }

    private boolean isManagedStack(ItemStack stack) {
        return stack != null && items.matches(stack);
    }

    private boolean isAssignedTargetSatisfied(SnapshotContext context, SlotAssignment assignment, ItemStack targetStack) {
        if (!ItemSearchIndex.matches(assignment.storageId, targetStack)) {
            return false;
        }

        PlacementSource betterSource = findBestPlacementSource(context, assignment, assignment.hotbarSlot);
        if (betterSource == null) {
            return true;
        }

        double targetQuality = ItemSearchIndex.getMatchQuality(assignment.storageId, targetStack);
        return betterSource.quality <= targetQuality + QUALITY_EPSILON;
    }

    private PlacementSource findBestPlacementSource(SnapshotContext context, SlotAssignment assignment, int excludedInventoryIndex) {
        PlacementSource bestSource = null;

        for (int inventoryIndex = 0; inventoryIndex < InventorySnapshot.INVENTORY_SIZE; inventoryIndex++) {
            if (inventoryIndex == excludedInventoryIndex) {
                continue;
            }

            ItemStack sourceStack = context.snapshot.getSlot(inventoryIndex);
            if (!ItemSearchIndex.matches(assignment.storageId, sourceStack)) {
                continue;
            }

            if (inventoryIndex < HOTBAR_SIZE && isCorrectHotbarSlot(context, inventoryIndex)) {
                continue;
            }

            PlacementSource candidate = new PlacementSource(
                inventoryIndex,
                ItemSearchIndex.getMatchQuality(assignment.storageId, sourceStack),
                inventoryIndex < HOTBAR_SIZE ? 1 : 0,
                sourceStack != null ? sourceStack.stackSize : 0
            );
            if (bestSource == null || candidate.isBetterThan(bestSource)) {
                bestSource = candidate;
            }
        }

        return bestSource;
    }

    private static PlannedAction pickBetterAction(PlannedAction currentBest, PlannedAction candidate) {
        if (candidate == null) {
            return currentBest;
        }

        return currentBest == null || ACTION_COMPARATOR.compare(candidate, currentBest) < 0 ? candidate : currentBest;
    }

    private void click(int slotId, int button, int mode) {
        mc.playerController.windowClick(mc.thePlayer.openContainer.windowId, slotId, button, mode, mc.thePlayer);
    }

    private boolean isManagedInventoryOpen() {
        return Utils.nullCheck()
            && mc.currentScreen instanceof GuiInventory
            && mc.thePlayer.openContainer instanceof ContainerPlayer
            && Utils.inInventory();
    }

    private static boolean isQuickMoveMergeSafe(InventorySnapshot snapshot, int targetHotbarSlot, ItemStack sourceStack, ItemStack targetStack) {
        if (!canStacksMerge(sourceStack, targetStack) || !isPartialStack(targetStack)) {
            return false;
        }

        for (int hotbarSlot = 0; hotbarSlot < HOTBAR_SIZE; hotbarSlot++) {
            if (hotbarSlot == targetHotbarSlot) {
                continue;
            }

            ItemStack hotbarStack = snapshot.getSlot(hotbarSlot);
            if (hotbarStack != null && canStacksMerge(hotbarStack, sourceStack) && isPartialStack(hotbarStack) && hotbarSlot < targetHotbarSlot) {
                return false;
            }
        }

        return true;
    }

    private static boolean canDepositToSlot(RecoveryView view, int inventoryIndex) {
        ItemStack slotStack = view.getSlot(inventoryIndex);
        return slotStack == null || canMergeIntoSlot(view, inventoryIndex);
    }

    private static boolean canMergeIntoSlot(RecoveryView view, int inventoryIndex) {
        ItemStack slotStack = view.getSlot(inventoryIndex);
        ItemStack carried = view.getCarried();
        return canStacksMerge(slotStack, carried) && isPartialStack(slotStack);
    }

    private static boolean canStacksMerge(ItemStack first, ItemStack second) {
        if (first == null || second == null || first.getItem() != second.getItem()) {
            return false;
        }
        if (first.getHasSubtypes() && first.getMetadata() != second.getMetadata()) {
            return false;
        }
        return ItemStack.areItemStackTagsEqual(first, second);
    }

    private static boolean isPartialStack(ItemStack stack) {
        return stack != null && stack.isStackable() && stack.stackSize < stack.getMaxStackSize();
    }

    private static int getRemainingRoom(ItemStack stack) {
        return stack == null ? 0 : Math.max(0, stack.getMaxStackSize() - stack.stackSize);
    }

    private static int toContainerSlot(int inventoryIndex) {
        if (inventoryIndex < 0 || inventoryIndex >= InventorySnapshot.INVENTORY_SIZE) {
            return -1;
        }
        return inventoryIndex < HOTBAR_SIZE ? inventoryIndex + 36 : inventoryIndex;
    }

    private interface StepValidator {
        boolean isValid(SnapshotContext context, Inventory module);
    }

    private interface StepHook {
        void run(Inventory module);
    }

    private interface RecoveryView {
        ItemStack getSlot(int inventoryIndex);

        ItemStack getCarried();
    }

    private static final StepHook NO_OP = new StepHook() {
        @Override
        public void run(Inventory module) {
        }
    };

    private enum SessionState {
        ACTIVE,
        EXECUTING,
        COMPLETE_LATCHED
    }

    private enum PlanType {
        PLACE_TO_HOTBAR,
        MERGE_TO_TARGET,
        PICKUP_ALL_TO_TARGET,
        STACK_UNCONFIGURED_ITEMS,
        RECOVER_CURSOR,
        PRE_CLOSE_RECOVERY
    }

    private enum MergeSourceType {
        MAIN_INVENTORY,
        HOTBAR
    }

    private static final class PlannedClick {
        final int slotId;
        final int button;
        final int mode;
        final StepValidator validator;
        final StepHook beforeExecute;
        final StepHook afterExecute;

        PlannedClick(int slotId, int button, int mode, StepValidator validator, StepHook beforeExecute, StepHook afterExecute) {
            this.slotId = slotId;
            this.button = button;
            this.mode = mode;
            this.validator = validator;
            this.beforeExecute = beforeExecute;
            this.afterExecute = afterExecute;
        }
    }

    private static final class PlannedAction {
        final PlanType type;
        final int clickCount;
        final int targetHotbarSlot;
        final int priorityIndex;
        final int resultingSize;
        final boolean completesTarget;
        final int displacesCorrectHotbar;
        final int primarySourceIndex;
        final List<PlannedClick> steps;
        int nextStepIndex;

        PlannedAction(PlanType type, int clickCount, int targetHotbarSlot, int priorityIndex, int resultingSize, boolean completesTarget, int displacesCorrectHotbar, int primarySourceIndex, List<PlannedClick> steps) {
            this.type = type;
            this.clickCount = clickCount;
            this.targetHotbarSlot = targetHotbarSlot;
            this.priorityIndex = priorityIndex;
            this.resultingSize = resultingSize;
            this.completesTarget = completesTarget;
            this.displacesCorrectHotbar = displacesCorrectHotbar;
            this.primarySourceIndex = primarySourceIndex;
            this.steps = steps;
        }

        PlannedClick peek() {
            return nextStepIndex < steps.size() ? steps.get(nextStepIndex) : null;
        }

        void advance() {
            nextStepIndex++;
        }

        boolean isComplete() {
            return nextStepIndex >= steps.size();
        }
    }

    private static final class PlacementSource {
        final int inventoryIndex;
        final double quality;
        final int sourcePreference;
        final int resultingSize;

        PlacementSource(int inventoryIndex, double quality, int sourcePreference, int resultingSize) {
            this.inventoryIndex = inventoryIndex;
            this.quality = quality;
            this.sourcePreference = sourcePreference;
            this.resultingSize = resultingSize;
        }

        boolean isBetterThan(PlacementSource other) {
            int comparison = Double.compare(quality, other.quality);
            if (Math.abs(quality - other.quality) > QUALITY_EPSILON) {
                return comparison > 0;
            }

            comparison = Integer.compare(sourcePreference, other.sourcePreference);
            if (comparison != 0) {
                return comparison < 0;
            }

            comparison = Integer.compare(resultingSize, other.resultingSize);
            if (comparison != 0) {
                return comparison > 0;
            }

            return inventoryIndex < other.inventoryIndex;
        }
    }

    private static final class SlotAssignment {
        final int hotbarSlot;
        final String storageId;
        final int priorityIndex;

        SlotAssignment(int hotbarSlot, String storageId, int priorityIndex) {
            this.hotbarSlot = hotbarSlot;
            this.storageId = storageId;
            this.priorityIndex = priorityIndex;
        }
    }

    private static final class SnapshotContext {
        final InventorySnapshot snapshot;
        final SlotAssignment[] assignments;

        private SnapshotContext(InventorySnapshot snapshot, SlotAssignment[] assignments) {
            this.snapshot = snapshot;
            this.assignments = assignments;
        }

        static SnapshotContext create(InventorySnapshot snapshot, SlotAssignment[] assignments) {
            return new SnapshotContext(snapshot, assignments);
        }
    }

    private static final class InventorySnapshot implements RecoveryView {
        static final int INVENTORY_SIZE = 36;

        final ItemStack[] slots = new ItemStack[INVENTORY_SIZE];
        final ItemStack carried;

        private InventorySnapshot(ItemStack[] slots, ItemStack carried) {
            System.arraycopy(slots, 0, this.slots, 0, slots.length);
            this.carried = carried;
        }

        static InventorySnapshot capture() {
            ItemStack[] slots = new ItemStack[INVENTORY_SIZE];
            for (int inventoryIndex = 0; inventoryIndex < INVENTORY_SIZE; inventoryIndex++) {
                ItemStack stack = mc.thePlayer.inventory.getStackInSlot(inventoryIndex);
                slots[inventoryIndex] = stack != null ? stack.copy() : null;
            }

            ItemStack carried = mc.thePlayer.inventory.getItemStack();
            return new InventorySnapshot(slots, carried != null ? carried.copy() : null);
        }

        @Override
        public ItemStack getSlot(int inventoryIndex) {
            if (inventoryIndex < 0 || inventoryIndex >= INVENTORY_SIZE) {
                return null;
            }
            return slots[inventoryIndex];
        }

        @Override
        public ItemStack getCarried() {
            return carried;
        }
    }

    private static final class MergeSource {
        final int inventoryIndex;
        final int stackSize;
        final MergeSourceType type;

        MergeSource(int inventoryIndex, int stackSize, MergeSourceType type) {
            this.inventoryIndex = inventoryIndex;
            this.stackSize = stackSize;
            this.type = type;
        }
    }

    private static final class MergeUse {
        final MergeSource source;
        final int effectiveAdded;
        final boolean returnsRemainder;

        MergeUse(MergeSource source, int effectiveAdded, boolean returnsRemainder) {
            this.source = source;
            this.effectiveAdded = effectiveAdded;
            this.returnsRemainder = returnsRemainder;
        }
    }

    private static final class MergePath {
        final int cost;
        final List<MergeUse> uses;

        MergePath(int cost, List<MergeUse> uses) {
            this.cost = cost;
            this.uses = uses;
        }

        MergePath append(MergeUse mergeUse, int extraCost) {
            List<MergeUse> nextUses = new ArrayList<MergeUse>(uses.size() + 1);
            nextUses.addAll(uses);
            nextUses.add(mergeUse);
            return new MergePath(cost + extraCost, nextUses);
        }
    }

    private static final class MergePlanData {
        final int resultingSize;
        final int clickCount;
        final List<MergeUse> uses;
        final int primarySourceIndex;

        MergePlanData(int resultingSize, int clickCount, List<MergeUse> uses, int primarySourceIndex) {
            this.resultingSize = resultingSize;
            this.clickCount = clickCount;
            this.uses = uses;
            this.primarySourceIndex = primarySourceIndex;
        }
    }

    private static final class RecoveryPlanData {
        final List<Integer> depositIndices;
        final boolean willDrop;

        RecoveryPlanData(List<Integer> depositIndices, boolean willDrop) {
            this.depositIndices = depositIndices;
            this.willDrop = willDrop;
        }
    }

    private static final class SimulatedInventory implements RecoveryView {
        final ItemStack[] slots = new ItemStack[InventorySnapshot.INVENTORY_SIZE];
        ItemStack carried;

        SimulatedInventory(InventorySnapshot snapshot) {
            for (int inventoryIndex = 0; inventoryIndex < InventorySnapshot.INVENTORY_SIZE; inventoryIndex++) {
                ItemStack stack = snapshot.getSlot(inventoryIndex);
                slots[inventoryIndex] = stack != null ? stack.copy() : null;
            }
            carried = snapshot.carried != null ? snapshot.carried.copy() : null;
        }

        void deposit(int inventoryIndex) {
            if (carried == null) {
                return;
            }

            ItemStack slotStack = slots[inventoryIndex];
            if (slotStack == null) {
                slots[inventoryIndex] = carried.copy();
                carried = null;
                return;
            }

            if (!canStacksMerge(slotStack, carried)) {
                return;
            }

            int moved = Math.min(getRemainingRoom(slotStack), carried.stackSize);
            slotStack.stackSize += moved;
            carried.stackSize -= moved;
            if (carried.stackSize <= 0) {
                carried = null;
            }
        }

        @Override
        public ItemStack getSlot(int inventoryIndex) {
            if (inventoryIndex < 0 || inventoryIndex >= slots.length) {
                return null;
            }
            return slots[inventoryIndex];
        }

        @Override
        public ItemStack getCarried() {
            return carried;
        }
    }
}
