package tn.esprithub.server.admin.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tn.esprithub.server.common.exception.BusinessException;
import tn.esprithub.server.github.service.GitHubRepositoryFetchService;
import tn.esprithub.server.github.service.GitHubRepositoryScheduler;
import tn.esprithub.server.github.service.GitHubWebhookService;
import tn.esprithub.server.repository.entity.Repository;
import tn.esprithub.server.repository.repository.RepositoryEntityRepository;
import tn.esprithub.server.project.entity.Task;
import tn.esprithub.server.project.repository.TaskRepository;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.user.repository.UserRepository;
import tn.esprithub.server.admin.service.AdminUserDataService;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
// ...existing code...
import org.springframework.http.ContentDisposition;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:4200", "http://127.0.0.1:4200"}, allowCredentials = "true")
public class AdminUserController {
    
    private final UserRepository userRepository;
    private final RepositoryEntityRepository repositoryRepository;
    private final TaskRepository taskRepository;
    private final GitHubRepositoryFetchService gitHubRepositoryFetchService;
    private final GitHubWebhookService gitHubWebhookService;
    private final GitHubRepositoryScheduler gitHubRepositoryScheduler;
    private final AdminUserDataService adminUserDataService;

    // Track bulk fetch status
    private volatile boolean bulkFetchInProgress = false;
    private volatile String bulkFetchStatus = "IDLE";

    /**
     * Test endpoint to check if CORS and routing are working
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, String>> testEndpoint() {
        log.info("🔍 Test endpoint called - CORS and routing working");
        return ResponseEntity.ok(Map.of(
            "message", "Admin user controller is working",
            "timestamp", java.time.LocalDateTime.now().toString(),
            "server", "esprithub-server",
            "port", "8090"
        ));
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        log.info("🏥 Health check endpoint called");

        try {
            // Test database connection
            long userCount = userRepository.count();
            long repoCount = repositoryRepository.count();

            return ResponseEntity.ok(Map.of(
                "status", "UP",
                "timestamp", java.time.LocalDateTime.now().toString(),
                "database", "CONNECTED",
                "userCount", userCount,
                "repositoryCount", repoCount,
                "server", "esprithub-server",
                "port", "8090"
            ));
        } catch (Exception e) {
            log.error("❌ Health check failed", e);
            return ResponseEntity.status(500).body(Map.of(
                "status", "DOWN",
                "timestamp", java.time.LocalDateTime.now().toString(),
                "error", e.getMessage(),
                "server", "esprithub-server",
                "port", "8090"
            ));
        }
    }
    
    /**
     * Get detailed user information including repositories and tasks
     */
    @GetMapping("/{userId}/details")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getUserDetails(@PathVariable UUID userId) {
        log.info("🔍 Getting comprehensive user details for user: {}", userId);

        try {
            log.debug("🔍 Looking up user in database...");
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException("User not found"));

            log.info("✅ Found user: {} ({})", user.getEmail(), user.getGithubUsername());

            // Get comprehensive user data including repositories, files, commits, etc.
            log.debug("🔍 Getting comprehensive user data...");
            Map<String, Object> userDetails = adminUserDataService.getComprehensiveUserData(user);

            log.info("✅ Successfully retrieved user details for: {}", user.getEmail());
            return ResponseEntity.ok(userDetails);

        } catch (Exception e) {
            log.error("❌ Error getting user details for user: {}", userId, e);
            throw new BusinessException("Failed to get user details: " + e.getMessage());
        }
    }
    
    /**
     * Get all repositories for a specific user
     */
    @GetMapping("/{userId}/repositories")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getUserRepositories(@PathVariable UUID userId) {
        log.info("🔍 Getting repository summaries for user: {}", userId);

        // Check if this is actually a repository ID being passed as user ID (common frontend mistake)
        if (!userRepository.existsById(userId)) {
            // Check if this UUID is actually a repository ID
            if (repositoryRepository.existsById(userId)) {
                log.warn("⚠️ Repository ID {} passed as user ID, redirecting to repository details", userId);

                // Return repository details instead of user repositories
                Map<String, Object> repositoryDetails = adminUserDataService.getRepositoryDetails(userId);
                if (!repositoryDetails.isEmpty()) {
                    // Wrap in a list format to match expected response structure
                    return ResponseEntity.ok(java.util.List.of(repositoryDetails));
                }
            }

            log.warn("❌ Neither user nor repository found for ID: {}", userId);
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "User not found");
        }

        // Get existing repositories from database
        List<Repository> repositories = repositoryRepository.findByOwnerIdAndIsActiveTrue(userId);

        // Always return a non-null JSON array
        if (repositories == null) {
            repositories = java.util.Collections.emptyList();
        }

        // Return lightweight summaries only (no large content)
        List<Map<String, Object>> repositorySummaries = adminUserDataService.getRepositoriesSummary(repositories);

        log.info("✅ Returning {} repository summaries", repositorySummaries.size());
        return ResponseEntity.ok(repositorySummaries);
    }

    /**
     * Get paginated repositories with more details (chunked response)
     */
    @GetMapping("/{userId}/repositories/paginated")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getUserRepositoriesPaginated(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {
        log.info("🔍 Getting paginated repositories for user: {} (page: {}, size: {})", userId, page, size);

        // Only check existence, don't fetch the user entity
        if (!userRepository.existsById(userId)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "User not found");
        }

        // Get repositories with pagination
        List<Repository> allRepositories = repositoryRepository.findByOwnerIdAndIsActiveTrue(userId);

        // Manual pagination to avoid large responses
        int start = page * size;
        int end = Math.min(start + size, allRepositories.size());

        if (start >= allRepositories.size()) {
            // Return empty page if beyond range
            Map<String, Object> response = new HashMap<>();
            response.put("repositories", java.util.Collections.emptyList());
            response.put("page", page);
            response.put("size", size);
            response.put("totalElements", allRepositories.size());
            response.put("totalPages", (int) Math.ceil((double) allRepositories.size() / size));
            response.put("hasNext", false);
            response.put("hasPrevious", page > 0);
            return ResponseEntity.ok(response);
        }

        List<Repository> pageRepositories = allRepositories.subList(start, end);
        List<Map<String, Object>> repositoryDetails = adminUserDataService.getRepositoriesWithDetails(pageRepositories);

        Map<String, Object> response = new HashMap<>();
        response.put("repositories", repositoryDetails);
        response.put("page", page);
        response.put("size", size);
        response.put("totalElements", allRepositories.size());
        response.put("totalPages", (int) Math.ceil((double) allRepositories.size() / size));
        response.put("hasNext", end < allRepositories.size());
        response.put("hasPrevious", page > 0);

        log.info("✅ Returning page {} with {} repositories", page, repositoryDetails.size());
        return ResponseEntity.ok(response);
    }

    /**
     * Get task summaries for a specific user (lightweight)
     */
    @GetMapping("/{userId}/tasks")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getUserTasks(@PathVariable UUID userId) {
        log.info("🔍 Getting task summaries for user: {}", userId);

        // Only check existence, don't fetch the user entity
        if (!userRepository.existsById(userId)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "User not found");
        }

        List<Task> tasks = taskRepository.findTasksAssignedToUser(userId);

        // Return lightweight summaries only
        List<Map<String, Object>> taskSummaries = adminUserDataService.getTaskSummaries(tasks);

        log.info("✅ Returning {} task summaries", taskSummaries.size());
        return ResponseEntity.ok(taskSummaries);
    }

    /**
     * Get task counts only (ultra-lightweight)
     */
    @GetMapping("/{userId}/tasks/count")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getUserTaskCounts(@PathVariable UUID userId) {
        log.info("🔍 Getting task counts for user: {}", userId);

        // Only check existence, don't fetch the user entity
        if (!userRepository.existsById(userId)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "User not found");
        }

        long totalTasks = taskRepository.countTasksAssignedToUser(userId);
        long completedTasks = taskRepository.countCompletedTasksForUser(userId);

        Map<String, Object> taskCounts = new HashMap<>();
        taskCounts.put("totalTasks", totalTasks);
        taskCounts.put("completedTasks", completedTasks);
        taskCounts.put("pendingTasks", totalTasks - completedTasks);

        log.info("✅ Returning task counts: {} total, {} completed", totalTasks, completedTasks);
        return ResponseEntity.ok(taskCounts);
    }
    
    /**
     * Search repositories across all users (admin only)
     */
    @GetMapping("/repositories/search")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<Repository>> searchRepositories(
            @RequestParam String query,
            Pageable pageable) {
        
        log.info("Searching repositories with query: {}", query);
        
        try {
            Page<Repository> repositories = repositoryRepository.findByNameContainingIgnoreCaseOrFullNameContainingIgnoreCase(
                    query, query, pageable);
            
            return ResponseEntity.ok(repositories);
            
        } catch (Exception e) {
            log.error("Error searching repositories with query: {}", query, e);
            throw new BusinessException("Failed to search repositories: " + e.getMessage());
        }
    }
    
    /**
     * Get all repositories (admin only)
     */
    @GetMapping("/repositories")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<Repository>> getAllRepositories(Pageable pageable) {
        log.info("Getting all repositories");
        
        try {
            Page<Repository> repositories = repositoryRepository.findAll(pageable);
            
            return ResponseEntity.ok(repositories);
            
        } catch (Exception e) {
            log.error("Error getting all repositories", e);
            throw new BusinessException("Failed to get repositories: " + e.getMessage());
        }
    }

    /**
     * Manually trigger repository fetching for a specific user
     */
    @PostMapping("/{userId}/fetch-repositories")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> fetchUserRepositories(@PathVariable UUID userId) {
        log.info("Manual trigger: fetching repositories for user: {}", userId);

        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException("User not found"));

            if (user.getGithubToken() == null || user.getGithubToken().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "User does not have a GitHub token",
                    "message", "Cannot fetch repositories without GitHub token"
                ));
            }

            // Fetch repositories directly
            List<Repository> repositories = gitHubRepositoryFetchService.fetchAndSaveUserRepositories(user);

            return ResponseEntity.ok(Map.of(
                "message", "Repository fetch completed successfully",
                "user", user.getEmail(),
                "githubUsername", user.getGithubUsername(),
                "repositoriesFound", repositories.size()
            ));

        } catch (Exception e) {
            log.error("Error triggering repository fetch for user: {}", userId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to trigger repository fetch: " + e.getMessage()
            ));
        }
    }

    /**
     * Manually trigger SMART repository fetching for all users (only fetches if needed)
     */
    @PostMapping("/fetch-all-repositories")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> fetchAllRepositories() {
        log.info("🧠 SMART BULK FETCH: Starting smart repository fetch for ALL users");

        try {
            // Check if bulk fetch is already in progress
            if (bulkFetchInProgress) {
                return ResponseEntity.ok(Map.of(
                    "message", "Bulk fetch is already in progress",
                    "status", bulkFetchStatus
                ));
            }

            // Get all users with GitHub tokens
            List<User> usersWithTokens = userRepository.findUsersWithGitHubTokens();
            log.info("Found {} users with GitHub tokens", usersWithTokens.size());

            if (usersWithTokens.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                    "message", "No users with GitHub tokens found",
                    "usersProcessed", 0,
                    "repositoriesFound", 0
                ));
            }

            // Start bulk processing in background
            bulkFetchInProgress = true;
            bulkFetchStatus = "STARTING";

            new Thread(() -> {
                int totalUsers = usersWithTokens.size();
                int processedUsers = 0;
                int fetchedUsers = 0;
                int skippedUsers = 0;
                int totalRepositories = 0;
                int errorCount = 0;

                log.info("🧠 Starting SMART bulk repository fetch for {} users", totalUsers);
                bulkFetchStatus = "PROCESSING";

                for (User user : usersWithTokens) {
                    try {
                        processedUsers++;
                        bulkFetchStatus = String.format("Processing user %d/%d: %s", processedUsers, totalUsers, user.getEmail());

                        // Get existing repositories to check if we need to fetch
                        List<Repository> existingRepos = repositoryRepository.findByOwnerIdAndIsActiveTrue(user.getId());
                        boolean needsFetch = existingRepos.isEmpty();

                        // If repos exist, check if they're stale (no sync in last 24 hours)
                        if (!needsFetch && !existingRepos.isEmpty()) {
                            LocalDateTime oneDayAgo = LocalDateTime.now().minusHours(24);
                            needsFetch = existingRepos.stream()
                                    .allMatch(repo -> repo.getLastSyncAt() == null || repo.getLastSyncAt().isBefore(oneDayAgo));
                        }

                        if (needsFetch) {
                            log.info("📥 Fetching repositories for user {}/{}: {} ({})",
                                   processedUsers, totalUsers, user.getEmail(), user.getGithubUsername());

                            List<Repository> repositories = gitHubRepositoryFetchService.fetchAndSaveUserRepositories(user, true);
                            totalRepositories += repositories.size();
                            fetchedUsers++;

                            log.info("✅ User {}/{}: Found {} repositories for {}",
                                   processedUsers, totalUsers, repositories.size(), user.getEmail());
                        } else {
                            skippedUsers++;
                            log.info("⏭️ Skipping user {}/{}: {} - repositories already synced recently",
                                   processedUsers, totalUsers, user.getEmail());
                        }

                        // Small delay to avoid rate limiting
                        Thread.sleep(1000);

                    } catch (Exception e) {
                        errorCount++;
                        log.error("❌ Error processing user: {} ({})", user.getEmail(), user.getGithubUsername(), e);
                    }
                }

                bulkFetchStatus = String.format("COMPLETED - Processed: %d users, Fetched: %d, Skipped: %d, Repositories: %d, Errors: %d",
                                               processedUsers, fetchedUsers, skippedUsers, totalRepositories, errorCount);
                bulkFetchInProgress = false;

                log.info("🎉 SMART BULK FETCH COMPLETED! Processed: {} users, Fetched: {}, Skipped: {}, Repositories: {}, Errors: {}",
                       processedUsers, fetchedUsers, skippedUsers, totalRepositories, errorCount);

            }).start();

            return ResponseEntity.ok(Map.of(
                "message", "Bulk repository fetch started for all users with GitHub tokens",
                "usersToProcess", usersWithTokens.size(),
                "status", "PROCESSING",
                "note", "Check server logs for progress. This will take several minutes."
            ));

        } catch (Exception e) {
            log.error("Error starting bulk repository fetch", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to start bulk repository fetch: " + e.getMessage()
            ));
        }
    }

    /**
     * Get bulk fetch status
     */
    @GetMapping("/fetch-all-repositories/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getBulkFetchStatus() {
        return ResponseEntity.ok(Map.of(
            "inProgress", bulkFetchInProgress,
            "status", bulkFetchStatus,
            "timestamp", java.time.LocalDateTime.now().toString()
        ));
    }

    /**
     * Get repository files for a specific repository
     */
    @GetMapping("/repositories/{repositoryId}/files")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getRepositoryFiles(
            @PathVariable UUID repositoryId,
            @RequestParam(required = false) String branch) {
        log.info("Getting files for repository: {} (branch: {})", repositoryId, branch);

        try {
            List<Map<String, Object>> files = adminUserDataService.getRepositoryFiles(repositoryId, branch);
            return ResponseEntity.ok(files);

        } catch (Exception e) {
            log.error("Error getting repository files for repository: {}", repositoryId, e);
            throw new BusinessException("Failed to get repository files: " + e.getMessage());
        }
    }

    /**
     * Get commit details with file changes
     */
    @GetMapping("/commits/{commitId}/details")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getCommitDetails(@PathVariable UUID commitId) {
        log.info("Getting commit details for commit: {}", commitId);

        try {
            Map<String, Object> commitDetails = adminUserDataService.getCommitDetails(commitId);
            return ResponseEntity.ok(commitDetails);

        } catch (Exception e) {
            log.error("Error getting commit details for commit: {}", commitId, e);
            throw new BusinessException("Failed to get commit details: " + e.getMessage());
        }
    }

    /**
     * Get detailed repository information (matches frontend route)
     */
    @GetMapping("/repositories/{repositoryId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getRepositoryDetailsById(@PathVariable UUID repositoryId) {
        log.info("🔍 Getting repository details for repository: {}", repositoryId);

        try {
            Map<String, Object> repositoryDetails = adminUserDataService.getRepositoryDetails(repositoryId);

            if (repositoryDetails.isEmpty()) {
                log.warn("❌ Repository not found: {}", repositoryId);
                throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "Repository not found"
                );
            }

            log.info("✅ Successfully retrieved repository details for: {}", repositoryId);
            return ResponseEntity.ok(repositoryDetails);

        } catch (org.springframework.web.server.ResponseStatusException e) {
            throw e; // Re-throw 404 errors
        } catch (Exception e) {
            log.error("❌ Error getting repository details for repository: {}", repositoryId, e);
            throw new BusinessException("Failed to get repository details: " + e.getMessage());
        }
    }

    /**
     * Get detailed repository information (alternative route)
     */
    @GetMapping("/repositories/{repositoryId}/details")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getRepositoryDetails(@PathVariable UUID repositoryId) {
        log.info("🔍 Getting repository details for repository: {} (details route)", repositoryId);

        // Delegate to the main method
        return getRepositoryDetailsById(repositoryId);
    }

    /**
     * Get file content
     */
    @GetMapping("/files/{fileId}/content")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getFileContent(@PathVariable UUID fileId) {
        log.info("Getting file content for file: {}", fileId);

        try {
            Map<String, Object> fileContent = adminUserDataService.getFileContent(fileId);
            return ResponseEntity.ok(fileContent);

        } catch (Exception e) {
            log.error("Error getting file content for file: {}", fileId, e);
            throw new BusinessException("Failed to get file content: " + e.getMessage());
        }
    }

    /**
     * FALLBACK: Handle repository details when called with wrong route
     * This handles cases where frontend calls /admin/users/{repoId} expecting repository details
     */
    @GetMapping("/{possibleRepoId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> handlePossibleRepositoryId(@PathVariable UUID possibleRepoId) {
        log.info("🔍 Checking if {} is a repository ID (fallback route)", possibleRepoId);

        // First check if it's a user ID
        if (userRepository.existsById(possibleRepoId)) {
            log.info("✅ Found user, redirecting to user details");
            return getUserDetails(possibleRepoId);
        }

        // If not a user, check if it's a repository ID
        if (repositoryRepository.existsById(possibleRepoId)) {
            log.info("✅ Found repository, returning repository details");

            try {
                Map<String, Object> repositoryDetails = adminUserDataService.getRepositoryDetails(possibleRepoId);

                if (repositoryDetails.isEmpty()) {
                    throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Repository not found"
                    );
                }

                return ResponseEntity.ok(repositoryDetails);

            } catch (org.springframework.web.server.ResponseStatusException e) {
                throw e;
            } catch (Exception e) {
                log.error("❌ Error getting repository details for: {}", possibleRepoId, e);
                throw new BusinessException("Failed to get repository details: " + e.getMessage());
            }
        }

        // Neither user nor repository found
        log.warn("❌ ID {} not found as user or repository", possibleRepoId);
        throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.NOT_FOUND,
            "Resource not found"
        );
    }

    @GetMapping("/export")
    // @PreAuthorize("hasRole('ADMIN')") // Temporarily disabled for testing
    public ResponseEntity<byte[]> exportUsersAsCsv() {
        log.info("📋 Exporting all users as CSV");
        
        try {
            List<User> users = userRepository.findAll();
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
            
            // CSV header
            writer.println("ID,Email,First Name,Last Name,Role,GitHub Username,GitHub Name,Is Active,Email Verified,Last Login,Registration Date");
            
            // CSV rows
            for (User user : users) {
                writer.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                    user.getId().toString(),
                    escapeForCsv(user.getEmail()),
                    escapeForCsv(user.getFirstName()),
                    escapeForCsv(user.getLastName()),
                    user.getRole().name(),
                    escapeForCsv(user.getGithubUsername()),
                    escapeForCsv(user.getGithubName()),
                    user.getIsActive(),
                    user.getIsEmailVerified(),
                    user.getLastLogin() != null ? user.getLastLogin().toString() : "",
                    user.getCreatedAt() != null ? user.getCreatedAt().toString() : ""
                );
            }
            
            writer.flush();
            writer.close();
            
            byte[] csvData = outputStream.toByteArray();
            outputStream.close();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/csv"));
            headers.setContentDisposition(ContentDisposition.builder("attachment")
                .filename("users_export.csv")
                .build());
            
            log.info("✅ Successfully exported {} users to CSV", users.size());
            return ResponseEntity.ok()
                .headers(headers)
                .body(csvData);
                
        } catch (Exception e) {
            log.error("❌ Error exporting users to CSV", e);
            throw new BusinessException("Failed to export users: " + e.getMessage());
        }
    }

    private String escapeForCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
