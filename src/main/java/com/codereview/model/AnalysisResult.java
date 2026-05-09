package com.codereview.model;

import java.util.List;

/**
 * Model class representing the result of a code analysis.
 * Contains the quality score, detected issues, suggestions, and quality status.
 *
 * NOTE: This is a stub model for the controller layer (Section 2).
 *       Member 1 (Section 1 - Analysis Engine) will expand this with
 *       full implementation details.
 */
public class AnalysisResult {

    private int score;
    private String qualityStatus;
    private List<String> issues;
    private List<String> suggestions;

    // Default constructor
    public AnalysisResult() {
    }

    // Parameterized constructor
    public AnalysisResult(int score, String qualityStatus, List<String> issues, List<String> suggestions) {
        this.score = score;
        this.qualityStatus = qualityStatus;
        this.issues = issues;
        this.suggestions = suggestions;
    }

    // --- Getters and Setters ---

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getQualityStatus() {
        return qualityStatus;
    }

    public void setQualityStatus(String qualityStatus) {
        this.qualityStatus = qualityStatus;
    }

    public List<String> getIssues() {
        return issues;
    }

    public void setIssues(List<String> issues) {
        this.issues = issues;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions;
    }
}
