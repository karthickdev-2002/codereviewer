package com.codereview.service;

import com.codereview.model.FileAnalysisResult;
import com.codereview.model.MethodAnalysisResult;
import com.codesage.model.AnalysisResult;
import com.codesage.model.CustomRule;
import com.codesage.service.CustomRuleService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class ZipAnalyzerService {

    private static final long MAX_ZIP_BYTES = 50L * 1024L * 1024L; // 50MB
    private static final long MAX_TOTAL_UNZIPPED_BYTES = 250L * 1024L * 1024L; // zip-bomb guard
    private static final int MAX_FILES_EXTRACTED = 10_000;

    private static final Pattern CLASS_PATTERN = Pattern.compile("(?m)\\b(class|interface|enum)\\s+\\w+");

    private static final Pattern METHOD_SIGNATURE_BLOCK_PATTERN = Pattern.compile(
            "(?m)(?:^|\\n)(?<indent>[ \\t]*)"
                    + "(?:(?:@[\\w.]++(?:\\([^)]*\\))?)[ \\t]*(?:\\r?\\n|\\z|[ \\t]))*"
                    + "(?:(?:public|private|protected|static|final|native|synchronized|abstract|strictfp|default)\\s+)*"
                    + "(?:<[\\s\\S]*?>\\s+)?"
                    + "(?:[\\w.<>,?\\[\\]]+\\s+)++"
                    + "(?<methodName>\\w+)\\s*\\([^)]*\\)\\s*(?:throws[^{]+)?\\{"
    );

    private static final Pattern COMPLEXITY_TOKENS = Pattern.compile(
            "(?i)\\b(if|for|while|case|catch|throw|&&|\\|\\||\\?)\\b"
    );

    private final CodeAnalyzerService codeAnalyzerService;
    private final CustomRuleService customRuleService;

    public ZipAnalyzerService(CodeAnalyzerService codeAnalyzerService, CustomRuleService customRuleService) {
        this.codeAnalyzerService = codeAnalyzerService;
        this.customRuleService = customRuleService;
    }

    public Map<String, Object> analyzeZip(MultipartFile zipFile) {
        validateZip(zipFile);

        Path extractionRoot = null;
        try {
            extractionRoot = Files.createTempDirectory("codesage-zip-" + UUID.randomUUID());
            Path projectRoot = extractZip(zipFile, extractionRoot);

            List<Path> javaFiles = findJavaFiles(projectRoot);
            if (javaFiles.isEmpty()) {
                return Map.of(
                        "error", "No Java Files Found"
                );
            }

            List<CustomRule> enabledRules = Optional.ofNullable(customRuleService.getEnabledRules())
                    .orElseGet(List::of);

            List<FileAnalysisResult> fileResults = new ArrayList<>();
            int totalIssues = 0;
            int totalFunctions = 0;
            int totalClasses = 0;
            int totalScore = 0;

            for (Path javaFile : javaFiles) {
                String content = Files.readString(javaFile, StandardCharsets.UTF_8);
                AnalysisResult base = enabledRules.isEmpty()
                        ? codeAnalyzerService.analyzeCode(content)
                        : codeAnalyzerService.analyzeCode(content, enabledRules);

                List<MethodAnalysisResult> methods = analyzeMethods(content);
                totalFunctions += methods.size();

                int classCount = countMatches(CLASS_PATTERN, content);
                totalClasses += classCount;

                FileAnalysisResult fileResult = new FileAnalysisResult(
                        javaFile.getFileName().toString(),
                        normalizeRelPath(projectRoot, javaFile),
                        base.getScore(),
                        base.getScoreCategory(),
                        base.getIssues(),
                        base.getSuggestions(),
                        methods
                );
                fileResults.add(fileResult);

                totalScore += base.getScore();
                totalIssues += safeSize(base.getIssues());
                for (MethodAnalysisResult mr : methods) {
                    totalIssues += safeSize(mr.getIssues());
                }
            }

            fileResults.sort(Comparator.comparing(FileAnalysisResult::getRelativePath, String.CASE_INSENSITIVE_ORDER));

            int avgScore = (int) Math.round(totalScore / (double) Math.max(1, fileResults.size()));
            String projectCategory = codeAnalyzerService.scoreCategoryFor(avgScore);

            String projectName = resolveProjectName(zipFile);

            Map<String, Object> projectLevel = new HashMap<>();
            projectLevel.put("projectName", projectName);
            projectLevel.put("totalJavaFiles", fileResults.size());
            projectLevel.put("totalClasses", totalClasses);
            projectLevel.put("totalFunctions", totalFunctions);
            projectLevel.put("totalIssues", totalIssues);
            projectLevel.put("averageProjectScore", avgScore);
            projectLevel.put("overallProjectCategory", projectCategory);
            projectLevel.put("generatedAt", Instant.now().toString());

            return Map.of(
                    "project", projectLevel,
                    "files", fileResults
            );
        } catch (IOException e) {
            return Map.of("error", "Invalid ZIP");
        } finally {
            if (extractionRoot != null) {
                cleanup(extractionRoot);
            }
        }
    }

    private void validateZip(MultipartFile zipFile) {
        if (zipFile == null || zipFile.isEmpty()) {
            throw new IllegalArgumentException("Invalid ZIP");
        }
        if (zipFile.getSize() > MAX_ZIP_BYTES) {
            throw new IllegalArgumentException("Huge upload abuse");
        }

        String name = zipFile.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new IllegalArgumentException("Invalid ZIP");
        }

        try (InputStream is = new BufferedInputStream(Objects.requireNonNull(zipFile.getInputStream()))) {
            is.mark(4);
            byte[] header = is.readNBytes(4);
            is.reset();
            if (header.length < 2 || header[0] != 'P' || header[1] != 'K') {
                throw new IllegalArgumentException("Invalid ZIP");
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("Invalid ZIP");
        }
    }

    public Path extractZip(MultipartFile zipFile, Path destinationDir) throws IOException {
        if (destinationDir == null) {
            throw new IOException("Invalid ZIP");
        }
        Files.createDirectories(destinationDir);

        long unzippedBytes = 0L;
        int extractedFiles = 0;

        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }

                extractedFiles++;
                if (extractedFiles > MAX_FILES_EXTRACTED) {
                    throw new IOException("Invalid ZIP");
                }

                Path outPath = destinationDir.resolve(entry.getName()).normalize();
                if (!outPath.startsWith(destinationDir.normalize())) {
                    throw new IOException("Invalid ZIP");
                }

                Files.createDirectories(outPath.getParent());

                try (var os = Files.newOutputStream(outPath)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = zis.read(buffer)) > 0) {
                        os.write(buffer, 0, read);
                        unzippedBytes += read;
                        if (unzippedBytes > MAX_TOTAL_UNZIPPED_BYTES) {
                            throw new IOException("Invalid ZIP");
                        }
                    }
                }
                zis.closeEntry();
            }
        }

        return destinationDir;
    }

    public List<Path> findJavaFiles(Path rootDir) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walkFileTree(rootDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file != null && file.getFileName() != null) {
                    String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (name.endsWith(".java")) {
                        javaFiles.add(file);
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return javaFiles;
    }

    public List<MethodAnalysisResult> analyzeMethods(String fileContent) {
        if (fileContent == null || fileContent.isBlank()) {
            return List.of();
        }

        List<MethodBlock> blocks = new ArrayList<>();
        Matcher m = METHOD_SIGNATURE_BLOCK_PATTERN.matcher(fileContent);
        while (m.find()) {
            int openBraceIndex = m.end() - 1;
            int closeBraceIndex = findMatchingBrace(fileContent, openBraceIndex);
            if (closeBraceIndex < 0) {
                continue;
            }
            String methodName = m.group("methodName");
            blocks.add(new MethodBlock(methodName, openBraceIndex, closeBraceIndex));
        }

        Map<String, Integer> normalizedBodyFreq = new HashMap<>();
        for (MethodBlock b : blocks) {
            String normalized = normalizeMethodBody(fileContent.substring(b.openBraceIndex + 1, b.closeBraceIndex));
            if (normalized.length() >= 120) {
                normalizedBodyFreq.merge(normalized, 1, Integer::sum);
            }
        }

        List<MethodAnalysisResult> results = new ArrayList<>();
        for (MethodBlock b : blocks) {
            String body = fileContent.substring(b.openBraceIndex + 1, b.closeBraceIndex);
            int lineCount = countLines(body);
            int nestedDepth = computeMaxBraceDepth(body);
            int complexity = estimateComplexity(body);

            List<String> issues = new ArrayList<>();
            List<String> suggestions = new ArrayList<>();

            int score = 100;
            if (lineCount > 40) {
                issues.add("Method is long (" + lineCount + " lines)");
                suggestions.add("Split into smaller methods");
                score -= 15;
            } else if (lineCount > 25) {
                issues.add("Method is moderately long (" + lineCount + " lines)");
                suggestions.add("Consider refactoring for readability");
                score -= 8;
            }

            if (nestedDepth > 4) {
                issues.add("Deep nesting detected (depth " + nestedDepth + ")");
                suggestions.add("Reduce nesting using guard clauses / early returns");
                score -= 15;
            } else if (nestedDepth > 3) {
                issues.add("Nesting is high (depth " + nestedDepth + ")");
                suggestions.add("Refactor conditional blocks");
                score -= 8;
            }

            if (complexity > 12) {
                issues.add("High complexity detected (complexity " + complexity + ")");
                suggestions.add("Simplify conditions and extract helpers");
                score -= 15;
            } else if (complexity > 8) {
                issues.add("Moderate complexity detected (complexity " + complexity + ")");
                suggestions.add("Break complex logic into helper methods");
                score -= 8;
            }

            String normalized = normalizeMethodBody(body);
            if (normalized.length() >= 120 && normalizedBodyFreq.getOrDefault(normalized, 0) > 1) {
                issues.add("Possible duplicate logic detected");
                suggestions.add("Extract repeated logic into a shared method");
                score -= 10;
            }

            score = clamp(score, 0, 100);

            MethodAnalysisResult r = new MethodAnalysisResult(
                    b.methodName,
                    lineCount,
                    score,
                    nestedDepth,
                    issues,
                    suggestions
            );
            r.getIssues().add("Complexity score: " + complexity);
            results.add(r);
        }

        results.sort(Comparator.comparing(MethodAnalysisResult::getMethodName, String.CASE_INSENSITIVE_ORDER));
        return results;
    }

    public void cleanup(Path extractionRoot) {
        if (extractionRoot == null) {
            return;
        }
        try {
            if (!Files.exists(extractionRoot)) {
                return;
            }
            Files.walk(extractionRoot)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private String resolveProjectName(MultipartFile zipFile) {
        String name = zipFile.getOriginalFilename();
        if (name == null || name.isBlank()) {
            return "Java Project";
        }
        String trimmed = name.trim();
        if (trimmed.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            trimmed = trimmed.substring(0, trimmed.length() - 4);
        }
        return trimmed.isBlank() ? "Java Project" : trimmed;
    }

    private String normalizeRelPath(Path root, Path file) {
        try {
            return root.relativize(file).toString().replace('\\', '/');
        } catch (Exception ex) {
            return file.getFileName().toString();
        }
    }

    private int countMatches(Pattern p, String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        int count = 0;
        Matcher m = p.matcher(s);
        while (m.find()) {
            count++;
        }
        return count;
    }

    private int safeSize(List<String> list) {
        return list == null ? 0 : list.size();
    }

    private int estimateComplexity(String body) {
        if (body == null || body.isBlank()) {
            return 1;
        }
        int tokens = 0;
        Matcher m = COMPLEXITY_TOKENS.matcher(body);
        while (m.find()) {
            tokens++;
        }
        return 1 + tokens;
    }

    private int countLines(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        int lines = 1;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    private int computeMaxBraceDepth(String body) {
        int depth = 0;
        int max = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '{') {
                depth++;
                max = Math.max(max, depth);
            } else if (c == '}') {
                depth = Math.max(0, depth - 1);
            }
        }
        return max;
    }

    private int findMatchingBrace(String s, int openBraceIndex) {
        if (openBraceIndex < 0 || openBraceIndex >= s.length() || s.charAt(openBraceIndex) != '{') {
            return -1;
        }
        int depth = 0;
        for (int k = openBraceIndex; k < s.length(); k++) {
            char ch = s.charAt(k);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return k;
                }
            }
        }
        return -1;
    }

    private String normalizeMethodBody(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*?$", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static class MethodBlock {
        final String methodName;
        final int openBraceIndex;
        final int closeBraceIndex;

        MethodBlock(String methodName, int openBraceIndex, int closeBraceIndex) {
            this.methodName = methodName == null ? "unknownMethod" : methodName;
            this.openBraceIndex = openBraceIndex;
            this.closeBraceIndex = closeBraceIndex;
        }
    }
}

