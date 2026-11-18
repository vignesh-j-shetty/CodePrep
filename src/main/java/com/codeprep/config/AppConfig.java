package com.codeprep.config;

import com.codeprep.model.Language;
import com.codeprep.service.DemoProblemInstaller;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record AppConfig(Path problemRoot, int executionTimeoutSeconds, Language selectedLanguage, String theme) {
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    public static Path configFile() {
        return Path.of(System.getProperty("user.home"), ".codeprep", "settings.json");
    }
    public static AppConfig defaults() {
        return new AppConfig(null, 5, Language.JAVA, "dark");
    }
    public static AppConfig load() throws IOException {
        Path file = configFile();
        if (!Files.isRegularFile(file)) return defaults();
        try {
            JsonNode data = MAPPER.readTree(file.toFile());
            Path root = data.hasNonNull("problemRoot") ? Path.of(data.get("problemRoot").asText()) : null;
            Language language = data.hasNonNull("selectedLanguage")
                    ? Language.valueOf(data.get("selectedLanguage").asText()) : Language.JAVA;
            return new AppConfig(root, Math.max(1, Math.min(60, data.path("executionTimeoutSeconds").asInt(5))),
                    language, data.path("theme").asText("dark"));
        } catch (IOException | RuntimeException ex) {
            throw new IOException("Could not read settings at " + file + ". Restore or repair this file; it was not reset.", ex);
        }
    }
    public static AppConfig initializeLibrary() throws IOException {
        boolean configured = Files.exists(configFile(), java.nio.file.LinkOption.NOFOLLOW_LINKS);
        AppConfig config = load();
        if (configured) {
            if (config.problemRoot() == null || !Files.isDirectory(config.problemRoot())) {
                throw new IOException("Your configured problem library is missing: " + config.problemRoot()
                        + ". Restore the library at that path or repair " + configFile() + ". No data was reset.");
            }
            return config;
        }
        Path root = configFile().getParent().resolve("problems");
        if (!Files.exists(root)) DemoProblemInstaller.install(root);
        if (!Files.isDirectory(root)) throw new IOException("Problem library is not a directory: " + root);
        config = new AppConfig(root, config.executionTimeoutSeconds(), config.selectedLanguage(), config.theme());
        config.save();
        return config;
    }
    public void save() throws IOException {
        Path file = configFile();
        Files.createDirectories(file.getParent());
        ObjectNode data = MAPPER.createObjectNode();
        if (problemRoot != null) data.put("problemRoot", problemRoot.toAbsolutePath().normalize().toString());
        data.put("executionTimeoutSeconds", executionTimeoutSeconds);
        data.put("selectedLanguage", selectedLanguage.name());
        data.put("theme", theme);
        MAPPER.writeValue(file.toFile(), data);
    }
}
