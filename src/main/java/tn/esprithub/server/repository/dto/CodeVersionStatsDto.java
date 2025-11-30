package tn.esprithub.server.repository.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeVersionStatsDto {
    private UUID repositoryId;
    private String repositoryName;
    private String repositoryFullName;
    
    // Basic statistics
    private long totalVersions;
    private long totalFiles;
    private long totalCommits;
    private long totalAuthors;
    private long totalBranches;
    
    // File statistics
    private Map<String, Long> filesByLanguage; // language -> count
    private Map<String, Long> versionsByLanguage; // language -> version count
    private List<FileStats> mostActiveFiles; // Files with most versions
    
    // Author statistics
    private Map<String, Long> versionsByAuthor; // author -> version count
    private List<AuthorStats> topContributors;
    
    // Timeline statistics
    private Map<String, Long> versionsByMonth; // YYYY-MM -> count
    private Map<String, Long> versionsByDayOfWeek; // Day name -> count
    private LocalDateTime firstCommitDate;
    private LocalDateTime lastCommitDate;
    
    // Code metrics
    private long totalLinesOfCode;
    private long totalLinesAdded;
    private long totalLinesDeleted;
    private Map<String, Long> linesByLanguage; // language -> total lines
    
    // Recent activity
    private List<RecentActivityDto> recentVersions;
    private double averageVersionsPerDay;
    private double averageFileSizeKB;
    
    // Branch statistics
    private Map<String, Long> versionsByBranch; // branch -> version count
    private String mostActiveBranch;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileStats {
        private String filePath;
        private String language;
        private long versionCount;
        private long totalLines;
        private LocalDateTime lastModified;
        private String lastAuthor;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthorStats {
        private String authorName;
        private String authorEmail;
        private String githubUsername;
        private long versionCount;
        private long linesAdded;
        private long linesDeleted;
        private LocalDateTime firstCommit;
        private LocalDateTime lastCommit;
        private List<String> favoriteLanguages; // Most used languages
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentActivityDto {
        private UUID versionId;
        private String commitSha;
        private String commitMessage;
        private String filePath;
        private String authorName;
        private String branchName;
        private LocalDateTime date;
        private int linesChanged;
    }
}
