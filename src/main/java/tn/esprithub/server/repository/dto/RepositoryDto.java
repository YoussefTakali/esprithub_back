package tn.esprithub.server.repository.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepositoryDto {
    private String name;
    private String fullName;
    private String description;
    private String url;
    private boolean isPrivate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String defaultBranch;
    private int starCount;
    private int forkCount;
    private String language;
    private long size;
    private List<String> collaborators;
    private List<String> branches;
    private boolean hasIssues;
    private boolean hasWiki;
    private String cloneUrl;
    private String sshUrl;
}
