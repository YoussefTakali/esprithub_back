package tn.esprithub.server.project.portal.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tn.esprithub.server.project.enums.TaskAssignmentType;
import tn.esprithub.server.project.enums.TaskStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentTaskDto {
    private UUID id;
    private String title;
    private String description;
    private TaskAssignmentType assignmentType;
    private String type;
    private TaskStatus status;
    private LocalDateTime dueDate;
    private boolean isGraded;
    private boolean isVisible;
    private boolean isOverdue;
    private String assignedTo;
    private UUID assignedToId;
    private UUID projectId;
    private String projectName;
    private String projectDescription;
    private String teacherName;
    private String teacherEmail;
    private UUID groupId;
    private String groupName;
    private List<String> groupMembers;
    private boolean isSubmitted;
    private LocalDateTime submissionDate;
    private String submissionNotes;
    private Double grade;
    private String feedback;
    private UUID repositoryId;
    private String repositoryName;
    private String repositoryUrl;
    private String repositoryBranch;
    private int daysLeft;
    private String urgencyLevel;
    private boolean canSubmit;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<String> tags;
    private String priority;
    private int totalCollaborators;
    private int completedByCollaborators;
    private List<RelatedTaskDto> relatedTasks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelatedTaskDto {
        private UUID id;
        private String title;
        private TaskStatus status;
        private LocalDateTime dueDate;
        private boolean isCompleted;
    }
}
