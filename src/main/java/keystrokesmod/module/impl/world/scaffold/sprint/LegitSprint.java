package keystrokesmod.module.impl.world.scaffold.sprint;

import keystrokesmod.module.impl.world.scaffold.IScaffoldSprint;
import keystrokesmod.utility.aim.RotationData;

public class LegitSprint implements IScaffoldSprint {
    @Override
    public String getName() { return "Legit"; }
    @Override
    public String getPrettyName() { return "Legit"; }
    @Override public void onEnable() {}
    @Override public void onDisable() {}
    @Override public boolean isSprint() { return true; }
    @Override public boolean isKeepY() { return true; }
    @Override public RotationData onFinalRotation(RotationData rotation) { return rotation; }
}
