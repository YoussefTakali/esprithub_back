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
public class StudentGroupDto {
    private UUID id;
    private String name;
    private String description;
    private UUID projectId;
    private String projectName;
    private String projectDescription;
    private LocalDateTime projectDeadline;
    private UUID classId;
    private String className;
    private List<GroupMemberDto> members;
    private int totalMembers;
    private String myRole;
    private UUID repositoryId;
    private String repositoryName;
    private String repositoryUrl;
    private String repositoryCloneUrl;
    private boolean hasRepository;
    private List<GroupTaskDto> assignedTasks;
    private int totalTasks;
    private int completedTasks;
    private int pendingTasks;
    private double completionRate;
    private LocalDateTime lastActivity;
    private String currentStatus;
    private int totalCommits;
    private int myCommits;
    private String mostActiveContributor;
    private List<GroupAnnouncementDto> recentAnnouncements;
    private boolean hasUnreadMessages;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupMemberDto {
        private UUID id;
        private String firstName;
        private String lastName;
        private String email;
        private String fullName;
        private boolean isOnline;
        private LocalDateTime lastActive;
        private int contributionScore;
        private String role;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupTaskDto {
        private UUID id;
        private String title;
        private String description;
        private LocalDateTime dueDate;
        private String status;
        private boolean isOverdue;
        private int daysLeft;
        private boolean isCompleted;
        private double progressPercentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupAnnouncementDto {
        private UUID id;
        private String title;
        private String message;
        private String authorName;
        private LocalDateTime timestamp;
        private String type;
        private boolean isRead;
    }
}
