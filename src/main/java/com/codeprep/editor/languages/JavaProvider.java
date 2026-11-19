package com.codeprep.editor.languages;

import com.codeprep.editor.LanguageProvider;
import com.codeprep.model.Language;
import com.sun.source.util.JavacTask;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

public final class JavaProvider implements LanguageProvider {
    private static final List<String> KEYWORDS = Arrays.asList((
            "abstract assert boolean break byte case catch char class const continue default do double "
            + "else enum extends final finally float for goto if implements import instanceof int interface "
            + "long native new package private protected public return short static strictfp super switch "
            + "synchronized this throw throws transient try void volatile while record sealed permits var "
            + "yield null true false").split(" "));
    private static final Pattern TOKENS = Pattern.compile(
            "(?<comment>//[^\\n]*|/\\*[\\s\\S]*?(?:\\*/|\\z))"
            + "|(?<string>\"\"\"[\\s\\S]*?(?:\"\"\"|\\z)|\"(?:\\\\.|[^\"\\\\\\r\\n])*+\"?|'(?:\\\\.|[^'\\\\\\r\\n])*+'?)"
            + "|(?<keyword>\\b(?:" + String.join("|", KEYWORDS) + ")\\b)"
            + "|(?<number>\\b(?:0[xX][\\da-fA-F_]+|0[bB][01_]+|\\d[\\d_]*(?:\\.[\\d_]+)?(?:[eE][+-]?[\\d_]+)?)[fFdDlL]?\\b)");
    private static final Pattern IDENTIFIER = Pattern.compile("\\b[A-Za-z_$][A-Za-z0-9_$]*\\b");
    private static final List<Snippet> SNIPPETS = List.of(
            snippet("sout", "Print a line", "System.out.println(|);"),
            snippet("fori", "Indexed for loop", "for (int i = 0; i < n; i++) {\n    |\n}"),
            snippet("main", "Main method", "public static void main(String[] args) {\n    |\n}"));

    private static Snippet snippet(String trigger, String description, String template) {
        int caret = template.indexOf('|');
        return new Snippet(trigger, description, template.replace("|", ""), caret);
    }

    @Override public Language language() { return Language.JAVA; }
    @Override public String displayName() { return "Java"; }
    @Override public String solutionDirectory() { return "java"; }
    @Override public String solutionFileName() { return "Solution.java"; }
    @Override public String starterCode() { return "import java.util.*;\n\nclass Solution {\n    \n}\n"; }
    @Override public Set<String> sourceExtensions() { return Set.of(".java"); }
    @Override public String indentation() { return "    "; }
    @Override public List<BracketPair> bracketPairs() {
        return List.of(new BracketPair('(', ')'), new BracketPair('[', ']'), new BracketPair('{', '}'));
    }
    @Override public CommentStyle comments() { return new CommentStyle("//", "/*", "*/"); }
    @Override public List<Snippet> snippets() { return SNIPPETS; }

    @Override public List<Token> highlight(String source) {
        var matcher = TOKENS.matcher(source);
        List<Token> result = new ArrayList<>();
        while (matcher.find()) {
            String style = matcher.group("comment") != null ? "comment" : matcher.group("string") != null ? "string"
                    : matcher.group("number") != null ? "number" : "keyword";
            result.add(new Token(matcher.start(), matcher.end(), style));
        }
        return List.copyOf(result);
    }

    @Override public List<Completion> complete(String source, int caret) {
        if (!autoPairAllowed(source, caret)) return List.of();
        int start = caret;
        while (start > 0 && Character.isJavaIdentifierPart(source.charAt(start - 1))) start--;
        String prefix = source.substring(start, caret);
        Set<String> words = new LinkedHashSet<>(KEYWORDS);
        words.addAll(List.of("String", "System", "Integer", "Long", "Math", "Arrays", "Collections",
                "List", "ArrayList", "Map", "HashMap", "Set", "HashSet", "Deque", "ArrayDeque"));
        var identifiers = IDENTIFIER.matcher(source);
        while (identifiers.find()) {
            if (identifiers.end() != caret) words.add(identifiers.group());
        }
        List<Completion> result = new ArrayList<>();
        int lineStart = source.lastIndexOf('\n', start - 1) + 1;
        String line = source.substring(lineStart, start);
        String indent = line.substring(0, line.length() - line.stripLeading().length());
        for (Snippet snippet : snippets()) {
            if (snippet.trigger().startsWith(prefix)) {
                String text = snippet.text().replace("\n", "\n" + indent);
                int offset = snippet.caretOffset() + (int) snippet.text().substring(0, snippet.caretOffset())
                        .chars().filter(ch -> ch == '\n').count() * indent.length();
                result.add(new Completion(snippet.trigger() + " - " + snippet.description(), start, caret, text, offset));
            }
        }
        for (String word : words) {
            if (word.startsWith(prefix) && !word.equals(prefix))
                result.add(new Completion(word, start, caret, word, word.length()));
            if (result.size() >= 80) break;
        }
        return List.copyOf(result);
    }

    @Override public boolean autoPairAllowed(String source, int caret) {
        return highlight(source).stream().noneMatch(t -> {
            if ((!t.style().equals("string") && !t.style().equals("comment")) || t.start() >= caret) return false;
            if (caret < t.end()) return true;
            if (caret != t.end()) return false;
            String token = source.substring(t.start(), t.end());
            if (token.startsWith("//")) return true;
            if (token.startsWith("/*")) return !token.endsWith("*/");
            if (token.startsWith("\"\"\"")) return token.length() < 6 || !token.endsWith("\"\"\"");
            return token.length() < 2 || token.charAt(token.length() - 1) != token.charAt(0)
                    || hasEscapedClosingQuote(token);
        });
    }

    private boolean hasEscapedClosingQuote(String token) {
        int slashes = 0;
        for (int i = token.length() - 2; i >= 0 && token.charAt(i) == '\\'; i--) slashes++;
        return slashes % 2 != 0;
    }

    @Override public String newline(String source, int caret) {
        int start = source.lastIndexOf('\n', caret - 1) + 1;
        String before = source.substring(start, caret);
        int whitespace = 0;
        while (whitespace < before.length() && (before.charAt(whitespace) == ' ' || before.charAt(whitespace) == '\t'))
            whitespace++;
        String code = maskLiterals(source).substring(start, caret).stripTrailing();
        return "\n" + before.substring(0, whitespace) + (code.endsWith("{") ? indentation() : "");
    }

    @Override public Optional<Formatter> formatter() { return Optional.of(this::format); }

    private String format(String source) {
        List<Token> protectedTokens = highlight(source).stream()
                .filter(t -> t.style().equals("string") || t.style().equals("comment")).toList();
        String masked = maskLiterals(source, protectedTokens);
        StringBuilder result = new StringBuilder();
        int braces = 0, parens = 0, offset = 0, protectedIndex = 0;
        for (String line : source.split("\n", -1)) {
            String code = masked.substring(offset, offset + line.length()).strip();
            while (protectedIndex < protectedTokens.size() && protectedTokens.get(protectedIndex).end() <= offset) protectedIndex++;
            boolean continuedLiteral = protectedIndex < protectedTokens.size() && protectedTokens.get(protectedIndex).start() < offset;
            int depth = Math.max(0, braces - (code.startsWith("}") ? 1 : 0));
            if (parens > 0 && !code.startsWith(")")) depth++;
            if (continuedLiteral) result.append(line);
            else if (!line.isBlank()) result.append(indentation().repeat(depth)).append(line.strip());
            for (char ch : code.toCharArray()) {
                if (ch == '{') braces++;
                if (ch == '}') braces = Math.max(0, braces - 1);
                if (ch == '(' || ch == '[') parens++;
                if (ch == ')' || ch == ']') parens = Math.max(0, parens - 1);
            }
            offset += line.length() + 1;
            if (offset <= source.length()) result.append('\n');
        }
        return result.toString();
    }

    private String maskLiterals(String source) {
        return maskLiterals(source, highlight(source));
    }

    private String maskLiterals(String source, List<Token> tokens) {
        char[] masked = source.toCharArray();
        for (Token token : tokens) {
            if (!token.style().equals("string") && !token.style().equals("comment")) continue;
            for (int i = token.start(); i < token.end(); i++) {
                if (masked[i] != '\n' && masked[i] != '\r') masked[i] = ' ';
            }
        }
        return new String(masked);
    }

    @Override public Optional<DiagnosticProvider> diagnostics() { return Optional.of(this::diagnose); }

    public List<Diagnostic> diagnose(String source) {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("Java diagnostics require a JDK, not a JRE.");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaFileObject file = new SimpleJavaFileObject(URI.create("string:///Solution.java"), JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return source; }
        };
        try (var manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, null)) {
            // Parse only: adapters may supply types unavailable to the editor's classpath.
            JavacTask task = (JavacTask) compiler.getTask(null, manager, diagnostics,
                    List.of("-proc:none", "--release", "17"), null, List.of(file));
            task.parse();
            return diagnostics.getDiagnostics().stream().map(d -> {
                int start = (int) Math.max(0, Math.min(source.length(), d.getStartPosition()));
                int end = (int) Math.max(start, Math.min(source.length(), d.getEndPosition()));
                Severity severity = switch (d.getKind()) {
                    case ERROR -> Severity.ERROR;
                    case WARNING, MANDATORY_WARNING -> Severity.WARNING;
                    default -> Severity.INFO;
                };
                return new Diagnostic(start, end, severity, d.getMessage(Locale.ROOT));
            }).toList();
        } catch (IOException ex) {
            throw new UncheckedIOException("Java diagnostics failed", ex);
        }
    }
}
