package tn.esprithub.server.project.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import tn.esprithub.server.project.enums.TaskAssignmentType;
import tn.esprithub.server.project.enums.TaskStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class TaskUpdateDto {
    private String title;
    private String description;
    private LocalDateTime dueDate;
    private TaskAssignmentType type;
    private UUID groupId;
    private UUID studentId;
    private UUID classeId;
    private TaskStatus status;
    @JsonProperty("isGraded")
    private Boolean graded;
    private List<UUID> projectIds;
    private List<UUID> groupIds;
    private List<UUID> studentIds;
    private List<UUID> classeIds;
    @JsonProperty("visible")
    private Boolean isVisible;
}
