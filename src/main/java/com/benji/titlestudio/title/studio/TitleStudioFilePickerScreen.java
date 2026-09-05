package com.benji.titlestudio.title.studio;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class TitleStudioFilePickerScreen extends TitleStudioRetroScreen {

    private static final int MAX_PAGE_SIZE = 8;
    private static final int ROW_HEIGHT = 20;

    private final Screen parent;
    private final Path directory;

    private final String extension;
    private final Consumer<Path> callback;
    private final int page;
    private final boolean selectDirectory;

    private List<Path> entries = List.of();
    private String error;

    public TitleStudioFilePickerScreen(Screen parent, Path directory, String extension, Consumer<Path> callback) {
        this(parent, directory, extension, callback, 0, false);
    }

    public TitleStudioFilePickerScreen(Screen parent, Path directory, String extension, Consumer<Path> callback, boolean selectDirectory) {
        this(parent, directory, extension, callback, 0, selectDirectory);
    }

    private TitleStudioFilePickerScreen(Screen parent, Path directory, String extension, Consumer<Path> callback, int page, boolean selectDirectory) {
        super(Component.literal(selectDirectory ? "Title Studio - Folder Picker" : "Title Studio - File Picker"));

        this.parent = parent;
        this.directory = directory;
        this.extension = extension != null ? extension.toLowerCase(Locale.ROOT) : "";
        this.callback = callback;
        this.page = Math.max(0, page);
        this.selectDirectory = selectDirectory;
    }

    @Override
    protected void init() {
        entries = readEntries();

        int panelW = Math.min(560, width - 30);
        int left = (width - panelW) / 2;

        int top = 22;
        int quickY = top + 24;
        int pathY = top + 46;
        int listTop = top + 70;
        int panelBottom = height - 18;
        int navY = panelBottom - 36;
        int listBottom = navY - 6;
        int rowsPerPage = Math.max(1, Math.min(MAX_PAGE_SIZE, (listBottom - listTop) / ROW_HEIGHT));

        buildQuickLocationButtons(left, panelW, quickY);

        Path parentDir = directory != null ? directory.getParent() : null;

        TitleStudioButton parentButton = new TitleStudioButton(left + 10, pathY, 72, 18, Component.literal("← Parent"), button -> {
            if (directory == null) {
                return;
            }

            minecraft.setScreen(new TitleStudioFilePickerScreen(parent, parentDir, extension, callback, 0, selectDirectory));
        });

        parentButton.active = directory != null;

        addRenderableWidget(parentButton);

        if (directory != null && directory.getParent() == null) {

            parentButton.active = true;
        }

        int maxPage = Math.max(0, (entries.size() - 1) / rowsPerPage);
        int currentPage = Math.min(page, maxPage);
        int start = currentPage * rowsPerPage;
        int end = Math.min(entries.size(), start + rowsPerPage);

        for (int i = start; i < end; i++) {
            Path path = entries.get(i);

            boolean dir = Files.isDirectory(path);

            String fileName = displayName(path);

            String label = (dir ? "[DIR] " : "") + fileName;

            int row = i - start;

            addRenderableWidget(new TitleStudioButton(left + 10, listTop + row * ROW_HEIGHT, panelW - 20, 18, Component.literal(shorten(label, 72)), button -> {
                if (dir) {
                    minecraft.setScreen(new TitleStudioFilePickerScreen(parent, path, extension, callback, 0, selectDirectory));

                    return;
                }

                if (!selectDirectory) {
                    callback.accept(path);

                    if (minecraft.screen == this) {
                        minecraft.setScreen(parent);
                    }
                }
            }));
        }

        TitleStudioButton previousButton = new TitleStudioButton(left + 10, navY, 30, 18, Component.literal("<"), button -> {
            if (currentPage > 0) {
                minecraft.setScreen(new TitleStudioFilePickerScreen(parent, directory, extension, callback, currentPage - 1, selectDirectory));
            }
        });

        previousButton.active = currentPage > 0;

        addRenderableWidget(previousButton);

        TitleStudioButton nextButton = new TitleStudioButton(left + 44, navY, 30, 18, Component.literal(">"), button -> {
            if (currentPage < maxPage) {
                minecraft.setScreen(new TitleStudioFilePickerScreen(parent, directory, extension, callback, currentPage + 1, selectDirectory));
            }
        });

        nextButton.active = currentPage < maxPage;

        addRenderableWidget(nextButton);

        if (selectDirectory) {
            int selectW = Math.min(132, Math.max(104, panelW / 3));

            int cancelW = 64;

            TitleStudioButton selectButton = new TitleStudioButton(left + panelW - selectW - cancelW - 16, navY, selectW, 18, Component.literal("Select this folder"), button -> {
                if (directory == null || !Files.isDirectory(directory)) {

                    error = "Choose a real folder first";
                    return;
                }

                callback.accept(directory);

                if (minecraft.screen == this) {
                    minecraft.setScreen(parent);
                }
            });

            selectButton.active = directory != null && Files.isDirectory(directory);

            addRenderableWidget(selectButton);
            addRenderableWidget(new TitleStudioButton(left + panelW - cancelW - 10, navY, cancelW, 18, Component.literal("Cancel"), button -> minecraft.setScreen(parent)));

        } else {
            addRenderableWidget(new TitleStudioButton(left + panelW - 74, navY, 64, 18, Component.literal("Cancel"), button -> minecraft.setScreen(parent)));
        }
    }

    private void buildQuickLocationButtons(int left, int panelW, int y) {
        Path home = getUserHome();

        Path downloads = childIfDirectory(home, "Downloads");

        Path desktop = childIfDirectory(home, "Desktop");

        Path game = minecraft != null && minecraft.gameDirectory != null

                ? minecraft.gameDirectory.toPath().toAbsolutePath().normalize()

                : null;


        List<QuickLocation> locations = new ArrayList<>();
        locations.add(new QuickLocation("Computer", null, true));


        if (home != null) {
            locations.add(new QuickLocation("Home", home, true));
        }


        if (downloads != null) {
            locations.add(new QuickLocation("Downloads", downloads, true));
        }


        if (desktop != null) {
            locations.add(new QuickLocation("Desktop", desktop, true));
        }


        if (game != null && Files.isDirectory(game)) {

            locations.add(new QuickLocation("Minecraft", game, true));
        }


        int gap = 4;
        int available = panelW - 20;
        int count = Math.max(1, locations.size());
        int buttonW = Math.max(54, (available - gap * (count - 1)) / count);
        int totalWidth = buttonW * count + gap * (count - 1);


        if (totalWidth > available) {

            buttonW = Math.max(42, (available - gap * (count - 1)) / count);
        }


        for (int index = 0; index < locations.size(); index++) {

            QuickLocation location = locations.get(index);


            int x = left + 10 + index * (buttonW + gap);


            String shown = shorten(location.label(), Math.max(4, buttonW / 6));


            addRenderableWidget(new TitleStudioButton(x, y, buttonW, 18, Component.literal(shown), button -> minecraft.setScreen(new TitleStudioFilePickerScreen(parent, location.path(), extension, callback, 0, selectDirectory))));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int panelW = Math.min(560, width - 30);
        int left = (width - panelW) / 2;
        int top = 22;
        int bottom = height - 18;


        TitleStudioRetroTheme.drawPanel(graphics, left, top, left + panelW, bottom);


        TitleStudioRetroTheme.drawTitleBar(graphics, left + 3, top + 3, left + panelW - 3, 20);


        graphics.drawString(font, selectDirectory ? "FOLDER PICKER  •  EXPORT FOR MODS" : "FILE PICKER  •  " + extension.toUpperCase(Locale.ROOT), left + 10, top + 8, TitleStudioRetroTheme.LIME, false);


        String shownPath = directory == null ? "Computer" : directory.toAbsolutePath().normalize().toString();
        graphics.drawString(font,

                shorten(shownPath, Math.max(20, (panelW - 110) / 6)),

                left + 92, top + 50,

                TitleStudioRetroTheme.TEXT_MUTED,

                false);


        if (error != null) {
            graphics.drawString(font, error, left + 10, bottom - 14, TitleStudioRetroTheme.ERROR, false);

        } else if (selectDirectory) {

            graphics.drawString(font, directory == null ? "Choose a drive/folder first." : "Open a folder, then press Select this folder.", left + 10, bottom - 14, TitleStudioRetroTheme.TEXT_MUTED, false);
        }


        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private List<Path> readEntries() {
        if (directory == null) {

            List<Path> roots = new ArrayList<>();


            try {
                File[] files = File.listRoots();


                if (files != null) {

                    for (File file : files) {

                        if (file != null) {
                            roots.add(file.toPath().toAbsolutePath().normalize());
                        }
                    }
                }

            } catch (Exception exception) {

                error = exception.getMessage();
            }


            roots.sort(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER));


            return roots;
        }


        if (!Files.isDirectory(directory)) {

            return List.of();
        }


        try (var stream = Files.list(directory)) {

            List<Path> list = new ArrayList<>();


            stream.forEach(path -> {

                if (Files.isDirectory(path)) {

                    list.add(path);

                    return;
                }


                if (selectDirectory) {
                    return;
                }


                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);


                if (extension.isBlank() || name.endsWith(extension)) {

                    list.add(path);
                }
            });


            list.sort(Comparator.comparing((Path path) -> !Files.isDirectory(path)).thenComparing(path -> displayName(path).toLowerCase(Locale.ROOT)));


            return list;

        } catch (Exception exception) {

            error = exception.getMessage();

            return List.of();
        }
    }

    private static Path getUserHome() {
        try {
            String value = System.getProperty("user.home");


            if (value == null || value.isBlank()) {

                return null;
            }


            Path path = Path.of(value).toAbsolutePath().normalize();


            return Files.isDirectory(path) ? path : null;

        } catch (Exception ignored) {

            return null;
        }
    }


    private static Path childIfDirectory(Path parent, String child) {
        if (parent == null || child == null || child.isBlank()) {

            return null;
        }


        try {
            Path result = parent.resolve(child).toAbsolutePath().normalize();


            return Files.isDirectory(result) ? result : null;

        } catch (Exception ignored) {

            return null;
        }
    }


    private static String displayName(Path path) {
        if (path == null) {
            return "";
        }


        Path fileName = path.getFileName();


        if (fileName != null) {

            String value = fileName.toString();


            if (!value.isBlank()) {
                return value;
            }
        }
        return path.toString();
    }


    private static String shorten(String value, int max) {
        if (value == null) {
            return "";
        }

        if (value.length() <= max) {

            return value;
        }

        return "…" + value.substring(Math.max(0, value.length() - max + 1));
    }


    private record QuickLocation(String label, Path path, boolean enabled) {
    }
}
