package com.codereview.controller;

import com.codereview.model.AnalysisResult;
import com.codereview.service.CodeAnalyzerService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * REST Controller for CodeSage AI Code Reviewer.
 *
 * Responsibilities:
 *  - Serve a health/status check (GET /api/status)
 *  - Receive code input via textarea OR file upload (POST /api/analyze)
 *  - Validate input and return friendly JSON error messages
 *  - Delegate analysis to CodeAnalyzerService
 *  - Return AnalysisResult as JSON response
 */
@RestController
public class AnalyzerController {

    private final CodeAnalyzerService codeAnalyzerService;

    /**
     * Constructor injection for the analyzer service.
     * Spring will auto-wire the CodeAnalyzerService bean.
     */
    public AnalyzerController(CodeAnalyzerService codeAnalyzerService) {
        this.codeAnalyzerService = codeAnalyzerService;
    }

    /**
     * GET /api/status — Simple health check endpoint.
     *
     * @return JSON with application status
     */
    @GetMapping("/api/status")
    public ResponseEntity<Map<String, String>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "status", "running",
                "application", "CodeSage AI Reviewer"
        ));
    }

    /**
     * POST /api/analyze — Accepts Java code via form data.
     *
     * Supports two input methods:
     *  1. "code" — plain text pasted from the textarea
     *  2. "file" — a .java file uploaded from the frontend
     *
     * If both are provided, the file takes priority.
     * Returns the AnalysisResult as JSON.
     *
     * @param code optional code text from the textarea
     * @param file optional uploaded .java file
     * @return JSON response with analysis result or error
     */
    @PostMapping("/api/analyze")
    public ResponseEntity<?> analyzeCode(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "file", required = false) MultipartFile file) {

        String sourceCode = null;

        // --- Priority: File upload takes precedence over textarea ---
        if (file != null && !file.isEmpty()) {
            // Validate file type
            String fileName = file.getOriginalFilename();
            if (fileName == null || !fileName.endsWith(".java")) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Only .java files are allowed. Please upload a valid Java file."
                ));
            }

            // Read file content
            try {
                sourceCode = new String(file.getBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return ResponseEntity.internalServerError().body(Map.of(
                        "error", "Failed to read the uploaded file. Please try again."
                ));
            }
        } else if (code != null && !code.trim().isEmpty()) {
            sourceCode = code;
        }

        // --- Validate: at least one input must be provided ---
        if (sourceCode == null || sourceCode.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Please paste some Java code or upload a .java file before clicking Analyze."
            ));
        }

        // --- Delegate to Analysis Engine ---
        try {
            AnalysisResult result = codeAnalyzerService.analyze(sourceCode);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Something went wrong while analyzing your code. Please try again."
            ));
        }
    }
}
