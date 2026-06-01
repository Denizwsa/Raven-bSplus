package keystrokesmod.module.impl.world.scaffold.sprint;

import keystrokesmod.module.impl.world.scaffold.IScaffoldSprint;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.aim.RotationData;
import net.minecraft.util.MathHelper;

public class LegitSprint implements IScaffoldSprint {
    @Override
    public String getName() { return "Legit"; }
    @Override
    public String getPrettyName() { return "Legit"; }
    @Override public void onEnable() {}
    @Override public void onDisable() {}
    @Override
    public boolean isSprint() {
        return Math.abs(MathHelper.wrapAngleTo180_float(net.minecraft.client.Minecraft.getMinecraft().thePlayer.rotationYaw)
                - MathHelper.wrapAngleTo180_float(RotationUtils.serverRotations[0])) <= 90;
    }
    @Override public boolean isKeepY() { return false; }
    @Override public RotationData onFinalRotation(RotationData rotation) { return rotation; }
}
