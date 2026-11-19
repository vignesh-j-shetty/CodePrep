package com.codeprep.editor;

import com.codeprep.model.Language;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Language services use UTF-16 offsets, like Java strings and RichTextFX.
 * Services must be stateless/thread-safe; diagnostics run off the UI thread.
 */
public interface LanguageProvider {
    Language language();
    String displayName();
    String solutionDirectory();
    String solutionFileName();
    String starterCode();
    Set<String> sourceExtensions();
    List<Token> highlight(String source);
    List<Completion> complete(String source, int caret);
    List<Snippet> snippets();
    List<BracketPair> bracketPairs();
    CommentStyle comments();
    String indentation();
    String newline(String source, int caret);
    default boolean autoPairAllowed(String source, int caret) { return true; }
    default Optional<Formatter> formatter() { return Optional.empty(); }
    default Optional<DiagnosticProvider> diagnostics() { return Optional.empty(); }

    record Token(int start, int end, String style) {}
    record Completion(String label, int start, int end, String text, int caretOffset) {}
    record Snippet(String trigger, String description, String text, int caretOffset) {}
    record BracketPair(char open, char close) {}
    record CommentStyle(String line, String blockStart, String blockEnd) {}
    record Diagnostic(int start, int end, Severity severity, String message) {}
    enum Severity { ERROR, WARNING, INFO }
    @FunctionalInterface interface Formatter { String format(String source); }
    @FunctionalInterface interface DiagnosticProvider { List<Diagnostic> diagnose(String source); }
}
