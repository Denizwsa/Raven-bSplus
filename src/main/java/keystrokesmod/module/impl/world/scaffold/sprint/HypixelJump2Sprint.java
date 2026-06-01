package keystrokesmod.module.impl.world.scaffold.sprint;

import keystrokesmod.module.impl.world.scaffold.IScaffoldSprint;
import keystrokesmod.utility.aim.RotationData;

public class HypixelJump2Sprint implements IScaffoldSprint {
    @Override
    public String getName() { return "Hypixel Jump 2"; }
    @Override
    public String getPrettyName() { return "Hypixel Jump 2"; }
    @Override public void onEnable() {}
    @Override public void onDisable() {}
    @Override public boolean isSprint() { return true; }
    @Override public boolean isKeepY() { return true; }
    @Override public RotationData onFinalRotation(RotationData rotation) { return rotation; }
}
