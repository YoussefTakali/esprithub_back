package tn.esprithub.server.github.webhook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprithub.server.notification.NotificationService;
import tn.esprithub.server.ai.CodeReviewService;
import tn.esprithub.server.ai.dto.CodeReviewResult;
import tn.esprithub.server.github.service.GitHubWebhookService;
import tn.esprithub.server.github.service.RepositoryDataSyncService;
import tn.esprithub.server.project.entity.Group;
import tn.esprithub.server.project.repository.GroupRepository;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.user.repository.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/github/webhook")
@RequiredArgsConstructor
@Slf4j
public class GitHubWebhookController {

    private final NotificationService notificationService;
    private final CodeReviewService codeReviewService;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final GitHubWebhookService gitHubWebhookService;
    private final RepositoryDataSyncService repositoryDataSyncService;

    /**
     * Endpoint pour recevoir les webhooks GitHub
     */
    @PostMapping
    public ResponseEntity<String> handleGitHubWebhook(
            @RequestHeader("X-GitHub-Event") String eventType,
            @RequestHeader("X-GitHub-Delivery") String deliveryId,
            @RequestHeader(value = "X-GitHub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Hook-ID", required = false) String hookId,
            @RequestBody String rawPayload) {

        log.info("Received GitHub webhook: {} - {}", eventType, deliveryId);

        Map<String, Object> payload = null;
        Map<String, Object> repository = null;
        String repositoryFullName = null;
        Long repositoryGithubId = null;

        try {
            // Validate webhook signature if provided
            if (signature != null && !gitHubWebhookService.validateWebhookSignature(signature, rawPayload)) {
                log.warn("Invalid webhook signature for delivery: {}", deliveryId);
                return ResponseEntity.status(401).body("Invalid signature");
            }

            // Parse payload
            payload = new com.fasterxml.jackson.databind.ObjectMapper().readValue(rawPayload, Map.class);

            // Extract repository info for downstream processing and delivery tracking
            @SuppressWarnings("unchecked")
            Map<String, Object> repositoryNode = (Map<String, Object>) payload.get("repository");
            repository = repositoryNode;
            repositoryFullName = repository != null ? (String) repository.get("full_name") : null;
            repositoryGithubId = extractRepositoryGithubId(repository);

            updateDeliveryStatus(true, null, hookId, repositoryGithubId, repositoryFullName);

            // Extract repository name for data sync

            switch (eventType) {
                case "push":
                    handlePushEvent(payload);
                    break;
                case "pull_request":
                    handlePullRequestEvent(payload);
                    break;
                case "issues":
                    handleIssueEvent(payload);
                    break;
                case "create":
                    handleCreateEvent(payload);
                    break;
                case "delete":
                    handleDeleteEvent(payload);
                    break;
                case "release":
                    handleReleaseEvent(payload);
                    break;
                case "fork":
                    handleForkEvent(payload);
                    break;
                case "watch":
                    handleWatchEvent(payload);
                    break;
                default:
                    log.info("Unhandled GitHub event type: {}", eventType);
            }

            // Sync repository data after processing the event
            if (repositoryFullName != null) {
                try {
                    repositoryDataSyncService.syncRepositoryData(repositoryFullName, eventType, payload);
                } catch (Exception e) {
                    log.error("Error syncing repository data for: {}", repositoryFullName, e);
                    // Don't fail the webhook processing if sync fails
                }
            }

            return ResponseEntity.ok("Webhook processed successfully");

        } catch (Exception e) {
            log.error("Error processing GitHub webhook", e);

            updateDeliveryStatus(false, e.getMessage(), hookId, repositoryGithubId, repositoryFullName);

            return ResponseEntity.internalServerError().body("Error processing webhook");
        }
    }

    /**
     * Gère les événements de push
     */
    private void handlePushEvent(Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        Map<String, Object> repository = (Map<String, Object>) payload.get("repository");
        @SuppressWarnings("unchecked")
        Map<String, Object> pusher = (Map<String, Object>) payload.get("pusher");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> commits = (List<Map<String, Object>>) payload.get("commits");
        
        if (repository == null || commits == null || commits.isEmpty()) {
            log.warn("Invalid push event payload");
            return;
        }
        
        String repositoryName = (String) repository.get("full_name");
        String branch = (String) payload.get("ref");
        String authorName = (String) pusher.get("name");
        
        // Traiter chaque commit
        for (Map<String, Object> commit : commits) {
            String commitMessage = (String) commit.get("message");
            String commitId = (String) commit.get("id");
            
            log.info("Processing push: {} - {} - {}", repositoryName, branch, commitMessage);
            
            // Récupérer les destinataires pour ce repository
            List<String> recipientEmails = getRecipientsForRepository(repositoryName);
            
            if (!recipientEmails.isEmpty()) {
                notificationService.sendGitHubEventNotification(
                    "push", repositoryName, branch, commitMessage, authorName, recipientEmails
                );
                
                // Analyse IA du code si des fichiers ont été modifiés
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> modifiedFiles = (List<Map<String, Object>>) commit.get("modified");
                if (modifiedFiles != null && !modifiedFiles.isEmpty()) {
                    analyzeModifiedFiles(modifiedFiles, repositoryName, commitMessage);
                }
            }
        }
    }

    /**
     * Gère les événements de pull request
     */
    private void handlePullRequestEvent(Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        Map<String, Object> repository = (Map<String, Object>) payload.get("repository");
        @SuppressWarnings("unchecked")
        Map<String, Object> pullRequest = (Map<String, Object>) payload.get("pull_request");
        String action = (String) payload.get("action");
        
        if (repository == null || pullRequest == null) {
            log.warn("Invalid pull request event payload");
            return;
        }
        
        String repositoryName = (String) repository.get("full_name");
        String title = (String) pullRequest.get("title");
        String state = (String) pullRequest.get("state");
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) pullRequest.get("user");
        String authorName = user != null ? (String) user.get("login") : "Unknown";
        
        log.info("Processing pull request: {} - {} - {} - {}", repositoryName, action, state, title);
        
        // Récupérer les destinataires pour ce repository
        List<String> recipientEmails = getRecipientsForRepository(repositoryName);
        
        if (!recipientEmails.isEmpty()) {
            String eventDescription = String.format("Pull Request %s: %s (%s)", action, title, state);
            notificationService.sendGitHubEventNotification(
                "pull_request", repositoryName, "main", eventDescription, authorName, recipientEmails
            );
        }
    }

    /**
     * Gère les événements d'issues
     */
    private void handleIssueEvent(Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        Map<String, Object> repository = (Map<String, Object>) payload.get("repository");
        @SuppressWarnings("unchecked")
        Map<String, Object> issue = (Map<String, Object>) payload.get("issue");
        String action = (String) payload.get("action");
        
        if (repository == null || issue == null) {
            log.warn("Invalid issue event payload");
            return;
        }
        
        String repositoryName = (String) repository.get("full_name");
        String title = (String) issue.get("title");
        String state = (String) issue.get("state");
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) issue.get("user");
        String authorName = user != null ? (String) user.get("login") : "Unknown";
        
        log.info("Processing issue: {} - {} - {} - {}", repositoryName, action, state, title);
        
        // Récupérer les destinataires pour ce repository
        List<String> recipientEmails = getRecipientsForRepository(repositoryName);
        
        if (!recipientEmails.isEmpty()) {
            String eventDescription = String.format("Issue %s: %s (%s)", action, title, state);
            notificationService.sendGitHubEventNotification(
                "issue", repositoryName, "main", eventDescription, authorName, recipientEmails
            );
        }
    }

    /**
     * Récupère les destinataires pour un repository donné
     */
    private List<String> getRecipientsForRepository(String repositoryName) {
        try {
            // Chercher les groupes qui ont ce repository
            List<Group> groups = groupRepository.findByRepositoryName(repositoryName);
            
            return groups.stream()
                    .flatMap(group -> {
                        List<String> emails = new java.util.ArrayList<>();
                        
                        // Ajouter les étudiants du groupe
                        if (group.getStudents() != null) {
                            emails.addAll(group.getStudents().stream()
                                    .map(User::getEmail)
                                    .collect(Collectors.toList()));
                        }
                        
                        // Ajouter le créateur du projet
                        if (group.getProject() != null && group.getProject().getCreatedBy() != null) {
                            emails.add(group.getProject().getCreatedBy().getEmail());
                        }
                        
                        return emails.stream();
                    })
                    .distinct()
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            log.error("Error getting recipients for repository: {}", repositoryName, e);
            return List.of();
        }
    }

    /**
     * Analyse les fichiers modifiés avec l'IA
     */
    private void analyzeModifiedFiles(List<Map<String, Object>> modifiedFiles, String repositoryName, String commitMessage) {
        try {
            for (Map<String, Object> file : modifiedFiles) {
                String fileName = (String) file.get("filename");
                String status = (String) file.get("status");
                
                // Analyser seulement les fichiers de code
                if (isCodeFile(fileName) && "modified".equals(status)) {
                    log.info("Analyzing modified file: {} in repository: {}", fileName, repositoryName);
                    
                    // Récupérer le contenu du fichier (cela nécessiterait une API GitHub)
                    // Pour l'instant, on analyse le message de commit
                    String language = getLanguageFromFileName(fileName);
                    String context = String.format("File modified in commit: %s", commitMessage);
                    
                    // Analyse basique du message de commit
                    try {
                        CodeReviewResult result = codeReviewService.analyzeCode(
                            commitMessage, 
                            language, 
                            context
                        );
                        
                        if (result.isSuccess()) {
                            log.info("AI analysis completed for file: {} - Score: {}/10", 
                                    fileName, result.getOverallScore());
                        } else {
                            log.warn("AI analysis failed for file: {} - {}", fileName, result.getMessage());
                        }
                    } catch (Exception e) {
                        log.error("Error during AI analysis for file: {}", fileName, e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error analyzing modified files", e);
        }
    }

    /**
     * Détermine si un fichier est un fichier de code
     */
    private boolean isCodeFile(String fileName) {
        if (fileName == null) return false;
        
        String[] codeExtensions = {
            ".java", ".js", ".ts", ".py", ".cpp", ".c", ".cs", ".php", ".rb", ".go", 
            ".rs", ".swift", ".kt", ".scala", ".clj", ".hs", ".ml", ".f90", ".m", ".pl"
        };
        
        String lowerFileName = fileName.toLowerCase();
        for (String ext : codeExtensions) {
            if (lowerFileName.endsWith(ext)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Détermine le langage de programmation basé sur l'extension du fichier
     */
    private String getLanguageFromFileName(String fileName) {
        if (fileName == null) return "text";
        
        String lowerFileName = fileName.toLowerCase();
        
        if (lowerFileName.endsWith(".java")) return "java";
        if (lowerFileName.endsWith(".js")) return "javascript";
        if (lowerFileName.endsWith(".ts")) return "typescript";
        if (lowerFileName.endsWith(".py")) return "python";
        if (lowerFileName.endsWith(".cpp") || lowerFileName.endsWith(".cc")) return "cpp";
        if (lowerFileName.endsWith(".c")) return "c";
        if (lowerFileName.endsWith(".cs")) return "csharp";
        if (lowerFileName.endsWith(".php")) return "php";
        if (lowerFileName.endsWith(".rb")) return "ruby";
        if (lowerFileName.endsWith(".go")) return "go";
        if (lowerFileName.endsWith(".rs")) return "rust";
        if (lowerFileName.endsWith(".swift")) return "swift";
        if (lowerFileName.endsWith(".kt")) return "kotlin";
        if (lowerFileName.endsWith(".scala")) return "scala";
        if (lowerFileName.endsWith(".clj")) return "clojure";
        if (lowerFileName.endsWith(".hs")) return "haskell";
        if (lowerFileName.endsWith(".ml")) return "ocaml";
        if (lowerFileName.endsWith(".f90")) return "fortran";
        if (lowerFileName.endsWith(".m")) return "objective-c";
        if (lowerFileName.endsWith(".pl")) return "perl";
        
        return "text";
    }

    private Long extractRepositoryGithubId(Map<String, Object> repositoryNode) {
        if (repositoryNode == null) {
            return null;
        }

        try {
            Object idValue = repositoryNode.get("id");
            if (idValue instanceof Number) {
                return ((Number) idValue).longValue();
            }
            if (idValue instanceof String idText && !idText.isBlank()) {
                return Long.parseLong(idText);
            }
        } catch (Exception e) {
            log.debug("Could not parse repository GitHub id", e);
        }
        return null;
    }

    private void updateDeliveryStatus(boolean success, String error, String hookId, Long repositoryGithubId, String repositoryFullName) {
        try {
            if (hookId != null && !hookId.isBlank()) {
                gitHubWebhookService.updateWebhookDelivery(hookId, success, error);
            } else if (repositoryGithubId != null || (repositoryFullName != null && !repositoryFullName.isBlank())) {
                gitHubWebhookService.updateWebhookDeliveryByRepository(repositoryGithubId, repositoryFullName, success, error);
            }
        } catch (Exception ex) {
            log.debug("Unable to update webhook delivery status", ex);
        }
    }

    /**
     * Handle branch/tag creation events
     */
    private void handleCreateEvent(Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        Map<String, Object> repository = (Map<String, Object>) payload.get("repository");
        String refType = (String) payload.get("ref_type");
        String ref = (String) payload.get("ref");

        if (repository == null) {
            log.warn("Invalid create event payload");
            return;
        }

        String repositoryName = (String) repository.get("full_name");
        log.info("Processing create event: {} {} created in {}", refType, ref, repositoryName);

        List<String> recipientEmails = getRecipientsForRepository(repositoryName);
        if (!recipientEmails.isEmpty()) {
            String eventDescription = String.format("%s '%s' created",
                    refType.substring(0, 1).toUpperCase() + refType.substring(1), ref);
            notificationService.sendGitHubEventNotification(
                "create", repositoryName, ref, eventDescription, "System", recipientEmails
            );
        }
    }

    /**
     * Handle branch/tag deletion events
     */
    private void handleDeleteEvent(Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        Map<String, Object> repository = (Map<String, Object>) payload.get("repository");
        String refType = (String) payload.get("ref_type");
        String ref = (String) payload.get("ref");

        if (repository == null) {
            log.warn("Invalid delete event payload");
            return;
        }

        String repositoryName = (String) repository.get("full_name");
        log.info("Processing delete event: {} {} deleted from {}", refType, ref, repositoryName);

        List<String> recipientEmails = getRecipientsForRepository(repositoryName);
        if (!recipientEmails.isEmpty()) {
            String eventDescription = String.format("%s '%s' deleted",
                    refType.substring(0, 1).toUpperCase() + refType.substring(1), ref);
            notificationService.sendGitHubEventNotification(
                "delete", repositoryName, ref, eventDescription, "System", recipientEmails
            );
        }
    }

    /**
     * Handle release events
     */
    private void handleReleaseEvent(Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        Map<String, Object> repository = (Map<String, Object>) payload.get("repository");
        @SuppressWarnings("unchecked")
        Map<String, Object> release = (Map<String, Object>) payload.get("release");
        String action = (String) payload.get("action");

        if (repository == null || release == null) {
            log.warn("Invalid release event payload");
            return;
        }

        String repositoryName = (String) repository.get("full_name");
        String tagName = (String) release.get("tag_name");
        String releaseName = (String) release.get("name");

        log.info("Processing release event: {} - {} {} in {}", action, tagName, releaseName, repositoryName);

        List<String> recipientEmails = getRecipientsForRepository(repositoryName);
        if (!recipientEmails.isEmpty()) {
            String eventDescription = String.format("Release %s: %s (%s)", action, releaseName, tagName);
            notificationService.sendGitHubEventNotification(
                "release", repositoryName, tagName, eventDescription, "System", recipientEmails
            );
        }
    }

    /**
     * Handle fork events
     */
    private void handleForkEvent(Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        Map<String, Object> repository = (Map<String, Object>) payload.get("repository");
        @SuppressWarnings("unchecked")
        Map<String, Object> forkee = (Map<String, Object>) payload.get("forkee");

        if (repository == null || forkee == null) {
            log.warn("Invalid fork event payload");
            return;
        }

        String repositoryName = (String) repository.get("full_name");
        String forkeeName = (String) forkee.get("full_name");

        log.info("Processing fork event: {} forked to {}", repositoryName, forkeeName);

        List<String> recipientEmails = getRecipientsForRepository(repositoryName);
        if (!recipientEmails.isEmpty()) {
            String eventDescription = String.format("Repository forked to %s", forkeeName);
            notificationService.sendGitHubEventNotification(
                "fork", repositoryName, "main", eventDescription, "System", recipientEmails
            );
        }
    }

    /**
     * Handle watch (star) events
     */
    private void handleWatchEvent(Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        Map<String, Object> repository = (Map<String, Object>) payload.get("repository");
        String action = (String) payload.get("action");

        if (repository == null) {
            log.warn("Invalid watch event payload");
            return;
        }

        String repositoryName = (String) repository.get("full_name");
        log.info("Processing watch event: {} - {}", action, repositoryName);

        // Watch events are usually less critical, so we might not notify for all of them
        if ("started".equals(action)) {
            List<String> recipientEmails = getRecipientsForRepository(repositoryName);
            if (!recipientEmails.isEmpty()) {
                String eventDescription = "Repository starred";
                notificationService.sendGitHubEventNotification(
                    "watch", repositoryName, "main", eventDescription, "System", recipientEmails
                );
            }
        }
    }

    /**
     * Endpoint de test pour vérifier que le webhook fonctionne
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, String>> testWebhook() {
        return ResponseEntity.ok(Map.of(
            "status", "Webhook endpoint is working",
            "timestamp", java.time.LocalDateTime.now().toString()
        ));
    }

    /**
     * Get webhook status for repository
     */
    @GetMapping("/repository/{repositoryId}/status")
    public ResponseEntity<Map<String, Object>> getWebhookStatus(@PathVariable String repositoryId) {
        log.info("🔍 Getting webhook status for repository: {}", repositoryId);
        
        try {
            // For now, return a basic status - this can be enhanced later
            Map<String, Object> status = Map.of(
                "repositoryId", repositoryId,
                "webhookActive", true,
                "lastEventReceived", System.currentTimeMillis(),
                "eventsProcessed", 0,
                "status", "active"
            );
            
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("❌ Error getting webhook status for repository: {}", repositoryId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to get webhook status",
                "message", e.getMessage()
            ));
        }
    }
}