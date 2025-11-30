package tn.esprithub.server.debug.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprithub.server.admin.service.AdminUserDataService;
import tn.esprithub.server.repository.repository.RepositoryCommitRepository;

import java.util.*;

@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"${app.cors.allowed-origins}"})
public class DebugController {

    private final AdminUserDataService adminUserDataService;
    private final RepositoryCommitRepository commitRepository;

    @GetMapping("/repository/{repositoryId}/details")
    public ResponseEntity<Map<String, Object>> getRepositoryDetails(@PathVariable UUID repositoryId) {
        log.info("Debug: Getting repository details for ID: {}", repositoryId);
        
        try {
            Map<String, Object> result = adminUserDataService.getRepositoryDetails(repositoryId);
            log.info("Debug: Repository details result size: {}", result.size());
            log.info("Debug: Repository details keys: {}", result.keySet());
            
            // Log specific data about files and commits
            if (result.containsKey("files")) {
                Object files = result.get("files");
                if (files instanceof List) {
                    log.info("Debug: Files count: {}", ((List<?>) files).size());
                    if (!((List<?>) files).isEmpty()) {
                        log.info("Debug: First file structure: {}", ((List<?>) files).get(0));
                    }
                }
            }
            
            if (result.containsKey("commits")) {
                Object commits = result.get("commits");
                if (commits instanceof List) {
                    log.info("Debug: Commits count: {}", ((List<?>) commits).size());
                    if (!((List<?>) commits).isEmpty()) {
                        log.info("Debug: First commit structure: {}", ((List<?>) commits).get(0));
                    }
                }
            }
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Debug: Error getting repository details", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/repository/{repositoryId}/files")
    public ResponseEntity<Map<String, Object>> getRepositoryFiles(@PathVariable UUID repositoryId) {
        log.info("Debug: Getting files for repository ID: {}", repositoryId);
        
        try {
            List<Map<String, Object>> files = adminUserDataService.getRepositoryFiles(repositoryId, null);
            log.info("Debug: Files count: {}", files.size());
            
            Map<String, Object> response = new HashMap<>();
            response.put("files", files);
            response.put("totalFiles", files.size());
            
            if (!files.isEmpty()) {
                log.info("Debug: Sample file: {}", files.get(0));
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Debug: Error getting files", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/repository/{repositoryId}/commits")
    public ResponseEntity<Map<String, Object>> getRepositoryCommits(@PathVariable UUID repositoryId) {
        log.info("Debug: Getting commits for repository ID: {}", repositoryId);
        
        try {
            var commits = commitRepository.findByRepositoryIdOrderByDateDesc(repositoryId);
            log.info("Debug: Raw commits count: {}", commits.size());
            
            List<Map<String, Object>> commitData = commits.stream()
                .limit(20) // Limit to first 20 for debugging
                .map(commit -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("id", commit.getId());
                    data.put("sha", commit.getSha());
                    data.put("message", commit.getMessage());
                    data.put("authorName", commit.getAuthorName());
                    data.put("authorEmail", commit.getAuthorEmail());
                    data.put("authorDate", commit.getAuthorDate());
                    data.put("committerName", commit.getCommitterName());
                    data.put("committerEmail", commit.getCommitterEmail());
                    data.put("committerDate", commit.getCommitterDate());
                    return data;
                })
                .toList();
            
            Map<String, Object> response = new HashMap<>();
            response.put("commits", commitData);
            response.put("totalCommits", commits.size());
            
            if (!commitData.isEmpty()) {
                log.info("Debug: Sample commit: {}", commitData.get(0));
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Debug: Error getting commits", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/file/{fileId}")
    public ResponseEntity<Map<String, Object>> getFileContent(@PathVariable UUID fileId) {
        log.info("Debug: Getting file content for ID: {}", fileId);
        
        try {
            Map<String, Object> fileData = adminUserDataService.getFileContent(fileId);
            log.info("Debug: File data keys: {}", fileData.keySet());
            
            if (fileData.containsKey("content")) {
                String content = (String) fileData.get("content");
                log.info("Debug: File content length: {}", content != null ? content.length() : "null");
            }
            
            return ResponseEntity.ok(fileData);
        } catch (Exception e) {
            log.error("Debug: Error getting file content", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
