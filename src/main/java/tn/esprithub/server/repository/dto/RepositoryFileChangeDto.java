package tn.esprithub.server.repository.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepositoryFileChangeDto {
    private String id; // UUID as String
    private String filePath;
    private String fileName;
    private String changeType;
    private String previousFilePath;
    private Integer additions;
    private Integer deletions;
    private Integer changes;
    private String sha;
    private String previousSha;
    private String patch;
    private String githubUrl;
    private String commitId; // UUID as String
    private String repositoryId; // UUID as String
}
