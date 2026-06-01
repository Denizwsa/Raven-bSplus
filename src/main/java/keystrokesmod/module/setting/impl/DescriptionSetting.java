package keystrokesmod.module.setting.impl;

import com.google.gson.JsonObject;
import keystrokesmod.module.setting.Setting;

public class DescriptionSetting extends Setting {
    public DescriptionSetting(String name) {
        super(name);
    }

    public String getDesc() {
        return getName();
    }

    @Override
    public void loadProfile(JsonObject data) {
    }
}
