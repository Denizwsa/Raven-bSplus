package keystrokesmod.event;

import net.minecraftforge.fml.common.eventhandler.Event;

public class SafeWalkEvent extends Event {
    private boolean safeWalk;

    public boolean isSafeWalk() {
        return safeWalk;
    }

    public void setSafeWalk(boolean safeWalk) {
        this.safeWalk = safeWalk;
    }
}
