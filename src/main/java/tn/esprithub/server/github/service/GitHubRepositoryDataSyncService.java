package tn.esprithub.server.github.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import tn.esprithub.server.repository.entity.*;
import tn.esprithub.server.repository.repository.*;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubRepositoryDataSyncService {
    
    private static final String GITHUB_API_BASE = "https://api.github.com";
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final RepositoryEntityRepository repositoryRepository;
    private final RepositoryBranchRepository branchRepository;
    private final RepositoryCommitRepository commitRepository;
    private final RepositoryFileRepository fileRepository;
    private final RepositoryCollaboratorRepository collaboratorRepository;
    private final UserRepository userRepository;
    
    /**
     * Sync ALL data for a repository from GitHub
     */
    @Transactional
    public void syncRepositoryData(Repository repository, String githubToken) {
        log.info("🔄 Starting comprehensive sync for repository: {}", repository.getFullName());
        
        try {
            repository.setSyncStatus("SYNCING");
            repository.setSyncError(null);
            repositoryRepository.save(repository);
            
            // 1. Sync repository metadata
            syncRepositoryMetadata(repository, githubToken);
            
            // 2. Sync branches
            syncRepositoryBranches(repository, githubToken);
            
            // 3. Sync commits for each branch
            syncRepositoryCommits(repository, githubToken);
            
            // 4. Sync files for default branch
            syncRepositoryFiles(repository, githubToken);
            
            // 5. Sync collaborators
            syncRepositoryCollaborators(repository, githubToken);
            
            repository.setSyncStatus("COMPLETED");
            repository.setLastSyncAt(LocalDateTime.now());
            repositoryRepository.save(repository);
            
            log.info("✅ Successfully synced repository: {}", repository.getFullName());
            
        } catch (Exception e) {
            log.error("❌ Error syncing repository: {}", repository.getFullName(), e);
            repository.setSyncStatus("FAILED");
            repository.setSyncError(e.getMessage());
            repositoryRepository.save(repository);
        }
    }
    
    /**
     * Sync repository metadata (languages, stats, etc.)
     */
    private void syncRepositoryMetadata(Repository repository, String githubToken) {
        try {
            // Get repository details
            String repoUrl = GITHUB_API_BASE + "/repos/" + repository.getFullName();
            JsonNode repoData = makeGitHubApiCall(repoUrl, githubToken);
            
            if (repoData != null) {
                repository.setLanguage(getStringValue(repoData, "language"));
                repository.setStarCount(getIntValue(repoData, "stargazers_count"));
                repository.setForkCount(getIntValue(repoData, "forks_count"));
                repository.setWatchersCount(getIntValue(repoData, "watchers_count"));
                repository.setOpenIssuesCount(getIntValue(repoData, "open_issues_count"));
                repository.setSizeKb(getLongValue(repoData, "size"));
                repository.setPushedAt(parseGitHubDate(getStringValue(repoData, "pushed_at")));
                repository.setArchived(getBooleanValue(repoData, "archived"));
                repository.setDisabled(getBooleanValue(repoData, "disabled"));
                repository.setFork(getBooleanValue(repoData, "fork"));
                repository.setHasIssues(getBooleanValue(repoData, "has_issues"));
                repository.setHasProjects(getBooleanValue(repoData, "has_projects"));
                repository.setHasWiki(getBooleanValue(repoData, "has_wiki"));
                repository.setHasPages(getBooleanValue(repoData, "has_pages"));
                repository.setHasDownloads(getBooleanValue(repoData, "has_downloads"));
                
                if (repoData.has("license") && !repoData.get("license").isNull()) {
                    repository.setLicenseName(getStringValue(repoData.get("license"), "name"));
                }
                
                if (repoData.has("topics") && repoData.get("topics").isArray()) {
                    repository.setTopicsJson(repoData.get("topics").toString());
                }
            }
            
            // Get languages
            String languagesUrl = GITHUB_API_BASE + "/repos/" + repository.getFullName() + "/languages";
            JsonNode languagesData = makeGitHubApiCall(languagesUrl, githubToken);
            
            if (languagesData != null) {
                repository.setLanguagesJson(languagesData.toString());
            }
            
            log.debug("✅ Synced metadata for repository: {}", repository.getFullName());
            
        } catch (Exception e) {
            log.error("❌ Error syncing repository metadata: {}", repository.getFullName(), e);
        }
    }
    
    /**
     * Sync all branches for a repository
     */
    private void syncRepositoryBranches(Repository repository, String githubToken) {
        try {
            String branchesUrl = GITHUB_API_BASE + "/repos/" + repository.getFullName() + "/branches";
            JsonNode branchesData = makeGitHubApiCall(branchesUrl, githubToken);
            
            if (branchesData != null && branchesData.isArray()) {
                for (JsonNode branchNode : branchesData) {
                    String branchName = getStringValue(branchNode, "name");
                    String sha = getStringValue(branchNode.get("commit"), "sha");
                    boolean isProtected = getBooleanValue(branchNode, "protected");
                    boolean isDefault = branchName.equals(repository.getDefaultBranch());
                    
                    RepositoryBranch branch = branchRepository
                            .findByRepositoryIdAndName(repository.getId(), branchName)
                            .orElse(RepositoryBranch.builder()
                                    .repository(repository)
                                    .name(branchName)
                                    .build());
                    
                    branch.setSha(sha);
                    branch.setIsProtected(isProtected);
                    branch.setIsDefault(isDefault);
                    
                    // Get commit details for the branch
                    if (branchNode.has("commit")) {
                        JsonNode commitNode = branchNode.get("commit");
                        if (commitNode.has("commit")) {
                            JsonNode commitDetails = commitNode.get("commit");
                            branch.setLastCommitMessage(getStringValue(commitDetails, "message"));
                            if (commitDetails.has("author")) {
                                branch.setLastCommitAuthor(getStringValue(commitDetails.get("author"), "name"));
                                branch.setLastCommitDate(parseGitHubDate(getStringValue(commitDetails.get("author"), "date")));
                            }
                        }
                    }
                    
                    branchRepository.save(branch);
                }
                
                log.debug("✅ Synced {} branches for repository: {}", branchesData.size(), repository.getFullName());
            }
            
        } catch (Exception e) {
            log.error("❌ Error syncing repository branches: {}", repository.getFullName(), e);
        }
    }
    
    /**
     * Sync commits for repository (limited to recent commits to avoid overwhelming the database)
     */
    private void syncRepositoryCommits(Repository repository, String githubToken) {
        try {
            // Get commits for the default branch (limit to recent 100 commits)
            String commitsUrl = GITHUB_API_BASE + "/repos/" + repository.getFullName() + "/commits?per_page=100";
            JsonNode commitsData = makeGitHubApiCall(commitsUrl, githubToken);
            
            if (commitsData != null && commitsData.isArray()) {
                for (JsonNode commitNode : commitsData) {
                    String sha = getStringValue(commitNode, "sha");
                    
                    // Skip if commit already exists
                    if (commitRepository.existsByRepositoryIdAndSha(repository.getId(), sha)) {
                        continue;
                    }
                    
                    JsonNode commitDetails = commitNode.get("commit");
                    JsonNode author = commitDetails.get("author");
                    JsonNode committer = commitDetails.get("committer");
                    JsonNode stats = commitNode.get("stats");
                    
                    RepositoryCommit commit = RepositoryCommit.builder()
                            .repository(repository)
                            .sha(sha)
                            .message(getStringValue(commitDetails, "message"))
                            .authorName(getStringValue(author, "name"))
                            .authorEmail(getStringValue(author, "email"))
                            .authorDate(parseGitHubDate(getStringValue(author, "date")))
                            .committerName(getStringValue(committer, "name"))
                            .committerEmail(getStringValue(committer, "email"))
                            .committerDate(parseGitHubDate(getStringValue(committer, "date")))
                            .githubUrl(getStringValue(commitNode, "html_url"))
                            .build();
                    
                    if (stats != null) {
                        commit.setAdditions(getIntValue(stats, "additions"));
                        commit.setDeletions(getIntValue(stats, "deletions"));
                        commit.setTotalChanges(getIntValue(stats, "total"));
                    }
                    
                    if (commitNode.has("parents") && commitNode.get("parents").isArray()) {
                        List<String> parentShas = new ArrayList<>();
                        for (JsonNode parent : commitNode.get("parents")) {
                            parentShas.add(getStringValue(parent, "sha"));
                        }
                        commit.setParentShas(String.join(",", parentShas));
                    }
                    
                    commitRepository.save(commit);
                }
                
                log.debug("✅ Synced {} commits for repository: {}", commitsData.size(), repository.getFullName());
            }
            
        } catch (Exception e) {
            log.error("❌ Error syncing repository commits: {}", repository.getFullName(), e);
        }
    }
    
    /**
     * Sync files for repository (default branch only, to avoid overwhelming the database)
     */
    private void syncRepositoryFiles(Repository repository, String githubToken) {
        try {
            String defaultBranch = repository.getDefaultBranch();
            if (defaultBranch == null || defaultBranch.isBlank()) {
                defaultBranch = "main"; // fallback
            }
            
            syncDirectoryFiles(repository, githubToken, defaultBranch, "", 0);
            
            log.debug("✅ Synced files for repository: {} (branch: {})", repository.getFullName(), defaultBranch);
            
        } catch (Exception e) {
            log.error("❌ Error syncing repository files: {}", repository.getFullName(), e);
        }
    }
    
    /**
     * Recursively sync files in a directory (with depth limit to avoid infinite recursion)
     */
    private void syncDirectoryFiles(Repository repository, String githubToken, String branchName, String path, int depth) {
        if (depth > 5) { // Limit recursion depth
            return;
        }
        
        try {
            String contentsUrl = GITHUB_API_BASE + "/repos/" + repository.getFullName() + "/contents/" + path + "?ref=" + branchName;
            JsonNode contentsData = makeGitHubApiCall(contentsUrl, githubToken);
            
            if (contentsData != null && contentsData.isArray()) {
                for (JsonNode fileNode : contentsData) {
                    String filePath = getStringValue(fileNode, "path");
                    String fileName = getStringValue(fileNode, "name");
                    String fileType = getStringValue(fileNode, "type");
                    
                    RepositoryFile file = fileRepository
                            .findByRepositoryIdAndBranchNameAndFilePath(repository.getId(), branchName, filePath)
                            .orElse(RepositoryFile.builder()
                                    .repository(repository)
                                    .branchName(branchName)
                                    .filePath(filePath)
                                    .fileName(fileName)
                                    .build());
                    
                    file.setFileType(fileType);
                    file.setSha(getStringValue(fileNode, "sha"));
                    file.setFileSize(getLongValue(fileNode, "size"));
                    file.setGithubUrl(getStringValue(fileNode, "html_url"));
                    file.setDownloadUrl(getStringValue(fileNode, "download_url"));
                    
                    if (fileName.contains(".")) {
                        String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
                        file.setFileExtension(extension);
                        file.setLanguage(detectLanguage(extension));
                    }
                    
                    // For files (not directories), get content if it's small enough
                    if ("file".equals(fileType) && file.getFileSize() != null && file.getFileSize() < 1024 * 1024) { // < 1MB
                        try {
                            if (fileNode.has("content")) {
                                String content = getStringValue(fileNode, "content");
                                String encoding = getStringValue(fileNode, "encoding");
                                
                                file.setContent(content);
                                file.setEncoding(encoding);
                                file.setIsBinary("base64".equals(encoding));
                                
                                // Count lines for text files
                                if (!"base64".equals(encoding) && content != null) {
                                    file.setLinesCount(content.split("\n").length);
                                }
                            }
                        } catch (Exception e) {
                            log.debug("Could not get content for file: {}", filePath);
                        }
                    }
                    
                    fileRepository.save(file);
                    
                    // Recursively process directories
                    if ("dir".equals(fileType)) {
                        syncDirectoryFiles(repository, githubToken, branchName, filePath, depth + 1);
                    }
                }
            }
            
        } catch (Exception e) {
            log.debug("Could not sync directory: {} (this is normal for large repositories)", path);
        }
    }
    
    /**
     * Sync collaborators for repository
     */
    private void syncRepositoryCollaborators(Repository repository, String githubToken) {
        try {
            String collaboratorsUrl = GITHUB_API_BASE + "/repos/" + repository.getFullName() + "/collaborators";
            JsonNode collaboratorsData = makeGitHubApiCall(collaboratorsUrl, githubToken);
            
            if (collaboratorsData != null && collaboratorsData.isArray()) {
                for (JsonNode collabNode : collaboratorsData) {
                    String githubUsername = getStringValue(collabNode, "login");
                    Long githubUserId = getLongValue(collabNode, "id");
                    
                    RepositoryCollaborator collaborator = collaboratorRepository
                            .findByRepositoryIdAndGithubUsername(repository.getId(), githubUsername)
                            .orElse(RepositoryCollaborator.builder()
                                    .repository(repository)
                                    .githubUsername(githubUsername)
                                    .build());
                    
                    collaborator.setGithubUserId(githubUserId);
                    collaborator.setAvatarUrl(getStringValue(collabNode, "avatar_url"));
                    collaborator.setGithubProfileUrl(getStringValue(collabNode, "html_url"));
                    
                    // Get permissions
                    if (collabNode.has("permissions")) {
                        JsonNode permissions = collabNode.get("permissions");
                        if (getBooleanValue(permissions, "admin")) {
                            collaborator.setPermissionLevel("admin");
                        } else if (getBooleanValue(permissions, "maintain")) {
                            collaborator.setPermissionLevel("maintain");
                        } else if (getBooleanValue(permissions, "push")) {
                            collaborator.setPermissionLevel("write");
                        } else if (getBooleanValue(permissions, "triage")) {
                            collaborator.setPermissionLevel("triage");
                        } else {
                            collaborator.setPermissionLevel("read");
                        }
                    }
                    
                    // Try to link to internal user
                    userRepository.findByGithubUsername(githubUsername)
                            .ifPresent(collaborator::setUser);
                    
                    collaboratorRepository.save(collaborator);
                }
                
                log.debug("✅ Synced {} collaborators for repository: {}", collaboratorsData.size(), repository.getFullName());
            }
            
        } catch (Exception e) {
            log.error("❌ Error syncing repository collaborators: {}", repository.getFullName(), e);
        }
    }
    
    // Utility methods
    private JsonNode makeGitHubApiCall(String url, String token) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.set("Accept", "application/vnd.github.v3+json");
            headers.set("User-Agent", "EspritHub-Repository-Sync");
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                return objectMapper.readTree(response.getBody());
            }
        } catch (Exception e) {
            log.debug("GitHub API call failed for URL: {}", url);
        }
        return null;
    }
    
    private String getStringValue(JsonNode node, String field) {
        return node != null && node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }
    
    private Integer getIntValue(JsonNode node, String field) {
        return node != null && node.has(field) && !node.get(field).isNull() ? node.get(field).asInt() : null;
    }
    
    private Long getLongValue(JsonNode node, String field) {
        return node != null && node.has(field) && !node.get(field).isNull() ? node.get(field).asLong() : null;
    }
    
    private Boolean getBooleanValue(JsonNode node, String field) {
        return node != null && node.has(field) && !node.get(field).isNull() ? node.get(field).asBoolean() : false;
    }
    
    private LocalDateTime parseGitHubDate(String dateString) {
        try {
            if (dateString != null && !dateString.isBlank()) {
                return LocalDateTime.parse(dateString.replace("Z", ""), ISO_FORMATTER);
            }
        } catch (Exception e) {
            log.debug("Error parsing date: {}", dateString);
        }
        return null;
    }
    
    private String detectLanguage(String extension) {
        return switch (extension.toLowerCase()) {
            case "java" -> "Java";
            case "js", "jsx" -> "JavaScript";
            case "ts", "tsx" -> "TypeScript";
            case "py" -> "Python";
            case "html", "htm" -> "HTML";
            case "css" -> "CSS";
            case "scss", "sass" -> "SCSS";
            case "json" -> "JSON";
            case "xml" -> "XML";
            case "md" -> "Markdown";
            case "yml", "yaml" -> "YAML";
            case "sql" -> "SQL";
            case "sh" -> "Shell";
            case "dockerfile" -> "Dockerfile";
            default -> null;
        };
    }
}
