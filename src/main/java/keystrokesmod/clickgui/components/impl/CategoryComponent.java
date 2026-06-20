package keystrokesmod.clickgui.components.impl;

import keystrokesmod.Raven;
import keystrokesmod.clickgui.animation.ScrollOffsetAnimation;
import keystrokesmod.clickgui.components.Component;
import keystrokesmod.clickgui.ClickGui;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.client.Gui;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.Timer;
import keystrokesmod.utility.font.RavenFontRenderer;
import keystrokesmod.utility.profile.Manager;
import keystrokesmod.utility.profile.Profile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class CategoryComponent {
    private static long interactionSequence;
    private static final Map<Module.category, CategoryIconStacks> CATEGORY_ICON_STACKS = buildCategoryIconStacks();

    public List<ModuleComponent> modules = new CopyOnWriteArrayList<>();
    public Module.category category;
    public boolean opened;
    public float width;
    public float y;
    public float x;
    public float titleHeight;
    public boolean dragging;
    public float xx;
    public float yy;
    public boolean hovering = false;
    public boolean hoveringOverCategory = false;
    public Timer smoothTimer;
    private Timer textTimer;
    public float big;

    private static final Color BG_COLOR = new Color(12, 12, 28, 180);
    private static final Color HEADER_GRADIENT_1 = new Color(30, 30, 60, 200);
    private static final Color HEADER_GRADIENT_2 = new Color(20, 20, 45, 200);
    private static final Color OUTLINE_1 = new Color(70, 70, 140, 120);
    private static final Color OUTLINE_2 = new Color(50, 50, 100, 80);
    private static final Color CATEGORY_NAME_COLOR = new Color(220, 220, 240);

    private float lastHeight;
    private float lastNamePos;
    private float animationStartNamePos;
    public float moduleY;
    private float screenHeight;
    private float screenWidth;
    private float animationStartHeight;

    private final ScrollOffsetAnimation scrollAnim = new ScrollOffsetAnimation(250);

    public long lastInteractedTime = 0L;

    private static final class CategoryLayoutMetrics {
        private final float visibleHeight;
        private final float minScrollY;
        private final float contentBottom;

        private CategoryLayoutMetrics(float visibleHeight, float minScrollY, float contentBottom) {
            this.visibleHeight = visibleHeight;
            this.minScrollY = minScrollY;
            this.contentBottom = contentBottom;
        }
    }

    private static final class CategoryIconStacks {
        private final ItemStack normalStack;
        private final ItemStack activeStack;

        private CategoryIconStacks(ItemStack normalStack, ItemStack activeStack) {
            this.normalStack = normalStack;
            this.activeStack = activeStack;
        }
    }

    public CategoryComponent(Module.category category) {
        this.category = category;
        this.width = 100;
        this.x = 5;
        this.moduleY = this.y = 5;
        this.titleHeight = 15;
        float moduleRenderY = this.titleHeight + 4;
        scrollAnim.reset(this.moduleY);

        this.lastHeight = this.y + this.titleHeight + 5;
        this.animationStartHeight = this.lastHeight;

        for (Module mod : Raven.getModuleManager().inCategory(this.category)) {
            ModuleComponent b = new ModuleComponent(mod, this, moduleRenderY);
            this.modules.add(b);
            moduleRenderY += 18;
        }
    }

    public List<ModuleComponent> getModules() {
        return this.modules;
    }

    public void reloadModules() {
        Map<String, Boolean> openStates = captureModuleOpenStates();
        this.modules.clear();
        this.titleHeight = 15;
        float moduleRenderY = this.titleHeight + 4;

        for (Module mod : Raven.getModuleManager().inCategory(this.category)) {
            ModuleComponent component = new ModuleComponent(mod, this, moduleRenderY);
            component.restoreOpenState(Boolean.TRUE.equals(openStates.get(mod.getName())));
            this.modules.add(component);
            moduleRenderY += 18;
        }

        syncAfterModuleReload();
    }

    public void reloadModules(boolean isProfile) {
        Map<String, Boolean> openStates = captureModuleOpenStates();
        this.modules.clear();
        this.titleHeight = 15;
        float moduleRenderY = this.titleHeight + 4;

        if ((this.category == Module.category.profiles && isProfile) || (this.category == Module.category.scripts && !isProfile)) {
            ModuleComponent manager = new ModuleComponent(isProfile ? new Manager() : new keystrokesmod.script.Manager(), this, moduleRenderY);
            manager.restoreOpenState(Boolean.TRUE.equals(openStates.get(manager.mod.getName())));
            this.modules.add(manager);

            if ((Raven.profileManager == null && isProfile) || (Raven.scriptManager == null && !isProfile)) {
                return;
            }

            if (isProfile) {
                for (Profile profile : Raven.profileManager.profiles) {
                    moduleRenderY += 18;
                    ModuleComponent b = new ModuleComponent(profile.getModule(), this, moduleRenderY);
                    b.restoreOpenState(Boolean.TRUE.equals(openStates.get(profile.getModule().getName())));
                    this.modules.add(b);
                }
            } else {
                Collection<Module> modulesCollection = Raven.scriptManager.scripts.values();
                List<Module> sortedModules = modulesCollection.stream().sorted(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());
                for (Module module : sortedModules) {
                    moduleRenderY += 18;
                    ModuleComponent b = new ModuleComponent(module, this, moduleRenderY);
                    b.restoreOpenState(Boolean.TRUE.equals(openStates.get(module.getName())));
                    this.modules.add(b);
                }
            }
        }

        syncAfterModuleReload();
    }

    private Map<String, Boolean> captureModuleOpenStates() {
        Map<String, Boolean> openStates = new HashMap<String, Boolean>();
        for (ModuleComponent mc : this.modules) {
            if (mc.mod != null) {
                openStates.put(mc.mod.getName(), mc.isOpened);
            }
        }
        return openStates;
    }

    private void syncAfterModuleReload() {
        CategoryLayoutMetrics lm = computeLayoutMetrics(this.opened || this.smoothTimer != null);
        float minScrollY = lm.minScrollY;
        float maxScrollY = this.y;
        float clampedScroll = Math.max(minScrollY, Math.min(maxScrollY, scrollAnim.getTarget()));
        this.moduleY = clampedScroll;
        scrollAnim.reset(clampedScroll);

        if (this.opened && !this.modules.isEmpty()) {
            this.big = lm.visibleHeight;
            this.lastHeight = lm.contentBottom;
            return;
        }

        if (!this.opened && this.smoothTimer == null) {
            this.big = 0f;
        }
        this.lastHeight = this.y + this.titleHeight + 5;
    }

    public void setX(float newX, boolean limit) {
        if (limit) {
            newX = Math.max(newX, 2);
            newX = Math.min(newX, screenWidth - this.width - 4);
        }
        this.x = newX;
    }

    public void setY(float y, boolean limit) {
        if (limit) {
            y = Math.max(y, 1);
            float maxY = screenHeight - this.titleHeight - 5;
            y = Math.min(y, maxY);
        }
        float scrollOffset = scrollAnim.getTarget() - this.y;
        this.y = y;
        float newTarget = y + scrollOffset;
        this.moduleY = newTarget;
        scrollAnim.reset(newTarget);
    }

    public void overTitle(boolean d) {
        this.dragging = d;
    }

    public boolean isOpened() {
        return this.opened;
    }

    public void markInteracted() {
        this.lastInteractedTime = ++interactionSequence;
    }

    public void mouseClicked(boolean on) {
        this.animationStartHeight = getCurrentAnimatedCategoryHeight();
        this.animationStartNamePos = getCurrentAnimatedNamePos();
        this.opened = on;
        (this.smoothTimer = new Timer(300.0f)).start();
        (this.textTimer = new Timer(300.0f)).start();
    }

    public void onScroll(int mouseScrollInput) {
        onScroll(mouseScrollInput, Float.NaN, Float.NaN);
    }

    public void onScroll(int mouseScrollInput, float mouseX, float mouseY) {
        for (ModuleComponent mod : this.modules) {
            mod.onScroll(mouseScrollInput);
        }
        if (!hoveringOverCategory || !this.opened) {
            return;
        }
        if (!Float.isNaN(mouseX) && !Float.isNaN(mouseY)) {
            ClickGui clickGui = Minecraft.getMinecraft().currentScreen instanceof ClickGui ? (ClickGui) Minecraft.getMinecraft().currentScreen : null;
            for (ModuleComponent mod : this.modules) {
                if (clickGui != null && !clickGui.matchesSearch(mod)) continue;
                for (Component comp : mod.settings) {
                    if (!mod.isOpened || !mod.isVisible(comp)) continue;
                    if (comp instanceof AbstractSearchListComponent) {
                        if (((AbstractSearchListComponent) comp).capturesCategoryScroll(mouseX, mouseY)) return;
                    } else if (comp instanceof PlayerListComponent) {
                        if (((PlayerListComponent) comp).capturesCategoryScroll(mouseX, mouseY)) return;
                    } else if (comp instanceof StringListComponent) {
                        if (((StringListComponent) comp).capturesCategoryScroll(mouseX, mouseY)) return;
                    }
                }
            }
        }
        this.markInteracted();
        float scrollSpeed = (float) Gui.scrollSpeed.getInput();
        float minScrollY = computeMinScrollY();
        float maxScrollY = this.y;
        float delta = scrollSpeed * (mouseScrollInput / 120f);
        if (delta != 0f) {
            scrollAnim.extend(delta);
        }
        scrollAnim.clampTarget(minScrollY, maxScrollY);
    }

    private float computeMinScrollY() {
        return computeLayoutMetrics(false).minScrollY;
    }

    public void renderWithSearch(FontRenderer renderer, boolean searchActive) {
        if (searchActive && !this.opened) {
            render(renderer);
            ClickGui clickGui = Minecraft.getMinecraft().currentScreen instanceof ClickGui ? (ClickGui) Minecraft.getMinecraft().currentScreen : null;
            if (clickGui == null) return;
            boolean hasMatch = this.modules.stream().anyMatch(m -> clickGui.matchesSearch(m));
            if (!hasMatch) return;

            float totalH = 0f;
            for (ModuleComponent c : this.modules) {
                if (clickGui.matchesSearch(c)) {
                    totalH += c.getHeight();
                }
            }

            float panelX1 = this.x - 2;
            float panelY1 = this.y + this.titleHeight + 2;
            float panelX2 = this.x + this.width + 2;
            float panelY2 = this.y + this.titleHeight + 4 + totalH + 2;

            RenderUtils.drawRoundedRectangle(panelX1, panelY1, panelX2, panelY2, 8, BG_COLOR.getRGB());
            RenderUtils.drawRoundedGradientOutline(panelX1, panelY1, panelX2, panelY2, 8, 1f, OUTLINE_1.getRGB(), OUTLINE_2.getRGB());

            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            RenderUtils.scissor(0, this.y + this.titleHeight + 2, this.x + this.width + 4, totalH + 4);

            float relY = this.titleHeight + 4;
            for (ModuleComponent c : this.modules) {
                if (!clickGui.matchesSearch(c)) continue;
                c.updateHeight(relY);
                c.render();
                relY += c.getHeight();
            }

            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            return;
        }
        render(renderer);
    }

    public void render(FontRenderer renderer) {
        this.width = 100;
        RavenFontRenderer titleRenderer = Gui.getClickGuiHeaderFontRenderer();

        if (smoothTimer != null && System.currentTimeMillis() - smoothTimer.last >= 330) {
            smoothTimer = null;
        }
        if (textTimer != null && System.currentTimeMillis() - textTimer.last >= 330) {
            textTimer = null;
        }

        for (ModuleComponent c : this.modules) {
            c.updateAnimationState();
        }

        CategoryLayoutMetrics lm = computeLayoutMetrics(this.opened || smoothTimer != null);
        big = (!this.opened && smoothTimer == null) ? 0f : lm.visibleHeight;
        float maxScrollY = this.y;
        float minScrollY = lm.minScrollY;

        scrollAnim.clampTarget(minScrollY, maxScrollY);

        moduleY = scrollAnim.getValue();
        moduleY = Math.max(minScrollY, Math.min(maxScrollY, moduleY));

        String catName = this.category.name();
        int catNameWidth = titleRenderer.getStringWidth(catName);
        float middlePos = this.x + this.width / 2 - catNameWidth / 2.0f;

        float contentBottom = lm.contentBottom;

        float extra;
        if (smoothTimer != null) {
            float targetHeight = this.opened ? contentBottom : (this.y + this.titleHeight + 5);
            extra = smoothTimer.getValueFloat(animationStartHeight, targetHeight, 1);
            if ((this.opened && extra > targetHeight) || (!this.opened && extra < targetHeight)) {
                extra = targetHeight;
            }
        } else {
            extra = contentBottom;
        }

        float targetNamePos = this.opened ? middlePos : (this.x + 14);
        float namePos;
        if (textTimer == null) {
            namePos = targetNamePos;
        } else {
            namePos = textTimer.getValueFloat(animationStartNamePos, targetNamePos, 1);
        }
        this.lastNamePos = namePos;
        this.lastHeight = extra;

        GL11.glPushMatrix();

        float panelX1 = this.x - 2;
        float panelY1 = this.y;
        float panelX2 = this.x + this.width + 2;
        float panelY2 = extra;

        RenderUtils.drawRoundedRectangle(panelX1, panelY1, panelX2, panelY2, 10, BG_COLOR.getRGB());

        RenderUtils.drawRoundedGradientRectangle(panelX1, panelY1, panelX2, panelY1 + this.titleHeight + 2, 10,
                HEADER_GRADIENT_1.getRGB(), HEADER_GRADIENT_2.getRGB());

        int outline1 = ((opened || hovering) && Gui.rainBowOutlines.isToggled())
                ? RenderUtils.setAlpha(Utils.getChroma(2, 0), 0.5f)
                : OUTLINE_1.getRGB();
        int outline2 = ((opened || hovering) && Gui.rainBowOutlines.isToggled())
                ? RenderUtils.setAlpha(Utils.getChroma(2, 700), 0.5f)
                : OUTLINE_2.getRGB();
        RenderUtils.drawRoundedGradientOutline(panelX1, panelY1, panelX2, panelY2, 10, 1f, outline1, outline2);

        renderItemForCategory(this.category, (int) (this.x + 2), (int) (this.y + 5), opened || hovering);
        titleRenderer.drawString(catName, namePos, this.y + 5, CATEGORY_NAME_COLOR.getRGB(), true);

        float moduleAreaTop = this.y + this.titleHeight + 4;
        float scissorBottom = extra - 2f;
        float moduleAreaHeight = Math.max(0f, scissorBottom - moduleAreaTop);

        if (this.opened || smoothTimer != null) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            RenderUtils.scissor(0, moduleAreaTop, this.x + this.width + 4, moduleAreaHeight);

            float scrollOffset = moduleY - this.y;
            GL11.glPushMatrix();
            GL11.glTranslatef(0f, scrollOffset, 0f);
            ClickGui clickGui = Minecraft.getMinecraft().currentScreen instanceof ClickGui ? (ClickGui) Minecraft.getMinecraft().currentScreen : null;
            for (ModuleComponent c2 : this.modules) {
                if (clickGui != null && !clickGui.matchesSearch(c2)) continue;
                c2.render();
            }
            GL11.glPopMatrix();

            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }

        GL11.glPopMatrix();
    }

    public void updateHeight() {
        float y = this.titleHeight + 4;
        ClickGui clickGui = Minecraft.getMinecraft().currentScreen instanceof ClickGui ? (ClickGui) Minecraft.getMinecraft().currentScreen : null;
        for (ModuleComponent component : this.modules) {
            if (clickGui != null && !clickGui.matchesSearch(component)) continue;
            component.updateHeight(y);
            y += component.getHeightF();
        }
    }

    public float getX() { return this.x; }
    public float getY() { return this.y; }
    public float getModuleY() { return this.moduleY; }
    public float getWidth() { return this.width; }

    public void mousePosition(int mouseX, int mouseY, boolean isTopmostUnderCursor) {
        if (this.dragging) {
            float newX = mouseX - this.xx;
            float newY = mouseY - this.yy;
            newX = Math.max(newX, 2);
            newX = Math.min(newX, screenWidth - this.width - 4);
            newY = Math.max(newY, 1);
            int maxY = (int) (screenHeight - this.titleHeight - 5);
            newY = Math.min(newY, maxY);
            this.setX(newX, false);
            this.setY(newY, false);
        }

        hoveringOverCategory = isTopmostUnderCursor && overCategory(mouseX, mouseY);
        hovering = isTopmostUnderCursor && overTitle(mouseX, mouseY);
    }

    public boolean overTitle(int x, int y) {
        return x >= this.x && x <= this.x + this.width && (float) y >= (float) this.y + 2.0F && y <= this.y + this.titleHeight + 1;
    }

    public boolean overCategory(int x, int y) {
        return x >= this.x - 2 && x <= this.x + this.width + 2 && (float) y >= (float) this.y + 2.0F && y <= this.y + this.titleHeight + big + 1;
    }

    public boolean draggable(int x, int y) {
        return x >= this.x && x <= this.x + this.width && y >= this.y && y <= this.y + this.titleHeight;
    }

    public boolean overRect(int x, int y) {
        return x >= this.x - 2 && x <= this.x + this.width + 2 && y >= this.y && y <= lastHeight;
    }

    private void renderItemForCategory(Module.category category, int x, int y, boolean enchant) {
        RenderItem renderItem = Minecraft.getMinecraft().getRenderItem();
        double scale = 0.6;
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, scale);
        CategoryIconStacks iconStacks = CATEGORY_ICON_STACKS.get(category);
        ItemStack itemStack = iconStacks == null ? null : (enchant ? iconStacks.activeStack : iconStacks.normalStack);
        if (itemStack != null) {
            RenderHelper.enableGUIStandardItemLighting();
            GlStateManager.disableBlend();
            GlStateManager.translate((float) (x / scale), (float) (y / scale), 0);
            renderItem.renderItemAndEffectIntoGUI(itemStack, 0, 0);
            GlStateManager.enableBlend();
            RenderHelper.disableStandardItemLighting();
        }
        GlStateManager.scale(1, 1, 1);
        GlStateManager.popMatrix();
    }

    private float getCurrentAnimatedNamePos() {
        if (textTimer != null) return lastNamePos;
        float middlePos = this.x + this.width / 2 - Gui.getClickGuiHeaderFontRenderer().getStringWidth(this.category.name()) / 2.0f;
        return this.opened ? middlePos : (this.x + 14);
    }

    private float getCurrentAnimatedCategoryHeight() {
        if (this.lastHeight > 0) return this.lastHeight;
        if (!this.modules.isEmpty() && (this.opened || this.smoothTimer != null)) {
            float modulesHeight = 0f;
            for (ModuleComponent c : this.modules) {
                modulesHeight += c.getHeightF();
            }
            return this.y + this.titleHeight + modulesHeight + 4;
        }
        return this.y + this.titleHeight + 5;
    }

    public void setScreenSize(float screenWidth, float screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    public void limitPositions() {
        setX(this.x, true);
        setY(this.y, true);
    }

    public void applySavedState(float x, float y, boolean opened, boolean clampToScreen) {
        if (clampToScreen) {
            setX(x, true);
            setY(y, true);
        } else {
            float scrollOffset = scrollAnim.getTarget() - this.y;
            this.x = x;
            this.y = y;
            float newTarget = y + scrollOffset;
            this.moduleY = newTarget;
            scrollAnim.reset(newTarget);
        }
        this.opened = opened;
        smoothTimer = null;
        textTimer = null;
        if (opened && !this.modules.isEmpty()) {
            CategoryLayoutMetrics lm = computeLayoutMetrics(true);
            this.big = lm.visibleHeight;
            this.lastHeight = lm.contentBottom;
        } else {
            this.big = 0f;
            this.lastHeight = this.y + this.titleHeight + 5;
        }
        this.moduleY = this.y;
        scrollAnim.reset(this.y);
    }

    public void onGuiClosed() {
        if (smoothTimer != null || textTimer != null) {
            float finalHeight = this.y + this.titleHeight;
            if (this.opened && !this.modules.isEmpty()) {
                for (ModuleComponent c : this.modules) {
                    finalHeight += c.getHeightF();
                }
                finalHeight += 4;
            } else {
                finalHeight += 5;
            }
            this.lastHeight = finalHeight;
        }
        smoothTimer = null;
        textTimer = null;
        moduleY = scrollAnim.getTarget();
        scrollAnim.reset(moduleY);
    }

    private CategoryLayoutMetrics computeLayoutMetrics(boolean updateModuleOffsets) {
        ClickGui clickGui = Minecraft.getMinecraft().currentScreen instanceof ClickGui ? (ClickGui) Minecraft.getMinecraft().currentScreen : null;
        if (this.modules.isEmpty() || (!this.opened && this.smoothTimer == null)) {
            return new CategoryLayoutMetrics(0f, this.y, this.y + this.titleHeight + 5);
        }

        float maxModulesHeight = (this.screenHeight * 0.85f) - this.titleHeight - 4;
        float visibleHeight = 0f;
        float totalScrollExtent = 0f;
        float moduleOffset = this.titleHeight + 4;

        for (ModuleComponent component : this.modules) {
            if (clickGui != null && !clickGui.matchesSearch(component)) continue;

            if (updateModuleOffsets) {
                component.updateHeight(moduleOffset);
            }

            float componentHeight = component.getHeightF();
            moduleOffset += componentHeight;
            totalScrollExtent += component.getScrollExtentHeightF();

            if (visibleHeight < maxModulesHeight) {
                visibleHeight += Math.min(componentHeight, maxModulesHeight - visibleHeight);
            }
        }

        float viewport = Math.min(maxModulesHeight, totalScrollExtent);
        float overflow = Math.max(0f, totalScrollExtent - viewport);
        float minScrollY = overflow > 0f ? this.y - overflow : this.y;
        float maxBottom = this.y + (this.screenHeight * 0.85f);
        float contentBottom = Math.min(this.y + this.titleHeight + 4 + visibleHeight, maxBottom);
        return new CategoryLayoutMetrics(Math.max(0f, visibleHeight), minScrollY, contentBottom);
    }

    private static Map<Module.category, CategoryIconStacks> buildCategoryIconStacks() {
        EnumMap<Module.category, CategoryIconStacks> iconStacks = new EnumMap<Module.category, CategoryIconStacks>(Module.category.class);
        for (Module.category category : Module.category.values()) {
            ItemStack normalStack = createCategoryIconStack(category, false);
            ItemStack activeStack = createCategoryIconStack(category, true);
            if (normalStack != null && activeStack != null) {
                iconStacks.put(category, new CategoryIconStacks(normalStack, activeStack));
            }
        }
        return iconStacks;
    }

    private static ItemStack createCategoryIconStack(Module.category category, boolean active) {
        ItemStack itemStack;
        switch (category) {
            case combat: itemStack = new ItemStack(Items.diamond_sword); break;
            case movement: itemStack = new ItemStack(Items.diamond_boots); break;
            case player: itemStack = new ItemStack(Items.golden_apple); break;
            case world: itemStack = new ItemStack(Items.filled_map); break;
            case render: itemStack = new ItemStack(Items.ender_eye); break;
            case minigames: itemStack = new ItemStack(Items.gold_ingot); break;
            case fun: itemStack = new ItemStack(Items.slime_ball); break;
            case other: itemStack = new ItemStack(Items.clock); break;
            case client: itemStack = new ItemStack(Items.compass); break;
            case profiles: itemStack = new ItemStack(Items.book); break;
            case scripts: itemStack = new ItemStack(Items.redstone); break;
            default: return null;
        }
        if (!active) return itemStack;
        if (category != Module.category.player) {
            itemStack.addEnchantment(Enchantment.unbreaking, 2);
        } else {
            itemStack.setItemDamage(1);
        }
        return itemStack;
    }
}
