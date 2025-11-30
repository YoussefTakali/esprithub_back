package tn.esprithub.server.project.portal.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import tn.esprithub.server.project.portal.dto.StudentDashboardDto;
import tn.esprithub.server.project.portal.dto.StudentDeadlineDto;
import tn.esprithub.server.project.portal.dto.StudentGroupDto;
import tn.esprithub.server.project.portal.dto.StudentNotificationDto;
import tn.esprithub.server.project.portal.dto.StudentProjectDto;
import tn.esprithub.server.project.portal.dto.StudentSubmissionDto;
import tn.esprithub.server.project.portal.dto.StudentTaskDto;

public interface StudentService {
    StudentDashboardDto getStudentDashboard(String studentEmail);

    Page<StudentTaskDto> getStudentTasks(String studentEmail, Pageable pageable, String status, String search);

    StudentTaskDto getTaskDetails(UUID taskId, String studentEmail);

    void submitTask(UUID taskId, String studentEmail, String notes);

    List<StudentGroupDto> getStudentGroups(String studentEmail);

    StudentGroupDto getGroupDetails(UUID groupId, String studentEmail);

    List<StudentProjectDto> getStudentProjects(String studentEmail);

    StudentProjectDto getProjectDetails(UUID projectId, String studentEmail);

    Map<String, Object> getStudentProfile(String studentEmail);

    List<StudentNotificationDto> getNotifications(String studentEmail, boolean unreadOnly);

    void markNotificationAsRead(UUID notificationId, String studentEmail);

    List<StudentDeadlineDto> getUpcomingDeadlines(String studentEmail, int days);

    Page<StudentSubmissionDto> getSubmissions(String studentEmail, Pageable pageable);

    List<Map<String, Object>> getAccessibleRepositories(String studentEmail);

    List<Map<String, Object>> getGroupRepositories(UUID groupId, String studentEmail);

    Map<String, Object> getRepositoryCommits(String repositoryId, String studentEmail, int page, int size, String branch);

    Map<String, Object> getRepositoryDetails(String repositoryId, String studentEmail);

    Map<String, Object> getGitHubRepositoryByFullName(String owner, String repo, String studentEmail);

    List<Map<String, Object>> getStudentGitHubRepositories(String studentEmail);

    Map<String, Object> getAcademicProgress(String studentEmail);

    List<Map<String, Object>> getWeeklySchedule(String studentEmail);

    List<Map<String, Object>> getRecentActivities(String studentEmail, int limit);

    List<Map<String, Object>> getRepositoryFiles(String owner, String repo, String path, String branch, String studentEmail);

    Map<String, Object> getFileContent(String owner, String repo, String path, String branch, String studentEmail);

    List<Map<String, Object>> getRepositoryCommits(String owner, String repo, String branch, int page, int perPage, String studentEmail);

    Map<String, Object> getCommitDetails(String owner, String repo, String sha, String studentEmail);

    List<Map<String, Object>> getRepositoryBranches(String owner, String repo, String studentEmail);

    Map<String, Object> createFile(String owner, String repo, String path, String content, String message, String branch, String studentEmail);

    Map<String, Object> updateFile(String owner, String repo, String path, String content, String message, String sha, String branch, String studentEmail);

    Map<String, Object> deleteFile(String owner, String repo, String path, String message, String sha, String branch, String studentEmail);

    Map<String, Object> createBranch(String owner, String repo, String branchName, String fromBranch, String studentEmail);

    List<Map<String, Object>> getRepositoryContributors(String owner, String repo, String studentEmail);

    Map<String, Object> uploadFile(String owner, String repo, String path, byte[] fileContent, String message, String branch, String studentEmail);

    Map<String, Object> uploadMultipleFiles(String owner, String repo, String basePath, Map<String, byte[]> files, String message, String branch, String studentEmail);

    Map<String, Object> getRepositoryOverview(String owner, String repo, String branch, String studentEmail);

    Map<String, Object> getRepositoryFileTree(String owner, String repo, String branch, String studentEmail);

    Map<String, Object> debugGitHubAccess(String studentEmail);

    String getLatestCommitHash(String repositoryId, String studentEmail);
}
