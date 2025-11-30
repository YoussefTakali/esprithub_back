package tn.esprithub.server.project.dto;

import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class GroupDto {
    private UUID id;
    private String name;
    private UUID projectId;
    private UUID classeId;
    private List<UUID> studentIds;
    private boolean repoCreated;
    private String repoUrl;
    private String repoError;
    private UUID repositoryId;
    private String repositoryFullName;
}
