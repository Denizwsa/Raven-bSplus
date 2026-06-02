package keystrokesmod.module.impl.render;

import keystrokesmod.config.AnimationConfig;
import keystrokesmod.config.AnimationMode;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.ravenbs.IntProperty;
import keystrokesmod.module.setting.ravenbs.ModeProperty;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class Animations extends Module {
    public final ModeProperty mode = new ModeProperty("Mode", 0,
            new String[]{"VANILLA", "EXHIBITION", "ETB", "SIGMA", "DORTWARE", "PLAIN",
                    "SPIN", "AVATAR", "SWONG", "SWANG", "SWANK", "STYLES",
                    "NUDGE", "PUNCH", "JIGSAW", "SLIDE"});
    public final IntProperty scale = new IntProperty("Scale", 100, 50, 150);
    public final IntProperty swingSpeed = new IntProperty("Swing speed", 0, 0, 100);

    public Animations() {
        super("Animations", category.render, 0);
        mode.register(this);
        scale.register(this);
        swingSpeed.register(this);
    }

    @Override
    public void onEnable() {
        syncConfig();
    }

    @Override
    public void onDisable() {
        AnimationConfig.setEnabled(false);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !isEnabled()) {
            return;
        }
        syncConfig();
    }

    private void syncConfig() {
        AnimationConfig.setEnabled(true);
        AnimationMode[] modes = AnimationMode.values();
        if (mode.getValue() < modes.length) {
            AnimationConfig.setMode(modes[mode.getValue()]);
        }
        AnimationConfig.setScale(scale.getValue());
        AnimationConfig.setSwingSpeed(swingSpeed.getValue());
    }

    @Override
    public String getInfo() {
        String[] modes = {"Vanilla", "Exhibition", "ETB", "Sigma", "Dortware", "Plain",
                "Spin", "Avatar", "Swong", "Swang", "Swank", "Styles",
                "Nudge", "Punch", "Jigsaw", "Slide"};
        int idx = mode.getValue();
        return idx >= 0 && idx < modes.length ? modes[idx] : "Vanilla";
    }
}
