package tn.esprithub.server.project.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.esprithub.server.common.exception.BusinessException;
import tn.esprithub.server.project.dto.*;
import tn.esprithub.server.project.service.SubmissionService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/submissions")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"${app.cors.allowed-origins}"})
public class SubmissionController {

    private final SubmissionService submissionService;

    /**
     * Create a new submission (for students)
     */
    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<SubmissionDto> createSubmission(
            @Valid @RequestBody CreateSubmissionDto createDto,
            Authentication authentication) {
        log.info("Creating submission for task: {} by user: {}", createDto.getTaskId(), authentication.getName());
        
        try {
            SubmissionDto submission = submissionService.createSubmission(createDto, authentication.getName());
            return ResponseEntity.ok(submission);
        } catch (BusinessException e) {
            log.error("Error creating submission: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error creating submission", e);
            throw new BusinessException("Failed to create submission: " + e.getMessage());
        }
    }

    /**
     * Get submission details with code files
     */
    @GetMapping("/{submissionId}")
    @PreAuthorize("hasRole('STUDENT') or hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<SubmissionDetailsDto> getSubmissionDetails(@PathVariable UUID submissionId) {
        log.info("Getting submission details for: {}", submissionId);
        
        try {
            SubmissionDetailsDto submission = submissionService.getSubmissionDetails(submissionId);
            return ResponseEntity.ok(submission);
        } catch (BusinessException e) {
            log.error("Error getting submission details: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error getting submission details", e);
            throw new BusinessException("Failed to get submission details: " + e.getMessage());
        }
    }

    /**
     * Grade a submission (for teachers and admins)
     */
    @PutMapping("/{submissionId}/grade")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<SubmissionDto> gradeSubmission(
            @PathVariable UUID submissionId,
            @Valid @RequestBody GradeSubmissionDto gradeDto,
            Authentication authentication) {
        log.info("Grading submission: {} by: {}", submissionId, authentication.getName());
        
        try {
            SubmissionDto submission = submissionService.gradeSubmission(submissionId, gradeDto, authentication.getName());
            return ResponseEntity.ok(submission);
        } catch (BusinessException e) {
            log.error("Error grading submission: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error grading submission", e);
            throw new BusinessException("Failed to grade submission: " + e.getMessage());
        }
    }

    /**
     * Get submissions for a task (for teachers/admins)
     */
    @GetMapping("/task/{taskId}")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<List<SubmissionDto>> getSubmissionsForTask(@PathVariable UUID taskId) {
        log.info("Getting submissions for task: {}", taskId);
        
        try {
            List<SubmissionDto> submissions = submissionService.getSubmissionsForTask(taskId);
            return ResponseEntity.ok(submissions);
        } catch (BusinessException e) {
            log.error("Error getting submissions for task: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error getting submissions for task", e);
            throw new BusinessException("Failed to get submissions for task: " + e.getMessage());
        }
    }

    /**
     * Get student's own submissions
     */
    @GetMapping("/my-submissions")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Page<SubmissionDto>> getMySubmissions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        log.info("Getting submissions for student: {}", authentication.getName());
        
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<SubmissionDto> submissions = submissionService.getSubmissionsForUser(authentication.getName(), pageable);
            return ResponseEntity.ok(submissions);
        } catch (BusinessException e) {
            log.error("Error getting student submissions: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error getting student submissions", e);
            throw new BusinessException("Failed to get student submissions: " + e.getMessage());
        }
    }

    /**
     * Get available tasks for submission (for students)
     */
    @GetMapping("/available-tasks")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<Map<String, Object>>> getAvailableTasksForSubmission(Authentication authentication) {
        log.info("Getting available tasks for student: {}", authentication.getName());
        
        try {
            List<Map<String, Object>> tasks = submissionService.getAvailableTasksForStudent(authentication.getName());
            return ResponseEntity.ok(tasks);
        } catch (BusinessException e) {
            log.error("Error getting available tasks: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error getting available tasks", e);
            throw new BusinessException("Failed to get available tasks: " + e.getMessage());
        }
    }

    /**
     * Health check for submission controller
     */
    @GetMapping("/health")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> healthCheck() {
        log.info("Submission controller health check");
        
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "controller", "SubmissionController",
            "timestamp", java.time.LocalDateTime.now().toString()
        ));
    }

    /**
     * Get submissions for a teacher (tasks they created or projects they collaborate on)
     */
    @GetMapping("/teacher-submissions")
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<Page<SubmissionDto>> getTeacherSubmissions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        log.info("Getting submissions for teacher: {}", authentication.getName());
        
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<SubmissionDto> submissions = submissionService.getSubmissionsForTeacher(authentication.getName(), pageable);
            return ResponseEntity.ok(submissions);
        } catch (BusinessException e) {
            log.error("Error getting teacher submissions: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error getting teacher submissions", e);
            throw new BusinessException("Failed to get teacher submissions: " + e.getMessage());
        }
    }
}
