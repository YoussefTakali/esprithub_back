package tn.esprithub.server.project.portal.dto;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherDashboardDto {
    private List<Map<String, Object>> classes; // id, name, level, studentCount
    private List<Map<String, Object>> projects; // id, name, status, deadline, classIds
    private List<Map<String, Object>> repositories; // name, url, commitCount, collaboratorCount
    private int totalCommits;
    private List<Map<String, Object>> overdueStudents; // studentName, class, project, task, deadline
}
