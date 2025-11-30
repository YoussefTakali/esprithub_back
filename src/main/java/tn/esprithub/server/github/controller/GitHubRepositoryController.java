package tn.esprithub.server.github.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tn.esprithub.server.admin.service.AdminUserDataService;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/github")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:4200", "http://127.0.0.1:4200"}, allowCredentials = "true")
public class GitHubRepositoryController {

    private final AdminUserDataService adminUserDataService;

    /**
     * Get repository details for admin dashboard
     */
    @GetMapping("/repositories/{repositoryId}/details")
    @PreAuthorize("hasRole('ADMIN') or hasRole('TEACHER')")
    public ResponseEntity<Map<String, Object>> getRepositoryDetails(@PathVariable UUID repositoryId) {
        log.info("🔍 Getting GitHub repository details for: {}", repositoryId);
        
        try {
            Map<String, Object> repositoryDetails = adminUserDataService.getRepositoryDetails(repositoryId);
            
            if (repositoryDetails.isEmpty()) {
                log.warn("❌ Repository not found: {}", repositoryId);
                return ResponseEntity.notFound().build();
            }
            
            log.info("✅ Successfully retrieved repository details for: {}", repositoryId);
            return ResponseEntity.ok(repositoryDetails);
            
        } catch (Exception e) {
            log.error("❌ Error getting repository details: {}", repositoryId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to get repository details",
                "message", e.getMessage(),
                "repositoryId", repositoryId.toString()
            ));
        }
    }

    /**
     * Get repository summary (lightweight version)
     */
    @GetMapping("/repositories/{repositoryId}/summary")
    @PreAuthorize("hasRole('ADMIN') or hasRole('TEACHER')")
    public ResponseEntity<Map<String, Object>> getRepositorySummary(@PathVariable UUID repositoryId) {
        log.info("🔍 Getting repository summary for: {}", repositoryId);
        
        try {
            // Get basic repository info without heavy data
            Map<String, Object> repositoryDetails = adminUserDataService.getRepositoryDetails(repositoryId);
            
            if (repositoryDetails.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            // Return only essential data for quick loading
            Map<String, Object> summary = new java.util.HashMap<>();
            summary.put("id", repositoryDetails.get("id"));
            summary.put("name", repositoryDetails.get("name"));
            summary.put("fullName", repositoryDetails.get("fullName"));
            summary.put("description", repositoryDetails.get("description"));
            summary.put("url", repositoryDetails.get("url"));
            summary.put("isPrivate", repositoryDetails.get("isPrivate"));
            summary.put("language", repositoryDetails.get("language"));
            summary.put("statistics", repositoryDetails.get("statistics"));
            summary.put("branchCount", repositoryDetails.get("branchCount"));
            summary.put("totalCommits", repositoryDetails.get("totalCommits"));
            summary.put("fileCount", repositoryDetails.get("fileCount"));
            summary.put("collaboratorCount", repositoryDetails.get("collaboratorCount"));
            
            return ResponseEntity.ok(summary);
            
        } catch (Exception e) {
            log.error("❌ Error getting repository summary: {}", repositoryId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to get repository summary",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Get repository files
     */
    @GetMapping("/repositories/{repositoryId}/files")
    @PreAuthorize("hasRole('ADMIN') or hasRole('TEACHER')")
    public ResponseEntity<Object> getRepositoryFiles(@PathVariable UUID repositoryId, 
                                                    @RequestParam(required = false) String branchName) {
        log.info("🔍 Getting files for repository: {} branch: {}", repositoryId, branchName);
        
        try {
            var files = adminUserDataService.getRepositoryFiles(repositoryId, branchName);
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            log.error("❌ Error getting repository files: {}", repositoryId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to get repository files",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Get file content by file ID
     */
    @GetMapping("/files/{fileId}/content")
    @PreAuthorize("hasRole('ADMIN') or hasRole('TEACHER')")
    public ResponseEntity<Object> getFileContent(@PathVariable UUID fileId) {
        log.info("🔍 Getting file content for: {}", fileId);
        
        try {
            var fileContent = adminUserDataService.getFileContent(fileId);
            if (fileContent.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(fileContent);
        } catch (Exception e) {
            log.error("❌ Error getting file content: {}", fileId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to get file content",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Get repository commits
     */
    @GetMapping("/repositories/{repositoryId}/commits")
    @PreAuthorize("hasRole('ADMIN') or hasRole('TEACHER')")
    public ResponseEntity<Object> getRepositoryCommits(@PathVariable UUID repositoryId,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "50") int size) {
        log.info("🔍 Getting commits for repository: {} page: {} size: {}", repositoryId, page, size);
        
        try {
            var details = adminUserDataService.getRepositoryDetails(repositoryId);
            if (details.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            var commits = details.get("commits");
            return ResponseEntity.ok(Map.of(
                "commits", commits,
                "totalCommits", details.get("totalCommits"),
                "repositoryId", repositoryId
            ));
        } catch (Exception e) {
            log.error("❌ Error getting repository commits: {}", repositoryId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to get repository commits",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Get commit details
     */
    @GetMapping("/commits/{commitId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('TEACHER')")
    public ResponseEntity<Object> getCommitDetails(@PathVariable UUID commitId) {
        log.info("🔍 Getting commit details for: {}", commitId);
        
        try {
            var commitDetails = adminUserDataService.getCommitDetails(commitId);
            if (commitDetails.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(commitDetails);
        } catch (Exception e) {
            log.error("❌ Error getting commit details: {}", commitId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to get commit details",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Test endpoint for debugging data issues
     */
    @GetMapping("/test/data")
    @PreAuthorize("hasRole('ADMIN') or hasRole('TEACHER')")
    public ResponseEntity<Object> testDataEndpoint() {
        log.info("🔍 Testing data endpoint");
        
        try {
            Map<String, Object> testData = Map.of(
                "message", "API is working correctly",
                "timestamp", System.currentTimeMillis(),
                "status", "success",
                "endpoints", Map.of(
                    "repositories", "/api/v1/github/repositories/{id}/details",
                    "files", "/api/v1/github/repositories/{id}/files",
                    "commits", "/api/v1/github/repositories/{id}/commits",
                    "fileContent", "/api/v1/github/files/{id}/content",
                    "webhookStatus", "/api/v1/webhooks/repository/{id}/status"
                )
            );
            
            return ResponseEntity.ok(testData);
        } catch (Exception e) {
            log.error("❌ Error in test endpoint", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Test endpoint failed",
                "message", e.getMessage()
            ));
        }
    }
}
