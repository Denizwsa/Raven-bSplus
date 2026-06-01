package keystrokesmod.module.impl.render;

import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.notification.NotificationManager;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class Notifications extends Module {
    public static SliderSetting duration;
    public static SliderSetting maxVisible;
    public static ButtonSetting enableToggle;

    public Notifications() {
        super("Notifications", category.render);
        this.registerSetting(new DescriptionSetting("Toggle notifications for module events"));
        this.registerSetting(enableToggle = new ButtonSetting("Enabled", true));
        this.registerSetting(duration = new SliderSetting("Duration", 1.5, 0.5, 5.0, 0.1));
        this.registerSetting(maxVisible = new SliderSetting("Max visible", 5, 1, 10, 1));
        this.alwaysOn = true;
    }

    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        NotificationManager.setEnabled(enableToggle.isToggled());
        NotificationManager.setDisplayDuration((long) (duration.getInput() * 1000));
        NotificationManager.setMaxVisible((int) maxVisible.getInput());
    }
}
