package tn.esprithub.server.project.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprithub.server.admin.service.AdminUserDataService;
import tn.esprithub.server.common.exception.BusinessException;
import tn.esprithub.server.project.dto.*;
import tn.esprithub.server.project.entity.*;
import tn.esprithub.server.project.repository.*;
import tn.esprithub.server.repository.entity.RepositoryCommit;
import tn.esprithub.server.repository.repository.RepositoryCommitRepository;
import tn.esprithub.server.project.portal.service.StudentService;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SubmissionService {
    
    private static final String USER_NOT_FOUND_MESSAGE = "User not found: ";
    
    private final SubmissionRepository submissionRepository;
    private final SubmissionFileRepository submissionFileRepository;
    private final TaskRepository taskRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final RepositoryCommitRepository commitRepository;
    private final AdminUserDataService adminUserDataService;
    private final StudentService studentService;

    /**
     * Create a new submission for a task
     */
    public SubmissionDto createSubmission(CreateSubmissionDto createDto, String userEmail) {
        log.info("Creating submission for task: {} by user: {}", createDto.getTaskId(), userEmail);
        
        // Get the user
        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new BusinessException(USER_NOT_FOUND_MESSAGE + userEmail));
        
        // Get the task
        Task task = taskRepository.findById(createDto.getTaskId())
            .orElseThrow(() -> new BusinessException("Task not found: " + createDto.getTaskId()));
        
        // Validate task is active and available
        if (!task.isVisible()) {
            throw new BusinessException("Task is not available for submission");
        }
        
        // Check due date
        boolean isLate = task.getDueDate() != null && LocalDateTime.now().isAfter(task.getDueDate());
        
        // Get group if specified or determine from task assignment
        Group group = null;
        if (createDto.getGroupId() != null) {
            group = groupRepository.findById(createDto.getGroupId())
                .orElseThrow(() -> new BusinessException("Group not found: " + createDto.getGroupId()));
        } else {
            // Try to find the user's group for this task
            group = findUserGroupForTask(user.getId(), task);
        }
        
        // Check if submission already exists
        Optional<Submission> existingSubmission;
        if (group != null) {
            existingSubmission = submissionRepository.findByTaskIdAndGroupId(task.getId(), group.getId());
        } else {
            existingSubmission = submissionRepository.findByTaskIdAndUserId(task.getId(), user.getId());
        }
        
        Submission submission;
        if (existingSubmission.isPresent()) {
            // Update existing submission
            submission = existingSubmission.get();
            submission.setCommitHash(createDto.getCommitHash());
            submission.setSubmittedAt(LocalDateTime.now());
            submission.setStatus(Submission.SubmissionStatus.SUBMITTED);
            submission.setIsLate(isLate);
            submission.setNotes(createDto.getNotes());
            submission.setAttemptNumber(submission.getAttemptNumber() + 1);
            log.info("Updating existing submission: {} (attempt: {})", submission.getId(), submission.getAttemptNumber());
        } else {
            // Create new submission
            submission = Submission.builder()
                .taskId(task.getId())
                .studentId(user.getId())
                .userId(user.getId())
                .groupId(group != null ? group.getId() : null)
                .commitHash(createDto.getCommitHash())
                .submittedAt(LocalDateTime.now())
                .status(Submission.SubmissionStatus.SUBMITTED)
                .isLate(isLate)
                .attemptNumber(1)
                .notes(createDto.getNotes())
                .build();
            log.info("Creating new submission for task: {} by user: {}", task.getId(), user.getId());
        }
        
        submission = submissionRepository.save(submission);
        
        // Save repository files content for this submission
        try {
            saveRepositoryFilesForSubmission(submission, group, user);
        } catch (Exception e) {
            log.warn("Failed to save repository files for submission {}: {}", submission.getId(), e.getMessage());
        }
        
        return convertToDto(submission);
    }

    /**
     * Get submission details with code files
     */
    @Transactional(readOnly = true)
    public SubmissionDetailsDto getSubmissionDetails(UUID submissionId) {
        log.info("Getting submission details for: {}", submissionId);
        
        Submission submission = submissionRepository.findById(submissionId)
            .orElseThrow(() -> new BusinessException("Submission not found: " + submissionId));
        
        // Get task details
        Task task = taskRepository.findById(submission.getTaskId())
            .orElseThrow(() -> new BusinessException("Task not found: " + submission.getTaskId()));
        
        // Get user details
        User user = userRepository.findById(submission.getUserId())
            .orElseThrow(() -> new BusinessException(USER_NOT_FOUND_MESSAGE + submission.getUserId()));
        
        // Get group details if applicable
        Group group = null;
        if (submission.getGroupId() != null) {
            group = groupRepository.findById(submission.getGroupId()).orElse(null);
        }
        
        // Get graded by user if applicable
        String gradedByName = null;
        if (submission.getGradedBy() != null) {
            Optional<User> gradedByUser = userRepository.findById(submission.getGradedBy());
            gradedByName = gradedByUser.map(u -> u.getFirstName() + " " + u.getLastName()).orElse(null);
        }
        
        // Get repository and commit details
        UUID repositoryId = null;
        String repositoryName = null;
        String repositoryUrl = null;
        List<Map<String, Object>> files = null;
        Map<String, Object> commitDetails = null;
        
        // Get saved submission files
        List<SubmissionFile> submissionFiles = submissionFileRepository.findBySubmissionIdAndIsActiveTrue(submissionId);
        if (!submissionFiles.isEmpty()) {
            files = submissionFiles.stream()
                .map(this::convertSubmissionFileToMap)
                .toList();
        }
        
        if (group != null && group.getRepository() != null) {
            repositoryId = group.getRepository().getId();
            repositoryName = group.getRepository().getName();
            repositoryUrl = group.getRepository().getUrl();
            
            try {
                // Get commit details
                Optional<RepositoryCommit> commit = commitRepository.findByRepositoryIdAndSha(repositoryId, submission.getCommitHash());
                if (commit.isPresent()) {
                    commitDetails = Map.of(
                        "hash", commit.get().getSha(),
                        "message", commit.get().getMessage(),
                        "author", commit.get().getAuthorName(),
                        "date", commit.get().getAuthorDate(),
                        "url", commit.get().getGithubUrl() != null ? commit.get().getGithubUrl() : ""
                    );
                }
            } catch (Exception e) {
                log.warn("Could not get commit details for submission: {}", submissionId, e);
            }
        }
        
        return SubmissionDetailsDto.builder()
            .id(submission.getId())
            .taskId(submission.getTaskId())
            .taskTitle(task.getTitle())
            .taskDescription(task.getDescription())
            .userId(submission.getUserId())
            .userName(user.getFirstName() + " " + user.getLastName())
            .userEmail(user.getEmail())
            .groupId(submission.getGroupId())
            .groupName(group != null ? group.getName() : null)
            .commitHash(submission.getCommitHash())
            .submittedAt(submission.getSubmittedAt())
            .status(submission.getStatus().toString())
            .grade(submission.getGrade())
            .maxGrade(submission.getMaxGrade())
            .feedback(submission.getFeedback())
            .gradedAt(submission.getGradedAt())
            .gradedByName(gradedByName)
            .isLate(submission.getIsLate())
            .attemptNumber(submission.getAttemptNumber())
            .notes(submission.getNotes())
            .repositoryId(repositoryId)
            .repositoryName(repositoryName)
            .repositoryUrl(repositoryUrl)
            .files(files)
            .commitDetails(commitDetails)
            .gradePercentage(submission.getGradePercentage())
            .isPassing(submission.isPassing())
            .isGraded(submission.isGraded())
            .build();
    }

    /**
     * Grade a submission
     */
    public SubmissionDto gradeSubmission(UUID submissionId, GradeSubmissionDto gradeDto, String graderEmail) {
        log.info("Grading submission: {} by: {}", submissionId, graderEmail);
        
        Submission submission = submissionRepository.findById(submissionId)
            .orElseThrow(() -> new BusinessException("Submission not found: " + submissionId));
        
        User grader = userRepository.findByEmail(graderEmail)
            .orElseThrow(() -> new BusinessException(USER_NOT_FOUND_MESSAGE + graderEmail));
        
        // Validate grader has permission to grade this submission
        validateGraderPermission(submission, grader);
        
        submission.setGrade(gradeDto.getGrade());
        submission.setMaxGrade(gradeDto.getMaxGrade());
        submission.setFeedback(gradeDto.getFeedback());
        submission.setGradedAt(LocalDateTime.now());
        submission.setGradedBy(grader.getId());
        submission.setStatus(Submission.SubmissionStatus.GRADED);
        
        submission = submissionRepository.save(submission);
        
        return convertToDto(submission);
    }

    /**
     * Get submissions for a task (for teachers)
     */
    @Transactional(readOnly = true)
    public List<SubmissionDto> getSubmissionsForTask(UUID taskId) {
        log.info("Getting submissions for task: {}", taskId);
        
        List<Submission> submissions = submissionRepository.findByTaskIdOrderBySubmittedAtDesc(taskId);
        return submissions.stream()
            .map(this::convertToDto)
            .toList();
    }

    /**
     * Get submissions for a user (for students)
     */
    @Transactional(readOnly = true)
    public Page<SubmissionDto> getSubmissionsForUser(String userEmail, Pageable pageable) {
        log.info("Getting submissions for user: {}", userEmail);
        
        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new BusinessException(USER_NOT_FOUND_MESSAGE + userEmail));
        
        Page<Submission> submissions = submissionRepository.findByUserIdOrderBySubmittedAtDesc(user.getId(), pageable);
        return submissions.map(this::convertToDto);
    }

    /**
     * Get tasks available for submission by a student
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAvailableTasksForStudent(String userEmail) {
        log.info("Getting available tasks for student: {}", userEmail);
        
        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new BusinessException(USER_NOT_FOUND_MESSAGE + userEmail));
        
        // This would need to be implemented based on your task assignment logic
        // For now, return tasks assigned to the student
        List<Task> tasks = taskRepository.findTasksAssignedToUser(user.getId());
        
        return tasks.stream()
            .map(task -> {
                // Check if user already submitted
                boolean hasSubmitted = submissionRepository.existsByTaskIdAndUserId(task.getId(), user.getId());
                
                // Get user's group for this task if applicable
                Group userGroup = findUserGroupForTask(user.getId(), task);
                
                Map<String, Object> taskMap = new HashMap<>();
                taskMap.put("id", task.getId());
                taskMap.put("title", task.getTitle());
                taskMap.put("description", task.getDescription());
                taskMap.put("dueDate", task.getDueDate());
                taskMap.put("graded", task.isGraded());
                taskMap.put("hasSubmitted", hasSubmitted);
                taskMap.put("groupId", userGroup != null ? userGroup.getId() : null);
                taskMap.put("groupName", userGroup != null ? userGroup.getName() : null);
                taskMap.put("repositoryId", userGroup != null && userGroup.getRepository() != null ? userGroup.getRepository().getId() : null);
                return taskMap;
            })
            .toList();
    }

    /**
     * Get submissions for tasks that a teacher has access to
     */
    public Page<SubmissionDto> getSubmissionsForTeacher(String teacherEmail, Pageable pageable) {
        log.info("Getting submissions for teacher: {}", teacherEmail);
        
        // For now, get all submissions. We can add filtering by teacher's tasks later
        Page<Submission> submissions = submissionRepository.findAllByOrderBySubmittedAtDesc(pageable);
        
        return submissions.map(this::convertToDto);
    }
    
    private SubmissionDto convertToDto(Submission submission) {
        // Get related entities
        Task task = taskRepository.findById(submission.getTaskId()).orElse(null);
        User user = userRepository.findById(submission.getUserId()).orElse(null);
        Group group = submission.getGroupId() != null ? 
            groupRepository.findById(submission.getGroupId()).orElse(null) : null;
        User gradedBy = submission.getGradedBy() != null ? 
            userRepository.findById(submission.getGradedBy()).orElse(null) : null;
        
        return SubmissionDto.builder()
            .id(submission.getId())
            .taskId(submission.getTaskId())
            .taskTitle(task != null ? task.getTitle() : null)
            .userId(submission.getUserId())
            .userName(user != null ? user.getFirstName() + " " + user.getLastName() : null)
            .userEmail(user != null ? user.getEmail() : null)
            .groupId(submission.getGroupId())
            .groupName(group != null ? group.getName() : null)
            .commitHash(submission.getCommitHash())
            .submittedAt(submission.getSubmittedAt())
            .status(submission.getStatus().toString())
            .grade(submission.getGrade())
            .maxGrade(submission.getMaxGrade())
            .feedback(submission.getFeedback())
            .gradedAt(submission.getGradedAt())
            .gradedByName(gradedBy != null ? gradedBy.getFirstName() + " " + gradedBy.getLastName() : null)
            .isLate(submission.getIsLate())
            .attemptNumber(submission.getAttemptNumber())
            .notes(submission.getNotes())
            .gradePercentage(submission.getGradePercentage())
            .isPassing(submission.isPassing())
            .isGraded(submission.isGraded())
            .build();
    }

    private Group findUserGroupForTask(UUID userId, Task task) {
        // This logic depends on how tasks are assigned to groups
        // For now, find any group that contains the user and is assigned to the task
        if (task.getAssignedToGroups() != null) {
            return task.getAssignedToGroups().stream()
                .filter(group -> group.getStudents().stream()
                    .anyMatch(student -> student.getId().equals(userId)))
                .findFirst()
                .orElse(null);
        }
        return null;
    }

    private void validateGraderPermission(Submission submission, User grader) {
        // Get the task
        Task task = taskRepository.findById(submission.getTaskId())
            .orElseThrow(() -> new BusinessException("Task not found"));
        
        // Check if grader is admin, teacher, or collaborator on the project
        if (!grader.getRole().name().equals("ADMIN") && 
            !grader.getRole().name().equals("TEACHER")) {
            
            // Check if user is a collaborator on any project containing this task
            boolean isCollaborator = task.getProjects().stream()
                .anyMatch(project -> project.getCollaborators().stream()
                    .anyMatch(collaborator -> collaborator.getId().equals(grader.getId())));
            
            if (!isCollaborator) {
                throw new BusinessException("You don't have permission to grade this submission");
            }
        }
    }
    
    /**
     * Save repository files content for a submission
     */
    private void saveRepositoryFilesForSubmission(Submission submission, Group group, User user) {
        if (group == null || group.getRepository() == null) {
            log.debug("No group repository found for submission {}", submission.getId());
            return;
        }
        
        try {
            // Get repository details from group
            var repository = group.getRepository();
            String repoOwner = repository.getOwner() != null ? repository.getOwner().getGithubUsername() : "unknown";
            String repoName = repository.getName();
            
            if (user.getGithubToken() == null || user.getGithubToken().isBlank()) {
                log.warn("User {} has no GitHub token, cannot fetch repository files", user.getEmail());
                return;
            }
            
            // Get files from the root directory
            List<Map<String, Object>> files = studentService.getRepositoryFiles(
                repoOwner, repoName, "", "main", user.getEmail()
            );
            
            // Save each file
            for (Map<String, Object> fileInfo : files) {
                saveFileFromRepository(submission, fileInfo, repoOwner, repoName, user);
            }
            
            log.info("Saved {} files for submission {}", files.size(), submission.getId());
            
        } catch (Exception e) {
            log.error("Error saving repository files for submission {}: {}", submission.getId(), e.getMessage());
        }
    }
    
    /**
     * Save a single file from repository
     */
    private void saveFileFromRepository(Submission submission, Map<String, Object> fileInfo, 
                                       String repoOwner, String repoName, User user) {
        try {
            String fileName = (String) fileInfo.get("name");
            String type = (String) fileInfo.get("type");
            String path = (String) fileInfo.get("path");
            
            // Skip directories and non-code files for now
            if (!"file".equals(type)) {
                return;
            }
            
            // Check if it's a code file
            String extension = "";
            if (fileName.contains(".")) {
                extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
            }
            
            if (!isCodeFile(extension)) {
                log.debug("Skipping non-code file: {}", fileName);
                return;
            }
            
            // Get file content
            Map<String, Object> fileContent = studentService.getFileContent(
                repoOwner, repoName, path, "main", user.getEmail());
            
            String content = (String) fileContent.get("content");
            if (content != null) {
                // Decode base64 content
                content = decodeFileContent(content);
                
                // Create SubmissionFile
                SubmissionFile submissionFile = SubmissionFile.builder()
                    .submission(submission)
                    .fileName(fileName)
                    .originalName(fileName)
                    .filePath(path != null ? path : fileName)
                    .fileSize((long) content.length())
                    .contentType(getContentType(extension))
                    .content(content)
                    .isActive(true)
                    .build();
                
                submissionFileRepository.save(submissionFile);
                log.debug("Saved file: {} ({} bytes)", fileName, content.length());
            }
            
        } catch (Exception e) {
            log.warn("Failed to save file from repository: {}", e.getMessage());
        }
    }
    
    /**
     * Get content type based on file extension
     */
    private String getContentType(String extension) {
        return switch (extension.toLowerCase()) {
            case "java" -> "text/x-java-source";
            case "js" -> "text/javascript";
            case "ts" -> "text/typescript";
            case "py" -> "text/x-python";
            case "html" -> "text/html";
            case "css" -> "text/css";
            case "json" -> "application/json";
            case "xml" -> "text/xml";
            case "md" -> "text/markdown";
            case "txt" -> "text/plain";
            default -> "text/plain";
        };
    }
    
    /**
     * Check if file extension is a code file
     */
    private boolean isCodeFile(String extension) {
        String[] codeExtensions = {"java", "js", "ts", "py", "cpp", "c", "cs", "php", "rb", "go", "rs", "kt", "swift", 
                                 "html", "css", "scss", "xml", "json", "yaml", "yml", "sql", "sh", "bat", "txt", "md"};
        for (String ext : codeExtensions) {
            if (extension.equals(ext)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Decode base64 file content
     */
    private String decodeFileContent(String content) {
        try {
            return new String(java.util.Base64.getDecoder().decode(content));
        } catch (Exception e) {
            log.debug("Content is not base64 encoded, returning as-is");
            return content;
        }
    }
    
    /**
     * Convert SubmissionFile to Map for API response
     */
    private Map<String, Object> convertSubmissionFileToMap(SubmissionFile file) {
        Map<String, Object> fileMap = new HashMap<>();
        fileMap.put("id", file.getId());
        fileMap.put("name", file.getFileName());
        fileMap.put("originalName", file.getOriginalName());
        fileMap.put("path", file.getFilePath());
        fileMap.put("size", file.getFileSize());
        fileMap.put("contentType", file.getContentType());
        fileMap.put("content", file.getContent());
        fileMap.put("extension", file.getFileExtension());
        fileMap.put("displaySize", file.getDisplaySize());
        fileMap.put("createdAt", file.getCreatedAt());
        fileMap.put("type", "file"); // For compatibility with existing frontend code
        return fileMap;
    }
}
