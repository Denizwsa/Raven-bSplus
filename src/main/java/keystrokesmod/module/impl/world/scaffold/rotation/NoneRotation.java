package keystrokesmod.module.impl.world.scaffold.rotation;

import keystrokesmod.event.RotationEvent;
import keystrokesmod.module.impl.world.scaffold.IScaffoldRotation;
import keystrokesmod.utility.aim.RotationData;

public class NoneRotation implements IScaffoldRotation {
    @Override
    public String getName() {
        return "None";
    }

    @Override
    public String getPrettyName() {
        return "None";
    }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {}

    @Override
    public RotationData onRotation(float placeYaw, float placePitch, boolean forceStrict, RotationEvent event) {
        return new RotationData(event.getYaw(), event.getPitch());
    }
}
