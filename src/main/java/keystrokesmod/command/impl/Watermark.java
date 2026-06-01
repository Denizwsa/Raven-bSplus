package keystrokesmod.command.impl;

import keystrokesmod.command.Command;
import keystrokesmod.command.CommandInput;
import keystrokesmod.module.impl.render.HUD;
import keystrokesmod.utility.Utils;

import java.util.Collections;
import java.util.List;

public class Watermark extends Command {
    public Watermark() {
        super("watermark", "wm");
    }

    @Override
    public void execute(CommandInput input) {
        if (input.argumentCount() == 0) {
            Utils.sendMessage("&cUsage: " + getPrefix() + "watermark <text>");
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.argumentCount(); i++) {
            sb.append(input.getArgument(i)).append(" ");
        }

        String newName = sb.toString().trim();
        HUD.watermarkName = newName;
        Utils.sendMessage("&aWatermark changed to: &r" + newName);
    }

    @Override
    public List<String> suggest(CommandInput input) {
        return Collections.emptyList();
    }
}
