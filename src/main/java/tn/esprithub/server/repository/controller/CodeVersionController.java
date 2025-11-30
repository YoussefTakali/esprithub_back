package tn.esprithub.server.repository.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.esprithub.server.repository.dto.CodeVersionDto;
import tn.esprithub.server.repository.dto.CodeVersionComparisonDto;
import tn.esprithub.server.repository.dto.CodeVersionStatsDto;
import tn.esprithub.server.repository.service.CodeVersionService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/code-versions")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class CodeVersionController {

    private final CodeVersionService codeVersionService;

    // Get all versions of a specific file
    @GetMapping("/repository/{repositoryId}/file/{filePath}/history")
    public ResponseEntity<List<CodeVersionDto>> getFileVersionHistory(
            @PathVariable UUID repositoryId,
            @PathVariable String filePath,
            Authentication authentication) {
        log.info("Fetching version history for file: {} in repository: {} by teacher: {}", 
                filePath, repositoryId, authentication.getName());
        List<CodeVersionDto> versions = codeVersionService.getFileVersionHistory(repositoryId, filePath);
        return ResponseEntity.ok(versions);
    }

    // Get latest version of a specific file
    @GetMapping("/repository/{repositoryId}/file/{filePath}/latest")
    public ResponseEntity<CodeVersionDto> getLatestFileVersion(
            @PathVariable UUID repositoryId,
            @PathVariable String filePath,
            Authentication authentication) {
        log.info("Fetching latest version for file: {} in repository: {} by teacher: {}", 
                filePath, repositoryId, authentication.getName());
        CodeVersionDto version = codeVersionService.getLatestFileVersion(repositoryId, filePath);
        return ResponseEntity.ok(version);
    }

    // Get all versions in a repository with pagination
    @GetMapping("/repository/{repositoryId}")
    public ResponseEntity<Page<CodeVersionDto>> getRepositoryVersions(
            @PathVariable UUID repositoryId,
            Pageable pageable,
            Authentication authentication) {
        log.info("Fetching repository versions for: {} by teacher: {}", repositoryId, authentication.getName());
        Page<CodeVersionDto> versions = codeVersionService.getRepositoryVersions(repositoryId, pageable);
        return ResponseEntity.ok(versions);
    }

    // Get versions by commit SHA
    @GetMapping("/commit/{commitSha}")
    public ResponseEntity<List<CodeVersionDto>> getVersionsByCommit(
            @PathVariable String commitSha,
            Authentication authentication) {
        log.info("Fetching versions for commit: {} by teacher: {}", commitSha, authentication.getName());
        List<CodeVersionDto> versions = codeVersionService.getVersionsByCommit(commitSha);
        return ResponseEntity.ok(versions);
    }

    // Get versions by branch
    @GetMapping("/repository/{repositoryId}/branch/{branchName}")
    public ResponseEntity<List<CodeVersionDto>> getVersionsByBranch(
            @PathVariable UUID repositoryId,
            @PathVariable String branchName,
            Authentication authentication) {
        log.info("Fetching versions for branch: {} in repository: {} by teacher: {}", 
                branchName, repositoryId, authentication.getName());
        List<CodeVersionDto> versions = codeVersionService.getVersionsByBranch(repositoryId, branchName);
        return ResponseEntity.ok(versions);
    }

    // Get current state of repository (latest version of each file)
    @GetMapping("/repository/{repositoryId}/current-state")
    public ResponseEntity<List<CodeVersionDto>> getCurrentRepositoryState(
            @PathVariable UUID repositoryId,
            Authentication authentication) {
        log.info("Fetching current state for repository: {} by teacher: {}", repositoryId, authentication.getName());
        List<CodeVersionDto> currentState = codeVersionService.getCurrentRepositoryState(repositoryId);
        return ResponseEntity.ok(currentState);
    }

    // Compare two versions
    @GetMapping("/compare")
    public ResponseEntity<CodeVersionComparisonDto> compareVersions(
            @RequestParam UUID version1Id,
            @RequestParam UUID version2Id,
            Authentication authentication) {
        log.info("Comparing versions: {} vs {} by teacher: {}", version1Id, version2Id, authentication.getName());
        CodeVersionComparisonDto comparison = codeVersionService.compareVersions(version1Id, version2Id);
        return ResponseEntity.ok(comparison);
    }

    // Get repository version statistics
    @GetMapping("/repository/{repositoryId}/stats")
    public ResponseEntity<CodeVersionStatsDto> getRepositoryVersionStats(
            @PathVariable UUID repositoryId,
            Authentication authentication) {
        log.info("Fetching version statistics for repository: {} by teacher: {}", repositoryId, authentication.getName());
        CodeVersionStatsDto stats = codeVersionService.getRepositoryVersionStats(repositoryId);
        return ResponseEntity.ok(stats);
    }

    // Get file content at specific version
    @GetMapping("/version/{versionId}/content")
    public ResponseEntity<Map<String, String>> getFileContentAtVersion(
            @PathVariable UUID versionId,
            Authentication authentication) {
        log.info("Fetching file content for version: {} by teacher: {}", versionId, authentication.getName());
        String content = codeVersionService.getFileContentAtVersion(versionId);
        return ResponseEntity.ok(Map.of("content", content));
    }

    // Search versions by content
    @GetMapping("/repository/{repositoryId}/search")
    public ResponseEntity<List<CodeVersionDto>> searchVersionsByContent(
            @PathVariable UUID repositoryId,
            @RequestParam String query,
            Authentication authentication) {
        log.info("Searching versions in repository: {} with query: '{}' by teacher: {}", 
                repositoryId, query, authentication.getName());
        List<CodeVersionDto> versions = codeVersionService.searchVersionsByContent(repositoryId, query);
        return ResponseEntity.ok(versions);
    }

    // Manually trigger saving versions from a GitHub commit
    @PostMapping("/repository/{owner}/{repo}/sync-commit/{commitSha}")
    public ResponseEntity<List<CodeVersionDto>> syncCommitVersions(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable String commitSha,
            @RequestParam(required = false) String branch,
            Authentication authentication) {
        String repositoryFullName = owner + "/" + repo;
        String branchName = branch != null ? branch : "main";
        log.info("Syncing commit versions for repository: {} commit: {} by teacher: {}", 
                repositoryFullName, commitSha, authentication.getName());
        
        try {
            List<CodeVersionDto> versions = codeVersionService.saveCodeVersionsFromCommit(
                repositoryFullName, commitSha, "Manual sync", branchName, authentication.getName());
            return ResponseEntity.ok(versions);
        } catch (Exception e) {
            log.error("Error syncing commit versions: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    // Archive old versions
    @PostMapping("/repository/{repositoryId}/archive")
    public ResponseEntity<Map<String, String>> archiveOldVersions(
            @PathVariable UUID repositoryId,
            @RequestParam String beforeDate,
            Authentication authentication) {
        log.info("Archiving old versions for repository: {} before: {} by teacher: {}", 
                repositoryId, beforeDate, authentication.getName());
        
        try {
            LocalDateTime date = LocalDateTime.parse(beforeDate);
            codeVersionService.archiveOldVersions(repositoryId, date);
            return ResponseEntity.ok(Map.of("message", "Old versions archived successfully"));
        } catch (Exception e) {
            log.error("Error archiving versions: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to archive versions: " + e.getMessage()));
        }
    }
}
