package tn.esprithub.server.github.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tn.esprithub.server.repository.entity.Repository;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.user.repository.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.webhook.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class GitHubRepositoryScheduler {
    
    private final UserRepository userRepository;
    private final GitHubRepositoryFetchService gitHubRepositoryFetchService;
    private final GitHubWebhookService gitHubWebhookService;
    
    /**
     * Smart repository fetch - only fetches if data is stale (every 24 hours)
     */
    @Scheduled(fixedRate = 86400000) // 24 hours in milliseconds
    public void smartRepositoryFetch() {
        log.info("🧠 Starting SMART repository fetch (only for stale data)");

        try {
            List<User> usersWithTokens = userRepository.findUsersWithGitHubTokens();
            log.info("Found {} users with GitHub tokens", usersWithTokens.size());

            int checkedCount = 0;
            int fetchedCount = 0;
            int skippedCount = 0;
            int errorCount = 0;

            for (User user : usersWithTokens) {
                try {
                    checkedCount++;

                    // Only fetch if repositories are stale or missing
                    List<Repository> repositories = gitHubRepositoryFetchService.fetchAndSaveUserRepositories(user, false);

                    if (repositories.isEmpty()) {
                        skippedCount++;
                        log.debug("⏭️ Skipped user: {} - no repositories or recently synced", user.getEmail());
                    } else {
                        fetchedCount++;
                        log.debug("🔄 Fetched {} repositories for user: {}", repositories.size(), user.getEmail());
                    }

                } catch (Exception e) {
                    errorCount++;
                    log.error("❌ Error checking repositories for user: {} ({})", user.getEmail(), user.getGithubUsername(), e);
                }
            }

            log.info("✅ Smart fetch completed. Checked: {}, Fetched: {}, Skipped: {}, Errors: {}",
                   checkedCount, fetchedCount, skippedCount, errorCount);

        } catch (Exception e) {
            log.error("Error during smart repository fetch", e);
        }
    }

    /**
     * Legacy method - fetch repositories for all users (force refresh)
     */
    public void fetchRepositoriesForAllUsers() {
        log.info("🔄 Starting FORCED repository fetch for all users");

        try {
            List<User> usersWithTokens = userRepository.findUsersWithGitHubTokens();
            log.info("Found {} users with GitHub tokens", usersWithTokens.size());

            int successCount = 0;
            int errorCount = 0;

            for (User user : usersWithTokens) {
                try {
                    log.debug("Fetching repositories for user: {} ({})", user.getEmail(), user.getGithubUsername());

                    // Force refresh
                    List<Repository> repositories = gitHubRepositoryFetchService.fetchAndSaveUserRepositories(user, true);

                    successCount++;
                    log.debug("Successfully processed {} repositories for user: {}", repositories.size(), user.getEmail());

                } catch (Exception e) {
                    errorCount++;
                    log.error("Error fetching repositories for user: {} ({})", user.getEmail(), user.getGithubUsername(), e);
                }
            }

            log.info("Completed forced repository fetch. Success: {}, Errors: {}", successCount, errorCount);

        } catch (Exception e) {
            log.error("Error during forced repository fetch", e);
        }
    }
    
    /**
     * Fetch repositories for a specific user (can be called manually)
     */
    public void fetchRepositoriesForUser(String userEmail) {
        log.info("Fetching repositories for specific user: {}", userEmail);
        
        try {
            User user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userEmail));
            
            if (user.getGithubToken() == null || user.getGithubToken().isBlank()) {
                log.warn("User {} does not have a GitHub token", userEmail);
                return;
            }
            
            List<Repository> repositories = gitHubRepositoryFetchService.fetchAndSaveUserRepositories(user);
            
            // Note: Webhook subscription is disabled in development
            // Enable in production with proper public webhook URL
            
            log.info("Successfully fetched {} repositories for user: {}", repositories.size(), userEmail);
            
        } catch (Exception e) {
            log.error("Error fetching repositories for user: {}", userEmail, e);
        }
    }
    
    /**
     * Fetch repositories for all users immediately (manual trigger)
     */
    public void fetchRepositoriesForAllUsersNow() {
        log.info("Manual trigger: fetching repositories for all users");
        fetchRepositoriesForAllUsers();
    }
}
