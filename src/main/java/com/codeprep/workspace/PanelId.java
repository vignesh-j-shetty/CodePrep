package com.codeprep.workspace;

public enum PanelId {
    PROBLEMS("Problems"), PROBLEM("Problem Viewer"), EDITOR("Code Editor"), RESULTS("Test Results");

    private final String title;

    PanelId(String title) { this.title = title; }

    public String title() { return title; }
}
