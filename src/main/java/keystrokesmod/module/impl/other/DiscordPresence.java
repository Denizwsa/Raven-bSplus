package keystrokesmod.module.impl.other;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.utility.discord.DiscordRP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class DiscordPresence extends Module {

    public DiscordPresence() {
        super("Discord Presence", category.other);
        this.registerSetting(new DescriptionSetting("Internal Discord Rich Presence updater"));
        this.ignoreOnSave = true;
        this.hidden = true;
    }

    @Override
    public void onEnable() {
        if (!DiscordRP.isInitialized()) {
            DiscordRP.init();
        } else {
            DiscordRP.update();
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!isEnabled()) return;
        DiscordRP.update();
    }
}
