package keystrokesmod.clickgui.components.impl;

import keystrokesmod.Raven;
import keystrokesmod.clickgui.ClickGui;
import keystrokesmod.clickgui.components.Component;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.Setting;
import keystrokesmod.module.setting.impl.*;
import keystrokesmod.module.impl.client.Gui;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Timer;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.font.RavenFontRenderer;
import keystrokesmod.utility.profile.Manager;
import keystrokesmod.utility.profile.ProfileModule;
import keystrokesmod.utility.shader.RoundedUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.nio.IntBuffer;
import java.util.Map;

public class ModuleComponent extends Component {
    public Module mod;
    public CategoryComponent categoryComponent;
    public float yPos;
    public ArrayList<Component> settings;
    public boolean isOpened;
    private boolean hovering;
    private Timer hoverTimer;
    private Timer hoverDescTimer;
    private boolean hoverStarted;
    private Timer smoothTimer;
    private float smoothingY = 18f;
    private float animationStartY = 18f;
    private float animationTargetY = 18f;

    private static final IntBuffer SCISSOR_BOX = BufferUtils.createIntBuffer(16);
    private static final float GROUP_CHILD_INDENT = 6f;

    private static final Color HOVER_BG = new Color(255, 255, 255, 35);
    private static final Color ENABLED_TEXT = new Color(140, 220, 255);
    private static final Color DISABLED_TEXT = new Color(200, 200, 210);
    private static final Color UNSAVED_COLOR = new Color(114, 188, 250);
    private static final Color INVALID_COLOR = new Color(255, 80, 80);
    private static final Color TOGGLE_ON_BG = new Color(60, 190, 255);
    private static final Color TOGGLE_OFF_BG = new Color(50, 50, 65);
    private static final Color TOGGLE_ON_KNOB = Color.WHITE;
    private static final Color TOGGLE_OFF_KNOB = new Color(150, 150, 165);
    private static final Color DESCR_BG = new Color(0, 0, 0, 200);

    private final boolean categoryManager;
    private final Map<Component, GroupComponent> owningGroups = new IdentityHashMap<Component, GroupComponent>();
    private final Map<String, GroupComponent> groupsByName = new HashMap<String, GroupComponent>();

    public ModuleComponent(Module mod, CategoryComponent p, float yPos) {
        this.mod = mod;
        this.categoryComponent = p;
        this.yPos = yPos;
        this.settings = new ArrayList();
        this.categoryManager = mod instanceof Manager || mod instanceof keystrokesmod.script.Manager;
        this.isOpened = categoryManager;
        float collapsedHeight = getCollapsedHeight();
        this.smoothingY = collapsedHeight;
        this.animationStartY = collapsedHeight;
        this.animationTargetY = collapsedHeight;
        rebuildSettingsList();
    }

    private void rebuildSettingsList() {
        this.settings = new ArrayList();
        float y = yPos + getSettingStartOffset();
        if (mod != null && !mod.getSettings().isEmpty()) {
            for (Setting v : mod.getSettings()) {
                if (!v.visible) continue;
                if (v instanceof SliderSetting) {
                    SliderComponent s = new SliderComponent((SliderSetting) v, this, y);
                    this.settings.add(s);
                    y += 12;
                } else if (v instanceof ButtonSetting) {
                    ButtonComponent c = new ButtonComponent(mod, (ButtonSetting) v, this, y);
                    this.settings.add(c);
                    y += 12;
                } else if (v instanceof DescriptionSetting) {
                    DescriptionComponent m = new DescriptionComponent((DescriptionSetting) v, this, y);
                    this.settings.add(m);
                    y += 12;
                } else if (v instanceof KeySetting) {
                    BindComponent keyComponent = new BindComponent(this, (KeySetting) v, y);
                    this.settings.add(keyComponent);
                    y += 12;
                } else if (v instanceof GroupSetting) {
                    GroupComponent c = new GroupComponent((GroupSetting) v, this, y);
                    this.settings.add(c);
                    y += 12;
                } else if (v instanceof ColorSetting) {
                    ColorComponent cc = new ColorComponent((ColorSetting) v, this, y);
                    this.settings.add(cc);
                    y += 12;
                } else if (v instanceof PotionListSetting) {
                    PotionSearchComponent psc = new PotionSearchComponent((PotionListSetting) v, this, y);
                    this.settings.add(psc);
                    y += 12;
                } else if (v instanceof InventoryItemListSetting) {
                    InventoryItemSearchComponent iisc = new InventoryItemSearchComponent((InventoryItemListSetting) v, this, y);
                    this.settings.add(iisc);
                    y += 12;
                } else if (v instanceof ItemListSetting) {
                    ItemSearchComponent isc = new ItemSearchComponent((ItemListSetting) v, this, y);
                    this.settings.add(isc);
                    y += 12;
                } else if (v instanceof PlayerListSetting) {
                    PlayerListComponent plc = new PlayerListComponent((PlayerListSetting) v, this, y);
                    this.settings.add(plc);
                    y += plc.getHeightF();
                } else if (v instanceof StringListSetting) {
                    StringListComponent slc = new StringListComponent((StringListSetting) v, this, y);
                    this.settings.add(slc);
                    y += slc.getHeightF();
                } else if (v instanceof keystrokesmod.module.setting.impl.BlockListSetting) {
                    BlockSearchComponent bsc = new BlockSearchComponent((keystrokesmod.module.setting.impl.BlockListSetting) v, this, y);
                    this.settings.add(bsc);
                    y += 12;
                } else if (v instanceof TextSetting) {
                    TextFieldComponent tfc = new TextFieldComponent((TextSetting) v, this, y);
                    this.settings.add(tfc);
                    y += tfc.getHeightF();
                }
            }
        }
        if (!categoryManager) {
            this.settings.add(new BindComponent(this, y));
        }
        rebuildGroupOwnershipCache();
    }

    public void reloadSettings() {
        boolean wasOpened = this.isOpened;
        Map<SliderSetting, Boolean> sliderHeldStates = new HashMap<SliderSetting, Boolean>();
        Map<ColorSetting, Boolean> colorExpandedStates = new HashMap<ColorSetting, Boolean>();

        for (Component component : this.settings) {
            if (component instanceof SliderComponent) {
                sliderHeldStates.put(((SliderComponent) component).sliderSetting, ((SliderComponent) component).heldDown);
            } else if (component instanceof ColorComponent) {
                colorExpandedStates.put(((ColorComponent) component).colorSetting, ((ColorComponent) component).expanded);
            }
        }

        rebuildSettingsList();
        for (Component component : this.settings) {
            if (component instanceof SliderComponent) {
                Boolean wasHeldDown = sliderHeldStates.get(((SliderComponent) component).sliderSetting);
                if (wasHeldDown != null) ((SliderComponent) component).heldDown = wasHeldDown;
            } else if (component instanceof ColorComponent) {
                Boolean wasExpanded = colorExpandedStates.get(((ColorComponent) component).colorSetting);
                if (wasExpanded != null) ((ColorComponent) component).restoreExpandedState(wasExpanded);
            }
        }
        restoreOpenState(wasOpened);
        updateSettingPositions();
    }

    public void restoreOpenState(boolean opened) {
        this.isOpened = categoryManager || opened;
        this.smoothTimer = null;
        float height = this.isOpened ? getHeightF() : getCollapsedHeight();
        this.smoothingY = height;
        this.animationStartY = height;
        this.animationTargetY = height;
    }

    public void updateAnimationState() {
        if (smoothTimer != null) {
            if (System.currentTimeMillis() - smoothTimer.last >= 280) {
                smoothTimer = null;
                smoothingY = animationTargetY;
                animationStartY = animationTargetY;
            } else {
                smoothingY = smoothTimer.getValueFloat(animationStartY, animationTargetY, 1);
                if (smoothingY == animationTargetY) {
                    smoothTimer = null;
                    animationStartY = animationTargetY;
                }
            }
        }
    }

    public void updateHeight(float newY) {
        this.yPos = newY;
        float y = this.yPos + getCollapsedHeight();
        int idx = 0;
        while (idx < this.settings.size()) {
            Component co = this.settings.get(idx);
            if (!isVisibleBase(co)) { idx++; continue; }

            if (co instanceof GroupComponent) {
                GroupComponent group = (GroupComponent) co;
                float progress = group.getAnimationProgress();
                co.updateHeight(y);
                float groupHeaderY = y;
                y += getBaseComponentHeightF(co);
                idx++;

                float childY = y;
                float totalChildrenFullHeight = 0f;
                while (idx < this.settings.size()) {
                    Component child = this.settings.get(idx);
                    if (!isVisibleBase(child)) { idx++; continue; }
                    if (getOwningGroup(child) != group) break;
                    child.updateHeight(childY);
                    float baseH = getBaseComponentHeightF(child);
                    childY += baseH;
                    totalChildrenFullHeight += baseH;
                    setChildIndent(child);
                    idx++;
                }
                y = groupHeaderY + getBaseComponentHeightF(group) + totalChildrenFullHeight * progress;
            } else {
                co.updateHeight(y);
                setChildIndent(co);
                y += getBaseComponentHeightF(co);
                idx++;
            }
        }
    }

    private void setChildIndent(Component co) {
        GroupComponent group = getOwningGroup(co);
        float indent = (group != null) ? GROUP_CHILD_INDENT : 0f;
        if (co instanceof SliderComponent) {
            ((SliderComponent) co).xOffset = indent;
        } else if (co instanceof ButtonComponent) {
            ((ButtonComponent) co).xOffset = indent;
        } else if (co instanceof BindComponent && ((BindComponent) co).keySetting != null) {
            ((BindComponent) co).xOffset = indent;
        } else if (co instanceof ColorComponent) {
            ((ColorComponent) co).xOffset = indent;
        } else if (co instanceof AbstractTextInputComponent) {
            ((AbstractTextInputComponent) co).setXOffset(indent);
        }
    }

    public void render() {
        boolean scissorRequired = smoothTimer != null;
        if (hasModuleHeader()) {
            float baseX = this.categoryComponent.getX();
            float baseW = this.categoryComponent.getWidth();
            float modY = this.categoryComponent.getY() + yPos;

            if (hovering || hoverTimer != null) {
                double hoverAlpha = (hovering && hoverTimer != null)
                    ? hoverTimer.getValueFloat(0, 45, 1)
                    : (hoverTimer != null && !hovering)
                    ? 45 - hoverTimer.getValueFloat(0, 45, 1)
                    : 45;
                if (hoverAlpha <= 0) { hoverTimer = null; hoverAlpha = 0; }
                int hoverBg = Utils.mergeAlpha(HOVER_BG.getRGB(), (int) hoverAlpha);
                RenderUtils.drawRoundedRectangle(baseX, modY, baseX + baseW, modY + 18, 6, hoverBg);
            }

            boolean isEnabled = this.mod.isEnabled();
            Color accentColor = isEnabled ? ENABLED_TEXT : DISABLED_TEXT;

            RavenFontRenderer titleRenderer = Gui.getClickGuiHeaderFontRenderer();
            float textX = baseX + 6;
            float textY = modY + 5;
            titleRenderer.drawString(this.mod.getName(), textX, textY, accentColor.getRGB(), true);

            drawToggleSwitch(baseX + baseW - 20, modY + 4, isEnabled);

            String info = this.mod.getInfo();
            if (!info.isEmpty() && hovering && this.mod.isEnabled()) {
                RavenFontRenderer settingRenderer = Gui.getClickGuiSettingFontRenderer();
                float descWidth = settingRenderer.getStringWidth(info) * 0.5f + 8;
                float descX = Math.min(baseX + baseW - descWidth - 2, baseX + baseW - 22 - descWidth);
                if (descX < baseX + 2) descX = baseX + 2;
                float descY = modY - 9;
                RenderUtils.drawRoundedRectangle(descX, descY, descX + descWidth, descY + 8, 3, DESCR_BG.getRGB());
                GL11.glPushMatrix();
                GL11.glScaled(0.5, 0.5, 0.5);
                settingRenderer.drawString(info, descX * 2, (descY + 1) * 2, new Color(200, 200, 220).getRGB(), true);
                GL11.glPopMatrix();
            }
        }

        if (scissorRequired) {
            ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
            int scale = sr.getScaleFactor();
            double guiScale = ClickGui.getActiveRenderScale();
            float scrollOffset = this.categoryComponent.getModuleY() - this.categoryComponent.getY();
            int scissorX = (int) Math.floor((this.categoryComponent.getX() - 2) * guiScale * scale);
            int scissorY = (int) Math.floor((sr.getScaledHeight() - ((this.categoryComponent.getY() + this.yPos + smoothingY + scrollOffset) * guiScale)) * scale);
            int scissorW = (int) Math.ceil((this.categoryComponent.getWidth() + 4) * guiScale * scale);
            int scissorH = (int) Math.ceil(smoothingY * guiScale * scale);
            pushScissor(scissorX, scissorY, scissorW, scissorH);
        }

        if (this.isOpened || smoothTimer != null) {
            renderSettingsWithGroupScissorReveal();
        }

        if (scissorRequired) {
            popScissor();
        }
    }

    private void drawToggleSwitch(float x, float y, boolean enabled) {
        float w = 16;
        float h = 10;
        float r = h / 2;

        Color bgColor = enabled ? TOGGLE_ON_BG : TOGGLE_OFF_BG;
        RoundedUtils.drawRound(x, y, w, h, r, bgColor);

        float knobX = enabled ? x + w - h + 1 : x + 1;
        float knobSize = h - 2;
        Color knobColor = enabled ? TOGGLE_ON_KNOB : TOGGLE_OFF_KNOB;
        RoundedUtils.drawRound(knobX, y + 1, knobSize, knobSize, knobSize / 2, knobColor);
    }

    public void drawScreen(int x, int y) {
        boolean overModule = hasModuleHeader() && overModuleName(x, y) && this.categoryComponent.opened;
        if (overModule) {
            if (!hovering) hoverStarted = true;
            hovering = true;
            if (hoverTimer == null) (hoverTimer = new Timer(75)).start();
        } else {
            if (hovering && hoverStarted) (hoverTimer = new Timer(75)).start();
            hoverStarted = false;
            hovering = false;
        }

        for (Component c : this.settings) {
            c.drawScreen(x, y);
        }
    }

    public int getModuleColor() {
        if (this.mod.script != null && this.mod.script.error) return INVALID_COLOR.getRGB();
        if (this.mod.moduleCategory() == Module.category.profiles
                && !(this.mod instanceof Manager)
                && !((ProfileModule) this.mod).saved
                && Raven.currentProfile != null
                && Raven.currentProfile.getModule() == this.mod) {
            return UNSAVED_COLOR.getRGB();
        }
        return this.mod.isEnabled() ? ENABLED_TEXT.getRGB() : DISABLED_TEXT.getRGB();
    }

    @Override
    public float getHeightF() {
        if (smoothTimer != null) return smoothingY;
        if (!this.isOpened) return getCollapsedHeight();
        float h = getCollapsedHeight();
        for (Component c : this.settings) {
            h += getAnimatedComponentHeightF(c);
        }
        return h;
    }

    @Override
    public int getHeight() {
        return Math.round(getHeightF());
    }

    public void onSliderChange() {
        for (Component c : this.settings) {
            if (c instanceof SliderComponent) ((SliderComponent) c).onSliderChange();
        }
    }

    public float getScrollExtentHeightF() {
        if (isOpened || (smoothTimer != null && animationTargetY > 18f)) {
            float h = getCollapsedHeight();
            for (Component c : settings) {
                if (!isVisibleBase(c)) continue;
                GroupComponent group = getOwningGroup(c);
                float progress = group != null ? group.getAnimationProgress() : 1f;
                float effectiveProgress = (group != null && group.opened) ? Math.max(progress, 1f) : progress;
                h += getBaseComponentHeightF(c) * effectiveProgress;
            }
            return h;
        }
        return getHeightF();
    }

    public boolean onClick(int x, int y, int mouse) {
        if (hasModuleHeader() && this.overModuleName(x, y) && mouse == 0 && this.mod.canBeEnabled()) {
            this.mod.toggle();
            if (this.mod.moduleCategory() != Module.category.profiles) {
                if (Raven.currentProfile != null) Raven.currentProfile.getModule().saved = false;
            }
            return true;
        }

        if (hasModuleHeader() && this.overModuleName(x, y) && mouse == 1) {
            float currentHeight = smoothTimer != null ? smoothingY : (isOpened ? getHeightF() : 18f);
            this.animationStartY = currentHeight;
            this.isOpened = !this.isOpened;
            float targetHeight;
            if (this.isOpened) {
                float h = getCollapsedHeight();
                for (Component c : this.settings) {
                    h += getAnimatedComponentHeightF(c);
                }
                targetHeight = h;
            } else {
                targetHeight = getCollapsedHeight();
            }
            this.animationTargetY = targetHeight;
            (this.smoothTimer = new Timer(250)).start();
            return true;
        }

        for (Component settingComponent : this.settings) {
            if (settingComponent.onClick(x, y, mouse)) return true;
        }
        return false;
    }

    public void mouseReleased(int x, int y, int m) {
        for (Component c : this.settings) {
            c.mouseReleased(x, y, m);
        }
    }

    public void keyTyped(char t, int k) {
        for (Component c : this.settings) {
            c.keyTyped(t, k);
        }
    }

    public void onScroll(int scroll) {
        for (Component component : this.settings) {
            component.onScroll(scroll);
        }
    }

    public void onGuiClosed() {
        for (Component c : this.settings) {
            c.onGuiClosed();
        }
        smoothTimer = null;
        hoverTimer = null;
        float finalHeight = isOpened ? getHeightF() : getCollapsedHeight();
        smoothingY = finalHeight;
        animationStartY = finalHeight;
        animationTargetY = finalHeight;
    }

    public boolean overModuleName(int x, int y) {
        if (!hasModuleHeader()) return false;
        float modY = this.categoryComponent.getModuleY() + this.yPos;
        return x > this.categoryComponent.getX()
            && x < this.categoryComponent.getX() + this.categoryComponent.getWidth()
            && y > modY
            && y < modY + 18;
    }

    public void updateSettingPositions() {
        this.categoryComponent.updateHeight();
    }

    public boolean isVisible(Component component) {
        if (!isVisibleBase(component)) return false;
        GroupComponent group = getOwningGroup(component);
        if (group == null) return true;
        return group.getAnimationProgress() > 0;
    }

    private GroupComponent getOwningGroup(Component component) {
        return owningGroups.get(component);
    }

    private String getGroupName(Component component) {
        if (component instanceof SliderComponent && ((SliderComponent) component).sliderSetting.groupSetting != null)
            return ((SliderComponent) component).sliderSetting.groupSetting.getName();
        if (component instanceof ButtonComponent && ((ButtonComponent) component).buttonSetting.group != null)
            return ((ButtonComponent) component).buttonSetting.group.getName();
        if (component instanceof BindComponent && ((BindComponent) component).keySetting != null && ((BindComponent) component).keySetting.group != null)
            return ((BindComponent) component).keySetting.group.getName();
        if (component instanceof ColorComponent && ((ColorComponent) component).colorSetting.groupSetting != null)
            return ((ColorComponent) component).colorSetting.groupSetting.getName();
        if (component instanceof AbstractTextInputComponent)
            return ((AbstractTextInputComponent) component).getGroupName();
        return "";
    }

    private void rebuildGroupOwnershipCache() {
        owningGroups.clear();
        groupsByName.clear();
        for (Component component : this.settings) {
            if (component instanceof GroupComponent) {
                groupsByName.put(((GroupComponent) component).setting.getName(), (GroupComponent) component);
            }
        }
        for (Component component : this.settings) {
            String groupName = getGroupName(component);
            if (!groupName.isEmpty()) {
                GroupComponent gc = groupsByName.get(groupName);
                if (gc != null) owningGroups.put(component, gc);
            }
        }
    }

    private float getBaseComponentHeightF(Component component) {
        if (component instanceof SliderComponent) return 16f;
        if (component instanceof ColorComponent) {
            ColorComponent cc = (ColorComponent) component;
            float progress = cc.getAnimationProgress();
            return 12f + (cc.getExpandedHeight() - 12f) * progress;
        }
        if (component instanceof AbstractSearchListComponent || component instanceof TextFieldComponent || component instanceof PlayerListComponent || component instanceof StringListComponent)
            return component.getHeightF();
        return 12f;
    }

    private float getAnimatedComponentHeightF(Component component) {
        if (!isVisibleBase(component)) return 0f;
        float base = getBaseComponentHeightF(component);
        GroupComponent group = getOwningGroup(component);
        float progress = group != null ? group.getAnimationProgress() : 1f;
        return base * progress;
    }

    private void renderSettingsWithGroupScissorReveal() {
        int i = 0;
        while (i < settings.size()) {
            Component c = settings.get(i);
            if (!isVisibleBase(c)) { i++; continue; }
            if (c instanceof GroupComponent) {
                ((GroupComponent) c).render();
                i++;
                while (i < settings.size()) {
                    Component child = settings.get(i);
                    if (!isVisibleBase(child)) { i++; continue; }
                    if (getOwningGroup(child) != c) break;
                    i++;
                }
            } else {
                c.render();
                i++;
            }
        }
        i = 0;
        while (i < settings.size()) {
            Component c = settings.get(i);
            if (!isVisibleBase(c)) { i++; continue; }
            if (c instanceof GroupComponent) {
                GroupComponent group = (GroupComponent) c;
                i++;
                float progress = group.getAnimationProgress();
                float groupContentTop = this.categoryComponent.getModuleY()
                        + group.getOffset() + getBaseComponentHeightF(group);
                float groupContentHeight = 0f;
                int j = i;
                while (j < settings.size()) {
                    Component child = settings.get(j);
                    if (!isVisibleBase(child)) { j++; continue; }
                    if (getOwningGroup(child) != group) break;
                    groupContentHeight += getBaseComponentHeightF(child);
                    j++;
                }
                if (progress > 0f && groupContentHeight > 0f) {
                    float revealHeight = groupContentHeight * progress;
                    ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
                    int sf = sr.getScaleFactor();
                    double guiScale = ClickGui.getActiveRenderScale();
                    double screenH = sr.getScaledHeight();
                    float compLeft = this.categoryComponent.getX();
                    float compWidth = this.categoryComponent.getWidth() + 4;
                    int newLeft = (int) Math.floor(compLeft * guiScale * sf);
                    int newRight = (int) Math.ceil((compLeft + compWidth) * guiScale * sf);
                    int newW = Math.max(0, newRight - newLeft);
                    int newGlBottom = (int) Math.floor((screenH - ((groupContentTop + revealHeight) * guiScale)) * sf);
                    int newGlTop = (int) Math.ceil((screenH - (groupContentTop * guiScale)) * sf);
                    int newH = Math.max(0, newGlTop - newGlBottom);
                    pushScissor(newLeft, newGlBottom, newW, newH);
                    while (i < j) {
                        Component child = settings.get(i);
                        if (isVisibleBase(child) && getOwningGroup(child) == group) {
                            child.render();
                        }
                        i++;
                    }
                    popScissor();
                } else {
                    i = j;
                }
            } else {
                i++;
            }
        }
    }

    private static final int MAX_SCISSOR_DEPTH = 4;
    private final int[][] scissorStack = new int[MAX_SCISSOR_DEPTH][5];
    private int scissorDepth = 0;

    private void pushScissor(int x, int y, int w, int h) {
        boolean wasEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int[] saved = scissorStack[scissorDepth++];
        if (wasEnabled) {
            SCISSOR_BOX.clear();
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX, SCISSOR_BOX);
            saved[0] = 1;
            saved[1] = SCISSOR_BOX.get(0);
            saved[2] = SCISSOR_BOX.get(1);
            saved[3] = SCISSOR_BOX.get(2);
            saved[4] = SCISSOR_BOX.get(3);
            int ix = Math.max(saved[1], x);
            int iy = Math.max(saved[2], y);
            int iw = Math.max(0, Math.min(saved[1] + saved[3], x + w) - ix);
            int ih = Math.max(0, Math.min(saved[2] + saved[4], y + h) - iy);
            GL11.glScissor(ix, iy, iw, ih);
        } else {
            saved[0] = 0;
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(x, y, w, h);
        }
    }

    private void popScissor() {
        int[] saved = scissorStack[--scissorDepth];
        if (saved[0] == 1) {
            GL11.glScissor(saved[1], saved[2], saved[3], saved[4]);
        } else {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    private boolean isVisibleBase(Component component) {
        return component.isBaseVisible();
    }

    private boolean hasModuleHeader() {
        return !categoryManager;
    }

    private float getCollapsedHeight() {
        return hasModuleHeader() ? 18f : 0f;
    }

    private float getSettingStartOffset() {
        return hasModuleHeader() ? 14f : 0f;
    }
}
