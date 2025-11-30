package tn.esprithub.server.project.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class SubmissionDto {
    private UUID id;
    private UUID taskId;
    private String taskTitle;
    private UUID userId;
    private String userName;
    private String userEmail;
    private UUID groupId;
    private String groupName;
    private String commitHash;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime submittedAt;
    
    private String status;
    private Double grade;
    private Double maxGrade;
    private String feedback;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime gradedAt;
    
    private String gradedByName;
    private Boolean isLate;
    private Integer attemptNumber;
    private String notes;
    
    // Calculated fields
    private Double gradePercentage;
    private Boolean isPassing;
    private Boolean isGraded;
}
