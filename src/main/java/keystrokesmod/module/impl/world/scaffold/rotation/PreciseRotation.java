package keystrokesmod.module.impl.world.scaffold.rotation;

import keystrokesmod.event.RotationEvent;
import keystrokesmod.module.impl.world.scaffold.IScaffoldRotation;
import keystrokesmod.utility.aim.AimSimulator;
import keystrokesmod.utility.aim.RotationData;
import net.minecraft.client.Minecraft;

public class PreciseRotation implements IScaffoldRotation {
    private static final Minecraft mc = Minecraft.getMinecraft();

    @Override
    public String getName() {
        return "Precise";
    }

    @Override
    public String getPrettyName() {
        return "Precise";
    }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {}

    @Override
    public RotationData onRotation(float placeYaw, float placePitch, boolean forceStrict, RotationEvent event) {
        float yaw = event.getYaw();
        float pitch = event.getPitch();
        if (mc.thePlayer == null) {
            return new RotationData(placeYaw, placePitch);
        }
        float serverYaw = yaw;
        float serverPitch = pitch;
        float diffYaw = ((placeYaw - serverYaw) % 360 + 540) % 360 - 180;
        float diffPitch = placePitch - serverPitch;
        int speed = forceStrict ? 180 : 45;
        float moveYaw = AimSimulator.rotMove(diffYaw, 0, speed);
        float movePitch = AimSimulator.rotMove(diffPitch, 0, speed);
        if (Math.abs(moveYaw) < 0.1f) moveYaw = diffYaw;
        if (Math.abs(movePitch) < 0.1f) movePitch = diffPitch;
        return new RotationData(serverYaw + moveYaw, serverPitch + movePitch);
    }
}
