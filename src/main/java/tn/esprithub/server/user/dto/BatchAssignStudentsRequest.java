package tn.esprithub.server.user.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Request body for batch assigning students to a class
 */
@Data
public class BatchAssignStudentsRequest {
    @NotEmpty
    private List<UUID> studentIds;
}
