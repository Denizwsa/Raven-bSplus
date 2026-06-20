package keystrokesmod.module.setting.ravenbs;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.SliderSetting;

public class PercentProperty {
    private final SliderSetting setting;

    public PercentProperty(String name, int defaultPercent) {
        this.setting = new SliderSetting(name, defaultPercent, 0, 100, 1);
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
}
