package com.codesage.model;

/**
 * Represents a user-defined custom analysis rule in the CodeSage application.
 */
public class CustomRule {

    private Long id;
    private String ruleName;
    private String detectionType;
    private String patternValue;
    private Integer deductionScore;
    private String issueMessage;
    private String suggestionMessage;
    private Boolean enabled;

    public CustomRule() {
        // Required for Jackson and Spring deserialization.
    }

    public CustomRule(Long id,
                      String ruleName,
                      String detectionType,
                      String patternValue,
                      Integer deductionScore,
                      String issueMessage,
                      String suggestionMessage,
                      Boolean enabled) {
        this.id = id;
        this.ruleName = ruleName;
        this.detectionType = detectionType;
        this.patternValue = patternValue;
        this.deductionScore = deductionScore;
        this.issueMessage = issueMessage;
        this.suggestionMessage = suggestionMessage;
        this.enabled = enabled;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getDetectionType() {
        return detectionType;
    }

    public void setDetectionType(String detectionType) {
        this.detectionType = detectionType;
    }

    public String getPatternValue() {
        return patternValue;
    }

    public void setPatternValue(String patternValue) {
        this.patternValue = patternValue;
    }

    public Integer getDeductionScore() {
        return deductionScore;
    }

    public void setDeductionScore(Integer deductionScore) {
        this.deductionScore = deductionScore;
    }

    public String getIssueMessage() {
        return issueMessage;
    }

    public void setIssueMessage(String issueMessage) {
        this.issueMessage = issueMessage;
    }

    public String getSuggestionMessage() {
        return suggestionMessage;
    }

    public void setSuggestionMessage(String suggestionMessage) {
        this.suggestionMessage = suggestionMessage;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String toString() {
        return "CustomRule{" +
                "id=" + id +
                ", ruleName='" + ruleName + '\'' +
                ", detectionType='" + detectionType + '\'' +
                ", patternValue='" + patternValue + '\'' +
                ", deductionScore=" + deductionScore +
                ", issueMessage='" + issueMessage + '\'' +
                ", suggestionMessage='" + suggestionMessage + '\'' +
                ", enabled=" + enabled +
                '}';
    }
}
