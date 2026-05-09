package com.codereview.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.codereview.service.CodeAnalyzerService;
import com.codereview.service.ZipAnalyzerService;
import com.codesage.model.AnalysisResult;
import com.codesage.model.CustomRule;

/**
 * REST Controller for CodeSage AI Code Reviewer.
 * Supports legacy file upload form and modern JSON-based rule-enhanced analysis.
 */
@RestController
public class AnalyzerController {

    private final CodeAnalyzerService codeAnalyzerService;
    private final ZipAnalyzerService zipAnalyzerService;

    public AnalyzerController(CodeAnalyzerService codeAnalyzerService, ZipAnalyzerService zipAnalyzerService) {
        this.codeAnalyzerService = codeAnalyzerService;
        this.zipAnalyzerService = zipAnalyzerService;
    }

    @GetMapping("/api/status")
    public ResponseEntity<Map<String, String>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "status", "running",
                "application", "CodeSage AI Reviewer"
        ));
    }

    @PostMapping("/api/analyze")
    public ResponseEntity<?> analyzeCodeForm(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "file", required = false) MultipartFile file) {

        String sourceCode = null;

        if (file != null && !file.isEmpty()) {
            String fileName = file.getOriginalFilename();
            if (fileName == null || !fileName.endsWith(".java")) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Only .java files are allowed. Please upload a valid Java file."
                ));
            }
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

        if (sourceCode == null || sourceCode.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Please paste some Java code or upload a .java file before clicking Analyze."
            ));
        }

        try {
            AnalysisResult result = codeAnalyzerService.analyzeCode(sourceCode);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Something went wrong while analyzing your code. Please try again."
            ));
        }
    }

    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeCodeJson(@RequestBody AnalyzeRequest request) {
        if (request == null || request.getCode() == null || request.getCode().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Please provide Java code and try again."
            ));
        }

        List<CustomRule> customRules = request.getCustomRules();
        if (customRules == null) {
            customRules = Collections.emptyList();
        }

        try {
            AnalysisResult result = codeAnalyzerService.analyzeCode(request.getCode(), customRules);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Something went wrong while analyzing your code. Please try again."
            ));
        }
    }

    @PostMapping("/analyze-zip")
    public ResponseEntity<?> analyzeZip(@RequestParam("file") MultipartFile file) {
        try {
            var result = zipAnalyzerService.analyzeZip(file);
            if (result != null && result.containsKey("error")) {
                return ResponseEntity.badRequest().body(result);
            }
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", ex.getMessage() == null || ex.getMessage().isBlank() ? "Invalid ZIP" : ex.getMessage()
            ));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Something went wrong while analyzing your ZIP. Please try again."
            ));
        }
    }

    public static class AnalyzeRequest {
        private String code;
        private List<CustomRule> customRules;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public List<CustomRule> getCustomRules() {
            return customRules;
        }

        public void setCustomRules(List<CustomRule> customRules) {
            this.customRules = customRules;
        }
    }
}
