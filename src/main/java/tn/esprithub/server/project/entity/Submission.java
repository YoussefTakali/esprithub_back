package tn.esprithub.server.project.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;
import tn.esprithub.server.common.entity.BaseEntity;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "submissions")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Submission extends BaseEntity {

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "commit_hash", nullable = false, length = 40)
    private String commitHash;

    @CreationTimestamp
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private SubmissionStatus status = SubmissionStatus.SUBMITTED;

    @Column(name = "grade")
    private Double grade;

    @Column(name = "max_grade")
    private Double maxGrade;

    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "graded_at")
    private LocalDateTime gradedAt;

    @Column(name = "graded_by")
    private UUID gradedBy;

    @Builder.Default
    @Column(name = "is_late", nullable = false)
    private Boolean isLate = false;

    @Builder.Default
    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber = 1;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    public enum SubmissionStatus {
        DRAFT,
        SUBMITTED,
        GRADED,
        RETURNED,
        RESUBMITTED
    }

    // Helper methods (without entity dependencies)
    public boolean isGraded() {
        return status == SubmissionStatus.GRADED && grade != null;
    }

    public boolean isPassing() {
        if (grade == null || maxGrade == null) {
            return false;
        }
        return (grade / maxGrade) >= 0.5; // 50% passing grade
    }

    public double getGradePercentage() {
        if (grade == null || maxGrade == null) {
            return 0.0;
        }
        return (grade / maxGrade) * 100;
    }
}
