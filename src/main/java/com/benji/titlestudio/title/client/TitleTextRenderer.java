package com.benji.titlestudio.title.client;

import com.benji.titlestudio.title.data.TitleDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TitleTextRenderer {

    private TitleTextRenderer() {
    }

    public static void render(TitleDefinition definition, GuiGraphics graphics, int viewportX, int viewportY, int viewportWidth, int viewportHeight, float ageTicks) {
        if (definition == null || graphics == null) return;
        definition.normalize();
        if (definition.text.isEmpty()) return;

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;

        ResourceLocation fontId = ResourceLocation.tryParse(definition.font);
        if (fontId == null) {
            fontId = ResourceLocation.fromNamespaceAndPath("minecraft", "default");
        }

        Layout layout = layout(font, definition.text, fontId);
        if (layout.glyphs.isEmpty()) return;

        Lifecycle lifecycle = lifecycle(definition, ageTicks);
        if (lifecycle.alpha <= 0.001F) return;

        float targetX = viewportX + definition.position.x * viewportWidth;
        float targetY = viewportY + definition.position.y * viewportHeight;

        float baseScale = definition.position.scale * lifecycle.scale;
        if (definition.effects.has("pulse")) {
            float pulse = Mth.sin(ageTicks * 0.05F * definition.effects.pulse_speed);
            baseScale *= 1.0F + pulse * definition.effects.pulse_amount;
        }

        float anchorX = anchorX(definition.position.anchor, layout.width);
        float anchorY = anchorY(definition.position.anchor, layout.height);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(targetX + lifecycle.offsetX, targetY + lifecycle.offsetY, 900.0F);
        pose.mulPose(Axis.ZP.rotationDegrees(definition.position.rotation + lifecycle.rotation));
        pose.scale(baseScale, baseScale, 1.0F);
        pose.translate(-anchorX, -anchorY, 0.0F);

        int visibleGlyphs = visibleGlyphs(definition, lifecycle, layout.glyphs.size());
        float time = ageTicks;

        for (int order = 0; order < layout.glyphs.size(); order++) {
            Glyph glyph = layout.glyphs.get(order);
            if (order >= visibleGlyphs) continue;
            if (Character.isWhitespace(glyph.character)) continue;

            float gx = glyph.x + alignedLineOffset(definition.position.align, layout, glyph.line);
            float gy = glyph.y;
            float glyphScale = 1.0F;
            float glyphAlpha = lifecycle.alpha;

            if (definition.effects.has("wave")) {
                gy += Mth.sin(time * 0.044F * definition.effects.wave_speed + glyph.index * definition.effects.wave_frequency) * definition.effects.wave_amplitude;
            }

            if (definition.effects.has("shake")) {
                long frame = (long) Math.floor(time * 1.8F);
                long seed = glyph.index * 734287L + frame * 912271L;
                gx += hashOffset(seed) * definition.effects.shake_strength;
                gy += hashOffset(seed + 19L) * definition.effects.shake_strength;
            }

            if ("explode".equalsIgnoreCase(definition.enter.preset) && lifecycle.phase == Phase.ENTER) {
                float local = staggerProgress(lifecycle.rawProgress, order, layout.glyphs.size(), 0.55F);
                float eased = ease(local, definition.enter.easing);
                glyphScale *= Mth.lerp(eased, Math.max(1.0F, definition.enter.scale), 1.0F);
                glyphAlpha *= eased;
            }

            if ("letter_wave".equalsIgnoreCase(definition.enter.preset) && lifecycle.phase == Phase.ENTER) {
                float local = staggerProgress(lifecycle.rawProgress, order, layout.glyphs.size(), 0.62F);
                float eased = ease(local, definition.enter.easing);
                gy += (1.0F - eased) * definition.enter.distance;
                glyphAlpha *= eased;
            }

            if ("scatter".equalsIgnoreCase(definition.exit.preset) && lifecycle.phase == Phase.EXIT) {
                float local = staggerProgress(lifecycle.rawProgress, order, layout.glyphs.size(), 0.52F);
                float eased = ease(local, definition.exit.easing);
                long seed = glyph.index * 104729L + 991L;
                gx += hashOffset(seed) * definition.exit.distance * eased;
                gy += hashOffset(seed + 71L) * definition.exit.distance * eased;
                glyphAlpha *= 1.0F - eased;
            }

            int rgb;
            if (definition.style.gradient.size() >= 2) {

                float gradientT;
                if (layout.width > 1 && glyph.width > 0) {
                    gradientT = Mth.clamp((glyph.x + glyph.width * 0.5F) / layout.width, 0.0F, 1.0F);
                } else {
                    gradientT = layout.glyphs.size() <= 1 ? 0.0F : Mth.clamp(order / (float) (layout.glyphs.size() - 1), 0.0F, 1.0F);
                }
                rgb = TitleColorUtil.gradientColor(definition.style.gradient, gradientT);
            } else if ("rainbow".equalsIgnoreCase(definition.style.color)) {
                rgb = TitleColorUtil.rainbow(glyph.index * 0.095F + time * 0.0028F);
            } else {
                rgb = TitleColorUtil.parseColor(definition.style.color);
            }

            drawGlyph(graphics, font, fontId, glyph.character, gx, gy, glyph.width, glyphScale, rgb, glyphAlpha, definition.style.outline);
        }

        pose.popPose();
    }

    private static void drawGlyph(GuiGraphics graphics, Font font, ResourceLocation fontId, char character, float x, float y, int glyphWidth, float glyphScale, int rgb, float alpha, TitleDefinition.Outline outline) {
        if (alpha <= 0.01F) return;

        Component component = glyphComponent(character, fontId);
        PoseStack pose = graphics.pose();

        pose.pushPose();
        pose.translate(x, y, 0.0F);

        if (Math.abs(glyphScale - 1.0F) > 0.0001F) {
            pose.translate(glyphWidth * 0.5F, font.lineHeight * 0.5F, 0.0F);
            pose.scale(glyphScale, glyphScale, 1.0F);
            pose.translate(-glyphWidth * 0.5F, -font.lineHeight * 0.5F, 0.0F);
        }

        if (outline != null && outline.enabled && outline.width > 0.01F) {
            int outlineRgb = TitleColorUtil.parseColor(outline.color);
            int outlineArgb = TitleColorUtil.argb(outlineRgb, alpha);
            int radius = Math.max(1, Math.min(4, Math.round(outline.width)));

            for (int oy = -radius; oy <= radius; oy++) {
                for (int ox = -radius; ox <= radius; ox++) {
                    if (ox == 0 && oy == 0) continue;
                    if (ox * ox + oy * oy > radius * radius + 1) continue;
                    graphics.drawString(font, component, ox, oy, outlineArgb, false);
                }
            }
        }

        graphics.drawString(font, component, 0, 0, TitleColorUtil.argb(rgb, alpha), false);
        pose.popPose();
    }

    private static Component glyphComponent(char character, ResourceLocation fontId) {
        return Component.literal(String.valueOf(character)).withStyle(Style.EMPTY.withFont(fontId));
    }

    private static Layout layout(Font font, String text, ResourceLocation fontId) {
        List<Glyph> glyphs = new ArrayList<>();
        List<Integer> lineWidths = new ArrayList<>();

        int x = 0;
        int y = 0;
        int line = 0;
        int maxWidth = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '\n') {
                lineWidths.add(x);
                maxWidth = Math.max(maxWidth, x);
                x = 0;
                y += font.lineHeight + 2;
                line++;
                continue;
            }

            Component component = glyphComponent(c, fontId);
            int width = Math.max(0, font.width(component));
            glyphs.add(new Glyph(i, c, x, y, line, width));
            x += width;
        }

        lineWidths.add(x);
        maxWidth = Math.max(maxWidth, x);
        int height = Math.max(font.lineHeight, y + font.lineHeight);

        return new Layout(glyphs, lineWidths, maxWidth, height);
    }

    private static float alignedLineOffset(String align, Layout layout, int line) {
        int lineWidth = line >= 0 && line < layout.lineWidths.size() ? layout.lineWidths.get(line) : layout.width;

        if (align == null) return 0.0F;

        return switch (align.toLowerCase(Locale.ROOT)) {
            case "right" -> layout.width - lineWidth;
            case "center" -> (layout.width - lineWidth) * 0.5F;
            default -> 0.0F;
        };
    }

    private static int visibleGlyphs(TitleDefinition definition, Lifecycle lifecycle, int count) {
        if (count <= 0) return 0;

        if (lifecycle.phase == Phase.ENTER && "typewriter".equalsIgnoreCase(definition.enter.preset)) {
            return Mth.clamp((int) Math.ceil(count * lifecycle.easedProgress), 0, count);
        }

        if (lifecycle.phase == Phase.EXIT && "type_out".equalsIgnoreCase(definition.exit.preset)) {
            return Mth.clamp((int) Math.ceil(count * (1.0F - lifecycle.easedProgress)), 0, count);
        }

        return count;
    }

    private static Lifecycle lifecycle(TitleDefinition definition, float ageTicks) {
        int enterTicks = Math.max(0, definition.enter.duration);
        int holdTicks = Math.max(0, definition.hold_ticks);
        int exitTicks = Math.max(0, definition.exit.duration);

        if (enterTicks > 0 && ageTicks < enterTicks) {
            float raw = Mth.clamp(ageTicks / enterTicks, 0.0F, 1.0F);
            float p = ease(raw, definition.enter.easing);
            Lifecycle state = new Lifecycle(Phase.ENTER, raw, p);
            applyEnter(definition.enter, state);
            return state;
        }

        float exitStart = enterTicks + holdTicks;
        if (exitTicks > 0 && ageTicks >= exitStart) {
            float raw = Mth.clamp((ageTicks - exitStart) / exitTicks, 0.0F, 1.0F);
            float p = ease(raw, definition.exit.easing);
            Lifecycle state = new Lifecycle(Phase.EXIT, raw, p);
            applyExit(definition.exit, state);
            return state;
        }

        return new Lifecycle(Phase.HOLD, 1.0F, 1.0F);
    }

    private static void applyEnter(TitleDefinition.Transition transition, Lifecycle state) {
        String preset = transition.preset != null ? transition.preset.toLowerCase(Locale.ROOT) : "fade";
        float p = state.easedProgress;

        switch (preset) {
            case "none" -> state.alpha = 1.0F;
            case "fade" -> state.alpha = p;
            case "fade_up" -> {
                state.alpha = p;
                state.offsetY = (1.0F - p) * transition.distance;
            }
            case "fade_down" -> {
                state.alpha = p;
                state.offsetY = -(1.0F - p) * transition.distance;
            }
            case "slide_left" -> {
                state.alpha = p;
                state.offsetX = -(1.0F - p) * transition.distance;
            }
            case "slide_right" -> {
                state.alpha = p;
                state.offsetX = (1.0F - p) * transition.distance;
            }
            case "scale" -> {
                state.alpha = p;
                state.scale = Mth.lerp(p, transition.scale, 1.0F);
            }
            case "pop" -> {
                state.alpha = Mth.clamp(state.rawProgress * 2.0F, 0.0F, 1.0F);
                state.scale = Mth.lerp(p, transition.scale, 1.0F);
            }
            case "typewriter", "explode", "letter_wave" -> state.alpha = 1.0F;
            default -> state.alpha = p;
        }
    }

    private static void applyExit(TitleDefinition.Transition transition, Lifecycle state) {
        String preset = transition.preset != null ? transition.preset.toLowerCase(Locale.ROOT) : "fade";
        float p = state.easedProgress;
        float remaining = 1.0F - p;

        switch (preset) {
            case "none" -> state.alpha = 1.0F;
            case "fade" -> state.alpha = remaining;
            case "fade_up" -> {
                state.alpha = remaining;
                state.offsetY = -transition.distance * p;
            }
            case "fade_down" -> {
                state.alpha = remaining;
                state.offsetY = transition.distance * p;
            }
            case "slide_left" -> {
                state.alpha = remaining;
                state.offsetX = -transition.distance * p;
            }
            case "slide_right" -> {
                state.alpha = remaining;
                state.offsetX = transition.distance * p;
            }
            case "scale_down" -> {
                state.alpha = remaining;
                state.scale = Mth.lerp(p, 1.0F, transition.scale);
            }
            case "scale_up" -> {
                state.alpha = remaining;
                state.scale = Mth.lerp(p, 1.0F, Math.max(1.0F, transition.scale));
            }
            case "type_out", "scatter" -> state.alpha = 1.0F;
            default -> state.alpha = remaining;
        }
    }

    public static float ease(float t, String easing) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        String value = easing != null ? easing.toLowerCase(Locale.ROOT) : "linear";

        return switch (value) {
            case "in_cubic" -> t * t * t;
            case "out_cubic" -> 1.0F - (float) Math.pow(1.0F - t, 3.0D);
            case "in_out_cubic" -> t < 0.5F ? 4.0F * t * t * t : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 3.0D) / 2.0F;
            case "smoothstep" -> t * t * (3.0F - 2.0F * t);
            case "out_back" -> {
                float c1 = 1.70158F;
                float c3 = c1 + 1.0F;
                float x = t - 1.0F;
                yield 1.0F + c3 * x * x * x + c1 * x * x;
            }
            case "in_back" -> {
                float c1 = 1.70158F;
                float c3 = c1 + 1.0F;
                yield c3 * t * t * t - c1 * t * t;
            }
            case "out_quint" -> 1.0F - (float) Math.pow(1.0F - t, 5.0D);
            case "in_quint" -> t * t * t * t * t;
            default -> t;
        };
    }

    private static float staggerProgress(float global, int index, int count, float staggerFraction) {
        if (count <= 1) return global;
        float start = (index / (float) (count - 1)) * staggerFraction;
        return Mth.clamp((global - start) / Math.max(0.001F, 1.0F - staggerFraction), 0.0F, 1.0F);
    }

    private static float anchorX(String anchor, int width) {
        if (anchor == null) return width * 0.5F;
        String lower = anchor.toLowerCase(Locale.ROOT);
        if (lower.endsWith("_left") || "left".equals(lower)) return 0.0F;
        if (lower.endsWith("_right") || "right".equals(lower)) return width;
        return width * 0.5F;
    }

    private static float anchorY(String anchor, int height) {
        if (anchor == null) return 0.0F;
        String lower = anchor.toLowerCase(Locale.ROOT);
        if (lower.startsWith("bottom_") || "bottom".equals(lower)) return height;
        if (lower.startsWith("center_") || "center".equals(lower)) return height * 0.5F;
        return 0.0F;
    }

    private static float hashOffset(long value) {
        value ^= value << 13;
        value ^= value >>> 7;
        value ^= value << 17;
        return ((value & 1023L) / 1023.0F - 0.5F) * 1.6F;
    }

    private enum Phase {
        ENTER, HOLD, EXIT
    }

    private static final class Lifecycle {
        final Phase phase;
        final float rawProgress;
        final float easedProgress;

        float alpha = 1.0F;
        float scale = 1.0F;
        float offsetX;
        float offsetY;
        float rotation;

        Lifecycle(Phase phase, float rawProgress, float easedProgress) {
            this.phase = phase;
            this.rawProgress = rawProgress;
            this.easedProgress = easedProgress;
        }
    }

    private record Glyph(int index, char character, int x, int y, int line, int width) {
    }

    private record Layout(List<Glyph> glyphs, List<Integer> lineWidths, int width, int height) {
    }
}
