package com.benji.titlestudio.title.studio;

import com.benji.titlestudio.title.data.TitleDefinition;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class TitleStudioProject {

    public String workspace_id = UUID.randomUUID().toString();
    public String project_name = "New Title";
    public String namespace = "mytitles";
    public String title_path = "title_1";

    public TitleDefinition definition = new TitleDefinition();
    public Map<String, String> sounds = new LinkedHashMap<>();
    public Map<String, String> imported_fonts = new LinkedHashMap<>();

    public boolean preview_loop = true;

    public static TitleStudioProject createDefault() {
        TitleStudioProject project = new TitleStudioProject();
        project.project_name = "Desert Title";
        project.namespace = "mytitles";
        project.title_path = "desert";
        project.definition.text = "ANCIENT DESERT";
        project.definition.position.y = 0.16F;
        project.definition.position.scale = 2.2F;
        project.definition.style.color = "#F6D65A";
        project.definition.style.gradient.add("#FFE56B");
        project.definition.style.gradient.add("#F4B12A");
        project.definition.style.gradient.add("#DC7214");
        project.definition.style.outline.enabled = true;
        project.definition.style.outline.color = "#3B2115";
        project.definition.trigger.type = "biome";
        project.definition.trigger.target = "minecraft:desert";
        project.normalize();
        return project;
    }

    public void normalize() {
        if (workspace_id == null || workspace_id.isBlank()) workspace_id = UUID.randomUUID().toString();
        if (project_name == null || project_name.isBlank()) project_name = "Title Project";
        namespace = sanitizeNamespace(namespace);
        title_path = sanitizePath(title_path);
        if (definition == null) definition = new TitleDefinition();
        definition.normalize();
        if (sounds == null) sounds = new LinkedHashMap<>();
        if (imported_fonts == null) imported_fonts = new LinkedHashMap<>();
    }

    public String titleId() {
        normalize();
        return namespace + ":" + title_path;
    }

    public void rewriteNamespace(String oldNamespace, String newNamespace) {
        oldNamespace = sanitizeNamespace(oldNamespace);
        newNamespace = sanitizeNamespace(newNamespace);
        if (oldNamespace.equals(newNamespace)) return;

        String oldPrefix = oldNamespace + ":";
        String newPrefix = newNamespace + ":";

        if (definition != null) {
            definition.font = replacePrefix(definition.font, oldPrefix, newPrefix);
            if (definition.sound != null) {
                definition.sound.event = replacePrefix(definition.sound.event, oldPrefix, newPrefix);
            }
        }

        namespace = newNamespace;
    }

    private static String replacePrefix(String value, String oldPrefix, String newPrefix) {
        if (value == null) return null;
        return value.startsWith(oldPrefix) ? newPrefix + value.substring(oldPrefix.length()) : value;
    }

    public static String sanitizeNamespace(String value) {
        if (value == null) return "mytitles";
        value = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        return value.isBlank() ? "mytitles" : value;
    }

    public static String sanitizePath(String value) {
        if (value == null) return "title_1";
        value = value.toLowerCase(Locale.ROOT).replace('\\', '/').replaceAll("[^a-z0-9_./-]", "_");
        while (value.startsWith("/")) value = value.substring(1);
        return value.isBlank() ? "title_1" : value;
    }
}
