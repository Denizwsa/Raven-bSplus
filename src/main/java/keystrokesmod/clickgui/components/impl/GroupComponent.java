package keystrokesmod.clickgui.components.impl;

import keystrokesmod.clickgui.components.Component;
import keystrokesmod.module.impl.client.Gui;
import keystrokesmod.module.setting.impl.GroupSetting;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Timer;
import keystrokesmod.utility.font.RavenFontRenderer;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public class GroupComponent extends Component {
    public GroupSetting setting;
    private ModuleComponent component;
    public float o;
    private float x;
    private float y;
    public boolean opened;

    private Timer smoothTimer;
    private float animationProgress;
    private float animationStartProgress;
    private float animationTargetProgress;

    private static final float ANIMATION_DURATION = 250f;
    private static final Color GROUP_COLOR = new Color(180, 180, 200);
    private static final Color SEPARATOR_COLOR = new Color(50, 50, 70, 100);

    public GroupComponent(GroupSetting setting, ModuleComponent moduleComponent, float o) {
        this.setting = setting;
        this.component = moduleComponent;
        this.o = o;
        this.x = moduleComponent.categoryComponent.getX() + moduleComponent.categoryComponent.getWidth();
        this.y = moduleComponent.categoryComponent.getY() + moduleComponent.yPos;
        this.opened = setting.isOpened();
        this.animationProgress = opened ? 1f : 0f;
        this.animationStartProgress = this.animationProgress;
        this.animationTargetProgress = this.animationProgress;
    }

    public float getAnimationProgress() {
        if (smoothTimer != null) {
            if (System.currentTimeMillis() - smoothTimer.last >= ANIMATION_DURATION + 30) {
                smoothTimer = null;
                animationProgress = animationTargetProgress;
                animationStartProgress = animationTargetProgress;
            } else {
                animationProgress = smoothTimer.getValueFloat(animationStartProgress, animationTargetProgress, 1);
                if (animationProgress == animationTargetProgress) {
                    smoothTimer = null;
                    animationStartProgress = animationTargetProgress;
                }
            }
        }
        return animationProgress;
    }

    public void render() {
        float progress = getAnimationProgress();
        RavenFontRenderer renderer = Gui.getClickGuiSettingFontRenderer();
        float catX = this.component.categoryComponent.getX();
        float catW = this.component.categoryComponent.getWidth();
        float renderY = this.component.categoryComponent.getY() + this.o;

        GL11.glPushMatrix();
        GL11.glScaled(0.5D, 0.5D, 0.5D);

        float strX = (catX + 5) * 2 + 1;
        float strY = (renderY + 4) * 2;

        String arrow = (progress > 0.5f) ? "\u25bc" : "\u25b6";
        String text = arrow + "  " + this.setting.getName();
        renderer.drawString(text, strX, strY, GROUP_COLOR.getRGB(), false);

        GL11.glPopMatrix();

        if (progress > 0 && progress < 1) {
            float lineY = renderY + 11;
            float lineX1 = catX + 5;
            float lineX2 = catX + catW - 5;
            float lineProgress = Math.min(progress * 2, 1f);
            float currentEnd = lineX1 + (lineX2 - lineX1) * lineProgress;
            RenderUtils.drawHorizontalGradientRect(lineX1, lineY, currentEnd, lineY + 1,
                    SEPARATOR_COLOR.getRGB(), new Color(60, 180, 255, 100).getRGB());
        }
    }

    public void updateHeight(float n) {
        this.o = n;
    }

    @Override
    public float getOffset() {
        return this.o;
    }

    public void drawScreen(int x, int y) {
        this.y = this.component.categoryComponent.getModuleY() + this.o;
        this.x = this.component.categoryComponent.getX();
    }

    public boolean onClick(int x, int y, int b) {
        if (this.overGroup(x, y) && (b == 0 || b == 1) && this.component.isOpened) {
            float currentProgress = getAnimationProgress();
            this.animationStartProgress = currentProgress;
            this.opened = !this.opened;
            this.setting.setOpened(this.opened);
            this.animationTargetProgress = this.opened ? 1f : 0f;
            (this.smoothTimer = new Timer(ANIMATION_DURATION)).start();
            this.component.updateSettingPositions();
            return true;
        }
        return false;
    }

    public void onGuiClosed() {
        smoothTimer = null;
        animationProgress = opened ? 1f : 0f;
        animationStartProgress = animationProgress;
        animationTargetProgress = animationProgress;
    }

    public boolean overGroup(int x, int y) {
        return x > this.x && x < this.x + this.component.categoryComponent.getWidth() && y > this.y && y < this.y + 11;
    }
}
