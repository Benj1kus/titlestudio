package com.benji.titlestudio.title.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TitleDefinition {

    public int format = 1;
    public String text = "NEW TITLE";
    public String font = "minecraft:default";

    public Position position = new Position();
    public Style style = new Style();
    public EffectSettings effects = new EffectSettings();
    public Transition enter = Transition.enterDefaults();
    public int hold_ticks = 60;
    public Transition exit = Transition.exitDefaults();
    public Trigger trigger = new Trigger();
    public Sound sound = new Sound();

    public void normalize() {
        format = Math.max(1, format);
        if (text == null) text = "";
        if (font == null || font.isBlank()) font = "minecraft:default";

        if (position == null) position = new Position();
        if (style == null) style = new Style();
        if (effects == null) effects = new EffectSettings();
        if (enter == null) enter = Transition.enterDefaults();
        if (exit == null) exit = Transition.exitDefaults();
        if (trigger == null) trigger = new Trigger();
        if (sound == null) sound = new Sound();

        position.normalize();
        style.normalize();
        effects.normalize();
        enter.normalize(true);
        exit.normalize(false);
        trigger.normalize();
        sound.normalize();

        hold_ticks = clamp(hold_ticks, 0, 20 * 60 * 30);
    }

    public int totalTicks() {
        normalize();
        return Math.max(1, enter.duration + hold_ticks + exit.duration);
    }

    public static final class Position {

        public float x = 0.5F;
        public float y = 0.18F;
        public float scale = 2.0F;
        public float rotation = 0.0F;
        public String anchor = "top_center";
        public String align = "center";

        public void normalize() {
            x = clamp(x, -0.5F, 1.5F);
            y = clamp(y, -0.5F, 1.5F);
            scale = clamp(scale, 0.1F, 12.0F);
            rotation = clamp(rotation, -180.0F, 180.0F);
            if (anchor == null || anchor.isBlank()) anchor = "top_center";
            if (align == null || align.isBlank()) align = "center";
            anchor = anchor.toLowerCase(Locale.ROOT);
            align = align.toLowerCase(Locale.ROOT);
        }
    }

    public static final class Style {
        public String color = "#FFFFFF";
        public List<String> gradient = new ArrayList<>();
        public Outline outline = new Outline();

        public void normalize() {
            if (color == null) color = "#FFFFFF";
            if (gradient == null) gradient = new ArrayList<>();
            gradient.removeIf(value -> value == null || value.isBlank());
            if (outline == null) outline = new Outline();
            outline.normalize();
        }
    }

    public static final class Outline {
        public boolean enabled = true;
        public String color = "#1B1110";
        public float width = 1.0F;

        public void normalize() {
            if (color == null) color = "#000000";
            width = clamp(width, 0.0F, 4.0F);
        }
    }

    public static final class EffectSettings {
        public List<String> enabled = new ArrayList<>();

        public float wave_amplitude = 0.85F;
        public float wave_speed = 5.0F;
        public float wave_frequency = 0.55F;

        public float shake_strength = 0.65F;
        public float pulse_amount = 0.06F;
        public float pulse_speed = 2.0F;

        public void normalize() {
            if (enabled == null) enabled = new ArrayList<>();
            List<String> clean = new ArrayList<>();
            for (String value : enabled) {
                if (value == null || value.isBlank()) continue;
                String effect = value.trim().toLowerCase(Locale.ROOT);
                if (!clean.contains(effect)) clean.add(effect);
            }
            enabled = clean;

            wave_amplitude = clamp(wave_amplitude, 0.0F, 16.0F);
            wave_speed = clamp(wave_speed, 0.0F, 30.0F);
            wave_frequency = clamp(wave_frequency, 0.0F, 6.0F);
            shake_strength = clamp(shake_strength, 0.0F, 8.0F);
            pulse_amount = clamp(pulse_amount, 0.0F, 1.5F);
            pulse_speed = clamp(pulse_speed, 0.0F, 20.0F);
        }

        public boolean has(String effect) {
            if (effect == null || enabled == null) return false;
            return enabled.stream().anyMatch(value -> effect.equalsIgnoreCase(value));
        }

        public void set(String effect, boolean active) {
            if (enabled == null) enabled = new ArrayList<>();
            enabled.removeIf(value -> value.equalsIgnoreCase(effect));
            if (active) enabled.add(effect.toLowerCase(Locale.ROOT));
        }
    }

    public static final class Transition {
        public String preset = "fade";
        public String easing = "out_cubic";
        public int duration = 14;
        public float distance = 18.0F;
        public float scale = 0.72F;

        public static Transition enterDefaults() {
            Transition value = new Transition();
            value.preset = "pop";
            value.easing = "out_back";
            value.duration = 14;
            value.distance = 18.0F;
            value.scale = 0.72F;
            return value;
        }

        public static Transition exitDefaults() {
            Transition value = new Transition();
            value.preset = "fade_up";
            value.easing = "in_cubic";
            value.duration = 18;
            value.distance = 14.0F;
            value.scale = 0.92F;
            return value;
        }

        public void normalize(boolean entering) {
            if (preset == null || preset.isBlank()) preset = entering ? "pop" : "fade";
            if (easing == null || easing.isBlank()) easing = entering ? "out_cubic" : "in_cubic";
            preset = preset.toLowerCase(Locale.ROOT);
            easing = easing.toLowerCase(Locale.ROOT);
            duration = clamp(duration, 0, 20 * 60);
            distance = clamp(distance, -400.0F, 400.0F);
            scale = clamp(scale, 0.05F, 8.0F);
        }
    }

    public static final class Trigger {
        public String type = "biome";
        public String target = "minecraft:desert";
        public int minimum_stay_ticks = 8;
        public boolean once_per_visit = true;
        public int cooldown_ticks = 100;

        public void normalize() {
            if (type == null || type.isBlank()) type = "biome";
            if (target == null) target = "";
            type = type.toLowerCase(Locale.ROOT);
            target = target.trim().toLowerCase(Locale.ROOT);
            minimum_stay_ticks = clamp(minimum_stay_ticks, 0, 20 * 60 * 10);
            cooldown_ticks = clamp(cooldown_ticks, 0, 20 * 60 * 60);
        }
    }

    public static final class Sound {
        public String event = "";
        public String source = "master";
        public float volume = 0.8F;
        public float pitch = 1.0F;

        public void normalize() {
            if (event == null) event = "";
            if (source == null || source.isBlank()) source = "master";
            source = source.toLowerCase(Locale.ROOT);
            volume = clamp(volume, 0.0F, 4.0F);
            pitch = clamp(pitch, 0.05F, 4.0F);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
