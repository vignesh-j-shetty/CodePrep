package com.codeprep.workspace;

import static org.junit.jupiter.api.Assertions.*;
import static com.codeprep.workspace.WorkspaceLayout.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceStoreTest {
    @TempDir Path temp;
    private final WorkspaceStore store = new WorkspaceStore();

    @Test void roundTripsDockTreeVisibilityFloatingBoundsTabsAndTheme() throws Exception {
        WorkspaceLayout defaults = WorkspaceLayout.defaults();
        Map<PanelId, PanelState> panels = new EnumMap<>(defaults.panels());
        panels.put(PanelId.PROBLEM, new PanelState(false, false, null));
        panels.put(PanelId.RESULTS, new PanelState(true, true, new WindowBounds(40, 60, 800, 600, true)));
        var first = new WorkspaceState.DocumentTab("Arrays/TwoSum", "JAVA");
        var second = new WorkspaceState.DocumentTab("Lists/Reverse", "PYTHON");
        WorkspaceState state = new WorkspaceState(1, new WorkspaceLayout(defaults.root(), panels, PanelId.EDITOR),
                List.of(first, second), second, second.problemId(), "light", 18);
        Path file = temp.resolve("custom").resolve("workspace.json");
        store.save(file, state);
        assertEquals(state, store.load(file));
        assertFalse(Files.readString(file).contains("\"group\""));
        try (var files = Files.list(file.getParent())) {
            assertEquals(List.of(file), files.toList());
        }
    }

    @Test void savesReplacementAndEmptyEditorSession() throws Exception {
        Path file = temp.resolve("workspace.json");
        var original = state(WorkspaceLayout.defaults());
        store.save(file, original);
        var replacement = new WorkspaceState(1, original.layout(), List.of(), null, null, "dark", 13);
        store.save(file, replacement);
        assertEquals(replacement, store.load(file));
    }

    @Test void reportsMalformedAndUnsupportedFilesWithoutOverwriting() throws Exception {
        Path file = temp.resolve("workspace.json");
        Files.writeString(file, "{broken");
        assertThrows(IOException.class, () -> store.load(file));
        assertEquals("{broken", Files.readString(file));
        store.save(file, state(WorkspaceLayout.defaults()));
        String invalidVersion = Files.readString(file).replace("\"version\" : 1", "\"version\" : 99");
        Files.writeString(file, invalidVersion);
        assertThrows(IOException.class, () -> store.load(file));
        assertEquals(invalidVersion, Files.readString(file));
        Files.writeString(file, "null");
        assertThrows(IOException.class, () -> store.load(file));
    }

    @Test void rejectsDuplicateMissingPanelsAndInvalidSplitters() {
        var panels = WorkspaceLayout.defaults().panels();
        assertThrows(IllegalArgumentException.class, () -> new WorkspaceLayout(
                DockNode.group(PanelId.EDITOR, PanelId.EDITOR), panels, null));
        assertThrows(IllegalArgumentException.class, () -> new WorkspaceLayout(
                DockNode.group(PanelId.EDITOR), panels, null));
        assertThrows(IllegalArgumentException.class, () -> new WorkspaceLayout(
                DockNode.split("HORIZONTAL", DockNode.group(PanelId.PROBLEMS, PanelId.PROBLEM),
                        DockNode.group(PanelId.EDITOR, PanelId.RESULTS), Double.NaN), panels, null));
        assertThrows(IllegalArgumentException.class, () -> new WorkspaceLayout(
                WorkspaceLayout.defaults().root(), Map.of(), null));
        assertThrows(IllegalArgumentException.class, () -> new WindowBounds(0, 0, 0, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new PanelState(true, true, null));
    }

    @Test void rejectsInvalidDocumentSelectionAndTheme() {
        var tab = new WorkspaceState.DocumentTab("problem", "JAVA");
        assertThrows(IllegalArgumentException.class, () -> new WorkspaceState(1, WorkspaceLayout.defaults(),
                List.of(), tab, null, "dark", 13));
        assertThrows(IllegalArgumentException.class, () -> new WorkspaceState(1, WorkspaceLayout.defaults(),
                List.of(tab, tab), tab, null, "dark", 13));
        assertThrows(IllegalArgumentException.class, () -> new WorkspaceState(1, WorkspaceLayout.defaults(),
                List.of(), null, null, "unknown", 13));
        assertThrows(IllegalArgumentException.class, () -> new WorkspaceState.DocumentTab("problem", "not a language"));
    }

    private WorkspaceState state(WorkspaceLayout layout) {
        return new WorkspaceState(1, layout, List.of(), null, null, "dark", 13);
    }
}
