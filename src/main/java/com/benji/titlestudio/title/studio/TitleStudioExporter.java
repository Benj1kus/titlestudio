package com.benji.titlestudio.title.studio;

import com.benji.titlestudio.title.TitleRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class TitleStudioExporter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private TitleStudioExporter() {
    }

    public record ExportResult(Path root, Path datapack, Path resourcepack) {
    }
    public record ModExportResult(Path root, Path definition) {
    }

    public static ExportResult export(TitleStudioProject project) throws IOException {
        project.normalize();
        TitleStudioWorkspace.save(project);

        String slug = sanitize(project.namespace + "_" + project.title_path.replace('/', '_'));
        Path root = TitleStudioWorkspace.exportsRoot().resolve(slug);
        Path datapack = root.resolve("datapack");
        Path resourcepack = root.resolve("resourcepack");

        deleteTree(root);
        Files.createDirectories(datapack);
        Files.createDirectories(resourcepack);

        writePackMcmeta(datapack, "Title Studio Data - " + project.titleId());
        writePackMcmeta(resourcepack, "Title Studio Resources - " + project.titleId());

        Path definition = datapack.resolve("data").resolve(project.namespace).resolve("title_presentations").resolve(project.title_path + ".json");

        writeDefinition(project, definition);

        Path namespaceAssets = resourcepack.resolve("assets").resolve(project.namespace);
        Files.createDirectories(namespaceAssets);

        copyImportedAssets(project, namespaceAssets);
        writeSounds(project, namespaceAssets, false);
        writeFonts(project, namespaceAssets);

        zipTree(datapack, root.resolve("datapack.zip"));
        zipTree(resourcepack, root.resolve("resourcepack.zip"));

        return new ExportResult(root, datapack, resourcepack);
    }

    public static ExportResult installToCurrentInstance(TitleStudioProject project) throws IOException {
        ExportResult result = export(project);
        Minecraft minecraft = Minecraft.getInstance();

        String slug = sanitize(project.namespace + "_" + project.title_path.replace('/', '_'));

        Path resourceTarget = minecraft.gameDirectory.toPath().resolve("resourcepacks").resolve("TitleStudio_" + slug);

        deleteTree(resourceTarget);
        copyTree(result.resourcepack, resourceTarget);

        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server != null) {
            Path worldRoot = server.getWorldPath(LevelResource.ROOT);
            Path dataTarget = worldRoot.resolve("datapacks").resolve("TitleStudio_" + slug);
            deleteTree(dataTarget);
            copyTree(result.datapack, dataTarget);
        }

        return result;
    }

    public static ModExportResult exportForMod(TitleStudioProject project, Path destinationParent) throws IOException {
        if (destinationParent == null) {
            throw new IOException("No export folder selected");
        }

        project.normalize();
        TitleStudioWorkspace.save(project);

        Files.createDirectories(destinationParent);

        String namespace = sanitizeNamespaceFolder(project.namespace);
        Path root = destinationParent.resolve("TitleStudio_ModExport_" + namespace);

        Path definition = root.resolve("data").resolve(project.namespace).resolve("title_presentations").resolve(project.title_path + ".json");

        writeDefinition(project, definition);

        Path namespaceAssets = root.resolve("assets").resolve(project.namespace);
        Files.createDirectories(namespaceAssets);

        copyImportedAssets(project, namespaceAssets);
        writeSounds(project, namespaceAssets, true);
        writeFonts(project, namespaceAssets);

        return new ModExportResult(root, definition);
    }

    private static void writeDefinition(TitleStudioProject project, Path definition) throws IOException {
        Files.createDirectories(definition.getParent());
        Files.writeString(definition, TitleRegistry.toJson(project.definition), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void copyImportedAssets(TitleStudioProject project, Path namespaceAssets) throws IOException {
        Path imported = TitleStudioWorkspace.assetRoot(project);
        if (Files.exists(imported)) {
            copyTree(imported, namespaceAssets);
        }
    }

    private static void writeSounds(TitleStudioProject project, Path namespaceAssets, boolean mergeExisting) throws IOException {
        if (project.sounds == null || project.sounds.isEmpty()) {
            return;
        }

        Path soundsJson = namespaceAssets.resolve("sounds.json");
        JsonObject root = new JsonObject();

        if (mergeExisting && Files.isRegularFile(soundsJson)) {
            try {
                JsonElement existing = GSON.fromJson(Files.readString(soundsJson, StandardCharsets.UTF_8), JsonElement.class);

                if (existing != null && existing.isJsonObject()) {
                    root = existing.getAsJsonObject();
                }
            } catch (Exception ignored) {
                root = new JsonObject();
            }
        }

        for (Map.Entry<String, String> entry : project.sounds.entrySet()) {
            JsonObject sound = new JsonObject();
            JsonArray array = new JsonArray();
            JsonObject item = new JsonObject();

            item.addProperty("name", project.namespace + ":" + entry.getValue());
            item.addProperty("stream", false);

            array.add(item);
            sound.add("sounds", array);
            root.add(entry.getKey(), sound);
        }

        Files.createDirectories(namespaceAssets);
        Files.writeString(soundsJson, GSON.toJson(root), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void writeFonts(TitleStudioProject project, Path namespaceAssets) throws IOException {
        if (project.imported_fonts == null || project.imported_fonts.isEmpty()) return;

        Path fontDir = namespaceAssets.resolve("font");
        Files.createDirectories(fontDir);

        for (String fontPath : project.imported_fonts.keySet()) {
            JsonObject provider = new JsonObject();
            provider.addProperty("type", "ttf");
            provider.addProperty("file", project.namespace + ":" + fontPath + ".ttf");
            provider.addProperty("size", 16.0F);
            provider.addProperty("oversample", 4.0F);

            JsonArray shift = new JsonArray();
            shift.add(0.0F);
            shift.add(0.0F);
            provider.add("shift", shift);

            JsonArray providers = new JsonArray();
            providers.add(provider);

            JsonObject fallback = new JsonObject();
            fallback.addProperty("type", "reference");
            fallback.addProperty("id", "minecraft:default");
            providers.add(fallback);

            JsonObject root = new JsonObject();
            root.add("providers", providers);

            Files.writeString(fontDir.resolve(fontPath + ".json"), GSON.toJson(root), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    private static void writePackMcmeta(Path root, String description) throws IOException {
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", 15);
        pack.addProperty("description", description);

        JsonObject wrapper = new JsonObject();
        wrapper.add("pack", pack);

        Files.writeString(root.resolve("pack.mcmeta"), GSON.toJson(wrapper), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void zipTree(Path source, Path zipFile) throws IOException {
        Files.deleteIfExists(zipFile);
        Files.createDirectories(zipFile.getParent());

        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            try (var stream = Files.walk(source)) {
                for (Path path : stream.filter(Files::isRegularFile).toList()) {
                    String entryName = source.relativize(path).toString().replace('\\', '/');
                    zip.putNextEntry(new ZipEntry(entryName));
                    Files.copy(path, zip);
                    zip.closeEntry();
                }
            }
        }
    }

    private static void copyTree(Path source, Path target) throws IOException {
        if (!Files.exists(source)) return;

        try (var stream = Files.walk(source)) {
            for (Path path : stream.toList()) {
                Path relative = source.relativize(path);
                Path out = target.resolve(relative.toString());

                if (Files.isDirectory(path)) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(path, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static String sanitize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }

    private static String sanitizeNamespaceFolder(String value) {
        if (value == null || value.isBlank()) {
            return "your_mod";
        }

        String result = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        return result.isBlank() ? "your_mod" : result;
    }
}
