package keystrokesmod.clickgui.components.impl;

import keystrokesmod.Raven;
import keystrokesmod.clickgui.components.Component;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.client.Gui;
import keystrokesmod.module.setting.impl.KeySetting;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Theme;
import keystrokesmod.utility.font.RavenFontRenderer;
import keystrokesmod.utility.profile.ProfileModule;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public class BindComponent extends Component {
    private static final String EYE_ICON_PATH = "/assets/keystrokesmod/textures/gui/eye.png";
    private static final String EYE_OFF_ICON_PATH = "/assets/keystrokesmod/textures/gui/eye_off.png";
    private static final int EYE_ICON_PADDING = 2;

    public boolean isBinding;
    public ModuleComponent moduleComponent;
    public float o;
    public float x;
    private float y;
    public KeySetting keySetting;
    public float xOffset;

    private static final Color TEXT_COLOR = new Color(200, 200, 220);

    public BindComponent(ModuleComponent moduleComponent, float o) {
        this.moduleComponent = moduleComponent;
        this.x = moduleComponent.categoryComponent.getX() + moduleComponent.categoryComponent.getWidth();
        this.y = moduleComponent.categoryComponent.getY() + moduleComponent.yPos;
        this.o = o;
    }

    public BindComponent(ModuleComponent moduleComponent, KeySetting keySetting, float o) {
        this.moduleComponent = moduleComponent;
        this.x = moduleComponent.categoryComponent.getX() + moduleComponent.categoryComponent.getWidth();
        this.y = moduleComponent.categoryComponent.getY() + moduleComponent.yPos;
        this.keySetting = keySetting;
        this.o = o;
    }

    public void updateHeight(float n) {
        this.o = n;
    }

    @Override public float getOffset() { return o; }
    @Override public boolean isBaseVisible() { return keySetting == null || keySetting.visible; }

    public void render() {
        RavenFontRenderer renderer = Gui.getClickGuiSettingFontRenderer();
        float catX = this.moduleComponent.categoryComponent.getX();
        float renderY = this.moduleComponent.categoryComponent.getY() + this.o;

        GL11.glPushMatrix();
        GL11.glScaled(0.5D, 0.5D, 0.5D);
        int textColor = TEXT_COLOR.getRGB();
        if (keySetting == null) {
            String text = this.isBinding ? "Press a key..." : "Bind: \u00a7e" + getKeyAsStr(false) + "\u00a7r";
            renderer.drawString(text, (float) ((catX + 5) * 2) + xOffset, (float) ((renderY + 4) * 2), textColor, true);
            GL11.glPopMatrix();

            if (moduleComponent.mod.moduleCategory() != Module.category.profiles) {
                int iconSize = getEyeIconSize();
                float iconX = getEyeIconX(iconSize);
                float textHeight = Gui.getClickGuiSettingFontRenderer().getFontHeight() * 0.5f;
                float iconY = renderY + (textHeight - iconSize) / 2f + 1;

                int themeColor = !moduleComponent.mod.hidden
                        ? Theme.getGradient(Theme.descriptor[0], Theme.descriptor[1], 0)
                        : Theme.getGradient(Theme.hiddenBind[0], Theme.hiddenBind[1], 0);
                String iconPath = moduleComponent.mod.isHidden() ? EYE_OFF_ICON_PATH : EYE_ICON_PATH;
                RenderUtils.drawIcon(RenderUtils.getIcon(iconPath), iconX, iconY, iconSize, themeColor);
            }
        } else {
            String text = this.isBinding ? "Press a key..." : this.keySetting.getName() + ": \u00a7e" + getKeyAsStr(true) + "\u00a7r";
            renderer.drawString(text, (float) ((catX + 5) * 2) + xOffset, (float) ((renderY + 4) * 2), Theme.getGradient(Theme.descriptor[0], Theme.descriptor[1], 0), true);
            GL11.glPopMatrix();
        }
    }

    public void drawScreen(int x, int y) {
        this.y = moduleComponent.categoryComponent.getModuleY() + o;
        this.x = moduleComponent.categoryComponent.getX();
    }

    public boolean onClick(int x, int y, int button) {
        if (!overSetting(x, y) || !moduleComponent.isOpened || !moduleComponent.isVisible(this)) return false;
        if (button == 0 && moduleComponent.mod.moduleCategory() != Module.category.profiles && overEyeIcon(x, y)) {
            moduleComponent.mod.setHidden(!moduleComponent.mod.isHidden());
            if (Raven.currentProfile != null) Raven.currentProfile.getModule().saved = false;
            return true;
        }
        if (moduleComponent.mod.canBeEnabled() && button == 0 && overBindText(x, y)) {
            isBinding = !isBinding;
            return true;
        }
        if (moduleComponent.mod.canBeEnabled() && button > 1 && isBinding) {
            if (keySetting != null) keySetting.setKey(button + 1000);
            else moduleComponent.mod.setBind(button + 1000);
            if (Raven.currentProfile != null) Raven.currentProfile.getModule().saved = false;
            isBinding = false;
            return true;
        }
        return false;
    }

    private boolean overEyeIcon(int x, int y) {
        int iconSize = getEyeIconSize();
        float iconX = getEyeIconX(iconSize);
        float iconY = getEyeIconY(iconSize);
        return x >= iconX && x < iconX + iconSize && y >= iconY && y < iconY + iconSize;
    }

    private float getBindTextX(int x) {
        return x;
    }

    private float getBindTextY(float y) {
        return y;
    }

    private String getBindDisplayString() {
        if (keySetting == null)
            return isBinding ? "Press a key..." : "Bind: \u00a7e" + getKeyAsStr(false) + "\u00a7r";
        return isBinding ? "Press a key..." : keySetting.getName() + ": \u00a7e" + getKeyAsStr(true) + "\u00a7r";
    }

    private boolean overBindText(int mouseX, int mouseY) {
        String text = getBindDisplayString();
        RavenFontRenderer renderer = Gui.getClickGuiSettingFontRenderer();
        float left = this.moduleComponent.categoryComponent.getX() + 5 + (xOffset * 0.5f);
        float top = this.moduleComponent.categoryComponent.getY() + o + 4;
        float width = renderer.getStringWidth(text) * 0.5f;
        float height = renderer.getFontHeight() * 0.5f;
        return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
    }

    private int getEyeIconSize() {
        int fontH = Math.round(Gui.getClickGuiSettingFontRenderer().getFontHeight() * 0.5f);
        return Math.max(6, fontH - 1);
    }

    private float getEyeIconX(int iconSize) {
        return moduleComponent.categoryComponent.getX() + moduleComponent.categoryComponent.getWidth() - iconSize - EYE_ICON_PADDING;
    }

    private float getEyeIconY(int iconSize) {
        float textY = moduleComponent.categoryComponent.getY() + o + 4;
        float textHeight = Gui.getClickGuiSettingFontRenderer().getFontHeight() * 0.5f;
        return textY + (textHeight - iconSize) / 2f;
    }

    public void onScroll(int scroll) {
        if (!isBinding || scroll == 0) return;
        if (keySetting != null) keySetting.setKey(scroll > 0 ? 1069 : 1070);
        else moduleComponent.mod.setBind(scroll > 0 ? 1069 : 1070);
        if (Raven.currentProfile != null) Raven.currentProfile.getModule().saved = false;
        isBinding = false;
    }

    public void keyTyped(char t, int keybind) {
        if (!isBinding) return;
        if (keybind == Keyboard.KEY_0 || keybind == Keyboard.KEY_ESCAPE) {
            if (moduleComponent.mod instanceof Gui) moduleComponent.mod.setBind(54);
            else if (keySetting != null) keySetting.setKey(0);
            else moduleComponent.mod.setBind(0);
        } else {
            if (keySetting != null) keySetting.setKey(keybind);
            else moduleComponent.mod.setBind(keybind);
        }
        if (Raven.currentProfile != null) Raven.currentProfile.getModule().saved = false;
        isBinding = false;
    }

    public boolean overSetting(int mouseX, int mouseY) {
        float rowX = moduleComponent.categoryComponent.getX();
        float rowY = moduleComponent.categoryComponent.getModuleY() + o;
        float rowW = moduleComponent.categoryComponent.getWidth();
        return mouseX > rowX && mouseX < rowX + rowW && mouseY > rowY - 1 && mouseY < rowY + 12;
    }

    public String getKeyAsStr(boolean isKey) {
        int key = isKey ? keySetting.getKey() : moduleComponent.mod.getKeycode();
        return key >= 1000 ? ((key == 1069 || key == 1070) ? getScroll(key) : "M" + (key - 1000)) : Keyboard.getKeyName(key);
    }

    public String getScroll(int key) {
        if (key == 1069) return "MScrollUp";
        if (key == 1070) return "MScrollDown";
        return "&cERROR";
    }

    @Override public float getHeightF() { return keySetting != null ? 0f : 18f; }
    @Override public int getHeight() { return Math.round(getHeightF()); }

    public void onGuiClosed() { isBinding = false; }
}
