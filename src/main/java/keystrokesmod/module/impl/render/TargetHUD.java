package keystrokesmod.module.impl.render;

import keystrokesmod.module.Module;
import keystrokesmod.module.impl.combat.KillAura;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Theme;
import keystrokesmod.utility.Timer;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.shader.BlurUtils;
import keystrokesmod.utility.shader.RoundedUtils;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.client.config.GuiButtonExt;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class TargetHUD extends Module {
    private SliderSetting mode;
    private SliderSetting theme;
    private ButtonSetting renderEsp;
    private ButtonSetting showStatus;
    private ButtonSetting healthColor;
    private ButtonSetting hitParticles;
    private SliderSetting particleCount;
    private SliderSetting particleLifetime;
    private SliderSetting particleSpeed;
    private Timer fadeTimer;
    private Timer healthBarTimer = null;
    private EntityLivingBase target;
    private long lastAliveMS;
    private double lastHealth;
    private double prevHealthForParticles;
    private float lastHealthBar;
    public EntityLivingBase renderEntity;
    public int posX = 70;
    public int posY = 30;
    private String[] modes = new String[]{ "Modern", "Modern 2", "Legacy" };
    private final List<HitParticle> particles = new ArrayList<>();
    private final Random particleRandom = new Random();

    public TargetHUD() {
        super("TargetHUD", category.render);
        this.registerSetting(new DescriptionSetting("Only works with KillAura."));
        this.registerSetting(mode = new SliderSetting("Mode", 0, modes));
        this.registerSetting(theme = new SliderSetting("Theme", 0, Theme.themes));
        this.registerSetting(new ButtonSetting("Edit position", () -> {
            mc.displayGuiScreen(new EditScreen());
        }));
        this.registerSetting(renderEsp = new ButtonSetting("Render ESP", true));
        this.registerSetting(showStatus = new ButtonSetting("Show win or loss", true));
        this.registerSetting(healthColor = new ButtonSetting("Traditional health color", false));
        this.registerSetting(hitParticles = new ButtonSetting("Hit particles", true));
        this.registerSetting(particleCount = new SliderSetting("Particle count", 12, 4, 30, 1));
        this.registerSetting(particleLifetime = new SliderSetting("Particle lifetime", 800, 200, 2000, 50));
        this.registerSetting(particleSpeed = new SliderSetting("Particle speed", 1.5, 0.5, 4.0, 0.1));
    }

    public void onDisable() {
        reset();
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent ev) {
        if (!Utils.nullCheck()) {
            reset();
            return;
        }
        if (ev.phase == TickEvent.Phase.END) {
            if (mc.currentScreen != null) {
                reset();
                return;
            }
            if (KillAura.attackingEntity != null) {
                target = KillAura.attackingEntity;
                lastAliveMS = System.currentTimeMillis();
                fadeTimer = null;
            } else if (target != null) {
                if (System.currentTimeMillis() - lastAliveMS >= 400 && fadeTimer == null) {
                    (fadeTimer = new Timer(400)).start();
                }
            }
            else {
                return;
            }
            String playerInfo = target.getDisplayName().getFormattedText();
            double health = target.getHealth() / target.getMaxHealth();
            if (target.isDead) {
                health = 0;
            }

            // Detect hit (health decreased) -> spawn particles
            if (hitParticles != null && hitParticles.isToggled()
                    && health < prevHealthForParticles && prevHealthForParticles > 0) {
                spawnHitParticles();
            }
            prevHealthForParticles = health;

            if (health != lastHealth) {
                (healthBarTimer = new Timer(mode.getInput() == 0 ? 500 : (mode.getInput() == 1 ? 600 : 350))).start();
            }
            lastHealth = health;
            playerInfo += " " + Utils.getHealthStr(target, true);
            drawTargetHUD(fadeTimer, playerInfo, health);
            updateAndRenderParticles();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRenderWorld(RenderWorldLastEvent renderWorldLastEvent) {
        if (!renderEsp.isToggled() || !Utils.nullCheck()) {
            return;
        }
        if (KillAura.target != null) {
            RenderUtils.renderEntity(KillAura.target, 2, 0.0, 0.0, Theme.getGradient((int) theme.getInput(), 0), false);
        }
        else if (renderEntity != null) {
            RenderUtils.renderEntity(renderEntity, 2, 0.0, 0.0, Theme.getGradient((int) theme.getInput(), 0), false);
        }
    }

    private void spawnHitParticles() {
        ScaledResolution sr = new ScaledResolution(mc);
        int targetStrWithPadding = mc.fontRendererObj.getStringWidth("") + 8;
        int xCenter = (sr.getScaledWidth() / 2 - targetStrWithPadding / 2) + posX + (targetStrWithPadding / 2);
        int yBase = (sr.getScaledHeight() / 2 + 15) + posY;
        int count = (int) particleCount.getInput();
        for (int i = 0; i < count; i++) {
            double angle = particleRandom.nextDouble() * Math.PI * 2.0;
            double speed = (particleRandom.nextDouble() * 0.5 + 0.5) * particleSpeed.getInput();
            double vx = Math.cos(angle) * speed;
            double vy = Math.sin(angle) * speed - 0.3;
            particles.add(new HitParticle(
                    xCenter + (particleRandom.nextDouble() - 0.5) * 20,
                    yBase + (particleRandom.nextDouble() - 0.5) * 10,
                    vx, vy, System.currentTimeMillis(), (long) particleLifetime.getInput()));
        }
    }

    private void updateAndRenderParticles() {
        if (particles.isEmpty()) return;
        long now = System.currentTimeMillis();
        Iterator<HitParticle> it = particles.iterator();
        ScaledResolution sr = new ScaledResolution(mc);
        int width = sr.getScaledWidth();
        int height = sr.getScaledHeight();
        while (it.hasNext()) {
            HitParticle p = it.next();
            p.x += p.vx;
            p.y += p.vy;
            p.vy += 0.05; // gravity
            p.vx *= 0.98;
            p.vy *= 0.98;
            long elapsed = now - p.startTime;
            if (elapsed >= p.lifetime) {
                it.remove();
                continue;
            }
            float lifeProgress = (float) elapsed / (float) p.lifetime;
            float alpha = 1.0f - lifeProgress;
            int size = Math.max(1, (int) (4 * (1.0f - lifeProgress * 0.5f)));
            int[] grad = Theme.getGradients((int) theme.getInput());
            int color = Utils.mergeAlpha(grad[0], (int) (alpha * 220));
            // Glow halo (larger faded square behind)
            int haloSize = size + 2;
            int haloColor = Utils.mergeAlpha(grad[1], (int) (alpha * 70));
            RenderUtils.drawRect((float) p.x - haloSize / 2.0f, (float) p.y - haloSize / 2.0f,
                    (float) p.x + haloSize / 2.0f, (float) p.y + haloSize / 2.0f, haloColor);
            // Core particle
            RenderUtils.drawRect((float) p.x - size / 2.0f, (float) p.y - size / 2.0f,
                    (float) p.x + size / 2.0f, (float) p.y + size / 2.0f, color);
        }
    }

    private void drawTargetHUD(Timer fadeTimer, String string, double health) {
        if (showStatus.isToggled()) {
            // health is target's HP ratio (0-1). If target has less HP % than us, we're winning.
            double playerHealthRatio = Utils.getTotalHealth(mc.thePlayer) / mc.thePlayer.getMaxHealth();
            boolean winning = health <= playerHealthRatio;
            string = string + " " + (winning ? "§a[W]" : "§c[L]");
        }
        final ScaledResolution scaledResolution = new ScaledResolution(mc);
        final int padding = 8;
        final int targetStrWithPadding = mc.fontRendererObj.getStringWidth(string) + padding;
        final int x = (scaledResolution.getScaledWidth() / 2 - targetStrWithPadding / 2) + posX;
        final int y = (scaledResolution.getScaledHeight() / 2 + 15) + posY;
        final int n6 = x - padding;
        final int n7 = y - padding;
        final int n8 = x + targetStrWithPadding;
        final int n9 = y + (mc.fontRendererObj.FONT_HEIGHT + 5) - 6 + padding;
        final int alpha = (fadeTimer == null) ? 255 : (255 - fadeTimer.getValueInt(0, 255, 1));
        if (alpha > 0) {
            final int maxAlphaOutline = (alpha > 110) ? 110 : alpha;
            final int maxAlphaBackground = (alpha > 210) ? 210 : alpha;
            final int[] gradientColors = Theme.getGradients((int) theme.getInput());
            int modeIdx = (int) mode.getInput();
            switch (modeIdx) {
                case 0:
                    float bloomRadius = (fadeTimer == null) ? 2f : (2f * alpha / 255f);
                    float blurRadius = (fadeTimer == null) ? 3 : (3f * alpha / 255f);
                    BlurUtils.prepareBloom();
                    RoundedUtils.drawRound((float) n6, (float) n7, Math.abs((float) n6 - n8), Math.abs((float) n7 - (n9 + 13)), 8.0f, true, new Color(0, 0, 0, maxAlphaBackground));
                    BlurUtils.bloomEnd(3, bloomRadius);
                    BlurUtils.prepareBlur();
                    RoundedUtils.drawRound((float) n6, (float) n7, Math.abs((float) n6 - n8), Math.abs((float) n7 - (n9 + 13)), 8.0f, true, new Color(Utils.mergeAlpha(Color.black.getRGB(), maxAlphaOutline)));
                    BlurUtils.blurEnd(2, blurRadius);
                    break;
                case 1:
                    // Modern 2: Minimalist horizontal bar with gradient accent
                    // No blur, no bloom - just clean flat panels with gradient bottom accent
                    int m2W = Math.abs(n8 - n6);
                    int m2H = 36; // bigger height for vertical layout
                    int m2Y = n7;
                    int m2X = n6;
                    int[] m2Grad = Theme.getGradients((int) theme.getInput());
                    int m2Bg = Utils.mergeAlpha(new Color(18, 18, 22).getRGB(), maxAlphaBackground);
                    int m2AccentAlpha = (int) (alpha * 0.9);

                    // Main background panel (slightly taller for vertical layout)
                    RenderUtils.drawRect((float) m2X, (float) m2Y, (float) (m2X + m2W), (float) (m2Y + m2H), m2Bg);

                    // Left side: vertical gradient accent bar (thin, 3px)
                    RenderUtils.drawVerticalGradientRect(
                            (float) m2X, (float) m2Y, (float) (m2X + 3), (float) (m2Y + m2H),
                            Utils.mergeAlpha(m2Grad[0], m2AccentAlpha),
                            Utils.mergeAlpha(m2Grad[1], m2AccentAlpha));

                    // Top thin line (separator between header and content)
                    int sepY = m2Y + 14;
                    RenderUtils.drawRect((float) (m2X + 8), (float) sepY, (float) (m2X + m2W - 8), (float) (sepY + 1),
                            Utils.mergeAlpha(new Color(80, 80, 90).getRGB(), (int) (alpha * 0.5)));

                    // Bottom gradient accent line (3px tall)
                    int accentLineH = 3;
                    int accentLineY = m2Y + m2H - accentLineH;
                    RenderUtils.drawHorizontalGradientRect(
                            (float) m2X, (float) accentLineY, (float) (m2X + m2W), (float) (m2Y + m2H),
                            Utils.mergeAlpha(m2Grad[0], m2AccentAlpha),
                            Utils.mergeAlpha(m2Grad[1], m2AccentAlpha));
                    break;
                case 2:
                    RenderUtils.drawRoundedGradientOutlinedRectangle((float) n6, (float) n7, (float) n8, (float) (n9 + 13), 10.0f, Utils.mergeAlpha(Color.black.getRGB(), maxAlphaOutline), Utils.mergeAlpha(gradientColors[0], alpha), Utils.mergeAlpha(gradientColors[1], alpha));
                    break;
            }
            int n13 = n6 + 6;
            int n14 = n8 - 6;
            int n15 = n9;
            int healthBarH = 5;

            // For Modern 2: position health bar lower in the box
            if (modeIdx == 1) {
                n15 = n7 + 24; // 24px from top of box
                healthBarH = 4;
            }

            // Bar background
            RenderUtils.drawRoundedRectangle((float) n13, (float) n15, (float) n14, (float) (n15 + healthBarH), 4.0f, Utils.mergeAlpha(Color.black.getRGB(), maxAlphaOutline));
            int mergedGradientLeft = Utils.mergeAlpha(gradientColors[0], maxAlphaBackground);
            int mergedGradientRight = Utils.mergeAlpha(gradientColors[1], maxAlphaBackground);
            float healthBar = (float) (int) (n14 + (n13 - n14) * (1 - health));
            boolean smoothBack = false;
            if (healthBar != lastHealthBar && lastHealthBar - n13 >= 3 && healthBarTimer != null ) {
                int type;
                if (modeIdx == 0) type = 4;
                else if (modeIdx == 1) type = 5;
                else type = 1;
                float diff = lastHealthBar - healthBar;
                if (diff > 0) {
                    lastHealthBar = lastHealthBar - healthBarTimer.getValueFloat(0, diff, type);
                }
                else {
                    smoothBack = true;
                    lastHealthBar = healthBarTimer.getValueFloat(lastHealthBar, healthBar, type);
                }
            }
            else {
                lastHealthBar = healthBar;
            }
            if (healthColor.isToggled()) {
                mergedGradientLeft = mergedGradientRight = Utils.mergeAlpha(Utils.getColorForHealth(health), maxAlphaBackground);
            }
            if (lastHealthBar > n14) {
                lastHealthBar = n14;
            }

            switch (modeIdx) {
                case 0:
                    RenderUtils.drawRoundedRectangle((float) n13, (float) n15, lastHealthBar, (float) (n15 + healthBarH), 4.0f, Utils.darkenColor(mergedGradientRight, 25));
                    RenderUtils.drawRoundedGradientRect((float) n13, (float) n15, smoothBack ? lastHealthBar : healthBar, (float) (n15 + healthBarH), 4.0f, mergedGradientLeft, mergedGradientLeft, mergedGradientRight, mergedGradientRight);
                    break;
                case 1:
                    // Modern 2: thin flat health bar at bottom
                    RenderUtils.drawRect((float) n13, (float) n15, lastHealthBar, (float) (n15 + healthBarH), Utils.darkenColor(mergedGradientRight, 20));
                    RenderUtils.drawHorizontalGradientRect((float) n13, (float) n15, smoothBack ? lastHealthBar : healthBar, (float) (n15 + healthBarH), mergedGradientLeft, mergedGradientRight);
                    break;
                case 2:
                    RenderUtils.drawRoundedGradientRect((float) n13, (float) n15, lastHealthBar, (float) (n15 + 5), 4.0f, mergedGradientLeft, mergedGradientLeft, mergedGradientRight, mergedGradientRight);
                    break;
            }
            GL11.glPushMatrix();
            GL11.glEnable(GL11.GL_BLEND);
            int textColor = (new Color(220, 220, 220, 255).getRGB() & 0xFFFFFF) | (Utils.clamp(alpha + 15) << 24);
            if (modeIdx == 1) {
                // Slightly brighter text for Modern 2
                textColor = (new Color(255, 255, 255, 255).getRGB() & 0xFFFFFF) | (Utils.clamp(alpha + 25) << 24);
            }
            mc.fontRendererObj.drawString(string, (float) x, (float) y, textColor, true);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glPopMatrix();
        }
        else {
            target = null;
            healthBarTimer = null;
        }
    }

    private void drawModern2Border(float x1, float y1, float x2, float y2, float radius, float thickness, int color1, int color2, int alpha) {
        // Kept for compatibility - no longer used in Modern 2 mode
    }

    private void reset() {
        fadeTimer = null;
        target = null;
        healthBarTimer = null;
        renderEntity = null;
        particles.clear();
    }

    class EditScreen extends GuiScreen {
        GuiButtonExt resetPosition;
        boolean d = false;
        int miX = 0;
        int miY = 0;
        int maX = 0;
        int maY = 0;
        int aX = 70;
        int aY = 30;
        int laX = 0;
        int laY = 0;
        int lmX = 0;
        int lmY = 0;
        int clickMinX = 0;

        public void initGui() {
            super.initGui();
            this.buttonList.add(this.resetPosition = new GuiButtonExt(1, this.width - 90, this.height - 25, 85, 20, "Reset position"));
            this.aX = posX;
            this.aY = posY;
        }

        public void drawScreen(int mX, int mY, float pt) {
            ScaledResolution res = new ScaledResolution(this.mc);
            drawRect(0, 0, this.width, this.height, -1308622848);
            int miX = this.aX;
            int miY = this.aY;
            String playerInfo = mc.thePlayer.getDisplayName().getFormattedText();
            double health = mc.thePlayer.getHealth() / mc.thePlayer.getMaxHealth();
            if (mc.thePlayer.isDead) {
                health = 0;
            }
            lastHealth = health;
            playerInfo += " " + Utils.getHealthStr(mc.thePlayer, true);
            drawTargetHUD(null, playerInfo, health);
            if (showStatus.isToggled()) {
                double playerHealthRatio = Utils.getTotalHealth(mc.thePlayer) / mc.thePlayer.getMaxHealth();
                playerInfo = playerInfo + " " + ((health <= playerHealthRatio) ? "§a[W]" : "§c[L]");
            }
            int stringWidth = mc.fontRendererObj.getStringWidth(playerInfo) + 8;
            int maX = (res.getScaledWidth() / 2 - stringWidth / 2) + miX + mc.fontRendererObj.getStringWidth(playerInfo) + 8;
            int maY = (res.getScaledHeight() / 2 + 15) +  miY + (mc.fontRendererObj.FONT_HEIGHT + 5) - 6 + 8;
            this.miX = miX;
            this.miY = miY;
            this.maX = maX;
            this.maY = maY;
            this.clickMinX = miX;
            posX = miX;
            posY = miY;
            String edit = "Edit the HUD position by dragging.";
            int x = res.getScaledWidth() / 2 - fontRendererObj.getStringWidth(edit) / 2;
            int y = res.getScaledHeight() / 2 - 20;
            RenderUtils.drawColoredString(edit, '-', x, y, 2L, 0L, true, this.mc.fontRendererObj);

            try {
                this.handleInput();
            }
            catch (IOException var12) {
            }

            super.drawScreen(mX, mY, pt);
        }

        protected void mouseClickMove(int mX, int mY, int b, long t) {
            super.mouseClickMove(mX, mY, b, t);
            if (b == 0) {
                if (this.d) {
                    this.aX = this.laX + (mX - this.lmX);
                    this.aY = this.laY + (mY - this.lmY);
                }
                else if (mX > this.clickMinX && mX < this.maX && mY > this.miY && mY < this.maY) {
                    this.d = true;
                    this.lmX = mX;
                    this.lmY = mY;
                    this.laX = this.aX;
                    this.laY = this.aY;
                }

            }
        }

        protected void mouseReleased(int mX, int mY, int s) {
            super.mouseReleased(mX, mY, s);
            if (s == 0) {
                this.d = false;
            }

        }

        public void actionPerformed(GuiButton b) {
            if (b == this.resetPosition) {
                this.aX = posX = 70;
                this.aY = posY = 30;
            }

        }

        public boolean doesGuiPauseGame() {
            return false;
        }
    }

    private static class HitParticle {
        double x;
        double y;
        double vx;
        double vy;
        long startTime;
        long lifetime;

        HitParticle(double x, double y, double vx, double vy, long startTime, long lifetime) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.startTime = startTime;
            this.lifetime = lifetime;
        }
    }
}
