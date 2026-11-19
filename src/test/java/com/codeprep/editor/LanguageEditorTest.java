package com.codeprep.editor;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.codeprep.config.AppConfig;
import com.codeprep.controller.MainController;
import com.codeprep.editor.languages.JavaProvider;
import com.codeprep.executor.ExecutionManager;
import com.codeprep.model.Language;
import com.codeprep.service.DemoProblemInstaller;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
}
