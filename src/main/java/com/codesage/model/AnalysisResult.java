package com.codesage.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable-friendly container for static analysis output: numeric score, qualitative category,
 * and human-readable findings ({@link #issues}) with remediation hints ({@link #suggestions}).
 */
public class AnalysisResult {

    /** Aggregate quality score in the range {@code [0, 100]}. */
    private int score;

    /** Derived band for {@link #score} (e.g. Excellent, Good). */
    private String scoreCategory;

    /** Detected problems, suitable for display or logging. */
    private List<String> issues;

    /** Recommended fixes or refactorings aligned with {@link #issues}. */
    private List<String> suggestions;

    /**
     * Creates an empty result shell (score 0, empty lists). Prefer
     * {@link #AnalysisResult(int, String, List, List)} for populated instances.
     */
    public AnalysisResult() {
        this(0, "", new ArrayList<>(), new ArrayList<>());
    }

    /**
     * Fully initialized analysis snapshot.
     *
     * @param score          final score after deductions
     * @param scoreCategory  category label derived from score bands
     * @param issues         detected issues (may be mutable list reference)
     * @param suggestions    suggestions paired logically with issues
     */
    public AnalysisResult(int score, String scoreCategory, List<String> issues, List<String> suggestions) {
        this.score = score;
        this.scoreCategory = scoreCategory;
        this.issues = issues != null ? issues : new ArrayList<>();
        this.suggestions = suggestions != null ? suggestions : new ArrayList<>();
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getScoreCategory() {
        return scoreCategory;
    }

    public void setScoreCategory(String scoreCategory) {
        this.scoreCategory = scoreCategory;
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

    @Override
    public String toString() {
        return "AnalysisResult{"
                + "score=" + score
                + ", scoreCategory='" + scoreCategory + '\''
                + ", issues=" + issues
                + ", suggestions=" + suggestions
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AnalysisResult that = (AnalysisResult) o;
        return score == that.score
                && Objects.equals(scoreCategory, that.scoreCategory)
                && Objects.equals(issues, that.issues)
                && Objects.equals(suggestions, that.suggestions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(score, scoreCategory, issues, suggestions);
    }
}
