package tn.esprithub.server.repository.service;

import tn.esprithub.server.repository.dto.FileUploadDto;
import tn.esprithub.server.repository.dto.RepositoryDto;
import tn.esprithub.server.repository.dto.RepositoryStatsDto;

import java.util.List;
import java.util.Map;

public interface RepositoryService {
    
    List<RepositoryDto> getTeacherRepositories(String teacherEmail);
    
    RepositoryStatsDto getRepositoryStats(String repoFullName, String teacherEmail);
    
    String uploadFile(String repoFullName, FileUploadDto uploadDto, String teacherEmail);

    List<Map<String, Object>> getRepositoryFiles(String repoFullName, String branch, String teacherEmail);
    List<String> getRepositoryBranches(String repoFullName, String teacherEmail);
    
    String deleteFile(String repoFullName, String filePath, String commitMessage, String branch, String teacherEmail);
    
    // New methods for branch and collaborator management
    void createBranch(String repoFullName, String branchName, String fromBranch, String teacherEmail);
    
    void deleteBranch(String repoFullName, String branchName, String teacherEmail);
    
    List<Map<String, Object>> getCollaborators(String repoFullName, String teacherEmail);
    
    void addCollaborator(String repoFullName, String username, String permission, String teacherEmail);
    
    void removeCollaborator(String repoFullName, String username, String teacherEmail);
    
    List<Map<String, Object>> getCommits(String repoFullName, String branch, int page, String teacherEmail);


    /**
     * Get file content from GitHub for a teacher (like student version)
     */
    Map<String, Object> getFileContent(String owner, String repo, String path, String branch, String teacherEmail);

    Object getLatestCommit(String owner, String repo, String path, String branch, String teacherEmail);

    void updateRepository(String repoFullName, Map<String, Object> settings, String teacherEmail);

    void deleteRepository(String repoFullName, String teacherEmail);

    RepositoryDto createRepository(String repositoryName, String description, boolean isPrivate, String teacherEmail);
    void cancelInvitation(String repoFullName, String username, String teacherEmail);
     int getCommitCount(String owner, String repo, String branch, String teacherEmail);
    Map<String, Object> getRepoLanguages(String repoFullName, String branch, String teacherEmail) ;
    public List<Object> getLatestRepoCommit(String repoFullName, String branch, String teacherEmail);
    public List<Object> getBranchCommits(String repoFullName, String branch, String teacherEmail);
    public List<Object> listBranches(String repoFullName, String teacherEmail);
    public Map<String, Object> getRepositoryLanguages(String repoFullName, String branch, String teacherEmail);
}
