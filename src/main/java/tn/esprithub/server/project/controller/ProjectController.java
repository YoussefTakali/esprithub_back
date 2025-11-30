package tn.esprithub.server.project.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tn.esprithub.server.project.dto.ProjectCreateDto;
import tn.esprithub.server.project.dto.ProjectDto;
import tn.esprithub.server.project.dto.TeacherClassCourseDto;
import tn.esprithub.server.project.dto.ProjectUpdateDto;
import tn.esprithub.server.project.entity.Project;
import tn.esprithub.server.project.mapper.ProjectMapper;
import tn.esprithub.server.project.service.ProjectService;
import tn.esprithub.server.academic.entity.Classe;
import tn.esprithub.server.academic.repository.ClasseRepository;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.user.repository.UserRepository;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectService projectService;
    private final ClasseRepository classeRepository;
    private final UserRepository userRepository;

    public ProjectController(ProjectService projectService, ClasseRepository classeRepository, UserRepository userRepository) {
        this.projectService = projectService;
        this.classeRepository = classeRepository;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<ProjectDto> createProject(@RequestBody ProjectCreateDto dto, @AuthenticationPrincipal User currentUser) {
        Project project = new Project();
        project.setName(dto.getName());
        project.setDescription(dto.getDescription());
        project.setDeadline(dto.getDeadline());
        project.setCreatedBy(currentUser);
        if (dto.getClassIds() != null && !dto.getClassIds().isEmpty()) {
            List<Classe> classes = classeRepository.findAllById(dto.getClassIds());
            project.setClasses(classes);
        }
        if (dto.getCollaboratorIds() != null && !dto.getCollaboratorIds().isEmpty()) {
            project.setCollaborators(userRepository.findAllById(dto.getCollaboratorIds()));
        }
        Project created = projectService.createProject(project);
        return ResponseEntity.ok(ProjectMapper.toDto(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProjectDto> updateProject(@PathVariable UUID id, @RequestBody ProjectUpdateDto dto) {
        Project updated = projectService.updateProject(id, dto);
        return ResponseEntity.ok(ProjectMapper.toDto(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable UUID id) {
        projectService.deleteProject(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectDto> getProjectById(@PathVariable UUID id) {
        Project found = projectService.getProjectById(id);
        return ResponseEntity.ok(ProjectMapper.toDto(found));
    }

    @GetMapping
    public ResponseEntity<List<ProjectDto>> getAllProjects() {
        List<Project> projects = projectService.getAllProjects();
        List<ProjectDto> dtos = projects.stream().map(ProjectMapper::toDto).toList();
        return ResponseEntity.ok(dtos);
    }

    // Add a collaborator to a project
    @PostMapping("/{projectId}/collaborators/{userId}")
    public ResponseEntity<ProjectDto> addCollaborator(@PathVariable UUID projectId, @PathVariable UUID userId) {
        Project updated = projectService.addCollaborator(projectId, userId);
        return ResponseEntity.ok(ProjectMapper.toDto(updated));
    }

    // Remove a collaborator from a project
    @DeleteMapping("/{projectId}/collaborators/{userId}")
    public ResponseEntity<ProjectDto> removeCollaborator(@PathVariable UUID projectId, @PathVariable UUID userId) {
        Project updated = projectService.removeCollaborator(projectId, userId);
        return ResponseEntity.ok(ProjectMapper.toDto(updated));
    }

    // Get teacher's classes with course names for project creation
    @GetMapping("/my-classes-courses")
    public ResponseEntity<List<TeacherClassCourseDto>> getMyClassesWithCourses(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(projectService.getMyClassesWithCourses(currentUser));
    }
}
