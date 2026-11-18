package com.codeprep.service;

import com.codeprep.model.Difficulty;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProblemMarkdownParser {
    private static final Pattern DIFFICULTY = Pattern.compile("(?im)^\\s*Difficulty:\\s*(Easy|Medium|Hard)\\s*$");
    private static final Pattern TOPICS = Pattern.compile("(?im)^\\s*Topics:\\s*(.+?)\\s*$");
    public record Parsed(String title, Difficulty difficulty, List<String> topics, String markdown) {}

    public Parsed parse(String markdown) {
        Matcher titleMatcher = Pattern.compile("(?m)^#\\s+(.+?)\\s*$").matcher(markdown);
        String title = titleMatcher.find() ? titleMatcher.group(1).trim() : "Untitled Problem";
        Matcher difficultyMatcher = DIFFICULTY.matcher(markdown);
        Difficulty difficulty = difficultyMatcher.find()
                ? Difficulty.valueOf(difficultyMatcher.group(1).toUpperCase()) : Difficulty.UNKNOWN;
        Matcher topicsMatcher = TOPICS.matcher(markdown);
        List<String> topics = new ArrayList<>();
        if (topicsMatcher.find()) {
            for (String topic : topicsMatcher.group(1).split(",")) {
                if (!topic.isBlank()) topics.add(topic.trim());
            }
        }
        String content = markdown.replaceFirst("(?m)^#[\\t ]+[^\\r\\n]+(?:\\r\\n|\\r|\\n)?", "")
                .replaceAll("(?im)^[\\t ]*Difficulty:[\\t ]*(Easy|Medium|Hard)[\\t ]*(?:\\r\\n|\\r|\\n|$)", "")
                .replaceAll("(?im)^[\\t ]*Topics:[\\t ]*[^\\r\\n]+(?:\\r\\n|\\r|\\n|$)", "")
                .replaceFirst("\\A(?:[\\t ]*\\R)+", "").replaceFirst("(?:\\R[\\t ]*)+\\z", "");
        return new Parsed(title, difficulty, List.copyOf(topics), content);
    }
}
