package com.codeprep.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.*;

public final class WorkspaceStore {
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public static Path defaultFile() {
        return Path.of(System.getProperty("user.home"), ".codeprep", "workspace.json");
    }

    public WorkspaceState load(Path file) throws IOException {
        try {
            WorkspaceState state = mapper.readValue(file.toFile(), WorkspaceState.class);
            if (state == null) throw new IOException("Workspace file is empty: " + file);
            return state;
        } catch (IOException | IllegalArgumentException ex) {
            throw new IOException("Could not read workspace " + file + ". Repair or remove it; it was not reset.", ex);
        }
    }

    public void save(Path file, WorkspaceState state) throws IOException {
        Path target = file.toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "workspace-", ".tmp");
        try {
            mapper.writeValue(temporary.toFile(), state);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
