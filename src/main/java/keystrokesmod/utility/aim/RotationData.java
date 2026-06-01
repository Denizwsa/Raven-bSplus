package keystrokesmod.utility.aim;

public class RotationData {
    private final float yaw;
    private final float pitch;

    public RotationData(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }
}
