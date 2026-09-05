package com.benji.titlestudio.title.studio;

import com.benji.titlestudio.title.TitleRegistry;
import com.benji.titlestudio.title.data.TitleDefinition;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

public final class TitleStudioWorkspace {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private TitleStudioWorkspace() {
    }

    public static Path root() {
        Path gameDirectory = Minecraft.getInstance().gameDirectory.toPath();

        Path current = gameDirectory.resolve("title_studio");
        Path legacy = gameDirectory.resolve("oasiso_title_studio");
        if (!Files.exists(current) && Files.exists(legacy)) {
            return legacy;
        }
        return current;
    }

    public static Path projectsRoot() {
        return root().resolve("projects");
    }

    public static Path exportsRoot() {
        return root().resolve("exports");
    }

    public static Path projectRoot(TitleStudioProject project) {
        return projectsRoot().resolve(project.workspace_id);
    }

    public static Path projectJson(TitleStudioProject project) {
        return projectRoot(project).resolve("project.json");
    }

    public static Path assetRoot(TitleStudioProject project) {
        return projectRoot(project).resolve("assets");
    }

    public static void save(TitleStudioProject project) throws IOException {
        project.normalize();
        Files.createDirectories(projectRoot(project));
        Files.writeString(projectJson(project), GSON.toJson(project), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        Files.createDirectories(root());
        Files.writeString(root().resolve("last_project.txt"), project.workspace_id, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public static TitleStudioProject load(Path json) throws IOException {
        TitleStudioProject project = GSON.fromJson(Files.readString(json, StandardCharsets.UTF_8), TitleStudioProject.class);

        if (project == null) throw new IOException("Invalid Title Studio project");
        project.normalize();
        return project;
    }

    public static TitleStudioProject loadLastOrDefault() {
        try {
            Path marker = root().resolve("last_project.txt");
            if (Files.isRegularFile(marker)) {
                String id = Files.readString(marker, StandardCharsets.UTF_8).trim();
                Path json = projectsRoot().resolve(id).resolve("project.json");
                if (Files.isRegularFile(json)) return load(json);
            }
        } catch (Exception ignored) {
        }
        return TitleStudioProject.createDefault();
    }

    public static String importSound(TitleStudioProject project, Path source) throws IOException {
        requireExtension(source, ".ogg");

        String fileName = sanitizeFileName(source.getFileName().toString());
        String base = fileName.substring(0, fileName.length() - 4);
        String eventKey = sanitizeResourcePath(base);

        Path target = assetRoot(project).resolve("sounds/titles").resolve(eventKey + ".ogg");

        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);

        project.sounds.put(eventKey, "titles/" + eventKey);
        return project.namespace + ":" + eventKey;
    }

    public static String importFont(TitleStudioProject project, Path source) throws IOException {
        requireExtension(source, ".ttf");

        String fileName = sanitizeFileName(source.getFileName().toString());
        String base = fileName.substring(0, fileName.length() - 4);
        base = sanitizeResourcePath(base);

        Path target = assetRoot(project).resolve("font").resolve(base + ".ttf");

        Files.createDirectories(target.getParent());

        Path normalizedSource = source.toAbsolutePath().normalize();
        Path normalizedTarget = target.toAbsolutePath().normalize();

        if (!normalizedSource.equals(normalizedTarget)) {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }

        project.imported_fonts.put(base, "font/" + base + ".ttf");
        return project.namespace + ":" + base;
    }

    public static void importTitleJson(TitleStudioProject project, Path source) throws IOException {
        requireExtension(source, ".json");
        TitleDefinition definition = TitleRegistry.fromJson(Files.readString(source, StandardCharsets.UTF_8));
        if (definition == null) throw new IOException("Title JSON is empty or invalid");
        project.definition = definition;
        project.normalize();
    }

    private static void requireExtension(Path source, String extension) throws IOException {
        if (source == null || !Files.isRegularFile(source) || !source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension)) {
            throw new IOException("Expected " + extension + " file");
        }
    }

    private static String sanitizeFileName(String value) {
        value = value.toLowerCase(Locale.ROOT).replace(' ', '_').replaceAll("[^a-z0-9_.-]", "_");
        return value.isBlank() ? "asset" : value;
    }

    private static String sanitizeResourcePath(String value) {
        value = value.toLowerCase(Locale.ROOT).replace(' ', '_').replaceAll("[^a-z0-9_./-]", "_");
        return value.isBlank() ? "title_asset" : value;
    }
}
