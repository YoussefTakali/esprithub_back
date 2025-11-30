package tn.esprithub.server.project.service;

import tn.esprithub.server.project.entity.Project;
import tn.esprithub.server.project.dto.TeacherClassCourseDto;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.project.dto.ProjectUpdateDto;

import java.util.List;
import java.util.UUID;

public interface ProjectService {
    Project createProject(Project project);
    Project updateProject(UUID id, Project project);
    void deleteProject(UUID id);
    Project getProjectById(UUID id);
    List<Project> getAllProjects();
    Project addCollaborator(UUID projectId, UUID userId);
    Project removeCollaborator(UUID projectId, UUID userId);
    List<TeacherClassCourseDto> getMyClassesWithCourses(User teacher);
    Project updateProject(UUID id, ProjectUpdateDto dto);
}
