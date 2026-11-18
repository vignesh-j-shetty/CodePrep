package com.codeprep.repository;

import com.codeprep.model.ProblemProgress;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ProgressRepository {
    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules().enable(SerializationFeature.INDENT_OUTPUT);
    public ProgressRepository(Path file) { this.file = file; }
    public synchronized Map<String, ProblemProgress> load() throws IOException {
        if (!Files.exists(file)) return new LinkedHashMap<>();
        return mapper.readValue(file.toFile(), new TypeReference<>() {});
    }
    public synchronized void save(Map<String, ProblemProgress> progress) throws IOException {
        Files.createDirectories(file.getParent());
        mapper.writeValue(file.toFile(), progress);
    }
}
