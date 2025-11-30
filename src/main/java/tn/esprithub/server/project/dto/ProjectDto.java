package tn.esprithub.server.project.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;

@Data
public class ProjectDto {
    private UUID id;
    private String name;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private SimpleUserDto createdBy;
    private List<SimpleUserDto> collaborators;
    private List<UUID> classIds;
    private List<UUID> groupIds;
    private List<UUID> taskIds;
    private List<ClassSummary> classes;
    private List<GroupSummaryDto> groups;
    private LocalDateTime deadline;

    @Data
    public static class SimpleUserDto {
        private UUID id;
        private String firstName;
        private String lastName;
        private String email;
    }

    @Data
    public static class ClassSummary {
        private UUID id;
        private String name;
        private String courseName;
    }

    @Data
    public static class GroupSummaryDto {
        private UUID id;
        private String name;
        private List<UUID> studentIds;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class GroupSummary extends GroupSummaryDto {}
}
