package tn.esprithub.server.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprithub.server.repository.entity.*;
import tn.esprithub.server.repository.repository.*;
import tn.esprithub.server.project.entity.Task;
import tn.esprithub.server.project.repository.TaskRepository;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.github.service.GitHubRepositoryService;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserDataService {
    
    private final RepositoryEntityRepository repositoryRepository;
    private final RepositoryBranchRepository branchRepository;
    private final RepositoryCommitRepository commitRepository;
    private final RepositoryFileRepository fileRepository;
    private final RepositoryFileChangeRepository fileChangeRepository;
    private final RepositoryCollaboratorRepository collaboratorRepository;
    private final TaskRepository taskRepository;
    private final GitHubRepositoryService gitHubRepositoryService;
    
    /**
     * Get comprehensive user data with summary information only (no large content)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getComprehensiveUserData(User user) {
        log.info("Getting comprehensive data for user: {}", user.getEmail());

        Map<String, Object> userData = new HashMap<>();

        // Basic user info
        userData.put("id", user.getId());
        userData.put("email", user.getEmail());
        userData.put("firstName", user.getFirstName());
        userData.put("lastName", user.getLastName());
        userData.put("fullName", user.getFirstName() + " " + user.getLastName());
        userData.put("role", user.getRole().name());
        userData.put("githubUsername", user.getGithubUsername());
        userData.put("githubName", user.getGithubName());
        userData.put("isActive", user.getIsActive());
        userData.put("isEmailVerified", user.getIsEmailVerified());
        userData.put("lastLogin", user.getLastLogin());
        userData.put("createdAt", user.getCreatedAt());
        userData.put("updatedAt", user.getUpdatedAt());

        // Department and class info
        if (user.getDepartement() != null) {
            Map<String, Object> dept = new HashMap<>();
            dept.put("id", user.getDepartement().getId());
            dept.put("nom", user.getDepartement().getNom());
            userData.put("departement", dept);
        }

        if (user.getClasse() != null) {
            Map<String, Object> classe = new HashMap<>();
            classe.put("id", user.getClasse().getId());
            classe.put("nom", user.getClasse().getNom());
            userData.put("classe", classe);
        }

        // Get repositories with SUMMARY data only (no large content)
        List<Repository> repositories = repositoryRepository.findByOwnerIdAndIsActiveTrue(user.getId());
        userData.put("repositories", getRepositoriesSummary(repositories));

        // Get task counts only (ultra-lightweight)
        long totalTasks = taskRepository.countTasksAssignedToUser(user.getId());
        long completedTasks = taskRepository.countCompletedTasksForUser(user.getId());

        Map<String, Object> taskSummary = new HashMap<>();
        taskSummary.put("totalTasks", totalTasks);
        taskSummary.put("completedTasks", completedTasks);
        taskSummary.put("pendingTasks", totalTasks - completedTasks);
        userData.put("taskSummary", taskSummary);

        // Get statistics
        userData.put("statistics", getUserStatistics(user.getId(), repositories));

        return userData;
    }
    
    /**
     * Get repositories with minimal summary information (fastest response)
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRepositoriesSummary(List<Repository> repositories) {
        return repositories.stream().map(repo -> {
            Map<String, Object> repoData = new HashMap<>();

            // Basic repository info only
            repoData.put("id", repo.getId());
            repoData.put("name", repo.getName());
            repoData.put("fullName", repo.getFullName());
            repoData.put("description", repo.getDescription());
            repoData.put("url", repo.getUrl());
            repoData.put("isPrivate", repo.getIsPrivate());
            repoData.put("defaultBranch", repo.getDefaultBranch());
            repoData.put("language", repo.getLanguage());
            repoData.put("starCount", repo.getStarCount());
            repoData.put("forkCount", repo.getForkCount());
            repoData.put("lastSyncAt", repo.getLastSyncAt());

            // Just counts (no actual data)
            repoData.put("branchCount", branchRepository.countByRepositoryId(repo.getId()));
            repoData.put("totalCommits", commitRepository.countByRepositoryId(repo.getId()));
            repoData.put("fileCount", fileRepository.countByRepositoryId(repo.getId()));
            repoData.put("collaboratorCount", collaboratorRepository.countActiveByRepositoryId(repo.getId()));

            return repoData;
        }).collect(Collectors.toList());
    }

    /**
     * Get minimal task summaries (ultra-lightweight)
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTaskSummaries(List<Task> tasks) {
        return tasks.stream().map(task -> {
            Map<String, Object> taskData = new HashMap<>();

            // Only essential task info
            taskData.put("id", task.getId());
            taskData.put("title", task.getTitle());
            taskData.put("status", task.getStatus() != null ? task.getStatus().toString() : "UNKNOWN");
            taskData.put("dueDate", task.getDueDate());
            taskData.put("isGraded", task.isGraded());

            return taskData;
        }).collect(Collectors.toList());
    }

    /**
     * Get repositories with summary information (no large data like file contents)
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRepositoriesWithDetails(List<Repository> repositories) {
        return repositories.stream().map(repo -> {
            Map<String, Object> repoData = new HashMap<>();

            // Basic repository info
            repoData.put("id", repo.getId());
            repoData.put("name", repo.getName());
            repoData.put("fullName", repo.getFullName());
            repoData.put("description", repo.getDescription());
            repoData.put("url", repo.getUrl());
            repoData.put("isPrivate", repo.getIsPrivate());
            repoData.put("defaultBranch", repo.getDefaultBranch());
            repoData.put("language", repo.getLanguage());
            repoData.put("starCount", repo.getStarCount());
            repoData.put("forkCount", repo.getForkCount());
            repoData.put("watchersCount", repo.getWatchersCount());
            repoData.put("lastSyncAt", repo.getLastSyncAt());
            repoData.put("createdAt", repo.getCreatedAt());
            repoData.put("updatedAt", repo.getUpdatedAt());

            // Get branch count only (not full branch data)
            long branchCount = branchRepository.countByRepositoryId(repo.getId());
            repoData.put("branchCount", branchCount);

            // Get commit count only (not full commit data)
            long totalCommits = commitRepository.countByRepositoryId(repo.getId());
            repoData.put("totalCommits", totalCommits);

            // Get file count
            long fileCount = fileRepository.countByRepositoryId(repo.getId());
            repoData.put("fileCount", fileCount);

            // Get collaborator count only
            long collaboratorCount = collaboratorRepository.countActiveByRepositoryId(repo.getId());
            repoData.put("collaboratorCount", collaboratorCount);

            return repoData;
        }).collect(Collectors.toList());
    }

    /**
     * Get comprehensive repository information including ALL data (files, commits, collaborators, etc.)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getRepositoryDetails(UUID repositoryId) {
        Optional<Repository> repoOpt = repositoryRepository.findById(repositoryId);
        if (repoOpt.isEmpty()) {
            return new HashMap<>();
        }

        Repository repo = repoOpt.get();
        Map<String, Object> repoData = new HashMap<>();

        // Basic repository info
        repoData.put("id", repo.getId());
        repoData.put("name", repo.getName());
        repoData.put("fullName", repo.getFullName());
        repoData.put("description", repo.getDescription());
        repoData.put("url", repo.getUrl());
        repoData.put("isPrivate", repo.getIsPrivate());
        repoData.put("defaultBranch", repo.getDefaultBranch());
        repoData.put("language", repo.getLanguage());
        repoData.put("starCount", repo.getStarCount());
        repoData.put("forkCount", repo.getForkCount());
        repoData.put("watchersCount", repo.getWatchersCount());
        repoData.put("lastSyncAt", repo.getLastSyncAt());
        repoData.put("createdAt", repo.getCreatedAt());
        repoData.put("updatedAt", repo.getUpdatedAt());

        // Get ALL branches with details
        List<RepositoryBranch> branches = branchRepository.findByRepositoryIdOrderByIsDefaultDescNameAsc(repo.getId());
        repoData.put("branches", branches.stream().map(this::mapBranchToData).collect(Collectors.toList()));
        repoData.put("branchCount", branches.size());

        // Get ALL commits (not just recent ones)
        List<RepositoryCommit> allCommits = commitRepository.findByRepositoryIdOrderByDateDesc(repo.getId());
        repoData.put("commits", allCommits.stream().map(this::mapCommitToData).collect(Collectors.toList()));
        repoData.put("totalCommits", allCommits.size());

        // Get recent commits separately for quick access
        List<RepositoryCommit> recentCommits = allCommits.stream().limit(10).collect(Collectors.toList());
        repoData.put("recentCommits", recentCommits.stream().map(this::mapCommitToData).collect(Collectors.toList()));

        // Get ALL collaborators with details
        List<RepositoryCollaborator> collaborators = collaboratorRepository.findByRepositoryIdAndIsActiveTrue(repo.getId());
        repoData.put("collaborators", collaborators.stream().map(this::mapCollaboratorToData).collect(Collectors.toList()));
        repoData.put("collaboratorCount", collaborators.size());

        // Get ALL files (without content for performance, but with metadata)
        List<RepositoryFile> files = fileRepository.findByRepositoryIdOrderByFilePathAsc(repo.getId());
        repoData.put("files", files.stream().map(this::mapFileToDataWithoutContent).collect(Collectors.toList()));
        repoData.put("fileCount", files.size());

        // Get file statistics by language
        Map<String, Long> languageStats = files.stream()
            .filter(file -> file.getLanguage() != null && !file.getLanguage().isEmpty())
            .collect(Collectors.groupingBy(RepositoryFile::getLanguage, Collectors.counting()));
        repoData.put("languageStatistics", languageStats);

        // Get repository statistics
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("totalFiles", files.size());
        statistics.put("totalCommits", allCommits.size());
        statistics.put("totalBranches", branches.size());
        statistics.put("totalCollaborators", collaborators.size());
        statistics.put("totalLines", files.stream().mapToLong(f -> f.getLinesCount() != null ? f.getLinesCount() : 0).sum());
        statistics.put("totalSize", files.stream().mapToLong(f -> f.getFileSize() != null ? f.getFileSize() : 0).sum());
        repoData.put("statistics", statistics);

        return repoData;
    }
    
    /**
     * Get repository files without content (for listing)
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRepositoryFiles(UUID repositoryId, String branchName) {
        List<RepositoryFile> files;

        if (branchName != null && !branchName.isEmpty()) {
            files = fileRepository.findByRepositoryIdAndBranchNameOrderByFilePathAsc(repositoryId, branchName);
        } else {
            // Get files from default branch
            files = fileRepository.findByRepositoryIdOrderByFilePathAsc(repositoryId);
        }

        return files.stream().map(this::mapFileToDataWithoutContent).collect(Collectors.toList());
    }

    /**
     * Get specific file with content
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getFileContent(UUID fileId) {
        // Try to fetch from GitHub first (if possible)
        Optional<RepositoryFile> fileOpt = fileRepository.findById(fileId);
        if (fileOpt.isPresent()) {
            RepositoryFile file = fileOpt.get();
            try {
                // Try to get repo info
                var repo = file.getRepository();
                String fullName = repo.getFullName();
                String[] parts = fullName.split("/");
                if (parts.length == 2) {
                    String owner = parts[0];
                    String repoName = parts[1];
                    String branch = file.getBranchName();
                    String path = file.getFilePath();
                    // Try to fetch from GitHub
                    // You need a user with a valid GitHub token; for now, fallback to DB if not available
                    // TODO: Pass user context for token
                    // Map<String, Object> githubFile = gitHubRepositoryService.fetchFileContent(owner, repoName, branch, path, userToken);
                    // if (githubFile != null && githubFile.get("content") != null) return githubFile;
                }
            } catch (Exception e) {
                log.warn("Failed to fetch from GitHub, falling back to DB: {}", e.getMessage());
            }
            // Fallback to DB
            return mapFileToDataWithContent(file);
        }
        // Not found in DB, cannot fetch from GitHub without metadata
        return new HashMap<>();
    }
    
    /**
     * Get commit details with file changes
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCommitDetails(UUID commitId) {
        Optional<RepositoryCommit> commitOpt = commitRepository.findById(commitId);
        if (commitOpt.isEmpty()) {
            return new HashMap<>();
        }
        RepositoryCommit commit = commitOpt.get();
        Map<String, Object> commitData = mapCommitToData(commit);
        // Get file changes for this commit (first 100 only, chunked)
        List<RepositoryFileChange> fileChanges = fileChangeRepository.findByCommitIdOrderByFilePathAsc(commitId, org.springframework.data.domain.PageRequest.of(0, 100));
        commitData.put("fileChanges", fileChanges.stream().map(this::mapFileChangeToData).toList());
        commitData.put("fileChangesTotal", fileChangeRepository.countByCommitId(commitId));
        return commitData;
    }
    
    /**
     * Get user statistics
     */
    private Map<String, Object> getUserStatistics(UUID userId, List<Repository> repositories) {
        Map<String, Object> stats = new HashMap<>();
        
        // Repository statistics
        stats.put("totalRepositories", repositories.size());
        stats.put("privateRepositories", (int) repositories.stream().filter(r -> r.getIsPrivate()).count());
        stats.put("publicRepositories", (int) repositories.stream().filter(r -> !r.getIsPrivate()).count());
        
        // Commit statistics
        long totalCommits = repositories.stream()
                .mapToLong(repo -> commitRepository.countByRepositoryId(repo.getId()))
                .sum();
        stats.put("totalCommits", totalCommits);
        
        // File statistics
        long totalFiles = repositories.stream()
                .mapToLong(repo -> fileRepository.countByRepositoryId(repo.getId()))
                .sum();
        stats.put("totalFiles", totalFiles);
        
        // Branch statistics
        long totalBranches = repositories.stream()
                .mapToLong(repo -> branchRepository.countByRepositoryId(repo.getId()))
                .sum();
        stats.put("totalBranches", totalBranches);
        
        // Task statistics
        long totalTasks = taskRepository.countTasksAssignedToUser(userId);
        long completedTasks = taskRepository.countCompletedTasksForUser(userId);
        stats.put("totalTasks", totalTasks);
        stats.put("completedTasks", completedTasks);
        stats.put("pendingTasks", totalTasks - completedTasks);
        
        return stats;
    }
    
    // Helper mapping methods
    private Map<String, Object> mapBranchToData(RepositoryBranch branch) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", branch.getId());
        data.put("name", branch.getName());
        data.put("sha", branch.getSha());
        data.put("isProtected", branch.getIsProtected());
        data.put("isDefault", branch.getIsDefault());
        data.put("lastCommitMessage", branch.getLastCommitMessage());
        data.put("lastCommitAuthor", branch.getLastCommitAuthor());
        data.put("lastCommitDate", branch.getLastCommitDate());
        data.put("commitsCount", branch.getCommitsCount());
        return data;
    }
    
    private Map<String, Object> mapCommitToData(RepositoryCommit commit) {
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
        data.put("additions", commit.getAdditions());
        data.put("deletions", commit.getDeletions());
        data.put("totalChanges", commit.getTotalChanges());
        data.put("filesChanged", commit.getFilesChanged());
        data.put("githubUrl", commit.getGithubUrl());
        return data;
    }
    
    private Map<String, Object> mapFileToDataWithoutContent(RepositoryFile file) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", file.getId());
        data.put("filePath", file.getFilePath());
        data.put("fileName", file.getFileName());
        data.put("branchName", file.getBranchName());
        data.put("fileType", file.getFileType());
        data.put("fileExtension", file.getFileExtension());
        data.put("fileSize", file.getFileSize());
        data.put("language", file.getLanguage());
        data.put("isBinary", file.getIsBinary());
        data.put("linesCount", file.getLinesCount());
        data.put("lastModified", file.getLastModified());
        // No content included to avoid large JSON responses
        return data;
    }

    private Map<String, Object> mapFileToDataWithContent(RepositoryFile file) {
        Map<String, Object> data = mapFileToDataWithoutContent(file);
        data.put("content", file.getContent()); // Include content for viewing
        return data;
    }
    
    private Map<String, Object> mapFileChangeToData(RepositoryFileChange change) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", change.getId());
        data.put("filePath", change.getFilePath());
        data.put("fileName", change.getFileName());
        data.put("changeType", change.getChangeType());
        data.put("previousFilePath", change.getPreviousFilePath());
        data.put("additions", change.getAdditions());
        data.put("deletions", change.getDeletions());
        data.put("changes", change.getChanges());
        data.put("patch", change.getPatch());
        return data;
    }
    
    private Map<String, Object> mapCollaboratorToData(RepositoryCollaborator collaborator) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", collaborator.getId());
        data.put("githubUsername", collaborator.getGithubUsername());
        data.put("avatarUrl", collaborator.getAvatarUrl());
        data.put("permissionLevel", collaborator.getPermissionLevel());
        data.put("roleName", collaborator.getRoleName());
        data.put("contributionsCount", collaborator.getContributionsCount());
        data.put("commitsCount", collaborator.getCommitsCount());
        return data;
    }
}
