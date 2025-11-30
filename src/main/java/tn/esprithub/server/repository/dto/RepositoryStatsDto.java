package tn.esprithub.server.repository.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepositoryStatsDto {
    private String repositoryName;
    private String fullName;
    private int totalCommits;
    private int totalBranches;
    private int totalCollaborators;
    private int totalFiles;
    private long totalSize;
    private LocalDateTime lastActivity;
    private String mostActiveContributor;
    private Map<String, Integer> languageStats;
    private List<CommitDto> recentCommits;
    private List<BranchActivityDto> branchActivity;
    private int openIssues;
    private int closedIssues;
    private int openPullRequests;
    private int mergedPullRequests;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommitDto {
        private String sha;
        private String message;
        private String author;
        private LocalDateTime date;
        private String url;
        private String avatarUrl;
    }
    
    
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BranchActivityDto {
        private String branchName;
        private int commitCount;
        private LocalDateTime lastCommit;
        private String lastCommitAuthor;
        private boolean isProtected;
    }
}
