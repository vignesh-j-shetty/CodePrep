package com.codeprep.editor;

import com.codeprep.model.Language;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Deliberately different language rules; not registered as shipped language support. */
public class TestLanguageProvider implements LanguageProvider {
    @Override public Language language() { return new Language("PYTHON"); }
    @Override public String displayName() { return "Test Python"; }
    @Override public String solutionDirectory() { return "python"; }
    @Override public String solutionFileName() { return "solution.py"; }
    @Override public String starterCode() { return "def solve():\n  pass\n"; }
    @Override public Set<String> sourceExtensions() { return Set.of(".py"); }
    @Override public List<Token> highlight(String source) {
        return source.startsWith("#") ? List.of(new Token(0, source.length(), "comment")) : List.of();
    }
    @Override public List<Completion> complete(String source, int caret) {
        return List.of(new Completion("print", 0, caret, "print()", 6));
    }
    @Override public List<Snippet> snippets() { return List.of(new Snippet("pr", "print", "print()", 6)); }
    @Override public List<BracketPair> bracketPairs() { return List.of(new BracketPair('(', ')')); }
    @Override public CommentStyle comments() { return new CommentStyle("#", null, null); }
    @Override public String indentation() { return "  "; }
    @Override public String newline(String source, int caret) { return source.substring(0, caret).endsWith(":") ? "\n  " : "\n"; }
    @Override public Optional<Formatter> formatter() { return Optional.of(String::stripTrailing); }
}
