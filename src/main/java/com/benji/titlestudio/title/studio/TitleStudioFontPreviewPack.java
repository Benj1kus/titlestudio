package com.benji.titlestudio.title.studio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class TitleStudioFontPreviewPack {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<String, String> LAST_FINGERPRINTS = new HashMap<>();

    private static boolean reloadInProgress;

    private TitleStudioFontPreviewPack() {
    }

    public static void ensureLoaded(TitleStudioProject project) {
        if (project == null) {
            return;
        }

        project.normalize();

        boolean hasFonts = project.imported_fonts != null && !project.imported_fonts.isEmpty();
        boolean hasSounds = project.sounds != null && !project.sounds.isEmpty();

        if (!hasFonts && !hasSounds) {
            return;
        }


        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft == null) {
            return;
        }


        try {
            String fingerprint = fingerprint(project);
            String previous = LAST_FINGERPRINTS.get(project.workspace_id);

            Path packRoot = previewPackRoot(minecraft, project);
            String folderName = packRoot.getFileName().toString();

            PackRepository repository = minecraft.getResourcePackRepository();

            boolean selectedAlready = repository.getSelectedIds().stream().anyMatch(id -> matchesPreviewPackId(id, folderName));
            if (fingerprint.equals(previous) && selectedAlready) {

                return;
            }


            writePreviewPack(project, packRoot);
            repository.reload();
            String packId = repository.getAvailableIds().stream().filter(id -> matchesPreviewPackId(id, folderName)).findFirst().orElse(null);

            if (packId == null) {
                return;
            }


            if (!repository.getSelectedIds().contains(packId)) {

                repository.addPack(packId);
            }

            minecraft.options.updateResourcePacks(repository);
            LAST_FINGERPRINTS.put(project.workspace_id, fingerprint);

            if (!reloadInProgress) {
                reloadInProgress = true;


                minecraft.reloadResourcePacks().whenComplete((ignored, throwable) -> minecraft.execute(() -> reloadInProgress = false));
            }

        } catch (Exception ignored) {
        }
    }


    public static boolean isImportedFont(TitleStudioProject project, String fontId) {
        if (project == null || fontId == null || project.imported_fonts == null) {

            return false;
        }


        String prefix = project.namespace + ":";


        if (!fontId.startsWith(prefix)) {

            return false;
        }


        String path = fontId.substring(prefix.length());


        return project.imported_fonts.containsKey(path);
    }

    private static void writePreviewPack(TitleStudioProject project, Path packRoot) throws IOException {

        deleteTree(packRoot);

        Files.createDirectories(packRoot);

        writePackMcmeta(packRoot);

        writeFonts(project, packRoot);

        writeSounds(project, packRoot);
    }


    private static void writePackMcmeta(Path packRoot) throws IOException {

        JsonObject pack = new JsonObject();

        pack.addProperty("pack_format", 15);
        pack.addProperty("description", "Title Studio live preview assets");


        JsonObject wrapper = new JsonObject();

        wrapper.add("pack", pack);


        Files.writeString(packRoot.resolve("pack.mcmeta"), GSON.toJson(wrapper), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void writeFonts(TitleStudioProject project, Path packRoot) throws IOException {

        if (project.imported_fonts == null || project.imported_fonts.isEmpty()) {

            return;
        }

        Path fontDir = packRoot.resolve("assets").resolve(project.namespace).resolve("font");
        Files.createDirectories(fontDir);


        for (Map.Entry<String, String> entry : project.imported_fonts.entrySet()) {
            String fontPath = sanitizeResourcePath(entry.getKey());
            String assetPath = entry.getValue();


            if (assetPath == null || assetPath.isBlank()) {
                continue;
            }

            Path source = TitleStudioWorkspace.assetRoot(project).resolve(assetPath).normalize();

            if (!Files.isRegularFile(source)) {
                continue;
            }


            Path ttfTarget = fontDir.resolve(fontPath + ".ttf");
            Files.createDirectories(ttfTarget.getParent());
            Files.copy(source, ttfTarget, StandardCopyOption.REPLACE_EXISTING);
            JsonArray providers = new JsonArray();
            JsonObject ttf = new JsonObject();

            ttf.addProperty("type", "ttf");
            ttf.addProperty("file", project.namespace + ":" + fontPath + ".ttf");
            ttf.addProperty("size", 16.0F);
            ttf.addProperty("oversample", 4.0F);


            JsonArray shift = new JsonArray();

            shift.add(0.0F);
            shift.add(0.0F);

            ttf.add("shift", shift);


            providers.add(ttf);
            JsonObject fallback = new JsonObject();

            fallback.addProperty("type", "reference");
            fallback.addProperty("id", "minecraft:default");


            providers.add(fallback);


            JsonObject fontJson = new JsonObject();

            fontJson.add("providers", providers);

            Path jsonTarget = fontDir.resolve(fontPath + ".json");

            Files.createDirectories(jsonTarget.getParent());

            Files.writeString(jsonTarget, GSON.toJson(fontJson), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    private static void writeSounds(TitleStudioProject project, Path packRoot) throws IOException {

        if (project.sounds == null || project.sounds.isEmpty()) {

            return;
        }


        Path namespaceAssets = packRoot.resolve("assets").resolve(project.namespace);
        Path soundRoot = namespaceAssets.resolve("sounds");

        Files.createDirectories(soundRoot);


        JsonObject soundsJson = new JsonObject();


        for (Map.Entry<String, String> entry : project.sounds.entrySet()) {
            String eventKey = sanitizeResourcePath(entry.getKey());
            String soundPath = sanitizeResourcePath(entry.getValue());

            if (eventKey.isBlank() || soundPath.isBlank()) {

                continue;
            }

            Path source = TitleStudioWorkspace.assetRoot(project).resolve("sounds").resolve(soundPath + ".ogg").normalize();


            if (!Files.isRegularFile(source)) {

                continue;
            }

            Path target = soundRoot.resolve(soundPath + ".ogg");
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);

            JsonArray variants = new JsonArray();

            variants.add(project.namespace + ":" + soundPath);


            JsonObject event = new JsonObject();
            event.add("sounds", variants);


            soundsJson.add(eventKey, event);
        }


        if (soundsJson.size() <= 0) {
            return;
        }


        Files.writeString(namespaceAssets.resolve("sounds.json"), GSON.toJson(soundsJson), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }


    private static Path previewPackRoot(Minecraft minecraft, TitleStudioProject project) {
        String shortId = project.workspace_id != null ? project.workspace_id.replaceAll("[^a-zA-Z0-9_-]", "") : "project";


        if (shortId.length() > 12) {

            shortId = shortId.substring(0, 12);
        }

        String folder = "TitleStudioPreview_" + shortId;

        return minecraft.getResourcePackDirectory().resolve(folder);
    }


    private static boolean matchesPreviewPackId(String id, String folderName) {
        if (id == null) {
            return false;
        }


        String lower = id.toLowerCase(Locale.ROOT);
        String folder = folderName.toLowerCase(Locale.ROOT);


        return lower.equals("file/" + folder) || lower.endsWith("/" + folder) || lower.equals(folder) || lower.contains(folder);
    }

    private static String fingerprint(TitleStudioProject project) {
        StringBuilder builder = new StringBuilder(project.namespace).append('|');


        builder.append("fonts{");


        if (project.imported_fonts != null) {
            project.imported_fonts.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                Path file = TitleStudioWorkspace.assetRoot(project).resolve(entry.getValue());
                appendFingerprintFile(builder, entry.getKey() + "=" + entry.getValue(), file);
            });
        }


        builder.append("}|sounds{");

        if (project.sounds != null) {
            project.sounds.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                String soundPath = sanitizeResourcePath(entry.getValue());
                Path file = TitleStudioWorkspace.assetRoot(project).resolve("sounds").resolve(soundPath + ".ogg");
                appendFingerprintFile(builder, entry.getKey() + "=" + entry.getValue(), file);
            });
        }

        builder.append('}');

        return builder.toString();
    }


    private static void appendFingerprintFile(StringBuilder builder, String id, Path file) {
        builder.append(id).append('=');

        try {
            builder.append(Files.size(file)).append('@').append(Files.getLastModifiedTime(file).toMillis());

        } catch (Exception ignored) {
            builder.append("missing");
        }

        builder.append(';');
    }


    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {

            return;
        }

        try (var stream = Files.walk(root)) {

            for (Path path : stream.sorted(java.util.Comparator.reverseOrder()).toList()) {

                Files.deleteIfExists(path);
            }
        }
    }


    private static String sanitizeResourcePath(String value) {
        if (value == null) {
            return "title_asset";
        }

        value = value.toLowerCase(Locale.ROOT).replace(' ', '_').replaceAll("[^a-z0-9_./-]", "_");


        while (value.startsWith("/")) {

            value = value.substring(1);
        }


        return value.isBlank() ? "title_asset" : value;
    }
}
