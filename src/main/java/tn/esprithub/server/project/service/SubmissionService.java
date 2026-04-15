package tn.esprithub.server.project.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tn.esprithub.server.ai.CodeReviewService;
import tn.esprithub.server.ai.dto.CodeReviewResult;
import tn.esprithub.server.admin.service.AdminUserDataService;
import tn.esprithub.server.common.exception.BusinessException;
import tn.esprithub.server.notification.NotificationService;
import tn.esprithub.server.project.dto.*;
import tn.esprithub.server.project.entity.*;
import tn.esprithub.server.project.portal.service.StudentService;
import tn.esprithub.server.project.repository.*;
import tn.esprithub.server.repository.entity.RepositoryCommit;
import tn.esprithub.server.repository.entity.RepositoryFileChange;
import tn.esprithub.server.repository.repository.RepositoryCommitRepository;
import tn.esprithub.server.repository.repository.RepositoryFileChangeRepository;
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
    private static final String GITHUB_API_BASE = "https://api.github.com/repos";
    private static final int MAX_DIFF_PAYLOAD_CHARS = 24_000;
    private static final int MAX_DIFF_FILES = 15;
    private static final int MAX_SUBMISSION_FILES = 300;
    private static final List<String> SUPPORTED_PREVIEW_FRAMEWORKS = List.of(
            "Angular",
            "React",
            "Vue",
            "Next.js",
            "Spring Boot",
            "Express",
            "Django",
            "Flask",
            "FastAPI"
    );
    
    private final SubmissionRepository submissionRepository;
    private final SubmissionFileRepository submissionFileRepository;
    private final TaskRepository taskRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final RepositoryCommitRepository commitRepository;
    private final RepositoryFileChangeRepository fileChangeRepository;
    private final AdminUserDataService adminUserDataService;
    private final StudentService studentService;
    private final NotificationService notificationService;
    private final CodeReviewService codeReviewService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

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
            submission.setAiReviewCache(null);
            submission.setAiReviewCacheCommitHash(null);
            submission.setAiReviewCachedAt(null);
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
                .aiReviewCache(null)
                .aiReviewCacheCommitHash(null)
                .aiReviewCachedAt(null)
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

        notifyTaskReviewersAboutSubmission(task, submission, user);
        
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

        Task task = taskRepository.findById(submission.getTaskId())
            .orElseThrow(() -> new BusinessException("Task not found: "));
        notifyStudentsAboutGradedSubmission(task, submission, grader);
        
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

    /**
     * Build a runnable app preview for teachers based on a submission's repository stack.
     */
    @Transactional(readOnly = true)
    public SubmissionReviewAppPreviewDto getTeacherReviewAppPreview(UUID submissionId, String teacherEmail) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new BusinessException("Submission not found: " + submissionId));

        User teacher = userRepository.findByEmail(teacherEmail)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND_MESSAGE + teacherEmail));

        validateGraderPermission(submission, teacher);

        Group group = submission.getGroupId() != null
                ? groupRepository.findById(submission.getGroupId()).orElse(null)
                : null;

        if (group == null || group.getRepository() == null) {
            throw new BusinessException("No repository is linked to this submission.");
        }

        tn.esprithub.server.repository.entity.Repository repository = group.getRepository();
        List<SubmissionFile> submissionFiles = submissionFileRepository.findBySubmissionIdAndIsActiveTrue(submissionId);

        FrameworkDetection detection = detectFramework(submissionFiles, repository.getLanguage());
        Optional<String> hostedUrl = findExistingPreviewUrl(repository, submissionFiles);

        String previewUrl = hostedUrl.orElseGet(() -> buildCloudPreviewUrl(repository, detection));
        if (!hasText(previewUrl)) {
            throw new BusinessException("Could not generate a preview link for this submission.");
        }

        return SubmissionReviewAppPreviewDto.builder()
                .framework(detection.framework())
                .language(detection.language())
                .provider(hostedUrl.isPresent() ? detectProviderFromUrl(previewUrl) : detection.provider())
                .previewUrl(previewUrl)
                .source(hostedUrl.isPresent() ? "EXISTING_DEPLOYMENT" : "CLOUD_PREVIEW")
                .confidence(detection.confidence())
                .detectedSignals(detection.signals())
                .supportedFrameworks(SUPPORTED_PREVIEW_FRAMEWORKS)
                .repositoryUrl(repository.getUrl())
                .build();
    }

    /**
     * Analyze a submission commit with AI as a PR review and return grade suggestion.
     */
    public CodeReviewResult getTeacherSubmissionAiReview(UUID submissionId, String teacherEmail, boolean forceRefresh) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new BusinessException("Submission not found: " + submissionId));

        Task task = taskRepository.findById(submission.getTaskId())
            .orElseThrow(() -> new BusinessException("Task not found: " + submission.getTaskId()));

        User teacher = userRepository.findByEmail(teacherEmail)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND_MESSAGE + teacherEmail));

        validateGraderPermission(submission, teacher);

        Group group = submission.getGroupId() != null
                ? groupRepository.findById(submission.getGroupId()).orElse(null)
                : null;

        tn.esprithub.server.repository.entity.Repository repository = group != null ? group.getRepository() : null;
        List<SubmissionFile> submissionFiles = submissionFileRepository.findBySubmissionIdAndIsActiveTrue(submissionId);

        if (repository == null && submissionFiles.isEmpty()) {
            throw new BusinessException("No repository or submission files found for AI review.");
        }

        if (!forceRefresh) {
            Optional<CodeReviewResult> cachedReview = loadCachedAiReview(submission);
            if (cachedReview.isPresent()) {
                return cachedReview.get();
            }
        }

        String language = detectSubmissionLanguage(repository, submissionFiles);
        Optional<String> diffPayloadOpt = buildCommitDiffForSubmission(submission, repository, teacher);

        if (diffPayloadOpt.isEmpty() || !hasText(diffPayloadOpt.get())) {
            return CodeReviewResult.builder()
                    .success(false)
                    .message("Unable to retrieve exact commit diff for this submission. Please sync repository commit data and retry AI review.")
                    .build();
        }

        String diffPayload = diffPayloadOpt.get();

        double gradingScale = submission.getMaxGrade() != null && submission.getMaxGrade() > 0
            ? submission.getMaxGrade()
            : 20.0;

        CodeReviewResult result = codeReviewService.analyzeSubmissionAgainstTask(
            diffPayload,
            language,
            task.getTitle(),
            task.getDescription(),
            gradingScale
        );

        if (!result.isSuccess()) {
            return result;
        }

        result.setAnalyzedLanguage(language);
        result.setAnalyzedFile(submission.getCommitHash());

        if (result.getSuggestedGrade() == null && result.getOverallScore() != null) {
            result.setSuggestedGrade(Math.max(0, Math.min(100, result.getOverallScore() * 10)));
        }

        if (!hasText(result.getGradeRationale())) {
            result.setGradeRationale("Suggested grade generated from AI PR-style review of the submitted commit.");
        }

        if (result.getPullRequestDecision() == null) {
            result.setPullRequestDecision(CodeReviewResult.PullRequestDecision.COMMENT);
        }

        if (result.getRiskLevel() == null) {
            result.setRiskLevel(CodeReviewResult.RiskLevel.MEDIUM);
        }

        if (result.getRiskScore() == null && result.getOverallScore() != null) {
            result.setRiskScore(Math.max(0, Math.min(100, 100 - (result.getOverallScore() * 10))));
        }

        if (result.getRiskSignals() == null) {
            result.setRiskSignals(List.of());
        }

        if (result.getPrBlockingConcerns() == null) {
            result.setPrBlockingConcerns(List.of());
        }

        cacheAiReview(submission, result);

        return result;
    }

    private Optional<CodeReviewResult> loadCachedAiReview(Submission submission) {
        if (!hasText(submission.getAiReviewCache()) || !hasText(submission.getAiReviewCacheCommitHash())) {
            return Optional.empty();
        }

        if (!Objects.equals(submission.getCommitHash(), submission.getAiReviewCacheCommitHash())) {
            return Optional.empty();
        }

        try {
            CodeReviewResult cached = objectMapper.readValue(submission.getAiReviewCache(), CodeReviewResult.class);
            return Optional.ofNullable(cached);
        } catch (Exception ex) {
            log.warn("Failed to deserialize cached AI review for submission {}: {}", submission.getId(), ex.getMessage());
            return Optional.empty();
        }
    }

    private void cacheAiReview(Submission submission, CodeReviewResult result) {
        if (submission == null || result == null || !result.isSuccess()) {
            return;
        }

        try {
            submission.setAiReviewCache(objectMapper.writeValueAsString(result));
            submission.setAiReviewCacheCommitHash(submission.getCommitHash());
            submission.setAiReviewCachedAt(LocalDateTime.now());
            submissionRepository.save(submission);
        } catch (Exception ex) {
            log.warn("Failed to cache AI review for submission {}: {}", submission.getId(), ex.getMessage());
        }
    }

    private Optional<String> buildCommitDiffForSubmission(Submission submission,
                                                           tn.esprithub.server.repository.entity.Repository repository,
                                                           User requester) {

        if (repository != null) {
            Optional<String> githubDiff = fetchCommitDiffFromGitHub(repository, submission.getCommitHash(), requester);
            if (githubDiff.isPresent()) {
                return githubDiff;
            }

            Optional<String> storedDiff = buildDiffFromStoredFileChanges(repository, submission.getCommitHash());
            if (storedDiff.isPresent()) {
                return storedDiff;
            }
        }

        return Optional.empty();
    }

    private Optional<String> fetchCommitDiffFromGitHub(tn.esprithub.server.repository.entity.Repository repository,
                                                        String commitHash,
                                                        User requester) {
        if (!hasText(commitHash)) {
            return Optional.empty();
        }

        String repoFullName = resolveRepositoryFullName(repository);
        if (!hasText(repoFullName)) {
            return Optional.empty();
        }

        String token = resolveGitHubTokenForDiff(repository, requester);
        if (!hasText(token)) {
            return Optional.empty();
        }

        try {
            String url = GITHUB_API_BASE + "/" + repoFullName + "/commits/" + commitHash;
            HttpHeaders headers = buildGitHubHeaders(token);
            headers.set("X-GitHub-Api-Version", "2022-11-28");
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    requestEntity,
                    (Class<Map<String, Object>>) (Class<?>) Map.class
            );

            Map<String, Object> body = response.getBody();
            if (body == null || !(body.get("files") instanceof List<?> files) || files.isEmpty()) {
                return Optional.empty();
            }

            StringBuilder diffBuilder = new StringBuilder();
            int processedFiles = 0;

            for (Object fileObject : files) {
                if (!(fileObject instanceof Map<?, ?> rawFile)) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> fileData = (Map<String, Object>) rawFile;

                String newPath = asString(fileData.get("filename"));
                if (!hasText(newPath)) {
                    continue;
                }

                String status = asString(fileData.get("status"));
                String oldPath = asString(fileData.get("previous_filename"));
                if (!hasText(oldPath)) {
                    oldPath = newPath;
                }

                diffBuilder.append("diff --git a/").append(oldPath).append(" b/").append(newPath).append("\n");
                diffBuilder.append("added".equalsIgnoreCase(status) ? "--- /dev/null\n" : "--- a/" + oldPath + "\n");
                diffBuilder.append("removed".equalsIgnoreCase(status) ? "+++ /dev/null\n" : "+++ b/" + newPath + "\n");

                String patch = asString(fileData.get("patch"));
                if (hasText(patch)) {
                    diffBuilder.append(patch).append("\n\n");
                } else {
                    int additions = asInteger(fileData.get("additions")) != null ? asInteger(fileData.get("additions")) : 0;
                    int deletions = asInteger(fileData.get("deletions")) != null ? asInteger(fileData.get("deletions")) : 0;
                    int changes = asInteger(fileData.get("changes")) != null ? asInteger(fileData.get("changes")) : additions + deletions;
                    diffBuilder.append("@@ metadata @@\n");
                    diffBuilder.append("+status: ").append(status).append("\n");
                    diffBuilder.append("+additions: ").append(additions).append("\n");
                    diffBuilder.append("+deletions: ").append(deletions).append("\n");
                    diffBuilder.append("+changes: ").append(changes).append("\n\n");
                }

                processedFiles++;
                if (processedFiles >= MAX_DIFF_FILES || diffBuilder.length() >= MAX_DIFF_PAYLOAD_CHARS) {
                    break;
                }
            }

            String payload = trimToMax(diffBuilder.toString(), MAX_DIFF_PAYLOAD_CHARS);
            return hasText(payload) ? Optional.of(payload) : Optional.empty();
        } catch (Exception ex) {
            log.debug("Could not fetch commit diff from GitHub for {}: {}", commitHash, ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> buildDiffFromStoredFileChanges(tn.esprithub.server.repository.entity.Repository repository,
                                                            String commitHash) {
        if (repository == null || !hasText(commitHash)) {
            return Optional.empty();
        }

        Optional<RepositoryCommit> commitOpt = commitRepository.findByRepositoryIdAndSha(repository.getId(), commitHash);
        if (commitOpt.isEmpty()) {
            return Optional.empty();
        }

        List<RepositoryFileChange> fileChanges = fileChangeRepository.findByCommitIdOrderByFilePathAsc(
                commitOpt.get().getId(),
                PageRequest.of(0, MAX_DIFF_FILES)
        );

        if (fileChanges.isEmpty()) {
            return Optional.empty();
        }

        StringBuilder diffBuilder = new StringBuilder();
        for (RepositoryFileChange change : fileChanges) {
            String path = hasText(change.getFilePath()) ? change.getFilePath() : change.getFileName();
            if (!hasText(path)) {
                continue;
            }

            String previousPath = hasText(change.getPreviousFilePath()) ? change.getPreviousFilePath() : path;
            String changeType = hasText(change.getChangeType()) ? change.getChangeType() : "modified";

            diffBuilder.append("diff --git a/").append(previousPath).append(" b/").append(path).append("\n");
            diffBuilder.append("added".equalsIgnoreCase(changeType) ? "--- /dev/null\n" : "--- a/" + previousPath + "\n");
            diffBuilder.append("removed".equalsIgnoreCase(changeType) ? "+++ /dev/null\n" : "+++ b/" + path + "\n");

            if (hasText(change.getPatch())) {
                diffBuilder.append(change.getPatch()).append("\n\n");
            } else {
                diffBuilder.append("@@ metadata @@\n");
                diffBuilder.append("+changeType: ").append(changeType).append("\n");
                diffBuilder.append("+additions: ").append(change.getAdditions() != null ? change.getAdditions() : 0).append("\n");
                diffBuilder.append("+deletions: ").append(change.getDeletions() != null ? change.getDeletions() : 0).append("\n");
                diffBuilder.append("+changes: ").append(change.getChanges() != null ? change.getChanges() : 0).append("\n\n");
            }

            if (diffBuilder.length() >= MAX_DIFF_PAYLOAD_CHARS) {
                break;
            }
        }

        String payload = trimToMax(diffBuilder.toString(), MAX_DIFF_PAYLOAD_CHARS);
        return hasText(payload) ? Optional.of(payload) : Optional.empty();
    }

    private String resolveGitHubTokenForDiff(tn.esprithub.server.repository.entity.Repository repository, User requester) {
        if (repository != null && repository.getOwner() != null && hasText(repository.getOwner().getGithubToken())) {
            return repository.getOwner().getGithubToken();
        }

        if (requester != null && hasText(requester.getGithubToken())) {
            return requester.getGithubToken();
        }

        return null;
    }

    private String detectSubmissionLanguage(tn.esprithub.server.repository.entity.Repository repository,
                                            List<SubmissionFile> submissionFiles) {
        if (repository != null && hasText(repository.getLanguage())) {
            return normalizeLanguageForAi(repository.getLanguage());
        }

        Map<String, Integer> languageCounts = new HashMap<>();
        for (SubmissionFile file : submissionFiles) {
            String path = hasText(file.getFilePath()) ? file.getFilePath() : file.getFileName();
            String extension = extractExtensionFromPath(path);
            String language = extensionToLanguage(extension);
            if (language != null) {
                languageCounts.merge(language, 1, Integer::sum);
            }
        }

        return languageCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("text");
    }

    private String extensionToLanguage(String extension) {
        return switch (extension) {
            case "java" -> "java";
            case "js", "jsx" -> "javascript";
            case "ts", "tsx" -> "typescript";
            case "py" -> "python";
            case "cpp", "cc", "cxx" -> "cpp";
            case "c" -> "c";
            case "cs" -> "csharp";
            case "kt" -> "kotlin";
            case "go" -> "go";
            case "rs" -> "rust";
            case "php" -> "php";
            case "rb" -> "ruby";
            case "swift" -> "swift";
            case "html", "htm" -> "html";
            case "css", "scss", "sass" -> "css";
            case "sql" -> "sql";
            case "xml" -> "xml";
            case "json" -> "json";
            case "yml", "yaml" -> "yaml";
            case "md" -> "markdown";
            case "sh" -> "bash";
            case "ps1" -> "powershell";
            default -> null;
        };
    }

    private String normalizeLanguageForAi(String language) {
        String normalized = language.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "c++" -> "cpp";
            case "c#" -> "csharp";
            case "js" -> "javascript";
            case "ts" -> "typescript";
            default -> normalized;
        };
    }

    private String extractExtensionFromPath(String path) {
        if (!hasText(path) || !path.contains(".")) {
            return "";
        }

        String lowerPath = path.toLowerCase(Locale.ROOT);
        return lowerPath.substring(lowerPath.lastIndexOf('.') + 1);
    }

    private String trimToMax(String input, int maxChars) {
        if (!hasText(input) || input.length() <= maxChars) {
            return input;
        }
        return input.substring(0, maxChars) + "\n\n... [diff truncated]";
    }

    private void notifyTaskReviewersAboutSubmission(Task task, Submission submission, User submitter) {
        Map<UUID, User> recipients = new LinkedHashMap<>();

        if (task.getProjects() != null) {
            for (Project project : task.getProjects()) {
                if (project == null) {
                    continue;
                }
                if (project.getCreatedBy() != null && project.getCreatedBy().getId() != null) {
                    recipients.put(project.getCreatedBy().getId(), project.getCreatedBy());
                }
                if (project.getCollaborators() != null) {
                    for (User collaborator : project.getCollaborators()) {
                        if (collaborator != null && collaborator.getId() != null) {
                            recipients.put(collaborator.getId(), collaborator);
                        }
                    }
                }
            }
        }

        if (task.getAssignedToClasses() != null) {
            for (var classe : task.getAssignedToClasses()) {
                if (classe == null || classe.getTeachers() == null) {
                    continue;
                }
                for (User teacher : classe.getTeachers()) {
                    if (teacher != null && teacher.getId() != null) {
                        recipients.put(teacher.getId(), teacher);
                    }
                }
            }
        }

        if (submitter != null && submitter.getId() != null) {
            recipients.remove(submitter.getId());
        }

        if (recipients.isEmpty()) {
            return;
        }

        String title = "New submission received: " + task.getTitle();
        String message = String.format(
                "%s submitted attempt #%d for task '%s'%s.",
                submitter != null ? submitter.getFullName() : "A student",
                submission.getAttemptNumber() != null ? submission.getAttemptNumber() : 1,
                task.getTitle(),
                Boolean.TRUE.equals(submission.getIsLate()) ? " (late)" : ""
        );

        notificationService.createInAppNotifications(recipients.values(), title, message, "INFO");
    }

    private void notifyStudentsAboutGradedSubmission(Task task, Submission submission, User grader) {
        Map<UUID, User> recipients = new LinkedHashMap<>();

        userRepository.findById(submission.getUserId())
                .ifPresent(student -> recipients.put(student.getId(), student));

        if (submission.getGroupId() != null) {
            groupRepository.findById(submission.getGroupId()).ifPresent(group -> {
                if (group.getStudents() == null) {
                    return;
                }
                for (User student : group.getStudents()) {
                    if (student != null && student.getId() != null) {
                        recipients.put(student.getId(), student);
                    }
                }
            });
        }

        if (grader != null && grader.getId() != null) {
            recipients.remove(grader.getId());
        }

        if (recipients.isEmpty()) {
            return;
        }

        String score = "";
        if (submission.getGrade() != null && submission.getMaxGrade() != null) {
            score = String.format(" Grade: %.2f/%.2f.", submission.getGrade(), submission.getMaxGrade());
        }

        String feedback = hasText(submission.getFeedback()) ? " Feedback is available." : "";
        String title = "Submission graded: " + task.getTitle();
        String message = String.format(
                "Your submission for '%s' was graded by %s.%s%s",
                task.getTitle(),
                grader != null ? grader.getFullName() : "your teacher",
                score,
                feedback
        );

        String notificationType = submission.isPassing() ? "SUCCESS" : "WARNING";
        notificationService.createInAppNotifications(recipients.values(), title, message, notificationType);
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

    private FrameworkDetection detectFramework(List<SubmissionFile> files, String repositoryLanguage) {
        Set<String> knownFiles = new HashSet<>();
        Map<String, String> contentByFile = new HashMap<>();

        for (SubmissionFile file : files) {
            if (file.getFileName() != null) {
                knownFiles.add(file.getFileName().toLowerCase(Locale.ROOT));
            }
            if (file.getFilePath() != null) {
                knownFiles.add(file.getFilePath().toLowerCase(Locale.ROOT));
            }
            if (file.getContent() != null) {
                if (file.getFileName() != null) {
                    contentByFile.put(file.getFileName().toLowerCase(Locale.ROOT), file.getContent());
                }
                if (file.getFilePath() != null) {
                    contentByFile.put(file.getFilePath().toLowerCase(Locale.ROOT), file.getContent());
                }
            }
        }

        String packageJson = findFileContent(contentByFile, "package.json");
        String pomXml = findFileContent(contentByFile, "pom.xml");
        String buildGradle = findFileContent(contentByFile, "build.gradle", "build.gradle.kts");
        String requirementsTxt = findFileContent(contentByFile, "requirements.txt");
        String pyproject = findFileContent(contentByFile, "pyproject.toml");

        Set<String> npmDependencies = extractNpmDependencies(packageJson);

        if (hasFile(knownFiles, "angular.json") || npmDependencies.contains("@angular/core")) {
            return new FrameworkDetection("Angular", "TypeScript", "STACKBLITZ", 0.98,
                    List.of("angular.json", "@angular/core"));
        }

        if (npmDependencies.contains("next")) {
            return new FrameworkDetection("Next.js", "TypeScript", "STACKBLITZ", 0.96,
                    List.of("next", "package.json"));
        }

        if (npmDependencies.contains("react")) {
            return new FrameworkDetection("React", "JavaScript", "STACKBLITZ", 0.95,
                    List.of("react", "package.json"));
        }

        if (npmDependencies.contains("vue") || hasFile(knownFiles, "vue.config.js")) {
            return new FrameworkDetection("Vue", "JavaScript", "STACKBLITZ", 0.94,
                    List.of("vue", "package.json"));
        }

        if (containsIgnoreCase(pomXml, "spring-boot") || containsIgnoreCase(buildGradle, "org.springframework.boot")) {
            return new FrameworkDetection("Spring Boot", "Java", "REPLIT", 0.95,
                    List.of("pom.xml/build.gradle", "spring-boot"));
        }

        if (npmDependencies.contains("express") || hasFile(knownFiles, "server.js")) {
            return new FrameworkDetection("Express", "JavaScript", "REPLIT", 0.90,
                    List.of("express/server.js", "package.json"));
        }

        if (hasFile(knownFiles, "manage.py") || containsIgnoreCase(requirementsTxt, "django") || containsIgnoreCase(pyproject, "django")) {
            return new FrameworkDetection("Django", "Python", "REPLIT", 0.93,
                    List.of("manage.py/requirements.txt", "django"));
        }

        if (containsIgnoreCase(requirementsTxt, "fastapi") || containsIgnoreCase(pyproject, "fastapi")) {
            return new FrameworkDetection("FastAPI", "Python", "REPLIT", 0.93,
                    List.of("requirements.txt/pyproject.toml", "fastapi"));
        }

        if (containsIgnoreCase(requirementsTxt, "flask") || containsIgnoreCase(pyproject, "flask")) {
            return new FrameworkDetection("Flask", "Python", "REPLIT", 0.92,
                    List.of("requirements.txt/pyproject.toml", "flask"));
        }

        if (hasFile(knownFiles, "index.html")) {
            return new FrameworkDetection("Static Web", "HTML", "STACKBLITZ", 0.75,
                    List.of("index.html"));
        }

        String language = hasText(repositoryLanguage) ? repositoryLanguage : "Unknown";
        String provider = ("JavaScript".equalsIgnoreCase(language) || "TypeScript".equalsIgnoreCase(language))
                ? "STACKBLITZ"
                : "REPLIT";

        return new FrameworkDetection("Generic " + language + " App", language, provider, 0.50,
                List.of("repository.language"));
    }

    private Optional<String> findExistingPreviewUrl(tn.esprithub.server.repository.entity.Repository repository,
                                                    List<SubmissionFile> files) {
        Optional<String> deploymentUrl = findLatestSuccessfulDeploymentUrl(repository);
        if (deploymentUrl.isPresent()) {
            return deploymentUrl;
        }

        Optional<String> homepageUrl = findPackageHomepage(files);
        if (homepageUrl.isPresent()) {
            return homepageUrl;
        }

        if (Boolean.TRUE.equals(repository.getHasPages())) {
            return buildGitHubPagesUrl(repository);
        }

        return Optional.empty();
    }

    private Optional<String> findLatestSuccessfulDeploymentUrl(tn.esprithub.server.repository.entity.Repository repository) {
        if (repository.getOwner() == null || !hasText(repository.getOwner().getGithubToken())) {
            return Optional.empty();
        }

        String repoFullName = resolveRepositoryFullName(repository);
        if (!hasText(repoFullName) || !repoFullName.contains("/")) {
            return Optional.empty();
        }

        String[] repoParts = repoFullName.split("/", 2);
        String owner = repoParts[0];
        String repo = repoParts[1];

        HttpHeaders headers = buildGitHubHeaders(repository.getOwner().getGithubToken());
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            String deploymentsUrl = UriComponentsBuilder
                    .fromHttpUrl(GITHUB_API_BASE + "/" + owner + "/" + repo + "/deployments")
                    .queryParam("per_page", 10)
                    .toUriString();

            ResponseEntity<Map[]> deploymentsResponse = restTemplate.exchange(
                    deploymentsUrl,
                    HttpMethod.GET,
                    requestEntity,
                    Map[].class
            );

            Map[] deployments = deploymentsResponse.getBody();
            if (deployments == null || deployments.length == 0) {
                return Optional.empty();
            }

            for (Map deployment : deployments) {
                Object statusesUrlObj = deployment.get("statuses_url");
                if (!(statusesUrlObj instanceof String statusesUrl) || !hasText(statusesUrl)) {
                    continue;
                }

                String statusesQueryUrl = UriComponentsBuilder
                        .fromUriString(statusesUrl)
                        .queryParam("per_page", 10)
                        .toUriString();

                ResponseEntity<Map[]> statusesResponse = restTemplate.exchange(
                        statusesQueryUrl,
                        HttpMethod.GET,
                        requestEntity,
                        Map[].class
                );

                Map[] statuses = statusesResponse.getBody();
                if (statuses == null || statuses.length == 0) {
                    continue;
                }

                for (Map status : statuses) {
                    String state = asString(status.get("state"));
                    if (!"success".equalsIgnoreCase(state)) {
                        continue;
                    }

                    String environmentUrl = asString(status.get("environment_url"));
                    if (isHttpUrl(environmentUrl)) {
                        return Optional.of(environmentUrl);
                    }

                    String targetUrl = asString(status.get("target_url"));
                    if (isHttpUrl(targetUrl)) {
                        return Optional.of(targetUrl);
                    }
                }
            }
        } catch (Exception ex) {
            log.debug("Could not resolve GitHub deployment URL for repository {}: {}", repoFullName, ex.getMessage());
        }

        return Optional.empty();
    }

    private Optional<String> findPackageHomepage(List<SubmissionFile> files) {
        for (SubmissionFile file : files) {
            String fileName = file.getFileName();
            if (fileName == null || !"package.json".equalsIgnoreCase(fileName)) {
                continue;
            }

            try {
                JsonNode root = objectMapper.readTree(file.getContent());
                JsonNode homepageNode = root.get("homepage");
                if (homepageNode != null && homepageNode.isTextual() && isHttpUrl(homepageNode.asText())) {
                    return Optional.of(homepageNode.asText());
                }
            } catch (Exception ignored) {
                log.debug("Unable to parse package.json homepage for preview generation");
            }
        }

        return Optional.empty();
    }

    private Optional<String> buildGitHubPagesUrl(tn.esprithub.server.repository.entity.Repository repository) {
        String repoFullName = resolveRepositoryFullName(repository);
        if (!hasText(repoFullName) || !repoFullName.contains("/")) {
            return Optional.empty();
        }

        String[] parts = repoFullName.split("/", 2);
        String owner = parts[0];
        String repo = parts[1];

        if ((owner + ".github.io").equalsIgnoreCase(repo)) {
            return Optional.of("https://" + owner + ".github.io/");
        }

        return Optional.of("https://" + owner + ".github.io/" + repo + "/");
    }

    private String buildCloudPreviewUrl(tn.esprithub.server.repository.entity.Repository repository,
                                        FrameworkDetection detection) {
        String repoFullName = resolveRepositoryFullName(repository);
        if (!hasText(repoFullName)) {
            return repository.getUrl();
        }

        String branch = hasText(repository.getDefaultBranch()) ? repository.getDefaultBranch() : "main";

        if (Boolean.TRUE.equals(repository.getIsPrivate())) {
            return UriComponentsBuilder
                    .fromHttpUrl("https://github.com/codespaces/new")
                    .queryParam("hide_repo_select", true)
                    .queryParam("repo", repoFullName)
                    .queryParam("ref", branch)
                    .toUriString();
        }

        return switch (detection.provider()) {
            case "STACKBLITZ" -> "https://stackblitz.com/github/" + repoFullName + "?file=README.md";
            case "GITPOD" -> "https://gitpod.io/#https://github.com/" + repoFullName;
            default -> "https://replit.com/github/" + repoFullName;
        };
    }

    private String detectProviderFromUrl(String previewUrl) {
        String lower = previewUrl.toLowerCase(Locale.ROOT);
        if (lower.contains("vercel.app")) {
            return "VERCEL";
        }
        if (lower.contains("netlify.app")) {
            return "NETLIFY";
        }
        if (lower.contains("render.com")) {
            return "RENDER";
        }
        if (lower.contains("railway.app")) {
            return "RAILWAY";
        }
        if (lower.contains("herokuapp.com")) {
            return "HEROKU";
        }
        if (lower.contains("github.io")) {
            return "GITHUB_PAGES";
        }
        if (lower.contains("stackblitz.com")) {
            return "STACKBLITZ";
        }
        if (lower.contains("replit.com")) {
            return "REPLIT";
        }
        if (lower.contains("codesandbox.io")) {
            return "CODESANDBOX";
        }
        if (lower.contains("github.com/codespaces")) {
            return "GITHUB_CODESPACES";
        }
        return "EXTERNAL_HOST";
    }

    private Set<String> extractNpmDependencies(String packageJson) {
        if (!hasText(packageJson)) {
            return Set.of();
        }

        try {
            JsonNode root = objectMapper.readTree(packageJson);
            Set<String> dependencies = new HashSet<>();
            collectDependencyKeys(root.get("dependencies"), dependencies);
            collectDependencyKeys(root.get("devDependencies"), dependencies);
            collectDependencyKeys(root.get("peerDependencies"), dependencies);
            return dependencies;
        } catch (Exception ex) {
            log.debug("Could not parse package.json dependencies: {}", ex.getMessage());
            return Set.of();
        }
    }

    private void collectDependencyKeys(JsonNode node, Set<String> collector) {
        if (node == null || !node.isObject()) {
            return;
        }

        Iterator<String> fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            collector.add(fieldNames.next().toLowerCase(Locale.ROOT));
        }
    }

    private String findFileContent(Map<String, String> contentByFile, String... fileNames) {
        for (String fileName : fileNames) {
            String normalized = fileName.toLowerCase(Locale.ROOT);
            for (Map.Entry<String, String> entry : contentByFile.entrySet()) {
                String key = entry.getKey();
                if (key.equals(normalized) || key.endsWith("/" + normalized)) {
                    return entry.getValue();
                }
            }
        }
        return "";
    }

    private boolean hasFile(Set<String> knownFiles, String fileName) {
        String normalized = fileName.toLowerCase(Locale.ROOT);
        return knownFiles.stream().anyMatch(file -> file.equals(normalized) || file.endsWith("/" + normalized));
    }

    private boolean containsIgnoreCase(String value, String marker) {
        return hasText(value) && value.toLowerCase(Locale.ROOT).contains(marker.toLowerCase(Locale.ROOT));
    }

    private String resolveRepositoryFullName(tn.esprithub.server.repository.entity.Repository repository) {
        if (hasText(repository.getFullName()) && repository.getFullName().contains("/")) {
            return repository.getFullName();
        }

        String repoUrl = repository.getUrl();
        if (!hasText(repoUrl)) {
            return null;
        }

        String normalized = repoUrl.replace("https://github.com/", "").replace("http://github.com/", "");
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }
        if (normalized.endsWith(".git")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        return normalized;
    }

    private HttpHeaders buildGitHubHeaders(String githubToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(githubToken);
        headers.setAccept(List.of(MediaType.parseMediaType("application/vnd.github+json")));
        return headers;
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.intValue();
        }

        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean isHttpUrl(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("http://") || normalized.startsWith("https://");
    }

    private record FrameworkDetection(String framework,
                                      String language,
                                      String provider,
                                      double confidence,
                                      List<String> signals) {
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
            String repoFullName = resolveRepositoryFullName(repository);
            String repoOwner = null;
            String repoName = null;

            if (hasText(repoFullName) && repoFullName.contains("/")) {
                String[] parts = repoFullName.split("/", 2);
                repoOwner = parts[0];
                repoName = parts[1];
            } else {
                repoOwner = repository.getOwner() != null ? repository.getOwner().getGithubUsername() : null;
                repoName = repository.getName();
            }

            if (!hasText(repoOwner) || !hasText(repoName)) {
                log.warn("Cannot resolve repository owner/name for submission {}", submission.getId());
                return;
            }

            String ref = hasText(submission.getCommitHash())
                    ? submission.getCommitHash()
                    : (hasText(repository.getDefaultBranch()) ? repository.getDefaultBranch() : "main");
            
            if (user.getGithubToken() == null || user.getGithubToken().isBlank()) {
                log.warn("User {} has no GitHub token, cannot fetch repository files", user.getEmail());
                return;
            }

            // Replace previous snapshot so resubmissions always reflect the selected commit.
            submissionFileRepository.deleteBySubmissionId(submission.getId());
            
            Map<String, Map<String, Object>> filesByPath = new LinkedHashMap<>();
            collectRepositoryFilesRecursively(
                    repoOwner,
                    repoName,
                    "",
                    ref,
                    user.getEmail(),
                    filesByPath,
                    new HashSet<>()
            );

            if (filesByPath.isEmpty()) {
                log.warn("No files found for submission {} at ref {}", submission.getId(), ref);
                return;
            }
            
            int savedCount = 0;
            for (Map<String, Object> fileInfo : filesByPath.values()) {
                if (saveFileFromRepository(submission, fileInfo, repoOwner, repoName, ref, user)) {
                    savedCount++;
                }
            }
            
            log.info("Saved {} files for submission {} at ref {}", savedCount, submission.getId(), ref);
            
        } catch (Exception e) {
            log.error("Error saving repository files for submission {}: {}", submission.getId(), e.getMessage());
        }
    }

    private void collectRepositoryFilesRecursively(String repoOwner,
                                                   String repoName,
                                                   String path,
                                                   String ref,
                                                   String userEmail,
                                                   Map<String, Map<String, Object>> filesByPath,
                                                   Set<String> visitedPaths) {
        if (filesByPath.size() >= MAX_SUBMISSION_FILES) {
            return;
        }

        String normalizedPath = hasText(path) ? path : "";
        if (!visitedPaths.add(normalizedPath)) {
            return;
        }

        List<Map<String, Object>> entries = studentService.getRepositoryFiles(
                repoOwner,
                repoName,
                normalizedPath,
                ref,
                userEmail
        );

        for (Map<String, Object> entry : entries) {
            if (filesByPath.size() >= MAX_SUBMISSION_FILES) {
                break;
            }

            if (entry == null) {
                continue;
            }

            String type = asString(entry.get("type")).toLowerCase(Locale.ROOT);
            String entryPath = asString(entry.get("path"));

            if (!hasText(entryPath)) {
                continue;
            }

            if ("file".equals(type)) {
                filesByPath.putIfAbsent(entryPath, entry);
                continue;
            }

            if ("dir".equals(type) || "directory".equals(type) || "tree".equals(type)) {
                collectRepositoryFilesRecursively(
                        repoOwner,
                        repoName,
                        entryPath,
                        ref,
                        userEmail,
                        filesByPath,
                        visitedPaths
                );
            }
        }
    }
    
    /**
     * Save a single file from repository
     */
    private boolean saveFileFromRepository(Submission submission, Map<String, Object> fileInfo,
                                           String repoOwner, String repoName, String ref, User user) {
        try {
            String fileName = (String) fileInfo.get("name");
            String type = (String) fileInfo.get("type");
            String path = (String) fileInfo.get("path");
            
            // Skip directories and non-code files for now
            if (!"file".equals(type)) {
                return false;
            }
            
            // Check if it's a code file
            String extension = "";
            if (fileName != null && fileName.contains(".")) {
                extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
            }
            
            if (!isCodeFile(extension)) {
                log.debug("Skipping non-code file: {}", fileName);
                return false;
            }
            
            // Get file content
            Map<String, Object> fileContent = studentService.getFileContent(
                repoOwner, repoName, path, ref, user.getEmail());
            
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
                return true;
            }
            
        } catch (Exception e) {
            log.warn("Failed to save file from repository: {}", e.getMessage());
        }

        return false;
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
