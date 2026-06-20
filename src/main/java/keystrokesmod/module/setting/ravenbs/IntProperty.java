package keystrokesmod.module.setting.ravenbs;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.SliderSetting;

import java.util.function.Supplier;

public class IntProperty {
    private final SliderSetting setting;

    public IntProperty(String name, int defaultValue, int min, int max) {
        this.setting = new SliderSetting(name, defaultValue, min, max, 1);
    }

    public IntProperty(String name, int defaultValue, int min, int max, Supplier<Boolean> visible) {
        this(name, defaultValue, min, max);
    }

    public void register(Module module) {
        module.registerSetting(setting);
    }

    public int getValue() {
        return (int) setting.getInput();
    }

    public void setValue(int value) {
        setting.setValue(value);
    }

    public SliderSetting getSetting() {
        return setting;
    }
}
