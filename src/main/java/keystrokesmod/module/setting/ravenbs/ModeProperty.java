package keystrokesmod.module.setting.ravenbs;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.SliderSetting;

import java.util.function.Supplier;

public class ModeProperty {
    private final SliderSetting setting;
    private final String[] modes;

    public ModeProperty(String name, int defaultIndex, String[] modes) {
        this.modes = modes;
        this.setting = new SliderSetting(name, defaultIndex, modes);
    }

    public ModeProperty(String name, int defaultIndex, String[] modes, Supplier<Boolean> visible) {
        this(name, defaultIndex, modes);
    }

    public void register(Module module) {
        module.registerSetting(setting);
    }

    public int getValue() {
        return (int) setting.getInput();
    }

    public SliderSetting getSetting() {
        return setting;
    }

    public String[] getModes() {
        return modes;
    }
}
