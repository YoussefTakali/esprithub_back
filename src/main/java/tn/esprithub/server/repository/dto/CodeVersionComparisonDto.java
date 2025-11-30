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
public class CodeVersionComparisonDto {
    private UUID version1Id;
    private UUID version2Id;
    
    // Version 1 info
    private String version1CommitSha;
    private String version1CommitMessage;
    private LocalDateTime version1Date;
    private String version1Author;
    
    // Version 2 info
    private String version2CommitSha;
    private String version2CommitMessage;
    private LocalDateTime version2Date;
    private String version2Author;
    
    // File info
    private String filePath;
    private String language;
    
    // Comparison results
    private String diffContent; // Unified diff format
    private List<LineDiff> lineDiffs;
    
    // Statistics
    private int totalLinesAdded;
    private int totalLinesDeleted;
    private int totalLinesModified;
    private int totalLinesUnchanged;
    private double changePercentage;
    
    // Metadata
    private boolean isRenamed;
    private String oldFilePath;
    private String newFilePath;
    private boolean isBinaryFile;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineDiff {
        private int lineNumber;
        private String type; // ADDED, DELETED, MODIFIED, UNCHANGED
        private String oldContent;
        private String newContent;
        private String context; // Surrounding lines for context
    }
}
