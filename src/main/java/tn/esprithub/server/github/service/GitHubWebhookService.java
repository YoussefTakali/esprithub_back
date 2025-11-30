package tn.esprithub.server.github.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tn.esprithub.server.common.exception.BusinessException;
import tn.esprithub.server.config.properties.WebhookProperties;
import tn.esprithub.server.repository.entity.Repository;
import tn.esprithub.server.repository.entity.WebhookSubscription;
import tn.esprithub.server.repository.repository.WebhookSubscriptionRepository;
import tn.esprithub.server.user.entity.User;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubWebhookService {
    
    private static final String GITHUB_API_BASE = "https://api.github.com";
    private static final String HMAC_SHA256 = "HmacSHA256";
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final WebhookProperties webhookProperties;
    private final WebhookSubscriptionRepository webhookSubscriptionRepository;
    
    /**
     * Subscribe to webhook for a repository
     */
    public WebhookSubscription subscribeToRepository(Repository repository, User user) {
        log.info("Checking webhook subscription for repository: {}", repository.getFullName());

        if (user.getGithubToken() == null || user.getGithubToken().isBlank()) {
            throw new BusinessException("GitHub token not found for user: " + user.getEmail());
        }

        // Check if webhook already exists
        Optional<WebhookSubscription> existing = webhookSubscriptionRepository.findByRepositoryId(repository.getId());
        if (existing.isPresent() && existing.get().getStatus() == WebhookSubscription.WebhookStatus.ACTIVE) {
            log.debug("Webhook already exists for repository: {}", repository.getFullName());
            return existing.get();
        }

        // Check if webhook URL is publicly accessible (skip localhost in development)
        String webhookUrl = webhookProperties.getFullWebhookUrl();
        if (isLocalhostUrl(webhookUrl)) {
            log.warn("⚠️ Skipping webhook subscription for {} - localhost URL not accessible to GitHub", repository.getFullName());
            return createPlaceholderSubscription(repository, existing, "LOCALHOST", "Localhost URL not accessible to GitHub");
        }

        // Validate repository access before creating webhook
        try {
            String[] parts = repository.getFullName().split("/");
            if (parts.length != 2) {
                log.warn("Invalid repository name format: {}", repository.getFullName());
                return createPlaceholderSubscription(repository, existing, "INVALID", "Invalid repository name format");
            }

            if (!validateRepositoryAccess(parts[0], parts[1], user.getGithubToken())) {
                log.warn("⚠️ Skipping webhook subscription for {} - no admin access or repository not found", repository.getFullName());
                return createPlaceholderSubscription(repository, existing, "NOACCESS", "No admin access to repository or repository not found");
            }
        } catch (Exception e) {
            log.warn("⚠️ Error validating repository access for {}: {}", repository.getFullName(), e.getMessage());
            return createPlaceholderSubscription(repository, existing, "ERROR", "Error validating repository access: " + e.getMessage());
        }
        
        try {
            String[] parts = repository.getFullName().split("/");
            if (parts.length != 2) {
                throw new BusinessException("Invalid repository fullName format: " + repository.getFullName());
            }
            
            String owner = parts[0];
            String repo = parts[1];
            
            // Create webhook via GitHub API
            String webhookId = createGitHubWebhook(owner, repo, user.getGithubToken());
            
            // Save or update webhook subscription
            WebhookSubscription subscription = existing.orElse(WebhookSubscription.builder()
                    .repository(repository)
                    .build());
                    
            subscription.setWebhookId(webhookId);
            subscription.setWebhookUrl(webhookProperties.getFullWebhookUrl());
            subscription.setStatus(WebhookSubscription.WebhookStatus.ACTIVE);
            subscription.setEvents(String.join(",", webhookProperties.getEvents()));
            subscription.setSecretHash(generateSecretHash());
            subscription.setSubscriptionDate(LocalDateTime.now());
            subscription.setFailureCount(0);
            subscription.setLastError(null);
            
            WebhookSubscription saved = webhookSubscriptionRepository.save(subscription);
            log.info("Successfully subscribed to webhook for repository: {} with ID: {}", 
                    repository.getFullName(), webhookId);
            
            return saved;
            
        } catch (Exception e) {
            log.error("❌ Failed to subscribe to webhook for repository: {}", repository.getFullName(), e);

            // Update existing subscription with error or create new failed subscription
            WebhookSubscription failedSubscription = createPlaceholderSubscription(
                repository, existing, "FAILED", e.getMessage());

            throw new BusinessException("Failed to subscribe to webhook: " + e.getMessage());
        }
    }

    /**
     * Check if a URL is a localhost URL
     */
    private boolean isLocalhostUrl(String url) {
        return url != null && (
            url.contains("localhost") ||
            url.contains("127.0.0.1") ||
            url.contains("0.0.0.0") ||
            url.startsWith("http://192.168.")
        );
    }

    /**
     * Create a placeholder subscription for tracking
     */
    private WebhookSubscription createPlaceholderSubscription(
            Repository repository,
            Optional<WebhookSubscription> existing,
            String statusPrefix,
            String errorMessage) {

        WebhookSubscription subscription = existing.orElse(WebhookSubscription.builder()
                .repository(repository)
                .build());

        subscription.setWebhookId(statusPrefix + "-" + System.currentTimeMillis());
        subscription.setWebhookUrl(webhookProperties.getFullWebhookUrl());
        subscription.setStatus(WebhookSubscription.WebhookStatus.FAILED);
        subscription.setEvents(String.join(",", webhookProperties.getEvents()));

        if (subscription.getSecretHash() == null) {
            subscription.setSecretHash(generateSecretHash());
        }

        if (subscription.getSubscriptionDate() == null) {
            subscription.setSubscriptionDate(LocalDateTime.now());
        }

        subscription.setLastError(errorMessage);
        subscription.setFailureCount(subscription.getFailureCount() + 1);

        return webhookSubscriptionRepository.save(subscription);
    }

    /**
     * Validate if user has admin access to repository
     */
    private boolean validateRepositoryAccess(String owner, String repo, String token) {
        try {
            String url = GITHUB_API_BASE + "/repos/" + owner + "/" + repo;

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "token " + token);
            headers.set("Accept", "application/vnd.github.v3+json");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                // Check if user has admin permissions
                JsonNode repoData = objectMapper.readTree(response.getBody());
                JsonNode permissions = repoData.get("permissions");

                if (permissions != null && permissions.has("admin")) {
                    return permissions.get("admin").asBoolean();
                }

                // If permissions not available, assume user has access (they can read the repo)
                return true;
            }

            return false;

        } catch (Exception e) {
            log.debug("Error validating repository access for {}/{}: {}", owner, repo, e.getMessage());
            return false;
        }
    }

    /**
     * Unsubscribe from webhook for a repository
     */
    public void unsubscribeFromRepository(Repository repository, User user) {
        log.info("Unsubscribing from webhook for repository: {}", repository.getFullName());
        
        Optional<WebhookSubscription> subscription = webhookSubscriptionRepository.findByRepositoryId(repository.getId());
        if (subscription.isEmpty()) {
            log.warn("No webhook subscription found for repository: {}", repository.getFullName());
            return;
        }
        
        try {
            String[] parts = repository.getFullName().split("/");
            String owner = parts[0];
            String repo = parts[1];
            
            // Delete webhook via GitHub API
            deleteGitHubWebhook(owner, repo, subscription.get().getWebhookId(), user.getGithubToken());
            
            // Update subscription status
            WebhookSubscription sub = subscription.get();
            sub.setStatus(WebhookSubscription.WebhookStatus.INACTIVE);
            webhookSubscriptionRepository.save(sub);
            
            log.info("Successfully unsubscribed from webhook for repository: {}", repository.getFullName());
            
        } catch (Exception e) {
            log.error("Failed to unsubscribe from webhook for repository: {}", repository.getFullName(), e);
            throw new BusinessException("Failed to unsubscribe from webhook: " + e.getMessage());
        }
    }
    
    /**
     * Validate webhook signature
     */
    public boolean validateWebhookSignature(String signature, String payload) {
        if (signature == null || !signature.startsWith("sha256=")) {
            return false;
        }
        
        try {
            String expectedSignature = "sha256=" + generateHmacSha256(payload, webhookProperties.getSecret());
            return signature.equals(expectedSignature);
        } catch (Exception e) {
            log.error("Error validating webhook signature", e);
            return false;
        }
    }
    
    /**
     * Update webhook delivery status
     */
    public void updateWebhookDelivery(String webhookId, boolean success, String error) {
        if (webhookId == null || webhookId.isBlank()) {
            return;
        }

        webhookSubscriptionRepository.findByWebhookId(webhookId)
                .ifPresent(subscription -> updateDeliveryMetadata(subscription, success, error));
    }

    /**
     * Update delivery status when GitHub hook id header is missing but repository identifiers are available.
     */
    public void updateWebhookDeliveryByRepository(Long repositoryGithubId, String repositoryFullName, boolean success, String error) {
        Optional<WebhookSubscription> subscription = Optional.empty();

        if (repositoryGithubId != null) {
            subscription = webhookSubscriptionRepository.findByRepositoryGithubId(repositoryGithubId);
        }

        if (subscription.isEmpty() && repositoryFullName != null && !repositoryFullName.isBlank()) {
            subscription = webhookSubscriptionRepository.findByRepositoryFullName(repositoryFullName);
        }

        subscription.ifPresent(sub -> updateDeliveryMetadata(sub, success, error));
    }

    private void updateDeliveryMetadata(WebhookSubscription subscription, boolean success, String error) {
        subscription.setLastDelivery(LocalDateTime.now());

        if (success) {
            subscription.setFailureCount(0);
            subscription.setLastError(null);
            if (subscription.getStatus() == WebhookSubscription.WebhookStatus.FAILED) {
                subscription.setStatus(WebhookSubscription.WebhookStatus.ACTIVE);
            }
        } else {
            int previousFailures = Optional.ofNullable(subscription.getFailureCount()).orElse(0);
            subscription.setFailureCount(previousFailures + 1);
            subscription.setLastError(error);

            if (subscription.getFailureCount() >= webhookProperties.getRetry().getMaxFailures()) {
                subscription.setStatus(WebhookSubscription.WebhookStatus.FAILED);
            }
        }

        webhookSubscriptionRepository.save(subscription);
    }
    
    private String createGitHubWebhook(String owner, String repo, String token) throws Exception {
        String url = GITHUB_API_BASE + "/repos/" + owner + "/" + repo + "/hooks";
        
        Map<String, Object> config = new HashMap<>();
        config.put("url", webhookProperties.getFullWebhookUrl());
        config.put("content_type", "json");
        config.put("secret", webhookProperties.getSecret());
        config.put("insecure_ssl", "0");
        
        Map<String, Object> webhook = new HashMap<>();
        webhook.put("name", "web");
        webhook.put("active", true);
        webhook.put("events", webhookProperties.getEvents());
        webhook.put("config", config);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        headers.set("Accept", "application/vnd.github.v3+json");
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(webhook, headers);
        
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        
        if (response.getStatusCode().is2xxSuccessful()) {
            JsonNode responseNode = objectMapper.readTree(response.getBody());
            return responseNode.get("id").asText();
        } else {
            throw new RuntimeException("GitHub API returned status: " + response.getStatusCode());
        }
    }
    
    private void deleteGitHubWebhook(String owner, String repo, String webhookId, String token) throws Exception {
        String url = GITHUB_API_BASE + "/repos/" + owner + "/" + repo + "/hooks/" + webhookId;
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("Accept", "application/vnd.github.v3+json");
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.DELETE, entity, String.class);
        
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("GitHub API returned status: " + response.getStatusCode());
        }
    }
    
    private String generateSecretHash() {
        try {
            return generateHmacSha256("webhook-secret", webhookProperties.getSecret());
        } catch (Exception e) {
            log.error("Error generating secret hash", e);
            return null;
        }
    }
    
    private String generateHmacSha256(String data, String key) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
