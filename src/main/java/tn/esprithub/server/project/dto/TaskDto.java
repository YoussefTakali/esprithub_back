package tn.esprithub.server.project.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import tn.esprithub.server.project.enums.TaskAssignmentType;
import tn.esprithub.server.project.enums.TaskStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class TaskDto {
    private UUID id;
    private String title;
    private String description;
    private LocalDateTime dueDate;
    private List<UUID> projectIds;
    private TaskAssignmentType type;
    private List<UUID> groupIds;
    private List<UUID> studentIds;
    private List<UUID> classeIds;
    private TaskStatus status;
    private boolean graded;
    @JsonProperty("visible")
    private boolean isVisible;
}
