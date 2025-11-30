package tn.esprithub.server.github.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import tn.esprithub.server.common.exception.BusinessException;
import tn.esprithub.server.repository.entity.CodeVersion;
import tn.esprithub.server.repository.entity.Repository;
import tn.esprithub.server.repository.repository.CodeVersionRepository;
import tn.esprithub.server.repository.repository.RepositoryEntityRepository;
import tn.esprithub.server.user.entity.User;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepositoryDataSyncService {
    
    private static final String GITHUB_API_BASE = "https://api.github.com";
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final RepositoryEntityRepository repositoryRepository;
    private final CodeVersionRepository codeVersionRepository;
    private final GitHubRepositoryDataSyncService comprehensiveDataSyncService;
    
    /**
     * Synchronize repository data when webhook is received
     */
    @Transactional
    public void syncRepositoryData(String repositoryFullName, String eventType, Map<String, Object> payload) {
        log.info("🔍 Checking webhook for repository: {} (event: {})", repositoryFullName, eventType);

        try {
            Repository repository = repositoryRepository.findByFullName(repositoryFullName)
                    .orElse(null);

            if (repository == null) {
                log.warn("Repository not found in database: {}", repositoryFullName);
                return;
            }

            User owner = repository.getOwner();
            if (owner.getGithubToken() == null || owner.getGithubToken().isBlank()) {
                log.warn("No GitHub token for repository owner: {}", owner.getEmail());
                return;
            }

            // Check if we should process this webhook (avoid unnecessary processing)
            if (!shouldProcessWebhook(repository, eventType, payload)) {
                log.info("⏭️ Skipping webhook processing - no significant changes detected");
                return;
            }

            log.info("🔄 Processing webhook for repository: {} (event: {})", repositoryFullName, eventType);
            
            switch (eventType) {
                case "push":
                    syncCommitsFromPushEvent(repository, payload, owner.getGithubToken());
                    break;
                case "create":
                    syncBranchOrTagCreation(repository, payload, owner.getGithubToken());
                    break;
                case "delete":
                    handleBranchOrTagDeletion(repository, payload);
                    break;
                case "release":
                    syncReleaseData(repository, payload, owner.getGithubToken());
                    break;
                default:
                    log.debug("No specific sync action for event type: {}", eventType);
            }

            // Update last sync time
            repository.setLastSyncAt(LocalDateTime.now());
            repositoryRepository.save(repository);

            log.info("✅ Webhook processing completed for repository: {}", repositoryFullName);

        } catch (Exception e) {
            log.error("Error syncing repository data for: {}", repositoryFullName, e);
        }
    }

    /**
     * Check if we should process this webhook (avoid unnecessary processing)
     */
    private boolean shouldProcessWebhook(Repository repository, String eventType, Map<String, Object> payload) {
        // Always process certain critical events
        if (eventType.equals("push") || eventType.equals("create") || eventType.equals("delete")) {
            return true;
        }

        // For other events, check if we processed recently (within last 5 minutes)
        if (repository.getLastSyncAt() != null) {
            LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
            if (repository.getLastSyncAt().isAfter(fiveMinutesAgo)) {
                log.debug("Repository {} was synced recently, skipping non-critical webhook", repository.getFullName());
                return false;
            }
        }

        return true;
    }
    
    /**
     * Sync commits from push event
     */
    private void syncCommitsFromPushEvent(Repository repository, Map<String, Object> payload, String token) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> commits = (List<Map<String, Object>>) payload.get("commits");
        
        if (commits == null || commits.isEmpty()) {
            return;
        }
        
        String[] parts = repository.getFullName().split("/");
        String owner = parts[0];
        String repo = parts[1];
        
        for (Map<String, Object> commitData : commits) {
            try {
                String commitSha = (String) commitData.get("id");
                String commitMessage = (String) commitData.get("message");
                
                // Check if we already have this commit
                if (codeVersionRepository.existsByCommitSha(commitSha)) {
                    continue;
                }
                
                // Get detailed commit information from GitHub API
                JsonNode detailedCommit = fetchCommitDetails(owner, repo, commitSha, token);
                if (detailedCommit != null) {
                    saveCommitData(repository, detailedCommit);
                }
                
            } catch (Exception e) {
                log.error("Error processing commit from push event", e);
            }
        }
    }
    
    /**
     * Handle branch or tag creation
     */
    private void syncBranchOrTagCreation(Repository repository, Map<String, Object> payload, String token) {
        String refType = (String) payload.get("ref_type");
        String ref = (String) payload.get("ref");
        
        log.info("Handling {} creation: {} in repository: {}", refType, ref, repository.getFullName());
        
        if ("branch".equals(refType)) {
            // For new branches, we might want to sync recent commits
            syncRecentCommitsForBranch(repository, ref, token);
        }
        // For tags, the information is usually already captured in commits
    }
    
    /**
     * Handle branch or tag deletion
     */
    private void handleBranchOrTagDeletion(Repository repository, Map<String, Object> payload) {
        String refType = (String) payload.get("ref_type");
        String ref = (String) payload.get("ref");
        
        log.info("Handling {} deletion: {} in repository: {}", refType, ref, repository.getFullName());
        
        // We might want to mark related code versions as deleted or archived
        // For now, we'll just log the event
    }
    
    /**
     * Sync release data
     */
    private void syncReleaseData(Repository repository, Map<String, Object> payload, String token) {
        @SuppressWarnings("unchecked")
        Map<String, Object> release = (Map<String, Object>) payload.get("release");
        String action = (String) payload.get("action");
        
        if (release == null) {
            return;
        }
        
        String tagName = (String) release.get("tag_name");
        String releaseName = (String) release.get("name");
        
        log.info("Handling release {}: {} ({}) in repository: {}", 
                action, releaseName, tagName, repository.getFullName());
        
        // We could store release information in a separate table if needed
        // For now, we'll ensure the tag commit is captured
        if ("published".equals(action)) {
            String[] parts = repository.getFullName().split("/");
            String owner = parts[0];
            String repo = parts[1];
            
            try {
                // Get the commit SHA for this tag
                JsonNode tagData = fetchTagDetails(owner, repo, tagName, token);
                if (tagData != null && tagData.has("object")) {
                    String commitSha = tagData.get("object").get("sha").asText();
                    
                    if (!codeVersionRepository.existsByCommitSha(commitSha)) {
                        JsonNode commitData = fetchCommitDetails(owner, repo, commitSha, token);
                        if (commitData != null) {
                            saveCommitData(repository, commitData);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error syncing release data", e);
            }
        }
    }
    
    /**
     * Sync recent commits for a branch
     */
    private void syncRecentCommitsForBranch(Repository repository, String branch, String token) {
        String[] parts = repository.getFullName().split("/");
        String owner = parts[0];
        String repo = parts[1];
        
        try {
            JsonNode commits = fetchRecentCommits(owner, repo, branch, token);
            if (commits != null && commits.isArray()) {
                for (JsonNode commit : commits) {
                    String commitSha = commit.get("sha").asText();
                    
                    if (!codeVersionRepository.existsByCommitSha(commitSha)) {
                        saveCommitData(repository, commit);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error syncing recent commits for branch: {}", branch, e);
        }
    }
    
    /**
     * Fetch commit details from GitHub API
     */
    private JsonNode fetchCommitDetails(String owner, String repo, String sha, String token) {
        try {
            String url = GITHUB_API_BASE + "/repos/" + owner + "/" + repo + "/commits/" + sha;
            HttpHeaders headers = createHeaders(token);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                return objectMapper.readTree(response.getBody());
            }
        } catch (Exception e) {
            log.error("Error fetching commit details for SHA: {}", sha, e);
        }
        return null;
    }
    
    /**
     * Fetch tag details from GitHub API
     */
    private JsonNode fetchTagDetails(String owner, String repo, String tag, String token) {
        try {
            String url = GITHUB_API_BASE + "/repos/" + owner + "/" + repo + "/git/refs/tags/" + tag;
            HttpHeaders headers = createHeaders(token);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                return objectMapper.readTree(response.getBody());
            }
        } catch (Exception e) {
            log.error("Error fetching tag details for: {}", tag, e);
        }
        return null;
    }
    
    /**
     * Fetch recent commits from GitHub API
     */
    private JsonNode fetchRecentCommits(String owner, String repo, String branch, String token) {
        try {
            String url = GITHUB_API_BASE + "/repos/" + owner + "/" + repo + "/commits?sha=" + branch + "&per_page=10";
            HttpHeaders headers = createHeaders(token);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                return objectMapper.readTree(response.getBody());
            }
        } catch (Exception e) {
            log.error("Error fetching recent commits for branch: {}", branch, e);
        }
        return null;
    }
    
    /**
     * Save commit data to database
     */
    private void saveCommitData(Repository repository, JsonNode commitData) {
        try {
            String commitSha = commitData.get("sha").asText();
            String commitMessage = commitData.get("commit").get("message").asText();
            
            // For now, we'll create a generic file path since we don't have specific file information
            // In a real implementation, you might want to fetch the files changed in this commit
            String filePath = "repository_commit";
            
            CodeVersion codeVersion = CodeVersion.builder()
                    .commitSha(commitSha)
                    .commitMessage(commitMessage)
                    .filePath(filePath)
                    .repository(repository)
                    .build();
            
            codeVersionRepository.save(codeVersion);
            log.debug("Saved commit data: {} for repository: {}", commitSha, repository.getFullName());
            
        } catch (Exception e) {
            log.error("Error saving commit data", e);
        }
    }
    
    /**
     * Create HTTP headers with GitHub token
     */
    private HttpHeaders createHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("Accept", "application/vnd.github.v3+json");
        headers.set("User-Agent", "EspritHub-Webhook-Service");
        return headers;
    }
}
