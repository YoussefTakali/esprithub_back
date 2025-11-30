package tn.esprithub.server.github.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.esprithub.server.config.properties.WebhookProperties;
import tn.esprithub.server.github.service.GitHubWebhookService;
import tn.esprithub.server.repository.entity.Repository;
import tn.esprithub.server.repository.entity.WebhookSubscription;
import tn.esprithub.server.repository.repository.RepositoryEntityRepository;
import tn.esprithub.server.repository.repository.WebhookSubscriptionRepository;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.webhook.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class WebhookSubscriptionScheduler {
    
    private final GitHubWebhookService gitHubWebhookService;
    private final RepositoryEntityRepository repositoryRepository;
    private final WebhookSubscriptionRepository webhookSubscriptionRepository;
    private final UserRepository userRepository;
    private final WebhookProperties webhookProperties;
    
    /**
     * Scheduled task to check and subscribe to webhooks for new repositories
     * Runs every 30 minutes by default (configurable)
     */
    @Scheduled(fixedRateString = "#{${app.webhook.scheduling.subscription-check-interval:30} * 60 * 1000}")
    @Transactional
    public void checkAndSubscribeToNewRepositories() {
        log.info("Starting webhook subscription check for new repositories");
        
        try {
            // Find all active repositories without webhook subscriptions
            List<Repository> repositoriesWithoutWebhooks = repositoryRepository.findRepositoriesWithoutWebhooks();
            
            log.info("Found {} repositories without webhook subscriptions", repositoriesWithoutWebhooks.size());
            
            int successCount = 0;
            int failureCount = 0;
            
            for (Repository repository : repositoriesWithoutWebhooks) {
                try {
                    User owner = repository.getOwner();
                    
                    // Skip if user doesn't have GitHub token
                    if (owner.getGithubToken() == null || owner.getGithubToken().isBlank()) {
                        log.debug("Skipping repository {} - owner {} has no GitHub token", 
                                repository.getFullName(), owner.getEmail());
                        continue;
                    }
                    
                    // Skip if user is not active
                    if (!owner.getIsActive()) {
                        log.debug("Skipping repository {} - owner {} is not active", 
                                repository.getFullName(), owner.getEmail());
                        continue;
                    }
                    
                    log.info("Subscribing to webhook for repository: {} (owner: {})", 
                            repository.getFullName(), owner.getEmail());
                    
                    gitHubWebhookService.subscribeToRepository(repository, owner);
                    successCount++;
                    
                    // Add small delay to avoid rate limiting
                    Thread.sleep(1000);
                    
                } catch (Exception e) {
                    log.error("Failed to subscribe to webhook for repository: {}", 
                            repository.getFullName(), e);
                    failureCount++;
                }
            }
            
            log.info("Webhook subscription check completed. Success: {}, Failures: {}", 
                    successCount, failureCount);
                    
        } catch (Exception e) {
            log.error("Error during webhook subscription check", e);
        }
    }
    
    /**
     * Scheduled task to perform health checks on existing webhooks
     * Runs every 6 hours by default (configurable)
     */
    @Scheduled(fixedRateString = "#{${app.webhook.scheduling.health-check-interval:6} * 60 * 60 * 1000}")
    @Transactional
    public void performWebhookHealthCheck() {
        log.info("Starting webhook health check");
        
        try {
            // Find stale webhooks (no ping in last 24 hours)
            LocalDateTime threshold = LocalDateTime.now().minusHours(24);
            List<WebhookSubscription> staleWebhooks = webhookSubscriptionRepository.findStaleWebhooks(threshold);
            
            log.info("Found {} stale webhooks", staleWebhooks.size());
            
            for (WebhookSubscription subscription : staleWebhooks) {
                try {
                    log.warn("Webhook for repository {} appears stale (last ping: {})", 
                            subscription.getRepository().getFullName(), subscription.getLastPing());
                    
                    // You could implement ping webhook functionality here
                    // For now, just log the issue
                    
                } catch (Exception e) {
                    log.error("Error checking webhook health for repository: {}", 
                            subscription.getRepository().getFullName(), e);
                }
            }
            
            // Find failed webhooks and attempt retry
            List<WebhookSubscription> failedWebhooks = webhookSubscriptionRepository
                    .findByStatus(WebhookSubscription.WebhookStatus.FAILED);
            
            log.info("Found {} failed webhooks to retry", failedWebhooks.size());
            
            int retrySuccessCount = 0;
            int retryFailureCount = 0;
            
            for (WebhookSubscription subscription : failedWebhooks) {
                try {
                    // Only retry if failure count is less than max attempts
                    if (subscription.getFailureCount() < webhookProperties.getRetry().getMaxAttempts()) {
                        log.info("Retrying webhook subscription for repository: {}", 
                                subscription.getRepository().getFullName());
                        
                        User owner = subscription.getRepository().getOwner();
                        gitHubWebhookService.subscribeToRepository(subscription.getRepository(), owner);
                        retrySuccessCount++;
                    } else {
                        log.warn("Max retry attempts reached for webhook subscription: {}", 
                                subscription.getRepository().getFullName());
                    }
                    
                } catch (Exception e) {
                    log.error("Failed to retry webhook subscription for repository: {}", 
                            subscription.getRepository().getFullName(), e);
                    retryFailureCount++;
                }
            }
            
            log.info("Webhook health check completed. Retry success: {}, Retry failures: {}", 
                    retrySuccessCount, retryFailureCount);
                    
        } catch (Exception e) {
            log.error("Error during webhook health check", e);
        }
    }
    
    /**
     * Scheduled task to clean up old webhook data
     * Runs daily at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupOldWebhookData() {
        log.info("Starting webhook data cleanup");
        
        try {
            // Find webhooks that have been inactive for more than 30 days
            LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
            
            List<WebhookSubscription> inactiveWebhooks = webhookSubscriptionRepository
                    .findByStatus(WebhookSubscription.WebhookStatus.INACTIVE);
            
            int cleanedCount = 0;
            
            for (WebhookSubscription subscription : inactiveWebhooks) {
                if (subscription.getUpdatedAt().isBefore(cutoff)) {
                    log.info("Cleaning up old inactive webhook for repository: {}", 
                            subscription.getRepository().getFullName());
                    
                    webhookSubscriptionRepository.delete(subscription);
                    cleanedCount++;
                }
            }
            
            log.info("Webhook cleanup completed. Cleaned {} old webhooks", cleanedCount);
            
        } catch (Exception e) {
            log.error("Error during webhook cleanup", e);
        }
    }
}
