package com.codesage.controller;

import com.codesage.model.CustomRule;
import com.codesage.service.CustomRuleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.List;

/**
 * Controller exposing runtime custom rule CRUD endpoints.
 */
@RestController
@RequestMapping("/rules")
public class CustomRuleController {

    private final CustomRuleService customRuleService;

    public CustomRuleController(CustomRuleService customRuleService) {
        this.customRuleService = customRuleService;
    }

    @GetMapping("/all")
    public ResponseEntity<List<CustomRule>> getAllRules() {
        return ResponseEntity.ok(customRuleService.getAllRules());
    }

    @PostMapping("/create")
    public ResponseEntity<?> createRule(@RequestBody CustomRule rule) {
        try {
            CustomRule createdRule = customRuleService.createRule(rule);
            return ResponseEntity.ok(createdRule);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/update")
    public ResponseEntity<?> updateRule(@RequestBody CustomRule rule) {
        try {
            CustomRule updatedRule = customRuleService.updateRule(rule);
            return ResponseEntity.ok(updatedRule);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/delete")
    public ResponseEntity<?> deleteRule(@RequestBody IdRequest request) {
        try {
            customRuleService.deleteRule(request.getId());
            return ResponseEntity.ok(Map.of("message", "Rule deleted successfully."));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/toggle")
    public ResponseEntity<?> toggleRule(@RequestBody ToggleRequest request) {
        try {
            CustomRule toggled = customRuleService.toggleRule(request.getId(), request.getEnabled());
            return ResponseEntity.ok(toggled);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/test")
    public ResponseEntity<?> testRule(@RequestBody TestRuleRequest request) {
        try {
            boolean matches = customRuleService.testRule(request.getRule(), request.getSampleCode());
            return ResponseEntity.ok(Map.of(
                    "matched", matches,
                    "message", matches ? "MATCHED" : "NOT MATCHED"
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    public static class IdRequest {
        private Long id;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }
    }

    public static class ToggleRequest {
        private Long id;
        private Boolean enabled;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class TestRuleRequest {
        private CustomRule rule;
        private String sampleCode;

        public CustomRule getRule() {
            return rule;
        }

        public void setRule(CustomRule rule) {
            this.rule = rule;
        }

        public String getSampleCode() {
            return sampleCode;
        }

        public void setSampleCode(String sampleCode) {
            this.sampleCode = sampleCode;
        }
    }
}
