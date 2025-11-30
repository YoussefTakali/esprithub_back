package tn.esprithub.server.github.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.esprithub.server.common.exception.BusinessException;
import tn.esprithub.server.github.service.GitHubWebhookService;
import tn.esprithub.server.repository.entity.Repository;
import tn.esprithub.server.repository.entity.WebhookSubscription;
import tn.esprithub.server.repository.repository.RepositoryEntityRepository;
import tn.esprithub.server.repository.repository.WebhookSubscriptionRepository;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.user.repository.UserRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:4200", "http://127.0.0.1:4200"}, allowCredentials = "true")
public class WebhookManagementController {
    
    private final GitHubWebhookService gitHubWebhookService;
    private final WebhookSubscriptionRepository webhookSubscriptionRepository;
    private final RepositoryEntityRepository repositoryRepository;
    private final UserRepository userRepository;
    
    /**
     * Get webhook subscription status for all user's repositories
     */
    @GetMapping("/status")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getWebhookStatus(Authentication authentication) {
        log.info("Getting webhook status for user: {}", authentication.getName());
        
        try {
            User user = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new BusinessException("User not found"));
            
            List<WebhookSubscription> subscriptions = webhookSubscriptionRepository.findByRepositoryOwnerId(user.getId());
            
            long activeCount = subscriptions.stream()
                    .filter(ws -> ws.getStatus() == WebhookSubscription.WebhookStatus.ACTIVE)
                    .count();
            
            long failedCount = subscriptions.stream()
                    .filter(ws -> ws.getStatus() == WebhookSubscription.WebhookStatus.FAILED)
                    .count();
            
            long inactiveCount = subscriptions.stream()
                    .filter(ws -> ws.getStatus() == WebhookSubscription.WebhookStatus.INACTIVE)
                    .count();
            
            Map<String, Object> status = new HashMap<>();
            status.put("totalSubscriptions", subscriptions.size());
            status.put("activeSubscriptions", activeCount);
            status.put("failedSubscriptions", failedCount);
            status.put("inactiveSubscriptions", inactiveCount);
            status.put("subscriptions", subscriptions);
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            log.error("Error getting webhook status", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Get webhook subscriptions with pagination
     */
    @GetMapping
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<Page<WebhookSubscription>> getWebhookSubscriptions(
            Pageable pageable,
            @RequestParam(required = false) WebhookSubscription.WebhookStatus status,
            Authentication authentication) {
        
        log.info("Getting webhook subscriptions for user: {}", authentication.getName());
        
        try {
            User user = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new BusinessException("User not found"));
            
            Page<WebhookSubscription> subscriptions;
            if (status != null) {
                subscriptions = webhookSubscriptionRepository.findByOwnerIdAndStatus(user.getId(), status, pageable);
            } else {
                subscriptions = webhookSubscriptionRepository.findByRepositoryOwnerId(user.getId(), pageable);
            }
            
            return ResponseEntity.ok(subscriptions);
            
        } catch (Exception e) {
            log.error("Error getting webhook subscriptions", e);
            throw new BusinessException("Failed to get webhook subscriptions: " + e.getMessage());
        }
    }
    
    /**
     * Subscribe to webhook for a specific repository
     */
    @PostMapping("/subscribe/{repositoryId}")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> subscribeToWebhook(
            @PathVariable UUID repositoryId,
            Authentication authentication) {
        
        log.info("Subscribing to webhook for repository: {} by user: {}", repositoryId, authentication.getName());
        
        try {
            User user = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new BusinessException("User not found"));
            
            Repository repository = repositoryRepository.findById(repositoryId)
                    .orElseThrow(() -> new BusinessException("Repository not found"));
            
            // Check if user owns the repository
            if (!repository.getOwner().getId().equals(user.getId())) {
                throw new BusinessException("You can only subscribe to webhooks for your own repositories");
            }
            
            WebhookSubscription subscription = gitHubWebhookService.subscribeToRepository(repository, user);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Successfully subscribed to webhook");
            response.put("webhookId", subscription.getWebhookId());
            response.put("status", subscription.getStatus());
            response.put("repositoryName", repository.getFullName());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error subscribing to webhook", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Unsubscribe from webhook for a specific repository
     */
    @DeleteMapping("/unsubscribe/{repositoryId}")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> unsubscribeFromWebhook(
            @PathVariable UUID repositoryId,
            Authentication authentication) {
        
        log.info("Unsubscribing from webhook for repository: {} by user: {}", repositoryId, authentication.getName());
        
        try {
            User user = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new BusinessException("User not found"));
            
            Repository repository = repositoryRepository.findById(repositoryId)
                    .orElseThrow(() -> new BusinessException("Repository not found"));
            
            // Check if user owns the repository
            if (!repository.getOwner().getId().equals(user.getId())) {
                throw new BusinessException("You can only unsubscribe from webhooks for your own repositories");
            }
            
            gitHubWebhookService.unsubscribeFromRepository(repository, user);
            
            return ResponseEntity.ok(Map.of(
                "message", "Successfully unsubscribed from webhook",
                "repositoryName", repository.getFullName()
            ));
            
        } catch (Exception e) {
            log.error("Error unsubscribing from webhook", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Re-register webhook for a specific repository (useful for failed webhooks)
     */
    @PostMapping("/reregister/{repositoryId}")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> reregisterWebhook(
            @PathVariable UUID repositoryId,
            Authentication authentication) {
        
        log.info("Re-registering webhook for repository: {} by user: {}", repositoryId, authentication.getName());
        
        try {
            User user = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new BusinessException("User not found"));
            
            Repository repository = repositoryRepository.findById(repositoryId)
                    .orElseThrow(() -> new BusinessException("Repository not found"));
            
            // Check if user owns the repository
            if (!repository.getOwner().getId().equals(user.getId())) {
                throw new BusinessException("You can only re-register webhooks for your own repositories");
            }
            
            // First unsubscribe if exists
            try {
                gitHubWebhookService.unsubscribeFromRepository(repository, user);
            } catch (Exception e) {
                log.debug("No existing webhook to unsubscribe from", e);
            }
            
            // Then subscribe again
            WebhookSubscription subscription = gitHubWebhookService.subscribeToRepository(repository, user);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Successfully re-registered webhook");
            response.put("webhookId", subscription.getWebhookId());
            response.put("status", subscription.getStatus());
            response.put("repositoryName", repository.getFullName());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error re-registering webhook", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Bulk subscribe to webhooks for all user's repositories
     */
    @PostMapping("/subscribe-all")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> subscribeToAllWebhooks(Authentication authentication) {
        log.info("Bulk subscribing to webhooks for user: {}", authentication.getName());
        
        try {
            User user = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new BusinessException("User not found"));
            
            List<Repository> repositories = repositoryRepository.findByOwnerIdAndIsActiveTrue(user.getId());
            
            int successCount = 0;
            int failureCount = 0;
            
            for (Repository repository : repositories) {
                try {
                    // Check if webhook already exists
                    if (!webhookSubscriptionRepository.existsByRepositoryId(repository.getId())) {
                        gitHubWebhookService.subscribeToRepository(repository, user);
                        successCount++;
                    }
                } catch (Exception e) {
                    log.error("Failed to subscribe to webhook for repository: {}", repository.getFullName(), e);
                    failureCount++;
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Bulk webhook subscription completed");
            response.put("totalRepositories", repositories.size());
            response.put("successCount", successCount);
            response.put("failureCount", failureCount);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error in bulk webhook subscription", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Get webhook subscriptions for a specific user (admin only)
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<WebhookSubscription>> getUserWebhookSubscriptions(
            @PathVariable UUID userId,
            Pageable pageable) {

        log.info("Getting webhook subscriptions for user: {}", userId);

        try {
            Page<WebhookSubscription> subscriptions = webhookSubscriptionRepository
                    .findByRepositoryOwnerId(userId, pageable);

            return ResponseEntity.ok(subscriptions);

        } catch (Exception e) {
            log.error("Error getting webhook subscriptions for user: {}", userId, e);
            throw new BusinessException("Failed to get user webhook subscriptions: " + e.getMessage());
        }
    }

    /**
     * Subscribe to webhook for a specific repository as admin
     */
    @PostMapping("/admin/subscribe/{repositoryId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> adminSubscribeToWebhook(
            @PathVariable UUID repositoryId,
            @RequestBody Map<String, String> request) {

        String userId = request.get("userId");
        log.info("Admin subscribing to webhook for repository: {} for user: {}", repositoryId, userId);

        try {
            User user = userRepository.findById(UUID.fromString(userId))
                    .orElseThrow(() -> new BusinessException("User not found"));

            Repository repository = repositoryRepository.findById(repositoryId)
                    .orElseThrow(() -> new BusinessException("Repository not found"));

            WebhookSubscription subscription = gitHubWebhookService.subscribeToRepository(repository, user);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Successfully subscribed to webhook");
            response.put("webhookId", subscription.getWebhookId());
            response.put("status", subscription.getStatus());
            response.put("repositoryName", repository.getFullName());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error admin subscribing to webhook", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Unsubscribe from webhook for a specific repository as admin
     */
    @DeleteMapping("/admin/unsubscribe/{repositoryId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> adminUnsubscribeFromWebhook(
            @PathVariable UUID repositoryId,
            @RequestBody Map<String, String> request) {

        String userId = request.get("userId");
        log.info("Admin unsubscribing from webhook for repository: {} for user: {}", repositoryId, userId);

        try {
            User user = userRepository.findById(UUID.fromString(userId))
                    .orElseThrow(() -> new BusinessException("User not found"));

            Repository repository = repositoryRepository.findById(repositoryId)
                    .orElseThrow(() -> new BusinessException("Repository not found"));

            gitHubWebhookService.unsubscribeFromRepository(repository, user);

            return ResponseEntity.ok(Map.of(
                "message", "Successfully unsubscribed from webhook",
                "repositoryName", repository.getFullName()
            ));

        } catch (Exception e) {
            log.error("Error admin unsubscribing from webhook", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get webhook statistics (admin only)
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getWebhookStatistics() {
        log.info("Getting webhook statistics");

        try {
            long totalSubscriptions = webhookSubscriptionRepository.count();
            long activeSubscriptions = webhookSubscriptionRepository.countByStatus(WebhookSubscription.WebhookStatus.ACTIVE);
            long failedSubscriptions = webhookSubscriptionRepository.countByStatus(WebhookSubscription.WebhookStatus.FAILED);
            long inactiveSubscriptions = webhookSubscriptionRepository.countByStatus(WebhookSubscription.WebhookStatus.INACTIVE);

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalSubscriptions", totalSubscriptions);
            stats.put("activeSubscriptions", activeSubscriptions);
            stats.put("failedSubscriptions", failedSubscriptions);
            stats.put("inactiveSubscriptions", inactiveSubscriptions);

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("Error getting webhook statistics", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get webhook status for a specific repository
     */
    @GetMapping("/repository/{repositoryId}/status")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getRepositoryWebhookStatus(@PathVariable UUID repositoryId) {
        log.info("Getting webhook status for repository: {}", repositoryId);
        
        try {
            Repository repository = repositoryRepository.findById(repositoryId)
                    .orElseThrow(() -> new BusinessException("Repository not found"));
            
            WebhookSubscription subscription = webhookSubscriptionRepository
                    .findByRepositoryId(repositoryId)
                    .orElse(null);
            
            Map<String, Object> status = new HashMap<>();
            status.put("repositoryId", repositoryId);
            status.put("repositoryName", repository.getName());
            status.put("hasWebhook", subscription != null);
            
            if (subscription != null) {
                status.put("webhookId", subscription.getWebhookId());
                status.put("isActive", subscription.getStatus() == WebhookSubscription.WebhookStatus.ACTIVE);
                status.put("events", subscription.getEvents());
                status.put("createdAt", subscription.getCreatedAt());
                status.put("lastTriggered", subscription.getLastDelivery());
                status.put("failureCount", subscription.getFailureCount());
                status.put("isHealthy", subscription.getFailureCount() < 5);
            } else {
                status.put("webhookId", null);
                status.put("isActive", false);
                status.put("events", List.of());
                status.put("createdAt", null);
                status.put("lastTriggered", null);
                status.put("failureCount", 0);
                status.put("isHealthy", true);
            }
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            log.error("Error getting webhook status for repository: {}", repositoryId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to get webhook status",
                "message", e.getMessage(),
                "repositoryId", repositoryId.toString()
            ));
        }
    }
}
