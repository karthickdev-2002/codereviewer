package com.codereview.service;

import com.codesage.model.AnalysisResult;
import com.codesage.model.CustomRule;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * Lightweight static analyzer for Java-like source ({@link #analyzeCode(String)}).
 * Uses regex and brace-aware scanning only - no external analyzers or AI.
 */
@Service
public class CodeAnalyzerService {

    private static final int INITIAL_SCORE = 100;
    private static final int MIN_SCORE = 0;

    private static final int DEDUCTION_HARDCODED_SECRETS = 20;
    private static final int DEDUCTION_PRINTLN = 10;
    private static final int DEDUCTION_DEEP_NESTED_IF = 15;
    private static final int DEDUCTION_OVERSIZED_METHOD = 10;
    private static final int DEDUCTION_POOR_NAME_EACH = 5;
    private static final int DEDUCTION_DUPLICATE_LINES = 10;

    private static final int MAX_METHOD_BODY_LINES = 25;
    private static final int MAX_ALLOWED_IF_NESTING_DEPTH = 3;

    private static final Pattern SECRET_ASSIGNMENT_PATTERN = Pattern.compile(
            "(?i)\\b(password|secret|apiKey|token)\\s*=",
            Pattern.MULTILINE
    );

    private static final Pattern PRINTLN_PATTERN = Pattern.compile(
            "System\\.out\\.println\\s*\\(",
            Pattern.MULTILINE
    );

    /** Simple names flagged as non-descriptive when used as declared identifiers. */
    private static final Set<String> POOR_NAMES = Set.of(
            "a", "b", "c", "x", "temp", "test", "data", "val", "obj"
    );

    /**
     * Local declarations: {@code Type name} or {@code var name} - captures the identifier name.
     */
    private static final Pattern LOCAL_DECLARATION_PATTERN = Pattern.compile(
            "(?m)(?:\\bvar\\b|\\b(?:boolean|byte|char|short|int|long|float|double)\\b|"
                    + "\\b(?:Boolean|Byte|Character|Short|Integer|Long|Float|Double|Number|String|Object|var)\\b"
                    + "|\\b(?:List|Map|Set|Collection|Optional|Stream)\\b(?:<[^>]+>)?"
                    + "|\\b[A-Z][\\w.]*\\b(?:<[^>]+>)?)\\s+(\\w+)\\s*(?:=|;|,)");

    /**
     * Method signature leading to a block body (best-effort for typical Java).
     */
    private static final Pattern METHOD_SIGNATURE_BLOCK_PATTERN = Pattern.compile(
            "(?m)(?:^|\\n)(?<indent>[ \\t]*)"
                    + "(?:(?:@[\\w.]++(?:\\([^)]*\\))?)[ \\t]*(?:\\r?\\n|\\z|[ \\t]))*"
                    + "(?:(?:public|private|protected|static|final|native|synchronized|abstract|strictfp|default)\\s+)*"
                    + "(?:<[\\s\\S]*?>\\s+)?"
                    + "(?:[\\w.<>,?\\[\\]]+\\s+)++"
                    + "(?<methodName>\\w+)\\s*\\([^)]*\\)\\s*(?:throws[^{]+)?\\{"
    );

    /**
     * Runs all built-in checks, aggregates score, category, issues, and suggestions.
     *
     * @param code Java source snippet or full file content; null is treated as empty
     * @return populated {@link AnalysisResult}
     */
    public AnalysisResult analyzeCode(String code) {
        String raw = code == null ? "" : code;
        List<String> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        int score = INITIAL_SCORE;

        String scanSurface = stripCommentsAndStringLiterals(raw);

        if (detectHardcodedSecrets(scanSurface, issues, suggestions)) {
            score -= DEDUCTION_HARDCODED_SECRETS;
        }
        if (detectSystemOutPrintln(scanSurface, issues, suggestions)) {
            score -= DEDUCTION_PRINTLN;
        }
        int maxIfDepth = computeMaxIfBlockNestingDepth(scanSurface);
        if (maxIfDepth > MAX_ALLOWED_IF_NESTING_DEPTH) {
            issues.add("Deep nested if statements detected (depth " + maxIfDepth + ", allowed ≤ "
                    + MAX_ALLOWED_IF_NESTING_DEPTH + ")");
            suggestions.add("Refactor into smaller methods");
            score -= DEDUCTION_DEEP_NESTED_IF;
        }
        int oversizedMethods = detectOversizedMethods(scanSurface, issues, suggestions);
        score -= oversizedMethods * DEDUCTION_OVERSIZED_METHOD;

        int poorNames = countPoorVariableNames(scanSurface, issues, suggestions);
        score -= poorNames * DEDUCTION_POOR_NAME_EACH;

        if (detectDuplicateNonEmptyLines(raw, issues, suggestions)) {
            score -= DEDUCTION_DUPLICATE_LINES;
        }

        score = clamp(score, MIN_SCORE, INITIAL_SCORE);
        String category = resolveScoreCategory(score);
        return new AnalysisResult(score, category, issues, suggestions);
    }

    public AnalysisResult analyzeCode(String code, List<CustomRule> customRules) {
        AnalysisResult baseResult = analyzeCode(code);
        if (customRules == null || customRules.isEmpty()) {
            return baseResult;
        }

        List<CustomRule> enabledRules = customRules.stream()
                .filter(Objects::nonNull)
                .filter(rule -> Boolean.TRUE.equals(rule.getEnabled()))
                .collect(Collectors.toList());

        if (enabledRules.isEmpty()) {
            return baseResult;
        }

        List<String> issues = new ArrayList<>(baseResult.getIssues());
        List<String> suggestions = new ArrayList<>(baseResult.getSuggestions());
        int score = baseResult.getScore();

        for (CustomRule rule : enabledRules) {
            if (!isCustomRuleValid(rule)) {
                continue;
            }
            if (matchesCustomRule(code, rule)) {
                score -= rule.getDeductionScore();
                issues.add("[CUSTOM RULE] " + rule.getRuleName() + " - " + rule.getIssueMessage());
                suggestions.add(rule.getSuggestionMessage());
            }
        }

        score = clamp(score, MIN_SCORE, INITIAL_SCORE);
        String category = resolveScoreCategory(score);
        return new AnalysisResult(score, category, issues, suggestions);
    }

    private boolean isCustomRuleValid(CustomRule rule) {
        if (rule == null
                || rule.getRuleName() == null || rule.getRuleName().trim().isEmpty()
                || rule.getDetectionType() == null
                || rule.getPatternValue() == null || rule.getPatternValue().trim().isEmpty()
                || rule.getIssueMessage() == null || rule.getIssueMessage().trim().isEmpty()
                || rule.getSuggestionMessage() == null || rule.getSuggestionMessage().trim().isEmpty()
                || rule.getDeductionScore() == null
                || rule.getDeductionScore() < 1 || rule.getDeductionScore() > 50) {
            return false;
        }

        if ("Regex Pattern".equals(rule.getDetectionType()) && !isRegexValid(rule.getPatternValue())) {
            return false;
        }

        return switch (rule.getDetectionType()) {
            case "Regex Pattern",
                    "Keyword Match",
                    "Contains Text",
                    "Starts With",
                    "Ends With" -> true;
            default -> false;
        };
    }

    private boolean matchesCustomRule(String code, CustomRule rule) {
        String pattern = rule.getPatternValue().trim();
        return switch (rule.getDetectionType()) {
            case "Regex Pattern" -> matchesRegex(code, pattern);
            case "Keyword Match", "Contains Text" -> code.contains(pattern);
            case "Starts With" -> anyLineMatches(code, pattern, true);
            case "Ends With" -> anyLineMatches(code, pattern, false);
            default -> false;
        };
    }

    private boolean matchesRegex(String code, String pattern) {
        try {
            return Pattern.compile(pattern, Pattern.MULTILINE).matcher(code).find();
        } catch (PatternSyntaxException ex) {
            return false;
        }
    }

    private boolean anyLineMatches(String code, String pattern, boolean startsWith) {
        String[] lines = code.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (startsWith && trimmed.startsWith(pattern)) {
                return true;
            }
            if (!startsWith && trimmed.endsWith(pattern)) {
                return true;
            }
        }
        return false;
    }

    private boolean isRegexValid(String regex) {
        if (regex == null || regex.trim().isEmpty()) {
            return false;
        }
        try {
            Pattern.compile(regex, Pattern.MULTILINE);
            return true;
        } catch (PatternSyntaxException ex) {
            return false;
        }
    }

    private boolean detectHardcodedSecrets(String code, List<String> issues, List<String> suggestions) {
        if (!SECRET_ASSIGNMENT_PATTERN.matcher(code).find()) {
            return false;
        }
        issues.add("Possible hardcoded secret (password / secret / apiKey / token assignment)");
        suggestions.add("Move secrets to environment variables or config vault");
        return true;
    }

    private boolean detectSystemOutPrintln(String code, List<String> issues, List<String> suggestions) {
        if (!PRINTLN_PATTERN.matcher(code).find()) {
            return false;
        }
        issues.add("System.out.println usage detected");
        suggestions.add("Use Logger framework instead");
        return true;
    }

    /**
     * Estimates maximum nesting of braced {@code if} / {@code else if} bodies using a small state machine.
     */
    private int computeMaxIfBlockNestingDepth(String code) {
        int maxDepth = 0;
        int currentDepth = 0;
        int i = 0;
        final int n = code.length();

        Deque<Boolean> openedByIf = new ArrayDeque<>();

        while (i < n) {
            char c = code.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            if (startsWithElseIf(code, i)) {
                i = skipElseIfHeader(code, i);
                i = skipWhitespace(code, i);
                if (i < n && code.charAt(i) == '{') {
                    currentDepth++;
                    maxDepth = Math.max(maxDepth, currentDepth);
                    openedByIf.push(true);
                    i++;
                } else {
                    i = skipStatementWithoutBraces(code, i);
                    while (i < n && Character.isWhitespace(code.charAt(i))) {
                        i++;
                    }
                }
                continue;
            }

            if (startsWithKeywordIf(code, i)) {
                i = skipIfHeader(code, i);
                i = skipWhitespace(code, i);
                if (i < n && code.charAt(i) == '{') {
                    currentDepth++;
                    maxDepth = Math.max(maxDepth, currentDepth);
                    openedByIf.push(true);
                    i++;
                } else {
                    i = skipStatementWithoutBraces(code, i);
                }
                continue;
            }

            if (c == '{') {
                openedByIf.push(false);
                i++;
                continue;
            }

            if (c == '}') {
                if (!openedByIf.isEmpty()) {
                    boolean fromIf = openedByIf.pop();
                    if (fromIf) {
                        currentDepth = Math.max(0, currentDepth - 1);
                    }
                }
                i++;
                continue;
            }

            i++;
        }

        return maxDepth;
    }

    private boolean startsWithKeywordIf(String s, int idx) {
        if (!regionMatchesKeyword(s, idx, "if")) {
            return false;
        }
        int after = idx + 2;
        return after >= s.length() || !Character.isJavaIdentifierPart(s.charAt(after));
    }

    private boolean startsWithElseIf(String s, int idx) {
        if (!regionMatchesKeyword(s, idx, "else")) {
            return false;
        }
        int j = idx + 4;
        j = skipWhitespace(s, j);
        return regionMatchesKeyword(s, j, "if");
    }

    private boolean regionMatchesKeyword(String s, int idx, String kw) {
        if (idx + kw.length() > s.length()) {
            return false;
        }
        if (!s.regionMatches(true, idx, kw, 0, kw.length())) {
            return false;
        }
        int before = idx - 1;
        int after = idx + kw.length();
        if (before >= 0 && Character.isJavaIdentifierPart(s.charAt(before))) {
            return false;
        }
        return after >= s.length() || !Character.isJavaIdentifierPart(s.charAt(after));
    }

    private int skipWhitespace(String s, int i) {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }

    private int skipIfHeader(String s, int i) {
        i += 2;
        i = skipWhitespace(s, i);
        if (i >= s.length() || s.charAt(i) != '(') {
            return i;
        }
        return skipBalanced(s, i, '(', ')') + 1;
    }

    private int skipElseIfHeader(String s, int i) {
        i += 4;
        i = skipWhitespace(s, i);
        i += 2;
        i = skipWhitespace(s, i);
        if (i < s.length() && s.charAt(i) == '(') {
            i = skipBalanced(s, i, '(', ')') + 1;
        }
        return i;
    }

    private int skipBalanced(String s, int openIdx, char open, char close) {
        int depth = 0;
        for (int k = openIdx; k < s.length(); k++) {
            char ch = s.charAt(k);
            if (ch == open) {
                depth++;
            } else if (ch == close) {
                depth--;
                if (depth == 0) {
                    return k;
                }
            }
        }
        return s.length() - 1;
    }

    /**
     * Advances past a simple statement when {@code if} has no braces (until {@code ;} at depth 0).
     */
    private int skipStatementWithoutBraces(String s, int i) {
        int depthParen = 0;
        int depthBracket = 0;
        int depthAngle = 0;
        while (i < s.length()) {
            char ch = s.charAt(i);
            if (ch == '(') {
                depthParen++;
            } else if (ch == ')') {
                depthParen = Math.max(0, depthParen - 1);
            } else if (ch == '[') {
                depthBracket++;
            } else if (ch == ']') {
                depthBracket = Math.max(0, depthBracket - 1);
            } else if (ch == '<') {
                depthAngle++;
            } else if (ch == '>') {
                depthAngle = Math.max(0, depthAngle - 1);
            } else if (ch == ';' && depthParen == 0 && depthBracket == 0) {
                return i + 1;
            } else if (ch == '{' && depthParen == 0 && depthBracket == 0) {
                return i;
            }
            i++;
        }
        return i;
    }

    /**
     * @return number of methods whose body exceeds {@link #MAX_METHOD_BODY_LINES}
     */
    private int detectOversizedMethods(String scanSurface, List<String> issues, List<String> suggestions) {
        Matcher m = METHOD_SIGNATURE_BLOCK_PATTERN.matcher(scanSurface);
        int count = 0;
        while (m.find()) {
            int braceIndex = m.end() - 1;
            int bodyStartLine = lineNumberAtIndex(scanSurface, braceIndex);
            int closing = findMatchingBrace(scanSurface, braceIndex);
            if (closing < 0) {
                continue;
            }
            int bodyEndLine = lineNumberAtIndex(scanSurface, closing);
            int lines = bodyEndLine - bodyStartLine;
            if (lines > MAX_METHOD_BODY_LINES) {
                count++;
                String methodName = m.group("methodName");
                issues.add("Method '" + methodName + "' body spans " + lines + " lines (limit "
                        + MAX_METHOD_BODY_LINES + ")");
                suggestions.add("Split into reusable methods");
            }
        }
        return count;
    }

    private int lineNumberAtIndex(String code, int index) {
        if (code.isEmpty()) {
            return 1;
        }
        int clamped = Math.max(0, Math.min(index, code.length() - 1));
        int line = 1;
        for (int k = 0; k < clamped; k++) {
            if (code.charAt(k) == '\n') {
                line++;
            }
        }
        return line;
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

    private int countPoorVariableNames(String code, List<String> issues, List<String> suggestions) {
        Matcher m = LOCAL_DECLARATION_PATTERN.matcher(code);
        int hits = 0;
        while (m.find()) {
            String name = m.group(1);
            if (name != null && POOR_NAMES.contains(name.toLowerCase(Locale.ROOT))) {
                hits++;
                issues.add("Poor variable name '" + name + "' hurts readability");
                suggestions.add("Use descriptive variable names");
            }
        }
        return hits;
    }

    private boolean detectDuplicateNonEmptyLines(String rawCode, List<String> issues, List<String> suggestions) {
        String[] lines = rawCode.split("\\R");
        Map<String, Integer> freq = new HashMap<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            freq.merge(trimmed, 1, Integer::sum);
        }
        boolean found = freq.values().stream().anyMatch(c -> c > 1);
        if (!found) {
            return false;
        }
        issues.add("Duplicate non-empty lines detected");
        suggestions.add("Extract repeated logic");
        return true;
    }

    /**
     * Removes line comments, block comments, and string/char literals so regex scans ignore literals.
     */
    private String stripCommentsAndStringLiterals(String code) {
        StringBuilder out = new StringBuilder(code.length());
        int i = 0;
        final int n = code.length();

        while (i < n) {
            char c = code.charAt(i);

            if (c == '/' && i + 1 < n) {
                char next = code.charAt(i + 1);
                if (next == '/') {
                    i += 2;
                    while (i < n && code.charAt(i) != '\n') {
                        i++;
                    }
                    continue;
                }
                if (next == '*') {
                    i += 2;
                    while (i + 1 < n && !(code.charAt(i) == '*' && code.charAt(i + 1) == '/')) {
                        i++;
                    }
                    i = Math.min(n, i + 2);
                    continue;
                }
            }

            if (c == '"') {
                out.append(c);
                i++;
                while (i < n) {
                    char ch = code.charAt(i);
                    out.append(' ');
                    if (ch == '\\' && i + 1 < n) {
                        i += 2;
                        continue;
                    }
                    if (ch == '"') {
                        i++;
                        break;
                    }
                    i++;
                }
                continue;
            }

            if (c == '\'') {
                out.append(c);
                i++;
                while (i < n) {
                    char ch = code.charAt(i);
                    out.append(' ');
                    if (ch == '\\' && i + 1 < n) {
                        i += 2;
                        continue;
                    }
                    if (ch == '\'') {
                        i++;
                        break;
                    }
                    i++;
                }
                continue;
            }

            out.append(c);
            i++;
        }

        return out.toString();
    }

    private String resolveScoreCategory(int score) {
        if (score >= 90) {
            return "Excellent";
        }
        if (score >= 70) {
            return "Good";
        }
        if (score >= 50) {
            return "Average";
        }
        return "Poor";
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
