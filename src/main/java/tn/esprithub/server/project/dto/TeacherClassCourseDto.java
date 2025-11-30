package tn.esprithub.server.project.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
public class TeacherClassCourseDto {
    private UUID classId;
    private String className;
    private String courseName;
    private List<ProjectDto> projects;
}
