package keystrokesmod.module.impl.world.scaffold.rotation;

import keystrokesmod.event.RotationEvent;
import keystrokesmod.module.impl.world.scaffold.IScaffoldRotation;
import keystrokesmod.utility.aim.RotationData;

public class StrictRotation implements IScaffoldRotation {
    @Override
    public String getName() {
        return "Strict";
    }

    @Override
    public String getPrettyName() {
        return "Strict";
    }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {}

    @Override
    public RotationData onRotation(float placeYaw, float placePitch, boolean forceStrict, RotationEvent event) {
        return new RotationData(placeYaw, placePitch);
    }
}
