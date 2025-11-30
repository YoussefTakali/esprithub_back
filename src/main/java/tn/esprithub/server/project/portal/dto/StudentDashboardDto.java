package tn.esprithub.server.project.portal.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentDashboardDto {
    private String studentName;
    private String studentEmail;
    private String className;
    private String departmentName;
    private String levelName;
    private int totalTasks;
    private int pendingTasks;
    private int completedTasks;
    private int overdueTasks;
    private int totalProjects;
    private int activeProjects;
    private int completedProjects;
    private int totalGroups;
    private int activeGroups;
    private List<RecentActivityDto> recentActivities;
    private List<UpcomingDeadlineDto> upcomingDeadlines;
    private List<WeeklyTaskDto> weeklyTasks;
    private Map<String, Integer> taskStatusCounts;
    private Map<String, Integer> projectStatusCounts;
    private int unreadNotifications;
    private List<NotificationDto> recentNotifications;
    private double completionRate;
    private int submissionsThisMonth;
    private String currentSemester;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentActivityDto {
        private String type;
        private String title;
        private String description;
        private LocalDateTime timestamp;
        private String relatedEntityId;
        private String relatedEntityType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpcomingDeadlineDto {
        private String id;
        private String title;
        private String type;
        private LocalDateTime deadline;
        private int daysLeft;
        private String priority;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeeklyTaskDto {
        private String id;
        private String title;
        private String type;
        private LocalDateTime dueDate;
        private String status;
        private String priority;
        private boolean isOverdue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationDto {
        private String id;
        private String title;
        private String message;
        private String type;
        private LocalDateTime timestamp;
        private boolean isRead;
        private String actionUrl;
    }
}
