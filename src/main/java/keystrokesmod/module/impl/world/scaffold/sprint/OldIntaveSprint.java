package keystrokesmod.module.impl.world.scaffold.sprint;

import keystrokesmod.event.JumpEvent;
import keystrokesmod.module.impl.world.scaffold.IScaffoldSprint;
import keystrokesmod.utility.aim.RotationData;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class OldIntaveSprint implements IScaffoldSprint {
    @Override
    public String getName() { return "Old Intave"; }
    @Override
    public String getPrettyName() { return "Old Intave"; }
    @Override public void onEnable() {}
    @Override public void onDisable() {}
    @Override public boolean isSprint() {
        return !net.minecraft.client.Minecraft.getMinecraft().thePlayer.onGround;
    }
    @Override public boolean isKeepY() { return true; }
    @Override public RotationData onFinalRotation(RotationData rotation) { return rotation; }

    @SubscribeEvent
    public void onJump(JumpEvent event) {
        event.setCanceled(true);
        net.minecraft.client.Minecraft.getMinecraft().thePlayer.motionY = 0.42F;
    }
}
