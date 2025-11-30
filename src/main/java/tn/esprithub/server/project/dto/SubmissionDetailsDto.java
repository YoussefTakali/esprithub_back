package tn.esprithub.server.project.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class SubmissionDetailsDto {
    private UUID id;
    private UUID taskId;
    private String taskTitle;
    private String taskDescription;
    private UUID userId;
    private String userName;
    private String userEmail;
    private UUID groupId;
    private String groupName;
    private String commitHash;
    private LocalDateTime submittedAt;
    private String status;
    private Double grade;
    private Double maxGrade;
    private String feedback;
    private LocalDateTime gradedAt;
    private String gradedByName;
    private Boolean isLate;
    private Integer attemptNumber;
    private String notes;
    
    // Repository and code information
    private UUID repositoryId;
    private String repositoryName;
    private String repositoryUrl;
    private List<Map<String, Object>> files; // Files in the submission
    private Map<String, Object> commitDetails; // Commit information
    
    // Calculated fields
    private Double gradePercentage;
    private Boolean isPassing;
    private Boolean isGraded;
}
