package keystrokesmod.module.impl.combat.autoclicker;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.Utils;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MovingObjectPosition;

import java.util.Random;

public class NormalAutoClicker extends IAutoClicker {
    public final SliderSetting variance;
    public final ButtonSetting jitter;
    private long nextClickTime;
    private final Random random = new Random();

    public NormalAutoClicker(String name, Module.category category, boolean weaponOnly, boolean jitter) {
        super(name, category);
        this.registerSetting(variance = new SliderSetting("Variance", 30.0, 0.0, 100.0, 1.0));
        this.registerSetting(this.jitter = new ButtonSetting("Jitter", jitter));
        this.weaponOnly.setEnabled(weaponOnly);
    }

    @Override
    public boolean click() {
        if (mc.thePlayer == null || mc.currentScreen != null) return false;
        if (disableCreative.isToggled() && mc.thePlayer.capabilities.isCreativeMode) return false;
        if (weaponOnly.isToggled() && !Utils.holdingWeapon()) return false;
        if (breakBlocks.isToggled()) {
            MovingObjectPosition mop = mc.objectMouseOver;
            if (mop == null || mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) return false;
        }
        int minCpsI = (int) minCps.getInput();
        int maxCpsI = (int) maxCps.getInput();
        if (minCpsI < 1) minCpsI = 1;
        if (maxCpsI < minCpsI) maxCpsI = minCpsI;
        double cps = minCpsI + random.nextInt(maxCpsI - minCpsI + 1);
        double variance = this.variance.getInput() / 100.0;
        long delay = (long) (1000.0 / cps * (1.0 + (random.nextGaussian() * variance * 0.2)));
        if (delay < 33) delay = 33;
        if (nextClickTime == 0) nextClickTime = System.currentTimeMillis();
        if (System.currentTimeMillis() < nextClickTime) return false;
        nextClickTime = System.currentTimeMillis() + delay;
        int key = mc.gameSettings.keyBindAttack.getKeyCode();
        KeyBinding.onTick(key);
        return true;
    }
}
