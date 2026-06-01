package keystrokesmod.utility.ravenbs;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;

public final class PacketHelper {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private PacketHelper() {
    }

    public static void sendPacket(Packet<?> packet) {
        if (mc.thePlayer != null && mc.thePlayer.sendQueue != null) {
            mc.thePlayer.sendQueue.addToSendQueue(packet);
        }
    }
}
