package tn.esprithub.server.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tn.esprithub.server.ai.dto.CodeReviewResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class CodeReviewService {

    private static final Pattern DIFF_HEADER_PATTERN = Pattern.compile("(?m)^diff --git a/(.+?) b/(.+)$");
    private static final Set<String> DOC_EXTENSIONS = Set.of("md", "markdown", "txt", "rst", "adoc");

    @Value("${app.ai.provider.base-url:https://integrate.api.nvidia.com/v1}")
    private String aiBaseUrl;

    @Value("${app.ai.provider.api-key:${NVIDIA_API_KEY:}}")
    private String aiApiKey;

    @Value("${app.ai.provider.model:minimaxai/minimax-m2.7}")
    private String aiModel;

    @Value("${app.ai.provider.temperature:0.3}")
    private double aiTemperature;

    @Value("${app.ai.provider.top-p:0.7}")
    private double aiTopP;

    @Value("${app.ai.provider.max-tokens:4096}")
    private int aiMaxTokens;

    @Value("${app.ai.openai.api-key:}")
    private String legacyOpenAiApiKey;

    @Value("${app.ai.code-review.test-mode:false}")
    private boolean testMode;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    /**
     * Analyze code and return review, grade suggestion, and risk detection.
     */
    public CodeReviewResult analyzeCode(String code, String language, String context) {
        if (!isAiConfigured() && !testMode) {
            log.warn("AI provider is not configured, skipping code analysis");
            return unavailableResult();
        }

        if (testMode || !isAiConfigured()) {
            log.info("Using test mode for code analysis");
            return generateTestResponse(code, language, context, false);
        }

        long startTime = System.currentTimeMillis();

        try {
            String prompt = buildCodeReviewPrompt(code, language, context);
            String analysis = callChatCompletion(prompt);
            CodeReviewResult result = parseCodeReviewResponse(analysis);
            result.setAnalyzedLanguage(language);
            result.setAnalysisTimeMs(System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            log.error("Error analyzing code with AI", e);

            if (e.getMessage() != null && e.getMessage().contains("Rate limit")) {
                log.info("Falling back to test mode due to rate limit");
                return generateTestResponse(code, language, context, false);
            }

            return CodeReviewResult.builder()
                    .success(false)
                    .message("Error analyzing code: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Analyze a diff for pull-request review.
     */
    public CodeReviewResult analyzeDiff(String diff, String language) {
        List<String> changedFiles = extractChangedFilesFromDiff(diff);

        if (isDocumentationOnlyChange(changedFiles)) {
            return buildDocumentationOnlyDiffReview(changedFiles, language);
        }

        if (!hasText(diff) || changedFiles.isEmpty()) {
            return CodeReviewResult.builder()
                    .success(false)
                    .message("AI PR review requires a valid commit diff with changed files.")
                    .build();
        }

        if (!isAiConfigured() && !testMode) {
            log.warn("AI provider is not configured, skipping diff analysis");
            return unavailableResult();
        }

        if (testMode || !isAiConfigured()) {
            log.info("Using test mode for diff analysis");
            return generateTestResponse(diff, language, "Pull request diff analysis", true);
        }

        long startTime = System.currentTimeMillis();

        try {
            String prompt = buildDiffReviewPrompt(diff, language, changedFiles);
            String analysis = callChatCompletion(prompt);
            CodeReviewResult result = parseCodeReviewResponse(analysis);
            result.setAnalyzedLanguage(language);
            result.setAnalysisTimeMs(System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            log.error("Error analyzing diff with AI", e);

            if (e.getMessage() != null && e.getMessage().contains("Rate limit")) {
                return generateTestResponse(diff, language, "Pull request diff analysis", true);
            }

            return CodeReviewResult.builder()
                    .success(false)
                    .message("Error analyzing diff: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Analyze a student submission commit against teacher task requirements.
     * This is stricter than generic diff review and can legitimately output very low grades.
     */
    public CodeReviewResult analyzeSubmissionAgainstTask(
            String diff,
            String language,
            String taskTitle,
            String taskDescription,
            double gradingScaleMax
    ) {
        List<String> changedFiles = extractChangedFilesFromDiff(diff);
        if (!hasText(diff) || changedFiles.isEmpty()) {
            return CodeReviewResult.builder()
                    .success(false)
                    .message("Task-aligned AI grading requires an exact commit diff with changed files.")
                    .build();
        }

        double effectiveScaleMax = gradingScaleMax > 0 ? gradingScaleMax : 20.0;

        if (!isAiConfigured() && !testMode) {
            return unavailableResult();
        }

        if (testMode || !isAiConfigured()) {
            return buildTaskAlignedTestReview(diff, language, taskTitle, taskDescription, effectiveScaleMax, changedFiles);
        }

        long startTime = System.currentTimeMillis();

        try {
            String prompt = buildTaskAlignedSubmissionPrompt(diff, language, taskTitle, taskDescription, effectiveScaleMax, changedFiles);
            String analysis = callChatCompletion(prompt);
            CodeReviewResult result = parseCodeReviewResponse(analysis);
            result.setAnalyzedLanguage(language);
            result.setAnalysisTimeMs(System.currentTimeMillis() - startTime);

            return normalizeTaskAlignedResult(result, taskDescription, changedFiles, effectiveScaleMax);
        } catch (Exception e) {
            log.error("Error analyzing submission against task", e);

            if (e.getMessage() != null && e.getMessage().contains("Rate limit")) {
                return buildTaskAlignedTestReview(diff, language, taskTitle, taskDescription, effectiveScaleMax, changedFiles);
            }

            return CodeReviewResult.builder()
                    .success(false)
                    .message("Error analyzing submission against task: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Analyze a full file and return review, grade suggestion, and risk detection.
     */
    public CodeReviewResult analyzeFile(String fileName, String fileContent, String language) {
        if (!isAiConfigured() && !testMode) {
            log.warn("AI provider is not configured, skipping file analysis");
            return unavailableResult();
        }

        if (testMode || !isAiConfigured()) {
            log.info("Using test mode for file analysis");
            CodeReviewResult testResult = generateTestResponse(fileContent, language, fileName, false);
            testResult.setAnalyzedFile(fileName);
            return testResult;
        }

        long startTime = System.currentTimeMillis();

        try {
            String prompt = buildFileReviewPrompt(fileName, fileContent, language);
            String analysis = callChatCompletion(prompt);
            CodeReviewResult result = parseCodeReviewResponse(analysis);
            result.setAnalyzedLanguage(language);
            result.setAnalyzedFile(fileName);
            result.setAnalysisTimeMs(System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            log.error("Error analyzing file with AI", e);

            if (e.getMessage() != null && e.getMessage().contains("Rate limit")) {
                CodeReviewResult testResult = generateTestResponse(fileContent, language, fileName, false);
                testResult.setAnalyzedFile(fileName);
                return testResult;
            }

            return CodeReviewResult.builder()
                    .success(false)
                    .message("Error analyzing file: " + e.getMessage())
                    .build();
        }
    }

    private String callChatCompletion(String prompt) {
        try {
            Thread.sleep(800);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", aiModel);
        requestBody.put("messages", List.of(Map.of(
            "role", "system",
            "content", buildSystemInstruction()
        ), Map.of(
            "role", "user",
            "content", prompt
        )));
        requestBody.put("temperature", aiTemperature);
        requestBody.put("top_p", aiTopP);
        requestBody.put("max_tokens", aiMaxTokens);
        requestBody.put("stream", false);

        String providerUrl = buildCompletionsUrl();

        @SuppressWarnings("unchecked")
        Map<String, Object> response = webClient.post()
                .uri(providerUrl)
                .header("Authorization", "Bearer " + resolveApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(status -> status.value() == 429,
                        error -> Mono.error(new RuntimeException("Rate limit exceeded - please wait and retry")))
                .onStatus(HttpStatusCode::isError,
                        error -> error.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new RuntimeException(
                                        "AI provider error " + error.statusCode().value() + ": " + body))))
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

        throw new RuntimeException("Invalid response from AI provider");
    }

    private String buildCodeReviewPrompt(String code, String language, String context) {
        String effectiveContext = hasText(context) ? context : "No additional context provided";

        return String.format("""
            Analyze the following %s code and return a strict JSON object only.

            Context: %s

            Code:
            ```%s
            %s
            ```

            Required JSON schema:
            {
                "overallScore": 1-10,
                "summary": "brief code quality summary",
                "strengths": ["strength"],
                "issues": [
                    {
                        "type": "BUG|SECURITY|PERFORMANCE|STYLE|MAINTAINABILITY",
                        "severity": "LOW|MEDIUM|HIGH|CRITICAL",
                        "line": "line number/range if known",
                        "description": "issue description",
                        "suggestion": "fix suggestion"
                    }
                ],
                "suggestions": [
                    {
                        "category": "IMPROVEMENT|OPTIMIZATION|BEST_PRACTICE|REFACTORING|DOCUMENTATION",
                        "description": "improvement suggestion",
                        "priority": "LOW|MEDIUM|HIGH"
                    }
                ],
                "securityConcerns": ["security concern"],
                "performanceTips": ["performance tip"],
                "bestPractices": ["best practice"],
                "suggestedGrade": 0-100,
                "gradeRationale": "short grading rationale",
                "riskLevel": "LOW|MEDIUM|HIGH|CRITICAL",
                "riskScore": 0-100,
                "riskSignals": ["risk signal"],
                "pullRequestDecision": "APPROVE|COMMENT|REQUEST_CHANGES",
                "prBlockingConcerns": ["blocking concern"]
            }

            Focus on:
            - Code quality and readability
            - PR-readiness
            - Real risk factors and severity
            - A realistic grade for academic review
            """, language, effectiveContext, language, code);
    }

    private String buildDiffReviewPrompt(String diff, String language, List<String> changedFiles) {
        String changedFilesBlock = changedFiles.isEmpty()
                ? "- none"
                : changedFiles.stream().map(file -> "- " + file).collect(java.util.stream.Collectors.joining("\n"));

        return String.format("""
            Analyze the following %s pull-request diff and return strict JSON only.

            Authoritative changed files:
            %s

            Diff:
            ```diff
            %s
            ```

            Required JSON schema:
            {
                "overallScore": 1-10,
                "summary": "brief PR summary",
                "strengths": ["positive change"],
                "issues": [
                    {
                        "type": "BUG|SECURITY|PERFORMANCE|STYLE|MAINTAINABILITY",
                        "severity": "LOW|MEDIUM|HIGH|CRITICAL",
                        "line": "file and hunk if relevant",
                        "description": "concern description",
                        "suggestion": "fix recommendation"
                    }
                ],
                "suggestions": [
                    {
                        "category": "IMPROVEMENT|OPTIMIZATION|BEST_PRACTICE|REFACTORING|DOCUMENTATION",
                        "description": "suggestion",
                        "priority": "LOW|MEDIUM|HIGH"
                    }
                ],
                "securityConcerns": ["security concern"],
                "performanceTips": ["performance tip"],
                "bestPractices": ["best practice"],
                "suggestedGrade": 0-100,
                "gradeRationale": "short rationale",
                "riskLevel": "LOW|MEDIUM|HIGH|CRITICAL",
                "riskScore": 0-100,
                "riskSignals": ["risk signal"],
                "pullRequestDecision": "APPROVE|COMMENT|REQUEST_CHANGES",
                "prBlockingConcerns": ["blocking concern"]
            }

            Focus on:
            - Regression risks
            - Security and correctness
            - Potential regressions
            - Merge readiness decision
            - Grade suggestion for evaluator
            
                Non-negotiable constraints:
                - ONLY reference evidence visible in this diff.
                - NEVER mention files not listed in "Authoritative changed files".
                - If evidence is insufficient, keep issues empty and use APPROVE or COMMENT.
                - Do not assign HIGH/CRITICAL severity without explicit diff evidence.
                """, language, changedFilesBlock, diff);
    }

        private String buildTaskAlignedSubmissionPrompt(
                        String diff,
                        String language,
                        String taskTitle,
                        String taskDescription,
                        double gradingScaleMax,
                        List<String> changedFiles
        ) {
                String changedFilesBlock = changedFiles.isEmpty()
                                ? "- none"
                                : changedFiles.stream().map(file -> "- " + file).collect(java.util.stream.Collectors.joining("\n"));

                String title = hasText(taskTitle) ? taskTitle : "Untitled task";
                String description = hasText(taskDescription) ? taskDescription : "No task description provided";

                return String.format("""
                        You are grading a student submission commit for a teacher.
                        Be strict and requirements-driven.

                        Task title:
                        %s

                        Task description:
                        %s

                        Commit changed files:
                        %s

                        Commit diff:
                        ```diff
                        %s
                        ```

                        Grading policy:
                        - Grade must reflect how well submission matches task requirements, not only code style.
                        - It is valid to give 0/%s if core deliverables are missing.
                        - It is valid to give %s/%s if all core requirements are satisfied with strong quality.
                        - Weighting guideline for suggestedGrade (0-100):
                            1) Requirements coverage/completeness: 60%%
                            2) Correctness and technical soundness: 25%%
                            3) Quality/maintainability/readability: 15%%

                        Output JSON only with schema:
                        {
                            "overallScore": 1-10,
                            "summary": "...",
                            "strengths": ["..."],
                            "issues": [
                                {
                                    "type": "BUG|SECURITY|PERFORMANCE|STYLE|MAINTAINABILITY",
                                    "severity": "LOW|MEDIUM|HIGH|CRITICAL",
                                    "line": "file/hunk if known",
                                    "description": "...",
                                    "suggestion": "..."
                                }
                            ],
                            "suggestions": [
                                {
                                    "category": "IMPROVEMENT|OPTIMIZATION|BEST_PRACTICE|REFACTORING|DOCUMENTATION",
                                    "description": "...",
                                    "priority": "LOW|MEDIUM|HIGH"
                                }
                            ],
                            "securityConcerns": ["..."],
                            "performanceTips": ["..."],
                            "bestPractices": ["..."],
                            "suggestedGrade": 0-100,
                            "gradeRationale": "must explicitly explain requirement coverage vs missing deliverables",
                            "riskLevel": "LOW|MEDIUM|HIGH|CRITICAL",
                            "riskScore": 0-100,
                            "riskSignals": ["..."],
                            "pullRequestDecision": "APPROVE|COMMENT|REQUEST_CHANGES",
                            "prBlockingConcerns": ["must include unmet core task requirements if any"]
                        }

                        Non-negotiable rules:
                        - Never invent files, rows, data, or vulnerabilities not visible in this diff.
                        - Never mention files outside "Commit changed files".
                        - If submission is unrelated to the task requirements, use REQUEST_CHANGES and low suggestedGrade.
                        - If evidence is insufficient, say so instead of hallucinating.
                        """, title, description, changedFilesBlock, diff,
                                formatScale(gradingScaleMax), formatScale(gradingScaleMax), formatScale(gradingScaleMax));
        }

    private String buildFileReviewPrompt(String fileName, String fileContent, String language) {
        return String.format("""
            Analyze the following %s file and return strict JSON only.

            File: %s

            Code:
            ```%s
            %s
            ```

            Required JSON schema:
            {
                "overallScore": 1-10,
                "summary": "file quality summary",
                "strengths": ["strength"],
                "issues": [
                    {
                        "type": "BUG|SECURITY|PERFORMANCE|STYLE|MAINTAINABILITY",
                        "severity": "LOW|MEDIUM|HIGH|CRITICAL",
                        "line": "line/range",
                        "description": "issue description",
                        "suggestion": "fix suggestion"
                    }
                ],
                "suggestions": [
                    {
                        "category": "IMPROVEMENT|OPTIMIZATION|BEST_PRACTICE|REFACTORING|DOCUMENTATION",
                        "description": "suggestion",
                        "priority": "LOW|MEDIUM|HIGH"
                    }
                ],
                "securityConcerns": ["security concern"],
                "performanceTips": ["performance tip"],
                "bestPractices": ["best practice"],
                "suggestedGrade": 0-100,
                "gradeRationale": "short rationale",
                "riskLevel": "LOW|MEDIUM|HIGH|CRITICAL",
                "riskScore": 0-100,
                "riskSignals": ["risk signal"],
                "pullRequestDecision": "APPROVE|COMMENT|REQUEST_CHANGES",
                "prBlockingConcerns": ["blocking concern"]
            }

            Focus on:
            - Code quality and maintainability
            - Review risks
            - Grading quality
            - File-level best practices
            """, language, fileName, language, fileContent, language);
    }

    private CodeReviewResult parseCodeReviewResponse(String response) {
        try {
            String normalizedJson = normalizeJsonPayload(response);

            @SuppressWarnings("unchecked")
            Map<String, Object> analysis = objectMapper.readValue(normalizedJson, Map.class);

            Integer overallScore = clamp(asInteger(analysis.get("overallScore")), 1, 10);
            Integer suggestedGrade = clamp(asInteger(analysis.get("suggestedGrade")), 0, 100);
            if (suggestedGrade == null && overallScore != null) {
                suggestedGrade = overallScore * 10;
            }

            Integer riskScore = clamp(asInteger(analysis.get("riskScore")), 0, 100);
            if (riskScore == null && overallScore != null) {
                riskScore = clamp(100 - (overallScore * 10), 0, 100);
            }

            CodeReviewResult.RiskLevel riskLevel = parseRiskLevel(analysis.get("riskLevel"));
            if (riskLevel == null && riskScore != null) {
                riskLevel = riskLevelFromScore(riskScore);
            }

            List<Map<String, Object>> issuesData = asMapList(
                    analysis.containsKey("issues") ? analysis.get("issues") : analysis.get("concerns"));
            List<Map<String, Object>> suggestionsData = asMapList(analysis.get("suggestions"));

            List<CodeReviewResult.CodeIssue> parsedIssues = parseIssues(issuesData);

            CodeReviewResult.PullRequestDecision prDecision = parsePullRequestDecision(analysis.get("pullRequestDecision"));
            if (prDecision == null) {
                prDecision = inferPullRequestDecision(parsedIssues, riskLevel);
            }

            List<String> riskSignals = asStringList(
                    analysis.containsKey("riskSignals") ? analysis.get("riskSignals") : analysis.get("breakingChanges"));

            List<String> blockingConcerns = asStringList(
                    analysis.containsKey("prBlockingConcerns")
                            ? analysis.get("prBlockingConcerns")
                            : analysis.get("breakingChanges"));

            return CodeReviewResult.builder()
                    .success(true)
                    .overallScore(overallScore)
                    .summary(asString(analysis.get("summary")))
                    .strengths(asStringList(
                            analysis.containsKey("strengths") ? analysis.get("strengths") : analysis.get("positiveChanges")))
                    .issues(parsedIssues)
                    .suggestions(parseSuggestions(suggestionsData))
                    .securityConcerns(asStringList(analysis.get("securityConcerns")))
                    .performanceTips(asStringList(
                            analysis.containsKey("performanceTips")
                                    ? analysis.get("performanceTips")
                                    : analysis.get("testingRecommendations")))
                    .bestPractices(asStringList(
                            analysis.containsKey("bestPractices")
                                    ? analysis.get("bestPractices")
                                    : analysis.get("refactoringOpportunities")))
                    .suggestedGrade(suggestedGrade)
                    .gradeRationale(asString(analysis.get("gradeRationale")))
                    .riskLevel(riskLevel)
                    .riskScore(riskScore)
                    .riskSignals(riskSignals)
                    .pullRequestDecision(prDecision)
                    .prBlockingConcerns(blockingConcerns)
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
        if (issuesData == null || issuesData.isEmpty()) {
            return List.of();
        }

        return issuesData.stream()
                .map(issue -> CodeReviewResult.CodeIssue.builder()
                        .type(parseIssueType(issue.get("type")))
                        .severity(parseIssueSeverity(issue.get("severity")))
                        .line(asString(issue.get("line")))
                        .description(asString(issue.get("description")))
                        .suggestion(asString(issue.get("suggestion")))
                        .build())
                .toList();
    }

    private List<CodeReviewResult.CodeSuggestion> parseSuggestions(List<Map<String, Object>> suggestionsData) {
        if (suggestionsData == null || suggestionsData.isEmpty()) {
            return List.of();
        }

        return suggestionsData.stream()
                .map(suggestion -> CodeReviewResult.CodeSuggestion.builder()
                        .category(parseSuggestionCategory(suggestion.get("category")))
                        .description(asString(suggestion.get("description")))
                        .priority(parseSuggestionPriority(suggestion.get("priority")))
                        .build())
                .toList();
    }

    /**
     * Generate deterministic test-mode output.
     */
    private CodeReviewResult generateTestResponse(String code, String language, String context, boolean isPrReview) {
        try {
            Thread.sleep(700);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        boolean hasComments = code.contains("//") || code.contains("/*") || code.contains("*");
        boolean hasErrorHandling = code.contains("try") || code.contains("catch") || code.contains("if");
        boolean hasLongLines = code.lines().anyMatch(line -> line.length() > 80);
        boolean hasMagicNumbers = code.matches("(?s).*\\b\\d{3,}\\b.*");
        boolean hasHardcodedSecrets = code.toLowerCase(Locale.ROOT).contains("password")
                || code.toLowerCase(Locale.ROOT).contains("apikey")
                || code.toLowerCase(Locale.ROOT).contains("token");

        int score = 6;
        if (hasComments) score++;
        if (hasErrorHandling) score++;
        if (!hasLongLines) score++;
        if (!hasMagicNumbers) score++;
        if (!hasHardcodedSecrets) score++;
        score = clamp(score, 1, 10);

        List<CodeReviewResult.CodeIssue> issues = new ArrayList<>();
        List<CodeReviewResult.CodeSuggestion> suggestions = new ArrayList<>();

        if (!hasComments) {
            issues.add(CodeReviewResult.CodeIssue.builder()
                    .type(CodeReviewResult.CodeIssueType.STYLE)
                    .severity(CodeReviewResult.IssueSeverity.MEDIUM)
                    .description("Code lacks explanatory comments")
                    .suggestion("Add comments around non-obvious logic")
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

        if (hasHardcodedSecrets) {
            issues.add(CodeReviewResult.CodeIssue.builder()
                    .type(CodeReviewResult.CodeIssueType.SECURITY)
                    .severity(CodeReviewResult.IssueSeverity.CRITICAL)
                    .description("Potential hard-coded sensitive data detected")
                    .suggestion("Move secrets to environment variables or a secret manager")
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

        int riskScore = clamp(issues.size() * 18 + (hasHardcodedSecrets ? 25 : 0), 0, 100);
        CodeReviewResult.RiskLevel riskLevel = riskLevelFromScore(riskScore);
        int suggestedGrade = clamp((score * 10) - (issues.size() * 3), 0, 100);
        CodeReviewResult.PullRequestDecision decision = inferPullRequestDecision(issues, riskLevel);

        List<String> riskSignals = new ArrayList<>();
        if (!hasErrorHandling) {
            riskSignals.add("Insufficient error handling");
        }
        if (hasHardcodedSecrets) {
            riskSignals.add("Possible secrets in source code");
        }
        if (hasLongLines) {
            riskSignals.add("Readability degradation due to long lines");
        }
        if (riskSignals.isEmpty()) {
            riskSignals.add("No major risks detected in heuristic test mode");
        }

        List<String> blockingConcerns = new ArrayList<>();
        if (decision == CodeReviewResult.PullRequestDecision.REQUEST_CHANGES || isPrReview) {
            for (CodeReviewResult.CodeIssue issue : issues) {
                if (issue.getSeverity() == CodeReviewResult.IssueSeverity.CRITICAL
                        || issue.getSeverity() == CodeReviewResult.IssueSeverity.HIGH) {
                    blockingConcerns.add(issue.getDescription());
                }
            }
        }

        return CodeReviewResult.builder()
                .success(true)
                .overallScore(score)
                .summary("Test mode analysis - heuristic review output")
                .strengths(List.of("Code structure is readable", "Language syntax appears correct"))
                .issues(issues)
                .suggestions(suggestions)
                .securityConcerns(List.of("Validate inputs", "Avoid hard-coded credentials"))
                .performanceTips(List.of("Profile the code for performance bottlenecks"))
                .bestPractices(List.of("Follow " + language + " coding conventions"))
                .suggestedGrade(suggestedGrade)
                .gradeRationale("Grade is derived from code quality heuristics and issue severity in test mode")
                .riskLevel(riskLevel)
                .riskScore(riskScore)
                .riskSignals(riskSignals)
                .pullRequestDecision(decision)
                .prBlockingConcerns(blockingConcerns)
                .analyzedLanguage(language)
                .analysisTimeMs(700L)
                .build();
    }

    private String buildSystemInstruction() {
        return "You are an expert software reviewer. Always return valid JSON only. "
                + "Do not include markdown, code fences, or explanations outside JSON. "
                + "Use realistic risk and grade values based on evidence in the input. "
                + "Do not invent files, rows, secrets, or vulnerabilities not explicitly shown.";
    }

    private CodeReviewResult normalizeTaskAlignedResult(
            CodeReviewResult result,
            String taskDescription,
            List<String> changedFiles,
            double gradingScaleMax
    ) {
        if (!result.isSuccess()) {
            return result;
        }

        if (result.getSuggestedGrade() == null) {
            int fallback = result.getOverallScore() != null ? result.getOverallScore() * 10 : 50;
            result.setSuggestedGrade(clamp(fallback, 0, 100));
        }

        if (result.getPullRequestDecision() == null) {
            result.setPullRequestDecision(CodeReviewResult.PullRequestDecision.COMMENT);
        }

        if (result.getRiskLevel() == null) {
            result.setRiskLevel(CodeReviewResult.RiskLevel.MEDIUM);
        }

        if (result.getRiskScore() == null) {
            int fallbackRisk = result.getOverallScore() != null ? 100 - (result.getOverallScore() * 10) : 50;
            result.setRiskScore(clamp(fallbackRisk, 0, 100));
        }

        if (result.getRiskSignals() == null) {
            result.setRiskSignals(List.of());
        }

        if (result.getPrBlockingConcerns() == null) {
            result.setPrBlockingConcerns(List.of());
        }

        boolean docsOnly = isDocumentationOnlyChange(changedFiles);
        boolean taskLikelyRequiresImplementation = taskDescriptionLikelyRequiresImplementation(taskDescription);

        if (docsOnly && taskLikelyRequiresImplementation) {
            int capped = Math.min(result.getSuggestedGrade(), 20);
            result.setSuggestedGrade(capped);
            result.setPullRequestDecision(CodeReviewResult.PullRequestDecision.REQUEST_CHANGES);
            result.setRiskLevel(CodeReviewResult.RiskLevel.HIGH);
            result.setRiskScore(Math.max(result.getRiskScore(), 70));

            List<String> blockers = new ArrayList<>(result.getPrBlockingConcerns());
            blockers.add("Core task requirements appear missing: documentation-only commit for implementation-oriented task.");
            result.setPrBlockingConcerns(blockers.stream().distinct().toList());

            String rationale = hasText(result.getGradeRationale())
                    ? result.getGradeRationale() + " "
                    : "";
            rationale += "Task alignment penalty applied: submission does not provide expected implementation artifacts.";
            result.setGradeRationale(rationale);

            if (result.getOverallScore() != null) {
                result.setOverallScore(Math.min(result.getOverallScore(), 3));
            }
        }

        String scaleNote = String.format(
                Locale.ROOT,
                " Suggested grade on %.1f-point scale: %.2f/%.1f.",
                gradingScaleMax,
                (result.getSuggestedGrade() / 100.0) * gradingScaleMax,
                gradingScaleMax
        );

        if (!hasText(result.getGradeRationale())) {
            result.setGradeRationale("Grade is based primarily on task requirement coverage and deliverable completeness." + scaleNote);
        } else if (!result.getGradeRationale().contains("-point scale")) {
            result.setGradeRationale(result.getGradeRationale() + scaleNote);
        }

        return result;
    }

    private boolean taskDescriptionLikelyRequiresImplementation(String taskDescription) {
        if (!hasText(taskDescription)) {
            return true;
        }

        String normalized = taskDescription.toLowerCase(Locale.ROOT);
        String[] implementationKeywords = {
                "implement", "build", "develop", "create", "feature", "function", "endpoint",
                "api", "service", "algorithm", "backend", "frontend", "database", "integration",
                "crud", "module", "application", "security", "deploy", "coding", "code"
        };

        for (String keyword : implementationKeywords) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }

        String[] documentationKeywords = {
                "readme", "documentation", "document", "report", "write-up", "specification", "markdown"
        };

        for (String keyword : documentationKeywords) {
            if (normalized.contains(keyword)) {
                return false;
            }
        }

        return true;
    }

    private CodeReviewResult buildTaskAlignedTestReview(
            String diff,
            String language,
            String taskTitle,
            String taskDescription,
            double gradingScaleMax,
            List<String> changedFiles
    ) {
        boolean docsOnly = isDocumentationOnlyChange(changedFiles);
        boolean implementationRequired = taskDescriptionLikelyRequiresImplementation(taskDescription);

        int suggestedGrade = docsOnly && implementationRequired ? 10 : 65;
        int overallScore = docsOnly && implementationRequired ? 2 : 6;
        CodeReviewResult.PullRequestDecision decision = docsOnly && implementationRequired
                ? CodeReviewResult.PullRequestDecision.REQUEST_CHANGES
                : CodeReviewResult.PullRequestDecision.COMMENT;

        List<CodeReviewResult.CodeIssue> issues = new ArrayList<>();
        if (docsOnly && implementationRequired) {
            issues.add(CodeReviewResult.CodeIssue.builder()
                    .type(CodeReviewResult.CodeIssueType.MAINTAINABILITY)
                    .severity(CodeReviewResult.IssueSeverity.HIGH)
                    .description("Submission appears documentation-only while task likely requires implementation deliverables.")
                    .suggestion("Provide code changes that satisfy the core functional requirements described by the teacher.")
                    .build());
        }

        return CodeReviewResult.builder()
                .success(true)
                .overallScore(overallScore)
                .summary("Task-aware test review based on requirement coverage heuristics.")
                .strengths(List.of("Structured commit", "Diff is readable"))
                .issues(issues)
                .suggestions(List.of(
                        CodeReviewResult.CodeSuggestion.builder()
                                .category(CodeReviewResult.SuggestionCategory.IMPROVEMENT)
                                .description("Align submission changes directly with explicit task deliverables.")
                                .priority(CodeReviewResult.SuggestionPriority.HIGH)
                                .build()))
                .securityConcerns(List.of())
                .performanceTips(List.of())
                .bestPractices(List.of("Reference task requirements in commit message and PR notes"))
                .suggestedGrade(suggestedGrade)
                .gradeRationale(String.format(
                        Locale.ROOT,
                        "Task-aware grading test mode. Suggested grade on %.1f-point scale: %.2f/%.1f.",
                        gradingScaleMax,
                        (suggestedGrade / 100.0) * gradingScaleMax,
                        gradingScaleMax
                ))
                .riskLevel(docsOnly && implementationRequired ? CodeReviewResult.RiskLevel.HIGH : CodeReviewResult.RiskLevel.MEDIUM)
                .riskScore(docsOnly && implementationRequired ? 75 : 45)
                .riskSignals(docsOnly && implementationRequired
                        ? List.of("Likely missing core implementation deliverables")
                        : List.of("Partial requirement coverage uncertainty"))
                .pullRequestDecision(decision)
                .prBlockingConcerns(docsOnly && implementationRequired
                        ? List.of("Core task requirements appear unmet by the submitted diff")
                        : List.of())
                .analyzedLanguage(hasText(language) ? language : "text")
                .analysisTimeMs(5L)
                .build();
    }

    private String formatScale(double gradingScaleMax) {
        return String.format(Locale.ROOT, "%.1f", gradingScaleMax);
    }

    private List<String> extractChangedFilesFromDiff(String diff) {
        if (!hasText(diff)) {
            return List.of();
        }

        Matcher matcher = DIFF_HEADER_PATTERN.matcher(diff);
        Set<String> files = new LinkedHashSet<>();

        while (matcher.find()) {
            String filePath = matcher.group(2);
            if (hasText(filePath)) {
                files.add(filePath.trim());
            }
        }

        return List.copyOf(files);
    }

    private boolean isDocumentationOnlyChange(List<String> changedFiles) {
        if (changedFiles == null || changedFiles.isEmpty()) {
            return false;
        }

        return changedFiles.stream().allMatch(this::isDocumentationFile);
    }

    private boolean isDocumentationFile(String path) {
        if (!hasText(path)) {
            return false;
        }

        String normalized = path.toLowerCase(Locale.ROOT);
        String fileName = normalized.contains("/")
                ? normalized.substring(normalized.lastIndexOf('/') + 1)
                : normalized;

        if (fileName.startsWith("readme") || fileName.startsWith("changelog") || fileName.startsWith("license")
                || fileName.startsWith("contributing")) {
            return true;
        }

        if (!fileName.contains(".")) {
            return false;
        }

        String extension = fileName.substring(fileName.lastIndexOf('.') + 1);
        return DOC_EXTENSIONS.contains(extension);
    }

    private CodeReviewResult buildDocumentationOnlyDiffReview(List<String> changedFiles, String language) {
        return CodeReviewResult.builder()
                .success(true)
                .overallScore(8)
                .summary("Documentation-only change detected; no code-risk concerns found in the submitted diff.")
                .strengths(List.of("Clear documentation update", "Low technical risk for runtime behavior"))
                .issues(List.of())
                .suggestions(List.of(
                        CodeReviewResult.CodeSuggestion.builder()
                                .category(CodeReviewResult.SuggestionCategory.DOCUMENTATION)
                                .description("Verify wording accuracy and keep docs aligned with implementation changes.")
                                .priority(CodeReviewResult.SuggestionPriority.MEDIUM)
                                .build()))
                .securityConcerns(List.of())
                .performanceTips(List.of())
                .bestPractices(List.of("Use concise commit messages for docs-only updates"))
                .suggestedGrade(85)
                .gradeRationale("Docs-only PR with low technical risk and acceptable quality.")
                .riskLevel(CodeReviewResult.RiskLevel.LOW)
                .riskScore(10)
                .riskSignals(List.of("No executable code changes detected"))
                .pullRequestDecision(CodeReviewResult.PullRequestDecision.APPROVE)
                .prBlockingConcerns(List.of())
                .analyzedLanguage(hasText(language) ? language : "text")
                .analysisTimeMs(5L)
                .build();
    }

    private boolean isAiConfigured() {
        return hasText(resolveApiKey()) && hasText(aiBaseUrl) && hasText(aiModel);
    }

    private String resolveApiKey() {
        if (hasText(aiApiKey)) {
            return aiApiKey.trim();
        }

        if (hasText(legacyOpenAiApiKey)) {
            return legacyOpenAiApiKey.trim();
        }

        return "";
    }

    private String buildCompletionsUrl() {
        String baseUrl = aiBaseUrl == null ? "" : aiBaseUrl.trim();
        if (baseUrl.endsWith("/")) {
            return baseUrl + "chat/completions";
        }
        return baseUrl + "/chat/completions";
    }

    private CodeReviewResult unavailableResult() {
        return CodeReviewResult.builder()
                .success(false)
                .message("AI analysis not available - provider API key not configured")
                .build();
    }

    private String normalizeJsonPayload(String response) {
        if (!hasText(response)) {
            throw new IllegalArgumentException("Empty AI response");
        }

        String payload = response.trim();
        payload = payload.replaceFirst("^```(?:json)?\\s*", "");
        payload = payload.replaceFirst("\\s*```$", "");

        int firstBrace = payload.indexOf('{');
        int lastBrace = payload.lastIndexOf('}');

        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return payload.substring(firstBrace, lastBrace + 1);
        }

        return payload;
    }

    private List<Map<String, Object>> asMapList(Object value) {
        if (!(value instanceof List<?> listValue)) {
            return List.of();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : listValue) {
            if (item instanceof Map<?, ?> sourceMap) {
                Map<String, Object> normalized = new HashMap<>();
                sourceMap.forEach((key, mapValue) -> normalized.put(String.valueOf(key), mapValue));
                result.add(normalized);
            }
        }
        return result;
    }

    private List<String> asStringList(Object value) {
        if (value == null) {
            return List.of();
        }

        if (value instanceof List<?> listValue) {
            return listValue.stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(this::hasText)
                    .toList();
        }

        String textValue = String.valueOf(value).trim();
        if (!hasText(textValue)) {
            return List.of();
        }
        return List.of(textValue);
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String result = String.valueOf(value).trim();
        return hasText(result) ? result : null;
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.intValue();
        }

        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Integer clamp(Integer value, int min, int max) {
        if (value == null) {
            return null;
        }
        return Math.max(min, Math.min(max, value));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private CodeReviewResult.CodeIssueType parseIssueType(Object raw) {
        String normalized = normalizeEnum(raw);
        if (!hasText(normalized)) {
            return CodeReviewResult.CodeIssueType.MAINTAINABILITY;
        }

        try {
            return CodeReviewResult.CodeIssueType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return CodeReviewResult.CodeIssueType.MAINTAINABILITY;
        }
    }

    private CodeReviewResult.IssueSeverity parseIssueSeverity(Object raw) {
        String normalized = normalizeEnum(raw);
        if (!hasText(normalized)) {
            return CodeReviewResult.IssueSeverity.MEDIUM;
        }

        try {
            return CodeReviewResult.IssueSeverity.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return CodeReviewResult.IssueSeverity.MEDIUM;
        }
    }

    private CodeReviewResult.SuggestionCategory parseSuggestionCategory(Object raw) {
        String normalized = normalizeEnum(raw);
        if (!hasText(normalized)) {
            return CodeReviewResult.SuggestionCategory.IMPROVEMENT;
        }

        try {
            return CodeReviewResult.SuggestionCategory.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return CodeReviewResult.SuggestionCategory.IMPROVEMENT;
        }
    }

    private CodeReviewResult.SuggestionPriority parseSuggestionPriority(Object raw) {
        String normalized = normalizeEnum(raw);
        if (!hasText(normalized)) {
            return CodeReviewResult.SuggestionPriority.MEDIUM;
        }

        try {
            return CodeReviewResult.SuggestionPriority.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return CodeReviewResult.SuggestionPriority.MEDIUM;
        }
    }

    private CodeReviewResult.RiskLevel parseRiskLevel(Object raw) {
        String normalized = normalizeEnum(raw);
        if (!hasText(normalized)) {
            return null;
        }

        try {
            return CodeReviewResult.RiskLevel.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private CodeReviewResult.PullRequestDecision parsePullRequestDecision(Object raw) {
        String normalized = normalizeEnum(raw);
        if (!hasText(normalized)) {
            return null;
        }

        try {
            return CodeReviewResult.PullRequestDecision.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private CodeReviewResult.PullRequestDecision inferPullRequestDecision(
            List<CodeReviewResult.CodeIssue> issues,
            CodeReviewResult.RiskLevel riskLevel) {

        boolean hasBlockingIssue = issues.stream().anyMatch(issue -> issue.getSeverity() == CodeReviewResult.IssueSeverity.CRITICAL
                || issue.getSeverity() == CodeReviewResult.IssueSeverity.HIGH);

        if (hasBlockingIssue || riskLevel == CodeReviewResult.RiskLevel.CRITICAL || riskLevel == CodeReviewResult.RiskLevel.HIGH) {
            return CodeReviewResult.PullRequestDecision.REQUEST_CHANGES;
        }

        if (!issues.isEmpty()) {
            return CodeReviewResult.PullRequestDecision.COMMENT;
        }

        return CodeReviewResult.PullRequestDecision.APPROVE;
    }

    private CodeReviewResult.RiskLevel riskLevelFromScore(Integer riskScore) {
        if (riskScore == null) {
            return CodeReviewResult.RiskLevel.MEDIUM;
        }

        if (riskScore >= 75) {
            return CodeReviewResult.RiskLevel.CRITICAL;
        }
        if (riskScore >= 55) {
            return CodeReviewResult.RiskLevel.HIGH;
        }
        if (riskScore >= 30) {
            return CodeReviewResult.RiskLevel.MEDIUM;
        }
        return CodeReviewResult.RiskLevel.LOW;
    }

    private String normalizeEnum(Object raw) {
        if (raw == null) {
            return null;
        }
        return String.valueOf(raw)
                .trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
} 