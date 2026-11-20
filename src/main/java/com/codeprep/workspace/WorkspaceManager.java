package com.codeprep.workspace;

import static com.codeprep.workspace.WorkspaceLayout.*;

import java.util.*;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.Screen;
import javafx.stage.Stage;

/** Owns all panel nodes; moving/hiding a panel never disposes its document or control state. */
public final class WorkspaceManager implements AutoCloseable {
    public enum DockSide { LEFT, RIGHT, TOP, BOTTOM, CENTER }
    private static final DataFormat PANEL_FORMAT = new DataFormat("application/x-codeprep-panel");
    private static final String DROP_TARGET = "codeprep.dockTarget";
    private final Stage owner;
    private final BorderPane view = new BorderPane();
    private final Map<PanelId, Node> content = new EnumMap<>(PanelId.class);
    private final Map<PanelId, Stage> floating = new EnumMap<>(PanelId.class);
    private final Map<PanelId, CheckMenuItem> menuItems = new EnumMap<>(PanelId.class);
    private final Map<DockNode, SplitPane> splitters = new IdentityHashMap<>();
    private final Map<DockNode, TabPane> groups = new IdentityHashMap<>();
    private final List<Tab> renderedTabs = new ArrayList<>();
    private WorkspaceLayout layout = WorkspaceLayout.defaults();
    private String theme = "dark";
    private boolean rebuilding;
    private boolean droppedOnWorkspace;
    private final String dragToken = UUID.randomUUID().toString();

    public WorkspaceManager(Stage owner, Map<PanelId, Node> panes) {
        this.owner = owner;
        if (!panes.keySet().equals(EnumSet.allOf(PanelId.class))) {
            throw new IllegalArgumentException("Register all four workspace panels");
        }
        content.putAll(panes);
        view.getStyleClass().add("workspace");
        // The narrow frame remains a root docking target even when every panel is hidden.
        installDropTarget(view, null, false);
        rebuild();
    }

    public BorderPane view() { return view; }

    public Menu createViewMenu() {
        Menu menu = new Menu("View");
        for (PanelId id : PanelId.values()) {
            CheckMenuItem item = new CheckMenuItem(id.title());
            item.setSelected(layout.panels().get(id).visible());
            item.setOnAction(e -> { if (item.isSelected()) show(id); else hide(id); });
            menuItems.put(id, item);
            menu.getItems().add(item);
        }
        MenuItem editorOnly = new MenuItem("Editor Only / Restore Workspace");
        editorOnly.setAccelerator(new KeyCodeCombination(KeyCode.F12, KeyCombination.SHORTCUT_DOWN,
                KeyCombination.SHIFT_DOWN));
        editorOnly.setOnAction(e -> maximize(PanelId.EDITOR));
        CheckMenuItem fullScreen = new CheckMenuItem("Full Screen");
        fullScreen.setOnAction(e -> owner.setFullScreen(fullScreen.isSelected()));
        owner.fullScreenProperty().addListener((obs, old, value) -> fullScreen.setSelected(value));
        MenuItem restore = new MenuItem("Restore All Panels");
        restore.setOnAction(e -> {
            capture();
            Map<PanelId, PanelState> states = new EnumMap<>(layout.panels());
            states.replaceAll((id, state) -> new PanelState(true, state.floating(), state.bounds()));
            layout = new WorkspaceLayout(layout.root(), states, null);
            rebuild();
        });
        MenuItem reset = new MenuItem("Reset Default Layout");
        reset.setOnAction(e -> restore(WorkspaceLayout.defaults()));
        menu.getItems().addAll(new SeparatorMenuItem(), editorOnly, fullScreen, restore, reset);
        return menu;
    }

    public WorkspaceLayout snapshot() { capture(); return layout; }

    public void restore(WorkspaceLayout saved) {
        layout = Objects.requireNonNull(saved);
        rebuild();
    }

    public void setTheme(String theme) {
        this.theme = theme;
        applyTheme(view);
        floating.values().forEach(window -> applyTheme(window.getScene().getRoot()));
    }

    private void applyTheme(Node node) {
        node.getStyleClass().remove("light");
        if ("light".equals(theme)) node.getStyleClass().add("light");
    }

    public void show(PanelId id) {
        capture();
        PanelState old = layout.panels().get(id);
        setState(id, new PanelState(true, old.floating(), old.bounds()), null);
        rebuild();
        for (Tab tab : renderedTabs) {
            if (tab.getUserData() == id) tab.getTabPane().getSelectionModel().select(tab);
        }
        Stage window = floating.get(id);
        if (window != null) window.toFront();
    }

    public void hide(PanelId id) {
        capture();
        PanelState old = layout.panels().get(id);
        setState(id, new PanelState(false, old.floating(), old.bounds()), null);
        rebuild();
    }

    public void maximize(PanelId id) {
        capture();
        PanelState old = layout.panels().get(id);
        setState(id, new PanelState(true, old.floating(), old.bounds()),
                layout.maximized() == id ? null : id);
        rebuild();
    }

    public void floatPanel(PanelId id, double x, double y) {
        capture();
        setState(id, new PanelState(true, true, new WindowBounds(x, y, 760, 560, false)), null);
        rebuild();
    }

    public void dock(PanelId id, PanelId target, DockSide side) {
        capture();
        if (id == target) return;
        DockNode tree = remove(layout.root(), id);
        DockNode addition = DockNode.group(id);
        tree = insert(tree, target, addition, side);
        Map<PanelId, PanelState> states = new EnumMap<>(layout.panels());
        states.put(id, new PanelState(true, false, states.get(id).bounds()));
        if (target != null && states.get(target).floating()) {
            PanelState targetState = states.get(target);
            states.put(target, new PanelState(true, false, targetState.bounds()));
        }
        layout = new WorkspaceLayout(tree, states, null);
        rebuild();
    }

    private void setState(PanelId id, PanelState state, PanelId maximized) {
        Map<PanelId, PanelState> states = new EnumMap<>(layout.panels());
        states.put(id, state);
        layout = new WorkspaceLayout(layout.root(), states, maximized);
    }

    private static DockNode remove(DockNode node, PanelId id) {
        if (node.isGroup()) {
            List<PanelId> tabs = node.tabs().stream().filter(tab -> tab != id).toList();
            if (tabs.isEmpty()) return null;
            return new DockNode(tabs, tabs.contains(node.selected()) ? node.selected() : tabs.get(0),
                    null, null, null, 0.5);
        }
        DockNode first = remove(node.first(), id), second = remove(node.second(), id);
        if (first == null) return second;
        if (second == null) return first;
        return DockNode.split(node.axis(), first, second, node.ratio());
    }

    private static DockNode insert(DockNode node, PanelId target, DockNode addition, DockSide side) {
        if (node == null) return addition;
        if (target == null || node.isGroup() && node.tabs().contains(target)) {
            if (side == DockSide.CENTER) {
                if (!node.isGroup()) return DockNode.split("HORIZONTAL", node, addition, 0.7);
                List<PanelId> tabs = new ArrayList<>(node.tabs());
                tabs.addAll(addition.tabs());
                return new DockNode(tabs, addition.selected(), null, null, null, 0.5);
            }
            boolean before = side == DockSide.LEFT || side == DockSide.TOP;
            String axis = side == DockSide.LEFT || side == DockSide.RIGHT ? "HORIZONTAL" : "VERTICAL";
            return DockNode.split(axis, before ? addition : node, before ? node : addition, before ? 0.3 : 0.7);
        }
        if (node.isGroup()) return node;
        return DockNode.split(node.axis(), insert(node.first(), target, addition, side),
                insert(node.second(), target, addition, side), node.ratio());
    }

    private void capture() {
        if (rebuilding) return;
        Map<PanelId, PanelState> states = new EnumMap<>(layout.panels());
        floating.forEach((id, window) -> {
            PanelState old = states.get(id);
            if (!window.isMaximized()) {
                states.put(id, new PanelState(old.visible(), true, new WindowBounds(
                        window.getX(), window.getY(), window.getWidth(), window.getHeight(), false)));
            } else {
                WindowBounds bounds = old.bounds();
                states.put(id, new PanelState(old.visible(), true, new WindowBounds(
                        bounds.x(), bounds.y(), bounds.width(), bounds.height(), true)));
            }
        });
        layout = new WorkspaceLayout(capture(layout.root()), states, layout.maximized());
    }

    private DockNode capture(DockNode node) {
        if (node.isGroup()) {
            TabPane pane = groups.get(node);
            PanelId selected = pane == null || pane.getSelectionModel().getSelectedItem() == null
                    ? node.selected() : (PanelId) pane.getSelectionModel().getSelectedItem().getUserData();
            DockNode captured = new DockNode(node.tabs(), selected, null, null, null, 0.5);
            if (pane != null) { groups.remove(node); groups.put(captured, pane); }
            return captured;
        }
        SplitPane split = splitters.get(node);
        double ratio = split == null ? node.ratio() : split.getDividerPositions()[0];
        ratio = Math.max(0.02, Math.min(0.98, ratio));
        DockNode captured = DockNode.split(node.axis(), capture(node.first()), capture(node.second()), ratio);
        if (split != null) { splitters.remove(node); splitters.put(captured, split); }
        return captured;
    }

    private void rebuild() {
        rebuilding = true;
        try {
            renderedTabs.forEach(tab -> tab.setContent(null));
            renderedTabs.clear();
            view.setCenter(null);
            floating.values().forEach(window -> {
                window.setOnCloseRequest(null);
                window.close();
            });
            floating.clear();
            groups.clear();
            splitters.clear();
            if (layout.maximized() != null) {
                view.setCenter(buildGroup(List.of(layout.maximized()), layout.maximized(), null));
            } else {
                Node docked = build(layout.root());
                if (docked == null) {
                    Label empty = new Label("No docked panels. Reopen panels from View or drag a floating panel here.");
                    empty.getStyleClass().add("muted");
                    docked = new StackPane(empty);
                }
                view.setCenter(docked);
            }
            for (PanelId id : PanelId.values()) {
                PanelState state = layout.panels().get(id);
                if (state.visible() && state.floating() && layout.maximized() == null) buildFloating(id, state);
                CheckMenuItem item = menuItems.get(id);
                if (item != null) item.setSelected(state.visible());
            }
        } finally {
            rebuilding = false;
        }
    }

    private Node build(DockNode node) {
        if (node.isGroup()) {
            List<PanelId> visible = node.tabs().stream().filter(id -> {
                PanelState state = layout.panels().get(id);
                return state.visible() && !state.floating();
            }).toList();
            if (visible.isEmpty()) return null;
            return buildGroup(visible, node.selected(), node);
        }
        Node first = build(node.first()), second = build(node.second());
        if (first == null) return second;
        if (second == null) return first;
        SplitPane split = new SplitPane(first, second);
        split.setOrientation(Orientation.valueOf(node.axis()));
        split.setDividerPositions(node.ratio());
        split.setMinSize(0, 0);
        splitters.put(node, split);
        return split;
    }

    private Node buildGroup(List<PanelId> ids, PanelId selected, DockNode model) {
        TabPane pane = new TabPane();
        pane.setMinSize(0, 0);
        pane.getStyleClass().add("dock-tabs");
        pane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        for (PanelId id : ids) {
            Tab tab = new Tab();
            tab.setUserData(id);
            tab.setContent(content.get(id));
            tab.setOnCloseRequest(e -> { e.consume(); hide(id); });
            Label title = new Label(id.title());
            title.setTooltip(new Tooltip("Drag to dock or detach. Double-click to maximize/restore. Right-click for panel actions."));
            title.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) maximize(id);
            });
            title.setOnDragDetected(e -> {
                Dragboard board = title.startDragAndDrop(TransferMode.MOVE);
                ClipboardContent data = new ClipboardContent();
                data.put(PANEL_FORMAT, dragToken + ":" + id.name());
                board.setContent(data);
                droppedOnWorkspace = false;
                e.consume();
            });
            title.setOnDragDone(e -> {
                // Windows reports 0,0 for DRAG_DONE and may report foreign accept modes, so use our own state.
                var pointer = new javafx.scene.robot.Robot().getMousePosition();
                if (!droppedOnWorkspace && outsideWindows(pointer.getX(), pointer.getY())) {
                    floatPanel(id, pointer.getX() - 70, pointer.getY() - 20);
                }
                droppedOnWorkspace = false;
                e.consume();
            });
            MenuButton actions = new MenuButton("...");
            actions.getStyleClass().add("dock-actions");
            actions.getItems().setAll(panelActions(id));
            HBox header = new HBox(6, title, actions);
            header.setAlignment(Pos.CENTER_LEFT);
            tab.setGraphic(header);
            ContextMenu context = new ContextMenu();
            context.getItems().setAll(panelActions(id));
            tab.setContextMenu(context);
            pane.getTabs().add(tab);
            renderedTabs.add(tab);
            if (id == selected) pane.getSelectionModel().select(tab);
        }
        if (model != null) groups.put(model, pane);
        StackPane target = new StackPane(pane);
        target.setMinSize(0, 0);
        installDropTarget(target, ids.get(0), true);
        return target;
    }

    private List<MenuItem> panelActions(PanelId id) {
        MenuItem hide = new MenuItem("Hide");
        hide.setOnAction(e -> hide(id));
        MenuItem detach = new MenuItem("Float");
        detach.setOnAction(e -> floatPanel(id, owner.getX() + 100, owner.getY() + 100));
        MenuItem maximize = new MenuItem(layout.maximized() == id ? "Restore" : "Maximize");
        maximize.setOnAction(e -> maximize(id));
        Menu dock = new Menu("Dock to Workspace");
        for (DockSide side : DockSide.values()) {
            if (side == DockSide.CENTER) continue;
            MenuItem item = new MenuItem(side.name().charAt(0) + side.name().substring(1).toLowerCase(Locale.ROOT));
            item.setOnAction(e -> dock(id, null, side));
            dock.getItems().add(item);
        }
        return List.of(hide, detach, maximize, dock);
    }

    private boolean outsideWindows(double x, double y) {
        if (inside(owner, x, y)) return false;
        return floating.values().stream().noneMatch(window -> inside(window, x, y));
    }

    private boolean inside(Stage stage, double x, double y) {
        return x >= stage.getX() && x <= stage.getX() + stage.getWidth()
                && y >= stage.getY() && y <= stage.getY() + stage.getHeight();
    }

    private void installDropTarget(Region target, PanelId relative, boolean overlay) {
        if (overlay) target.getProperties().put(DROP_TARGET, true);
        Label hint = new Label();
        hint.getStyleClass().add("dock-drop-hint");
        hint.setMouseTransparent(true);
        hint.setVisible(false);
        hint.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        hint.setAlignment(Pos.CENTER);
        if (overlay) ((StackPane) target).getChildren().add(hint);
        // Capture panel drags before WebView/editor controls handle their own text/file drops.
        target.addEventFilter(DragEvent.DRAG_OVER, e -> {
            if (!overlay && isPanelTarget(e.getTarget())) return;
            PanelId dragged = draggedPanel(e.getDragboard());
            if (dragged == null) return;
            PanelId destination = dropRelative(target, relative, dragged);
            if (dragged != destination) {
                e.acceptTransferModes(TransferMode.MOVE);
                hint.setText("Dock " + sideAt(target, e).name().toLowerCase(Locale.ROOT));
                hint.setVisible(true);
            }
            e.consume();
        });
        target.setOnDragExited(e -> hint.setVisible(false));
        target.addEventFilter(DragEvent.DRAG_DROPPED, e -> {
            if (!overlay && isPanelTarget(e.getTarget())) return;
            PanelId dragged = draggedPanel(e.getDragboard());
            if (dragged == null) return;
            hint.setVisible(false);
            droppedOnWorkspace = true;
            DockSide side = sideAt(target, e);
            PanelId destination = dropRelative(target, relative, dragged);
            e.setDropCompleted(dragged != destination);
            e.consume();
            if (dragged != destination) dock(dragged, destination, side);
        });
    }

    private boolean isPanelTarget(javafx.event.EventTarget target) {
        if (target instanceof Node node) {
            for (Node ancestor = node; ancestor != null; ancestor = ancestor.getParent()) {
                if (ancestor.getProperties().containsKey(DROP_TARGET)) return true;
            }
        }
        return false;
    }

    private PanelId dropRelative(Region target, PanelId relative, PanelId dragged) {
        if (relative == dragged && target instanceof StackPane stack
                && stack.getChildren().get(0) instanceof TabPane tabs) {
            return tabs.getTabs().stream().map(tab -> (PanelId) tab.getUserData())
                    .filter(id -> id != dragged).findFirst().orElse(relative);
        }
        return relative;
    }

    private PanelId draggedPanel(Dragboard board) {
        Object value = board.getContent(PANEL_FORMAT);
        if (!(value instanceof String text) || !text.startsWith(dragToken + ":")) return null;
        return PanelId.valueOf(text.substring(dragToken.length() + 1));
    }

    private DockSide sideAt(Region target, DragEvent event) {
        var point = target.screenToLocal(event.getScreenX(), event.getScreenY());
        double x = point.getX() / Math.max(1, target.getWidth());
        double y = point.getY() / Math.max(1, target.getHeight());
        double edge = Math.min(Math.min(x, 1 - x), Math.min(y, 1 - y));
        if (edge > 0.24) return DockSide.CENTER;
        if (edge == x) return DockSide.LEFT;
        if (edge == 1 - x) return DockSide.RIGHT;
        if (edge == y) return DockSide.TOP;
        return DockSide.BOTTOM;
    }

    private void buildFloating(PanelId id, PanelState state) {
        Stage window = new Stage();
        window.initOwner(owner);
        window.setTitle(id.title() + " - CodePrep");
        BorderPane root = new BorderPane(buildGroup(List.of(id), id, null));
        root.getStyleClass().add("root");
        applyTheme(root);
        Scene scene = new Scene(root, 760, 560);
        scene.getStylesheets().add(WorkspaceManager.class.getResource("/styles.css").toExternalForm());
        window.setScene(scene);
        window.setMinWidth(240);
        window.setMinHeight(180);
        WindowBounds bounds = state.bounds();
        if (bounds != null) {
            var screens = Screen.getScreensForRectangle(bounds.x(), bounds.y(), bounds.width(), bounds.height());
            var screen = (screens.isEmpty() ? Screen.getPrimary() : screens.get(0)).getVisualBounds();
            double width = Math.min(bounds.width(), screen.getWidth());
            double height = Math.min(bounds.height(), screen.getHeight());
            window.setWidth(width);
            window.setHeight(height);
            window.setX(Math.max(screen.getMinX(), Math.min(bounds.x(), screen.getMaxX() - width)));
            window.setY(Math.max(screen.getMinY(), Math.min(bounds.y(), screen.getMaxY() - height)));
            window.setMaximized(bounds.maximized() || layout.maximized() == id);
        }
        window.setOnCloseRequest(e -> { e.consume(); hide(id); });
        floating.put(id, window);
        window.show();
    }

    @Override public void close() {
        renderedTabs.forEach(tab -> tab.setContent(null));
        floating.values().forEach(window -> {
            window.setOnCloseRequest(null);
            window.close();
        });
        floating.clear();
    }
}
