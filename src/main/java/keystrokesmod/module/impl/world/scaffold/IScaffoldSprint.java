package keystrokesmod.module.impl.world.scaffold;

import keystrokesmod.utility.aim.RotationData;

public interface IScaffoldSprint {
    String getName();
    String getPrettyName();
    void onEnable();
    void onDisable();
    boolean isSprint();
    boolean isKeepY();
    RotationData onFinalRotation(RotationData rotation);
}
