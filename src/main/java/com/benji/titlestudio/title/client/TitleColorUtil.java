package com.benji.titlestudio.title.client;

import net.minecraft.util.Mth;

import java.awt.Color;
import java.util.List;
import java.util.Locale;

public final class TitleColorUtil {

    private TitleColorUtil() {
    }

    public static int parseColor(String value) {
        if (value == null) return 0xFFFFFF;

        value = value.trim().toLowerCase(Locale.ROOT);

        return switch (value) {
            case "blue" -> 0x4AA3FF;
            case "red" -> 0xFF4D55;
            case "gold", "golden" -> 0xFFD45A;
            case "green" -> 0x55E878;
            case "white" -> 0xFFFFFF;
            case "black" -> 0x000000;
            case "purple" -> 0xB76CFF;
            case "cyan" -> 0x42F2E1;
            case "orange" -> 0xFF9E42;
            case "pink" -> 0xFF79C8;
            default -> parseHex(value);
        };
    }

    public static int gradientColor(List<String> colors, float t) {
        if (colors == null || colors.isEmpty()) return 0xFFFFFF;
        if (colors.size() == 1) return parseColor(colors.get(0));

        t = Mth.clamp(t, 0.0F, 1.0F);
        int sections = colors.size() - 1;
        float scaled = t * sections;
        int index = Mth.clamp((int) Math.floor(scaled), 0, sections - 1);
        float local = scaled - index;

        int a = parseColor(colors.get(index));
        int b = parseColor(colors.get(index + 1));

        int r = Math.round(Mth.lerp(local, (a >> 16) & 255, (b >> 16) & 255));
        int g = Math.round(Mth.lerp(local, (a >> 8) & 255, (b >> 8) & 255));
        int bl = Math.round(Mth.lerp(local, a & 255, b & 255));
        return (r << 16) | (g << 8) | bl;
    }

    public static int rainbow(float hue) {
        return Color.HSBtoRGB(hue - (float) Math.floor(hue), 0.76F, 1.0F) & 0xFFFFFF;
    }

    public static int argb(int rgb, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
        if (a > 0 && a < 4) a = 4;
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static int parseHex(String value) {
        try {
            if (value.startsWith("#")) value = value.substring(1);
            if (value.startsWith("0x")) value = value.substring(2);

            if (value.length() == 3) {
                value = "" + value.charAt(0) + value.charAt(0) + value.charAt(1) + value.charAt(1) + value.charAt(2) + value.charAt(2);
            }

            return Integer.parseInt(value, 16) & 0xFFFFFF;
        } catch (Exception ignored) {
            return 0xFFFFFF;
        }
    }
}
