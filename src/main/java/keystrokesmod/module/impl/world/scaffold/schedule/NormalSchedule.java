package keystrokesmod.module.impl.world.scaffold.schedule;

import keystrokesmod.module.impl.world.scaffold.IScaffoldSchedule;

public class NormalSchedule implements IScaffoldSchedule {
    @Override
    public String getName() { return "Normal"; }
    @Override
    public String getPrettyName() { return "Normal"; }
    @Override public void onEnable() {}
    @Override public void onDisable() {}
    @Override public boolean noRotation() { return false; }
    @Override public boolean noPlace() { return false; }
}
