package tn.esprithub.server.project.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class ProjectUpdateDto {
    private String name;
    private String description;
    private LocalDateTime deadline;
    private List<UUID> classIds;
    private List<UUID> collaboratorIds;
}
