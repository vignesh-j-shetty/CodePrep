package com.codeprep.editor;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.codeprep.config.AppConfig;
import com.codeprep.controller.MainController;
import com.codeprep.editor.languages.JavaProvider;
import com.codeprep.executor.ExecutionManager;
import com.codeprep.model.Language;
import com.codeprep.service.DemoProblemInstaller;
import com.codeprep.workspace.*;
import com.codeprep.workspace.WorkspaceLayout.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import org.fxmisc.richtext.CodeArea;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LanguageEditorTest {
    private static boolean started;
    @TempDir Path temp;

    @BeforeAll static void startToolkit() throws Exception {
        assumeTrue(!System.getProperty("os.name").startsWith("Linux") || System.getenv("DISPLAY") != null,
                "JavaFX interaction tests require a display.");
        CountDownLatch ready = new CountDownLatch(1);
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            ready.countDown();
        });
        started = true;
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        // Load native WebKit before individual tests redirect settings to a temporary home.
        fx(() -> new javafx.scene.web.WebView());
    }

    @AfterAll static void stopToolkit() { if (started) Platform.exit(); }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(15, TimeUnit.SECONDS);
    }

    private static void press(CodeArea area, KeyCode key, boolean shift, boolean control) {
        area.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", key, shift, control, false, false));
    }

    @Test void switchesIndentationCommentsBracketsAndUndoHistoryAtRuntime() throws Exception {
        fx(() -> {
            List<String> errors = new ArrayList<>();
            try (var editor = new LanguageEditor(new JavaProvider(), errors::add)) {
                CodeArea area = (CodeArea) editor.lookup(".code-area");
                editor.replaceText("if (ok) {");
                area.moveTo(area.getLength());
                press(area, KeyCode.ENTER, false, false);
                assertEquals("if (ok) {\n    ", editor.getText());
                editor.setProvider(new TestLanguageProvider());
                editor.replaceText("if ok:");
                assertFalse(area.isUndoAvailable());
                area.moveTo(area.getLength());
                press(area, KeyCode.ENTER, false, false);
                assertEquals("if ok:\n  ", editor.getText());
                press(area, KeyCode.TAB, false, false);
                assertEquals("if ok:\n    ", editor.getText());
                editor.replaceText("value");
                area.moveTo(5);
                press(area, KeyCode.SLASH, false, true);
                assertEquals("# value", editor.getText());
                editor.replaceText("");
                area.fireEvent(new KeyEvent(KeyEvent.KEY_TYPED, "(", "", KeyCode.UNDEFINED, false, false, false, false));
                assertEquals("()", editor.getText());
                assertEquals(1, editor.getCaretPosition());
                press(area, KeyCode.BACK_SPACE, false, false);
                assertEquals("", editor.getText());
                assertTrue(errors.isEmpty());
            }
            return null;
        });
    }

    @Test void textSizeShortcutsScaleCodeAndLineNumbersWithinBounds() throws Exception {
        fx(() -> {
            Stage stage = new Stage();
            try (var editor = new LanguageEditor(new JavaProvider(), message -> fail(message))) {
                stage.setScene(new Scene(editor, 600, 400));
                stage.getScene().getStylesheets().add(LanguageEditor.class.getResource("/styles.css").toExternalForm());
                stage.show();
                CodeArea area = (CodeArea) editor.lookup(".code-area");
                editor.replaceText("int x;\nint y;");
                assertEquals(LanguageEditor.DEFAULT_FONT_SIZE, editor.getFontSize());
                press(area, KeyCode.EQUALS, false, true);
                assertEquals(14, editor.getFontSize());
                press(area, KeyCode.MINUS, false, true);
                press(area, KeyCode.MINUS, false, true);
                assertEquals(12, editor.getFontSize());
                press(area, KeyCode.DIGIT0, false, true);
                assertEquals(13, editor.getFontSize());
                editor.setFontSize(500);
                assertEquals(LanguageEditor.MAX_FONT_SIZE, editor.getFontSize());
                editor.zoomIn();
                assertEquals(LanguageEditor.MAX_FONT_SIZE, editor.getFontSize());
                editor.setFontSize(20);
                editor.applyCss();
                editor.layout();
                assertEquals(20, ((javafx.scene.text.Text) area.lookup(".text")).getFont().getSize(), 0.01);
                assertEquals(20, ((Label) area.lookup(".lineno")).getFont().getSize(), 0.01);
            } finally {
                stage.close();
            }
            return null;
        });
    }

    @Test void completionMenusAndFormattingUseTheCurrentProvider() throws Exception {
        fx(() -> {
            Stage stage = new Stage();
            try (var editor = new LanguageEditor(new JavaProvider(), message -> fail(message))) {
                stage.setScene(new Scene(editor, 600, 400));
                stage.show();
                CodeArea area = (CodeArea) editor.lookup(".code-area");
                editor.replaceText("sout");
                area.moveTo(4);
                press(area, KeyCode.SPACE, false, true);
                ContextMenu menu = javafx.stage.Window.getWindows().stream().filter(w -> w instanceof ContextMenu)
                        .map(w -> (ContextMenu) w).filter(ContextMenu::isShowing).findFirst().orElseThrow();
                menu.getItems().stream().filter(item -> item.getText().startsWith("sout")).findFirst().orElseThrow().fire();
                assertEquals("System.out.println();", editor.getText());
                assertEquals("System.out.println(".length(), editor.getCaretPosition());
                editor.setProvider(new TestLanguageProvider());
                editor.replaceText("pr");
                area.moveTo(2);
                press(area, KeyCode.SPACE, false, true);
                menu.getItems().get(0).fire();
                assertEquals("print()", editor.getText());
                assertEquals(6, editor.getCaretPosition());
                editor.replaceText("value  ");
                press(area, KeyCode.F, true, true);
                assertEquals("value", editor.getText());
            } finally {
                stage.close();
            }
            return null;
        });
    }

    @Test void missingFormatterIsReportedWithoutChangingTheDocument() throws Exception {
        fx(() -> {
            List<String> errors = new ArrayList<>();
            var provider = new TestLanguageProvider() {
                @Override public java.util.Optional<Formatter> formatter() { return java.util.Optional.empty(); }
            };
            try (var editor = new LanguageEditor(provider, errors::add)) {
                editor.replaceText("unchanged  ");
                press((CodeArea) editor.lookup(".code-area"), KeyCode.F, true, true);
                assertEquals("unchanged  ", editor.getText());
                assertEquals(List.of("No formatter registered for Test Python."), errors);
            }
            return null;
        });
    }

    @Test void changingProvidersDiscardsInFlightDiagnostics() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        CountDownLatch current = new CountDownLatch(1);
        var slow = new TestLanguageProvider() {
            @Override public java.util.Optional<DiagnosticProvider> diagnostics() {
                return java.util.Optional.of(source -> {
                    entered.countDown();
                    boolean done = false;
                    while (!done) {
                        try { done = release.await(10, TimeUnit.SECONDS); }
                        catch (InterruptedException ex) { /* Model a provider that cannot cancel an in-flight call. */ }
                    }
                    finished.countDown();
                    return List.of(new Diagnostic(0, 1, Severity.ERROR, "stale diagnostic"));
                });
            }
        };
        LanguageEditor editor = fx(() -> {
            var value = new LanguageEditor(slow, message -> fail(message));
            value.replaceText("old");
            return value;
        });
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            fx(() -> {
                editor.setProvider(new TestLanguageProvider() {
                    @Override public java.util.Optional<DiagnosticProvider> diagnostics() {
                        return java.util.Optional.of(source -> {
                            current.countDown();
                            return List.of();
                        });
                    }
                });
                editor.replaceText("new");
                return null;
            });
            release.countDown();
            assertTrue(finished.await(10, TimeUnit.SECONDS));
            assertTrue(current.await(10, TimeUnit.SECONDS));
            fx(() -> {
                assertTrue(editor.lookupAll(".editor-status").stream().map(n -> ((Label) n).getText())
                        .noneMatch(text -> text.contains("stale diagnostic")));
                return null;
            });
        } finally {
            release.countDown();
            fx(() -> { editor.close(); return null; });
        }
    }

    @Test void selectorKeepsDraftsAndFilesSeparateAndDisablesUnavailableExecution() throws Exception {
        Path library = temp.resolve("library");
        DemoProblemInstaller.install(library);
        Path javaFile = library.resolve("Arrays").resolve("TwoSum").resolve("solutions").resolve("java").resolve("Solution.java");
        String originalJava = Files.readString(javaFile);
        String oldHome = System.getProperty("user.home");
        System.setProperty("user.home", temp.toString());
        try {
            fx(() -> {
                Stage stage = new Stage();
                var registry = new LanguageProviderRegistry(List.of(new JavaProvider(), new TestLanguageProvider()));
                var controller = new MainController(new AppConfig(library, 5, Language.JAVA, "dark"),
                        stage, registry, ExecutionManager.discover());
                Parent root = (Parent) controller.createView();
                stage.setScene(new Scene(root));
                controller.initialize();
                root.applyCss();
                root.layout();
                var editor = (LanguageEditor) root.lookup(".language-editor");
                assertNotNull(editor);
                try {
                    ComboBox<?> selector = root.lookupAll(".combo-box").stream().map(n -> (ComboBox<?>) n)
                            .filter(c -> c.getValue() instanceof Language).findFirst().orElseThrow();
                    editor.replaceText("java draft");
                    // select(index) avoids a generic cast and exercises the real selector handler.
                    selector.getSelectionModel().select(1);
                    assertEquals(new TestLanguageProvider().starterCode(), editor.getText());
                    Button run = root.lookupAll(".button").stream().map(n -> (Button) n)
                            .filter(b -> b.getText().equals("Run")).findFirst().orElseThrow();
                    assertTrue(run.isDisabled());
                    editor.replaceText("python draft");
                    Button save = root.lookupAll(".button").stream().map(n -> (Button) n)
                            .filter(b -> b.getText().equals("Save")).findFirst().orElseThrow();
                    save.fire();
                    Path pythonFile = javaFile.getParent().getParent().resolve("python").resolve("solution.py");
                    assertEquals("python draft", Files.readString(pythonFile));
                    assertEquals(originalJava, Files.readString(javaFile));
                    List.copyOf(javafx.stage.Window.getWindows()).forEach(javafx.stage.Window::hide);
                    selector.getSelectionModel().select(0);
                    assertEquals("java draft", editor.getText());
                    assertFalse(run.isDisabled());
                    selector.getSelectionModel().select(1);
                    assertEquals("python draft", editor.getText());
                    assertEquals(new Language("PYTHON"), AppConfig.load().selectedLanguage());
                } finally {
                    editor.close();
                    stage.close();
                }
                return null;
            });
        } finally {
            System.setProperty("user.home", oldHome);
        }
    }

    @Test void textSizeSelectorSyncsWithEditorAndPersistsOnClose() throws Exception {
        Path library = temp.resolve("library");
        DemoProblemInstaller.install(library);
        String oldHome = System.getProperty("user.home");
        System.setProperty("user.home", temp.toString());
        try {
            fx(() -> {
                Stage stage = new Stage();
                var controller = new MainController(new AppConfig(library, 5, Language.JAVA, "dark", 15),
                        stage, new LanguageProviderRegistry(List.of(new JavaProvider())), ExecutionManager.discover());
                Parent root = (Parent) controller.createView();
                stage.setScene(new Scene(root, 1000, 700));
                controller.initialize();
                stage.show();
                root.applyCss();
                root.layout();
                var editor = (LanguageEditor) root.lookup(".language-editor");
                try {
                    @SuppressWarnings("unchecked")
                    ComboBox<Integer> sizes = (ComboBox<Integer>) root.lookup(".font-size-selector");
                    assertEquals(15, editor.getFontSize());
                    assertEquals(15, sizes.getValue());
                    sizes.setValue(18);
                    assertEquals(18, editor.getFontSize());
                    press((CodeArea) editor.lookup(".code-area"), KeyCode.EQUALS, false, true);
                    assertEquals(20, sizes.getValue());
                } finally {
                    stage.hide();
                }
                assertEquals(20, AppConfig.load().editorFontSize());
                assertEquals(Language.JAVA, AppConfig.load().selectedLanguage());
                return null;
            });
        } finally {
            System.setProperty("user.home", oldHome);
        }
    }

    @Test void panelsHideReopenFloatDockStackMaximizeAndRestoreWithoutLosingContent() throws Exception {
        fx(() -> {
            Stage stage = new Stage();
            Map<PanelId, javafx.scene.Node> panes = new EnumMap<>(PanelId.class);
            for (PanelId id : PanelId.values()) panes.put(id, new TextField(id.title()));
            WorkspaceManager manager = new WorkspaceManager(stage, panes);
            stage.setScene(new Scene(manager.view(), 1000, 700));
            stage.show();
            try {
                manager.hide(PanelId.PROBLEM);
                layout(manager.view());
                assertNull(panes.get(PanelId.PROBLEM).getScene());
                assertNotNull(panes.get(PanelId.EDITOR).getScene());
                manager.show(PanelId.PROBLEM);
                layout(manager.view());
                assertSame(stage.getScene(), panes.get(PanelId.PROBLEM).getScene());
                manager.floatPanel(PanelId.RESULTS, stage.getX() + 40, stage.getY() + 40);
                assertNotSame(stage.getScene(), panes.get(PanelId.RESULTS).getScene());
                Stage floating = (Stage) panes.get(PanelId.RESULTS).getScene().getWindow();
                floating.setWidth(640);
                var floatingState = manager.snapshot();
                assertEquals(640, floatingState.panels().get(PanelId.RESULTS).bounds().width(), 1);
                floating.fireEvent(new javafx.stage.WindowEvent(floating, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST));
                assertFalse(manager.snapshot().panels().get(PanelId.RESULTS).visible());
                manager.show(PanelId.RESULTS);
                assertTrue(manager.snapshot().panels().get(PanelId.RESULTS).floating());
                manager.dock(PanelId.RESULTS, PanelId.EDITOR, WorkspaceManager.DockSide.CENTER);
                layout(manager.view());
                // Assert using the model as well as the actual rendered tab group.
                assertFalse(manager.snapshot().panels().get(PanelId.RESULTS).floating());
                assertTrue(manager.view().lookupAll(".tab-pane").stream().filter(TabPane.class::isInstance)
                        .map(TabPane.class::cast).anyMatch(t -> t.getTabs().size() == 2));
                ((TextField) panes.get(PanelId.EDITOR)).setText("draft survives moving");
                var beforeMaximize = manager.snapshot();
                manager.maximize(PanelId.EDITOR);
                layout(manager.view());
                assertSame(stage.getScene(), panes.get(PanelId.EDITOR).getScene());
                assertNull(panes.get(PanelId.PROBLEMS).getScene());
                manager.maximize(PanelId.EDITOR);
                layout(manager.view());
                assertEquals(beforeMaximize, manager.snapshot());
                assertEquals("draft survives moving", ((TextField) panes.get(PanelId.EDITOR)).getText());
                for (PanelId id : PanelId.values()) manager.hide(id);
                assertTrue(manager.snapshot().panels().values().stream().noneMatch(PanelState::visible));
                manager.show(PanelId.EDITOR);
                layout(manager.view());
                assertSame(stage.getScene(), panes.get(PanelId.EDITOR).getScene());
                manager.restore(floatingState);
                assertNotSame(stage.getScene(), panes.get(PanelId.RESULTS).getScene());
            } finally {
                manager.close();
                stage.close();
            }
            return null;
        });
    }

    @Test void splitSizesTrackResizingAfterMultipleSnapshotsAndEveryDockSide() throws Exception {
        fx(() -> {
            Stage stage = new Stage();
            Map<PanelId, javafx.scene.Node> panes = new EnumMap<>(PanelId.class);
            for (PanelId id : PanelId.values()) panes.put(id, new Label(id.title()));
            WorkspaceManager manager = new WorkspaceManager(stage, panes);
            stage.setScene(new Scene(manager.view(), 1000, 700));
            stage.show();
            try {
                SplitPane rootSplit = (SplitPane) manager.view().getCenter();
                rootSplit.setDividerPositions(0.61);
                assertEquals(0.61, manager.snapshot().root().ratio(), 0.001);
                rootSplit.setDividerPositions(0.66);
                assertEquals(0.66, manager.snapshot().root().ratio(), 0.001);
                for (var side : WorkspaceManager.DockSide.values()) {
                    manager.dock(PanelId.RESULTS, PanelId.EDITOR, side);
                    WorkspaceLayout saved = manager.snapshot();
                    manager.restore(saved);
                    layout(manager.view());
                    assertDockTreeEquals(saved.root(), manager.snapshot().root());
                    assertEquals(saved.panels(), manager.snapshot().panels());
                    assertSame(stage.getScene(), panes.get(PanelId.RESULTS).getScene());
                }
                manager.floatPanel(PanelId.EDITOR, 50, 50);
                manager.maximize(PanelId.EDITOR);
                layout(manager.view());
                assertSame(stage.getScene(), panes.get(PanelId.EDITOR).getScene());
                assertNull(panes.get(PanelId.PROBLEMS).getScene());
                manager.maximize(PanelId.EDITOR);
                assertNotSame(stage.getScene(), panes.get(PanelId.EDITOR).getScene());
            } finally {
                manager.close();
                stage.close();
            }
            return null;
        });
    }

    @Test void workspaceRestoresTabsSelectionThemeAndClosedPanelsWhileFiltersPreserveDrafts() throws Exception {
        Path library = temp.resolve("library");
        DemoProblemInstaller.install(library);
        String oldHome = System.getProperty("user.home");
        System.setProperty("user.home", temp.toString());
        try {
            var javaTab = new WorkspaceState.DocumentTab("Arrays/TwoSum", "JAVA");
            var pythonTab = new WorkspaceState.DocumentTab("Arrays/TwoSum", "PYTHON");
            Map<PanelId, PanelState> panels = new EnumMap<>(WorkspaceLayout.defaults().panels());
            panels.put(PanelId.PROBLEM, new PanelState(false, false, null));
            new WorkspaceStore().save(WorkspaceStore.defaultFile(), new WorkspaceState(1,
                    new WorkspaceLayout(WorkspaceLayout.defaults().root(), panels, null),
                    List.of(javaTab, pythonTab), pythonTab, javaTab.problemId(), "light", 18));
            fx(() -> {
                Stage stage = new Stage();
                var registry = new LanguageProviderRegistry(List.of(new JavaProvider(), new TestLanguageProvider()));
                var controller = new MainController(new AppConfig(library, 5, Language.JAVA, "dark"),
                        stage, registry, ExecutionManager.discover());
                Parent root = (Parent) controller.createView();
                stage.setScene(new Scene(root, 1100, 750));
                stage.show();
                controller.initialize();
                root.applyCss();
                root.layout();
                try {
                    var tabs = (TabPane) root.lookup("#solution-tabs");
                    assertEquals(2, tabs.getTabs().size());
                    assertTrue(root.getStyleClass().contains("light"));
                    var editor = (LanguageEditor) root.lookup(".language-editor");
                    assertEquals(18, editor.getFontSize());
                    assertEquals(new TestLanguageProvider().starterCode(), editor.getText());
                    tabs.getSelectionModel().select(0);
                    assertEquals(Language.JAVA, AppConfig.load().selectedLanguage());
                    editor.replaceText("java tab draft");
                    tabs.getSelectionModel().select(1);
                    assertEquals(new TestLanguageProvider().starterCode(), editor.getText());
                    tabs.getSelectionModel().select(0);
                    assertEquals("java tab draft", editor.getText());
                    TextField search = (TextField) root.lookup(".search-field");
                    search.setText("no such problem");
                    assertEquals("java tab draft", editor.getText());
                    MenuBar bar = (MenuBar) root.lookup(".menu-bar");
                    CheckMenuItem problem = (CheckMenuItem) bar.getMenus().get(0).getItems().get(1);
                    assertFalse(problem.isSelected());
                    CheckMenuItem problems = (CheckMenuItem) bar.getMenus().get(0).getItems().get(0);
                    problems.setSelected(false);
                    problems.fire();
                    layout(root);
                    assertEquals("java tab draft", editor.getText());
                    assertNotNull(editor.getScene());
                    // Closing a panel must not be confused with closing its solution tabs.
                    assertEquals(2, tabs.getTabs().size());
                    // Accept the real discard confirmation: workspace files must not persist source drafts.
                    var confirmed = new java.util.concurrent.atomic.AtomicBoolean();
                    Platform.runLater(() -> {
                        for (var window : List.copyOf(javafx.stage.Window.getWindows())) {
                            if (window == stage || window.getScene() == null) continue;
                            if (window.getScene().getRoot() instanceof javafx.scene.control.DialogPane dialog
                                    && dialog.getHeaderText().equals("Close without saving?")) {
                                confirmed.set(true);
                                ((Button) dialog.lookupButton(javafx.scene.control.ButtonType.OK)).fire();
                            }
                        }
                    });
                    stage.fireEvent(new javafx.stage.WindowEvent(stage, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST));
                    assertTrue(confirmed.get());
                    var saved = new WorkspaceStore().load(WorkspaceStore.defaultFile());
                    assertEquals(2, saved.openTabs().size());
                    assertEquals(javaTab, saved.activeTab());
                    assertFalse(saved.layout().panels().get(PanelId.PROBLEM).visible());
                    assertFalse(saved.layout().panels().get(PanelId.PROBLEMS).visible());
                    assertEquals("light", saved.theme());
                } finally {
                    stage.close();
                }
                return null;
            });
        } finally {
            System.setProperty("user.home", oldHome);
        }
    }

    private static void layout(Parent root) {
        root.applyCss();
        root.layout();
    }

    @Test void closingAllSolutionsPersistsEmptySessionAndProblemsCanReopenThem() throws Exception {
        Path library = temp.resolve("library");
        DemoProblemInstaller.install(library);
        Path reverse = library.resolve("LinkedList").resolve("ReverseLinkedList");
        Files.createDirectories(reverse);
        for (String file : List.of("problem.md", "testcases.json")) {
            try (var source = getClass().getResourceAsStream("/demo-problems/LinkedList/ReverseLinkedList/" + file)) {
                assertNotNull(source);
                Files.copy(source, reverse.resolve(file));
            }
        }
        String oldHome = System.getProperty("user.home");
        System.setProperty("user.home", temp.toString());
        try {
            new WorkspaceStore().save(WorkspaceStore.defaultFile(), new WorkspaceState(1, WorkspaceLayout.defaults(),
                    List.of(), null, null, "dark", 13));
            fx(() -> {
                Stage stage = new Stage();
                var controller = new MainController(new AppConfig(library, 5, Language.JAVA, "dark"), stage);
                Parent root = (Parent) controller.createView();
                stage.setScene(new Scene(root, 1100, 750));
                stage.show();
                controller.initialize();
                layout(root);
                try {
                    var tabs = (TabPane) root.lookup("#solution-tabs");
                    assertTrue(tabs.getTabs().isEmpty());
                    tabs.setStyle("-fx-open-tab-animation: none; -fx-close-tab-animation: none;");
                    layout(root);
                    var list = (javafx.scene.control.ListView<?>) root.lookup("#problems-list");
                    list.getSelectionModel().select(0);
                    assertEquals(1, tabs.getTabs().size());
                    layout(root);
                    var editor = (LanguageEditor) root.lookup(".language-editor");
                    String original = editor.getText();
                    editor.replaceText("first problem draft");
                    list.getSelectionModel().select(1);
                    assertEquals(2, tabs.getTabs().size());
                    assertEquals(new JavaProvider().starterCode(), editor.getText());
                    tabs.getSelectionModel().select(0);
                    assertEquals("first problem draft", editor.getText());
                    editor.replaceText(original);
                    assertTrue(tabs.getTabs().stream().noneMatch(t -> t.getText().startsWith("*")),
                            tabs.getTabs().stream().map(javafx.scene.control.Tab::getText).toList().toString());
                    // Use the actual TabPane close buttons, not direct list removal.
                    for (int attempt = 0; attempt < 3 && !tabs.getTabs().isEmpty(); attempt++) {
                        layout(root);
                        var close = tabs.lookup(".tab-close-button");
                        assertNotNull(close);
                        click(close);
                    }
                    assertTrue(tabs.getTabs().isEmpty());
                    assertTrue(list.getSelectionModel().isEmpty());
                    list.getSelectionModel().select(0);
                    assertEquals(1, tabs.getTabs().size());
                    layout(root);
                    click(tabs.lookup(".tab-close-button"));
                    assertTrue(tabs.getTabs().isEmpty());
                    stage.fireEvent(new javafx.stage.WindowEvent(stage, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST));
                    var saved = new WorkspaceStore().load(WorkspaceStore.defaultFile());
                    assertTrue(saved.openTabs().isEmpty());
                    assertNull(saved.selectedProblem());
                    assertNull(saved.activeTab());
                } finally { stage.close(); }
                return null;
            });
        } finally { System.setProperty("user.home", oldHome); }
    }

    private static void assertDockTreeEquals(DockNode expected, DockNode actual) {
        assertEquals(expected.tabs(), actual.tabs());
        assertEquals(expected.selected(), actual.selected());
        assertEquals(expected.axis(), actual.axis());
        if (!expected.isGroup()) {
            assertEquals(expected.ratio(), actual.ratio(), 0.01);
            assertDockTreeEquals(expected.first(), actual.first());
            assertDockTreeEquals(expected.second(), actual.second());
        }
    }

    private static void click(javafx.scene.Node node) {
        node.fireEvent(new javafx.scene.input.MouseEvent(
                javafx.scene.input.MouseEvent.MOUSE_PRESSED, 1, 1, 1, 1,
                javafx.scene.input.MouseButton.PRIMARY, 1, false, false, false, false,
                true, false, false, false, false, true, null));
        node.fireEvent(new javafx.scene.input.MouseEvent(
                javafx.scene.input.MouseEvent.MOUSE_RELEASED, 1, 1, 1, 1,
                javafx.scene.input.MouseButton.PRIMARY, 1, false, false, false, false,
                false, false, false, false, false, true, null));
    }

    @Test void nativePanelDragDocksOverAnEditorControl() throws Exception {
        record DragFixture(Stage stage, WorkspaceManager manager, javafx.scene.robot.Robot robot,
                           javafx.geometry.Point2D from, javafx.geometry.Point2D to,
                           javafx.geometry.Point2D originalMouse, List<String> events) {}
        DragFixture fixture = fx(() -> {
            Stage stage = new Stage();
            Map<PanelId, javafx.scene.Node> panes = new EnumMap<>(PanelId.class);
            for (PanelId id : PanelId.values()) panes.put(id, new javafx.scene.control.TextArea(id.title()));
            WorkspaceManager manager = new WorkspaceManager(stage, panes);
            stage.setScene(new Scene(manager.view(), 1100, 750));
            List<String> events = new ArrayList<>();
            stage.getScene().addEventFilter(javafx.scene.input.MouseEvent.ANY, event -> {
                if (event.getEventType() == javafx.scene.input.MouseEvent.DRAG_DETECTED
                        || event.getEventType() == javafx.scene.input.MouseEvent.MOUSE_PRESSED) {
                    events.add(event.getEventType() + " " + event.getTarget());
                }
            });
            stage.getScene().addEventFilter(javafx.scene.input.DragEvent.ANY,
                    event -> events.add(event.getEventType() + " " + event.getTarget()));
            stage.setAlwaysOnTop(true);
            stage.setX(40);
            stage.setY(40);
            stage.show();
            stage.toFront();
            layout(manager.view());
            Label source = manager.view().lookupAll(".label").stream().filter(Label.class::isInstance)
                    .map(Label.class::cast).filter(label -> label.getText().equals("Problem Viewer")
                            && label.getOnDragDetected() != null).findFirst().orElseThrow();
            var fromBounds = source.localToScreen(source.getBoundsInLocal());
            var toBounds = panes.get(PanelId.EDITOR).localToScreen(panes.get(PanelId.EDITOR).getBoundsInLocal());
            var robot = new javafx.scene.robot.Robot();
            return new DragFixture(stage, manager, robot,
                    new javafx.geometry.Point2D(fromBounds.getCenterX(), fromBounds.getCenterY()),
                    new javafx.geometry.Point2D(toBounds.getCenterX(), toBounds.getCenterY()),
                    robot.getMousePosition(), events);
        });
        assumeTrue(!java.awt.GraphicsEnvironment.isHeadless(), "Native drag tests require a desktop.");
        // On Windows the FX thread blocks inside the native drag loop, so input must come from this thread.
        java.awt.Robot robot = new java.awt.Robot();
        try {
            nativeDrag(robot, fixture.from(), fixture.to());
            assumeTrue(fx(() -> fixture.events().stream().anyMatch(event -> event.startsWith("MOUSE_PRESSED"))),
                    "Native drag tests require an unlocked interactive desktop accepting Robot input.");
            fx(() -> {
                assertTrue(sharesGroup(fixture.manager().snapshot().root(), PanelId.PROBLEM, PanelId.EDITOR),
                        fixture.events() + "\n" + fixture.manager().snapshot());
                return null;
            });

            // Dragging a docked tab onto the left edge of the editor splits it out again.
            var points = fx(() -> {
                fixture.manager().restore(WorkspaceLayout.defaults());
                layout(fixture.manager().view());
                Label source = dragHandle(fixture.manager(), "Problems");
                var from = source.localToScreen(source.getBoundsInLocal());
                var editor = editorRegion(fixture.manager());
                var to = editor.localToScreen(editor.getBoundsInLocal());
                return List.of(new javafx.geometry.Point2D(from.getCenterX(), from.getCenterY()),
                        new javafx.geometry.Point2D(to.getMaxX() - 8, to.getCenterY()));
            });
            nativeDrag(robot, points.get(0), points.get(1));
            fx(() -> {
                DockNode root = fixture.manager().snapshot().root();
                assertTrue(!sharesGroup(root, PanelId.PROBLEMS, PanelId.EDITOR), root.toString());
                assertTrue(rightOf(root, PanelId.EDITOR, PanelId.PROBLEMS), root.toString());
                return null;
            });

            // Dropping a tab outside every CodePrep window detaches it into a floating window.
            fx(() -> {
                fixture.stage().setX(40);
                fixture.stage().setY(40);
                fixture.stage().setWidth(900);
                fixture.stage().setHeight(650);
                return null;
            });
            Thread.sleep(500);
            var floatPoints = fx(() -> {
                layout(fixture.manager().view());
                Label source = dragHandle(fixture.manager(), "Test Results");
                var from = source.localToScreen(source.getBoundsInLocal());
                var screen = javafx.stage.Screen.getPrimary().getVisualBounds();
                return List.of(new javafx.geometry.Point2D(from.getCenterX(), from.getCenterY()),
                        new javafx.geometry.Point2D(Math.min(screen.getMaxX() - 60, 1100), 300));
            });
            assumeTrue(floatPoints.get(1).getX() > 980, "Screen too small for the float drag test.");
            nativeDrag(robot, floatPoints.get(0), floatPoints.get(1));
            fx(() -> {
                assertTrue(fixture.manager().snapshot().panels().get(PanelId.RESULTS).floating(),
                        fixture.manager().snapshot().toString());
                return null;
            });
        } finally {
            robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
            Thread.sleep(200);
            fx(() -> {
                fixture.manager().close();
                fixture.stage().close();
                return null;
            });
            var original = fixture.originalMouse();
            robot.mouseMove((int) original.getX(), (int) original.getY());
        }
    }

    private static void nativeDrag(java.awt.Robot robot, javafx.geometry.Point2D from,
                                   javafx.geometry.Point2D to) throws Exception {
        robot.mouseMove((int) from.getX(), (int) from.getY());
        Thread.sleep(150);
        robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
        Thread.sleep(150);
        for (int step = 1; step <= 20; step++) {
            var point = from.add(to.subtract(from).multiply(step / 20.0));
            robot.mouseMove((int) point.getX(), (int) point.getY());
            Thread.sleep(40);
        }
        Thread.sleep(150);
        robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
        Thread.sleep(500);
        fx(() -> null);
    }

    private static Label dragHandle(WorkspaceManager manager, String title) {
        return manager.view().lookupAll(".label").stream().filter(Label.class::isInstance)
                .map(Label.class::cast).filter(label -> label.getText().equals(title)
                        && label.getOnDragDetected() != null).findFirst().orElseThrow();
    }

    private static javafx.scene.Node editorRegion(WorkspaceManager manager) {
        return manager.view().lookupAll(".dock-tabs").stream().map(javafx.scene.control.TabPane.class::cast)
                .filter(tabs -> tabs.getTabs().stream().anyMatch(tab -> tab.getUserData() == PanelId.EDITOR))
                .findFirst().orElseThrow().getSelectionModel().getSelectedItem().getContent();
    }

    private static boolean rightOf(DockNode node, PanelId left, PanelId right) {
        if (node.isGroup()) return false;
        if ("HORIZONTAL".equals(node.axis()) && contains(node.first(), left) && contains(node.second(), right)
                && !contains(node.first(), right)) return true;
        return rightOf(node.first(), left, right) || rightOf(node.second(), left, right);
    }

    private static boolean contains(DockNode node, PanelId id) {
        return node.isGroup() ? node.tabs().contains(id) : contains(node.first(), id) || contains(node.second(), id);
    }

    private static boolean sharesGroup(DockNode node, PanelId first, PanelId second) {
        return node.isGroup() ? node.tabs().containsAll(List.of(first, second))
                : sharesGroup(node.first(), first, second) || sharesGroup(node.second(), first, second);
    }
}
