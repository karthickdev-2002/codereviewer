package com.codesage.service;

import com.codesage.model.CustomRule;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * In-memory custom rule manager for CodeSage.
 * Handles CRUD, validation, toggling, and test execution for runtime rules.
 */
@Service
public class CustomRuleService {

    private static final String DETECTION_REGEX = "Regex Pattern";
    private static final String DETECTION_KEYWORD = "Keyword Match";
    private static final String DETECTION_CONTAINS = "Contains Text";
    private static final String DETECTION_STARTS_WITH = "Starts With";
    private static final String DETECTION_ENDS_WITH = "Ends With";

    private final List<CustomRule> rules = new ArrayList<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public synchronized List<CustomRule> getAllRules() {
        return rules.stream()
                .map(this::copyRule)
                .collect(Collectors.toList());
    }

    public synchronized List<CustomRule> getEnabledRules() {
        return rules.stream()
                .filter(rule -> Boolean.TRUE.equals(rule.getEnabled()))
                .map(this::copyRule)
                .collect(Collectors.toList());
    }

    public synchronized CustomRule createRule(CustomRule rule) {
        validateCustomRule(rule, false);
        if (isDuplicateRuleName(rule.getRuleName(), null)) {
            throw new IllegalArgumentException("Rule name already exists. Please choose a unique name.");
        }

        rule.setId(idGenerator.getAndIncrement());
        if (rule.getEnabled() == null) {
            rule.setEnabled(Boolean.FALSE);
        }

        CustomRule saved = copyRule(rule);
        rules.add(saved);
        return copyRule(saved);
    }

    public synchronized CustomRule updateRule(CustomRule rule) {
        if (rule == null || rule.getId() == null) {
            throw new IllegalArgumentException("Rule id is required for updates.");
        }
        validateCustomRule(rule, true);
        CustomRule existing = findRuleById(rule.getId())
                .orElseThrow(() -> new IllegalArgumentException("No rule found with id " + rule.getId()));

        if (!existing.getRuleName().equalsIgnoreCase(rule.getRuleName())
                && isDuplicateRuleName(rule.getRuleName(), rule.getId())) {
            throw new IllegalArgumentException("Rule name already exists. Please choose a unique name.");
        }

        existing.setRuleName(rule.getRuleName().trim());
        existing.setDetectionType(rule.getDetectionType());
        existing.setPatternValue(rule.getPatternValue());
        existing.setDeductionScore(rule.getDeductionScore());
        existing.setIssueMessage(rule.getIssueMessage());
        existing.setSuggestionMessage(rule.getSuggestionMessage());
        existing.setEnabled(Boolean.TRUE.equals(rule.getEnabled()));

        return copyRule(existing);
    }

    public synchronized void deleteRule(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Rule id is required to delete a rule.");
        }
        boolean removed = rules.removeIf(rule -> id.equals(rule.getId()));
        if (!removed) {
            throw new IllegalArgumentException("No rule found with id " + id);
        }
    }

    public synchronized CustomRule toggleRule(Long id, Boolean enabled) {
        if (id == null) {
            throw new IllegalArgumentException("Rule id is required to toggle a rule.");
        }
        CustomRule existing = findRuleById(id)
                .orElseThrow(() -> new IllegalArgumentException("No rule found with id " + id));

        if (enabled == null) {
            existing.setEnabled(!Boolean.TRUE.equals(existing.getEnabled()));
        } else {
            existing.setEnabled(enabled);
        }
        return copyRule(existing);
    }

    public boolean validateRegex(String regex) {
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

    public boolean testRule(CustomRule rule, String sampleCode) {
        if (rule == null) {
            throw new IllegalArgumentException("Rule payload must not be null.");
        }
        if (sampleCode == null) {
            sampleCode = "";
        }
        validateCustomRule(rule, false);
        return matchesCustomRule(rule, sampleCode);
    }

    private boolean matchesCustomRule(CustomRule rule, String sampleCode) {
        String value = rule.getPatternValue().trim();
        switch (rule.getDetectionType()) {
            case DETECTION_REGEX:
                return matchesRegex(sampleCode, value);
            case DETECTION_KEYWORD:
            case DETECTION_CONTAINS:
                return sampleCode.contains(value);
            case DETECTION_STARTS_WITH:
                return anyLineMatches(sampleCode, value, true);
            case DETECTION_ENDS_WITH:
                return anyLineMatches(sampleCode, value, false);
            default:
                return false;
        }
    }

    private boolean matchesRegex(String input, String regex) {
        try {
            return Pattern.compile(regex, Pattern.MULTILINE).matcher(input).find();
        } catch (PatternSyntaxException ex) {
            return false;
        }
    }

    private boolean anyLineMatches(String content, String pattern, boolean startsWith) {
        String[] lines = content.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (startsWith && trimmed.startsWith(pattern)) {
                return true;
            }
            if (!startsWith && trimmed.endsWith(pattern)) {
                return true;
            }
        }
        return false;
    }

    private void validateCustomRule(CustomRule rule, boolean isUpdate) {
        if (rule == null) {
            throw new IllegalArgumentException("Rule payload must not be null.");
        }

        if (rule.getRuleName() == null || rule.getRuleName().trim().isEmpty()) {
            throw new IllegalArgumentException("Rule name must not be empty.");
        }

        if (rule.getDetectionType() == null || !allowedDetectionTypes().contains(rule.getDetectionType())) {
            throw new IllegalArgumentException("Invalid detection type selected.");
        }

        if (rule.getPatternValue() == null || rule.getPatternValue().trim().isEmpty()) {
            throw new IllegalArgumentException("Pattern or value must not be empty.");
        }

        if (DETECTION_REGEX.equals(rule.getDetectionType()) && !validateRegex(rule.getPatternValue())) {
            throw new IllegalArgumentException("Invalid regex pattern. Please correct the expression.");
        }

        if (rule.getDeductionScore() == null || rule.getDeductionScore() < 1 || rule.getDeductionScore() > 50) {
            throw new IllegalArgumentException("Deduction score must be between 1 and 50.");
        }

        if (rule.getIssueMessage() == null || rule.getIssueMessage().trim().isEmpty()) {
            throw new IllegalArgumentException("Issue message must not be empty.");
        }

        if (rule.getSuggestionMessage() == null || rule.getSuggestionMessage().trim().isEmpty()) {
            throw new IllegalArgumentException("Suggestion message must not be empty.");
        }

        if (isUpdate && rule.getId() == null) {
            throw new IllegalArgumentException("Rule id is required when updating a rule.");
        }
    }

    private boolean isDuplicateRuleName(String name, Long excludeId) {
        if (name == null) {
            return false;
        }
        String normalized = name.trim().toLowerCase();
        return rules.stream()
                .filter(rule -> excludeId == null || !excludeId.equals(rule.getId()))
                .anyMatch(rule -> rule.getRuleName() != null
                        && normalized.equals(rule.getRuleName().trim().toLowerCase()));
    }

    private List<String> allowedDetectionTypes() {
        return List.of(
                DETECTION_REGEX,
                DETECTION_KEYWORD,
                DETECTION_CONTAINS,
                DETECTION_STARTS_WITH,
                DETECTION_ENDS_WITH
        );
    }

    private Optional<CustomRule> findRuleById(Long id) {
        return rules.stream()
                .filter(rule -> id != null && id.equals(rule.getId()))
                .findFirst();
    }

    private CustomRule copyRule(CustomRule source) {
        if (source == null) {
            return null;
        }
        return new CustomRule(
                source.getId(),
                source.getRuleName(),
                source.getDetectionType(),
                source.getPatternValue(),
                source.getDeductionScore(),
                source.getIssueMessage(),
                source.getSuggestionMessage(),
                source.getEnabled()
        );
    }
}
