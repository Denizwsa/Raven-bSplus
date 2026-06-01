package keystrokesmod.module.impl.world.scaffold;

public interface IScaffoldSchedule {
    String getName();
    String getPrettyName();
    void onEnable();
    void onDisable();
    boolean noRotation();
    boolean noPlace();
}
