package tn.esprithub.server.repository.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.multipart.MultipartFile;
import tn.esprithub.server.repository.dto.RepositoryDto;
import tn.esprithub.server.repository.dto.RepositoryStatsDto;
import tn.esprithub.server.repository.dto.FileUploadDto;
import tn.esprithub.server.repository.service.RepositoryService;
import tn.esprithub.server.user.service.UserService;
import tn.esprithub.server.user.dto.UserSummaryDto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/repositories")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class RepositoryController {

    private final RepositoryService repositoryService;
    private final UserService userService;

    // OPTIONS handlers for CORS preflight requests
    @RequestMapping(value = "/**/stats", method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> optionsStats() {
        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "/**/files", method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> optionsFiles() {
        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "/**/branches", method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> optionsBranches() {
        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "/**/collaborators", method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> optionsCollaborators() {
        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "/**/commits", method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> optionsCommits() {
        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "/**", method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> optionsRepository() {
        return ResponseEntity.ok().build();
    }

    @GetMapping("/teacher")
    public ResponseEntity<List<RepositoryDto>> getTeacherRepositories(Authentication authentication) {
        log.info("Fetching repositories for teacher: {}", authentication.getName());
        List<RepositoryDto> repositories = repositoryService.getTeacherRepositories(authentication.getName());
        return ResponseEntity.ok(repositories);
    }

    @GetMapping("/users/search")
    public ResponseEntity<List<UserSummaryDto>> searchUsers(
            @RequestParam String q,
            Authentication authentication) {
        log.info("Searching users with query: {} by teacher: {}", q, authentication.getName());
        List<UserSummaryDto> users = userService.searchUsers(q);
        return ResponseEntity.ok(users);
    }

    @PostMapping
    public ResponseEntity<RepositoryDto> createRepository(
            @RequestBody Map<String, Object> repositoryData,
            Authentication authentication) {
        String name = (String) repositoryData.get("name");
        String description = (String) repositoryData.get("description");
        Boolean isPrivate = (Boolean) repositoryData.getOrDefault("isPrivate", true);

        log.info("Creating repository: {} by teacher: {}", name, authentication.getName());
        RepositoryDto repository = repositoryService.createRepository(name, description, isPrivate, authentication.getName());
        return ResponseEntity.ok(repository);
    }
    // Get latest commit for a specific path
    @GetMapping("/latest-commit")
    public ResponseEntity<?> getLatestCommit(
            @RequestParam String owner,
            @RequestParam String repo,
            @RequestParam String path,
            @RequestParam(value = "branch", defaultValue = "main") String branch,
            Authentication authentication) {
        try {
            log.info("Fetching latest commit for path: {} in repo: {}/{}, branch: {} by teacher: {}",
                    path, owner, repo, branch, authentication.getName());

            Object latestCommit = repositoryService.getLatestCommit(owner, repo, path, branch, authentication.getName());
            return ResponseEntity.ok(latestCommit);
        } catch (Exception e) {
            log.error("Error fetching latest commit: {}", e.getMessage());
            return ResponseEntity.status(500).body("Error fetching commit: " + e.getMessage() +
                    " for path: " + path + " in repo: " + owner + "/" + repo + ", branch: " + branch);
        }
    }

    @GetMapping("/{owner}/{repo}/stats")
    public ResponseEntity<RepositoryStatsDto> getRepositoryStats(
            @PathVariable String owner,
            @PathVariable String repo,
            Authentication authentication) {
        String repoFullName = owner + "/" + repo;
        log.info("Fetching stats for repository: {} by teacher: {}", repoFullName, authentication.getName());
        RepositoryStatsDto stats = repositoryService.getRepositoryStats(repoFullName, authentication.getName());
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/{owner}/{repo}/upload")
    public ResponseEntity<String> uploadFile(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam("file") MultipartFile file,
            @RequestParam("path") String path,
            @RequestParam("message") String commitMessage,
            @RequestParam(value = "branch", defaultValue = "main") String branch,
            Authentication authentication) {
        String repoFullName = owner + "/" + repo;
        log.info("Uploading file to repository: {} by teacher: {}", repoFullName, authentication.getName());

        FileUploadDto uploadDto = FileUploadDto.builder()
                .file(file)
                .path(path)
                .commitMessage(commitMessage)
                .branch(branch)
                .build();

        String commitSha = repositoryService.uploadFile(repoFullName, uploadDto, authentication.getName());
        return ResponseEntity.ok(commitSha);
    }
    @GetMapping("/{owner}/{repo}/files")
    public ResponseEntity<List<Map<String, Object>>> getRepositoryFiles(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam(value = "branch", defaultValue = "main") String branch,
            Authentication authentication) {
        String repoFullName = owner + "/" + repo;
        log.info("Fetching files for repository: {} branch: {} by teacher: {}", repoFullName, branch, authentication.getName());
        List<Map<String, Object>> files = repositoryService.getRepositoryFiles(repoFullName, branch, authentication.getName());
        return ResponseEntity.ok(files);
    }

    @GetMapping("/{owner}/{repo}/branches")
    public ResponseEntity<List<String>> getRepositoryBranches(
            @PathVariable String owner,
            @PathVariable String repo,
            Authentication authentication) {
        String repoFullName = owner + "/" + repo;
        log.info("Fetching branches for repository: {} by teacher: {}", repoFullName, authentication.getName());
        List<String> branches = repositoryService.getRepositoryBranches(repoFullName, authentication.getName());
        return ResponseEntity.ok(branches);
    }

    @DeleteMapping("/{owner}/{repo}/files/{filePath}")
    public ResponseEntity<String> deleteFile(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable String filePath,
            @RequestParam("message") String commitMessage,
            @RequestParam(value = "branch", defaultValue = "main") String branch,
            Authentication authentication) {
        String repoFullName = owner + "/" + repo;
        log.info("Deleting file {} from repository: {} by teacher: {}", filePath, repoFullName, authentication.getName());
        String commitSha = repositoryService.deleteFile(repoFullName, filePath, commitMessage, branch, authentication.getName());
        return ResponseEntity.ok(commitSha);
    }

    // Create a new branch
    @PostMapping("/{owner}/{repo}/branches")
    public ResponseEntity<Map<String, String>> createBranch(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        String repoFullName = owner + "/" + repo;
        String branchName = request.get("name");
        String fromBranch = request.getOrDefault("from", "main");
        log.info("Creating branch {} from {} in repository: {} by teacher: {}", branchName, fromBranch, repoFullName, authentication.getName());

        try {
            repositoryService.createBranch(repoFullName, branchName, fromBranch, authentication.getName());
            return ResponseEntity.ok(Map.of("message", "Branch created successfully", "branch", branchName));
        } catch (Exception e) {
            log.error("Error creating branch: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to create branch: " + e.getMessage()));
        }
    }

    // Delete a branch
    @DeleteMapping("/{owner}/{repo}/branches/{branchName}")
    public ResponseEntity<Map<String, String>> deleteBranch(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable String branchName,
            Authentication authentication) {
        String repoFullName = owner + "/" + repo;
        log.info("Deleting branch {} from repository: {} by teacher: {}", branchName, repoFullName, authentication.getName());

        try {
            repositoryService.deleteBranch(repoFullName, branchName, authentication.getName());
            return ResponseEntity.ok(Map.of("message", "Branch deleted successfully"));
        } catch (Exception e) {
            log.error("Error deleting branch: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to delete branch: " + e.getMessage()));
        }
    }

    // Get repository collaborators
    @GetMapping("/{owner}/{repo}/collaborators")
    public ResponseEntity<List<Map<String, Object>>> getCollaborators(
            @PathVariable String owner,
            @PathVariable String repo,
            Authentication authentication) {
        String repoFullName = owner + "/" + repo;
        log.info("Fetching collaborators for repository: {} by teacher: {}", repoFullName, authentication.getName());

        try {
            List<Map<String, Object>> collaborators = repositoryService.getCollaborators(repoFullName, authentication.getName());
            return ResponseEntity.ok(collaborators);
        } catch (Exception e) {
            log.error("Error fetching collaborators: {}", e.getMessage());
            return ResponseEntity.badRequest().body(List.of(Map.of("error", "Failed to fetch collaborators: " + e.getMessage())));
        }
    }

    // Add collaborator
    @PostMapping("/{owner}/{repo}/collaborators")
    public ResponseEntity<Map<String, String>> addCollaborator(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        String repoFullName = owner + "/" + repo;
        String usernameOrEmail = request.get("username");
        String permission = request.getOrDefault("permission", "push");
        log.info("Adding collaborator {} with permission {} to repository: {} by teacher: {}", usernameOrEmail, permission, repoFullName, authentication.getName());

        try {
            String githubUsername = usernameOrEmail;

            // If the input looks like an email, try to find the user's GitHub username
            if (usernameOrEmail.contains("@")) {
                List<UserSummaryDto> users = userService.searchUsers(usernameOrEmail);
                UserSummaryDto user = users.stream()
                        .filter(u -> usernameOrEmail.equalsIgnoreCase(u.getEmail()))
                        .findFirst()
                        .orElse(null);

                if (user != null && user.getGithubUsername() != null && !user.getGithubUsername().trim().isEmpty()) {
                    githubUsername = user.getGithubUsername();
                    log.info("Resolved email {} to GitHub username: {}", usernameOrEmail, githubUsername);
                } else {
                    return ResponseEntity.badRequest().body(Map.of("error", "User not found or GitHub username not set for email: " + usernameOrEmail));
                }
            }

            repositoryService.addCollaborator(repoFullName, githubUsername, permission, authentication.getName());
            return ResponseEntity.ok(Map.of("message", "Collaborator added successfully"));
        } catch (Exception e) {
            log.error("Error adding collaborator: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to add collaborator: " + e.getMessage()));
        }
    }

    // Remove collaborator
    @DeleteMapping("/{owner}/{repo}/collaborators/{username}")
    public ResponseEntity<Map<String, String>> removeCollaborator(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable String username,
            Authentication authentication) {
        String repoFullName = owner + "/" + repo;
        log.info("Removing collaborator {} from repository: {} by teacher: {}", username, repoFullName, authentication.getName());

        try {
            repositoryService.removeCollaborator(repoFullName, username, authentication.getName());
            return ResponseEntity.ok(Map.of("message", "Collaborator removed successfully"));
        } catch (Exception e) {
            log.error("Error removing collaborator: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to remove collaborator: " + e.getMessage()));
        }
    }
    //remove invitations
    // Cancel pending collaborator invitation
    @DeleteMapping("/{owner}/{repo}/invitations/{username}")
    public ResponseEntity<Map<String, String>> cancelInvitation(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable String username,
            Authentication authentication) {

        String repoFullName = owner + "/" + repo;
        log.info("Cancelling invitation for {} from repository: {} by teacher: {}", username, repoFullName, authentication.getName());

        try {
            repositoryService.cancelInvitation(repoFullName, username, authentication.getName());
            return ResponseEntity.ok(Map.of("message", "Invitation cancelled successfully"));
        } catch (Exception e) {
            log.error("Error cancelling invitation: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to cancel invitation: " + e.getMessage()));
        }
    }


    // Get repository commits
    @GetMapping("/{owner}/{repo}/commits")
    public ResponseEntity<List<Map<String, Object>>> getCommits(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam(value = "branch", defaultValue = "main") String branch,
            @RequestParam(value = "page", defaultValue = "1") int page,
            Authentication authentication) {
        String repoFullName = owner + "/" + repo;
        log.info("Fetching commits for repository: {} branch: {} page: {} by teacher: {}", repoFullName, branch, page, authentication.getName());

        try {
            List<Map<String, Object>> commits = repositoryService.getCommits(repoFullName, branch, page, authentication.getName());
            return ResponseEntity.ok(commits);
        } catch (Exception e) {
            log.error("Error fetching commits: {}", e.getMessage());
            return ResponseEntity.badRequest().body(List.of(Map.of("error", "Failed to fetch commits: " + e.getMessage())));
        }
    }

    // Update repository settings
    @PatchMapping("/{owner}/{repo}")
    public ResponseEntity<Map<String, String>> updateRepository(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestBody Map<String, Object> settings,
            Authentication authentication) {
        String repoFullName = owner + "/" + repo;
        log.info("Updating repository settings for: {} by teacher: {}", repoFullName, authentication.getName());

        try {
            repositoryService.updateRepository(repoFullName, settings, authentication.getName());
            return ResponseEntity.ok(Map.of("message", "Repository updated successfully"));
        } catch (Exception e) {
            log.error("Error updating repository: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to update repository: " + e.getMessage()));
        }
    }

    // Delete repository
    @DeleteMapping("/{owner}/{repo}")
    public ResponseEntity<Map<String, String>> deleteRepository(
            @PathVariable String owner,
            @PathVariable String repo,
            Authentication authentication) {
        String repoFullName = owner + "/" + repo;
        log.info("Deleting repository: {} by teacher: {}", repoFullName, authentication.getName());

        try {
            repositoryService.deleteRepository(repoFullName, authentication.getName());
            return ResponseEntity.ok(Map.of("message", "Repository deleted successfully"));
        } catch (Exception e) {
            log.error("Error deleting repository: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to delete repository: " + e.getMessage()));
        }
    }

    // Get file content
    @GetMapping("/{owner}/{repo}/files/{filePath}/content")
    public ResponseEntity<Map<String, Object>> getFileContent(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable String filePath,
            @RequestParam(value = "branch", defaultValue = "main") String branch,
            Authentication authentication) {
        log.info("Fetching file content for {}/{} at path: {} on branch: {} by teacher: {}", owner, repo, filePath, branch, authentication.getName());
        Map<String, Object> fileContent = repositoryService.getFileContent(owner, repo, filePath, branch, authentication.getName());
        return ResponseEntity.ok(fileContent);
    }
    @GetMapping("/{owner}/{repo}/commit-count")
    public ResponseEntity<Map<String, Integer>> getCommitCount(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam(value = "branch", defaultValue = "main") String branch,
            @RequestParam(value = "path", required = false) String path,
            Authentication authentication) {
        String repoFullName = owner + "/" + repo;
        log.info("Fetching commit count for repository: {} branch: {} path: {} by teacher: {}",
                repoFullName, branch, path, authentication.getName());

        int count = repositoryService.getCommitCount(repoFullName, branch, path, authentication.getName());
        Map<String, Integer> result = new HashMap<>();
        result.put("count", count);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{owner}/{repo}/repo-languages")
    public ResponseEntity<Map<String, Object>> getRepoLanguages(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam(value = "branch", defaultValue = "main") String branch,
            Authentication authentication) {
        String repoFullName = owner + "/" + repo;
        log.info("Fetching languages for repository: {} branch: {} by teacher: {}",
                repoFullName, branch, authentication.getName());

        Map<String, Object> languages = repositoryService.getRepoLanguages(repoFullName, branch, authentication.getName());
        return ResponseEntity.ok(languages);
    }

    @GetMapping("/{owner}/{repo}/latest-repo-commit")
    public ResponseEntity<List<Object>> getLatestRepoCommit(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam(value = "branch", defaultValue = "main") String branch,
            Authentication authentication) {
        String repoFullName = owner + "/" + repo;
        log.info("Fetching latest commit for repository: {} branch: {} by teacher: {}",
                repoFullName, branch, authentication.getName());

        List<Object> commits = repositoryService.getLatestRepoCommit(repoFullName, branch, authentication.getName());
        return ResponseEntity.ok(commits);
    }

    @GetMapping("/{owner}/{repo}/branch-commits")
    public ResponseEntity<List<Object>> getBranchCommits(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam String branch,
            Authentication authentication) {
        String repoFullName = owner + "/" + repo;
        log.info("Fetching commits for repository: {} branch: {} by teacher: {}",
                repoFullName, branch, authentication.getName());

        List<Object> commits = repositoryService.getBranchCommits(repoFullName, branch, authentication.getName());
        return ResponseEntity.ok(commits);
    }

    @GetMapping("/{owner}/{repo}/list-branches")
    public ResponseEntity<List<Object>> listBranches(
            @PathVariable String owner,
            @PathVariable String repo,
            Authentication authentication) {
        String repoFullName = owner + "/" + repo;
        log.info("Fetching branches for repository: {} by teacher: {}", repoFullName, authentication.getName());

        List<Object> branches = repositoryService.listBranches(repoFullName, authentication.getName());
        return ResponseEntity.ok(branches);
    }
}
