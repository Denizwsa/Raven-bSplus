package keystrokesmod.clickgui.components.impl;

import keystrokesmod.Raven;
import keystrokesmod.clickgui.components.Component;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.client.Gui;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.font.RavenFontRenderer;
import keystrokesmod.utility.profile.ProfileModule;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public class ButtonComponent extends Component {
    private Module mod;
    public ButtonSetting buttonSetting;
    private ModuleComponent moduleComponent;

    public float o;
    public float x;
    private float y;
    public float xOffset;

    private static final Color TOGGLE_ON = new Color(60, 180, 255);
    private static final Color TOGGLE_OFF = new Color(70, 70, 85);
    private static final Color TEXT_COLOR = new Color(200, 200, 220);
    private static final Color METHOD_COLOR = new Color(120, 200, 120);

    public ButtonComponent(Module mod, ButtonSetting op, ModuleComponent b, float o) {
        this.mod = mod;
        this.buttonSetting = op;
        this.moduleComponent = b;
        this.x = b.categoryComponent.getX() + b.categoryComponent.getWidth();
        this.y = b.categoryComponent.getY() + b.yPos;
        this.o = o;
    }

    public void render() {
        RavenFontRenderer renderer = Gui.getClickGuiSettingFontRenderer();
        float catX = this.moduleComponent.categoryComponent.getX();
        float renderY = this.moduleComponent.categoryComponent.getY() + this.o;

        boolean isToggle = !buttonSetting.isMethodButton;
        boolean isToggled = buttonSetting.isToggled();

        if (isToggle) {
            float toggleX = catX + this.moduleComponent.categoryComponent.getWidth() - 14;
            float toggleY = renderY + 3;
            float tw = 10;
            float th = 7;
            float tr = th / 2;
            int bgColor = isToggled ? TOGGLE_ON.getRGB() : TOGGLE_OFF.getRGB();
            RenderUtils.drawRoundedRectangle(toggleX, toggleY, toggleX + tw, toggleY + th, tr, bgColor);
            float knobX = isToggled ? toggleX + tw - th : toggleX;
            float knobR = th - 2;
            RenderUtils.drawRoundedRectangle(knobX + 1, toggleY + 1, knobX + 1 + knobR, toggleY + 1 + knobR, knobR / 2,
                    isToggled ? 0xFFFFFFFF : new Color(180, 180, 190).getRGB());
        }

        GL11.glPushMatrix();
        GL11.glScaled(0.5D, 0.5D, 0.5D);
        int textColor = buttonSetting.isMethodButton ? METHOD_COLOR.getRGB() : TEXT_COLOR.getRGB();
        String prefix = buttonSetting.isMethodButton ? "\u25c8  " : "";
        renderer.drawString(prefix + this.buttonSetting.getName(),
                (float) ((catX + 5) * 2) + xOffset,
                (float) ((renderY + 4) * 2),
                textColor, false);
        GL11.glScaled(1, 1, 1);
        GL11.glPopMatrix();
    }

    public void updateHeight(float n) {
        this.o = n;
    }

    @Override
    public float getOffset() {
        return this.o;
    }

    @Override
    public boolean isBaseVisible() {
        return this.buttonSetting.visible;
    }

    public void drawScreen(int x, int y) {
        this.y = this.moduleComponent.categoryComponent.getModuleY() + this.o;
        this.x = this.moduleComponent.categoryComponent.getX();
    }

    public boolean onClick(int x, int y, int b) {
        if (this.i(x, y) && b == 0 && this.moduleComponent.isOpened && this.moduleComponent.isVisible(this)) {
            if (this.buttonSetting.isMethodButton) {
                this.buttonSetting.runMethod();
                return false;
            }
            this.buttonSetting.toggle();
            this.mod.guiButtonToggled(this.buttonSetting);
            if (Raven.currentProfile != null && !this.mod.ignoreOnSave) {
                Raven.currentProfile.getModule().saved = false;
            }
        }
        return false;
    }

    public boolean i(int x, int y) {
        return x > this.x && x < this.x + this.moduleComponent.categoryComponent.getWidth() && y > this.y && y < this.y + 11;
    }
}
