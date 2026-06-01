package keystrokesmod.utility.notification;

import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Theme;
import keystrokesmod.utility.font.FontManager;
import keystrokesmod.utility.font.RavenFontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class NotificationManager {
    private static final List<Notification> notifications = new ArrayList<>();
    private static long displayDuration = 1500L;
    private static int maxVisible = 5;
    private static boolean enabled = true;

    public static void setEnabled(boolean enabled) {
        NotificationManager.enabled = enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setDisplayDuration(long duration) {
        displayDuration = Math.max(500L, duration);
    }

    public static long getDisplayDuration() {
        return displayDuration;
    }

    public static void setMaxVisible(int max) {
        maxVisible = Math.max(1, max);
    }

    public static int getMaxVisible() {
        return maxVisible;
    }

    public static void notify(String title, String message, Notification.Type type) {
        if (!enabled) return;
        notifications.add(new Notification(title, message, type, displayDuration));
        while (notifications.size() > maxVisible * 2) {
            notifications.remove(0);
        }
    }

    public static void notify(String message, Notification.Type type) {
        notify(type == Notification.Type.ENABLE ? "Enabled" :
               type == Notification.Type.DISABLE ? "Disabled" : "Info", message, type);
    }

    public static void clear() {
        notifications.clear();
    }

    public static int size() {
        return notifications.size();
    }

    public static void render() {
        if (notifications.isEmpty()) return;

        Iterator<Notification> it = notifications.iterator();
        while (it.hasNext()) {
            if (it.next().isExpired()) it.remove();
        }

        ScaledResolution sr = new ScaledResolution(mc);
        int screenWidth = sr.getScaledWidth();
        int screenHeight = sr.getScaledHeight();
        RavenFontRenderer font = FontManager.getHudRenderer("Minecraft", 1.0f);
        int fontHeight = font.getFontHeight();

        int margin = 8;
        int padding = 8;
        int lineSpacing = 2;
        int cornerRadius = 4;
        int iconSize = 6;

        int total = Math.min(notifications.size(), maxVisible);
        int currentIndex = 0;
        float slideDistance = 30.0f;

        for (int i = notifications.size() - 1; i >= 0 && currentIndex < total; i--) {
            Notification n = notifications.get(i);
            float alpha = n.getAlpha();
            if (alpha <= 0.0f) continue;

            String title = n.getTitle();
            String message = n.getMessage();
            int titleWidth = font.getStringWidth(title);
            int messageWidth = font.getStringWidth(message);
            int textWidth = Math.max(titleWidth, messageWidth);
            int boxWidth = textWidth + padding * 3 + iconSize;
            int boxHeight = fontHeight * 2 + lineSpacing + padding * 2;

            int yPos = screenHeight - margin - (currentIndex + 1) * (boxHeight + 4) + Math.round((1.0f - alpha) * slideDistance);
            int xPos = screenWidth - margin - boxWidth + Math.round((1.0f - alpha) * slideDistance);

            int alphaInt = (int) (alpha * 255);
            int bgColor = new Color(0, 0, 0, Math.min(140, (int) (alpha * 140))).getRGB();
            int borderColor = getTypeColor(n.getType(), alphaInt);
            int iconColor = borderColor;

            // Background
            RenderUtils.drawRoundedRectangle(xPos, yPos, xPos + boxWidth, yPos + boxHeight, cornerRadius, bgColor);

            // Border
            int borderThickness = 1;
            int borderA = (int) (alpha * 200);
            int borderRGB = (borderColor & 0x00FFFFFF) | (borderA << 24);
            drawRoundedRectOutline(xPos, yPos, xPos + boxWidth, yPos + boxHeight, cornerRadius, borderThickness, borderRGB);

            // Color accent bar on left
            int accentX = xPos;
            int accentY = yPos;
            int accentW = 3;
            int accentH = boxHeight;
            int accentRGB = (iconColor & 0x00FFFFFF) | (borderA << 24);
            RenderUtils.drawRect(accentX, accentY, accentX + accentW, accentY + accentH, accentRGB);

            // Icon (colored dot)
            int iconX = xPos + padding + 1;
            int iconY = yPos + padding + (boxHeight - padding * 2 - iconSize) / 2;
            int iconRGB = (iconColor & 0x00FFFFFF) | (borderA << 24);
            RenderUtils.drawRect(iconX, iconY, iconX + iconSize, iconY + iconSize, iconRGB);

            int textX = xPos + padding + iconSize + padding / 2;
            int titleY = yPos + padding - 1;
            int messageY = titleY + fontHeight + lineSpacing;

            int titleRGB = new Color(255, 255, 255, alphaInt).getRGB();
            int messageRGB = new Color(180, 180, 180, alphaInt).getRGB();

            font.drawString(title, textX, titleY, titleRGB);
            font.drawString(message, textX, messageY, messageRGB);

            currentIndex++;
        }
    }

    private static int getTypeColor(Notification.Type type, int alpha) {
        int color;
        switch (type) {
            case ENABLE:
                color = new Color(85, 255, 85).getRGB();
                break;
            case DISABLE:
                color = new Color(255, 85, 85).getRGB();
                break;
            case INFO:
            default:
                int[] grad = Theme.getGradients(1);
                color = grad[0];
                break;
        }
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private static void drawRoundedRectOutline(int x1, int y1, int x2, int y2, int radius, int thickness, int color) {
        int a = (color >>> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        // Top
        RenderUtils.drawRect(x1 + radius, y1, x2 - radius, y1 + thickness, color);
        // Bottom
        RenderUtils.drawRect(x1 + radius, y2 - thickness, x2 - radius, y2, color);
        // Left
        RenderUtils.drawRect(x1, y1 + radius, x1 + thickness, y2 - radius, color);
        // Right
        RenderUtils.drawRect(x2 - thickness, y1 + radius, x2, y2 - radius, color);

        // Corners (approx)
        int cornerPixels = Math.min(radius, 6);
        int cornerColor = ((a) << 24) | (r << 16) | (g << 8) | b;
        for (int i = 0; i < cornerPixels; i++) {
            for (int j = 0; j < thickness; j++) {
                // TL
                RenderUtils.drawRect(x1 + i, y1 + j, x1 + i + 1, y1 + j + 1, cornerColor);
                // TR
                RenderUtils.drawRect(x2 - i - 1, y1 + j, x2 - i, y1 + j + 1, cornerColor);
                // BL
                RenderUtils.drawRect(x1 + i, y2 - j - 1, x1 + i + 1, y2 - j, cornerColor);
                // BR
                RenderUtils.drawRect(x2 - i - 1, y2 - j - 1, x2 - i, y2 - j, cornerColor);
            }
        }
    }

    private static net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
}
