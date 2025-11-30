package tn.esprithub.server.github.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tn.esprithub.server.common.exception.BusinessException;
import tn.esprithub.server.github.dto.GitHubRepositoryDetailsDto;
import tn.esprithub.server.repository.repository.RepositoryEntityRepository;
import tn.esprithub.server.user.entity.User;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubRepositoryService {
    
    private static final String GITHUB_API_BASE = "https://api.github.com";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RepositoryEntityRepository repositoryRepository;

    public GitHubRepositoryDetailsDto getRepositoryDetails(String owner, String repo, User user) {
        if (user.getGithubToken() == null || user.getGithubToken().isBlank()) {
            throw new BusinessException("GitHub token not found for user");
        }

        log.info("Fetching GitHub repository details for {}/{} using token for user: {} (role: {})", 
            owner, repo, user.getEmail(), user.getRole());

        try {
            // Get basic repository information
            JsonNode repoData = fetchRepositoryData(owner, repo, user.getGithubToken());
            
            // Get additional data in parallel
            List<GitHubRepositoryDetailsDto.BranchDto> branches = fetchBranches(owner, repo, user.getGithubToken());
            List<GitHubRepositoryDetailsDto.CommitDto> commits = fetchRecentCommits(owner, repo, user.getGithubToken());
            List<GitHubRepositoryDetailsDto.ContributorDto> contributors = fetchContributors(owner, repo, user.getGithubToken());
            Map<String, Integer> languages = fetchLanguages(owner, repo, user.getGithubToken());
            List<GitHubRepositoryDetailsDto.ReleaseDto> releases = fetchReleases(owner, repo, user.getGithubToken());
            List<GitHubRepositoryDetailsDto.FileDto> files = fetchFiles(owner, repo, user.getGithubToken());

            return buildRepositoryDetailsDto(repoData, branches, commits, contributors, languages, releases, files);
            
        } catch (Exception e) {
            log.error("Error fetching repository details for {}/{}: {}", owner, repo, e.getMessage());
            throw new BusinessException("Failed to fetch repository details: " + e.getMessage());
        }
    }

    public GitHubRepositoryDetailsDto getRepositoryDetailsByRepositoryId(String repositoryId, User user) {
        if (user.getGithubToken() == null || user.getGithubToken().isBlank()) {
            throw new BusinessException("GitHub token not found for user");
        }

        try {
            // First, find the repository entity to get owner/repo information
            tn.esprithub.server.repository.entity.Repository repository = repositoryRepository.findById(UUID.fromString(repositoryId))
                    .orElseThrow(() -> new BusinessException("Repository not found"));
            
            // Extract owner and repo name from fullName (format: "owner/repo")
            String[] parts = repository.getFullName().split("/");
            if (parts.length != 2) {
                throw new BusinessException("Invalid repository fullName format: " + repository.getFullName());
            }
            
            String owner = parts[0];
            String repo = parts[1];
            
            log.info("Fetching GitHub data for repository: {}/{}", owner, repo);
            
            // Use the existing method to get GitHub data
            return getRepositoryDetails(owner, repo, user);
            
        } catch (Exception e) {
            log.error("Error fetching repository details for repository ID {}: {}", repositoryId, e.getMessage());
            throw new BusinessException("Failed to fetch repository details: " + e.getMessage());
        }
    }

    private JsonNode fetchRepositoryData(String owner, String repo, String token) {
        String url = GITHUB_API_BASE + "/repos/" + owner + "/" + repo;
        log.info("Making GitHub API call to: {}", url);
        log.info("Using token starting with: {}...", token != null && token.length() > 10 ? token.substring(0, 10) : "null");
        
        HttpHeaders headers = createHeaders(token);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully fetched repository data for {}/{}", owner, repo);
                return objectMapper.readTree(response.getBody());
            } else {
                log.error("GitHub API returned status {} for repository {}/{}", response.getStatusCode(), owner, repo);
                throw new BusinessException("GitHub API returned status: " + response.getStatusCode());
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("GitHub API HTTP error for {}/{}: Status={}, Response={}", 
                owner, repo, e.getStatusCode(), e.getResponseBodyAsString());
            
            if (e.getStatusCode().value() == 404) {
                log.warn("Repository {}/{} not found on GitHub (404)", owner, repo);
                throw new BusinessException("Repository " + owner + "/" + repo + " not found on GitHub");
            } else if (e.getStatusCode().value() == 403) {
                log.warn("Access denied to repository {}/{} (403) - check token permissions", owner, repo);
                throw new BusinessException("Access denied to repository " + owner + "/" + repo + " - insufficient permissions");
            } else if (e.getStatusCode().value() == 401) {
                log.error("Invalid GitHub token for accessing repository {}/{} (401)", owner, repo);
                throw new BusinessException("Invalid GitHub token - please reconnect your GitHub account");
            } else {
                log.error("GitHub API error {} for repository {}/{}: {}", e.getStatusCode(), owner, repo, e.getMessage());
                throw new BusinessException("GitHub API error: " + e.getStatusCode() + " - " + e.getMessage());
            }
        } catch (Exception e) {
            log.error("Error parsing repository data for {}/{}: {}", owner, repo, e.getMessage());
            throw new BusinessException("Failed to parse repository data: " + e.getMessage());
        }
    }

    private List<GitHubRepositoryDetailsDto.BranchDto> fetchBranches(String owner, String repo, String token) {
        try {
            String url = GITHUB_API_BASE + "/repos/" + owner + "/" + repo + "/branches";
            HttpHeaders headers = createHeaders(token);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode branches = objectMapper.readTree(response.getBody());
            
            List<GitHubRepositoryDetailsDto.BranchDto> branchList = new ArrayList<>();
            for (JsonNode branch : branches) {
                GitHubRepositoryDetailsDto.CommitDto lastCommit = null;
                if (branch.has("commit")) {
                    JsonNode commitNode = branch.get("commit");
                    lastCommit = GitHubRepositoryDetailsDto.CommitDto.builder()
                            .sha(commitNode.get("sha").asText())
                            .build();
                }
                
                branchList.add(GitHubRepositoryDetailsDto.BranchDto.builder()
                        .name(branch.get("name").asText())
                        .sha(branch.get("commit").get("sha").asText())
                        .isProtected(branch.has("protected") ? branch.get("protected").asBoolean() : false)
                        .lastCommit(lastCommit)
                        .build());
            }
            return branchList;
        } catch (Exception e) {
            log.warn("Failed to fetch branches for {}/{}: {}", owner, repo, e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<GitHubRepositoryDetailsDto.CommitDto> fetchRecentCommits(String owner, String repo, String token) {
        try {
            String url = GITHUB_API_BASE + "/repos/" + owner + "/" + repo + "/commits?per_page=10";
            HttpHeaders headers = createHeaders(token);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode commits = objectMapper.readTree(response.getBody());
            
            List<GitHubRepositoryDetailsDto.CommitDto> commitList = new ArrayList<>();
            for (JsonNode commit : commits) {
                JsonNode commitData = commit.get("commit");
                JsonNode author = commitData.get("author");
                JsonNode committer = commit.has("author") ? commit.get("author") : null;
                
                commitList.add(GitHubRepositoryDetailsDto.CommitDto.builder()
                        .sha(commit.get("sha").asText())
                        .message(commitData.get("message").asText())
                        .authorName(author.get("name").asText())
                        .authorEmail(author.get("email").asText())
                        .authorAvatarUrl(committer != null ? committer.get("avatar_url").asText() : null)
                        .date(parseGitHubDate(author.get("date").asText()))
                        .htmlUrl(commit.get("html_url").asText())
                        .build());
            }
            return commitList;
        } catch (Exception e) {
            log.warn("Failed to fetch commits for {}/{}: {}", owner, repo, e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<GitHubRepositoryDetailsDto.ContributorDto> fetchContributors(String owner, String repo, String token) {
        try {
            String url = GITHUB_API_BASE + "/repos/" + owner + "/" + repo + "/contributors?per_page=10";
            HttpHeaders headers = createHeaders(token);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode contributors = objectMapper.readTree(response.getBody());
            
            List<GitHubRepositoryDetailsDto.ContributorDto> contributorList = new ArrayList<>();
            for (JsonNode contributor : contributors) {
                contributorList.add(GitHubRepositoryDetailsDto.ContributorDto.builder()
                        .login(contributor.get("login").asText())
                        .name(contributor.has("name") ? contributor.get("name").asText() : contributor.get("login").asText())
                        .avatarUrl(contributor.get("avatar_url").asText())
                        .contributions(contributor.get("contributions").asInt())
                        .htmlUrl(contributor.get("html_url").asText())
                        .build());
            }
            return contributorList;
        } catch (Exception e) {
            log.warn("Failed to fetch contributors for {}/{}: {}", owner, repo, e.getMessage());
            return new ArrayList<>();
        }
    }

    private Map<String, Integer> fetchLanguages(String owner, String repo, String token) {
        try {
            String url = GITHUB_API_BASE + "/repos/" + owner + "/" + repo + "/languages";
            HttpHeaders headers = createHeaders(token);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode languages = objectMapper.readTree(response.getBody());
            
            Map<String, Integer> languageMap = new HashMap<>();
            languages.fields().forEachRemaining(entry -> 
                languageMap.put(entry.getKey(), entry.getValue().asInt()));
            
            return languageMap;
        } catch (Exception e) {
            log.warn("Failed to fetch languages for {}/{}: {}", owner, repo, e.getMessage());
            return new HashMap<>();
        }
    }

    private List<GitHubRepositoryDetailsDto.ReleaseDto> fetchReleases(String owner, String repo, String token) {
        try {
            String url = GITHUB_API_BASE + "/repos/" + owner + "/" + repo + "/releases?per_page=5";
            HttpHeaders headers = createHeaders(token);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode releases = objectMapper.readTree(response.getBody());
            
            List<GitHubRepositoryDetailsDto.ReleaseDto> releaseList = new ArrayList<>();
            for (JsonNode release : releases) {
                releaseList.add(GitHubRepositoryDetailsDto.ReleaseDto.builder()
                        .tagName(release.get("tag_name").asText())
                        .name(release.has("name") && !release.get("name").isNull() ? 
                               release.get("name").asText() : release.get("tag_name").asText())
                        .body(release.has("body") && !release.get("body").isNull() ? 
                              release.get("body").asText() : "")
                        .isDraft(release.get("draft").asBoolean())
                        .isPrerelease(release.get("prerelease").asBoolean())
                        .publishedAt(release.has("published_at") && !release.get("published_at").isNull() ? 
                                    parseGitHubDate(release.get("published_at").asText()) : null)
                        .htmlUrl(release.get("html_url").asText())
                        .build());
            }
            return releaseList;
        } catch (Exception e) {
            log.warn("Failed to fetch releases for {}/{}: {}", owner, repo, e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<GitHubRepositoryDetailsDto.FileDto> fetchFiles(String owner, String repo, String token) {
        try {
            String url = GITHUB_API_BASE + "/repos/" + owner + "/" + repo + "/contents";
            HttpHeaders headers = createHeaders(token);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode files = objectMapper.readTree(response.getBody());
            
            List<GitHubRepositoryDetailsDto.FileDto> fileList = new ArrayList<>();
            for (JsonNode file : files) {
                GitHubRepositoryDetailsDto.FileDto fileDto = GitHubRepositoryDetailsDto.FileDto.builder()
                        .name(file.get("name").asText())
                        .path(file.get("path").asText())
                        .type(file.get("type").asText())
                        .sha(file.get("sha").asText())
                        .size(file.has("size") ? file.get("size").asInt() : 0)
                        .downloadUrl(file.has("download_url") && !file.get("download_url").isNull() ? 
                                    file.get("download_url").asText() : null)
                        .htmlUrl(file.get("html_url").asText())
                        .build();
                fileList.add(fileDto);
            }
            return fileList;
        } catch (Exception e) {
            log.warn("Failed to fetch files for {}/{}: {}", owner, repo, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Fetch file content from GitHub by owner/repo/branch/path
     */
    public Map<String, Object> fetchFileContent(String owner, String repo, String branch, String path, String token) {
        String url = GITHUB_API_BASE + "/repos/" + owner + "/" + repo + "/contents/" + path + "?ref=" + branch;
        HttpHeaders headers = createHeaders(token);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode fileNode = objectMapper.readTree(response.getBody());
                Map<String, Object> result = new HashMap<>();
                result.put("name", fileNode.get("name").asText());
                result.put("path", fileNode.get("path").asText());
                result.put("content", fileNode.has("content") && !fileNode.get("content").isNull() ? fileNode.get("content").asText() : null);
                result.put("encoding", fileNode.has("encoding") && !fileNode.get("encoding").isNull() ? fileNode.get("encoding").asText() : null);
                result.put("sha", fileNode.has("sha") && !fileNode.get("sha").isNull() ? fileNode.get("sha").asText() : null);
                result.put("size", fileNode.has("size") && !fileNode.get("size").isNull() ? fileNode.get("size").asInt() : null);
                result.put("url", fileNode.has("url") && !fileNode.get("url").isNull() ? fileNode.get("url").asText() : null);
                return result;
            }
        } catch (Exception e) {
            log.warn("Failed to fetch file from GitHub: {}", e.getMessage());
        }
        return null;
    }

    private GitHubRepositoryDetailsDto buildRepositoryDetailsDto(
            JsonNode repoData,
            List<GitHubRepositoryDetailsDto.BranchDto> branches,
            List<GitHubRepositoryDetailsDto.CommitDto> commits,
            List<GitHubRepositoryDetailsDto.ContributorDto> contributors,
            Map<String, Integer> languages,
            List<GitHubRepositoryDetailsDto.ReleaseDto> releases,
            List<GitHubRepositoryDetailsDto.FileDto> files) {
        
        JsonNode ownerNode = repoData.get("owner");
        GitHubRepositoryDetailsDto.OwnerDto owner = GitHubRepositoryDetailsDto.OwnerDto.builder()
                .login(ownerNode.get("login").asText())
                .name(ownerNode.has("name") && !ownerNode.get("name").isNull() ? 
                      ownerNode.get("name").asText() : ownerNode.get("login").asText())
                .avatarUrl(ownerNode.get("avatar_url").asText())
                .type(ownerNode.get("type").asText())
                .htmlUrl(ownerNode.get("html_url").asText())
                .build();

        return GitHubRepositoryDetailsDto.builder()
                .id(repoData.get("id").asText())
                .name(repoData.get("name").asText())
                .fullName(repoData.get("full_name").asText())
                .description(repoData.has("description") && !repoData.get("description").isNull() ? 
                            repoData.get("description").asText() : null)
                .url(repoData.get("git_url").asText())
                .htmlUrl(repoData.get("html_url").asText())
                .cloneUrl(repoData.get("clone_url").asText())
                .sshUrl(repoData.get("ssh_url").asText())
                .gitUrl(repoData.get("git_url").asText())
                .isPrivate(repoData.get("private").asBoolean())
                .defaultBranch(repoData.get("default_branch").asText())
                .size(repoData.get("size").asInt())
                .language(repoData.has("language") && !repoData.get("language").isNull() ? 
                         repoData.get("language").asText() : null)
                .stargazersCount(repoData.get("stargazers_count").asInt())
                .watchersCount(repoData.get("watchers_count").asInt())
                .forksCount(repoData.get("forks_count").asInt())
                .openIssuesCount(repoData.get("open_issues_count").asInt())
                .createdAt(parseGitHubDate(repoData.get("created_at").asText()))
                .updatedAt(parseGitHubDate(repoData.get("updated_at").asText()))
                .pushedAt(repoData.has("pushed_at") && !repoData.get("pushed_at").isNull() ? 
                         parseGitHubDate(repoData.get("pushed_at").asText()) : null)
                .owner(owner)
                .branches(branches)
                .recentCommits(commits)
                .contributors(contributors)
                .languages(languages)
                .releases(releases)
                .files(files)
                .build();
    }

    private HttpHeaders createHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token);
        }
        headers.set("Accept", "application/vnd.github.v3+json");
        return headers;
    }

    private LocalDateTime parseGitHubDate(String dateString) {
        try {
            return LocalDateTime.parse(dateString, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            log.warn("Failed to parse date: {}", dateString);
            return null;
        }
    }
}
