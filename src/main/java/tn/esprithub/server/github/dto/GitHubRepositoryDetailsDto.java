package tn.esprithub.server.github.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitHubRepositoryDetailsDto {
    private String id;
    private String name;
    private String fullName;
    private String description;
    private String url;
    private String htmlUrl;
    private String cloneUrl;
    private String sshUrl;
    private String gitUrl;
    private Boolean isPrivate;
    private String defaultBranch;
    private Integer size;
    private String language;
    private Integer stargazersCount;
    private Integer watchersCount;
    private Integer forksCount;
    private Integer openIssuesCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime pushedAt;
    
    // Owner information
    private OwnerDto owner;
    
    // Repository stats
    private List<BranchDto> branches;
    private List<CommitDto> recentCommits;
    private List<ContributorDto> contributors;
    private Map<String, Integer> languages;
    private List<ReleaseDto> releases;
    private List<FileDto> files;
    
    // Group/Project context
    private String groupId;
    private String groupName;
    private String projectId;
    private String projectName;
    private String accessLevel;
    private Boolean canPush;
    private Boolean canPull;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OwnerDto {
        private String login;
        private String name;
        private String avatarUrl;
        private String type;
        private String htmlUrl;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BranchDto {
        private String name;
        private String sha;
        private Boolean isProtected;
        private CommitDto lastCommit;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommitDto {
        private String sha;
        private String message;
        private String authorName;
        private String authorEmail;
        private String authorAvatarUrl;
        private LocalDateTime date;
        private String htmlUrl;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContributorDto {
        private String login;
        private String name;
        private String avatarUrl;
        private Integer contributions;
        private String htmlUrl;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReleaseDto {
        private String tagName;
        private String name;
        private String body;
        private Boolean isDraft;
        private Boolean isPrerelease;
        private LocalDateTime publishedAt;
        private String htmlUrl;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileDto {
        private String name;
        private String path;
        private String type; // "file" or "dir"
        private String sha;
        private Integer size;
        private String downloadUrl;
        private String htmlUrl;
        private LocalDateTime lastModified;
        private String lastCommitMessage;
        private String lastCommitSha;
        private String lastCommitAuthor;
    }
}