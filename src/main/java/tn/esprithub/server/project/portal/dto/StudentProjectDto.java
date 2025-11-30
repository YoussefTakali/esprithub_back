package tn.esprithub.server.project.portal.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentProjectDto {
    private UUID id;
    private String name;
    private String description;
    private LocalDateTime deadline;
    private boolean isOverdue;
    private int daysLeft;
    private String teacherName;
    private String teacherEmail;
    private UUID teacherId;
    private UUID classId;
    private String className;
    private UUID myGroupId;
    private String myGroupName;
    private List<String> myGroupMembers;
    private String myRole;
    private double completionRate;
    private double myGroupProgress;
    private String currentPhase;
    private List<ProjectTaskDto> tasks;
    private int totalTasks;
    private int completedTasks;
    private int myCompletedTasks;
    private UUID repositoryId;
    private String repositoryName;
    private String repositoryUrl;
    private boolean hasRepository;
    private int totalGroups;
    private int activeGroups;
    private List<ProjectGroupDto> allGroups;
    private List<ProjectActivityDto> recentActivities;
    private LocalDateTime lastActivity;
    private boolean isSubmitted;
    private LocalDateTime submissionDate;
    private Double grade;
    private String feedback;
    private boolean isGraded;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String status;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectTaskDto {
        private UUID id;
        private String title;
        private String description;
        private LocalDateTime dueDate;
        private String status;
        private boolean isAssignedToMe;
        private boolean isAssignedToMyGroup;
        private boolean isCompleted;
        private boolean isOverdue;
        private int daysLeft;
        private String assignmentType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectGroupDto {
        private UUID id;
        private String name;
        private int memberCount;
        private double progressPercentage;
        private String status;
        private boolean isMyGroup;
        private List<String> memberNames;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectActivityDto {
        private String type;
        private String description;
        private String actorName;
        private LocalDateTime timestamp;
        private UUID relatedEntityId;
        private String relatedEntityType;
    }
}
