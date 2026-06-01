package keystrokesmod.module.impl.world.scaffold.schedule;

import keystrokesmod.module.impl.world.scaffold.IScaffoldSchedule;

public class TellySchedule implements IScaffoldSchedule {
    @Override
    public String getName() { return "Telly"; }
    @Override
    public String getPrettyName() { return "Telly"; }
    @Override public void onEnable() {}
    @Override public void onDisable() {}
    @Override public boolean noRotation() { return false; }
    @Override public boolean noPlace() { return false; }
}
