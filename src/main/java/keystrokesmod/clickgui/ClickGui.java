package keystrokesmod.clickgui;

import keystrokesmod.Raven;
import keystrokesmod.clickgui.components.Component;
import keystrokesmod.clickgui.components.FocusableTextComponent;
import keystrokesmod.clickgui.components.impl.BindComponent;
import keystrokesmod.clickgui.components.impl.CategoryComponent;
import keystrokesmod.clickgui.components.impl.ModuleComponent;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.client.CommandLine;
import keystrokesmod.module.impl.client.Gui;
import keystrokesmod.utility.CommandHandler;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Timer;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.font.RavenFontRenderer;
import keystrokesmod.utility.shader.BlurUtils;
import keystrokesmod.utility.shader.RoundedUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.config.GuiButtonExt;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ClickGui extends GuiScreen {
    private ScheduledFuture sf;
    private Timer logoSmoothWidth;
    private Timer logoSmoothLength;
    private Timer smoothEntity;
    private Timer backgroundFade;
    private Timer blurSmooth;
    private ScaledResolution sr;
    private GuiButtonExt commandLineSend;
    private GuiTextField commandLineInput;
    private GuiTextField searchField;
    public static ArrayList<CategoryComponent> categories;
    private int actualScreenWidth;
    private int actualScreenHeight;
    private double previousScale;
    private static boolean isNotFirstOpen;
    private boolean pendingScaleRefresh;

    private float bgParticleTime;
    private static final Color BG_COLOR_1 = new Color(10, 10, 22);
    private static final Color BG_COLOR_2 = new Color(20, 15, 30);
    private static final Color SEARCH_BG = new Color(25, 25, 45, 200);
    private static final Color SEARCH_BG_FOCUSED = new Color(35, 35, 65, 220);
    private static final int ACCENT = new Color(95, 235, 255).getRGB();

    public ClickGui() {
        categories = new ArrayList();
        int y = 5;
        for (Module.category c : Module.category.values()) {
            CategoryComponent cc = new CategoryComponent(c);
            cc.setY(y, false);
            categories.add(cc);
            y += 20;
        }
    }

    public void initMain() {
        (this.logoSmoothWidth = this.smoothEntity = this.blurSmooth = this.backgroundFade = new Timer(500.0F)).start();
        this.sf = Raven.getScheduledExecutor().schedule(() -> {
            (this.logoSmoothLength = new Timer(650.0F)).start();
        }, 650L, TimeUnit.MILLISECONDS);
    }

    @Override
    public void initGui() {
        super.initGui();
        double configuredScale = getConfiguredGuiScale();
        if (!isNotFirstOpen) {
            isNotFirstOpen = true;
            this.previousScale = configuredScale;
        }
        for (CategoryComponent c : categories) {
            c.setScreenSize(this.width, this.height);
        }
        if (Double.compare(this.previousScale, configuredScale) != 0) {
            for (CategoryComponent c : categories) {
                c.limitPositions();
            }
        }
        for (CategoryComponent c : categories) {
            if (c.category == Module.category.profiles) {
                c.reloadModules(true);
            } else if (c.category == Module.category.scripts) {
                c.reloadModules(false);
            } else {
                c.reloadModules();
            }
        }
        (this.searchField = new GuiTextField(0, this.mc.fontRendererObj, this.width - 115, 10, 105, 14)).setMaxStringLength(32);
        this.searchField.setText("");
        (this.commandLineInput = new GuiTextField(1, this.mc.fontRendererObj, 22, this.height - 100, 150, 20)).setMaxStringLength(256);
        this.buttonList.add(this.commandLineSend = new GuiButtonExt(2, 22, this.height - 70, 150, 20, "Send"));
        this.commandLineSend.visible = CommandLine.opened;
        this.previousScale = configuredScale;
    }

    private List<CategoryComponent> getCategoriesInRenderOrder() {
        List<CategoryComponent> renderOrder = new ArrayList<>(categories);
        renderOrder.sort(Comparator.comparingLong(c -> c.lastInteractedTime));
        return renderOrder;
    }

    private CategoryComponent getTopmostUnderCursor(List<CategoryComponent> renderOrder, int x, int y) {
        for (int i = renderOrder.size() - 1; i >= 0; i--) {
            if (renderOrder.get(i).overRect(x, y)) {
                return renderOrder.get(i);
            }
        }
        return null;
    }

    private void drawBackground() {
        int w = this.width;
        int h = this.height;

        if (Gui.backgroundBlur.getInput() != 0) {
            BlurUtils.prepareBlur();
            RoundedUtils.drawRound(0, 0, w, h, 0.0f, true, Color.black);
            float inputToRange = (float) (3 * ((Gui.backgroundBlur.getInput() + 35) / 100));
            BlurUtils.blurEnd(2, this.blurSmooth.getValueFloat(0, inputToRange, 1));
        }

        float bgAlpha = (float) (this.backgroundFade.getValueFloat(0.0F, 0.7F, 2) * 255.0F);
        if (Gui.darkBackground.isToggled() && bgAlpha > 1) {
            int alpha = (int) Math.min(220, bgAlpha);
            RenderUtils.drawGradientRect(0, 0, w, h, BG_COLOR_1.getRGB(), BG_COLOR_2.getRGB(), alpha);
        }

        drawAmbientParticles(w, h);
    }

    private void drawAmbientParticles(int w, int h) {
        bgParticleTime += 0.01f;
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glShadeModel(GL11.GL_SMOOTH);

        for (int i = 0; i < 8; i++) {
            float seed = i * 1.7f;
            float px = (float) (w * 0.1 + (Math.sin(bgParticleTime * 0.5 + seed) * 0.5 + 0.5) * w * 0.8);
            float py = (float) (h * 0.1 + (Math.cos(bgParticleTime * 0.3 + seed * 1.3) * 0.5 + 0.5) * h * 0.8);
            float size = (float) (2 + Math.sin(bgParticleTime + seed) * 1.5 + 3);
            float particleAlpha = (float) (0.05 + (Math.sin(bgParticleTime * 0.7 + seed * 2.1) * 0.5 + 0.5) * 0.08);

            int c = Utils.getChroma(2, (long) (seed * 200));
            GL11.glColor4f((c >> 16 & 0xFF) / 255f, (c >> 8 & 0xFF) / 255f, (c & 0xFF) / 255f, particleAlpha);
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glVertex2d(px, py);
            for (int j = 0; j <= 12; j++) {
                double angle = Math.PI * 2 * j / 12;
                GL11.glVertex2d(px + Math.cos(angle) * size, py + Math.sin(angle) * size);
            }
            GL11.glEnd();
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glPopMatrix();
    }

    private void drawWatermark() {
        if (Gui.removeWatermark.isToggled()) return;

        int h = this.height / 4;
        int wd = this.width / 2;
        int w_c = 30 - this.logoSmoothWidth.getValueInt(0, 30, 3);

        RavenFontRenderer titleRenderer = Gui.getClickGuiHeaderFontRenderer();
        float scale = 1.5f;

        GlStateManager.pushMatrix();
        GlStateManager.translate(wd, h - 5, 0);
        GlStateManager.scale(scale, scale, 1);

        String[] chars = {"r", "a", "v", "e", "n"};
        long[] delays = {1500L, 1200L, 900L, 600L, 300L};
        for (int i = 0; i < chars.length; i++) {
            float xOff = -25 + i * 10 - w_c * 0.3f;
            titleRenderer.drawString(chars[i], xOff, -20 + i * 3, Utils.getChroma(2L, delays[i]), true);
        }

        titleRenderer.drawString("bs+", 30 + w_c * 0.3f, 10, Utils.getChroma(2L, 0L), true);

        GlStateManager.popMatrix();

        float lineY1 = h - 30;
        float lineY2 = h + 43;
        float lineX = wd;

        RenderUtils.drawVerticalGradientRect(lineX - 1, lineY1, lineX, lineY2,
                new Color(95, 235, 255, 100).getRGB(),
                new Color(68, 102, 250, 100).getRGB());
        RenderUtils.drawVerticalGradientRect(lineX, lineY1, lineX + 1, lineY2,
                new Color(95, 235, 255, 60).getRGB(),
                new Color(68, 102, 250, 60).getRGB());

        if (this.logoSmoothLength != null) {
            int r = this.logoSmoothLength.getValueInt(0, 20, 2);
            RenderUtils.drawHorizontalGradientRect(lineX - 10, lineY1 - 1, lineX - 10 + r, lineY1,
                    ACCENT, new Color(ACCENT).getRGB());
            RenderUtils.drawHorizontalGradientRect(lineX + 10 - r, lineY2, lineX + 10, lineY2 + 1,
                    new Color(ACCENT).getRGB(), ACCENT);
        }
    }

    /*
     * drawScreen: MC passes mouseX/mouseY already in this GuiScreen's coordinate space.
     * Rendering is inside GlStateManager.scale(renderScale), but the logical positions
     * of all components (category.x, module.yPos, etc.) are in the SAME space as mouseX/Y.
     * No conversion needed. Use x, y directly.
     */
    public void drawScreen(int x, int y, float p) {
        drawBackground();

        GlStateManager.pushMatrix();
        GlStateManager.scale(getRenderScale(), getRenderScale(), 1.0D);

        drawWatermark();

        boolean hasSearch = !this.searchField.getText().isEmpty();
        List<CategoryComponent> renderOrder = getCategoriesInRenderOrder();
        CategoryComponent topmost = getTopmostUnderCursor(renderOrder, x, y);

        for (CategoryComponent c : renderOrder) {
            c.renderWithSearch(this.fontRendererObj, hasSearch);
            c.mousePosition(x, y, c == topmost);
            for (Component m : c.getModules()) {
                if (matchesSearch(m)) {
                    m.drawScreen(x, y);
                }
            }
        }

        drawSearchBar(x, y);

        GL11.glColor3f(1.0f, 1.0f, 1.0f);
        if (!Gui.removePlayerModel.isToggled()) {
            GlStateManager.pushMatrix();
            GlStateManager.disableBlend();
            GuiInventory.drawEntityOnScreen(
                this.width + 15 - this.smoothEntity.getValueInt(0, 40, 2),
                this.height - 10, 40,
                (float)(this.width - 25 - x),
                (float)(this.height - 50 - y),
                this.mc.thePlayer);
            GlStateManager.enableBlend();
            GlStateManager.popMatrix();
        }

        if (CommandLine.opened) {
            drawCommandLine();
        } else if (CommandLine.closed) {
            CommandLine.closed = false;
        }

        GlStateManager.popMatrix();
    }

    private void drawSearchBar(int mouseX, int mouseY) {
        boolean focused = this.searchField.isFocused();
        int barColor = focused ? SEARCH_BG_FOCUSED.getRGB() : SEARCH_BG.getRGB();

        RenderUtils.drawRoundedRectangle(this.width - 117, 8, this.width - 8, 24, 5, barColor);
        if (focused) {
            RenderUtils.drawRoundedOutline(this.width - 117, 8, this.width - 8, 24, 5, 1f, ACCENT);
        }

        this.searchField.drawTextBox();
        if (this.searchField.getText().isEmpty() && !this.searchField.isFocused()) {
            RavenFontRenderer sr = Gui.getClickGuiSettingFontRenderer();
            GlStateManager.pushMatrix();
            GlStateManager.scale(0.5f, 0.5f, 0.5f);
            sr.drawString("Search modules...", (this.width - 112) * 2, 12 * 2, new Color(140, 140, 160).getRGB(), false);
            GlStateManager.popMatrix();
        }
    }

    private void drawCommandLine() {
        int r;
        if (CommandLine.animate.isToggled()) {
            r = CommandLine.animation.getValueInt(0, 200, 2);
        } else {
            r = 200;
        }
        if (CommandLine.closed) {
            r = 200 - r;
            if (r == 0) {
                CommandLine.closed = false;
                CommandLine.opened = false;
                this.commandLineSend.visible = false;
            }
        }
        drawRect(0, 0, r, this.height, -1089466352);
        this.drawHorizontalLine(0, r - 1, (this.height - 345), -1);
        this.drawHorizontalLine(0, r - 1, (this.height - 115), -1);
        drawRect(r - 1, 0, r, this.height, ACCENT);
        CommandHandler.renderCommandOutput(this.fontRendererObj, this.height, r, this.sr.getScaleFactor());
        int x2 = r - 178;
        this.commandLineInput.xPosition = x2;
        this.commandLineSend.xPosition = x2;
        this.commandLineInput.drawTextBox();
        int clMouseX = Mouse.getEventX() * this.width / mc.displayWidth;
        int clMouseY = this.height - Mouse.getEventY() * this.height / mc.displayHeight - 1;
        super.drawScreen(clMouseX, clMouseY, 0);
    }

    public void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        List<CategoryComponent> inputOrder = new ArrayList<>(categories);
        inputOrder.sort((a, b) -> Long.compare(b.lastInteractedTime, a.lastInteractedTime));
        CategoryComponent topmostCategory = null;
        for (CategoryComponent category : inputOrder) {
            if (category.overRect(mouseX, mouseY)) {
                topmostCategory = category;
                break;
            }
        }

        if (topmostCategory != null) {
            topmostCategory.markInteracted();
        }

        if (mouseButton == 0) {
            for (CategoryComponent category : categories) {
                category.overTitle(false);
            }
            if (topmostCategory != null && topmostCategory.draggable(mouseX, mouseY)) {
                topmostCategory.overTitle(true);
                topmostCategory.xx = mouseX - topmostCategory.getX();
                topmostCategory.yy = mouseY - topmostCategory.getY();
                topmostCategory.dragging = true;
            }
        }

        if (mouseButton == 1 && topmostCategory != null && topmostCategory.overTitle(mouseX, mouseY)) {
            topmostCategory.mouseClicked(!topmostCategory.isOpened());
        }

        if (topmostCategory != null && topmostCategory.isOpened() && !topmostCategory.getModules().isEmpty() && !topmostCategory.overTitle(mouseX, mouseY)) {
            for (ModuleComponent component : topmostCategory.getModules()) {
                if (matchesSearch(component)) {
                    if (component.onClick(mouseX, mouseY, mouseButton)) {
                        break;
                    }
                }
            }
        }

        if (CommandLine.opened) {
            this.commandLineInput.mouseClicked(mouseX, mouseY, mouseButton);
            super.mouseClicked(mouseX, mouseY, mouseButton);
        }

        if (mouseButton == 0 || mouseButton == 1) {
            this.searchField.mouseClicked(mouseX, mouseY, mouseButton);
            FocusableTextComponent focusedComponent = findFocusedTextComponentAt(mouseX, mouseY);
            enforceSingleFocusedTextInput(focusedComponent);
        }
    }

    public void mouseReleased(int x, int y, int button) {
        if (button == 0) {
            for (CategoryComponent category : categories) {
                category.overTitle(false);
                if (category.isOpened() && !category.getModules().isEmpty()) {
                    for (Component module : category.getModules()) {
                        module.mouseReleased(x, y, button);
                    }
                }
            }
        }
        if (pendingScaleRefresh) {
            pendingScaleRefresh = false;
            refreshLayoutForConfiguredScale();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheelInput = Mouse.getDWheel();
        if (wheelInput != 0) {
            int mouseX = Mouse.getEventX() * this.width / mc.displayWidth;
            int mouseY = this.height - Mouse.getEventY() * this.height / mc.displayHeight - 1;
            for (CategoryComponent category : categories) {
                category.onScroll(wheelInput, mouseX, mouseY);
            }
        }
    }

    public void refreshAfterProfileLoad() {
        if (mc == null) {
            mc = Minecraft.getMinecraft();
        }
        refreshLayoutForConfiguredScale();
    }

    @Override
    public void setWorldAndResolution(Minecraft mc, final int width, final int height) {
        this.mc = mc;
        this.itemRender = mc.getRenderItem();
        this.fontRendererObj = mc.fontRendererObj;
        refreshScaledResolution();
        if (!MinecraftForge.EVENT_BUS.post(new GuiScreenEvent.InitGuiEvent.Pre(this, this.buttonList))) {
            this.buttonList.clear();
            this.initGui();
        }
        MinecraftForge.EVENT_BUS.post(new GuiScreenEvent.InitGuiEvent.Post(this, this.buttonList));
    }

    @Override
    public void keyTyped(char t, int k) {
        FocusableTextComponent activeTextInput = getActiveFocusedTextInput();
        if (k == Keyboard.KEY_ESCAPE) {
            if (activeTextInput != null) {
                activeTextInput.unfocusTextInput();
                return;
            }
            if (!binding()) {
                this.mc.displayGuiScreen(null);
                return;
            }
        }

        if (activeTextInput != null) {
            for (CategoryComponent category : categories) {
                if (!category.isOpened() || category.getModules().isEmpty()) continue;
                for (Component module : category.getModules()) {
                    module.keyTyped(t, k);
                }
            }
            return;
        }

        for (CategoryComponent category : categories) {
            if (category.isOpened() && !category.getModules().isEmpty()) {
                for (Component module : category.getModules()) {
                    module.keyTyped(t, k);
                }
            }
        }
        if (CommandLine.opened) {
            String cm = this.commandLineInput.getText();
            if (k == 28 && !cm.isEmpty()) {
                CommandHandler.runCommand(this.commandLineInput.getText());
                this.commandLineInput.setText("");
                return;
            }
            this.commandLineInput.textboxKeyTyped(t, k);
        }

        if (this.searchField.isFocused()) {
            String prevText = this.searchField.getText();
            this.searchField.textboxKeyTyped(t, k);
            if (!this.searchField.getText().equals(prevText)) {
                for (CategoryComponent c : categories) {
                    c.updateHeight();
                }
            }
        }
    }

    public boolean matchesSearch(Component component) {
        if (this.searchField.getText().isEmpty()) return true;
        if (component instanceof ModuleComponent) {
            return ((ModuleComponent) component).mod.getName().toLowerCase().contains(this.searchField.getText().toLowerCase());
        }
        return false;
    }

    public void actionPerformed(GuiButton b) {
        if (b == this.commandLineSend) {
            CommandHandler.runCommand(this.commandLineInput.getText());
            this.commandLineInput.setText("");
        }
    }

    @Override
    public void onGuiClosed() {
        this.logoSmoothLength = null;
        if (this.sf != null) {
            this.sf.cancel(true);
            this.sf = null;
        }
        for (CategoryComponent c : categories) {
            c.dragging = false;
            c.onGuiClosed();
            for (Component m : c.getModules()) {
                m.onGuiClosed();
            }
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private boolean binding() {
        for (CategoryComponent c : categories) {
            for (ModuleComponent m : c.getModules()) {
                for (Component component : m.settings) {
                    if (component instanceof BindComponent && ((BindComponent) component).isBinding) return true;
                    if (component instanceof FocusableTextComponent && ((FocusableTextComponent) component).isTextInputFocused()) return true;
                }
            }
        }
        return false;
    }

    private FocusableTextComponent getActiveFocusedTextInput() {
        FocusableTextComponent activeComponent = null;
        for (CategoryComponent category : categories) {
            for (ModuleComponent module : category.getModules()) {
                for (Component component : module.settings) {
                    if (component instanceof FocusableTextComponent) {
                        FocusableTextComponent tc = (FocusableTextComponent) component;
                        if (tc.isTextInputFocused()) {
                            if (activeComponent == null) {
                                activeComponent = tc;
                            } else {
                                tc.unfocusTextInput();
                            }
                        }
                    }
                }
            }
        }
        return activeComponent;
    }

    private FocusableTextComponent findFocusedTextComponentAt(int mouseX, int mouseY) {
        List<CategoryComponent> inputOrder = new ArrayList<>(categories);
        inputOrder.sort((a, b) -> Long.compare(b.lastInteractedTime, a.lastInteractedTime));
        for (CategoryComponent category : inputOrder) {
            if (!category.isOpened() || !category.overRect(mouseX, mouseY)) continue;
            for (ModuleComponent module : category.getModules()) {
                for (Component component : module.settings) {
                    if (component instanceof FocusableTextComponent) {
                        FocusableTextComponent tc = (FocusableTextComponent) component;
                        if (tc.isTextInputFocused() && tc.containsClick(mouseX, mouseY)) {
                            return tc;
                        }
                    }
                }
            }
        }
        return null;
    }

    private void enforceSingleFocusedTextInput(FocusableTextComponent keep) {
        for (CategoryComponent category : categories) {
            for (ModuleComponent module : category.getModules()) {
                for (Component component : module.settings) {
                    if (component instanceof FocusableTextComponent) {
                        FocusableTextComponent tc = (FocusableTextComponent) component;
                        if (tc != keep && tc.isTextInputFocused()) {
                            tc.unfocusTextInput();
                        }
                    }
                }
            }
        }
    }

    public void onSliderChange() {
        for (CategoryComponent c : categories) {
            for (ModuleComponent m : c.getModules()) {
                m.onSliderChange();
            }
        }
    }

    public void requestScaleRefresh() {
        this.pendingScaleRefresh = true;
    }

    private void refreshLayoutForConfiguredScale() {
        refreshScaledResolution();
        for (CategoryComponent c : categories) {
            c.setScreenSize(this.width, this.height);
            c.limitPositions();
        }
        this.buttonList.clear();
        initGui();
    }

    private void refreshScaledResolution() {
        this.sr = new ScaledResolution(mc);
        this.actualScreenWidth = this.sr.getScaledWidth();
        this.actualScreenHeight = this.sr.getScaledHeight();
        double targetScaleFactor = getTargetGuiScaleFactor();
        this.width = Math.max(1, MathHelper.ceiling_double_int((double) mc.displayWidth / targetScaleFactor));
        this.height = Math.max(1, MathHelper.ceiling_double_int((double) mc.displayHeight / targetScaleFactor));
    }

    private int getMaximumGuiScaleFactor() {
        int scaleFactor = 1;
        while (mc.displayWidth / (scaleFactor + 1) >= 320 && mc.displayHeight / (scaleFactor + 1) >= 240) {
            ++scaleFactor;
        }
        if (mc.isUnicode() && scaleFactor % 2 != 0 && scaleFactor != 1) {
            --scaleFactor;
        }
        return scaleFactor;
    }

    private double getTargetGuiScaleFactor() {
        return Math.max(1.0D, Math.min(getMaximumGuiScaleFactor(), getConfiguredGuiScale() * 2.0D));
    }

    public double getRenderScale() {
        return actualScreenWidth <= 0 || width <= 0 ? 1.0D : (double) actualScreenWidth / (double) width;
    }

    public static double getActiveRenderScale() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft.currentScreen instanceof ClickGui ? ((ClickGui) minecraft.currentScreen).getRenderScale() : 1.0D;
    }

    private double getConfiguredGuiScale() {
        return Gui.getClickGuiScale();
    }
}
