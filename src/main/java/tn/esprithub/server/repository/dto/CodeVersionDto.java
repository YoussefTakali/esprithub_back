package tn.esprithub.server.repository.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeVersionDto {
    private UUID id;
    private String commitSha;
    private String commitMessage;
    private String filePath;
    private String fileContent;
    private Long fileSize;
    private Integer lineCount;
    private String language;
    private String branchName;
    private Boolean isBinary;
    private String encoding;
    private String mimeType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Repository info
    private UUID repositoryId;
    private String repositoryName;
    private String repositoryFullName;
    
    // Author info
    private UUID authorId;
    private String authorName;
    private String authorEmail;
    private String authorGithubUsername;
    
    // Parent version info (for tracking changes)
    private UUID parentVersionId;
    private String parentCommitSha;
    
    // Change statistics
    private Integer linesAdded;
    private Integer linesDeleted;
    private Integer linesModified;
    
    // Tags and status
    private List<String> tags; // Parsed from JSON string
    private String status; // ACTIVE, ARCHIVED, DELETED
    
    // Additional metadata
    private String commitUrl; // GitHub commit URL
    private String fileUrl;   // GitHub file URL at this version
}
