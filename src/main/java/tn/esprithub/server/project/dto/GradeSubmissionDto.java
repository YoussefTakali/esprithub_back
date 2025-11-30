package tn.esprithub.server.project.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class GradeSubmissionDto {
    @Min(value = 0, message = "Grade cannot be negative")
    @Max(value = 100, message = "Grade cannot exceed 100")
    private Double grade;
    
    @Min(value = 0, message = "Max grade cannot be negative")
    @Max(value = 100, message = "Max grade cannot exceed 100")
    private Double maxGrade;
    
    private String feedback;
}
