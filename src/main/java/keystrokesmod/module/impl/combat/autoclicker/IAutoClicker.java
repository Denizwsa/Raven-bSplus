package keystrokesmod.module.impl.combat.autoclicker;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.module.setting.impl.ButtonSetting;

public class IAutoClicker extends Module {
    public final SliderSetting minCps;
    public final SliderSetting maxCps;
    public final ButtonSetting breakBlocks;
    public final ButtonSetting weaponOnly;
    public final ButtonSetting disableCreative;

    public IAutoClicker(String name, Module.category category) {
        super(name, category);
        this.registerSetting(new DescriptionSetting("Auto clicker mode"));
        this.registerSetting(minCps = new SliderSetting("Min CPS", 8.0, 1.0, 20.0, 0.5));
        this.registerSetting(maxCps = new SliderSetting("Max CPS", 12.0, 1.0, 20.0, 0.5));
        this.registerSetting(breakBlocks = new ButtonSetting("Break blocks", true));
        this.registerSetting(weaponOnly = new ButtonSetting("Weapon only", false));
        this.registerSetting(disableCreative = new ButtonSetting("Disable in creative", true));
    }

    public boolean click() {
        return false;
    }
}
