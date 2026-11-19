package com.codeprep.editor;

public final class EditorOperations {
    private EditorOperations() {}

    public static EditorIndentation.Edit toggleComment(String source, int anchor, int caret,
                                                       LanguageProvider.CommentStyle comments) {
        int start = source.lastIndexOf('\n', Math.min(anchor, caret) - 1) + 1;
        int last = Math.max(anchor, caret);
        if (last > Math.min(anchor, caret) && source.charAt(last - 1) == '\n') last--;
        int end = source.indexOf('\n', last);
        if (end < 0) end = source.length();
        String selection = source.substring(start, end);
        String replacement;
        if (comments.line() != null && !comments.line().isEmpty()) {
            String[] lines = selection.split("\n", -1);
            boolean uncomment = java.util.Arrays.stream(lines).filter(line -> !line.isBlank())
                    .allMatch(line -> line.stripLeading().startsWith(comments.line()));
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].isBlank()) continue;
                int indent = lines[i].length() - lines[i].stripLeading().length();
                if (uncomment) {
                    int remove = indent + comments.line().length();
                    if (remove < lines[i].length() && lines[i].charAt(remove) == ' ') remove++;
                    lines[i] = lines[i].substring(0, indent) + lines[i].substring(remove);
                } else {
                    lines[i] = lines[i].substring(0, indent) + comments.line() + " " + lines[i].substring(indent);
                }
            }
            replacement = String.join("\n", lines);
        } else if (comments.blockStart() != null && comments.blockEnd() != null) {
            if (selection.startsWith(comments.blockStart()) && selection.endsWith(comments.blockEnd())
                    && selection.length() >= comments.blockStart().length() + comments.blockEnd().length()) {
                replacement = selection.substring(comments.blockStart().length(), selection.length() - comments.blockEnd().length());
            } else {
                replacement = comments.blockStart() + selection + comments.blockEnd();
            }
        } else {
            throw new UnsupportedOperationException("This language does not define a comment style.");
        }
        return new EditorIndentation.Edit(start, end, replacement,
                anchor <= caret ? start : start + replacement.length(),
                anchor <= caret ? start + replacement.length() : start);
    }
}
