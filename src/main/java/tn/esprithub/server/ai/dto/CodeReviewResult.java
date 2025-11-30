package tn.esprithub.server.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeReviewResult {
    
    private boolean success;
    private String message;
    private String rawResponse;
    
    // Analyse générale
    private Integer overallScore; // 1-10
    private String summary;
    private List<String> strengths;
    
    // Problèmes identifiés
    private List<CodeIssue> issues;
    
    // Suggestions d'amélioration
    private List<CodeSuggestion> suggestions;
    
    // Recommandations spécifiques
    private List<String> securityConcerns;
    private List<String> performanceTips;
    private List<String> bestPractices;
    
    // Métadonnées
    private String analyzedLanguage;
    private String analyzedFile;
    private Long analysisTimeMs;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CodeIssue {
        private CodeIssueType type;
        private IssueSeverity severity;
        private String line;
        private String description;
        private String suggestion;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CodeSuggestion {
        private SuggestionCategory category;
        private String description;
        private SuggestionPriority priority;
    }
    
    public enum CodeIssueType {
        BUG, SECURITY, PERFORMANCE, STYLE, MAINTAINABILITY
    }
    
    public enum IssueSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    public enum SuggestionCategory {
        IMPROVEMENT, OPTIMIZATION, BEST_PRACTICE, REFACTORING, DOCUMENTATION
    }
    
    public enum SuggestionPriority {
        LOW, MEDIUM, HIGH
    }
} 