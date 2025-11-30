package tn.esprithub.server.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseAssignmentDto {
    private UUID id;
    private UUID courseId;
    private String courseName;
    private UUID niveauId;
    private UUID teacherId;
    private String teacherName;
}
