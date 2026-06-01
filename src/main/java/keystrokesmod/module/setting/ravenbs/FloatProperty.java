package keystrokesmod.module.setting.ravenbs;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.SliderSetting;

import java.util.function.Supplier;

public class FloatProperty {
    private final SliderSetting setting;

    public FloatProperty(String name, float defaultValue, float min, float max) {
        this.setting = new SliderSetting(name, defaultValue, min, max, 0.1);
    }

    public FloatProperty(String name, float defaultValue, float min, float max, Supplier<Boolean> visible) {
        this(name, defaultValue, min, max);
    }

    public void register(Module module) {
        module.registerSetting(setting);
    }

    public float getValue() {
        return (float) setting.getInput();
    }

    public SliderSetting getSetting() {
        return setting;
    }
}
