package com.codeprep.controller;

import com.codeprep.config.AppConfig;
import com.codeprep.dto.TestCaseDisplayResult;
import com.codeprep.editor.LanguageEditor;
import com.codeprep.editor.LanguageProvider;
import com.codeprep.editor.LanguageProviderRegistry;
import com.codeprep.editor.SolutionDocuments;
import com.codeprep.executor.ExecutionManager;
import com.codeprep.model.*;
import com.codeprep.repository.ProblemRepository;
import com.codeprep.repository.ProgressRepository;
import com.codeprep.service.*;
import com.codeprep.service.ProblemMarkdownParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.Parent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

public final class MainController {
    private final Stage stage;
    private AppConfig config;
    private final ProblemService problemService;
    private final ProblemImportService importService;
    private ProgressService progressService;
    private final LanguageProviderRegistry languages;
    private final ExecutionManager executionManager;
    private final TestCaseService testCaseService = new TestCaseService();
    private final List<Problem> allProblems = new ArrayList<>();
    private final Map<String, String> problemSearchIndex = new HashMap<>();
    private final SolutionDocuments documents = new SolutionDocuments();
    private final ObjectMapper mapper = new ObjectMapper();
    private Problem selected;
    private boolean changingEditor;
    private boolean changingLanguage;
    private ListView<Problem> problemList;
    private LanguageEditor editor;
    private WebView description;
    private TextField search;
    private ComboBox<String> difficultyFilter;
    private Label counts;
    private Label selectedTitle;
    private Label busyLabel;
    private Label caretPosition;
    private ProgressIndicator busyIndicator;
    private Button runButton;
    private Button runAllButton;
    private Button saveButton;
    private Button resetButton;
    private Button randomButton;
    private Button settingsButton;
    private Button importButton;
    private ComboBox<Language> languageSelector;
    private ComboBox<Integer> fontSizeSelector;
    private final javafx.animation.PauseTransition fontSizeSave = new javafx.animation.PauseTransition(javafx.util.Duration.millis(400));
    private boolean busy;
    private TabPane outputTabs;
    private TextArea testCasesText;
    private TextArea resultsText;
    private TextArea errorsText;
    private TextArea outputText;
    private final Parser markdownParser = Parser.builder().build();
    private final HtmlRenderer markdownRenderer = HtmlRenderer.builder().build();

    public MainController(AppConfig config, Stage stage) throws IOException {
        this(config, stage, LanguageProviderRegistry.discover(), ExecutionManager.discover());
    }

    public MainController(AppConfig config, Stage stage, LanguageProviderRegistry languages,
                          ExecutionManager executionManager) throws IOException {
        this.config = config;
        this.stage = stage;
        this.languages = languages;
        this.executionManager = executionManager;
        try {
            languages.require(config.selectedLanguage());
        } catch (IllegalArgumentException ex) {
            throw new IOException(ex.getMessage() + ". Install its language module or choose an installed language in settings.", ex);
        }
        ProblemRepository repository = new ProblemRepository(new ProblemMarkdownParser(), new TestCaseParser(mapper));
        this.problemService = new ProblemService(repository);
        this.importService = new ProblemImportService(repository, new ProblemFingerprintService(), languages);
        Path progressPath = config.problemRoot().resolve("progress.json");
        this.progressService = new ProgressService(new ProgressRepository(progressPath));
    }

    public Node createView() {
        stage.setOnCloseRequest(event -> {
            if (busy) {
                event.consume();
                showInfo("Operation in progress", "Wait for import or execution to finish before closing CodePrep.");
            } else if (hasUnsavedChanges() && !confirmClose()) event.consume();
        });
        stage.setOnHidden(event -> {
            if (fontSizeSave.getStatus() == javafx.animation.Animation.Status.RUNNING) {
                fontSizeSave.stop();
                persistEditorFontSize();
            }
            editor.close();
        });
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");
        root.setTop(buildToolbar());
        problemList = new ListView<>();
        problemList.setCellFactory(view -> new ProblemCell());
        problemList.setPlaceholder(new Label("No matching problems"));
        problemList.setPrefWidth(270);
        problemList.setMinWidth(220);
        VBox left = new VBox(10, buildProblemListHeader(), problemList);
        left.getStyleClass().add("side-panel");
        left.setPadding(new Insets(16));
        VBox.setVgrow(problemList, Priority.ALWAYS);

        description = new WebView();
        description.setContextMenuEnabled(false);
        StackPane descriptionPane = new StackPane(description);
        descriptionPane.getStyleClass().add("description-pane");
        VBox editorPane = buildEditorPane();
        SplitPane center = new SplitPane(descriptionPane, editorPane);
        center.setDividerPositions(0.47);
        descriptionPane.setMinWidth(220);
        editorPane.setMinWidth(300);
        center.getStyleClass().add("main-split");
        SplitPane main = new SplitPane(left, center);
        main.setDividerPositions(0.21);
        SplitPane workspace = new SplitPane(main, buildResultsPane());
        workspace.setOrientation(Orientation.VERTICAL);
        workspace.setDividerPositions(0.73);
        root.setCenter(workspace);

        problemList.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> {
            if (value == null) {
                clearSelection();
            } else if (!value.equals(selected)) {
                selectProblem(value);
            }
        });
        search.textProperty().addListener((obs, old, value) -> filterProblems());
        difficultyFilter.valueProperty().addListener((obs, old, value) -> filterProblems());
        return root;
    }

    private Node buildToolbar() {
        Label logo = new Label("CodePrep");
        logo.getStyleClass().add("logo");
        search = new TextField();
        search.setPromptText("Search title or topic");
        search.getStyleClass().add("search-field");
        search.setPrefWidth(280);
        languageSelector = new ComboBox<>();
        languageSelector.getItems().setAll(languages.providers().stream().map(LanguageProvider::language).toList());
        languageSelector.setConverter(new StringConverter<>() {
            @Override public String toString(Language language) {
                return language == null ? "" : languages.require(language).displayName();
            }
            @Override public Language fromString(String value) { throw new UnsupportedOperationException(); }
        });
        languageSelector.setValue(config.selectedLanguage());
        languageSelector.setOnAction(e -> {
            if (!changingLanguage) switchLanguage(languageSelector.getValue());
        });
        Button random = new Button("Random Problem");
        random.setTooltip(new Tooltip("Choose an unsolved problem, ignoring the current filters"));
        randomButton = random;
        random.setOnAction(e -> selectRandom());
        Button settings = new Button("Settings");
        settings.setTooltip(new Tooltip("Configure the default test timeout"));
        settingsButton = settings;
        settings.setOnAction(e -> showSettings());
        importButton = new Button("Import Questions");
        importButton.setTooltip(new Tooltip("Add questions from a folder"));
        importButton.setOnAction(e -> chooseImport());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(14, logo, spacer, search, languageSelector, random, importButton, settings);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(12, 18, 12, 18));
        bar.getStyleClass().add("toolbar");
        return bar;
    }

    private boolean hasUnsavedChanges() {
        try {
            for (var entry : documents.drafts().entrySet()) {
                Problem problem = allProblems.stream().filter(p -> p.id().equals(entry.getKey().problemId())).findFirst().orElseThrow();
                LanguageProvider provider = languages.require(entry.getKey().language());
                Path file = SolutionDocuments.file(problem.directory(), provider);
                String saved = Files.isRegularFile(file) ? Files.readString(file)
                        : documents.initial().getOrDefault(entry.getKey(), provider.starterCode());
                if (!entry.getValue().equals(saved)) return true;
            }
            return false;
        } catch (IOException ex) {
            return true;
        }
    }

    private boolean confirmClose() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "You have unsaved changes. Close CodePrep and discard them?", ButtonType.CANCEL, ButtonType.OK);
        alert.initOwner(stage);
        alert.setTitle("Unsaved solution");
        alert.setHeaderText("Close without saving?");
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private Node buildProblemListHeader() {
        Label heading = new Label("PROBLEMS");
        heading.getStyleClass().add("section-heading");
        difficultyFilter = new ComboBox<>();
        difficultyFilter.getItems().setAll("All difficulties", "Easy", "Medium", "Hard");
        difficultyFilter.setValue("All difficulties");
        counts = new Label("0 problems · 0 solved");
        counts.getStyleClass().add("muted");
        VBox box = new VBox(10, heading, difficultyFilter, counts);
        return box;
    }

    private VBox buildEditorPane() {
        selectedTitle = new Label("Select a problem");
        selectedTitle.getStyleClass().add("pane-title");
        busyLabel = new Label();
        busyLabel.getStyleClass().add("muted");
        busyIndicator = new ProgressIndicator();
        busyIndicator.setPrefSize(18, 18);
        busyIndicator.setVisible(false);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox titleBar = new HBox(8, selectedTitle, spacer, busyIndicator, busyLabel);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        editor = new LanguageEditor(activeProvider(), message -> showError("Editor operation unavailable", message));
        editor.setFontSize(config.editorFontSize());
        editor.setOnSave(() -> { if (!busy) saveSolution(); });
        editor.setOnRun(() -> { if (!runButton.isDisabled()) execute(false); });
        caretPosition = new Label("Ln 1, Col 1");
        caretPosition.getStyleClass().add("editor-status");
        editor.caretPositionProperty().addListener((observable, oldValue, newValue) -> updateCaretPosition());
        editor.textProperty().addListener((obs, old, value) -> {
            if (!changingEditor && selected != null) documents.drafts().put(documentKey(selected), value);
        });
        VBox.setVgrow(editor, Priority.ALWAYS);
        Button run = new Button("Run");
        run.getStyleClass().add("secondary-button");
        run.setTooltip(new Tooltip("Run sample tests (Ctrl+Enter)"));
        runButton = run;
        run.setOnAction(e -> execute(false));
        Button all = new Button("Run All Tests");
        all.getStyleClass().add("primary-button");
        all.setTooltip(new Tooltip("Run sample and hidden tests"));
        runAllButton = all;
        all.setOnAction(e -> execute(true));
        Button save = new Button("Save");
        save.setTooltip(new Tooltip("Save this solution (Ctrl+S)"));
        saveButton = save;
        save.setOnAction(e -> saveSolution());
        Button reset = new Button("Reset");
        reset.setTooltip(new Tooltip("Restore the saved solution or starter template"));
        resetButton = reset;
        reset.setOnAction(e -> resetSolution());
        Region actionSpacer = new Region();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);
        HBox actions = new HBox(8, caretPosition, buildFontSizeSelector(), actionSpacer, run, all, save, reset);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPadding(new Insets(10, 0, 0, 0));
        actions.getStyleClass().add("editor-actions");
        VBox box = new VBox(10, titleBar, editor, actions);
        box.setPadding(new Insets(16));
        box.getStyleClass().add("editor-pane");
        return box;
    }

    private Node buildFontSizeSelector() {
        fontSizeSelector = new ComboBox<>();
        fontSizeSelector.getStyleClass().add("font-size-selector");
        fontSizeSelector.getItems().setAll(LanguageEditor.FONT_SIZE_PRESETS);
        fontSizeSelector.setConverter(new StringConverter<>() {
            @Override public String toString(Integer size) { return size == null ? "" : size + " px"; }
            @Override public Integer fromString(String value) { throw new UnsupportedOperationException(); }
        });
        fontSizeSelector.setTooltip(new Tooltip("Editor text size (Ctrl+= / Ctrl+- / Ctrl+0, or Ctrl+scroll)"));
        fontSizeSelector.setFocusTraversable(false);
        fontSizeSelector.setValue(editor.getFontSize());
        fontSizeSelector.setOnAction(e -> {
            Integer size = fontSizeSelector.getValue();
            if (size != null && size != editor.getFontSize()) editor.setFontSize(size);
        });
        fontSizeSave.setOnFinished(e -> persistEditorFontSize());
        editor.fontSizeProperty().addListener((obs, old, size) -> {
            if (!size.equals(fontSizeSelector.getValue())) fontSizeSelector.setValue(size.intValue());
            fontSizeSave.playFromStart();
        });
        Label label = new Label("Text size");
        label.getStyleClass().add("editor-status");
        HBox box = new HBox(6, label, fontSizeSelector);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(0, 0, 0, 10));
        return box;
    }

    private void persistEditorFontSize() {
        if (config.editorFontSize() == editor.getFontSize()) return;
        AppConfig updated = config.withEditorFontSize(editor.getFontSize());
        try {
            updated.save();
            config = updated;
        } catch (IOException ex) { showError("Text size could not be saved", ex.getMessage()); }
    }

    private void updateCaretPosition() {
        int caret = editor.getCaretPosition();
        String text = editor.getText();
        int lineStart = text.lastIndexOf('\n', caret - 1) + 1;
        caretPosition.setText("Ln " + (editor.getCurrentParagraph() + 1) + ", Col " + (caret - lineStart + 1));
    }

    private Node buildResultsPane() {
        outputTabs = new TabPane();
        outputTabs.getStyleClass().add("results-pane");
        testCasesText = readOnlyArea();
        resultsText = readOnlyArea();
        errorsText = readOnlyArea();
        outputText = readOnlyArea();
        outputTabs.getTabs().setAll(
                new Tab("Test Cases", testCasesText), new Tab("Test Results", resultsText),
                new Tab("Compilation Errors", errorsText), new Tab("Execution Output", outputText));
        outputTabs.getTabs().forEach(tab -> tab.setClosable(false));
        outputTabs.setPrefHeight(220);
        outputTabs.setMinHeight(130);
        return outputTabs;
    }

    private TextArea readOnlyArea() {
        TextArea area = new TextArea();
        area.setEditable(false);
        area.setWrapText(true);
        area.getStyleClass().add("output-area");
        return area;
    }

    public void initialize() {
        try {
            allProblems.clear();
            allProblems.addAll(problemService.load(config.problemRoot()));
            rebuildSearchIndex();
            filterProblems();
            updateCounts();
            if (!allProblems.isEmpty()) problemList.getSelectionModel().selectFirst();
        } catch (IOException ex) {
            showError("Could not load problems", ex.getMessage());
        }
    }

    private void filterProblems() {
        if (problemList == null) return;
        String query = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        String difficulty = difficultyFilter.getValue();
        List<Problem> filtered = allProblems.stream()
                .filter(p -> query.isBlank() || problemSearchIndex.getOrDefault(p.id(), "").contains(query))
                .filter(p -> difficulty == null || difficulty.startsWith("All") || p.difficulty().name().equalsIgnoreCase(difficulty))
                .toList();
        Problem current = selected;
        problemList.getItems().setAll(filtered);
        if (current != null && filtered.contains(current)) problemList.getSelectionModel().select(current);
        else if (!filtered.isEmpty()) problemList.getSelectionModel().selectFirst();
        else problemList.getSelectionModel().clearSelection();
    }

    private void rebuildSearchIndex() {
        problemSearchIndex.clear();
        for (Problem problem : allProblems) {
            String searchable = problem.title() + " " + String.join(" ", problem.topics());
            problemSearchIndex.put(problem.id(), searchable.toLowerCase(Locale.ROOT));
        }
    }

    private void updateCounts() {
        long solved = allProblems.stream().filter(problem -> progressService.isSolved(problem.id())).count();
        if (counts != null) counts.setText(allProblems.size() + " problems · " + solved + " solved");
    }

    private void selectProblem(Problem problem) {
        String content;
        try {
            content = documentContent(problem, config.selectedLanguage());
        } catch (IOException ex) {
            showError("Could not read solution", ex.getMessage());
            problemList.getSelectionModel().select(selected);
            return;
        }
        selected = problem;
        selectedTitle.setText(problem.title() + "  ·  " + problem.difficulty().name());
        String html = markdownRenderer.render(markdownParser.parse(problem.descriptionMarkdown()));
        description.getEngine().loadContent("<html><head><meta charset='utf-8'><style>" + markdownCss() + "</style></head><body><main>" + html + "</main></body></html>");
        changingEditor = true;
        editor.replaceText(content);
        changingEditor = false;
        refreshSampleCases();
        resultsText.clear();
        errorsText.clear();
        outputText.clear();
        updateActionState();
    }

    private void clearSelection() {
        selected = null;
        selectedTitle.setText("Select a problem");
        description.getEngine().loadContent("<html><head><meta charset='utf-8'><style>" + markdownCss()
                + "</style></head><body><main><h1>Ready when you are</h1>"
                + "<p>Choose a problem from the list to view its description and start coding.</p>"
                + "</main></body></html>");
        changingEditor = true;
        editor.clear();
        changingEditor = false;
        testCasesText.clear();
        resultsText.clear();
        errorsText.clear();
        outputText.clear();
        updateActionState();
    }

    private LanguageProvider activeProvider() { return languages.require(config.selectedLanguage()); }

    private SolutionDocuments.Key documentKey(Problem problem) {
        return new SolutionDocuments.Key(problem.id(), config.selectedLanguage());
    }

    private String documentContent(Problem problem, Language language) throws IOException {
        var key = new SolutionDocuments.Key(problem.id(), language);
        if (documents.drafts().containsKey(key)) return documents.drafts().get(key);
        if (documents.initial().containsKey(key)) return documents.initial().get(key);
        LanguageProvider provider = languages.require(language);
        Path file = SolutionDocuments.file(problem.directory(), provider);
        String content = Files.isRegularFile(file) ? Files.readString(file) : provider.starterCode();
        documents.initial().put(key, content);
        return content;
    }

    private void switchLanguage(Language language) {
        if (language == null || language.equals(config.selectedLanguage())) return;
        try {
            String content = selected == null ? "" : documentContent(selected, language);
            AppConfig updated = new AppConfig(config.problemRoot(), config.executionTimeoutSeconds(), language, config.theme(),
                    config.editorFontSize());
            updated.save();
            config = updated;
            changingEditor = true;
            editor.setProvider(activeProvider());
            editor.replaceText(content);
            changingEditor = false;
            resultsText.clear();
            errorsText.clear();
            outputText.clear();
            updateActionState();
        } catch (IOException ex) {
            changingLanguage = true;
            languageSelector.setValue(config.selectedLanguage());
            changingLanguage = false;
            showError("Could not switch language", ex.getMessage());
        }
    }

    private void refreshSampleCases() {
        StringBuilder text = new StringBuilder();
        int i = 0;
        for (TestCase test : selected.testCases()) {
            i++;
            if (test.visibility() != TestCaseVisibility.SAMPLE) continue;
            text.append("Test Case ").append(i).append("\nInput: ").append(test.input().toPrettyString())
                    .append("\nExpected: ").append(test.expected().toPrettyString()).append("\n\n");
        }
        testCasesText.setText(text.toString());
    }

    private void execute(boolean allTests) {
        if (busy || selected == null) return;
        if (!executionManager.supports(config.selectedLanguage())) {
            showError("Execution unavailable", "No executor registered for " + activeProvider().displayName() + ".");
            return;
        }
        List<TestCase> selectedCases = testCaseService.forRun(selected, allTests);
        if (selectedCases.isEmpty()) { showError("No test cases", "This problem has no sample test cases."); return; }
        String code = editor.getText();
        Problem problem = selected;
        Language language = config.selectedLanguage();
        int timeout = config.executionTimeoutSeconds();
        setBusy(true, "Compiling and running…");
        String problemId = problem.id();
        Path adapterDir = SolutionDocuments.directory(problem.directory(), activeProvider());
        Task<ExecutionResult> task = new Task<>() {
            @Override protected ExecutionResult call() {
                return executionManager.execute(new ExecutionRequest(problem, code, language,
                        selectedCases, Duration.ofSeconds(timeout), adapterDir));
            }
        };
        task.setOnSucceeded(event -> {
            ExecutionResult result = task.getValue();
            present(result, allTests);
            try {
                boolean passed = result.compilationSucceeded() && result.testResults().size() == selectedCases.size()
                        && result.testResults().stream().allMatch(r -> r.status() == TestStatus.PASSED);
                progressService.recordAttempt(problemId, language, result.durationMillis(), passed, allTests);
                updateCounts();
                problemList.refresh();
            } catch (IOException ex) { showError("Could not save progress", ex.getMessage()); }
            finishExecution();
        });
        task.setOnFailed(event -> {
            showError("Execution failed", task.getException() == null ? "Unknown execution error." : task.getException().getMessage());
            finishExecution();
        });
        Thread thread = new Thread(task, "codeprep-execution");
        thread.setDaemon(true);
        thread.start();
    }

    private void present(ExecutionResult result, boolean allTests) {
        errorsText.setText(result.compilationErrors());
        outputText.setText(result.executionOutput());
        List<TestCaseDisplayResult> display = new ArrayList<>();
        for (TestCaseResult resultItem : result.testResults()) {
            boolean hidden = resultItem.visibility() == TestCaseVisibility.HIDDEN;
            display.add(new TestCaseDisplayResult("Test Case " + resultItem.index(), hidden, resultItem.status(),
                    hidden ? "" : json(resultItem.input()), hidden ? "" : json(resultItem.expected()),
                    hidden || resultItem.actual() == null ? "" : json(resultItem.actual()),
                    resultItem.durationMillis(), hidden && resultItem.status() != TestStatus.PASSED
                            && resultItem.status() != TestStatus.TIMED_OUT
                            ? "Hidden test case failed. Input and expected output are not available." : resultItem.message()));
        }
        StringBuilder text = new StringBuilder();
        int passed = 0, failed = 0;
        for (TestCaseDisplayResult item : display) {
            if (item.status() == TestStatus.PASSED) passed++; else failed++;
            text.append(item.label()).append("    ").append(item.status().displayName()).append("    ").append(item.durationMillis()).append(" ms\n");
            if (item.hidden()) {
                if (item.status() != TestStatus.PASSED) text.append("  ").append(item.message()).append("\n");
            } else {
                text.append("  Input: ").append(item.input()).append("\n  Expected: ").append(item.expected())
                        .append("\n  Actual: ").append(item.actual()).append("\n");
                if (!item.message().isBlank()) text.append("  ").append(item.message()).append("\n");
            }
            text.append("\n");
        }
        text.append("Execution Summary\nPassed: ").append(passed).append("\nFailed: ").append(failed)
                .append("\nTotal: ").append(display.size()).append("\nDuration: ").append(result.durationMillis()).append(" ms");
        if (!result.compilationSucceeded()) {
            outputTabs.getSelectionModel().select(2);
            if (result.compilationErrors().isBlank()) errorsText.setText(result.executionOutput());
        } else {
            resultsText.setText(text.toString());
            outputTabs.getSelectionModel().select(1);
        }
    }

    private String json(com.fasterxml.jackson.databind.JsonNode value) {
        try { return mapper.writeValueAsString(value); } catch (Exception ex) { return ""; }
    }

    private void finishExecution() {
        setBusy(false, "");
    }

    private void setBusy(boolean value, String message) {
        busy = value;
        problemList.setDisable(value);
        busyIndicator.setVisible(value);
        busyLabel.setText(message);
        updateActionState();
    }

    private void updateActionState() {
        boolean noProblem = selected == null;
        boolean noExecutor = !executionManager.supports(config.selectedLanguage());
        if (runButton != null) runButton.setDisable(busy || noProblem || noExecutor);
        if (runAllButton != null) runAllButton.setDisable(busy || noProblem || noExecutor);
        if (runButton != null) runButton.setTooltip(new Tooltip(noExecutor
                ? "No executor registered for " + activeProvider().displayName() : "Run sample tests (Ctrl+Enter)"));
        if (runAllButton != null) runAllButton.setTooltip(new Tooltip(noExecutor
                ? "No executor registered for " + activeProvider().displayName() : "Run sample and hidden tests"));
        if (saveButton != null) saveButton.setDisable(busy || noProblem);
        if (resetButton != null) resetButton.setDisable(busy || noProblem);
        if (search != null) search.setDisable(busy);
        if (difficultyFilter != null) difficultyFilter.setDisable(busy);
        if (randomButton != null) randomButton.setDisable(busy);
        if (languageSelector != null) languageSelector.setDisable(busy);
        if (settingsButton != null) settingsButton.setDisable(busy);
        if (importButton != null) importButton.setDisable(busy);
    }

    private void chooseImport() {
        if (busy) return;
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Import a question folder or a parent containing questions");
        var source = chooser.showDialog(stage);
        if (source == null) return;
        Task<ProblemImportService.Preview> task = new Task<>() {
            @Override protected ProblemImportService.Preview call() throws IOException {
                return importService.scan(source.toPath(), config.problemRoot());
            }
        };
        startImportTask(task, "Scanning questions...", this::showImportPreview);
    }

    private void showImportPreview(ProblemImportService.Preview preview) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle("Import Questions");
        dialog.setHeaderText("New questions will be imported. Review conflicts individually to import separate copies.");
        VBox rows = new VBox(14);
        Map<Path, CheckBox> conflicts = new HashMap<>();
        for (var candidate : preview.candidates()) {
            String title = candidate.problem() == null ? candidate.source().toString()
                    : candidate.problem().title();
            Label heading = new Label(candidate.status() + " - " + title);
            heading.setStyle("-fx-font-weight: bold;");
            Label reason = new Label(candidate.source() + "\n" + candidate.reason());
            reason.setWrapText(true);
            VBox row = new VBox(4, heading, reason);
            if (candidate.status() == ProblemImportService.Status.NEEDS_REVIEW) {
                CheckBox separate = new CheckBox("Reviewed: import separately (never overwrite)");
                conflicts.put(candidate.source(), separate);
                row.getChildren().add(separate);
            }
            rows.getChildren().add(row);
        }
        rows.setPadding(new Insets(12));
        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportWidth(800);
        scroll.setPrefViewportHeight(430);
        dialog.getDialogPane().setContent(scroll);
        ButtonType importType = new ButtonType("Import", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(importType, ButtonType.CANCEL);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != importType) return;
        Set<Path> reviewed = new HashSet<>();
        conflicts.forEach((path, checkbox) -> { if (checkbox.isSelected()) reviewed.add(path); });
        List<Problem> current = List.copyOf(allProblems);
        Task<ImportCompletion> task = new Task<>() {
            @Override protected ImportCompletion call() throws IOException {
                var result = importService.importQuestions(preview, reviewed);
                try {
                    return new ImportCompletion(result, problemService.loadPreservingExisting(config.problemRoot(), current), null);
                } catch (IOException ex) {
                    return new ImportCompletion(result, List.of(), ex.getMessage());
                }
            }
        };
        startImportTask(task, "Importing questions...", completion -> {
            if (completion.refreshError() == null) {
                refreshProblems(completion.problems());
            }
            var result = completion.result();
            String summary = "Imported: " + result.imported() + "   Skipped: " + result.skipped()
                    + "   Invalid: " + result.invalid() + "   Failed: " + result.failed();
            TextArea details = readOnlyArea();
            details.setText(String.join("\n\n", result.details())
                    + (completion.refreshError() == null ? "" : "\n\nLibrary refresh failed: "
                    + completion.refreshError() + ". Reopen CodePrep to reload the library."));
            details.setPrefSize(800, 430);
            Alert alert = new Alert(result.failed() > 0 || completion.refreshError() != null
                    ? Alert.AlertType.WARNING : Alert.AlertType.INFORMATION);
            alert.initOwner(stage);
            alert.setTitle("Import complete");
            alert.setHeaderText(summary);
            alert.getDialogPane().setContent(details);
            alert.showAndWait();
        });
    }

    private record ImportCompletion(ProblemImportService.Result result, List<Problem> problems, String refreshError) {}

    private void refreshProblems(List<Problem> problems) {
        allProblems.clear();
        allProblems.addAll(problems);
        rebuildSearchIndex();
        filterProblems();
        updateCounts();
        if (selected == null && !allProblems.isEmpty()) problemList.getSelectionModel().selectFirst();
    }

    private <T> void startImportTask(Task<T> task, String message, java.util.function.Consumer<T> onSuccess) {
        setBusy(true, message);
        task.setOnSucceeded(event -> {
            setBusy(false, "");
            onSuccess.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            setBusy(false, "");
            showError("Import could not complete", task.getException().getMessage());
        });
        Thread thread = new Thread(task, "codeprep-import");
        thread.setDaemon(true);
        thread.start();
    }

    private void saveSolution() {
        if (selected == null) return;
        Path target = solutionFile(selected);
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, editor.getText());
            documents.drafts().put(documentKey(selected), editor.getText());
            documents.initial().put(documentKey(selected), editor.getText());
            showInfo("Solution saved", target.toString());
        } catch (IOException ex) { showError("Could not save solution", ex.getMessage()); }
    }

    private Path solutionFile(Problem problem) {
        return SolutionDocuments.file(problem.directory(), activeProvider());
    }

    private void resetSolution() {
        if (selected == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Reset the editor to the saved solution or default template?", ButtonType.CANCEL, ButtonType.OK);
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        try {
            Path file = solutionFile(selected);
            String content = Files.isRegularFile(file) ? Files.readString(file) : activeProvider().starterCode();
            documents.initial().put(documentKey(selected), content);
            documents.drafts().remove(documentKey(selected));
            changingEditor = true;
            editor.replaceText(content);
            changingEditor = false;
        } catch (IOException ex) { showError("Could not reset solution", ex.getMessage()); }
    }

    private void selectRandom() {
        if (busy || allProblems.isEmpty()) return;
        List<Problem> choices = allProblems.stream().filter(p -> !progressService.isSolved(p.id())).toList();
        if (choices.isEmpty()) choices = allProblems;
        Problem choice = choices.get(new Random().nextInt(choices.size()));
        search.clear();
        difficultyFilter.setValue("All difficulties");
        filterProblems();
        problemList.getSelectionModel().select(choice);
    }

    private void showSettings() {
        if (busy) return;
        TextField rootLabel = new TextField(config.problemRoot().toString());
        rootLabel.setEditable(false);
        rootLabel.setPrefColumnCount(40);
        TextField timeout = new TextField(Integer.toString(config.executionTimeoutSeconds()));
        ComboBox<String> theme = new ComboBox<>();
        theme.getItems().setAll("dark");
        theme.setValue(config.theme());
        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(12);
        form.addRow(0, new Label("Library path (read-only)"), rootLabel);
        form.addRow(1, new Label("Default per-test timeout (seconds)"), timeout);
        form.addRow(2, new Label("Theme"), theme);
        form.addRow(3, new Label("Question-specific time limits override this default."));
        form.setPadding(new Insets(12));
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle("CodePrep Settings");
        dialog.setHeaderText("Application settings");
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        int seconds;
        try {
            seconds = Integer.parseInt(timeout.getText().trim());
            if (seconds < 1 || seconds > 60) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            showError("Invalid execution timeout", "Choose a whole number between 1 and 60 seconds.");
            return;
        }
        try {
            AppConfig updated = new AppConfig(config.problemRoot(), seconds, config.selectedLanguage(), theme.getValue(),
                    config.editorFontSize());
            updated.save();
            config = updated;
        } catch (IOException ex) { showError("Settings could not be saved", ex.getMessage()); }
    }

    private String markdownCss() {
        return "html{background:#14171d}body{font-family:'Segoe UI',Arial,sans-serif;background:#14171d;color:#dce1ea;"
                + "margin:0;padding:30px 34px;line-height:1.68;font-size:14px}main{max-width:820px;margin:0 auto}"
                + "h1,h2,h3{color:#f2f5fa;line-height:1.3}h1{font-size:26px;margin:0 0 20px}"
                + "h2{font-size:18px;margin:28px 0 10px;border-bottom:1px solid #292e39;padding-bottom:7px}"
                + "h3{font-size:15px;margin-top:22px}p,li{color:#c6ceda}li{margin:4px 0}"
                + "code,pre{font-family:Consolas,monospace;background:#20242d;color:#f0bd83;border-radius:5px}"
                + "code{padding:2px 5px}pre{padding:14px 16px;overflow:auto;border:1px solid #303745}"
                + "pre code{padding:0;background:transparent}blockquote{border-left:3px solid #548cf5;"
                + "margin-left:0;padding:2px 0 2px 14px;color:#aab4c4}a{color:#80b7ff}"
                + "table{border-collapse:collapse}th,td{border:1px solid #343a47;padding:7px 10px}"
                + "th{background:#20242d;color:#f1f4f9}";
    }

    private void showError(String title, String detail) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(detail == null ? "An unexpected error occurred." : detail);
        alert.show();
    }

    private void showInfo(String title, String detail) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, detail, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.show();
    }

    private final class ProblemCell extends ListCell<Problem> {
        @Override protected void updateItem(Problem problem, boolean empty) {
            super.updateItem(problem, empty);
            if (empty || problem == null) { setGraphic(null); setText(null); return; }
            Label title = new Label(problem.title());
            title.getStyleClass().add("problem-name");
            String category = problem.id().contains("/") ? problem.id().substring(0, problem.id().indexOf('/')) : "Uncategorized";
            Label info = new Label(category + "  ·  " + problem.difficulty().name() + "  ·  " + String.join(", ", problem.topics()));
            info.getStyleClass().add("muted");
            info.setWrapText(true);
            Label solved = new Label(progressService.isSolved(problem.id()) ? "✓" : "");
            solved.getStyleClass().add("solved-mark");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox row = new HBox(title, spacer, solved);
            VBox cell = new VBox(4, row, info);
            cell.setPadding(new Insets(6, 2, 6, 2));
            setGraphic(cell);
            setText(null);
        }
    }
}
