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

        copyUsedAssets(project, namespaceAssets);
        writeSounds(project, namespaceAssets);
        writeFonts(project, namespaceAssets);

        zipTree(datapack, root.resolve("datapack.zip"));
        zipTree(resourcepack, root.resolve("resourcepack.zip"));

        writePackReadme(root);

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
        String titleSlug = sanitize(project.title_path.replace('/', '_'));

        Path root = destinationParent.resolve("TitleStudio_ModExport_" + namespace + "_" + titleSlug);

        deleteTree(root);

        Files.createDirectories(root);

        Path definition = root.resolve("data").resolve(project.namespace).resolve("title_presentations").resolve(project.title_path + ".json");
        writeDefinition(project, definition);
        Path namespaceAssets = root.resolve("assets").resolve(project.namespace);

        Files.createDirectories(namespaceAssets);

        copyUsedAssets(project, namespaceAssets);
        writeModSoundsFragment(project, root);

        writeFonts(project, namespaceAssets);

        writeModReadme(root, project.namespace);

        return new ModExportResult(root, definition);
    }

    private static void writeDefinition(TitleStudioProject project, Path definition) throws IOException {
        Files.createDirectories(definition.getParent());
        Files.writeString(definition, TitleRegistry.toJson(project.definition), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void copyUsedAssets(TitleStudioProject project, Path namespaceAssets) throws IOException {

        Path imported = TitleStudioWorkspace.assetRoot(project);
        if (!Files.exists(imported)) {
            return;
        }

        String soundKey = localResourceKey(project.definition.sound.event, project.namespace);

        if (soundKey != null) {
            String soundPath = project.sounds.get(soundKey);
            if (soundPath != null && !soundPath.isBlank()) {
                copyOne(imported.resolve("sounds").resolve(soundPath + ".ogg"), namespaceAssets.resolve("sounds").resolve(soundPath + ".ogg"));
            }
        }

        String fontKey = localResourceKey(project.definition.font, project.namespace);

        if (fontKey != null) {

            String fontPath = project.imported_fonts.get(fontKey);
            if (fontPath != null && !fontPath.isBlank()) {
                copyOne(imported.resolve(fontPath), namespaceAssets.resolve(fontPath));
            }
        }
    }

    private static String localResourceKey(String resourceId, String namespace) {
        if (resourceId == null || resourceId.isBlank()) {

            return null;
        }

        String prefix = namespace + ":";

        if (!resourceId.startsWith(prefix)) {
            return null;
        }

        String key = resourceId.substring(prefix.length());

        return key.isBlank() ? null : key;
    }

    private static void copyOne(Path source, Path target) throws IOException {

        if (!Files.isRegularFile(source)) {
            return;
        }

        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void writeSounds(TitleStudioProject project, Path namespaceAssets) throws IOException {
        JsonObject root = createUsedSoundsJson(project);
        if (root.size() == 0) {
            return;
        }

        Files.createDirectories(namespaceAssets);
        Files.writeString(namespaceAssets.resolve("sounds.json"), GSON.toJson(root), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static JsonObject createUsedSoundsJson(TitleStudioProject project) {
        JsonObject root = new JsonObject();

        String soundKey = localResourceKey(project.definition.sound.event, project.namespace);

        if (soundKey == null) {
            return root;
        }

        String soundPath = project.sounds.get(soundKey);

        if (soundPath == null || soundPath.isBlank()) {

            return root;
        }

        JsonObject sound = new JsonObject();
        JsonArray array = new JsonArray();
        JsonObject item = new JsonObject();

        item.addProperty("name", project.namespace + ":" + soundPath);
        item.addProperty("stream", false);
        array.add(item);
        sound.add("sounds", array);
        root.add(soundKey, sound);

        return root;
    }

    private static void writeModSoundsFragment(TitleStudioProject project, Path root) throws IOException {
        JsonObject sounds = createUsedSoundsJson(project);
        if (sounds.size() == 0) {
            return;
        }

        Files.writeString(root.resolve("SOUNDS_TO_MERGE.json"), GSON.toJson(sounds), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void writeFonts(TitleStudioProject project, Path namespaceAssets) throws IOException {

        String fontKey = localResourceKey(project.definition.font, project.namespace);

        if (fontKey == null) {
            return;
        }

        String importedPath = project.imported_fonts.get(fontKey);

        if (importedPath == null || importedPath.isBlank()) {

            return;
        }

        Path fontDir = namespaceAssets.resolve("font");
        Files.createDirectories(fontDir);
        JsonObject provider = new JsonObject();

        provider.addProperty("type", "ttf");
        provider.addProperty("file", project.namespace + ":" + fontKey + ".ttf");
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

        Files.writeString(fontDir.resolve(fontKey + ".json"), GSON.toJson(root), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void writePackReadme(Path root) throws IOException {

        String text = """
                Title Studio Export
                
                1. Put datapack.zip into your world's datapacks folder.
                2. Put resourcepack.zip into .minecraft/resourcepacks and enable it.
                3. Run /reload or re-enter the world. Use F3+T after resource changes if needed.
                
                Both packs are required when the title uses custom fonts or sounds.
                """;

        Files.writeString(root.resolve("README.txt"), text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void writeModReadme(Path root, String namespace) throws IOException {

        String text = """
                Title Studio - Mod Export
                
                1. Copy data/ and assets/ into your mod's src/main/resources/.
                2. If SOUNDS_TO_MERGE.json exists, merge its top-level entries into assets/%s/sounds.json.
                   If sounds.json does not exist yet, move the file there and rename it to sounds.json.
                3. Rebuild/reload your mod.
                """.formatted(namespace);

        Files.writeString(root.resolve("README.txt"), text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
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
