package tn.esprithub.server.admin.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tn.esprithub.server.admin.service.AdminUserDataService;
import tn.esprithub.server.common.exception.BusinessException;
import tn.esprithub.server.repository.entity.RepositoryCommit;
import tn.esprithub.server.repository.repository.RepositoryCommitRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/repositories")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"${app.cors.allowed-origins}"})
public class AdminRepositoryController {

    private final AdminUserDataService adminUserDataService;
    private final RepositoryCommitRepository commitRepository;

    /**
     * Get detailed repository information by repository ID
     */
    @GetMapping("/{repositoryId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getRepositoryDetails(@PathVariable UUID repositoryId) {
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
            
            // Log detailed information about the data being returned
            log.info("✅ Repository details contains keys: {}", repositoryDetails.keySet());
            
            if (repositoryDetails.containsKey("files")) {
                Object files = repositoryDetails.get("files");
                if (files instanceof java.util.List) {
                    log.info("📁 Files count: {}", ((java.util.List<?>) files).size());
                }
            }
            
            if (repositoryDetails.containsKey("commits")) {
                Object commits = repositoryDetails.get("commits");
                if (commits instanceof java.util.List) {
                    log.info("📝 Commits count: {}", ((java.util.List<?>) commits).size());
                }
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
     * Get repository files (without content for listing)
     */
    @GetMapping("/{repositoryId}/files")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<java.util.List<Map<String, Object>>> getRepositoryFiles(
            @PathVariable UUID repositoryId,
            @RequestParam(required = false) String branch) {
        log.info("🔍 Getting files for repository: {} (branch: {})", repositoryId, branch);

        try {
            java.util.List<Map<String, Object>> files = adminUserDataService.getRepositoryFiles(repositoryId, branch);

            log.info("✅ Returning {} files for repository: {}", files.size(), repositoryId);
            return ResponseEntity.ok(files);

        } catch (Exception e) {
            log.error("❌ Error getting files for repository: {}", repositoryId, e);
            throw new BusinessException("Failed to get repository files: " + e.getMessage());
        }
    }

    /**
     * Get repository files with content (for full file browsing)
     */
    @GetMapping("/{repositoryId}/files/with-content")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<java.util.List<Map<String, Object>>> getRepositoryFilesWithContent(
            @PathVariable UUID repositoryId,
            @RequestParam(required = false) String branch,
            @RequestParam(defaultValue = "50") int limit) {
        log.info("🔍 Getting files with content for repository: {} (branch: {}, limit: {})", repositoryId, branch, limit);

        try {
            // Get files without content first
            java.util.List<Map<String, Object>> files = adminUserDataService.getRepositoryFiles(repositoryId, branch);

            // Limit the number of files to avoid huge responses
            java.util.List<Map<String, Object>> limitedFiles = files.stream()
                .limit(limit)
                .map(file -> {
                    UUID fileId = (UUID) file.get("id");
                    return adminUserDataService.getFileContent(fileId);
                })
                .filter(file -> !file.isEmpty())
                .collect(java.util.stream.Collectors.toList());

            log.info("✅ Returning {} files with content for repository: {}", limitedFiles.size(), repositoryId);
            return ResponseEntity.ok(limitedFiles);

        } catch (Exception e) {
            log.error("❌ Error getting files with content for repository: {}", repositoryId, e);
            throw new BusinessException("Failed to get repository files with content: " + e.getMessage());
        }
    }

    /**
     * Get specific file content
     */
    @GetMapping("/files/{fileId}/content")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getFileContent(@PathVariable UUID fileId) {
        log.info("🔍 Getting file content for file: {}", fileId);
        
        try {
            Map<String, Object> fileContent = adminUserDataService.getFileContent(fileId);
            
            if (fileContent.isEmpty()) {
                log.warn("❌ File not found: {}", fileId);
                throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, 
                    "File not found"
                );
            }
            
            log.info("✅ Successfully retrieved file content for: {}", fileId);
            return ResponseEntity.ok(fileContent);
            
        } catch (org.springframework.web.server.ResponseStatusException e) {
            throw e; // Re-throw 404 errors
        } catch (Exception e) {
            log.error("❌ Error getting file content for file: {}", fileId, e);
            throw new BusinessException("Failed to get file content: " + e.getMessage());
        }
    }

    /**
     * Get paginated commits for a repository
     */
    @GetMapping("/{repositoryId}/commits")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getRepositoryCommits(
            @PathVariable UUID repositoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String branch) {
        log.info("🔍 Getting commits for repository: {} (page: {}, size: {}, branch: {})", repositoryId, page, size, branch);

        try {
            // Get commits from repository
            List<RepositoryCommit> commits = commitRepository.findByRepositoryIdOrderByDateDesc(
                repositoryId, PageRequest.of(page, size)
            ).getContent();

            // Just return the commits directly for now - simpler approach
            return ResponseEntity.ok(Map.of(
                "commits", commits,
                "repositoryId", repositoryId,
                "page", page,
                "size", size,
                "totalCommits", commitRepository.countByRepositoryId(repositoryId)
            ));
        } catch (Exception e) {
            log.error("❌ Error getting commits for repository: {}", repositoryId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to get commits",
                "message", e.getMessage(),
                "repositoryId", repositoryId
            ));
        }
    }

    /**
     * Get commit details with file changes
     */
    @GetMapping("/commits/{commitId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getCommitDetails(@PathVariable UUID commitId) {
        log.info("🔍 Getting commit details for commit: {}", commitId);

        try {
            Map<String, Object> commitDetails = adminUserDataService.getCommitDetails(commitId);

            if (commitDetails.isEmpty()) {
                log.warn("❌ Commit not found: {}", commitId);
                throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "Commit not found"
                );
            }

            log.info("✅ Successfully retrieved commit details for: {}", commitId);
            return ResponseEntity.ok(commitDetails);

        } catch (org.springframework.web.server.ResponseStatusException e) {
            throw e; // Re-throw 404 errors
        } catch (Exception e) {
            log.error("❌ Error getting commit details for commit: {}", commitId, e);
            throw new BusinessException("Failed to get commit details: " + e.getMessage());
        }
    }

    /**
     * Health check for repository controller
     */
    @GetMapping("/health")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> healthCheck() {
        log.info("🏥 Repository controller health check");
        
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "controller", "AdminRepositoryController",
            "timestamp", java.time.LocalDateTime.now().toString()
        ));
    }

    /**
     * DEBUG: Public endpoint to test repository data (remove in production)
     */
    @GetMapping("/{repositoryId}/debug")
    public ResponseEntity<Map<String, Object>> getRepositoryDetailsDebug(@PathVariable UUID repositoryId) {
        log.info("🐛 DEBUG: Getting repository details for repository: {}", repositoryId);
        
        try {
            Map<String, Object> repositoryDetails = adminUserDataService.getRepositoryDetails(repositoryId);
            
            if (repositoryDetails.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                    "debug", true,
                    "error", "Repository not found",
                    "repositoryId", repositoryId.toString()
                ));
            }
            
            // Add debug information
            Map<String, Object> debugResponse = new HashMap<>(repositoryDetails);
            debugResponse.put("debug", true);
            debugResponse.put("timestamp", System.currentTimeMillis());
            debugResponse.put("requestedId", repositoryId.toString());
            
            // Log files and commits info
            if (repositoryDetails.containsKey("files")) {
                Object files = repositoryDetails.get("files");
                if (files instanceof java.util.List) {
                    java.util.List<?> filesList = (java.util.List<?>) files;
                    debugResponse.put("filesCount", filesList.size());
                    log.info("🐛 DEBUG: Files count: {}", filesList.size());
                    if (!filesList.isEmpty()) {
                        log.info("🐛 DEBUG: First file: {}", filesList.get(0));
                    }
                }
            }
            
            if (repositoryDetails.containsKey("commits")) {
                Object commits = repositoryDetails.get("commits");
                if (commits instanceof java.util.List) {
                    java.util.List<?> commitsList = (java.util.List<?>) commits;
                    debugResponse.put("commitsCount", commitsList.size());
                    log.info("🐛 DEBUG: Commits count: {}", commitsList.size());
                    if (!commitsList.isEmpty()) {
                        log.info("🐛 DEBUG: First commit: {}", commitsList.get(0));
                    }
                }
            }
            
            return ResponseEntity.ok(debugResponse);
            
        } catch (Exception e) {
            log.error("🐛 DEBUG: Error getting repository details", e);
            return ResponseEntity.ok(Map.of(
                "debug", true,
                "error", e.getMessage(),
                "repositoryId", repositoryId.toString()
            ));
        }
    }
}
