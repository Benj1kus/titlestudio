package com.benji.titlestudio.title.studio;

import com.benji.titlestudio.title.client.TitleTextRenderer;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class TitleStudioScreen extends TitleStudioRetroScreen {

    private static final int ACCENT = TitleStudioRetroTheme.LIME;
    private static final int PREVIEW_ACCENT = TitleStudioRetroTheme.LIME;
    private static final int LABEL = TitleStudioRetroTheme.TEXT_LIGHT;
    private static final int MUTED = TitleStudioRetroTheme.TEXT_MUTED;

    private static final int ROW_STEP = 32;
    private static final int CONTROL_H = 18;
    private static final int LEFT_SCROLL_STEP = 32;
    private static final int ANIMATION_SCROLL_STEP = 21;

    private static String STATUS = "Ready";
    private static String TOAST_TEXT;
    private static long TOAST_UNTIL;

    private static final List<String> ENTER_PRESETS = List.of("none", "fade", "fade_up", "fade_down", "slide_left", "slide_right", "scale", "pop", "typewriter", "explode", "letter_wave");
    private static final List<String> EXIT_PRESETS = List.of("none", "fade", "fade_up", "fade_down", "slide_left", "slide_right", "scale_down", "scale_up", "type_out", "scatter");
    private static final List<String> EASINGS = List.of("linear", "in_cubic", "out_cubic", "in_out_cubic", "smoothstep", "in_back", "out_back", "in_quint", "out_quint");
    private static final List<String> ANCHORS = List.of("top_left", "top_center", "top_right", "center_left", "center", "center_right", "bottom_left", "bottom_center", "bottom_right");
    private static final List<String> SOUND_SOURCES = List.of("master", "music", "records", "weather", "blocks", "hostile", "neutral", "players", "ambient", "voice");

    public enum Tab {
        BASIC("Basic"), STYLE("Style"), EFFECTS("Effects"), TRIGGER("Trigger"), AUDIO("Audio"), EXPORT("Export");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private TitleStudioProject project;
    private final Tab tab;
    private final List<LabelLine> labels = new ArrayList<>();
    private final List<AbstractWidget> fixedWidgets = new ArrayList<>();
    private final List<AbstractWidget> leftWidgets = new ArrayList<>();
    private final List<AbstractWidget> animationWidgets = new ArrayList<>();

    private int leftScroll;
    private int animationScroll;
    private boolean draggingLeftScrollbar;
    private boolean draggingAnimationScrollbar;

    private int inspectorW;
    private int contentTop;

    private int previewX;
    private int previewY;
    private int previewW;
    private int previewH;

    private int animationX;
    private int animationY;
    private int animationW;
    private int animationH;

    private int canvasX;
    private int canvasY;
    private int canvasW;
    private int canvasH;

    private boolean draggingPreview;
    private float previewAge;

    public TitleStudioScreen(TitleStudioProject project, Tab tab) {
        super(Component.literal("Title Studio"));
        this.project = project != null ? project : TitleStudioProject.createDefault();
        this.tab = tab != null ? tab : Tab.BASIC;
        this.project.normalize();
        this.previewAge = 0.0F;
    }

    @Override
    protected void init() {
        project.normalize();

        inspectorW = Mth.clamp(Math.round(width * 0.34F), 220, 292);
        if (width < 520) {
            inspectorW = Mth.clamp(Math.round(width * 0.38F), 188, 224);
        }

        contentTop = 82;
        calculateRightLayout();
        rebuildStudioWidgets();
        TitleStudioFontPreviewPack.ensureLoaded(project);
    }

    private void rebuildStudioWidgets() {
        clearWidgets();
        labels.clear();
        fixedWidgets.clear();
        leftWidgets.clear();
        animationWidgets.clear();

        leftScroll = Mth.clamp(leftScroll, 0, maxLeftScroll());
        animationScroll = Mth.clamp(animationScroll, 0, maxAnimationScroll());

        buildTabs();
        buildResetAllButton();

        switch (tab) {
            case BASIC -> initBasic();
            case STYLE -> initStyle();
            case EFFECTS -> initEffects();
            case TRIGGER -> initTrigger();
            case AUDIO -> initAudio();
            case EXPORT -> initExport();
        }

        initAnimationPanel();
        updateScrollVisibility();
    }

    private void calculateRightLayout() {
        previewX = inspectorW + 7;
        previewY = 28;
        previewW = Math.max(160, width - previewX - 7);

        int gap = 7;
        int bottomMargin = 8;

        animationH = Mth.clamp(Math.round(height * 0.30F), 104, 144);

        previewH = Math.max(104, height - previewY - bottomMargin - gap - animationH);

        animationX = previewX;
        animationY = previewY + previewH + gap;
        animationW = previewW;

        if (animationY + animationH > height - bottomMargin) {
            animationH = Math.max(88, height - animationY - bottomMargin);
        }
    }

    private void buildTabs() {
        int gap = 3;
        int buttonW = Math.max(56, (inspectorW - 20 - gap * 2) / 3);

        for (int i = 0; i < Tab.values().length; i++) {
            Tab value = Tab.values()[i];
            int col = i % 3;
            int row = i / 3;
            int x = 10 + col * (buttonW + gap);
            int y = 27 + row * 20;

            addFixedWidget(new TitleStudioButton(x, y, buttonW, 17, Component.literal(value.label), button -> reopen(value), value == tab));
        }
    }

    private void buildResetAllButton() {
        int buttonW = 70;

        addFixedWidget(new TitleStudioButton(width - buttonW - 10, 5, buttonW, 17, Component.literal("Reset all"), button -> resetAllSettings()));
    }

    private void initBasic() {
        field("Project name", project.project_name, 0, value -> project.project_name = value, 96);

        field("Namespace / mod id", project.namespace, 1, value -> {
            String old = project.namespace;
            String clean = TitleStudioProject.sanitizeNamespace(value);
            project.rewriteNamespace(old, clean);
        }, 64);

        field("Title path", project.title_path, 2, value -> project.title_path = TitleStudioProject.sanitizePath(value), 128);

        field("Text", project.definition.text, 3, value -> {
            project.definition.text = value;
            replayPreview();
        }, 1024);

        assetField("Font id / imported TTF", project.definition.font, 4, ".ttf", value -> {
            project.definition.font = value == null || value.isBlank() ? "minecraft:default" : value.trim();
            replayPreview();
        }, this::importFont);

        slider(5, "Scale", 0.25D, 6.0D, project.definition.position.scale, value -> project.definition.position.scale = value.floatValue());

        slider(6, "Rotation", -45.0D, 45.0D, project.definition.position.rotation, value -> project.definition.position.rotation = value.floatValue(), value -> String.format(Locale.ROOT, "%.1f°", value));

        cycleButton("Anchor", project.definition.position.anchor, 7, ANCHORS, value -> project.definition.position.anchor = value);

        cycleButton("Line align", project.definition.position.align, 8, List.of("left", "center", "right"), value -> project.definition.position.align = value);

        smallFields("X (0..1)", String.format(Locale.ROOT, "%.3f", project.definition.position.x), value -> project.definition.position.x = clamp01(parseFloat(value, project.definition.position.x)), "Y (0..1)", String.format(Locale.ROOT, "%.3f", project.definition.position.y), value -> project.definition.position.y = clamp01(parseFloat(value, project.definition.position.y)), 9);
    }

    private void initStyle() {
        colorField("Base color", project.definition.style.color, 0, value -> project.definition.style.color = value);

        field("Gradient: #HEX,#HEX,... (blank = none)", join(project.definition.style.gradient), 1, value -> {
            if (value == null || value.isBlank()) {
                project.definition.style.gradient = new ArrayList<>();
                return;
            }

            List<String> parsed = parseGradient(value);
            if (!parsed.isEmpty() && parsed.stream().allMatch(TitleStudioScreen::isValidGradientColor)) {
                project.definition.style.gradient = parsed;
            }
        }, 256);

        toggleButton("Outline", project.definition.style.outline.enabled, 2, value -> project.definition.style.outline.enabled = value);
        colorField("Outline color", project.definition.style.outline.color, 3, value -> project.definition.style.outline.color = value);
        slider(4, "Outline width", 0.0D, 4.0D, project.definition.style.outline.width, value -> project.definition.style.outline.width = value.floatValue());

        labels.add(new LabelLine("Gradient is horizontal and interpolated per glyph.", 12, rowY(6), MUTED));
        labels.add(new LabelLine("Use 'rainbow' as Base color for animated rainbow text.", 12, rowY(7), MUTED));
    }

    private void initEffects() {
        toggleButton("Wave", project.definition.effects.has("wave"), 0, value -> project.definition.effects.set("wave", value));

        slider(1, "Wave amplitude", 0.0D, 8.0D, project.definition.effects.wave_amplitude, value -> project.definition.effects.wave_amplitude = value.floatValue());
        slider(2, "Wave speed", 0.0D, 15.0D, project.definition.effects.wave_speed, value -> project.definition.effects.wave_speed = value.floatValue());
        slider(3, "Wave frequency", 0.0D, 2.5D, project.definition.effects.wave_frequency, value -> project.definition.effects.wave_frequency = value.floatValue());
        toggleButton("Shake", project.definition.effects.has("shake"), 4, value -> project.definition.effects.set("shake", value));
        slider(5, "Shake strength", 0.0D, 4.0D, project.definition.effects.shake_strength, value -> project.definition.effects.shake_strength = value.floatValue());
        toggleButton("Pulse", project.definition.effects.has("pulse"), 6, value -> project.definition.effects.set("pulse", value));
        slider(7, "Pulse amount", 0.0D, 0.35D, project.definition.effects.pulse_amount, value -> project.definition.effects.pulse_amount = value.floatValue());
        slider(8, "Pulse speed", 0.0D, 10.0D, project.definition.effects.pulse_speed, value -> project.definition.effects.pulse_speed = value.floatValue());
    }

    private void initTrigger() {
        cycleButton("Trigger type", project.definition.trigger.type, 0, List.of("biome", "structure"), value -> {
            project.definition.trigger.type = value;
            project.definition.trigger.target = "biome".equals(value) ? "minecraft:desert" : "minecraft:desert_pyramid";
        });

        registryField("Target", project.definition.trigger.target, 1, value -> {
            project.definition.trigger.target = value.toLowerCase(Locale.ROOT).trim();
        });

        smallFields("Minimum stay (ticks)", String.valueOf(project.definition.trigger.minimum_stay_ticks), value -> project.definition.trigger.minimum_stay_ticks = Math.max(0, parseInt(value, project.definition.trigger.minimum_stay_ticks)), "Cooldown (ticks)", String.valueOf(project.definition.trigger.cooldown_ticks), value -> project.definition.trigger.cooldown_ticks = Math.max(0, parseInt(value, project.definition.trigger.cooldown_ticks)), 2);

        toggleButton("Once per visit", project.definition.trigger.once_per_visit, 3, value -> project.definition.trigger.once_per_visit = value);

        labels.add(new LabelLine("Browse reads the current world's registries, including other mods.", 12, rowY(5), MUTED));
        labels.add(new LabelLine("Runtime trigger checks are server-side, so multiplayer is consistent.", 12, rowY(6), MUTED));
        labels.add(new LabelLine("NBT templates are shown in Browse, but need a region/placement tracker trigger.", 12, rowY(7), MUTED));
    }

    private void initAudio() {
        assetField("Appearance sound", project.definition.sound.event, 0, ".ogg", value -> {
            project.definition.sound.event = value != null ? value.trim() : "";
        }, this::importSound);

        cycleButton("Sound source", project.definition.sound.source, 1, SOUND_SOURCES, value -> project.definition.sound.source = value);

        slider(2, "Volume", 0.0D, 2.0D, project.definition.sound.volume, value -> project.definition.sound.volume = value.floatValue());
        slider(3, "Pitch", 0.25D, 2.0D, project.definition.sound.pitch, value -> project.definition.sound.pitch = value.floatValue());

        fullButton("Test current sound", 4, this::testSound);
        fullButton("Clear appearance sound", 5, () -> {
            project.definition.sound.event = "";
            reopen(tab);
        });

        labels.add(new LabelLine("Drop an .ogg anywhere on this screen to import it.", 12, rowY(7), MUTED));
    }

    private void initExport() {
        fullButton("Save Studio project", 0, this::saveProject);
        fullButton("Open another project.json", 1, this::openProject);
        fullButton("Import existing title JSON", 2, this::importTitleJson);
        fullButton("Quick export packs", 3, () -> exportProject(false));
        fullButton("Export + install into current instance", 4, this::installProject);
        fullButton("Export for Mods", 5, this::exportForMods);

        fullButton("Open exports folder", 6, () -> {
            try {
                java.nio.file.Files.createDirectories(TitleStudioWorkspace.exportsRoot());
                Util.getPlatform().openFile(TitleStudioWorkspace.exportsRoot().toFile());
            } catch (Exception exception) {
                STATUS = "Open folder failed: " + exception.getMessage();
            }
        });

        labels.add(new LabelLine("Studio project: .minecraft/title_studio/projects/...", 12, rowY(8), MUTED));
        labels.add(new LabelLine("Export for Mods creates ready data/<modid>/... + assets/<modid>/...", 12, rowY(9), MUTED));
        labels.add(new LabelLine("Copy the generated folder contents directly into src/main/resources.", 12, rowY(10), 0xFFFFC27A));
    }

    private void initAnimationPanel() {
        int gap = 4;
        int innerX = animationX + 8;
        int innerW = animationW - 16;
        int half = Math.max(64, (innerW - gap) / 2);

        addAnimationWidget(new TitleStudioButton(innerX, animationRowY(0), half, CONTROL_H, Component.literal("ENTER: " + project.definition.enter.preset), button -> {
            project.definition.enter.preset = next(project.definition.enter.preset, ENTER_PRESETS);
            replayAndReopen();
        }));

        addAnimationWidget(new TitleStudioButton(innerX + half + gap, animationRowY(0), innerW - half - gap, CONTROL_H, Component.literal("EXIT: " + project.definition.exit.preset), button -> {
            project.definition.exit.preset = next(project.definition.exit.preset, EXIT_PRESETS);
            replayAndReopen();
        }));

        addAnimationWidget(new TitleStudioButton(innerX, animationRowY(1), half, CONTROL_H, Component.literal("Enter ease: " + project.definition.enter.easing), button -> {
            project.definition.enter.easing = next(project.definition.enter.easing, EASINGS);
            replayAndReopen();
        }));

        addAnimationWidget(new TitleStudioButton(innerX + half + gap, animationRowY(1), innerW - half - gap, CONTROL_H, Component.literal("Exit ease: " + project.definition.exit.easing), button -> {
            project.definition.exit.easing = next(project.definition.exit.easing, EASINGS);
            replayAndReopen();
        }));

        int thirdGap = 3;
        int third = Math.max(46, (innerW - thirdGap * 2) / 3);

        addAnimationWidget(new TitleStudioSlider(innerX, animationRowY(2), third, CONTROL_H, "Enter", 0, 80, project.definition.enter.duration, value -> {
            project.definition.enter.duration = value.intValue();
            replayPreview();
        }, value -> value.intValue() + "t"));

        addAnimationWidget(new TitleStudioSlider(innerX + third + thirdGap, animationRowY(2), third, CONTROL_H, "Hold", 0, 240, project.definition.hold_ticks, value -> {
            project.definition.hold_ticks = value.intValue();
            replayPreview();
        }, value -> value.intValue() + "t"));

        addAnimationWidget(new TitleStudioSlider(innerX + (third + thirdGap) * 2, animationRowY(2), innerW - (third + thirdGap) * 2, CONTROL_H, "Exit", 0, 80, project.definition.exit.duration, value -> {
            project.definition.exit.duration = value.intValue();
            replayPreview();
        }, value -> value.intValue() + "t"));

        addAnimationWidget(new TitleStudioSlider(innerX, animationRowY(3), half, CONTROL_H, "Enter dist", 0, 80, project.definition.enter.distance, value -> project.definition.enter.distance = value.floatValue(), value -> String.format(Locale.ROOT, "%.0f", value)));
        addAnimationWidget(new TitleStudioSlider(innerX + half + gap, animationRowY(3), innerW - half - gap, CONTROL_H, "Exit dist", 0, 80, project.definition.exit.distance, value -> project.definition.exit.distance = value.floatValue(), value -> String.format(Locale.ROOT, "%.0f", value)));
        addAnimationWidget(new TitleStudioSlider(innerX, animationRowY(4), half, CONTROL_H, "Enter scale", 0.2D, 2.0D, project.definition.enter.scale, value -> project.definition.enter.scale = value.floatValue()));
        addAnimationWidget(new TitleStudioSlider(innerX + half + gap, animationRowY(4), innerW - half - gap, CONTROL_H, "Exit scale", 0.2D, 2.0D, project.definition.exit.scale, value -> project.definition.exit.scale = value.floatValue()));
        addAnimationWidget(new TitleStudioButton(innerX, animationRowY(5), Math.min(76, innerW / 2), CONTROL_H, Component.literal("▶ Replay"), button -> replayPreview()));
        addAnimationWidget(new TitleStudioButton(innerX + Math.min(80, innerW / 2), animationRowY(5), Math.min(84, innerW - Math.min(80, innerW / 2)), CONTROL_H, Component.literal("Loop: " + (project.preview_loop ? "ON" : "OFF")), button -> {
            project.preview_loop = !project.preview_loop;
            reopen(tab);
        }));
    }

    @Override
    public void tick() {
        super.tick();
        TitleStudioHistory.watch(project);

        previewAge += 1.0F;
        int total = project.definition.totalTicks();

        if (project.preview_loop) {
            if (previewAge > total + 18) previewAge = 0.0F;
        } else if (previewAge > total) {
            previewAge = total;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        TitleStudioRetroTheme.drawPanel(graphics, 4, 24, inspectorW, height - 4);
        TitleStudioRetroTheme.drawDarkInset(graphics, 8, leftViewportTop() - 3, inspectorW - 6, leftViewportBottom() + 2);
        TitleStudioRetroTheme.drawPanel(graphics, inspectorW + 4, 24, width - 4, height - 4);

        graphics.drawString(font, "TITLE STUDIO", 12, 10, TitleStudioRetroTheme.LIME, false);

        String titleId = font.plainSubstrByWidth(project.titleId(), Math.max(40, inspectorW - 96));

        graphics.drawString(font, titleId, 92, 10, TitleStudioRetroTheme.TEXT_MUTED, false);

        renderPreview(graphics, mouseX, mouseY, partialTick);
        renderAnimationPanel(graphics, partialTick);

        int leftTop = leftViewportTop();
        int leftBottom = leftViewportBottom();

        graphics.enableScissor(8, leftTop, inspectorW - 6, leftBottom);

        for (LabelLine label : labels) {
            if (label.y + font.lineHeight >= leftTop && label.y <= leftBottom - 1) {

                graphics.drawString(font, label.text, label.x, label.y, label.color, false);
            }
        }

        for (AbstractWidget widget : leftWidgets) {
            if (widget.visible) {
                widget.render(graphics, mouseX, mouseY, partialTick);
            }
        }

        graphics.disableScissor();

        for (AbstractWidget widget : fixedWidgets) {
            if (widget.visible) {
                widget.render(graphics, mouseX, mouseY, partialTick);
            }
        }

        graphics.enableScissor(animationX + 6, animationContentTop(), animationX + animationW - 8, animationContentBottom());

        for (AbstractWidget widget : animationWidgets) {
            if (widget.visible) {
                widget.render(graphics, mouseX, mouseY, partialTick);
            }
        }

        graphics.disableScissor();

        renderScrollbars(graphics);

        int statusX0 = 8;
        int statusX1 = inspectorW - 6;
        int statusY0 = height - 20;
        int statusY1 = height - 7;

        graphics.fill(statusX0, statusY0, statusX1, statusY1, TitleStudioRetroTheme.BEIGE);
        graphics.fill(statusX0, statusY0, statusX1, statusY0 + 1, TitleStudioRetroTheme.CREAM_LIGHT);
        graphics.fill(statusX0, statusY1 - 1, statusX1, statusY1, TitleStudioRetroTheme.BLACK);

        String status = font.plainSubstrByWidth(STATUS, Math.max(40, inspectorW - 28));
        graphics.drawString(font, status, 12, height - 17, TitleStudioRetroTheme.TEXT_HINT, false);

        renderToast(graphics);
    }

    private void renderPreview(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        TitleStudioRetroTheme.drawPanel(graphics, previewX, previewY, previewX + previewW, previewY + previewH);

        TitleStudioRetroTheme.drawTitleBar(graphics, previewX + 3, previewY + 3, previewX + previewW - 3, 20);

        graphics.drawString(font, "LIVE TITLE PREVIEW", previewX + 8, previewY + 8, PREVIEW_ACCENT, false);

        if (previewW > 260) {
            graphics.drawString(font, "drag green anchor to position", previewX + 8, previewY + 18, MUTED, false);
        }

        int areaX = previewX + 8;
        int areaY = previewY + 29;
        int areaW = previewW - 16;
        int areaH = previewH - 37;
        int virtualW = Math.max(1, minecraft.getWindow().getGuiScaledWidth());
        int virtualH = Math.max(1, minecraft.getWindow().getGuiScaledHeight());

        float previewScale = Math.min(areaW / (float) virtualW, areaH / (float) virtualH);

        canvasW = Math.max(1, Math.round(virtualW * previewScale));
        canvasH = Math.max(1, Math.round(virtualH * previewScale));

        canvasX = areaX + (areaW - canvasW) / 2;
        canvasY = areaY + (areaH - canvasH) / 2;

        renderPreviewBackdrop(graphics, canvasX, canvasY, canvasW, canvasH);

        graphics.enableScissor(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH);

        graphics.pose().pushPose();
        graphics.pose().translate(canvasX, canvasY, 0.0F);
        graphics.pose().scale(previewScale, previewScale, 1.0F);

        TitleTextRenderer.render(project.definition, graphics, 0, 0, virtualW, virtualH, previewAge + partialTick);

        graphics.pose().popPose();
        graphics.disableScissor();

        int anchorPx = canvasX + Math.round(project.definition.position.x * canvasW);
        int anchorPy = canvasY + Math.round(project.definition.position.y * canvasH);

        boolean hovered = mouseX >= canvasX && mouseX <= canvasX + canvasW && mouseY >= canvasY && mouseY <= canvasY + canvasH;

        if (hovered || draggingPreview) {
            graphics.fill(anchorPx - 5, anchorPy - 1, anchorPx + 6, anchorPy + 2, TitleStudioRetroTheme.LIME);
            graphics.fill(anchorPx - 1, anchorPy - 5, anchorPx + 2, anchorPy + 6, TitleStudioRetroTheme.LIME);
            graphics.fill(anchorPx - 2, anchorPy - 2, anchorPx + 3, anchorPy + 3, 0x55639D52);
        }
    }

    private void renderPreviewBackdrop(GuiGraphics graphics, int x, int y, int w, int h) {
        int bands = Math.max(10, Math.min(32, h / 4));
        for (int i = 0; i < bands; i++) {
            float t = i / (float) Math.max(1, bands - 1);
            int rgb = lerpRgb(0x181C23, 0x050608, t);
            int yy1 = y + Math.round(i * h / (float) bands);
            int yy2 = y + Math.round((i + 1) * h / (float) bands);
            graphics.fill(x, yy1, x + w, yy2 + 1, 0xFF000000 | rgb);
        }

        int inset = Math.max(2, Math.min(w, h) / 45);
        graphics.hLine(x, x + w, y, 0xFF303944);
        graphics.hLine(x, x + w, y + h, 0xFF20262E);
        graphics.vLine(x, y, y + h, 0xFF303944);
        graphics.vLine(x + w, y, y + h, 0xFF20262E);
        graphics.fill(x + inset, y + inset, x + inset + 1, y + h - inset, 0x332F8290);
    }

    private void renderAnimationPanel(GuiGraphics graphics, float partialTick) {
        TitleStudioRetroTheme.drawPanel(graphics, animationX, animationY, animationX + animationW, animationY + animationH);

        TitleStudioRetroTheme.drawTitleBar(graphics, animationX + 3, animationY + 3, animationX + animationW - 3, 20);

        graphics.drawString(font, "ANIMATION  •  ENTER / HOLD / EXIT", animationX + 8, animationY + 8, PREVIEW_ACCENT, false);

        int total = Math.max(1, project.definition.totalTicks());
        int enter = project.definition.enter.duration;
        int hold = project.definition.hold_ticks;

        int lineX = animationX + 8;
        int lineY = animationY + 20;
        int lineW = Math.max(35, animationW - 22);
        graphics.fill(lineX, lineY, lineX + lineW, lineY + 2, 0xFF28333E);

        int enterX = lineX + Math.round(lineW * (enter / (float) total));
        int exitX = lineX + Math.round(lineW * ((enter + hold) / (float) total));
        graphics.fill(lineX, lineY, enterX, lineY + 2, TitleStudioRetroTheme.GREEN_MID);
        graphics.fill(enterX, lineY, exitX, lineY + 2, 0xFF8C8D90);
        graphics.fill(exitX, lineY, lineX + lineW, lineY + 2, 0xFFC98B43);

        float shownAge = Math.min(previewAge, total);
        int playX = lineX + Math.round(lineW * (shownAge / total));
        graphics.fill(playX - 1, lineY - 2, playX + 2, lineY + 5, 0xFFFFFFFF);
    }

    private void renderToast(GuiGraphics graphics) {
        if (TOAST_TEXT == null || System.currentTimeMillis() > TOAST_UNTIL) return;

        int w = Math.min(width - 30, Math.max(170, font.width(TOAST_TEXT) + 24));
        int x = width - w - 14;
        int y = 12;

        graphics.fill(x + 3, y + 3, x + w + 3, y + 31, 0x88000000);
        graphics.fill(x, y, x + w, y + 28, TitleStudioRetroTheme.CREAM);
        graphics.fill(x, y, x + w, y + 2, TitleStudioRetroTheme.LIME);
        graphics.fill(x, y + 27, x + w, y + 28, TitleStudioRetroTheme.BLACK);
        graphics.drawString(font, TOAST_TEXT, x + 11, y + 10, 0xFF151812, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hitLeftScrollbar(mouseX, mouseY)) {
            draggingLeftScrollbar = true;
            updateLeftScrollFromMouse(mouseY);
            return true;
        }

        if (button == 0 && hitAnimationScrollbar(mouseX, mouseY)) {
            draggingAnimationScrollbar = true;
            updateAnimationScrollFromMouse(mouseY);
            return true;
        }

        if (button == 0 && mouseX >= canvasX && mouseX <= canvasX + canvasW && mouseY >= canvasY && mouseY <= canvasY + canvasH) {
            draggingPreview = true;
            updatePreviewPosition(mouseX, mouseY);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingLeftScrollbar && button == 0) {
            updateLeftScrollFromMouse(mouseY);
            return true;
        }

        if (draggingAnimationScrollbar && button == 0) {
            updateAnimationScrollFromMouse(mouseY);
            return true;
        }

        if (draggingPreview && button == 0) {
            updatePreviewPosition(mouseX, mouseY);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingLeftScrollbar || draggingAnimationScrollbar) {
            draggingLeftScrollbar = false;
            draggingAnimationScrollbar = false;
            return true;
        }

        if (draggingPreview) {
            draggingPreview = false;
            TitleStudioHistory.checkpoint(project);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= 4 && mouseX <= inspectorW && mouseY >= leftViewportTop() && mouseY <= leftViewportBottom()) {
            int old = leftScroll;
            leftScroll = Mth.clamp(leftScroll - (int) Math.signum(delta) * LEFT_SCROLL_STEP, 0, maxLeftScroll());
            if (leftScroll != old) {
                rebuildStudioWidgets();
                return true;
            }
        }

        if (mouseX >= animationX && mouseX <= animationX + animationW && mouseY >= animationContentTop() && mouseY <= animationContentBottom()) {
            int old = animationScroll;
            animationScroll = Mth.clamp(animationScroll - (int) Math.signum(delta) * ANIMATION_SCROLL_STEP, 0, maxAnimationScroll());
            if (animationScroll != old) {
                rebuildStudioWidgets();
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void updatePreviewPosition(double mouseX, double mouseY) {
        if (canvasW <= 0 || canvasH <= 0) return;
        project.definition.position.x = clamp01((float) ((mouseX - canvasX) / canvasW));
        project.definition.position.y = clamp01((float) ((mouseY - canvasY) / canvasH));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_Z) {
            TitleStudioProject restored = hasShiftDown() ? TitleStudioHistory.redo(project) : TitleStudioHistory.undo(project);

            if (restored != null) {
                STATUS = hasShiftDown() ? "Redo" : "Undo";
                minecraft.setScreen(new TitleStudioScreen(restored, tab));
            }
            return true;
        }

        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_Y) {
            TitleStudioProject restored = TitleStudioHistory.redo(project);
            if (restored != null) {
                STATUS = "Redo";
                minecraft.setScreen(new TitleStudioScreen(restored, tab));
            }
            return true;
        }

        if (TitleStudioClientHooks.OPEN_STUDIO.matches(keyCode, scanCode)) {
            saveQuietly();
            minecraft.setScreen(null);
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (paths == null || paths.isEmpty()) return;

        Path path = paths.get(0);
        String file = path.getFileName().toString().toLowerCase(Locale.ROOT);

        try {
            if (file.endsWith(".ogg")) {
                String id = TitleStudioWorkspace.importSound(project, path);
                project.definition.sound.event = id;
                STATUS = "Imported sound: " + id;
                showToast("OGG imported");
            } else if (file.endsWith(".ttf")) {
                String id = TitleStudioWorkspace.importFont(project, path);
                project.definition.font = id;
                TitleStudioFontPreviewPack.ensureLoaded(project);
                STATUS = "Imported font: " + id;
                showToast("TTF imported  •  loading live preview font...");
            } else if (file.endsWith(".json")) {
                if (file.equals("project.json")) {
                    TitleStudioProject loaded = TitleStudioWorkspace.load(path);
                    STATUS = "Loaded project: " + loaded.project_name;
                    minecraft.setScreen(new TitleStudioScreen(loaded, Tab.BASIC));
                    return;
                }

                TitleStudioWorkspace.importTitleJson(project, path);
                STATUS = "Imported title JSON: " + path.getFileName();
                showToast("Title JSON imported");
            } else {
                STATUS = "Drop .ttf, .ogg or .json into Title Studio.";
                return;
            }

            TitleStudioWorkspace.save(project);
            TitleStudioHistory.checkpoint(project);
            reopen(tab);
        } catch (Exception exception) {
            STATUS = "Drop import failed: " + exception.getMessage();
        }
    }

    @Override
    public void onClose() {
        saveQuietly();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void saveProject() {
        try {
            TitleStudioWorkspace.save(project);
            STATUS = "Saved: " + TitleStudioWorkspace.projectJson(project);
            showToast("Studio project saved");
        } catch (Exception exception) {
            STATUS = "Save failed: " + exception.getMessage();
        }
    }

    private void saveQuietly() {
        try {
            TitleStudioWorkspace.save(project);
        } catch (Exception ignored) {
        }
    }

    private void openProject() {
        minecraft.setScreen(new TitleStudioFilePickerScreen(this, TitleStudioWorkspace.projectsRoot(), ".json", path -> {
            if (!path.getFileName().toString().equalsIgnoreCase("project.json")) {
                STATUS = "Choose a Title Studio project.json file.";
                return;
            }

            try {
                TitleStudioProject loaded = TitleStudioWorkspace.load(path);
                STATUS = "Loaded: " + loaded.project_name;
                minecraft.setScreen(new TitleStudioScreen(loaded, Tab.BASIC));
            } catch (Exception exception) {
                STATUS = "Load failed: " + exception.getMessage();
            }
        }));
    }

    private void importTitleJson() {
        minecraft.setScreen(new TitleStudioFilePickerScreen(this, minecraft.gameDirectory.toPath(), ".json", path -> {
            try {
                TitleStudioWorkspace.importTitleJson(project, path);
                TitleStudioWorkspace.save(project);
                STATUS = "Imported title: " + path.getFileName();
                showToast("Title JSON imported");
                minecraft.setScreen(new TitleStudioScreen(project, tab));
            } catch (Exception exception) {
                STATUS = "Import failed: " + exception.getMessage();
            }
        }));
    }

    private void exportProject(boolean openFolder) {
        try {
            TitleStudioExporter.ExportResult result = TitleStudioExporter.export(project);
            STATUS = "Exported: " + result.root();
            showToast("Export complete  •  datapack.zip + resourcepack.zip");
            if (openFolder) Util.getPlatform().openFile(result.root().toFile());
        } catch (Exception exception) {
            STATUS = "Export failed: " + exception.getMessage();
        }
    }

    private void installProject() {
        try {
            TitleStudioExporter.ExportResult result = TitleStudioExporter.installToCurrentInstance(project);
            STATUS = "Installed into current instance: " + result.root();
            showToast("Installed  •  enable resource pack + /reload");
        } catch (Exception exception) {
            STATUS = "Install failed: " + exception.getMessage();
        }
    }

    private void exportForMods() {
        minecraft.setScreen(new TitleStudioFilePickerScreen(this, minecraft.gameDirectory.toPath(), "", destination -> {
            try {
                TitleStudioExporter.ModExportResult result = TitleStudioExporter.exportForMod(project, destination);

                STATUS = "Mod template exported: " + result.root();
                showToast("Export for Mods complete  •  data + assets ready");
                Util.getPlatform().openFile(result.root().toFile());

                if (minecraft.screen instanceof TitleStudioFilePickerScreen) {
                    minecraft.setScreen(this);
                }
            } catch (Exception exception) {
                STATUS = "Mod export failed: " + exception.getMessage();
                minecraft.setScreen(this);
            }
        }, true));
    }

    private void importFont(Path path) {
        try {
            String id = TitleStudioWorkspace.importFont(project, path);
            project.definition.font = id;
            TitleStudioWorkspace.save(project);
            TitleStudioFontPreviewPack.ensureLoaded(project);
            STATUS = "Imported font: " + id;
            showToast("TTF imported  •  loading live preview font...");
            minecraft.setScreen(new TitleStudioScreen(project, tab));
        } catch (Exception exception) {
            STATUS = "Font import failed: " + exception.getMessage();
        }
    }

    private void importSound(Path path) {
        try {
            String id = TitleStudioWorkspace.importSound(project, path);
            project.definition.sound.event = id;
            TitleStudioWorkspace.save(project);
            STATUS = "Imported sound: " + id;
            showToast("OGG imported");
            minecraft.setScreen(new TitleStudioScreen(project, tab));
        } catch (Exception exception) {
            STATUS = "Sound import failed: " + exception.getMessage();
        }
    }

    private void testSound() {
        String value = project.definition.sound.event;
        if (value == null || value.isBlank()) {
            STATUS = "Appearance sound is empty.";
            return;
        }

        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            STATUS = "Invalid sound id: " + value;
            return;
        }

        SoundSource source;
        try {
            source = SoundSource.valueOf(project.definition.sound.source.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            source = SoundSource.MASTER;
        }

        minecraft.getSoundManager().play(new SimpleSoundInstance(id, source, project.definition.sound.volume, project.definition.sound.pitch, RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));

        STATUS = "Played sound request: " + id;
    }

    private void fullButton(String label, int row, Runnable action) {
        addLeftWidget(new TitleStudioButton(10, rowY(row), inspectorW - 20, CONTROL_H, Component.literal(label), button -> action.run()));
    }

    private void field(String label, String value, int row, Consumer<String> responder, int maxLength) {
        int y = rowY(row);
        labels.add(new LabelLine(label, 10, y - 10, LABEL));

        TitleStudioTextField box = new TitleStudioTextField(font, 10, y, inspectorW - 20, CONTROL_H, Component.literal(label));
        box.setMaxLength(maxLength);
        box.setValue(value != null ? value : "");
        box.setResponder(responder);
        addLeftWidget(box);
    }

    private void assetField(String label, String value, int row, String extension, Consumer<String> responder, Consumer<Path> importer) {
        int y = rowY(row);
        int browseW = 58;
        labels.add(new LabelLine(label, 10, y - 10, LABEL));

        TitleStudioTextField box = new TitleStudioTextField(font, 10, y, inspectorW - 23 - browseW, CONTROL_H, Component.literal(label));
        box.setMaxLength(512);
        box.setValue(value != null ? value : "");
        box.setResponder(responder);
        addLeftWidget(box);

        addLeftWidget(new TitleStudioButton(inspectorW - 10 - browseW, y, browseW, CONTROL_H, Component.literal("Browse"), button -> minecraft.setScreen(new TitleStudioFilePickerScreen(this, minecraft.gameDirectory.toPath(), extension, importer))));
    }

    private void registryField(String label, String value, int row, Consumer<String> responder) {
        int y = rowY(row);
        int browseW = 58;
        labels.add(new LabelLine(label, 10, y - 10, LABEL));

        TitleStudioTextField box = new TitleStudioTextField(font, 10, y, inspectorW - 23 - browseW, CONTROL_H, Component.literal(label));
        box.setMaxLength(256);
        box.setValue(value != null ? value : "");
        box.setResponder(responder);
        addLeftWidget(box);

        addLeftWidget(new TitleStudioButton(inspectorW - 10 - browseW, y, browseW, CONTROL_H, Component.literal("Browse"), button -> minecraft.setScreen(new TitleStudioRegistryBrowserScreen(this, project.definition.trigger.type, selected -> {
            project.definition.trigger.target = selected;
            TitleStudioHistory.checkpoint(project);
            minecraft.setScreen(new TitleStudioScreen(project, tab));
        }))));
    }

    private void colorField(String label, String value, int row, Consumer<String> responder) {
        int y = rowY(row);
        int buttonW = 60;
        labels.add(new LabelLine(label, 10, y - 10, LABEL));

        TitleStudioTextField box = new TitleStudioTextField(font, 10, y, inspectorW - 23 - buttonW, CONTROL_H, Component.literal(label));
        box.setMaxLength(64);
        box.setValue(value != null ? value : "");
        box.setResponder(responder);
        addLeftWidget(box);

        addLeftWidget(new TitleStudioButton(inspectorW - 10 - buttonW, y, buttonW, CONTROL_H, Component.literal("Color"), button -> minecraft.setScreen(new TitleStudioColorPickerScreen(this, box.getValue() != null && !box.getValue().isBlank() ? box.getValue() : "#FFFFFF", color -> {
            responder.accept(color);
            TitleStudioHistory.checkpoint(project);
            minecraft.setScreen(new TitleStudioScreen(project, tab));
        }))));
    }

    private void slider(int row, String label, double min, double max, double value, Consumer<Double> responder) {
        slider(row, label, min, max, value, responder, number -> String.format(Locale.ROOT, "%.2f", number));
    }

    private void slider(int row, String label, double min, double max, double value, Consumer<Double> responder, java.util.function.Function<Double, String> formatter) {
        addLeftWidget(new TitleStudioSlider(10, rowY(row), inspectorW - 20, CONTROL_H, label, min, max, value, responder, formatter));
    }

    private void cycleButton(String label, String value, int row, List<String> options, Consumer<String> responder) {
        addLeftWidget(new TitleStudioButton(10, rowY(row), inspectorW - 20, CONTROL_H, Component.literal(label + ": " + value), button -> {
            responder.accept(next(value, options));
            TitleStudioHistory.checkpoint(project);
            reopen(tab);
        }));
    }

    private void toggleButton(String label, boolean value, int row, Consumer<Boolean> responder) {
        addLeftWidget(new TitleStudioButton(10, rowY(row), inspectorW - 20, CONTROL_H, Component.literal(label + ": " + (value ? "ON" : "OFF")), button -> {
            responder.accept(!value);
            TitleStudioHistory.checkpoint(project);
            reopen(tab);
        }));
    }

    private void smallFields(String labelA, String valueA, Consumer<String> responderA, String labelB, String valueB, Consumer<String> responderB, int row) {
        int y = rowY(row);
        int gap = 4;
        int w = (inspectorW - 20 - gap) / 2;

        labels.add(new LabelLine(labelA, 10, y - 10, LABEL));
        labels.add(new LabelLine(labelB, 10 + w + gap, y - 10, LABEL));

        TitleStudioTextField a = new TitleStudioTextField(font, 10, y, w, CONTROL_H, Component.literal(labelA));
        a.setMaxLength(48);
        a.setValue(valueA != null ? valueA : "");
        a.setResponder(responderA);
        addLeftWidget(a);

        TitleStudioTextField b = new TitleStudioTextField(font, 10 + w + gap, y, w, CONTROL_H, Component.literal(labelB));
        b.setMaxLength(48);
        b.setValue(valueB != null ? valueB : "");
        b.setResponder(responderB);
        addLeftWidget(b);
    }

    private int rowY(int row) {
        return contentTop + 8 + row * ROW_STEP - leftScroll;
    }

    private <T extends AbstractWidget> T addFixedWidget(T widget) {
        addRenderableWidget(widget);
        fixedWidgets.add(widget);
        return widget;
    }

    private <T extends AbstractWidget> T addLeftWidget(T widget) {
        addRenderableWidget(widget);
        leftWidgets.add(widget);
        return widget;
    }

    private <T extends AbstractWidget> T addAnimationWidget(T widget) {
        addRenderableWidget(widget);
        animationWidgets.add(widget);
        return widget;
    }

    private int leftViewportTop() {
        return 78;
    }

    private int leftViewportBottom() {
        return Math.max(leftViewportTop() + 40, height - 19);
    }

    private int leftRowCount() {
        return switch (tab) {
            case BASIC -> 10;
            case STYLE -> 8;
            case EFFECTS -> 9;
            case TRIGGER -> 8;
            case AUDIO -> 8;
            case EXPORT -> 11;
        };
    }

    private int maxLeftScroll() {
        int contentBottom = contentTop + 8 + (leftRowCount() - 1) * ROW_STEP + CONTROL_H + 5;
        return Math.max(0, contentBottom - leftViewportBottom());
    }

    private int animationContentTop() {
        return animationY + 28;
    }

    private int animationContentBottom() {
        return Math.max(animationContentTop() + 24, animationY + animationH - 6);
    }

    private int animationRowY(int row) {
        return animationContentTop() + row * ANIMATION_SCROLL_STEP - animationScroll;
    }

    private int maxAnimationScroll() {
        int contentBottom = animationContentTop() + 5 * ANIMATION_SCROLL_STEP + CONTROL_H + 2;
        return Math.max(0, contentBottom - animationContentBottom());
    }

    private void updateScrollVisibility() {
        int leftTop = leftViewportTop();
        int leftBottom = leftViewportBottom();
        for (AbstractWidget widget : leftWidgets) {
            widget.visible = widget.getY() >= leftTop && widget.getY() + widget.getHeight() <= leftBottom;
        }

        int animationTop = animationContentTop();
        int animationBottom = animationContentBottom();
        for (AbstractWidget widget : animationWidgets) {
            widget.visible = widget.getY() >= animationTop && widget.getY() + widget.getHeight() <= animationBottom;
        }
    }

    private void renderScrollbars(GuiGraphics graphics) {
        drawScrollbar(graphics, inspectorW - 6, leftViewportTop(), leftViewportBottom(), leftScroll, maxLeftScroll());

        drawScrollbar(graphics, animationX + animationW - 6, animationContentTop(), animationContentBottom(), animationScroll, maxAnimationScroll());
    }

    private static void drawScrollbar(GuiGraphics graphics, int x, int top, int bottom, int scroll, int maxScroll) {
        if (maxScroll <= 0 || bottom <= top) {
            return;
        }

        int trackH = bottom - top;
        int thumbH = Math.max(14, Math.round(trackH * (trackH / (float) (trackH + maxScroll))));
        int travel = Math.max(1, trackH - thumbH);
        int thumbY = top + Math.round(travel * (scroll / (float) maxScroll));

        TitleStudioRetroTheme.drawRetroScrollTrack(graphics, x, top, bottom, thumbY, thumbH);
    }

    private boolean hitLeftScrollbar(double mouseX, double mouseY) {
        return maxLeftScroll() > 0 && mouseX >= inspectorW - 9 && mouseX <= inspectorW && mouseY >= leftViewportTop() && mouseY <= leftViewportBottom();
    }

    private boolean hitAnimationScrollbar(double mouseX, double mouseY) {
        return maxAnimationScroll() > 0 && mouseX >= animationX + animationW - 9 && mouseX <= animationX + animationW && mouseY >= animationContentTop() && mouseY <= animationContentBottom();
    }

    private void updateLeftScrollFromMouse(double mouseY) {
        int top = leftViewportTop();
        int bottom = leftViewportBottom();
        int max = maxLeftScroll();
        if (max <= 0 || bottom <= top) return;
        float t = Mth.clamp((float) ((mouseY - top) / (bottom - top)), 0.0F, 1.0F);
        leftScroll = Math.round(t * max);
        rebuildStudioWidgets();
    }

    private void updateAnimationScrollFromMouse(double mouseY) {
        int top = animationContentTop();
        int bottom = animationContentBottom();
        int max = maxAnimationScroll();
        if (max <= 0 || bottom <= top) return;
        float t = Mth.clamp((float) ((mouseY - top) / (bottom - top)), 0.0F, 1.0F);
        animationScroll = Math.round(t * max);
        rebuildStudioWidgets();
    }

    private void resetAllSettings() {
        String workspaceId = project.workspace_id;

        TitleStudioProject defaults = TitleStudioProject.createDefault();

        defaults.workspace_id = workspaceId;
        defaults.normalize();

        project = defaults;

        leftScroll = 0;
        animationScroll = 0;
        previewAge = 0.0F;

        TitleStudioHistory.checkpoint(project);

        STATUS = "All Title Studio settings reset to defaults.";
        showToast("Reset all  •  defaults restored");

        minecraft.setScreen(new TitleStudioScreen(project, Tab.BASIC));
    }

    private void replayPreview() {
        previewAge = 0.0F;
    }

    private void replayAndReopen() {
        replayPreview();
        TitleStudioHistory.checkpoint(project);
        reopen(tab);
    }

    private void reopen(Tab next) {
        minecraft.setScreen(new TitleStudioScreen(project, next));
    }

    private static String next(String current, List<String> options) {
        if (options == null || options.isEmpty()) return current;
        int index = options.indexOf(current);
        if (index < 0) index = 0;
        else index = (index + 1) % options.size();
        return options.get(index);
    }


    private static boolean isValidGradientColor(String value) {
        if (value == null) return false;
        String clean = value.trim().toLowerCase(Locale.ROOT);
        if (clean.matches("#[0-9a-f]{6}")) return true;
        if (clean.matches("#[0-9a-f]{3}")) return true;
        return switch (clean) {
            case "blue", "red", "gold", "golden", "green", "white", "black", "purple", "cyan", "orange", "pink" -> true;
            default -> false;
        };
    }

    private static List<String> parseGradient(String value) {
        if (value == null || value.isBlank()) return new ArrayList<>();
        return new ArrayList<>(Arrays.stream(value.split(",")).map(String::trim).filter(part -> !part.isBlank()).toList());
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) return "";
        return String.join(",", values);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static float parseFloat(String value, float fallback) {
        try {
            return Float.parseFloat(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static float clamp01(float value) {
        return Mth.clamp(value, 0.0F, 1.0F);
    }

    private static void showToast(String text) {
        TOAST_TEXT = text;
        TOAST_UNTIL = System.currentTimeMillis() + 2800L;
    }

    private static int lerpRgb(int a, int b, float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        int ar = (a >> 16) & 255;
        int ag = (a >> 8) & 255;
        int ab = a & 255;
        int br = (b >> 16) & 255;
        int bg = (b >> 8) & 255;
        int bb = b & 255;
        int r = Math.round(Mth.lerp(t, ar, br));
        int g = Math.round(Mth.lerp(t, ag, bg));
        int bl = Math.round(Mth.lerp(t, ab, bb));
        return (r << 16) | (g << 8) | bl;
    }

    private record LabelLine(String text, int x, int y, int color) {
    }
}
