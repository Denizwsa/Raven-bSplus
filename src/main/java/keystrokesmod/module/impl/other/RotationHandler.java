package keystrokesmod.module.impl.other;

import keystrokesmod.event.PreMotionEvent;
import keystrokesmod.event.RotationEvent;
import keystrokesmod.helper.RotationHelper;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.utility.RotationUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class RotationHandler extends Module {

    private static float serverYaw;
    private static float serverPitch;

    public RotationHandler() {
        super("Rotation Handler", category.other);
        this.registerSetting(new DescriptionSetting("Internal rotation utility"));
        this.ignoreOnSave = true;
        this.hidden = true;
    }

    @SubscribeEvent
    public void onPreUpdate(PreMotionEvent e) {
        if (mc.thePlayer == null) return;
        RotationEvent ev = new RotationEvent(
                e.getYaw(),
                e.getPitch()
        );
        MinecraftForge.EVENT_BUS.post(ev);
        float yaw = ev.getYaw();
        float pitch = ev.getPitch();
        serverYaw = yaw;
        serverPitch = pitch;
        e.setRotations(yaw, pitch);
    }

    public static float getRotationYaw() {
        return serverYaw;
    }

    public static float getRotationPitch() {
        return serverPitch;
    }

    public static float getRotationYaw(float fallback) {
        if (mc.thePlayer == null) return fallback;
        return RotationUtils.serverRotations != null ? RotationUtils.serverRotations[0] : mc.thePlayer.rotationYaw;
    }

    public static float getRotationPitch(float fallback) {
        if (mc.thePlayer == null) return fallback;
        return RotationUtils.serverRotations != null ? RotationUtils.serverRotations[1] : mc.thePlayer.rotationPitch;
    }
}
