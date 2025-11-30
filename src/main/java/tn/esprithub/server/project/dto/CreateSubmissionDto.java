package tn.esprithub.server.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateSubmissionDto {
    @NotNull(message = "Task ID is required")
    private UUID taskId;
    
    @NotBlank(message = "Commit hash is required")
    private String commitHash;
    
    private UUID groupId; // Optional - for group submissions
    private String notes; // Optional submission notes
}
