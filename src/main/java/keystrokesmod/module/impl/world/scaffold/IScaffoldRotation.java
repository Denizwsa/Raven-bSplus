package keystrokesmod.module.impl.world.scaffold;

import keystrokesmod.event.RotationEvent;
import keystrokesmod.utility.aim.RotationData;

public interface IScaffoldRotation {
    String getName();
    String getPrettyName();
    void onEnable();
    void onDisable();
    RotationData onRotation(float placeYaw, float placePitch, boolean forceStrict, RotationEvent event);
}
