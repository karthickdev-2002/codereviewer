package com.codereview.model;

import java.util.ArrayList;
import java.util.List;

public class MethodAnalysisResult {

    private String methodName;
    private int lineCount;
    private int score;
    private int nestedDepth;
    private List<String> issues;
    private List<String> suggestions;

    public MethodAnalysisResult() {
        this.issues = new ArrayList<>();
        this.suggestions = new ArrayList<>();
    }

    public MethodAnalysisResult(String methodName,
                                int lineCount,
                                int score,
                                int nestedDepth,
                                List<String> issues,
                                List<String> suggestions) {
        this.methodName = methodName;
        this.lineCount = lineCount;
        this.score = score;
        this.nestedDepth = nestedDepth;
        this.issues = issues != null ? issues : new ArrayList<>();
        this.suggestions = suggestions != null ? suggestions : new ArrayList<>();
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public int getLineCount() {
        return lineCount;
    }

    public void setLineCount(int lineCount) {
        this.lineCount = lineCount;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public int getNestedDepth() {
        return nestedDepth;
    }

    public void setNestedDepth(int nestedDepth) {
        this.nestedDepth = nestedDepth;
    }

    public List<String> getIssues() {
        return issues;
    }

    public void setIssues(List<String> issues) {
        this.issues = issues != null ? issues : new ArrayList<>();
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions != null ? suggestions : new ArrayList<>();
    }
}

