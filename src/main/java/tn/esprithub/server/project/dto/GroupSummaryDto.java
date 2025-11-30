package tn.esprithub.server.project.dto;

import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class GroupSummaryDto {
    private UUID id;
    private String name;
    private List<UUID> studentIds;
}
