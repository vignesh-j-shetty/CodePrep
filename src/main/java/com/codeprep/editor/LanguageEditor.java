package com.codeprep.editor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Widget/lifecycle orchestration only; all language rules come from the provider. */
public final class LanguageEditor extends VBox implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(LanguageEditor.class);
    private final CodeArea area = new CodeArea();
    private final Label diagnosticStatus = new Label();
    private final ContextMenu completions = new ContextMenu();
    private final PauseTransition refresh = new PauseTransition(Duration.millis(250));
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "codeprep-diagnostics");
        thread.setDaemon(true);
        return thread;
    });
    private final Consumer<String> onError;
    private LanguageProvider provider;
    private List<LanguageProvider.Diagnostic> diagnostics = List.of();
    private Future<?> pending;
    private long revision;
    private boolean closed;
    private Runnable onSave = () -> {};
    private Runnable onRun = () -> {};
    private final IntegerProperty fontSize = new SimpleIntegerProperty(this, "fontSize", DEFAULT_FONT_SIZE);

    public static final int MIN_FONT_SIZE = 10;
    public static final int MAX_FONT_SIZE = 32;
    public static final int DEFAULT_FONT_SIZE = 13;
    public static final List<Integer> FONT_SIZE_PRESETS = List.of(10, 11, 12, 13, 14, 15, 16, 18, 20, 22, 24, 28, 32);

    public static int clampFontSize(int size) {
        return Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE, size));
    }

    public LanguageEditor(LanguageProvider provider, Consumer<String> onError) {
        getStyleClass().add("language-editor");
        this.provider = provider;
        this.onError = onError;
        StringBinding fontStyle = Bindings.createStringBinding(
                () -> "-fx-font-size: " + fontSize.get() + "px;", fontSize);
        area.styleProperty().bind(fontStyle);
        IntFunction<Node> lineNumbers = LineNumberFactory.get(area);
        area.setParagraphGraphicFactory(line -> {
            Node graphic = lineNumbers.apply(line);
            // LineNumberFactory assigns a fixed font; an inline style keeps the gutter in step with the text.
            graphic.styleProperty().bind(fontStyle);
            return graphic;
        });
        fontSize.addListener((obs, old, value) -> {
            int clamped = clampFontSize(value.intValue());
            if (clamped != value.intValue()) fontSize.set(clamped);
        });
        area.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (!event.isShortcutDown() || event.getDeltaY() == 0) return;
            if (event.getDeltaY() > 0) zoomIn(); else zoomOut();
            event.consume();
        });
        area.getStyleClass().add("code-area");
        var scroll = new VirtualizedScrollPane<>(area);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        diagnosticStatus.getStyleClass().add("editor-status");
        diagnosticStatus.setWrapText(true);
        diagnosticStatus.setOnMouseClicked(event -> {
            if (!diagnostics.isEmpty()) {
                area.moveTo(diagnostics.get(0).start());
                area.requestFollowCaret();
                area.requestFocus();
            }
        });
        getChildren().addAll(scroll, diagnosticStatus);
        area.textProperty().addListener((obs, old, text) -> {
            revision++;
            completions.hide();
            diagnostics = List.of();
            diagnosticStatus.setText("Diagnostics pending...");
            diagnosticStatus.setTooltip(null);
            if (pending != null) pending.cancel(true);
            refresh.playFromStart();
        });
        area.caretPositionProperty().addListener((obs, old, value) -> completions.hide());
        refresh.setOnFinished(event -> refreshServices());
        area.addEventFilter(KeyEvent.KEY_PRESSED, this::keyPressed);
        area.addEventFilter(KeyEvent.KEY_TYPED, this::keyTyped);
        ContextMenu menu = new ContextMenu();
        menu.getItems().addAll(action("Complete / snippets (Ctrl+Space)", this::showCompletions),
                action("Format document (Ctrl+Shift+F)", this::format),
                action("Toggle comment (Ctrl+/)", this::toggleComment), new SeparatorMenuItem(),
                action("Increase text size (Ctrl+=)", this::zoomIn), action("Decrease text size (Ctrl+-)", this::zoomOut),
                action("Reset text size (Ctrl+0)", this::resetZoom), new SeparatorMenuItem(),
                action("Undo", area::undo), action("Redo", area::redo),
                action("Cut", area::cut), action("Copy", area::copy), action("Paste", area::paste));
        area.setContextMenu(menu);
        refresh.playFromStart();
    }

    public void setProvider(LanguageProvider value) {
        provider = value;
        revision++;
        if (pending != null) pending.cancel(true);
        completions.hide();
        diagnostics = List.of();
        diagnosticStatus.setText("Diagnostics pending...");
        diagnosticStatus.setTooltip(null);
        area.clearStyle(0, area.getLength());
        refresh.playFromStart();
    }

    public IntegerProperty fontSizeProperty() { return fontSize; }
    public int getFontSize() { return fontSize.get(); }
    public void setFontSize(int size) { fontSize.set(clampFontSize(size)); }
    public void zoomIn() {
        setFontSize(FONT_SIZE_PRESETS.stream().filter(s -> s > getFontSize()).findFirst().orElse(MAX_FONT_SIZE));
    }
    public void zoomOut() {
        setFontSize(FONT_SIZE_PRESETS.stream().filter(s -> s < getFontSize()).reduce((a, b) -> b).orElse(MIN_FONT_SIZE));
    }
    public void resetZoom() { setFontSize(DEFAULT_FONT_SIZE); }

    public void setOnSave(Runnable action) { onSave = action; }
    public void setOnRun(Runnable action) { onRun = action; }
    public String getText() { return area.getText(); }
    public void replaceText(String text) {
        area.replaceText(text);
        area.getUndoManager().forgetHistory();
        area.moveTo(0);
    }
    public void clear() { replaceText(""); }
    public ObservableValue<String> textProperty() { return area.textProperty(); }
    public ObservableValue<Integer> caretPositionProperty() { return area.caretPositionProperty(); }
    public int getCaretPosition() { return area.getCaretPosition(); }
    public int getCurrentParagraph() { return area.getCurrentParagraph(); }

    private MenuItem action(String title, Runnable command) {
        MenuItem item = new MenuItem(title);
        item.setOnAction(event -> command.run());
        return item;
    }

    private void keyPressed(KeyEvent event) {
        if (event.isShortcutDown() && event.getCode() == KeyCode.S) onSave.run();
        else if (event.isShortcutDown() && event.getCode() == KeyCode.ENTER) onRun.run();
        else if (event.isControlDown() && event.getCode() == KeyCode.SPACE) showCompletions();
        else if (event.isShortcutDown() && event.isShiftDown() && event.getCode() == KeyCode.F) format();
        else if (event.isShortcutDown() && event.getCode() == KeyCode.SLASH) toggleComment();
        else if (event.isShortcutDown() && (event.getCode() == KeyCode.EQUALS || event.getCode() == KeyCode.PLUS
                || event.getCode() == KeyCode.ADD)) zoomIn();
        else if (event.isShortcutDown() && (event.getCode() == KeyCode.MINUS || event.getCode() == KeyCode.SUBTRACT)) zoomOut();
        else if (event.isShortcutDown() && (event.getCode() == KeyCode.DIGIT0 || event.getCode() == KeyCode.NUMPAD0)) resetZoom();
        else if (!event.isControlDown() && !event.isAltDown() && !event.isMetaDown()) {
            if (event.getCode() == KeyCode.TAB) {
                apply(EditorIndentation.tab(getText(), area.getAnchor(), getCaretPosition(), event.isShiftDown(), provider.indentation()));
            } else if (event.getCode() == KeyCode.ENTER && !event.isShiftDown()) {
                int caret = area.getSelection().getStart();
                String newline = provider.newline(getText(), caret);
                boolean betweenPair = area.getSelectedText().isEmpty() && betweenPair(caret)
                        && provider.autoPairAllowed(getText(), caret);
                if (betweenPair) {
                    int lineStart = getText().lastIndexOf('\n', caret - 1) + 1;
                    String line = getText().substring(lineStart, caret);
                    String indent = line.substring(0, line.length() - line.stripLeading().length());
                    area.replaceSelection(newline + "\n" + indent);
                    area.moveTo(caret + newline.length());
                } else area.replaceSelection(newline);
            } else if (event.getCode() == KeyCode.BACK_SPACE && area.getSelectedText().isEmpty()
                    && betweenPair(getCaretPosition()) && provider.autoPairAllowed(getText(), getCaretPosition())) {
                int caret = getCaretPosition();
                area.replaceText(caret - 1, caret + 1, "");
            } else return;
        } else return;
        event.consume();
    }

    private boolean betweenPair(int caret) {
        if (caret == 0 || caret >= getText().length()) return false;
        return provider.bracketPairs().stream().anyMatch(pair ->
                getText().charAt(caret - 1) == pair.open() && getText().charAt(caret) == pair.close());
    }

    private void keyTyped(KeyEvent event) {
        if (event.isControlDown() || event.isAltDown() || event.isMetaDown() || event.getCharacter().length() != 1) return;
        char ch = event.getCharacter().charAt(0);
        int caret = area.getSelection().getStart();
        if (!provider.autoPairAllowed(getText(), caret)) return;
        for (var pair : provider.bracketPairs()) {
            if (ch == pair.close() && area.getSelectedText().isEmpty()
                    && caret < getText().length() && getText().charAt(caret) == ch) {
                area.moveTo(caret + 1);
                event.consume();
                return;
            }
            if (ch == pair.open()) {
                String selected = area.getSelectedText();
                area.replaceSelection("" + pair.open() + selected + pair.close());
                area.selectRange(caret + 1, caret + 1 + selected.length());
                event.consume();
                return;
            }
        }
    }

    private void apply(EditorIndentation.Edit edit) {
        area.replaceText(edit.start(), edit.end(), edit.replacement());
        area.selectRange(edit.anchor(), edit.caret());
    }

    private void toggleComment() {
        try {
            apply(EditorOperations.toggleComment(getText(), area.getAnchor(), getCaretPosition(), provider.comments()));
        } catch (UnsupportedOperationException ex) {
            onError.accept(ex.getMessage());
        }
    }

    private void format() {
        try {
            var formatter = provider.formatter();
            if (formatter.isEmpty()) {
                onError.accept("No formatter registered for " + provider.displayName() + ".");
                return;
            }
            int caret = getCaretPosition();
            area.replaceText(formatter.get().format(getText()));
            area.moveTo(Math.min(caret, area.getLength()));
        } catch (RuntimeException ex) {
            LOG.error("Formatting failed for {}", provider.language(), ex);
            onError.accept("Formatting failed: " + ex.getMessage());
        }
    }

    private void showCompletions() {
        completions.hide();
        completions.getItems().clear();
        List<LanguageProvider.Completion> choices;
        try {
            choices = provider.complete(getText(), getCaretPosition());
        } catch (RuntimeException ex) {
            LOG.error("Completion failed for {}", provider.language(), ex);
            onError.accept("Completion failed: " + ex.getMessage());
            return;
        }
        if (choices.isEmpty()) {
            diagnosticStatus.setText("No completions at this position.");
            return;
        }
        choices.stream().limit(30).forEach(choice -> completions.getItems().add(action(choice.label(), () -> {
            area.replaceText(choice.start(), choice.end(), choice.text());
            area.moveTo(choice.start() + choice.caretOffset());
            area.requestFocus();
        })));
        area.getCaretBounds().ifPresentOrElse(bounds -> completions.show(area, bounds.getMinX(), bounds.getMaxY()),
                () -> completions.show(area, Side.BOTTOM, 0, 0));
    }

    private void refreshServices() {
        if (closed) return;
        paint();
        long version = revision;
        String source = getText();
        LanguageProvider active = provider;
        var diagnosticProvider = active.diagnostics();
        if (diagnosticProvider.isEmpty()) {
            diagnosticStatus.setText("No diagnostic provider registered for " + active.displayName());
            diagnosticStatus.setTooltip(null);
            return;
        }
        pending = worker.submit(() -> {
            try {
                var result = List.copyOf(diagnosticProvider.get().diagnose(source));
                Platform.runLater(() -> {
                    if (closed || version != revision) return;
                    diagnostics = result;
                    diagnosticStatus.setText(result.isEmpty() ? "No syntax diagnostics" : result.size() + " diagnostic(s): " + result.get(0).message());
                    diagnosticStatus.setTooltip(result.isEmpty() ? null : new Tooltip(result.stream().limit(40)
                            .map(d -> d.severity() + ": " + d.message()).collect(java.util.stream.Collectors.joining("\n"))));
                    paint();
                });
            } catch (RuntimeException ex) {
                LOG.error("Diagnostics failed for {}", active.language(), ex);
                Platform.runLater(() -> {
                    if (!closed && version == revision) {
                        diagnosticStatus.setText("Diagnostics unavailable: " + ex.getMessage());
                        diagnosticStatus.setTooltip(null);
                    }
                });
            }
        });
    }

    private void paint() {
        if (area.getLength() == 0) return;
        var tokens = provider.highlight(getText());
        var boundaries = new java.util.TreeSet<Integer>();
        boundaries.add(0);
        boundaries.add(area.getLength());
        tokens.forEach(t -> { boundaries.add(t.start()); boundaries.add(t.end()); });
        diagnostics.forEach(d -> {
            boundaries.add(Math.min(area.getLength(), d.start()));
            boundaries.add(Math.min(area.getLength(), Math.max(d.start() + 1, d.end())));
        });
        var spans = new StyleSpansBuilder<Collection<String>>();
        var sorted = new ArrayList<>(boundaries);
        int tokenIndex = 0;
        List<LanguageProvider.Token> ordered = tokens.stream().sorted(Comparator.comparingInt(LanguageProvider.Token::start)).toList();
        for (int i = 0; i < sorted.size() - 1; i++) {
            int start = sorted.get(i), end = sorted.get(i + 1);
            List<String> styles = new ArrayList<>();
            while (tokenIndex < ordered.size() && ordered.get(tokenIndex).end() <= start) tokenIndex++;
            if (tokenIndex < ordered.size() && ordered.get(tokenIndex).start() <= start) styles.add(ordered.get(tokenIndex).style());
            diagnostics.stream().filter(d -> d.start() < end && Math.max(d.start() + 1, d.end()) > start)
                    .forEach(d -> styles.add("diagnostic-" + d.severity().name().toLowerCase(java.util.Locale.ROOT)));
            spans.add(styles, end - start);
        }
        area.setStyleSpans(0, spans.create());
    }

    @Override public void close() {
        closed = true;
        refresh.stop();
        completions.hide();
        if (pending != null) pending.cancel(true);
        worker.shutdownNow();
    }
}
