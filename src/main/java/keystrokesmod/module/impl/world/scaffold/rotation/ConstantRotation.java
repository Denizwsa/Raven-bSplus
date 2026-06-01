package keystrokesmod.module.impl.world.scaffold.rotation;

import keystrokesmod.event.RotationEvent;
import keystrokesmod.module.impl.world.scaffold.IScaffoldRotation;
import keystrokesmod.utility.aim.RotationData;

public class ConstantRotation implements IScaffoldRotation {
    @Override
    public String getName() {
        return "Constant";
    }

    @Override
    public String getPrettyName() {
        return "Constant";
    }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {}

    @Override
    public RotationData onRotation(float placeYaw, float placePitch, boolean forceStrict, RotationEvent event) {
        float yaw = event.getYaw();
        float pitch = event.getPitch();
        float diffYaw = ((placeYaw - yaw) % 360 + 540) % 360 - 180;
        float diffPitch = placePitch - pitch;
        int speed = forceStrict ? 180 : 90;
        if (Math.abs(diffYaw) < 0.5f && Math.abs(diffPitch) < 0.5f) {
            return new RotationData(placeYaw, placePitch);
        }
        float newYaw = yaw;
        if (Math.abs(diffYaw) > 0) {
            newYaw += Math.signum(diffYaw) * Math.min(Math.abs(diffYaw), speed);
        }
        float newPitch = pitch;
        if (Math.abs(diffPitch) > 0) {
            newPitch += Math.signum(diffPitch) * Math.min(Math.abs(diffPitch), speed);
        }
        return new RotationData(newYaw, newPitch);
    }
}
