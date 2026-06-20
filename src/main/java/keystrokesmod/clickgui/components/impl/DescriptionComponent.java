package keystrokesmod.clickgui.components.impl;

import keystrokesmod.clickgui.components.Component;
import keystrokesmod.module.impl.client.Gui;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.utility.Theme;
import keystrokesmod.utility.font.RavenFontRenderer;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public class DescriptionComponent extends Component {
    public DescriptionSetting desc;
    private ModuleComponent p;
    public float o;
    public float x;
    public float y;

    private static final Color DESCR_COLOR = new Color(140, 140, 170);

    public DescriptionComponent(DescriptionSetting desc, ModuleComponent b, float o) {
        this.desc = desc;
        this.p = b;
        this.x = b.categoryComponent.getX() + b.categoryComponent.getWidth();
        this.y = b.categoryComponent.getY() + b.yPos;
        this.o = o;
    }

    public void render() {
        RavenFontRenderer renderer = Gui.getClickGuiSettingFontRenderer();
        float catX = this.p.categoryComponent.getX();
        float renderY = this.p.categoryComponent.getY() + this.o;

        GL11.glPushMatrix();
        GL11.glScaled(0.5D, 0.5D, 0.5D);
        renderer.drawString(this.desc.getDesc(),
                (float) ((catX + 5) * 2),
                (float) ((renderY + 4) * 2),
                DESCR_COLOR.getRGB(), true);
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
        return this.desc.visible;
    }
}
