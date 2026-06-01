package keystrokesmod.module.impl.render;

import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.combat.AntiKnockback;
import keystrokesmod.module.impl.combat.Velocity;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.module.setting.impl.TextSetting;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Theme;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.font.FontManager;
import keystrokesmod.utility.font.RavenFontRenderer;
import keystrokesmod.utility.notification.NotificationManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import org.lwjgl.opengl.GL11;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.fml.client.config.GuiButtonExt;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent;

import java.awt.Color;
import java.io.IOException;

public class HUD extends Module {
    private static final String[] COLOR_MODES = new String[] { "Static", "Gradient", "Rainbow" };
    private static final String[] WAVE_AXES = new String[] { "Vertical", "Horizontal" };
    private static final String[] VERTICAL_WAVE_DIRECTIONS = new String[] { "Down", "Up" };
    private static final String[] HORIZONTAL_WAVE_DIRECTIONS = new String[] { "Left", "Right" };
    /** Horizontal wave: scales screen X (center of row) into phase; larger = faster change across X. */
    private static final double HUD_WAVE_HORIZONTAL_X_SCALE = 0.35;
    private static final long HUD_RAINBOW_PERIOD_MS = 7500L;
    private static final double HUD_WAVE_ANGLE_SCALE = 0.12;

    public static SliderSetting colorMode;
    public static ColorSetting hudColor;
    public static ColorSetting hudColor2;
    public static SliderSetting waveAxis;
    public static SliderSetting verticalWaveDirection;
    public static SliderSetting horizontalWaveDirection;
    public static SliderSetting waveSpeed;
    public static SliderSetting waveLength;
    public static SliderSetting font;
    public static SliderSetting fontSize;
    private static SliderSetting outline;
    public static ButtonSetting alphabeticalSort;
    private static ButtonSetting drawBackground;
    private static ButtonSetting textShadow;
    private static ButtonSetting alignRight;
    private static ButtonSetting lowercase;
    public static ButtonSetting showInfo;
    private static ButtonSetting showWatermark;
    private static TextSetting watermarkText;
    public static String watermarkName = "Raven bS+";
    private static ButtonSetting showBgImage;
    private static SliderSetting bgImageOpacity;
    private static TextSetting bgImagePath;
    private static SliderSetting bgImageCornerRadius;
    private static ButtonSetting gradientBg;
    private static SliderSetting slideInSpeed;
    public static String backgroundImagePath = "C:\\Users\\deniz\\Desktop\\OIP-3759094112.jpg";
    public static ResourceLocation backgroundImageTexture = null;
    public static int backgroundImageWidth = 0;
    public static int backgroundImageHeight = 0;
    public static boolean backgroundImageLoaded = false;
    private static final float DEFAULT_POS_X = 5.0f;
    private static final float DEFAULT_POS_Y = 70.0f;
    public static float posX = DEFAULT_POS_X;
    public static float posY = DEFAULT_POS_Y;
    private static float relativePosX = Float.NaN;
    private static float relativePosY = Float.NaN;

    private static final String[] OUTLINE_MODES = new String[] { "None", "Full", "Side" };
    private static final String[] HUD_FONT_OPTIONS = FontManager.getHudFontOptions();
    private static final int BACKGROUND_COLOR = new Color(0, 0, 0, 110).getRGB();

    private boolean isAlphabeticalSort;
    private boolean canShowInfo;
    private String lastHudFontName = "";
    private float lastHudFontScale = -1.0f;

    public HUD() {
        super("HUD", Module.category.render);
        this.registerSetting(showWatermark = new ButtonSetting("Show watermark", true));
        this.registerSetting(watermarkText = new TextSetting("Change Watermark", "Raven bS+", "Enter watermark name...", 32, () -> {
            String newName = watermarkText.getText().trim();
            if (!newName.isEmpty()) {
                watermarkName = newName;
            }
        }));
        this.registerSetting(showBgImage = new ButtonSetting("Show background image", true));
        this.registerSetting(bgImageOpacity = new SliderSetting("Bg image opacity", 80.0, 0.0, 255.0, 1.0));
        this.registerSetting(bgImageCornerRadius = new SliderSetting("Bg image radius", 6.0, 0.0, 20.0, 0.5));
        this.registerSetting(bgImagePath = new TextSetting("Bg image path", backgroundImagePath, "Path to image file...", 256, () -> {
            String newPath = bgImagePath.getText().trim();
            if (!newPath.isEmpty()) {
                backgroundImagePath = newPath;
                loadBackgroundImage();
            }
        }));
        this.registerSetting(new ButtonSetting("Reload bg image", () -> loadBackgroundImage()));
        this.registerSetting(colorMode = new SliderSetting("Color mode", 0, COLOR_MODES));
        this.registerSetting(hudColor = new ColorSetting("Color", 255, 255, 255));
        this.registerSetting(hudColor2 = new ColorSetting("Color 2", 85, 85, 255));
        this.registerSetting(waveAxis = new SliderSetting("Wave axis", 0, WAVE_AXES));
        this.registerSetting(verticalWaveDirection = new SliderSetting("Wave direction", 0, VERTICAL_WAVE_DIRECTIONS));
        this.registerSetting(horizontalWaveDirection = new SliderSetting("Wave direction", 0, HORIZONTAL_WAVE_DIRECTIONS));
        this.registerSetting(waveSpeed = new SliderSetting("Wave speed", 1.0, 0.1, 5.0, 0.1));
        this.registerSetting(waveLength = new SliderSetting("Wave length", 1.0, 0.5, 5.0, 0.1));
        this.registerSetting(font = new SliderSetting("Font", 0, HUD_FONT_OPTIONS));
        this.registerSetting(fontSize = new SliderSetting("Scale", 0.9, 0.5, 2.0, 0.1));
        this.registerSetting(outline = new SliderSetting("Outline", 0, OUTLINE_MODES));
        this.registerSetting(new ButtonSetting("Edit position", () -> mc.displayGuiScreen(new EditScreen())));
        this.registerSetting(alignRight = new ButtonSetting("Align right", true));
        this.registerSetting(alphabeticalSort = new ButtonSetting("Alphabetical sort", false));
        this.registerSetting(drawBackground = new ButtonSetting("Draw background", false));
        this.registerSetting(gradientBg = new ButtonSetting("Gradient background", false));
        this.registerSetting(slideInSpeed = new SliderSetting("Slide in speed", 1.0, 0.0, 3.0, 0.1));
        this.registerSetting(textShadow = new ButtonSetting("Text shadow", true));
        this.registerSetting(lowercase = new ButtonSetting("Lowercase", false));
        this.registerSetting(showInfo = new ButtonSetting("Show module info", true));

        // Try to load the default background image
        loadBackgroundImage();
    }

    @Override
    public void guiUpdate() {
        int mode = colorMode == null ? 0 : (int) colorMode.getInput();
        if (hudColor != null) {
            hudColor.setVisible(mode == 0 || mode == 1, this);
        }
        if (hudColor2 != null) {
            hudColor2.setVisible(mode == 1, this);
        }
        boolean showWaveSettings = mode == 1 || mode == 2;
        boolean verticalAxis = hudWaveIsVertical();
        if (waveAxis != null) {
            waveAxis.setVisible(showWaveSettings, this);
        }
        if (verticalWaveDirection != null) {
            verticalWaveDirection.setVisible(showWaveSettings && verticalAxis, this);
        }
        if (horizontalWaveDirection != null) {
            horizontalWaveDirection.setVisible(showWaveSettings && !verticalAxis, this);
        }
        if (waveSpeed != null) {
            waveSpeed.setVisible(showWaveSettings, this);
        }
        if (waveLength != null) {
            waveLength.setVisible(showWaveSettings, this);
        }
        if (gradientBg != null) {
            gradientBg.setVisible(drawBackground.isToggled(), this);
        }
        if (bgImageOpacity != null) {
            bgImageOpacity.setVisible(showBgImage.isToggled(), this);
        }
        if (bgImageCornerRadius != null) {
            bgImageCornerRadius.setVisible(showBgImage.isToggled(), this);
        }
        if (bgImagePath != null) {
            bgImagePath.setVisible(showBgImage.isToggled(), this);
        }
    }

    @Override
    public void onEnable() {
        instance = this;
        guiUpdate();
        ModuleManager.sort();
    }

    @Override
    public void guiButtonToggled(ButtonSetting buttonSetting) {
        if (buttonSetting == alphabeticalSort || buttonSetting == showInfo) {
            ModuleManager.sort();
        }
    }

    public static void loadBackgroundImage() {
        backgroundImageTexture = null;
        backgroundImageWidth = 0;
        backgroundImageHeight = 0;
        backgroundImageLoaded = false;
        try {
            File file = new File(backgroundImagePath);
            if (!file.exists()) {
                return;
            }
            BufferedImage image = ImageIO.read(file);
            if (image == null) {
                return;
            }
            int w = image.getWidth();
            int h = image.getHeight();
            int maxDim = 512;
            if (w > maxDim || h > maxDim) {
                double scale = Math.min((double) maxDim / w, (double) maxDim / h);
                int newW = Math.max(1, (int) (w * scale));
                int newH = Math.max(1, (int) (h * scale));
                BufferedImage resized = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g = resized.createGraphics();
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(image, 0, 0, newW, newH, null);
                g.dispose();
                image = resized;
                w = newW;
                h = newH;
            }
            String name = "raven_bg_" + backgroundImagePath.hashCode();
            backgroundImageTexture = mc.renderEngine.getDynamicTextureLocation(name, new DynamicTexture(image));
            backgroundImageWidth = w;
            backgroundImageHeight = h;
            backgroundImageLoaded = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Slide-in animation tracking
    private final java.util.Map<Module, Long> moduleEnableTimestamps = new java.util.HashMap<>();
    private static final long SLIDE_IN_DURATION_MS = 250L;
    private static HUD instance;

    public static void onModuleEnabled(Module module) {
        if (instance != null) {
            instance.moduleEnableTimestamps.put(module, System.currentTimeMillis());
        }
    }

    public static void onModuleDisabled(Module module) {
        if (instance != null) {
            instance.moduleEnableTimestamps.put(module, System.currentTimeMillis());
        }
    }

    @SubscribeEvent
    public void onRenderTick(RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Utils.nullCheck()) {
            return;
        }

        if (isAlphabeticalSort != alphabeticalSort.isToggled()) {
            isAlphabeticalSort = alphabeticalSort.isToggled();
            ModuleManager.sort();
        }

        if (canShowInfo != showInfo.isToggled()) {
            canShowInfo = showInfo.isToggled();
            ModuleManager.sort();
        }

        String currentFontName = getSelectedFontName();
        float currentFontScale = getSelectedFontScale();
        if (!currentFontName.equals(lastHudFontName) || Float.compare(currentFontScale, lastHudFontScale) != 0) {
            lastHudFontName = currentFontName;
            lastHudFontScale = currentFontScale;
            ModuleManager.sort();
        }

        if (mc.currentScreen != null || mc.gameSettings.showDebugInfo) {
            return;
        }

        // Render watermark
        if (showWatermark != null && showWatermark.isToggled()) {
            renderWatermark();
        }

        syncPositionToResolution();

        for (Module module : ModuleManager.organizedModules) {
            module.getInfoUpdate();
            if (Module.sort) {
                break;
            }
        }

        if (Module.sort) {
            ModuleManager.sort();
        }
        Module.sort = false;

        RavenFontRenderer hudFont = getHudFontRenderer();
        int textTopOffset = hudFont.getTextTopOffset();
        int textBottomOffset = hudFont.getTextBottomOffset();
        int horizontalTextPadding = getHudHorizontalTextPadding();
        int textTopPadding = getHudTextTopPadding();
        int textBottomPadding = getHudTextBottomPadding();
        int outlineThickness = getHudOutlineThickness();
        int rowHeight = getHudRowHeight(textTopOffset, textBottomOffset, textTopPadding, textBottomPadding);
        float yPos = posY;
        double verticalWaveAccum = 0.0;
        boolean firstVisibleRow = true;
        String previousModule = "";
        double lastOutlineLeft = 0.0;
        double lastOutlineRight = 0.0;
        double lastBackgroundBottom = 0.0;
        boolean removeVelocity = ModuleManager.antiKnockback.isEnabled();

        // Track total arraylist bounds for background image
        int totalListWidth = 0;
        int renderedRows = 0;
        float listStartY = yPos;

        try {
            for (Module module : ModuleManager.organizedModules) {
                if (!module.isEnabled() || module == this || shouldSkipModule(module, removeVelocity)) {
                    continue;
                }

                String moduleName = getHudRenderText(module);
                int moduleWidth = hudFont.getStringWidth(moduleName);
                float xPos = posX;
                float textY = getHudTextY(yPos, textTopOffset, textTopPadding);
                double backgroundLeft = xPos - horizontalTextPadding;
                double backgroundRight = xPos + moduleWidth + horizontalTextPadding;
                double backgroundTop = yPos;
                double backgroundBottom = yPos + rowHeight;
                double outlineLeft = backgroundLeft - outlineThickness;
                double outlineRight = backgroundRight + outlineThickness;
                double outlineTop = backgroundTop - outlineThickness;

                if (alignRight.isToggled()) {
                    xPos -= moduleWidth;
                    backgroundLeft = xPos - horizontalTextPadding;
                    backgroundRight = xPos + moduleWidth + horizontalTextPadding;
                    outlineLeft = backgroundLeft - outlineThickness;
                    outlineRight = backgroundRight + outlineThickness;
                }

                // Slide-in animation offset
                float slideOffset = 0.0f;
                float slideSpeedMultiplier = (float) Math.max(0.1, slideInSpeed.getInput());
                Long enabledAt = moduleEnableTimestamps.get(module);
                if (enabledAt != null && slideSpeedMultiplier > 0) {
                    long elapsed = System.currentTimeMillis() - enabledAt;
                    long animDuration = (long) (SLIDE_IN_DURATION_MS / slideSpeedMultiplier);
                    if (elapsed < animDuration) {
                        float progress = (float) elapsed / animDuration;
                        float eased = 1.0f - (float) Math.pow(1.0 - progress, 3.0);
                        slideOffset = (1.0f - eased) * 20.0f;
                    } else {
                        moduleEnableTimestamps.remove(module);
                    }
                }
                if (alignRight.isToggled()) {
                    backgroundLeft += slideOffset;
                    backgroundRight += slideOffset;
                    outlineLeft += slideOffset;
                    outlineRight += slideOffset;
                    xPos += slideOffset;
                } else {
                    backgroundLeft -= slideOffset;
                    backgroundRight -= slideOffset;
                    outlineLeft -= slideOffset;
                    outlineRight -= slideOffset;
                    xPos -= slideOffset;
                }

                double rowCenterX = (backgroundLeft + backgroundRight) * 0.5;
                double wavePhase = hudWavePhase(verticalWaveAccum, rowCenterX);
                int color = getHudColor(wavePhase);

                if (drawBackground.isToggled()) {
                    if (gradientBg != null && gradientBg.isToggled()) {
                        int topColor = new Color(0, 0, 0, 90).getRGB();
                        int bottomColor = new Color(0, 0, 0, 140).getRGB();
                        RenderUtils.drawVerticalGradientRect((float) backgroundLeft, (float) backgroundTop, (float) backgroundRight, (float) backgroundBottom, topColor, bottomColor);
                    } else {
                        RenderUtils.drawRect(backgroundLeft, backgroundTop, backgroundRight, backgroundBottom, BACKGROUND_COLOR);
                    }
                }

                if (outline.getInput() == 1 && firstVisibleRow) {
                    RenderUtils.drawRect(outlineLeft, outlineTop, outlineRight, backgroundTop, color);
                }

                if (hudWaveIsVertical()) {
                    verticalWaveAccum += getVerticalWaveStep();
                }
                firstVisibleRow = false;

                if (outline.getInput() == 1 && !previousModule.isEmpty()) {
                    double difference = hudFont.getStringWidth(previousModule) - moduleWidth;
                    if (alphabeticalSort.isToggled() && difference < 0) {
                        RenderUtils.drawRect(outlineLeft, outlineTop, xPos - difference + horizontalTextPadding + outlineThickness, backgroundTop, color);
                    }
                    else if (alignRight.isToggled()) {
                        RenderUtils.drawRect(xPos - difference - horizontalTextPadding - outlineThickness, outlineTop, backgroundLeft, backgroundTop, color);
                    }
                    else {
                        RenderUtils.drawRect(backgroundRight, outlineTop, xPos + difference + moduleWidth + horizontalTextPadding + outlineThickness, backgroundTop, color);
                    }
                }

                if (outline.getInput() > 0) {
                    if (alignRight.isToggled()) {
                        RenderUtils.drawRect(backgroundRight, backgroundTop, outlineRight, backgroundBottom, color);
                    }
                    else {
                        RenderUtils.drawRect(outlineLeft, backgroundTop, backgroundLeft, backgroundBottom, color);
                    }
                }

                if (outline.getInput() == 1) {
                    if (alignRight.isToggled()) {
                        RenderUtils.drawRect(outlineLeft, backgroundTop, backgroundLeft, backgroundBottom, color);
                    }
                    else {
                        RenderUtils.drawRect(backgroundRight, backgroundTop, outlineRight, backgroundBottom, color);
                    }
                }

                drawHudText(hudFont, moduleName, xPos, textY, color);
                previousModule = moduleName;
                lastOutlineLeft = outlineLeft;
                lastOutlineRight = outlineRight;
                lastBackgroundBottom = backgroundBottom;
                yPos += rowHeight;

                // Track bounds for background image
                int rowWidth = (int) (backgroundRight - backgroundLeft);
                if (rowWidth > totalListWidth) totalListWidth = rowWidth;
                renderedRows++;
            }
        }
        catch (Exception exception) {
            Utils.sendMessage("&cAn error occurred rendering HUD. check your logs");
            exception.printStackTrace();
        }

        if (outline.getInput() == 1 && !previousModule.isEmpty()) {
            double bottomCenterX = (lastOutlineLeft + lastOutlineRight) * 0.5;
            double bottomPhase = hudWavePhase(verticalWaveAccum, bottomCenterX);
            RenderUtils.drawRect(lastOutlineLeft, lastBackgroundBottom, lastOutlineRight, lastBackgroundBottom + outlineThickness, getHudColor(bottomPhase));
        }

        // Render background image behind the entire arraylist
        if (showBgImage != null && showBgImage.isToggled()
                && backgroundImageLoaded && backgroundImageTexture != null
                && renderedRows > 0) {
            float padding = 4.0f;
            float imgX = alignRight.isToggled() ? (posX - totalListWidth - padding) : (posX - padding);
            float imgY = listStartY - padding;
            int imgRenderW = totalListWidth + Math.round(padding * 2);
            int imgRenderH = (int) ((float) (yPos - listStartY) + padding * 2);
            int opacity = (int) bgImageOpacity.getInput();
            int color = (opacity << 24) | 0x00FFFFFF;
            float cornerRadius = (float) bgImageCornerRadius.getInput();

            // Draw the image as the background using drawIcon-style rendering with custom size
            mc.getTextureManager().bindTexture(backgroundImageTexture);
            float a = ((color >>> 24) & 0xFF) / 255f;
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;
            net.minecraft.client.renderer.GlStateManager.color(r, g, b, a);
            net.minecraft.client.renderer.GlStateManager.enableBlend();
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            net.minecraft.client.renderer.GlStateManager.disableDepth();
            GL11.glPushMatrix();
            GL11.glTranslatef(imgX, imgY, 0f);
            // Maintain aspect ratio of the image
            float aspectRatio = (float) backgroundImageWidth / (float) backgroundImageHeight;
            int drawW = imgRenderW;
            int drawH = imgRenderH;
            if ((float) imgRenderW / (float) imgRenderH > aspectRatio) {
                drawW = Math.round(imgRenderH * aspectRatio);
            } else {
                drawH = Math.round(imgRenderW / aspectRatio);
            }
            float drawX = (imgRenderW - drawW) / 2.0f;
            float drawY = (imgRenderH - drawH) / 2.0f;
            net.minecraft.client.gui.Gui.drawModalRectWithCustomSizedTexture(
                    (int) drawX, (int) drawY, 0, 0, drawW, drawH, backgroundImageWidth, backgroundImageHeight);
            GL11.glPopMatrix();
            net.minecraft.client.renderer.GlStateManager.color(1f, 1f, 1f, 1f);
            net.minecraft.client.renderer.GlStateManager.enableDepth();
        }

        NotificationManager.render();
    }

    public static int getLongestModule() {
        RavenFontRenderer hudFont = getHudFontRenderer();
        int length = 0;

        for (Module module : ModuleManager.organizedModules) {
            if (module.isEnabled()) {
                length = Math.max(length, hudFont.getStringWidth(getHudRenderText(module)));
            }
        }

        return length;
    }

    private static boolean shouldSkipModule(Module module, boolean removeVelocity) {
        if (module.isHidden()) {
            return true;
        }
        if (module == ModuleManager.commandLine) {
            return true;
        }
        return module instanceof Velocity && removeVelocity;
    }

    private static boolean isLastVisibleModule(Module currentModule, boolean removeVelocity) {
        boolean foundCurrent = false;

        for (Module module : ModuleManager.organizedModules) {
            if (!foundCurrent) {
                if (module == currentModule) {
                    foundCurrent = true;
                }
                continue;
            }

            if (module.isEnabled() && !(module instanceof HUD) && !shouldSkipModule(module, removeVelocity)) {
                return false;
            }
        }

        return true;
    }

    static class EditScreen extends GuiScreen {
        private static final String EXAMPLE = "This is an-Example-HUD";

        private GuiButtonExt resetPosition;
        private boolean dragging = false;
        private float minX = 0.0f;
        private float minY = 0.0f;
        private float maxX = 0.0f;
        private float maxY = 0.0f;
        private float actualX = 5.0f;
        private float actualY = 70.0f;
        private float lastActualX = 0.0f;
        private float lastActualY = 0.0f;
        private int lastMouseX = 0;
        private int lastMouseY = 0;
        private float clickMinX = 0.0f;

        @Override
        public void initGui() {
            super.initGui();
            this.buttonList.add(this.resetPosition = new GuiButtonExt(1, this.width - 90, this.height - 25, 85, 20, "Reset position"));
            HUD.syncPositionToResolution(new ScaledResolution(this.mc));
            this.actualX = HUD.posX;
            this.actualY = HUD.posY;
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            ScaledResolution resolution = new ScaledResolution(this.mc);
            if (!this.dragging) {
                HUD.syncPositionToResolution(resolution);
                this.actualX = HUD.posX;
                this.actualY = HUD.posY;
            }
            drawRect(0, 0, this.width, this.height, -1308622848);
            float previewX = this.actualX;
            float previewY = this.actualY;
            float previewMaxX = previewX + 50.0f;
            float previewMaxY = previewY + 32.0f;
            float[] clickPos = this.getPreviewBounds(EXAMPLE);

            this.minX = previewX;
            this.minY = previewY;

            if (clickPos == null) {
                this.maxX = previewMaxX;
                this.maxY = previewMaxY;
                this.clickMinX = previewX;
            }
            else {
                this.maxX = clickPos[0];
                this.maxY = clickPos[1];
                this.clickMinX = clickPos[2];
            }

            HUD.setAbsolutePosition(previewX, previewY, resolution);

            int textX = resolution.getScaledWidth() / 2 - 84;
            int textY = resolution.getScaledHeight() / 2 - 20;
            RenderUtils.drawColoredString("Edit the HUD position by dragging.", '-', textX, textY, 2L, 0L, true, this.mc.fontRendererObj);

            try {
                this.handleInput();
            }
            catch (IOException ignored) {
            }

            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        private float[] getPreviewBounds(String text) {
            RavenFontRenderer hudFont = HUD.getHudFontRenderer();

            if (empty()) {
                float x = this.minX;
                float y = this.minY;
                String[] lines = text.split("-");
                int localTextTopPadding = getHudTextTopPadding();
                int localTextBottomPadding = getHudTextBottomPadding();
                int localRowHeight = getHudRowHeight(hudFont.getTextTopOffset(), hudFont.getTextBottomOffset(), localTextTopPadding, localTextBottomPadding);

                for (String line : lines) {
                    if (HUD.alignRight.isToggled()) {
                        x += hudFont.getStringWidth(lines[0]) - hudFont.getStringWidth(line);
                    }
                    float textY = getHudTextY(y, hudFont.getTextTopOffset(), localTextTopPadding);
                    drawHudText(hudFont, line, x, textY, Color.white.getRGB());
                    y += localRowHeight;
                }
                return null;
            }

            int longestModule = getLongestModule();
            float y = this.minY;
            double verticalWaveAccum = 0.0;
            boolean firstVisibleRow = true;
            String previousModule = "";
            double lastOutlineLeft = 0.0;
            double lastOutlineRight = 0.0;
            double lastBackgroundBottom = 0.0;
            boolean removeVelocity = ModuleManager.antiKnockback.isEnabled();
            int textTopOffset = hudFont.getTextTopOffset();
            int textBottomOffset = hudFont.getTextBottomOffset();
            int horizontalTextPadding = getHudHorizontalTextPadding();
            int textTopPadding = getHudTextTopPadding();
            int textBottomPadding = getHudTextBottomPadding();
            int outlineThickness = getHudOutlineThickness();
            int rowHeight = getHudRowHeight(textTopOffset, textBottomOffset, textTopPadding, textBottomPadding);

            try {
                for (Module module : ModuleManager.organizedModules) {
                    if (!module.isEnabled() || module instanceof HUD || shouldSkipModule(module, removeVelocity)) {
                        continue;
                    }

                    String moduleName = getHudRenderText(module);
                    int moduleWidth = hudFont.getStringWidth(moduleName);
                    float xPos = posX;
                    float textY = getHudTextY(y, textTopOffset, textTopPadding);
                    double backgroundLeft = xPos - horizontalTextPadding;
                    double backgroundRight = xPos + moduleWidth + horizontalTextPadding;
                    double backgroundTop = y;
                    double backgroundBottom = y + rowHeight;
                    double outlineLeft = backgroundLeft - outlineThickness;
                    double outlineRight = backgroundRight + outlineThickness;
                    double outlineTop = backgroundTop - outlineThickness;

                    if (alignRight.isToggled()) {
                        xPos -= moduleWidth;
                        backgroundLeft = xPos - horizontalTextPadding;
                        backgroundRight = xPos + moduleWidth + horizontalTextPadding;
                        outlineLeft = backgroundLeft - outlineThickness;
                        outlineRight = backgroundRight + outlineThickness;
                    }

                    double rowCenterX = (backgroundLeft + backgroundRight) * 0.5;
                    double wavePhase = hudWavePhase(verticalWaveAccum, rowCenterX);
                    int color = getHudColor(wavePhase);

                    if (outline.getInput() == 1 && firstVisibleRow) {
                        RenderUtils.drawRect(outlineLeft, outlineTop, outlineRight, backgroundTop, color);
                    }

                    if (hudWaveIsVertical()) {
                        verticalWaveAccum += getVerticalWaveStep();
                    }
                    firstVisibleRow = false;

                    if (drawBackground.isToggled()) {
                        RenderUtils.drawRect(backgroundLeft, backgroundTop, backgroundRight, backgroundBottom, BACKGROUND_COLOR);
                    }

                    if (outline.getInput() == 1 && !previousModule.isEmpty()) {
                        double difference = hudFont.getStringWidth(previousModule) - moduleWidth;
                        if (alphabeticalSort.isToggled() && difference < 0) {
                            RenderUtils.drawRect(outlineLeft, outlineTop, xPos - difference + horizontalTextPadding + outlineThickness, backgroundTop, color);
                        }
                        else if (alignRight.isToggled()) {
                            RenderUtils.drawRect(xPos - difference - horizontalTextPadding - outlineThickness, outlineTop, backgroundLeft, backgroundTop, color);
                        }
                        else {
                            RenderUtils.drawRect(backgroundRight, outlineTop, xPos + difference + moduleWidth + horizontalTextPadding + outlineThickness, backgroundTop, color);
                        }
                    }

                    if (outline.getInput() > 0) {
                        if (alignRight.isToggled()) {
                            RenderUtils.drawRect(backgroundRight, backgroundTop, outlineRight, backgroundBottom, color);
                        }
                        else {
                            RenderUtils.drawRect(outlineLeft, backgroundTop, backgroundLeft, backgroundBottom, color);
                        }
                    }

                    if (outline.getInput() == 1) {
                        if (alignRight.isToggled()) {
                            RenderUtils.drawRect(outlineLeft, backgroundTop, backgroundLeft, backgroundBottom, color);
                        }
                        else {
                            RenderUtils.drawRect(backgroundRight, backgroundTop, outlineRight, backgroundBottom, color);
                        }
                    }

                    drawHudText(hudFont, moduleName, xPos, textY, color);
                    previousModule = moduleName;
                    lastOutlineLeft = outlineLeft;
                    lastOutlineRight = outlineRight;
                    lastBackgroundBottom = backgroundBottom;
                    y += rowHeight;
                }
            }
            catch (Exception exception) {
                Utils.sendMessage("&cAn error occurred rendering HUD. check your logs");
                exception.printStackTrace();
            }

            if (outline.getInput() == 1 && !previousModule.isEmpty()) {
                double bottomCenterX = (lastOutlineLeft + lastOutlineRight) * 0.5;
                double bottomPhase = hudWavePhase(verticalWaveAccum, bottomCenterX);
                RenderUtils.drawRect(lastOutlineLeft, lastBackgroundBottom, lastOutlineRight, lastBackgroundBottom + outlineThickness, getHudColor(bottomPhase));
            }

            return new float[]{this.minX + longestModule, (float) Math.ceil(Math.max(y, lastBackgroundBottom)), this.minX - longestModule};
        }

        @Override
        protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceLastClick) {
            super.mouseClickMove(mouseX, mouseY, button, timeSinceLastClick);

            if (button != 0) {
                return;
            }

            if (this.dragging) {
                this.actualX = this.lastActualX + (mouseX - this.lastMouseX);
                this.actualY = this.lastActualY + (mouseY - this.lastMouseY);
            }
            else if (mouseX > this.clickMinX && mouseX < this.maxX && mouseY > this.minY && mouseY < this.maxY) {
                this.dragging = true;
                this.lastMouseX = mouseX;
                this.lastMouseY = mouseY;
                this.lastActualX = this.actualX;
                this.lastActualY = this.actualY;
            }
        }

        @Override
        protected void mouseReleased(int mouseX, int mouseY, int state) {
            super.mouseReleased(mouseX, mouseY, state);
            if (state == 0) {
                this.dragging = false;
            }
        }

        @Override
        public void actionPerformed(GuiButton button) {
            if (button == this.resetPosition) {
                HUD.resetPosition(new ScaledResolution(this.mc));
                this.actualX = HUD.posX;
                this.actualY = HUD.posY;
            }
        }

        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }

        private boolean empty() {
            for (Module module : ModuleManager.organizedModules) {
                if (module.isEnabled() && !module.getName().equals("HUD")) {
                    if (module.isHidden()) {
                        continue;
                    }
                    if (module == ModuleManager.commandLine) {
                        continue;
                    }
                    return false;
                }
            }
            return true;
        }
    }

    public static RavenFontRenderer getHudFontRenderer() {
        return FontManager.getHudRenderer(getSelectedFontName(), getSelectedFontScale());
    }

    public static String getHudText(Module module) {
        String moduleName = module instanceof AntiKnockback ? "Velocity" : module.getNameInHud();
        if (lowercase != null && lowercase.isToggled()) {
            moduleName = moduleName.toLowerCase();
        }
        return moduleName;
    }

    public static String getHudRenderText(Module module) {
        String moduleName = getHudText(module);
        if (showInfo != null && showInfo.isToggled() && !module.getInfo().isEmpty()) {
            moduleName += " \u00a77" + module.getInfo();
        }
        if (lowercase != null && lowercase.isToggled()) {
            moduleName = moduleName.toLowerCase();
        }
        return moduleName;
    }

    public static String getSelectedFontName() {
        if (font == null) {
            return HUD_FONT_OPTIONS[0];
        }
        int index = (int) Math.max(0, Math.min(font.getOptions().length - 1, font.getInput()));
        return font.getOptions()[index];
    }

    public static float getSelectedFontScale() {
        if (fontSize == null) {
            return 1.0f;
        }
        return (float) fontSize.getInput();
    }

    public static float getRelativePosX() {
        syncPositionToResolution();
        return relativePosX;
    }

    public static float getRelativePosY() {
        syncPositionToResolution();
        return relativePosY;
    }

    public static void setRelativePosition(float normalizedX, float normalizedY) {
        relativePosX = normalizedX;
        relativePosY = normalizedY;
        syncPositionToResolution();
    }

    public static void setAbsolutePosition(float absoluteX, float absoluteY) {
        setAbsolutePosition(absoluteX, absoluteY, new ScaledResolution(mc));
    }

    public static void resetPosition() {
        resetPosition(new ScaledResolution(mc));
    }

    private static void syncPositionToResolution() {
        syncPositionToResolution(new ScaledResolution(mc));
    }

    private static void syncPositionToResolution(ScaledResolution resolution) {
        int scaledWidth = Math.max(1, resolution.getScaledWidth());
        int scaledHeight = Math.max(1, resolution.getScaledHeight());

        if (Float.isNaN(relativePosX) || Float.isNaN(relativePosY)) {
            relativePosX = posX / scaledWidth;
            relativePosY = posY / scaledHeight;
        }

        posX = relativePosX * scaledWidth;
        posY = relativePosY * scaledHeight;
    }

    private static void setAbsolutePosition(float absoluteX, float absoluteY, ScaledResolution resolution) {
        posX = absoluteX;
        posY = absoluteY;

        int scaledWidth = Math.max(1, resolution.getScaledWidth());
        int scaledHeight = Math.max(1, resolution.getScaledHeight());
        relativePosX = absoluteX / scaledWidth;
        relativePosY = absoluteY / scaledHeight;
    }

    private static void resetPosition(ScaledResolution resolution) {
        setAbsolutePosition(DEFAULT_POS_X, DEFAULT_POS_Y, resolution);
    }

    private static int getHudHorizontalTextPadding() {
        return getScaledHudPixels(2.0f);
    }

    private static int getHudTextTopPadding() {
        return getScaledHudPixels(2.0f);
    }

    private static int getHudTextBottomPadding() {
        return 0;
    }

    private static int getHudOutlineThickness() {
        return getScaledHudPixels(1.0f);
    }

    private static int getHudRowHeight(int textTopOffset, int textBottomOffset, int textTopPadding, int textBottomPadding) {
        int textBoxHeight = Math.max(1, textBottomOffset - textTopOffset);
        return Math.max(1, textBoxHeight + textTopPadding + textBottomPadding);
    }

    private static float getHudTextY(float rowTop, int textTopOffset, int textTopPadding) {
        return rowTop + textTopPadding - textTopOffset;
    }

    private static int getScaledHudPixels(float basePixels) {
        return Math.max(1, Math.round(basePixels * getSelectedFontScale()));
    }

    private static boolean shouldDrawTextShadow() {
        return textShadow == null || textShadow.isToggled();
    }

    private static boolean hudWaveIsVertical() {
        return waveAxis == null || (int) waveAxis.getInput() == 0;
    }

    private static double hudWavePhase(double verticalAccum, double rowCenterX) {
        if (hudWaveIsVertical()) {
            return verticalAccum;
        }
        return rowCenterX * (HUD_WAVE_HORIZONTAL_X_SCALE / getWaveLengthMultiplier()) * getHorizontalWaveDirectionSign();
    }

    private static void drawHudText(RavenFontRenderer hudFont, String moduleName, float xPos, float textY, int fallbackColor) {
        if (!shouldUseHorizontalWaveText()) {
            hudFont.drawString(moduleName, xPos, textY, fallbackColor, shouldDrawTextShadow());
            return;
        }

        hudFont.drawGlyphString(moduleName, xPos, textY, (character, xOffset, width, formattingColor) -> {
            if (formattingColor != null) {
                return formattingColor;
            }
            return getHudColor(hudWavePhase(0.0, xPos + xOffset + width * 0.5f));
        }, shouldDrawTextShadow());
    }

    private static boolean shouldUseHorizontalWaveText() {
        return colorMode != null && (int) colorMode.getInput() != 0 && !hudWaveIsVertical();
    }

    private static double getVerticalWaveStep() {
        return (12.0 / getWaveLengthMultiplier()) * getVerticalWaveDirectionSign();
    }

    private static int getVerticalWaveDirectionSign() {
        return verticalWaveDirection == null || (int) verticalWaveDirection.getInput() == 0 ? -1 : 1;
    }

    private static int getHorizontalWaveDirectionSign() {
        return horizontalWaveDirection == null || (int) horizontalWaveDirection.getInput() == 0 ? -1 : 1;
    }

    /**
     * Accent color for HUD rows/outlines. Other modules can match HUD when enabled.
     */
    public static int getHudColor(double gradientOffset) {
        if (colorMode == null || hudColor == null) {
            return 0xFFFFFF;
        }
        int mode = (int) colorMode.getInput();
        if (mode == 2) {
            return getRainbowWaveColor(gradientOffset);
        }
        if (mode == 1 && hudColor2 != null) {
            java.awt.Color c1 = new java.awt.Color(hudColor.getRed(), hudColor.getGreen(), hudColor.getBlue());
            java.awt.Color c2 = new java.awt.Color(hudColor2.getRed(), hudColor2.getGreen(), hudColor2.getBlue());
            return getGradientWaveColor(c1, c2, gradientOffset);
        }
        return hudColor.getRGB();
    }

    private static int getGradientWaveColor(java.awt.Color c1, java.awt.Color c2, double gradientOffset) {
        double animationProgress = (Math.sin(getAnimatedWaveAngle(gradientOffset)) + 1.0) * 0.5;
        return Theme.convert(c1, c2, animationProgress).getRGB();
    }

    private static int getRainbowWaveColor(double gradientOffset) {
        double hue = getAnimatedWaveAngle(gradientOffset) / (Math.PI * 2.0);
        hue -= Math.floor(hue);
        return Color.getHSBColor((float) hue, 1.0F, 1.0F).getRGB();
    }

    private static double getAnimatedWaveAngle(double gradientOffset) {
        return System.currentTimeMillis() / (double) HUD_RAINBOW_PERIOD_MS * (Math.PI * 2.0) * getWaveSpeedMultiplier()
                + gradientOffset * HUD_WAVE_ANGLE_SCALE;
    }

    private static double getWaveSpeedMultiplier() {
        return waveSpeed == null ? 1.0 : Math.max(0.1, waveSpeed.getInput());
    }

    private static double getWaveLengthMultiplier() {
        return waveLength == null ? 1.0 : Math.max(0.5, waveLength.getInput());
    }

    private void renderWatermark() {
        ScaledResolution sr = new ScaledResolution(mc);
        String displayName = watermarkName;
        if (displayName == null || displayName.isEmpty()) {
            displayName = "Raven bS+";
        }
        RavenFontRenderer hudFont = getHudFontRenderer();
        int nameWidth = hudFont.getStringWidth(displayName);

        float wmX = 4.0f;
        float wmY = 4.0f;

        double wavePhase = hudWavePhase(0.0, wmX + nameWidth * 0.5);
        int color = getHudColor(wavePhase);

        // Pulse animation effect
        long currentTime = System.currentTimeMillis();
        double pulsePhase = (currentTime % 2000) / 2000.0 * Math.PI * 2;
        float pulseAlpha = (float) ((Math.sin(pulsePhase) + 1.0) * 0.15);

        // Background box with improved visual
        int baseBgColor = new Color(0, 0, 0, 140).getRGB();
        int bgColor = new Color(0, 0, 0, 140 + (int)(115 * pulseAlpha)).getRGB();
        int borderColor = color;
        
        double bgLeft = wmX - 4;
        double bgTop = wmY - 3;
        double bgRight = wmX + nameWidth + 4;
        double bgBottom = wmY + hudFont.getTextBottomOffset() + 4;

        // Draw outline (1px border)
        int outlineColor = new Color(color >> 16 & 255, color >> 8 & 255, color & 255, 200).getRGB();
        RenderUtils.drawRect(bgLeft - 1, bgTop - 3, bgRight + 1, bgBottom + 1, outlineColor);
        
        // Main background box
        RenderUtils.drawRect(bgLeft, bgTop, bgRight, bgBottom, bgColor);
        
        // Top accent line with glow
        RenderUtils.drawRect(bgLeft - 1, bgTop - 3, bgRight + 1, bgTop - 2, borderColor);
        RenderUtils.drawRect(bgLeft, bgTop - 2, bgRight, bgTop, borderColor);

        // Text with shadow (offset shadow)
        if (shouldUseHorizontalWaveText()) {
            // Draw shadow
            hudFont.drawGlyphString(displayName, wmX + 1, wmY + 1, (character, xOffset, width, formattingColor) -> {
                return new Color(0, 0, 0, 100).getRGB();
            }, false);
            // Draw text with wave
            hudFont.drawGlyphString(displayName, wmX, wmY, (character, xOffset, width, formattingColor) -> {
                if (formattingColor != null) {
                    return formattingColor;
                }
                return getHudColor(hudWavePhase(0.0, wmX + xOffset + width * 0.5f));
            }, shouldDrawTextShadow());
        } else {
            // Draw shadow
            hudFont.drawString(displayName, wmX + 1, wmY + 1, new Color(0, 0, 0, 100).getRGB(), false);
            // Draw text
            hudFont.drawString(displayName, wmX, wmY, color, shouldDrawTextShadow());
         }
     }
 
 }
