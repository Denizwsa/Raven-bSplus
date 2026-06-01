package keystrokesmod.module.impl.world.scaffold.rotation;

import keystrokesmod.event.RotationEvent;
import keystrokesmod.module.impl.world.scaffold.IScaffoldRotation;
import keystrokesmod.utility.aim.RotationData;
import net.minecraft.client.Minecraft;

public class BackwardsRotation implements IScaffoldRotation {
    private static final Minecraft mc = Minecraft.getMinecraft();

    @Override
    public String getName() {
        return "Backwards";
    }

    @Override
    public String getPrettyName() {
        return "Backwards";
    }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {}

    @Override
    public RotationData onRotation(float placeYaw, float placePitch, boolean forceStrict, RotationEvent event) {
        float yaw = event.getYaw();
        float playerYaw = mc.thePlayer.rotationYaw;
        float diff = ((placeYaw - playerYaw) % 360 + 540) % 360 - 180;
        if (Math.abs(diff) > 90) {
            return new RotationData(placeYaw - 180, placePitch);
        }
        return new RotationData(yaw, event.getPitch());
    }
}
