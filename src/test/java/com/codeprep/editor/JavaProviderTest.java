package com.codeprep.editor;

import static org.junit.jupiter.api.Assertions.*;

import com.codeprep.editor.languages.JavaProvider;
import org.junit.jupiter.api.Test;

class JavaProviderTest {
    private final JavaProvider provider = new JavaProvider();

    @Test void highlightsCommentsLiteralsKeywordsAndNumbersWithoutOverlap() {
        String source = "class Solution { String s = \"if 42\"; // return\n int n = 0xFF; }";
        var tokens = provider.highlight(source);
        assertTrue(tokens.stream().anyMatch(t -> t.style().equals("string") && source.substring(t.start(), t.end()).equals("\"if 42\"")));
        assertTrue(tokens.stream().anyMatch(t -> t.style().equals("comment")));
        assertTrue(tokens.stream().anyMatch(t -> t.style().equals("keyword")));
        assertTrue(tokens.stream().anyMatch(t -> t.style().equals("number") && source.substring(t.start(), t.end()).equals("0xFF")));
        int last = 0;
        for (var token : tokens) {
            assertTrue(token.start() >= last);
            assertTrue(token.end() > token.start());
            last = token.end();
        }
    }

    @Test void completesKeywordsLocalIdentifiersAndIndentedSnippets() {
        assertTrue(provider.complete("ret", 3).stream().anyMatch(c -> c.text().equals("return")));
        String source = "int localName;\nloc";
        assertTrue(provider.complete(source, source.length()).stream().anyMatch(c -> c.text().equals("localName")));
        var snippet = provider.complete("    fori", 8).stream().filter(c -> c.label().startsWith("fori")).findFirst().orElseThrow();
        assertEquals(4, snippet.start());
        assertEquals("for (int i = 0; i < n; i++) {\n        \n    }", snippet.text());
        assertEquals('\n', snippet.text().charAt(snippet.caretOffset()));
        assertEquals(' ', snippet.text().charAt(snippet.caretOffset() - 1));
        assertTrue(provider.complete("\"return\"", 4).isEmpty());
    }

    @Test void newlineUsesJavaRulesButIgnoresBracesInsideCommentsAndStrings() {
        assertEquals("\n        ", provider.newline("    if (ok) {tail", 13));
        assertEquals("\n    ", provider.newline("    abc {", 7));
        assertEquals("\n\t", provider.newline("\tword", 5));
        assertEquals("\n    ", provider.newline("    // {", 8));
        assertEquals("\n", provider.newline("String s = \"{\";", 15));
        assertFalse(provider.autoPairAllowed("// comment", 10));
        assertFalse(provider.autoPairAllowed("\"text\"", 3));
        assertTrue(provider.autoPairAllowed("call", 4));
        assertFalse(provider.autoPairAllowed("/* unfinished", 13));
        assertFalse(provider.autoPairAllowed("\"unfinished", 11));
        assertFalse(provider.autoPairAllowed("\"\"", 1));
        assertTrue(provider.autoPairAllowed("\"closed\"", 8));
    }

    @Test void formatsIndentationWithoutRewritingTextBlockContents() {
        String source = "class Solution {\nvoid solve() {\nString s = \"}\";\n}\n}\n";
        assertEquals("class Solution {\n    void solve() {\n        String s = \"}\";\n    }\n}\n",
                provider.formatter().orElseThrow().format(source));
        String block = "class Solution {\nString s = \"\"\"\n      { content }\n    \"\"\";\n}\n";
        String formatted = provider.formatter().orElseThrow().format(block);
        assertTrue(formatted.contains("\n      { content }\n    \"\"\";"));
        assertEquals(formatted, provider.formatter().orElseThrow().format(formatted));
    }

    @Test void reportsParserErrorsButNotUnresolvedAdapterTypes() {
        assertTrue(provider.diagnose("class Solution { Node solve(Node n) { return n; } }").isEmpty());
        var diagnostics = provider.diagnose("class Solution { void solve( }");
        assertFalse(diagnostics.isEmpty());
        assertTrue(diagnostics.stream().allMatch(d -> d.start() >= 0 && d.end() <= 30 && !d.message().isBlank()));
        assertTrue(diagnostics.stream().anyMatch(d -> d.severity() == LanguageProvider.Severity.ERROR));
    }

    @Test void handlesLongLiteralsAndUnterminatedTokensWithoutStackGrowth() {
        String literal = "\"" + "a".repeat(100_000) + "\"";
        assertEquals(java.util.List.of(new LanguageProvider.Token(0, literal.length(), "string")), provider.highlight(literal));
        assertEquals(1, provider.highlight("/*" + "x".repeat(100_000)).size());
        assertFalse(provider.autoPairAllowed(literal.substring(0, literal.length() - 1), literal.length() - 1));
    }
}
