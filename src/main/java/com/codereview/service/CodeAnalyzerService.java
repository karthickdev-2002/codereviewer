package com.codereview.service;

import com.codereview.model.AnalysisResult;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

/**
 * Service class responsible for analyzing Java source code.
 *
 * NOTE: This is a stub service for the controller layer (Section 2).
 *       Member 1 (Section 1 - Analysis Engine) will replace the analyze()
 *       method body with the full regex-based static analysis logic including:
 *       - Hardcoded password/secret detection
 *       - System.out.println misuse detection
 *       - Deep nested if-statement detection
 *       - Oversized method/class detection
 *       - Poor variable naming detection
 *       - Duplicate line detection
 *       - Quality score calculation (out of 100)
 */
@Service
public class CodeAnalyzerService {

    /**
     * Analyzes the given Java source code and returns an AnalysisResult.
     *
     * @param code the Java source code to analyze
     * @return AnalysisResult containing score, status, issues, and suggestions
     */
    public AnalysisResult analyze(String code) {
        // --- STUB IMPLEMENTATION ---
        // Member 1 will replace this with the full analysis engine.
        // This stub returns a placeholder result so the controller flow works end-to-end.

        List<String> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        issues.add("Analysis engine not yet implemented — connect Section 1.");
        suggestions.add("Integrate CodeAnalyzerService with the full rule-based scanner.");

        int score = 0;
        String qualityStatus = "Pending";

        return new AnalysisResult(score, qualityStatus, issues, suggestions);
    }
}
