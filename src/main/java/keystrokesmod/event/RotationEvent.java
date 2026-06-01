package keystrokesmod.event;

import net.minecraftforge.fml.common.eventhandler.Event;

public class RotationEvent extends Event {
    private float yaw;
    private float pitch;
    private boolean moveFix;
    private MoveFixType moveFixType = MoveFixType.None;

    public RotationEvent(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public boolean isMoveFix() {
        return moveFix;
    }

    @Deprecated
    public void setMoveFix(boolean moveFix) {
        this.moveFix = moveFix;
    }

    public void setMoveFix(MoveFixType type) {
        this.moveFixType = type;
    }

    public MoveFixType getMoveFix() {
        return moveFixType;
    }

    public enum MoveFixType {
        None,
        Silent,
        Normal
    }
}
