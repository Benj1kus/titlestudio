package com.benji.titlestudio.title.studio;

import com.benji.titlestudio.title.network.TitleNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class TitleStudioRegistryBrowserScreen extends TitleStudioRetroScreen {

    private static final int ROW_H = 18;
    private static final int ROW_STEP = 21;
    private static final int WHEEL_STEP = 42;

    private final net.minecraft.client.gui.screens.Screen parent;
    final String type;
    private final Consumer<String> callback;

    private TitleStudioTextField search;

    private List<BrowserEntry> all = List.of();

    private List<BrowserEntry> filtered = List.of();

    private int scrollOffset;
    private boolean waitingForServer;
    private boolean draggingScrollbar;

    public TitleStudioRegistryBrowserScreen(net.minecraft.client.gui.screens.Screen parent, String type, Consumer<String> callback) {
        super(Component.literal("Title Studio - Registry Browser"));

        this.parent = parent;

        this.type = type != null ? type.toLowerCase(Locale.ROOT) : "biome";

        this.callback = callback;
    }

    @Override
    protected void init() {
        all = collectClientIds();

        rebuildFiltered("");

        int panelW = panelWidth();
        int left = panelLeft();
        int top = panelTop();

        search = new TitleStudioTextField(font, left + 12, top + 30, panelW - 24, 18, Component.literal("Search"));
        search.setMaxLength(128);
        search.setResponder(value -> {
            scrollOffset = 0;

            rebuildFiltered(value);

            rebuildButtons();
        });

        rebuildButtons();
        setInitialFocus(search);

        if (minecraft.getConnection() != null) {

            waitingForServer = true;

            TitleNetwork.requestRegistry(type);
        }
    }

    public static void receiveServerEntries(String type, List<ResourceLocation> ids, List<ResourceLocation> templates) {
        Minecraft minecraft = Minecraft.getInstance();

        if (!(minecraft.screen instanceof TitleStudioRegistryBrowserScreen screen)) {

            return;
        }

        String normalized = type != null ? type.toLowerCase(Locale.ROOT) : "biome";

        if (!screen.type.equals(normalized)) {

            return;
        }

        screen.applyServerEntries(ids, templates);
    }

    private void applyServerEntries(List<ResourceLocation> ids, List<ResourceLocation> templates) {
        waitingForServer = false;

        List<BrowserEntry> result = new ArrayList<>();

        if (ids != null) {
            for (ResourceLocation id : ids) {
                if (id != null) {
                    result.add(new BrowserEntry(id, false));
                }
            }
        }

        if ("structure".equals(type) && templates != null) {

            for (ResourceLocation id : templates) {

                if (id == null) {
                    continue;
                }
                boolean alreadyWorldgen = result.stream().anyMatch(entry -> !entry.template() && entry.id().equals(id));

                if (!alreadyWorldgen) {
                    result.add(new BrowserEntry(id, true));
                }
            }
        }

        result.sort(Comparator.comparing(BrowserEntry::template).thenComparing(entry -> entry.id().toString()));

        all = result;

        scrollOffset = 0;

        rebuildFiltered(search != null ? search.getValue() : "");

        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();

        if (search != null) {
            addRenderableWidget(search);
        }

        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll());

        int left = panelLeft();
        int panelW = panelWidth();
        int top = listTop();
        int bottom = listBottom();
        int first = Math.max(0, scrollOffset / ROW_STEP);
        int yOffset = -(scrollOffset % ROW_STEP);
        int visibleSlot = 0;

        for (int index = first; index < filtered.size(); index++) {

            int y = top + yOffset + visibleSlot * ROW_STEP;
            if (y + ROW_H > bottom) {

                break;
            }

            if (y >= top) {

                BrowserEntry entry = filtered.get(index);

                String prefix = entry.template() ? "[TEMPLATE] " : "";

                TitleStudioButton button = new TitleStudioButton(left + 12, y, panelW - 31, ROW_H, Component.literal(prefix + entry.id()), pressed -> {
                    if (entry.template()) {
                        return;
                    }

                    callback.accept(entry.id().toString());

                    if (minecraft.screen == this) {

                        minecraft.setScreen(parent);
                    }
                });

                if (entry.template()) {
                    button.active = false;
                }

                addRenderableWidget(button);
            }

            visibleSlot++;
        }

        addRenderableWidget(new TitleStudioButton(left + panelW - 86, panelBottom() - 24, 74, 18, Component.literal("Cancel"), button -> minecraft.setScreen(parent)));

        if (search != null) {
            setInitialFocus(search);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int left = panelLeft();
        int panelW = panelWidth();
        int top = panelTop();
        int bottom = panelBottom();

        TitleStudioRetroTheme.drawPanel(graphics, left, top, left + panelW, bottom);

        TitleStudioRetroTheme.drawTitleBar(graphics, left + 3, top + 3, left + panelW - 3, 20);

        graphics.drawString(font, "REGISTRY  •  " + type.toUpperCase(Locale.ROOT), left + 10, top + 8, TitleStudioRetroTheme.LIME, false);

        String count = filtered.size() + " entries";

        graphics.drawString(font, count, left + panelW - font.width(count) - 10, top + 8, TitleStudioRetroTheme.TEXT_MUTED, false);

        TitleStudioRetroTheme.drawDarkInset(graphics, left + 9, listTop() - 3, left + panelW - 9, listBottom() + 3);

        if (Minecraft.getInstance().level == null) {

            graphics.drawString(font, "Join a world to read registries.", left + 14, listTop() + 6, TitleStudioRetroTheme.WARNING, false);

        } else if (waitingForServer && filtered.isEmpty()) {

            graphics.drawString(font, "Reading registry from server...", left + 14, listTop() + 6, TitleStudioRetroTheme.TEXT_HINT, false);

        } else if (filtered.isEmpty()) {

            graphics.drawString(font, "No matching entries.", left + 14, listTop() + 6, TitleStudioRetroTheme.TEXT_MUTED, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        renderScrollbar(graphics);

        if ("structure".equals(type)) {

            String hint = "WORLDGEN = selectable" + "  •  TEMPLATE = raw .nbt asset";

            graphics.drawString(font, font.plainSubstrByWidth(hint, Math.max(60, panelW - 112)), left + 12, bottom - 21, TitleStudioRetroTheme.TEXT_HINT, false);
        }
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int max = maxScroll();

        if (max <= 0) {
            return;
        }

        int top = listTop();
        int bottom = listBottom();
        int trackH = bottom - top;
        int contentH = Math.max(trackH, filtered.size() * ROW_STEP);
        int thumbH = Math.max(16, Math.round(trackH * (trackH / (float) contentH)));
        int travel = Math.max(1, trackH - thumbH);
        int thumbY = top + Math.round(travel * (scrollOffset / (float) max));

        TitleStudioRetroTheme.drawRetroScrollTrack(graphics, panelLeft() + panelWidth() - 17, top, bottom, thumbY, thumbH);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= panelLeft() && mouseX <= panelLeft() + panelWidth()

                && mouseY >= listTop() && mouseY <= listBottom()) {

            int old = scrollOffset;

            scrollOffset = Mth.clamp(scrollOffset - (int) Math.signum(delta) * WHEEL_STEP, 0, maxScroll());

            if (old != scrollOffset) {
                rebuildButtons();
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hitScrollbar(mouseX, mouseY)) {

            draggingScrollbar = true;

            updateScrollFromMouse(mouseY);

            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar && button == 0) {

            updateScrollFromMouse(mouseY);

            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar) {
            draggingScrollbar = false;

            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void rebuildFiltered(String query) {
        String q = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";

        List<BrowserEntry> result = new ArrayList<>();

        for (BrowserEntry entry : all) {

            String id = entry.id().toString().toLowerCase(Locale.ROOT);

            if (q.isEmpty() || id.contains(q) || (entry.template() && "template".contains(q))) {

                result.add(entry);
            }
        }

        filtered = result;

        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll());
    }

    private List<BrowserEntry> collectClientIds() {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null) {

            return List.of();
        }

        try {
            List<BrowserEntry> result = new ArrayList<>();
            if (!"structure".equals(type)) {

                Registry<Biome> registry = minecraft.level.registryAccess().registryOrThrow(Registries.BIOME);

                for (ResourceLocation id : registry.keySet()) {

                    result.add(new BrowserEntry(id, false));
                }
            }

            result.sort(Comparator.comparing(entry -> entry.id().toString()));

            return result;

        } catch (Exception ignored) {
            return List.of();
        }
    }

    private int panelWidth() {
        return Math.min(520, width - 26);
    }

    private int panelLeft() {
        return (width - panelWidth()) / 2;
    }

    private int panelTop() {
        return 28;
    }
    private int panelBottom() {
        return height - 14;
    }

    private int listTop() {
        return panelTop() + 58;
    }

    private int listBottom() {
        return Math.max(listTop() + 28, panelBottom() - 34);
    }

    private int maxScroll() {
        int viewportH = Math.max(1, listBottom() - listTop());

        int contentH = filtered.size() * ROW_STEP;

        return Math.max(0, contentH - viewportH);
    }

    private boolean hitScrollbar(double mouseX, double mouseY) {
        if (maxScroll() <= 0) {
            return false;
        }

        int x = panelLeft() + panelWidth() - 22;

        return mouseX >= x && mouseX <= x + 10

                && mouseY >= listTop() && mouseY <= listBottom();
    }

    private void updateScrollFromMouse(double mouseY) {
        int max = maxScroll();

        if (max <= 0) {
            return;
        }

        float t = Mth.clamp((float) ((mouseY - listTop()) / (listBottom() - listTop())), 0.0F, 1.0F);

        scrollOffset = Math.round(t * max);

        rebuildButtons();
    }

    private record BrowserEntry(ResourceLocation id, boolean template) {
    }
}
