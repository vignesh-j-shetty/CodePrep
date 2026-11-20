package com.codeprep.workspace;

import java.util.*;

/** Serializable docking tree, including the last dock location of hidden/floating panels. */
public record WorkspaceLayout(DockNode root, Map<PanelId, PanelState> panels, PanelId maximized) {
    public record WindowBounds(double x, double y, double width, double height, boolean maximized) {
        public WindowBounds {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(width)
                    || !Double.isFinite(height) || width < 180 || height < 120) {
                throw new IllegalArgumentException("Invalid floating window bounds");
            }
        }
    }

    public record PanelState(boolean visible, boolean floating, WindowBounds bounds) {
        public PanelState {
            if (floating && bounds == null) throw new IllegalArgumentException("Floating panels require window bounds");
        }
    }

    public record DockNode(List<PanelId> tabs, PanelId selected, String axis,
                           DockNode first, DockNode second, double ratio) {
        public DockNode {
            if (tabs != null) tabs = List.copyOf(tabs);
        }
        public static DockNode group(PanelId... ids) {
            return new DockNode(List.of(ids), ids[0], null, null, null, 0.5);
        }
        public static DockNode split(String axis, DockNode first, DockNode second, double ratio) {
            return new DockNode(List.of(), null, axis, first, second, ratio);
        }
        @com.fasterxml.jackson.annotation.JsonIgnore
        public boolean isGroup() { return axis == null; }
    }

    public WorkspaceLayout {
        if (panels == null || !panels.keySet().equals(EnumSet.allOf(PanelId.class))
                || panels.values().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Workspace must describe all four panels");
        }
        panels = Map.copyOf(panels);
        Set<PanelId> found = EnumSet.noneOf(PanelId.class);
        validate(root, found, 0);
        if (!found.equals(EnumSet.allOf(PanelId.class))) {
            throw new IllegalArgumentException("Workspace must contain each panel exactly once");
        }
        if (maximized != null && !panels.get(maximized).visible()) {
            throw new IllegalArgumentException("A hidden panel cannot be maximized");
        }
    }

    private static void validate(DockNode node, Set<PanelId> found, int depth) {
        if (node == null || depth > 12 || node.tabs() == null) {
            throw new IllegalArgumentException("Invalid docking tree");
        }
        if (node.isGroup()) {
            if (node.tabs().isEmpty() || node.first() != null || node.second() != null
                    || !node.tabs().contains(node.selected())) {
                throw new IllegalArgumentException("Invalid docking tab group");
            }
            for (PanelId id : node.tabs()) {
                if (id == null || !found.add(id)) throw new IllegalArgumentException("Duplicate or unknown panel");
            }
        } else {
            if (!List.of("HORIZONTAL", "VERTICAL").contains(node.axis()) || !node.tabs().isEmpty()
                    || node.selected() != null || !Double.isFinite(node.ratio())
                    || node.ratio() <= 0 || node.ratio() >= 1) {
                throw new IllegalArgumentException("Invalid workspace splitter");
            }
            validate(node.first(), found, depth + 1);
            validate(node.second(), found, depth + 1);
        }
    }

    public static WorkspaceLayout defaults() {
        Map<PanelId, PanelState> states = new EnumMap<>(PanelId.class);
        for (PanelId id : PanelId.values()) states.put(id, new PanelState(true, false, null));
        return new WorkspaceLayout(DockNode.split("VERTICAL",
                DockNode.split("HORIZONTAL", DockNode.group(PanelId.PROBLEMS),
                        DockNode.split("HORIZONTAL", DockNode.group(PanelId.PROBLEM),
                                DockNode.group(PanelId.EDITOR), 0.46), 0.21),
                DockNode.group(PanelId.RESULTS), 0.73), states, null);
    }
}
