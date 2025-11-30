package tn.esprithub.server.github.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import tn.esprithub.server.repository.entity.Repository;
import tn.esprithub.server.repository.repository.RepositoryEntityRepository;
import tn.esprithub.server.user.entity.User;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubRepositoryFetchService {
    
    private static final String GITHUB_API_BASE = "https://api.github.com";
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final RepositoryEntityRepository repositoryRepository;
    private final GitHubRepositoryDataSyncService dataSyncService;
    
    /**
     * Fetch repositories for a user only if needed (new user or data is stale)
     */
    @Transactional
    public List<Repository> fetchAndSaveUserRepositories(User user) {
        return fetchAndSaveUserRepositories(user, false);
    }

    /**
     * Fetch repositories for a user with option to force refresh
     */
    @Transactional
    public List<Repository> fetchAndSaveUserRepositories(User user, boolean forceRefresh) {
        log.info("Checking repositories for user: {} (GitHub: {}) - Force: {}", user.getEmail(), user.getGithubUsername(), forceRefresh);

        if (user.getGithubToken() == null || user.getGithubToken().isBlank()) {
            log.warn("No GitHub token found for user: {}", user.getEmail());
            return repositoryRepository.findByOwnerIdAndIsActiveTrue(user.getId());
        }

        if (user.getGithubUsername() == null || user.getGithubUsername().isBlank()) {
            log.warn("No GitHub username found for user: {}", user.getEmail());
            return repositoryRepository.findByOwnerIdAndIsActiveTrue(user.getId());
        }

        // Check if we need to fetch (only if forced or no recent sync)
        if (!forceRefresh && !shouldFetchRepositories(user)) {
            List<Repository> existingRepos = repositoryRepository.findByOwnerIdAndIsActiveTrue(user.getId());
            log.info("⏭️ Skipping fetch for user: {} - {} repositories already synced recently", user.getEmail(), existingRepos.size());
            return existingRepos;
        }

        // Test GitHub token first
        if (!testGitHubToken(user.getGithubToken())) {
            log.error("Invalid or expired GitHub token for user: {}", user.getEmail());
            return repositoryRepository.findByOwnerIdAndIsActiveTrue(user.getId());
        }

        log.info("🔄 Fetching repositories from GitHub for user: {} (GitHub: {})", user.getEmail(), user.getGithubUsername());
        
        try {
            // Fetch ALL repositories (public and private) that the user has access to
            List<JsonNode> allRepos = fetchAllUserRepositories(user.getGithubToken());

            log.info("Found {} repositories for user: {} ({})", allRepos.size(), user.getEmail(), user.getGithubUsername());
            
            List<Repository> savedRepositories = new ArrayList<>();
            
            for (JsonNode repoNode : allRepos) {
                try {
                    Repository repository = convertJsonToRepository(repoNode, user);

                    // Log repository details for debugging
                    log.info("Processing repository: {} (private: {}, owner: {})",
                           repository.getFullName(),
                           repository.getIsPrivate(),
                           repoNode.get("owner").get("login").asText());

                    // Check if repository already exists
                    Repository existingRepo = repositoryRepository.findByFullName(repository.getFullName())
                            .orElse(null);

                    if (existingRepo == null) {
                        Repository savedRepo = repositoryRepository.save(repository);
                        savedRepositories.add(savedRepo);
                        log.info("✅ Saved NEW repository: {} (private: {})", repository.getFullName(), repository.getIsPrivate());
                    } else {
                        // Update existing repository
                        updateExistingRepository(existingRepo, repoNode);
                        Repository updatedRepo = repositoryRepository.save(existingRepo);
                        savedRepositories.add(updatedRepo);
                        log.info("🔄 Updated existing repository: {} (private: {})", existingRepo.getFullName(), existingRepo.getIsPrivate());
                    }

                } catch (Exception e) {
                    log.error("❌ Error processing repository: {}", repoNode.get("full_name").asText(), e);
                }
            }
            
            log.info("Successfully processed {} repositories for user: {}", savedRepositories.size(), user.getEmail());

            // Trigger comprehensive sync for new repositories in a separate transaction
            triggerComprehensiveSync(savedRepositories, user);

            return savedRepositories;
            
        } catch (Exception e) {
            log.error("Error fetching repositories for user: {}", user.getEmail(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Check if we should fetch repositories for a user
     */
    private boolean shouldFetchRepositories(User user) {
        List<Repository> existingRepos = repositoryRepository.findByOwnerIdAndIsActiveTrue(user.getId());

        // If no repositories exist, we should fetch
        if (existingRepos.isEmpty()) {
            log.debug("No repositories found for user: {} - should fetch", user.getEmail());
            return true;
        }

        // Check if any repository was synced recently (within last 6 hours)
        LocalDateTime sixHoursAgo = LocalDateTime.now().minusHours(6);
        boolean hasRecentSync = existingRepos.stream()
                .anyMatch(repo -> repo.getLastSyncAt() != null && repo.getLastSyncAt().isAfter(sixHoursAgo));

        if (hasRecentSync) {
            log.debug("User {} has repositories synced within last 6 hours - skipping fetch", user.getEmail());
            return false;
        }

        log.debug("User {} repositories are stale (>6 hours) - should fetch", user.getEmail());
        return true;
    }

    /**
     * Trigger comprehensive sync for repositories in a separate transaction
     */
    private void triggerComprehensiveSync(List<Repository> repositories, User user) {
        if (user.getGithubToken() == null || user.getGithubToken().isBlank()) {
            return;
        }

        // Run comprehensive sync asynchronously to avoid transaction issues
        new Thread(() -> {
            for (Repository repository : repositories) {
                try {
                    Thread.sleep(1000); // Small delay between syncs to avoid rate limiting
                    dataSyncService.syncRepositoryData(repository, user.getGithubToken());
                    log.info("✅ Completed comprehensive sync for repository: {}", repository.getFullName());
                } catch (Exception e) {
                    log.warn("Failed to sync comprehensive data for repository: {}", repository.getFullName(), e);
                }
            }
        }).start();
    }

    /**
     * Fetch ALL repositories the user has access to (including private and collaborator repos)
     */
    private List<JsonNode> fetchAllUserRepositories(String token) {
        List<JsonNode> allRepos = new ArrayList<>();

        try {
            // Fetch all repositories with different visibility and affiliation settings
            // This will get ALL repos the user has access to including private and collaborator repos

            // Method 1: Get all repositories (owner, collaborator, organization_member)
            allRepos.addAll(fetchRepositoriesWithParams(token, "all", "all", "updated"));

            // Method 2: Also try to get repositories by specific affiliations to ensure we don't miss any
            allRepos.addAll(fetchRepositoriesWithParams(token, "owner", "all", "updated"));
            allRepos.addAll(fetchRepositoriesWithParams(token, "collaborator", "all", "updated"));
            allRepos.addAll(fetchRepositoriesWithParams(token, "organization_member", "all", "updated"));

            // Remove duplicates based on repository ID
            Map<Long, JsonNode> uniqueRepos = new HashMap<>();
            for (JsonNode repo : allRepos) {
                Long repoId = repo.get("id").asLong();
                uniqueRepos.put(repoId, repo);
            }

            List<JsonNode> result = new ArrayList<>(uniqueRepos.values());
            log.info("Fetched {} unique repositories from GitHub API", result.size());

            return result;

        } catch (Exception e) {
            log.error("Error fetching all user repositories", e);
            return new ArrayList<>();
        }
    }

    /**
     * Fetch repositories from GitHub API with specific parameters
     */
    private List<JsonNode> fetchRepositoriesWithParams(String token, String affiliation, String visibility, String sort) {
        List<JsonNode> repos = new ArrayList<>();
        int page = 1;
        int perPage = 100; // Maximum allowed by GitHub API

        try {
            while (true) {
                String url = GITHUB_API_BASE + "/user/repos" +
                           "?affiliation=" + affiliation +
                           "&visibility=" + visibility +
                           "&sort=" + sort +
                           "&per_page=" + perPage +
                           "&page=" + page;

                log.debug("Fetching repositories: {}", url);

                HttpHeaders headers = createHeaders(token);
                HttpEntity<String> entity = new HttpEntity<>(headers);

                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    JsonNode repoArray = objectMapper.readTree(response.getBody());

                    if (repoArray.isArray() && repoArray.size() > 0) {
                        for (JsonNode repo : repoArray) {
                            repos.add(repo);
                        }

                        log.debug("Fetched {} repositories from page {} (affiliation: {}, visibility: {})",
                                repoArray.size(), page, affiliation, visibility);

                        // If we got less than perPage results, we've reached the end
                        if (repoArray.size() < perPage) {
                            break;
                        }

                        page++;
                    } else {
                        break;
                    }
                } else {
                    log.warn("GitHub API returned status: {} for affiliation: {}, visibility: {}",
                           response.getStatusCode(), affiliation, visibility);
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Error fetching repositories with affiliation: {}, visibility: {}", affiliation, visibility, e);
        }

        return repos;
    }

    /**
     * Fetch repositories from GitHub API with pagination (legacy method - keeping for compatibility)
     */
    private List<JsonNode> fetchRepositoriesFromGitHub(String username, String affiliation, String token) {
        List<JsonNode> allRepos = new ArrayList<>();
        int page = 1;
        int perPage = 100;
        
        try {
            while (true) {
                String url = GITHUB_API_BASE + "/user/repos?affiliation=" + affiliation + 
                           "&per_page=" + perPage + "&page=" + page + "&sort=updated";
                
                HttpHeaders headers = createHeaders(token);
                HttpEntity<String> entity = new HttpEntity<>(headers);
                
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
                
                if (response.getStatusCode().is2xxSuccessful()) {
                    JsonNode repos = objectMapper.readTree(response.getBody());
                    
                    if (repos.isArray() && repos.size() > 0) {
                        for (JsonNode repo : repos) {
                            allRepos.add(repo);
                        }
                        
                        // If we got less than perPage results, we've reached the end
                        if (repos.size() < perPage) {
                            break;
                        }
                        
                        page++;
                    } else {
                        break;
                    }
                } else {
                    log.warn("GitHub API returned status: {} for user: {}", response.getStatusCode(), username);
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Error fetching repositories from GitHub for user: {}", username, e);
        }
        
        return allRepos;
    }
    
    /**
     * Convert GitHub API JSON response to Repository entity
     */
    private Repository convertJsonToRepository(JsonNode repoNode, User user) {
        return Repository.builder()
                .name(repoNode.get("name").asText())
                .fullName(repoNode.get("full_name").asText())
                .description(repoNode.has("description") && !repoNode.get("description").isNull() 
                           ? repoNode.get("description").asText() : null)
                .url(repoNode.get("html_url").asText())
                .cloneUrl(repoNode.get("clone_url").asText())
                .sshUrl(repoNode.get("ssh_url").asText())
                .isPrivate(repoNode.get("private").asBoolean())
                .defaultBranch(repoNode.get("default_branch").asText())
                .isActive(true)
                .owner(user)
                .createdAt(parseGitHubDate(repoNode.get("created_at").asText()))
                .updatedAt(parseGitHubDate(repoNode.get("updated_at").asText()))
                .build();
    }
    
    /**
     * Update existing repository with fresh data from GitHub
     */
    private void updateExistingRepository(Repository existingRepo, JsonNode repoNode) {
        existingRepo.setDescription(repoNode.has("description") && !repoNode.get("description").isNull() 
                                  ? repoNode.get("description").asText() : null);
        existingRepo.setUrl(repoNode.get("html_url").asText());
        existingRepo.setCloneUrl(repoNode.get("clone_url").asText());
        existingRepo.setSshUrl(repoNode.get("ssh_url").asText());
        existingRepo.setIsPrivate(repoNode.get("private").asBoolean());
        existingRepo.setDefaultBranch(repoNode.get("default_branch").asText());
        existingRepo.setUpdatedAt(parseGitHubDate(repoNode.get("updated_at").asText()));
    }
    
    /**
     * Parse GitHub date format to LocalDateTime
     */
    private LocalDateTime parseGitHubDate(String dateString) {
        try {
            // GitHub returns dates in ISO format like "2023-07-10T12:30:45Z"
            return LocalDateTime.parse(dateString.replace("Z", ""), ISO_FORMATTER);
        } catch (Exception e) {
            log.warn("Error parsing GitHub date: {}", dateString);
            return LocalDateTime.now();
        }
    }
    
    /**
     * Test if GitHub token is valid
     */
    private boolean testGitHubToken(String token) {
        try {
            String url = GITHUB_API_BASE + "/user";
            HttpHeaders headers = createHeaders(token);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode userInfo = objectMapper.readTree(response.getBody());
                log.debug("GitHub token valid for user: {}", userInfo.get("login").asText());
                return true;
            } else {
                log.warn("GitHub token test failed with status: {}", response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            log.error("Error testing GitHub token", e);
            return false;
        }
    }

    /**
     * Create HTTP headers with GitHub token
     */
    private HttpHeaders createHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("Accept", "application/vnd.github.v3+json");
        headers.set("User-Agent", "EspritHub-Repository-Fetcher");
        return headers;
    }
    
    /**
     * Fetch repositories for all users who have GitHub tokens
     */
    @Transactional
    public void fetchRepositoriesForAllUsers() {
        log.info("Starting repository fetch for all users with GitHub tokens");
        
        try {
            // This would require a method in UserRepository to find users with GitHub tokens
            // For now, we'll implement it in the scheduled service
            log.info("Repository fetch for all users completed");
        } catch (Exception e) {
            log.error("Error during bulk repository fetch", e);
        }
    }
}
