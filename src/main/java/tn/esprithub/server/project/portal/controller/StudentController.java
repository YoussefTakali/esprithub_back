package tn.esprithub.server.project.portal.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tn.esprithub.server.project.portal.dto.StudentDashboardDto;
import tn.esprithub.server.project.portal.dto.StudentDeadlineDto;
import tn.esprithub.server.project.portal.dto.StudentGroupDto;
import tn.esprithub.server.project.portal.dto.StudentNotificationDto;
import tn.esprithub.server.project.portal.dto.StudentProjectDto;
import tn.esprithub.server.project.portal.dto.StudentSubmissionDto;
import tn.esprithub.server.project.portal.dto.StudentTaskDto;
import tn.esprithub.server.security.service.AuthenticatedUserService;
import tn.esprithub.server.project.portal.service.StudentService;

@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class StudentController {

    private final StudentService studentService;
    private final AuthenticatedUserService authenticatedUserService;

    @GetMapping("/dashboard")
    public ResponseEntity<StudentDashboardDto> getDashboard(Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("Fetching dashboard data for student: {}", studentEmail);
        StudentDashboardDto dashboard = studentService.getStudentDashboard(studentEmail);
        return ResponseEntity.ok(dashboard);
    }

    @GetMapping("/tasks")
    public ResponseEntity<Page<StudentTaskDto>> getTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("Fetching tasks for student: {} (page: {}, size: {}, status: {}, search: {})",
            studentEmail, page, size, status, search);

        Pageable pageable = Pageable.ofSize(size).withPage(page);
        Page<StudentTaskDto> tasks = studentService.getStudentTasks(studentEmail, pageable, status, search);
        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<StudentTaskDto> getTaskDetails(
            @PathVariable UUID taskId,
            Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("Fetching task details for task: {} by student: {}", taskId, studentEmail);
        StudentTaskDto task = studentService.getTaskDetails(taskId, studentEmail);
        return ResponseEntity.ok(task);
    }

    @PostMapping("/tasks/{taskId}/submit")
    public ResponseEntity<Map<String, String>> submitTask(
            @PathVariable UUID taskId,
            @RequestBody(required = false) Map<String, String> submissionData,
            Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("Submitting task: {} by student: {}", taskId, studentEmail);

        try {
            String notes = submissionData != null ? submissionData.get("notes") : "";
            studentService.submitTask(taskId, studentEmail, notes);
            return ResponseEntity.ok(Map.of("message", "Task submitted successfully"));
        } catch (Exception e) {
            log.error("Error submitting task: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to submit task: " + e.getMessage()));
        }
    }

    @GetMapping("/groups")
    public ResponseEntity<List<StudentGroupDto>> getGroups(Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("Fetching groups for student: {}", studentEmail);
        List<StudentGroupDto> groups = studentService.getStudentGroups(studentEmail);
        return ResponseEntity.ok(groups);
    }

    @GetMapping("/groups/{groupId}")
    public ResponseEntity<StudentGroupDto> getGroupDetails(
            @PathVariable UUID groupId,
            Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("Fetching group details for group: {} by student: {}", groupId, studentEmail);
        StudentGroupDto group = studentService.getGroupDetails(groupId, studentEmail);
        return ResponseEntity.ok(group);
    }

    @GetMapping("/groups/{groupId}/repositories")
    public ResponseEntity<List<Map<String, Object>>> getGroupRepositories(
            @PathVariable UUID groupId,
            Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("Fetching repositories for group: {} by student: {}", groupId, studentEmail);
        try {
            List<Map<String, Object>> repositories = studentService.getGroupRepositories(groupId, studentEmail);
            return ResponseEntity.ok(repositories);
        } catch (Exception e) {
            log.error("Error fetching group repositories: {}", e.getMessage());
            return ResponseEntity.badRequest().body(List.of(Map.of("error", "Failed to fetch group repositories: " + e.getMessage())));
        }
    }

    @GetMapping("/repositories/{repositoryId}/commits")
    public ResponseEntity<Map<String, Object>> getRepositoryCommits(
            @PathVariable String repositoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "main") String branch,
            Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("🔍 Student {} requesting commits for repository: {} (page: {}, size: {})",
            studentEmail, repositoryId, page, size);
        log.info("🔐 Authentication details - Name: {}, Authorities: {}",
            studentEmail, authentication.getAuthorities());

        try {
            Map<String, Object> result = studentService.getRepositoryCommits(repositoryId, studentEmail, page, size, branch);
            log.info("✅ Successfully returning {} commits for repository {}",
                    result.get("totalCommits"), repositoryId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("❌ Error fetching repository commits for {}: {}", repositoryId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to fetch repository commits: " + e.getMessage()));
        }
    }

    @GetMapping("/repositories/{repositoryId}/latest-commit")
    public ResponseEntity<Map<String, String>> getLatestCommitHash(
            @PathVariable String repositoryId,
            Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("🔍 Student {} requesting latest commit hash for repository: {}",
            studentEmail, repositoryId);

        try {
            String latestHash = studentService.getLatestCommitHash(repositoryId, studentEmail);
            log.info("✅ Latest commit hash for repository {}: {}", repositoryId, latestHash);
            return ResponseEntity.ok(Map.of(
                "repositoryId", repositoryId,
                "latestCommitHash", latestHash,
                "message", "Latest commit hash retrieved successfully"
            ));
        } catch (Exception e) {
            log.error("❌ Error fetching latest commit hash for {}: {}", repositoryId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to fetch latest commit hash: " + e.getMessage()));
        }
    }

    @GetMapping("/projects")
    public ResponseEntity<List<StudentProjectDto>> getProjects(Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("Fetching projects for student: {}", studentEmail);
        List<StudentProjectDto> projects = studentService.getStudentProjects(studentEmail);
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/projects/{projectId}")
    public ResponseEntity<StudentProjectDto> getProjectDetails(
            @PathVariable UUID projectId,
            Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("Fetching project details for project: {} by student: {}", projectId, studentEmail);
        StudentProjectDto project = studentService.getProjectDetails(projectId, studentEmail);
        return ResponseEntity.ok(project);
    }

    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> getProfile(Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("Fetching profile for student: {}", studentEmail);
        Map<String, Object> profile = studentService.getStudentProfile(studentEmail);
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/notifications")
    public ResponseEntity<List<StudentNotificationDto>> getNotifications(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("Fetching notifications for student: {} (unread only: {})", studentEmail, unreadOnly);
        List<StudentNotificationDto> notifications = studentService.getNotifications(studentEmail, unreadOnly);
        return ResponseEntity.ok(notifications);
    }

    @PostMapping("/notifications/{notificationId}/read")
    public ResponseEntity<Map<String, String>> markNotificationAsRead(
            @PathVariable UUID notificationId,
            Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("Marking notification as read: {} by student: {}", notificationId, studentEmail);

        try {
            studentService.markNotificationAsRead(notificationId, studentEmail);
            return ResponseEntity.ok(Map.of("message", "Notification marked as read"));
        } catch (Exception e) {
            log.error("Error marking notification as read: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to mark notification as read"));
        }
    }

    @GetMapping("/deadlines")
    public ResponseEntity<List<StudentDeadlineDto>> getUpcomingDeadlines(
            @RequestParam(defaultValue = "7") int days,
            Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("Fetching upcoming deadlines for student: {} (next {} days)", studentEmail, days);
        List<StudentDeadlineDto> deadlines = studentService.getUpcomingDeadlines(studentEmail, days);
        return ResponseEntity.ok(deadlines);
    }

    @GetMapping("/submissions")
    public ResponseEntity<Page<StudentSubmissionDto>> getSubmissions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("Fetching submissions for student: {} (page: {}, size: {})", studentEmail, page, size);

        Pageable pageable = Pageable.ofSize(size).withPage(page);
        Page<StudentSubmissionDto> submissions = studentService.getSubmissions(studentEmail, pageable);
        return ResponseEntity.ok(submissions);
    }

    @GetMapping("/repositories")
    public ResponseEntity<List<Map<String, Object>>> getRepositories(Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("Fetching accessible repositories for student: {}", studentEmail);
        List<Map<String, Object>> repositories = studentService.getAccessibleRepositories(studentEmail);
        return ResponseEntity.ok(repositories);
    }

    @GetMapping("/repositories/{repositoryId}")
    public ResponseEntity<Map<String, Object>> getRepositoryById(
            @PathVariable String repositoryId,
            Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("Fetching repository by ID: {} for student: {}", repositoryId, studentEmail);
        Map<String, Object> repository = studentService.getRepositoryDetails(repositoryId, studentEmail);
        if (repository == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(repository);
    }

    @GetMapping("/repositories/{repositoryId}/details")
    public ResponseEntity<Map<String, Object>> getRepositoryDetails(
            @PathVariable String repositoryId,
            Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("Fetching detailed repository information for repository: {} by student: {}", repositoryId, studentEmail);
        Map<String, Object> repositoryDetails = studentService.getRepositoryDetails(repositoryId, studentEmail);
        return ResponseEntity.ok(repositoryDetails);
    }

    @GetMapping("/repositories/{repositoryId}/github-details")
    public ResponseEntity<Map<String, Object>> getGitHubRepositoryDetails(
            @PathVariable String repositoryId,
            Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("Fetching GitHub repository details for repository: {} by student: {}", repositoryId, studentEmail);

        try {
            Map<String, Object> githubDetails = studentService.getRepositoryDetails(repositoryId, studentEmail);
            return ResponseEntity.ok(githubDetails);
        } catch (Exception e) {
            log.error("Error fetching GitHub repository details: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to fetch GitHub repository details: " + e.getMessage()));
        }
    }

    @GetMapping("/github/{owner}/{repo}")
    public ResponseEntity<Map<String, Object>> getGitHubRepositoryByFullName(
            @PathVariable String owner,
            @PathVariable String repo,
            Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("Fetching GitHub repository details for {}/{} by student: {}", owner, repo, studentEmail);

        try {
            Map<String, Object> githubDetails = studentService.getGitHubRepositoryByFullName(owner, repo, studentEmail);
            return ResponseEntity.ok(githubDetails);
        } catch (Exception e) {
            log.error("Error fetching GitHub repository details: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to fetch GitHub repository details: " + e.getMessage()));
        }
    }

    @GetMapping("/github/{owner}/{repo}/files")
    public ResponseEntity<List<Map<String, Object>>> getRepositoryFiles(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam(value = "path", defaultValue = "") String path,
            @RequestParam(value = "branch", defaultValue = "main") String branch,
            Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("Fetching files for repository {}/{} at path: {} on branch: {} by student: {}",
            owner, repo, path, branch, studentEmail);

        try {
            List<Map<String, Object>> files = studentService.getRepositoryFiles(owner, repo, path, branch, studentEmail);
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            log.error("Error fetching repository files: {}", e.getMessage());
            return ResponseEntity.badRequest().body(List.of(Map.of("error", "Failed to fetch repository files: " + e.getMessage())));
        }
    }

    @GetMapping("/github/{owner}/{repo}/file-content")
    public ResponseEntity<Map<String, Object>> getFileContent(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam String path,
            @RequestParam(value = "branch", defaultValue = "main") String branch,
            Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("Fetching file content for {}/{} at path: {} on branch: {} by student: {}",
            owner, repo, path, branch, studentEmail);

        try {
            Map<String, Object> fileContent = studentService.getFileContent(owner, repo, path, branch, studentEmail);
            return ResponseEntity.ok(fileContent);
        } catch (Exception e) {
            log.error("Error fetching file content: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to fetch file content: " + e.getMessage()));
        }
    }

    @GetMapping("/github/{owner}/{repo}/commits")
    public ResponseEntity<List<Map<String, Object>>> getRepositoryCommits(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam(value = "branch", defaultValue = "main") String branch,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "per_page", defaultValue = "30") int perPage,
            Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("Fetching commits for repository {}/{} on branch: {} (page: {}, per_page: {}) by student: {}",
            owner, repo, branch, page, perPage, studentEmail);

        try {
            List<Map<String, Object>> commits = studentService.getRepositoryCommits(owner, repo, branch, page, perPage, studentEmail);
            return ResponseEntity.ok(commits);
        } catch (Exception e) {
            log.error("Error fetching repository commits: {}", e.getMessage());
            return ResponseEntity.badRequest().body(List.of(Map.of("error", "Failed to fetch repository commits: " + e.getMessage())));
        }
    }

    @GetMapping("/github/{owner}/{repo}/commits/{sha}")
    public ResponseEntity<Map<String, Object>> getCommitDetails(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable String sha,
            Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("Fetching commit details for {}/{}/commits/{} by student: {}",
            owner, repo, sha, studentEmail);

        try {
            Map<String, Object> commitDetails = studentService.getCommitDetails(owner, repo, sha, studentEmail);
            return ResponseEntity.ok(commitDetails);
        } catch (Exception e) {
            log.error("Error fetching commit details: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to fetch commit details: " + e.getMessage()));
        }
    }

    @GetMapping("/github/{owner}/{repo}/branches")
    public ResponseEntity<List<Map<String, Object>>> getRepositoryBranches(
            @PathVariable String owner,
            @PathVariable String repo,
            Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("Fetching branches for repository {}/{} by student: {}", owner, repo, studentEmail);

        try {
            List<Map<String, Object>> branches = studentService.getRepositoryBranches(owner, repo, studentEmail);
            return ResponseEntity.ok(branches);
        } catch (Exception e) {
            log.error("Error fetching repository branches: {}", e.getMessage());
            return ResponseEntity.badRequest().body(List.of(Map.of("error", "Failed to fetch repository branches: " + e.getMessage())));
        }
    }

    @PostMapping("/github/{owner}/{repo}/files")
    public ResponseEntity<Map<String, Object>> createFile(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestBody Map<String, Object> fileData,
            Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("Creating file in repository {}/{} by student: {}", owner, repo, studentEmail);

        try {
            String path = (String) fileData.get("path");
            String content = (String) fileData.get("content");
            String message = (String) fileData.get("message");
            String branch = (String) fileData.getOrDefault("branch", "main");

            Map<String, Object> result = studentService.createFile(owner, repo, path, content, message, branch, studentEmail);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error creating file: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to create file: " + e.getMessage()));
        }
    }

    @PutMapping("/github/{owner}/{repo}/files")
    public ResponseEntity<Map<String, Object>> updateFile(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestBody Map<String, Object> fileData,
            Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("Updating file in repository {}/{} by student: {}", owner, repo, studentEmail);

        try {
            String path = (String) fileData.get("path");
            String content = (String) fileData.get("content");
            String message = (String) fileData.get("message");
            String sha = (String) fileData.get("sha");
            String branch = (String) fileData.getOrDefault("branch", "main");

            Map<String, Object> result = studentService.updateFile(owner, repo, path, content, message, sha, branch, studentEmail);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error updating file: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to update file: " + e.getMessage()));
        }
    }

    @DeleteMapping("/github/{owner}/{repo}/files")
    public ResponseEntity<Map<String, Object>> deleteFile(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam String path,
            @RequestParam String message,
            @RequestParam String sha,
            @RequestParam(value = "branch", defaultValue = "main") String branch,
            Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("Deleting file {} from repository {}/{} by student: {}", path, owner, repo, studentEmail);

        try {
            Map<String, Object> result = studentService.deleteFile(owner, repo, path, message, sha, branch, studentEmail);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error deleting file: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to delete file: " + e.getMessage()));
        }
    }

    @PostMapping("/github/{owner}/{repo}/branches")
    public ResponseEntity<Map<String, Object>> createBranch(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestBody Map<String, Object> branchData,
            Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("Creating branch in repository {}/{} by student: {}", owner, repo, studentEmail);

        try {
            String branchName = (String) branchData.get("name");
            String fromBranch = (String) branchData.getOrDefault("from", "main");

            Map<String, Object> result = studentService.createBranch(owner, repo, branchName, fromBranch, studentEmail);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error creating branch: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to create branch: " + e.getMessage()));
        }
    }

    @GetMapping("/github/{owner}/{repo}/contributors")
    public ResponseEntity<List<Map<String, Object>>> getRepositoryContributors(
            @PathVariable String owner,
            @PathVariable String repo,
            Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("Fetching contributors for repository {}/{} by student: {}", owner, repo, studentEmail);

        try {
            List<Map<String, Object>> contributors = studentService.getRepositoryContributors(owner, repo, studentEmail);
            return ResponseEntity.ok(contributors);
        } catch (Exception e) {
            log.error("Error fetching repository contributors: {}", e.getMessage());
            return ResponseEntity.badRequest().body(List.of(Map.of("error", "Failed to fetch repository contributors: " + e.getMessage())));
        }
    }

    @GetMapping("/github-repositories")
    public ResponseEntity<List<Map<String, Object>>> getStudentGitHubRepositories(Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("Fetching all GitHub repositories for student: {}", studentEmail);

        try {
            List<Map<String, Object>> repositories = studentService.getStudentGitHubRepositories(studentEmail);
            return ResponseEntity.ok(repositories);
        } catch (Exception e) {
            log.error("Error fetching student GitHub repositories: {}", e.getMessage());
            return ResponseEntity.badRequest().body(List.of(Map.of("error", "Failed to fetch GitHub repositories: " + e.getMessage())));
        }
    }

    @GetMapping("/schedule")
    public ResponseEntity<Map<String, Object>> getSchedule(Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("Fetching schedule for student: {}", studentEmail);
        List<Map<String, Object>> weeklySchedule = studentService.getWeeklySchedule(studentEmail);
        Map<String, Object> scheduleResponse = Map.of(
                "weeklySchedule", weeklySchedule,
                "upcomingEvents", studentService.getUpcomingDeadlines(studentEmail, 7),
                "deadlines", studentService.getUpcomingDeadlines(studentEmail, 14)
        );
        return ResponseEntity.ok(scheduleResponse);
    }

    @GetMapping("/debug/github")
    public ResponseEntity<Map<String, Object>> debugGitHubAccess(Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("Debug: Testing GitHub access for student: {}", studentEmail);

        try {
            Map<String, Object> debugInfo = studentService.debugGitHubAccess(studentEmail);
            return ResponseEntity.ok(debugInfo);
        } catch (Exception e) {
            log.error("Error in GitHub debug: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "GitHub debug failed: " + e.getMessage()));
        }
    }

    @GetMapping("/github/{owner}/{repo}/overview")
    public ResponseEntity<Map<String, Object>> getRepositoryOverview(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam(value = "branch", required = false) String branch,
            Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("Fetching comprehensive overview for repository {}/{} by student: {}", owner, repo, studentEmail);

        try {
            Map<String, Object> overview = studentService.getRepositoryOverview(owner, repo, branch, studentEmail);
            return ResponseEntity.ok(overview);
        } catch (Exception e) {
            log.error("Error fetching repository overview: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to fetch repository overview: " + e.getMessage()));
        }
    }

    @GetMapping("/github/{owner}/{repo}/file-tree")
    public ResponseEntity<Map<String, Object>> getRepositoryFileTree(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam(value = "branch", required = false) String branch,
            Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("Fetching file tree for repository {}/{} on branch: {} by student: {}", owner, repo, branch, studentEmail);

        try {
            Map<String, Object> fileTree = studentService.getRepositoryFileTree(owner, repo, branch, studentEmail);
            return ResponseEntity.ok(fileTree);
        } catch (Exception e) {
            log.error("Error fetching repository file tree: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to fetch repository file tree: " + e.getMessage()));
        }
    }

    @PostMapping("/github/{owner}/{repo}/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam("file") MultipartFile file,
            @RequestParam("path") String path,
            @RequestParam("message") String message,
            @RequestParam(value = "branch", required = false) String branch,
            Authentication authentication) {
        String studentEmail = getStudentEmail(authentication);
        log.info("Uploading file to repository {}/{} at path: {} by student: {}", owner, repo, path, studentEmail);

        try {
            byte[] fileContent = file.getBytes();
            Map<String, Object> result = studentService.uploadFile(owner, repo, path, fileContent, message, branch, studentEmail);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error uploading file: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to upload file: " + e.getMessage()));
        }
    }

    @PostMapping("/github/{owner}/{repo}/upload-multiple")
    public ResponseEntity<Map<String, Object>> uploadMultipleFiles(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("basePath") String basePath,
            @RequestParam("message") String message,
            @RequestParam(value = "branch", required = false) String branch,
            Authentication authentication) {
            String studentEmail = getStudentEmail(authentication);
            log.info("Uploading {} files to repository {}/{} in path: {} by student: {}",
                files.length, owner, repo, basePath, studentEmail);

        try {
            Map<String, byte[]> fileMap = new HashMap<>();
            for (MultipartFile file : files) {
                if (!file.isEmpty()) {
                    fileMap.put(file.getOriginalFilename(), file.getBytes());
                }
            }

            Map<String, Object> result = studentService.uploadMultipleFiles(owner, repo, basePath, fileMap, message, branch, studentEmail);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error uploading multiple files: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to upload files: " + e.getMessage()));
        }
    }

    private String getStudentEmail(Authentication authentication) {
        return authenticatedUserService.getUser(authentication).getEmail();
    }
}
