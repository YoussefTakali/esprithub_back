package tn.esprithub.server.project.portal.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentSubmissionDto {
    private UUID id;
    private UUID taskId;
    private String taskTitle;
    private LocalDateTime submittedAt;
    private boolean graded;
    private Double grade;
    private Double maxGrade;
    private boolean late;
    private String status;
    private String feedback;
    private String reviewerName;
}
