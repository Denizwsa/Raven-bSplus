package keystrokesmod.module.impl.movement;

import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.Utils;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class Jump extends Module {
    private final SliderSetting mode;
    private final SliderSetting delay;
    private int tickCounter;

    public Jump() {
        super("Jump", category.movement);
        this.registerSetting(mode = new SliderSetting("Mode", 0, new String[]{"Always", "Hold"}));
        this.registerSetting(delay = new SliderSetting("Delay", " ticks", 0, 0, 10, 1));
    }

    @Override
    public void onEnable() {
        tickCounter = 0;
    }

    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        if (!Utils.nullCheck() || mc.currentScreen != null || mc.thePlayer.capabilities.isFlying) return;
        if (!mc.thePlayer.onGround) return;

        boolean shouldJump = (int) mode.getInput() == 0 ||
                ((int) mode.getInput() == 1 && mc.gameSettings.keyBindJump.isKeyDown());

        if (!shouldJump) return;

        if (tickCounter < (int) delay.getInput()) {
            tickCounter++;
            return;
        }

        mc.thePlayer.jump();
        tickCounter = 0;
    }
}
