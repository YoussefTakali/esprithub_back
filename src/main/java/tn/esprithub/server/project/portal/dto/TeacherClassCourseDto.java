package tn.esprithub.server.project.portal.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeacherClassCourseDto {
    private UUID classId;
    private String className;
    private String courseName;
}
