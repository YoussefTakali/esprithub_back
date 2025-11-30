package tn.esprithub.server.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.esprithub.server.ai.CodeReviewService;
import tn.esprithub.server.ai.dto.CodeReviewResult;
import tn.esprithub.server.email.EmailService;
import tn.esprithub.server.user.entity.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class CodeReviewNotificationService {

    private final CodeReviewService codeReviewService;
    private final EmailService emailService;
    private final TeamsNotificationService teamsNotificationService;

    /**
     * Analyse le code et envoie une notification avec les résultats
     */
    public void analyzeAndNotify(String code, String language, String context, 
                                List<User> recipients, String repositoryName, String fileName) {
        
        log.info("Starting AI code analysis for file: {} in repository: {}", fileName, repositoryName);
        
        long startTime = System.currentTimeMillis();
        CodeReviewResult result = codeReviewService.analyzeCode(code, language, context);
        long analysisTime = System.currentTimeMillis() - startTime;
        
        result.setAnalysisTimeMs(analysisTime);
        result.setAnalyzedLanguage(language);
        result.setAnalyzedFile(fileName);
        
        if (result.isSuccess()) {
            sendCodeReviewNotification(result, recipients, repositoryName, fileName);
        } else {
            sendAnalysisErrorNotification(result, recipients, repositoryName, fileName);
        }
    }

    /**
     * Analyse un diff et envoie une notification
     */
    public void analyzeDiffAndNotify(String diff, String language, List<User> recipients, 
                                   String repositoryName, String pullRequestTitle) {
        
        log.info("Starting AI diff analysis for PR: {} in repository: {}", pullRequestTitle, repositoryName);
        
        long startTime = System.currentTimeMillis();
        CodeReviewResult result = codeReviewService.analyzeDiff(diff, language);
        long analysisTime = System.currentTimeMillis() - startTime;
        
        result.setAnalysisTimeMs(analysisTime);
        result.setAnalyzedLanguage(language);
        
        if (result.isSuccess()) {
            sendDiffReviewNotification(result, recipients, repositoryName, pullRequestTitle);
        } else {
            sendAnalysisErrorNotification(result, recipients, repositoryName, pullRequestTitle);
        }
    }

    /**
     * Envoie une notification avec les résultats d'analyse de code
     */
    private void sendCodeReviewNotification(CodeReviewResult result, List<User> recipients, 
                                          String repositoryName, String fileName) {
        
        String subject = String.format("🤖 AI Code Review - %s (%s)", fileName, repositoryName);
        
        String emailContent = buildCodeReviewEmailContent(result, repositoryName, fileName);
        String teamsContent = buildCodeReviewTeamsContent(result, repositoryName, fileName);
        
        // Envoi par email
        for (User recipient : recipients) {
            try {
                emailService.sendNotificationEmail(recipient.getEmail(), subject, emailContent);
                log.info("Code review notification sent to: {}", recipient.getEmail());
            } catch (Exception e) {
                log.error("Failed to send code review email to: {}", recipient.getEmail(), e);
            }
        }
        
        // Envoi par Teams
        try {
            teamsNotificationService.sendNotification(teamsContent);
            log.info("Code review notification sent to Teams");
        } catch (Exception e) {
            log.error("Failed to send code review notification to Teams", e);
        }
    }

    /**
     * Envoie une notification avec les résultats d'analyse de diff
     */
    private void sendDiffReviewNotification(CodeReviewResult result, List<User> recipients, 
                                          String repositoryName, String pullRequestTitle) {
        
        String subject = String.format("🤖 AI Pull Request Review - %s (%s)", pullRequestTitle, repositoryName);
        
        String emailContent = buildDiffReviewEmailContent(result, repositoryName, pullRequestTitle);
        String teamsContent = buildDiffReviewTeamsContent(result, repositoryName, pullRequestTitle);
        
        // Envoi par email
        for (User recipient : recipients) {
            try {
                emailService.sendNotificationEmail(recipient.getEmail(), subject, emailContent);
                log.info("Diff review notification sent to: {}", recipient.getEmail());
            } catch (Exception e) {
                log.error("Failed to send diff review email to: {}", recipient.getEmail(), e);
            }
        }
        
        // Envoi par Teams
        try {
            teamsNotificationService.sendNotification(teamsContent);
            log.info("Diff review notification sent to Teams");
        } catch (Exception e) {
            log.error("Failed to send diff review notification to Teams", e);
        }
    }

    /**
     * Envoie une notification d'erreur d'analyse
     */
    private void sendAnalysisErrorNotification(CodeReviewResult result, List<User> recipients, 
                                             String repositoryName, String fileName) {
        
        String subject = String.format("⚠️ AI Analysis Error - %s (%s)", fileName, repositoryName);
        String message = String.format("AI analysis failed for %s in %s: %s", 
                                     fileName, repositoryName, result.getMessage());
        
        // Envoi par email
        for (User recipient : recipients) {
            try {
                emailService.sendNotificationEmail(recipient.getEmail(), subject, 
                    buildErrorEmailContent(message));
                log.info("Analysis error notification sent to: {}", recipient.getEmail());
            } catch (Exception e) {
                log.error("Failed to send analysis error email to: {}", recipient.getEmail(), e);
            }
        }
        
        // Envoi par Teams
        try {
            teamsNotificationService.sendSimpleNotification(message);
            log.info("Analysis error notification sent to Teams");
        } catch (Exception e) {
            log.error("Failed to send analysis error notification to Teams", e);
        }
    }

    private String buildCodeReviewEmailContent(CodeReviewResult result, String repositoryName, String fileName) {
        StringBuilder content = new StringBuilder();
        content.append(String.format("""
            <div style="font-family: Arial, sans-serif; max-width: 800px; margin: 0 auto;">
                <h2 style="color: #0366d6;">🤖 AI Code Review Report</h2>
                <div style="background-color: #f6f8fa; padding: 15px; border-radius: 6px; margin: 20px 0;">
                    <p><strong>Repository:</strong> %s</p>
                    <p><strong>File:</strong> %s</p>
                    <p><strong>Language:</strong> %s</p>
                    <p><strong>Analysis Time:</strong> %d ms</p>
                </div>
                
                <div style="margin: 20px 0;">
                    <h3>📊 Overall Assessment</h3>
                    <p><strong>Score:</strong> %d/10</p>
                    <p><strong>Summary:</strong> %s</p>
                </div>
            """, repositoryName, fileName, result.getAnalyzedLanguage(), 
                 result.getAnalysisTimeMs(), result.getOverallScore(), result.getSummary()));

        // Forces
        if (result.getStrengths() != null && !result.getStrengths().isEmpty()) {
            content.append("<div style='margin: 20px 0;'><h3>✅ Strengths</h3><ul>");
            for (String strength : result.getStrengths()) {
                content.append("<li>").append(strength).append("</li>");
            }
            content.append("</ul></div>");
        }

        // Problèmes
        if (result.getIssues() != null && !result.getIssues().isEmpty()) {
            content.append("<div style='margin: 20px 0;'><h3>⚠️ Issues Found</h3>");
            for (CodeReviewResult.CodeIssue issue : result.getIssues()) {
                String severityColor = getSeverityColor(issue.getSeverity());
                content.append(String.format("""
                    <div style='border-left: 4px solid %s; padding: 10px; margin: 10px 0; background-color: #f8f9fa;'>
                        <p><strong>%s</strong> (%s) - Line %s</p>
                        <p>%s</p>
                        <p><em>Suggestion:</em> %s</p>
                    </div>
                    """, severityColor, issue.getType(), issue.getSeverity(), 
                         issue.getLine(), issue.getDescription(), issue.getSuggestion()));
            }
            content.append("</div>");
        }

        // Suggestions
        if (result.getSuggestions() != null && !result.getSuggestions().isEmpty()) {
            content.append("<div style='margin: 20px 0;'><h3>💡 Suggestions</h3>");
            for (CodeReviewResult.CodeSuggestion suggestion : result.getSuggestions()) {
                String priorityColor = getPriorityColor(suggestion.getPriority());
                content.append(String.format("""
                    <div style='border-left: 4px solid %s; padding: 10px; margin: 10px 0; background-color: #f8f9fa;'>
                        <p><strong>%s</strong> (%s)</p>
                        <p>%s</p>
                    </div>
                    """, priorityColor, suggestion.getCategory(), suggestion.getPriority(), 
                         suggestion.getDescription()));
            }
            content.append("</div>");
        }

        // Recommandations spécifiques
        if (result.getSecurityConcerns() != null && !result.getSecurityConcerns().isEmpty()) {
            content.append("<div style='margin: 20px 0;'><h3>🔒 Security Concerns</h3><ul>");
            for (String concern : result.getSecurityConcerns()) {
                content.append("<li>").append(concern).append("</li>");
            }
            content.append("</ul></div>");
        }

        if (result.getPerformanceTips() != null && !result.getPerformanceTips().isEmpty()) {
            content.append("<div style='margin: 20px 0;'><h3>⚡ Performance Tips</h3><ul>");
            for (String tip : result.getPerformanceTips()) {
                content.append("<li>").append(tip).append("</li>");
            }
            content.append("</ul></div>");
        }

        if (result.getBestPractices() != null && !result.getBestPractices().isEmpty()) {
            content.append("<div style='margin: 20px 0;'><h3>📚 Best Practices</h3><ul>");
            for (String practice : result.getBestPractices()) {
                content.append("<li>").append(practice).append("</li>");
            }
            content.append("</ul></div>");
        }

        content.append("""
            <div style='margin: 20px 0; padding: 15px; background-color: #f8f9fa; border-radius: 6px;'>
                <p><em>This analysis was performed by AI. Please review the suggestions carefully before implementing changes.</em></p>
            </div>
            </div>
            """);

        return content.toString();
    }

    private String buildDiffReviewEmailContent(CodeReviewResult result, String repositoryName, String pullRequestTitle) {
        StringBuilder content = new StringBuilder();
        content.append(String.format("""
            <div style="font-family: Arial, sans-serif; max-width: 800px; margin: 0 auto;">
                <h2 style="color: #0366d6;">🤖 AI Pull Request Review</h2>
                <div style="background-color: #f6f8fa; padding: 15px; border-radius: 6px; margin: 20px 0;">
                    <p><strong>Repository:</strong> %s</p>
                    <p><strong>Pull Request:</strong> %s</p>
                    <p><strong>Language:</strong> %s</p>
                    <p><strong>Analysis Time:</strong> %d ms</p>
                </div>
                
                <div style="margin: 20px 0;">
                    <h3>📊 Review Summary</h3>
                    <p><strong>Score:</strong> %d/10</p>
                    <p><strong>Summary:</strong> %s</p>
                </div>
            """, repositoryName, pullRequestTitle, result.getAnalyzedLanguage(), 
                 result.getAnalysisTimeMs(), result.getOverallScore(), result.getSummary()));

        // Changements positifs
        if (result.getStrengths() != null && !result.getStrengths().isEmpty()) {
            content.append("<div style='margin: 20px 0;'><h3>✅ Positive Changes</h3><ul>");
            for (String strength : result.getStrengths()) {
                content.append("<li>").append(strength).append("</li>");
            }
            content.append("</ul></div>");
        }

        // Problèmes
        if (result.getIssues() != null && !result.getIssues().isEmpty()) {
            content.append("<div style='margin: 20px 0;'><h3>⚠️ Concerns</h3>");
            for (CodeReviewResult.CodeIssue issue : result.getIssues()) {
                String severityColor = getSeverityColor(issue.getSeverity());
                content.append(String.format("""
                    <div style='border-left: 4px solid %s; padding: 10px; margin: 10px 0; background-color: #f8f9fa;'>
                        <p><strong>%s</strong> (%s)</p>
                        <p>%s</p>
                        <p><em>Suggestion:</em> %s</p>
                    </div>
                    """, severityColor, issue.getType(), issue.getSeverity(), 
                         issue.getDescription(), issue.getSuggestion()));
            }
            content.append("</div>");
        }

        // Suggestions
        if (result.getSuggestions() != null && !result.getSuggestions().isEmpty()) {
            content.append("<div style='margin: 20px 0;'><h3>💡 Recommendations</h3>");
            for (CodeReviewResult.CodeSuggestion suggestion : result.getSuggestions()) {
                String priorityColor = getPriorityColor(suggestion.getPriority());
                content.append(String.format("""
                    <div style='border-left: 4px solid %s; padding: 10px; margin: 10px 0; background-color: #f8f9fa;'>
                        <p><strong>%s</strong> (%s)</p>
                        <p>%s</p>
                    </div>
                    """, priorityColor, suggestion.getCategory(), suggestion.getPriority(), 
                         suggestion.getDescription()));
            }
            content.append("</div>");
        }

        content.append("""
            <div style='margin: 20px 0; padding: 15px; background-color: #f8f9fa; border-radius: 6px;'>
                <p><em>This review was performed by AI. Please review the suggestions carefully before merging.</em></p>
            </div>
            </div>
            """);

        return content.toString();
    }

    private String buildErrorEmailContent(String errorMessage) {
        return String.format("""
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                <h2 style="color: #dc3545;">⚠️ AI Analysis Error</h2>
                <div style="background-color: #f8d7da; padding: 15px; border-radius: 6px; margin: 20px 0; border-left: 4px solid #dc3545;">
                    <p><strong>Error:</strong> %s</p>
                </div>
                <p>The AI code analysis failed. Please try again later or contact support if the issue persists.</p>
            </div>
            """, errorMessage);
    }

    private String buildCodeReviewTeamsContent(CodeReviewResult result, String repositoryName, String fileName) {
        Map<String, Object> card = new HashMap<>();
        card.put("type", "message");
        card.put("attachments", List.of(Map.of(
            "contentType", "application/vnd.microsoft.card.adaptive",
            "content", Map.of(
                "type", "AdaptiveCard",
                "version", "1.0",
                "body", List.of(
                    Map.of("type", "TextBlock", "text", "🤖 AI Code Review Report", "weight", "bolder", "size", "large"),
                    Map.of("type", "TextBlock", "text", String.format("**Repository:** %s", repositoryName)),
                    Map.of("type", "TextBlock", "text", String.format("**File:** %s", fileName)),
                    Map.of("type", "TextBlock", "text", String.format("**Score:** %d/10", result.getOverallScore())),
                    Map.of("type", "TextBlock", "text", String.format("**Summary:** %s", result.getSummary())),
                    Map.of("type", "TextBlock", "text", String.format("**Issues Found:** %d", 
                        result.getIssues() != null ? result.getIssues().size() : 0)),
                    Map.of("type", "TextBlock", "text", String.format("**Suggestions:** %d", 
                        result.getSuggestions() != null ? result.getSuggestions().size() : 0))
                )
            )
        )));
        
        return card.toString();
    }

    private String buildDiffReviewTeamsContent(CodeReviewResult result, String repositoryName, String pullRequestTitle) {
        Map<String, Object> card = new HashMap<>();
        card.put("type", "message");
        card.put("attachments", List.of(Map.of(
            "contentType", "application/vnd.microsoft.card.adaptive",
            "content", Map.of(
                "type", "AdaptiveCard",
                "version", "1.0",
                "body", List.of(
                    Map.of("type", "TextBlock", "text", "🤖 AI Pull Request Review", "weight", "bolder", "size", "large"),
                    Map.of("type", "TextBlock", "text", String.format("**Repository:** %s", repositoryName)),
                    Map.of("type", "TextBlock", "text", String.format("**PR:** %s", pullRequestTitle)),
                    Map.of("type", "TextBlock", "text", String.format("**Score:** %d/10", result.getOverallScore())),
                    Map.of("type", "TextBlock", "text", String.format("**Summary:** %s", result.getSummary())),
                    Map.of("type", "TextBlock", "text", String.format("**Concerns:** %d", 
                        result.getIssues() != null ? result.getIssues().size() : 0)),
                    Map.of("type", "TextBlock", "text", String.format("**Recommendations:** %d", 
                        result.getSuggestions() != null ? result.getSuggestions().size() : 0))
                )
            )
        )));
        
        return card.toString();
    }

    private String getSeverityColor(CodeReviewResult.IssueSeverity severity) {
        return switch (severity) {
            case CRITICAL -> "#dc3545";
            case HIGH -> "#fd7e14";
            case MEDIUM -> "#ffc107";
            case LOW -> "#28a745";
        };
    }

    private String getPriorityColor(CodeReviewResult.SuggestionPriority priority) {
        return switch (priority) {
            case HIGH -> "#007bff";
            case MEDIUM -> "#6c757d";
            case LOW -> "#28a745";
        };
    }
} 