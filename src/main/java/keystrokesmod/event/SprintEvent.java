package keystrokesmod.event;

import net.minecraftforge.fml.common.eventhandler.Event;

public class SprintEvent extends Event {
    private boolean sprint;
    private boolean sprinting;

    public boolean isSprint() {
        return sprint;
    }

    public void setSprint(boolean sprint) {
        this.sprint = sprint;
    }

    public boolean isSprinting() {
        return sprinting;
    }

    public void setSprinting(boolean sprinting) {
        this.sprinting = sprinting;
    }
}
