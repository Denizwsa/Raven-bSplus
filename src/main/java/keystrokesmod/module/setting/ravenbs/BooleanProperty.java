package keystrokesmod.module.setting.ravenbs;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;

import java.util.function.Supplier;

public class BooleanProperty {
    private final ButtonSetting setting;

    public BooleanProperty(String name, boolean defaultValue) {
        this.setting = new ButtonSetting(name, defaultValue);
    }

    public BooleanProperty(String name, boolean defaultValue, Supplier<Boolean> visible) {
        this.setting = new ButtonSetting(name, defaultValue);
    }

    public void register(Module module) {
        module.registerSetting(setting);
    }

    public boolean getValue() {
        return setting.isToggled();
    }

    public boolean isToggled() {
        return setting.isToggled();
    }

    public ButtonSetting getSetting() {
        return setting;
    }
}
