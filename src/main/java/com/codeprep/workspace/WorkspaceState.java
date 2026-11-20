package com.codeprep.workspace;

import java.util.List;

public record WorkspaceState(int version, WorkspaceLayout layout, List<DocumentTab> openTabs,
                             DocumentTab activeTab, String selectedProblem, String theme, int editorFontSize) {
    public record DocumentTab(String problemId, String language) {
        public DocumentTab {
            if (problemId == null || problemId.isBlank() || language == null || language.isBlank()) {
                throw new IllegalArgumentException("Invalid workspace document");
            }
            language = com.codeprep.model.Language.valueOf(language).name();
        }
    }

    public WorkspaceState {
        if (version != 1 || layout == null || openTabs == null || openTabs.stream().anyMatch(java.util.Objects::isNull)
                || openTabs.size() > 1000 || openTabs.stream().distinct().count() != openTabs.size()
                || activeTab != null && !openTabs.contains(activeTab)
                || !List.of("dark", "light").contains(theme) || editorFontSize < 10 || editorFontSize > 32) {
            throw new IllegalArgumentException("Unsupported or invalid workspace file");
        }
        openTabs = List.copyOf(openTabs);
    }
}
