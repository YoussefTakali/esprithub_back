package tn.esprithub.server.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import tn.esprithub.server.ai.dto.CodeReviewResult;

@Service
@RequiredArgsConstructor
@Slf4j
public class CodeReviewService {

    @Value("${app.ai.openai.api-key:}")
    private String openaiApiKey;

    @Value("${app.ai.openai.model:gpt-3.5-turbo}")
    private String openaiModel;

    @Value("${app.ai.code-review.test-mode:false}")
    private boolean testMode;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    /**
     * Analyse le code et fournit des suggestions d'amélioration
     */
    public CodeReviewResult analyzeCode(String code, String language, String context) {
        if ((openaiApiKey == null || openaiApiKey.trim().isEmpty()) && !testMode) {
            log.warn("OpenAI API key not configured, skipping code analysis");
            return CodeReviewResult.builder()
                    .success(false)
                    .message("AI analysis not available - API key not configured")
                    .build();
        }

        // Mode de test - simulation des réponses IA
        if (testMode || openaiApiKey == null || openaiApiKey.trim().isEmpty()) {
            log.info("Using test mode for code analysis");
            return generateTestResponse(code, language, context);
        }

        try {
            String prompt = buildCodeReviewPrompt(code, language, context);
            String analysis = callOpenAI(prompt);
            
            return parseCodeReviewResponse(analysis);
            
        } catch (Exception e) {
            log.error("Error analyzing code with AI", e);
            
            // Si c'est un rate limit, essayer le mode de test
            if (e.getMessage() != null && e.getMessage().contains("Rate limit")) {
                log.info("Falling back to test mode due to rate limit");
                return generateTestResponse(code, language, context);
            }
            
            return CodeReviewResult.builder()
                    .success(false)
                    .message("Error analyzing code: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Analyse un diff de code (pour les pull requests)
     */
    public CodeReviewResult analyzeDiff(String diff, String language) {
        if (openaiApiKey == null || openaiApiKey.trim().isEmpty()) {
            log.warn("OpenAI API key not configured, skipping diff analysis");
            return CodeReviewResult.builder()
                    .success(false)
                    .message("AI analysis not available - API key not configured")
                    .build();
        }

        try {
            String prompt = buildDiffReviewPrompt(diff, language);
            String analysis = callOpenAI(prompt);
            
            return parseCodeReviewResponse(analysis);
            
        } catch (Exception e) {
            log.error("Error analyzing diff with AI", e);
            return CodeReviewResult.builder()
                    .success(false)
                    .message("Error analyzing diff: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Analyse un fichier complet
     */
    public CodeReviewResult analyzeFile(String fileName, String fileContent, String language) {
        if (openaiApiKey == null || openaiApiKey.trim().isEmpty()) {
            log.warn("OpenAI API key not configured, skipping file analysis");
            return CodeReviewResult.builder()
                    .success(false)
                    .message("AI analysis not available - API key not configured")
                    .build();
        }

        try {
            String prompt = buildFileReviewPrompt(fileName, fileContent, language);
            String analysis = callOpenAI(prompt);
            
            return parseCodeReviewResponse(analysis);
            
        } catch (Exception e) {
            log.error("Error analyzing file with AI", e);
            return CodeReviewResult.builder()
                    .success(false)
                    .message("Error analyzing file: " + e.getMessage())
                    .build();
        }
    }

    private String callOpenAI(String prompt) {
        // Ajouter un délai pour éviter les rate limits
        try {
            Thread.sleep(2000); // 2 secondes de délai
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", openaiModel);
        requestBody.put("messages", List.of(Map.of(
            "role", "system",
            "content", "You are an expert code reviewer. Provide clear, actionable feedback in JSON format."
        ), Map.of(
            "role", "user",
            "content", prompt
        )));
        requestBody.put("temperature", 0.3);
        requestBody.put("max_tokens", 2000);

        Map<String, Object> response = webClient.post()
                .uri("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer " + openaiApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(status -> status.value() == 429, 
                    error -> {
                        log.warn("Rate limit exceeded, falling back to test mode");
                        return Mono.error(new RuntimeException("Rate limit exceeded - please wait a moment and try again"));
                    })
                .bodyToMono(Map.class)
                .block();

        if (response != null && response.containsKey("choices")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (!choices.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                return (String) message.get("content");
            }
        }

        throw new RuntimeException("Invalid response from OpenAI API");
    }

    private String buildCodeReviewPrompt(String code, String language, String context) {
        return String.format("""
            Analyze the following %s code and provide a comprehensive code review in JSON format.
            
            Context: %s
            
            Code:
            ```%s
            %s
            ```
            
            Please provide your analysis in the following JSON format:
            {
                "overallScore": 1-10,
                "summary": "Brief summary of the code quality",
                "strengths": ["list", "of", "strengths"],
                "issues": [
                    {
                        "type": "BUG|SECURITY|PERFORMANCE|STYLE|MAINTAINABILITY",
                        "severity": "LOW|MEDIUM|HIGH|CRITICAL",
                        "line": "line number or range",
                        "description": "description of the issue",
                        "suggestion": "how to fix it"
                    }
                ],
                "suggestions": [
                    {
                        "category": "IMPROVEMENT|OPTIMIZATION|BEST_PRACTICE",
                        "description": "suggestion description",
                        "priority": "LOW|MEDIUM|HIGH"
                    }
                ],
                "securityConcerns": ["list", "of", "security", "issues"],
                "performanceTips": ["list", "of", "performance", "tips"],
                "bestPractices": ["list", "of", "best", "practices", "to", "follow"]
            }
            
            Focus on:
            - Code quality and readability
            - Potential bugs and security issues
            - Performance optimizations
            - Best practices for %s
            - Maintainability and scalability
            """, language, context, language, code, language);
    }

    private String buildDiffReviewPrompt(String diff, String language) {
        return String.format("""
            Analyze the following code diff and provide a comprehensive review in JSON format.
            
            Diff:
            ```diff
            %s
            ```
            
            Please provide your analysis in the following JSON format:
            {
                "overallScore": 1-10,
                "summary": "Brief summary of the changes",
                "positiveChanges": ["list", "of", "good", "changes"],
                "concerns": [
                    {
                        "type": "BUG|SECURITY|PERFORMANCE|STYLE|MAINTAINABILITY",
                        "severity": "LOW|MEDIUM|HIGH|CRITICAL",
                        "description": "description of the concern",
                        "suggestion": "how to address it"
                    }
                ],
                "suggestions": [
                    {
                        "category": "IMPROVEMENT|OPTIMIZATION|BEST_PRACTICE",
                        "description": "suggestion description",
                        "priority": "LOW|MEDIUM|HIGH"
                    }
                ],
                "breakingChanges": ["list", "of", "potential", "breaking", "changes"],
                "testingRecommendations": ["list", "of", "tests", "to", "add"]
            }
            
            Focus on:
            - Impact of the changes
            - Potential regressions
            - Code quality improvements
            - Security implications
            - Performance impact
            """, diff);
    }

    private String buildFileReviewPrompt(String fileName, String fileContent, String language) {
        return String.format("""
            Analyze the following %s file and provide a comprehensive code review in JSON format.
            
            File: %s
            
            Code:
            ```%s
            %s
            ```
            
            Please provide your analysis in the following JSON format:
            {
                "overallScore": 1-10,
                "summary": "Brief summary of the file quality",
                "architecture": "assessment of the code architecture",
                "strengths": ["list", "of", "strengths"],
                "issues": [
                    {
                        "type": "BUG|SECURITY|PERFORMANCE|STYLE|MAINTAINABILITY",
                        "severity": "LOW|MEDIUM|HIGH|CRITICAL",
                        "line": "line number or range",
                        "description": "description of the issue",
                        "suggestion": "how to fix it"
                    }
                ],
                "suggestions": [
                    {
                        "category": "IMPROVEMENT|OPTIMIZATION|BEST_PRACTICE",
                        "description": "suggestion description",
                        "priority": "LOW|MEDIUM|HIGH"
                    }
                ],
                "refactoringOpportunities": ["list", "of", "refactoring", "opportunities"],
                "documentationNeeds": ["list", "of", "documentation", "needs"],
                "testCoverage": "assessment of test coverage needs"
            }
            
            Focus on:
            - Overall file structure and organization
            - Code quality and maintainability
            - Potential improvements
            - Best practices for %s
            - Documentation and testing needs
            """, language, fileName, language, fileContent, language);
    }

    private CodeReviewResult parseCodeReviewResponse(String response) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> analysis = objectMapper.readValue(response, Map.class);
            
            return CodeReviewResult.builder()
                    .success(true)
                    .overallScore((Integer) analysis.get("overallScore"))
                    .summary((String) analysis.get("summary"))
                    .strengths((List<String>) analysis.get("strengths"))
                    .issues(parseIssues((List<Map<String, Object>>) analysis.get("issues")))
                    .suggestions(parseSuggestions((List<Map<String, Object>>) analysis.get("suggestions")))
                    .securityConcerns((List<String>) analysis.get("securityConcerns"))
                    .performanceTips((List<String>) analysis.get("performanceTips"))
                    .bestPractices((List<String>) analysis.get("bestPractices"))
                    .build();
                    
        } catch (Exception e) {
            log.error("Error parsing AI response", e);
            return CodeReviewResult.builder()
                    .success(false)
                    .message("Error parsing AI response: " + e.getMessage())
                    .rawResponse(response)
                    .build();
        }
    }

    private List<CodeReviewResult.CodeIssue> parseIssues(List<Map<String, Object>> issuesData) {
        if (issuesData == null) return List.of();
        
        return issuesData.stream()
                .map(issue -> CodeReviewResult.CodeIssue.builder()
                        .type(CodeReviewResult.CodeIssueType.valueOf((String) issue.get("type")))
                        .severity(CodeReviewResult.IssueSeverity.valueOf((String) issue.get("severity")))
                        .line((String) issue.get("line"))
                        .description((String) issue.get("description"))
                        .suggestion((String) issue.get("suggestion"))
                        .build())
                .toList();
    }

    private List<CodeReviewResult.CodeSuggestion> parseSuggestions(List<Map<String, Object>> suggestionsData) {
        if (suggestionsData == null) return List.of();
        
        return suggestionsData.stream()
                .map(suggestion -> CodeReviewResult.CodeSuggestion.builder()
                        .category(CodeReviewResult.SuggestionCategory.valueOf((String) suggestion.get("category")))
                        .description((String) suggestion.get("description"))
                        .priority(CodeReviewResult.SuggestionPriority.valueOf((String) suggestion.get("priority")))
                        .build())
                .toList();
    }

    /**
     * Génère une réponse de test pour simuler l'analyse IA
     */
    private CodeReviewResult generateTestResponse(String code, String language, String context) {
        // Simuler un délai d'analyse
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Analyser le code pour détecter des patterns simples
        boolean hasComments = code.contains("//") || code.contains("/*") || code.contains("*");
        boolean hasErrorHandling = code.contains("try") || code.contains("catch") || code.contains("if");
        boolean hasLongLines = code.lines().anyMatch(line -> line.length() > 80);
        boolean hasMagicNumbers = code.matches(".*\\b\\d{3,}\\b.*");
        
        int score = 7; // Score de base
        if (hasComments) score++;
        if (hasErrorHandling) score++;
        if (!hasLongLines) score++;
        if (!hasMagicNumbers) score++;
        
        List<CodeReviewResult.CodeIssue> issues = new ArrayList<>();
        List<CodeReviewResult.CodeSuggestion> suggestions = new ArrayList<>();
        
        if (!hasComments) {
            issues.add(CodeReviewResult.CodeIssue.builder()
                    .type(CodeReviewResult.CodeIssueType.STYLE)
                    .severity(CodeReviewResult.IssueSeverity.MEDIUM)
                    .description("Code lacks comments")
                    .suggestion("Add comments to explain complex logic")
                    .build());
        }
        
        if (!hasErrorHandling) {
            issues.add(CodeReviewResult.CodeIssue.builder()
                    .type(CodeReviewResult.CodeIssueType.BUG)
                    .severity(CodeReviewResult.IssueSeverity.HIGH)
                    .description("No error handling detected")
                    .suggestion("Add try-catch blocks for error-prone operations")
                    .build());
        }
        
        if (hasLongLines) {
            issues.add(CodeReviewResult.CodeIssue.builder()
                    .type(CodeReviewResult.CodeIssueType.STYLE)
                    .severity(CodeReviewResult.IssueSeverity.LOW)
                    .description("Some lines are too long")
                    .suggestion("Break long lines to improve readability")
                    .build());
        }
        
        suggestions.add(CodeReviewResult.CodeSuggestion.builder()
                .category(CodeReviewResult.SuggestionCategory.BEST_PRACTICE)
                .description("Consider adding unit tests")
                .priority(CodeReviewResult.SuggestionPriority.HIGH)
                .build());
        
        suggestions.add(CodeReviewResult.CodeSuggestion.builder()
                .category(CodeReviewResult.SuggestionCategory.IMPROVEMENT)
                .description("Use meaningful variable names")
                .priority(CodeReviewResult.SuggestionPriority.MEDIUM)
                .build());
        
        return CodeReviewResult.builder()
                .success(true)
                .overallScore(score)
                .summary("Test mode analysis - Basic code quality assessment")
                .strengths(List.of("Code structure is readable", "Language syntax appears correct"))
                .issues(issues)
                .suggestions(suggestions)
                .securityConcerns(List.of("Consider input validation"))
                .performanceTips(List.of("Profile the code for performance bottlenecks"))
                .bestPractices(List.of("Follow " + language + " coding conventions"))
                .analyzedLanguage(language)
                .analysisTimeMs(1000L)
                .build();
    }
} 