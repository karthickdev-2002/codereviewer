package com.codereview.model;

import java.util.ArrayList;
import java.util.List;

public class FileAnalysisResult {

    private String fileName;
    private String relativePath;
    private int score;
    private String category;
    private List<String> issues;
    private List<String> suggestions;
    private List<MethodAnalysisResult> methodAnalysisResults;

    public FileAnalysisResult() {
        this.issues = new ArrayList<>();
        this.suggestions = new ArrayList<>();
        this.methodAnalysisResults = new ArrayList<>();
    }

    public FileAnalysisResult(String fileName,
                              String relativePath,
                              int score,
                              String category,
                              List<String> issues,
                              List<String> suggestions,
                              List<MethodAnalysisResult> methodAnalysisResults) {
        this.fileName = fileName;
        this.relativePath = relativePath;
        this.score = score;
        this.category = category;
        this.issues = issues != null ? issues : new ArrayList<>();
        this.suggestions = suggestions != null ? suggestions : new ArrayList<>();
        this.methodAnalysisResults = methodAnalysisResults != null ? methodAnalysisResults : new ArrayList<>();
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
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

    public List<MethodAnalysisResult> getMethodAnalysisResults() {
        return methodAnalysisResults;
    }

    public void setMethodAnalysisResults(List<MethodAnalysisResult> methodAnalysisResults) {
        this.methodAnalysisResults = methodAnalysisResults != null ? methodAnalysisResults : new ArrayList<>();
    }
}

