package tn.esprithub.server.repository.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import tn.esprithub.server.repository.dto.CodeVersionDto;
import tn.esprithub.server.repository.dto.CodeVersionComparisonDto;
import tn.esprithub.server.repository.dto.CodeVersionStatsDto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface CodeVersionService {
    
    /**
     * Save a new code version from GitHub commit
     */
    CodeVersionDto saveCodeVersion(String repositoryFullName, String commitSha, String filePath, 
                                  String fileContent, String commitMessage, String branchName, 
                                  String teacherEmail);
    
    /**
     * Bulk save multiple code versions from a GitHub commit
     */
    List<CodeVersionDto> saveCodeVersionsFromCommit(String repositoryFullName, String commitSha, 
                                                   String commitMessage, String branchName, 
                                                   String teacherEmail);
    
    /**
     * Get all versions of a specific file
     */
    List<CodeVersionDto> getFileVersionHistory(UUID repositoryId, String filePath);
    
    /**
     * Get the latest version of a specific file
     */
    CodeVersionDto getLatestFileVersion(UUID repositoryId, String filePath);
    
    /**
     * Get all versions in a repository with pagination
     */
    Page<CodeVersionDto> getRepositoryVersions(UUID repositoryId, Pageable pageable);
    
    /**
     * Get versions by commit SHA
     */
    List<CodeVersionDto> getVersionsByCommit(String commitSha);
    
    /**
     * Get versions by author
     */
    List<CodeVersionDto> getVersionsByAuthor(UUID authorId);
    
    /**
     * Get versions in a specific branch
     */
    List<CodeVersionDto> getVersionsByBranch(UUID repositoryId, String branchName);
    
    /**
     * Get versions between dates
     */
    List<CodeVersionDto> getVersionsByDateRange(UUID repositoryId, LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Get versions by programming language
     */
    List<CodeVersionDto> getVersionsByLanguage(UUID repositoryId, String language);
    
    /**
     * Get versions with specific tags
     */
    List<CodeVersionDto> getVersionsByTag(UUID repositoryId, String tag);
    
    /**
     * Get latest version of each file in repository (current state)
     */
    List<CodeVersionDto> getCurrentRepositoryState(UUID repositoryId);
    
    /**
     * Compare two versions of the same file
     */
    CodeVersionComparisonDto compareVersions(UUID version1Id, UUID version2Id);
    
    /**
     * Get version statistics for a repository
     */
    CodeVersionStatsDto getRepositoryVersionStats(UUID repositoryId);
    
    /**
     * Archive old versions (soft delete)
     */
    void archiveOldVersions(UUID repositoryId, LocalDateTime beforeDate);
    
    /**
     * Restore archived version
     */
    CodeVersionDto restoreVersion(UUID versionId);
    
    /**
     * Delete version permanently
     */
    void deleteVersion(UUID versionId);
    
    /**
     * Get file content at specific version
     */
    String getFileContentAtVersion(UUID versionId);
    
    /**
     * Search versions by content
     */
    List<CodeVersionDto> searchVersionsByContent(UUID repositoryId, String searchQuery);
}
