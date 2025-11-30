package tn.esprithub.server.project.portal.controller;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.esprithub.server.academic.entity.Classe;
import tn.esprithub.server.academic.repository.ClasseRepository;
import tn.esprithub.server.project.dto.ProjectDto;
import tn.esprithub.server.project.entity.Project;
import tn.esprithub.server.project.mapper.ProjectMapper;
import tn.esprithub.server.project.portal.dto.TeacherClassCourseDto;
import tn.esprithub.server.project.portal.dto.TeacherDashboardDto;
import tn.esprithub.server.project.portal.service.TeacherClassCourseService;
import tn.esprithub.server.project.repository.ProjectRepository;
import tn.esprithub.server.repository.service.RepositoryService;
import tn.esprithub.server.user.entity.User;

@RestController
@RequestMapping("/api/teacher")
@RequiredArgsConstructor
public class TeacherController {
    private final ClasseRepository classeRepository;
    private final ProjectRepository projectRepository;
    private final TeacherClassCourseService teacherClassCourseService;
    private final RepositoryService repositoryService;

    @GetMapping("/classes")
    public ResponseEntity<List<Classe>> getMyClasses(@AuthenticationPrincipal User currentUser) {
        List<Classe> classes = classeRepository.findByTeachers_Id(currentUser.getId());
        return ResponseEntity.ok(classes);
    }

    @GetMapping("/projects")
    public ResponseEntity<List<ProjectDto>> getMyProjects(@AuthenticationPrincipal User currentUser) {
        List<Project> projects = projectRepository.findWithClassesByCreatedByOrCollaborator(currentUser.getId());
        List<ProjectDto> dtos = projects.stream().map(ProjectMapper::toDto).toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/classes-with-courses")
    public ResponseEntity<List<TeacherClassCourseDto>> getMyClassesWithCourses(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(teacherClassCourseService.getClassesWithCourses(currentUser));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<TeacherDashboardDto> getDashboard(@AuthenticationPrincipal User currentUser) {
        List<Classe> classes = classeRepository.findByTeachers_Id(currentUser.getId());
        List<Map<String, Object>> classList = new java.util.ArrayList<>();
        for (Classe c : classes) {
            Long studentCount = classeRepository.countStudentsByClasseId(c.getId());
            classList.add(Map.of(
                "id", c.getId(),
                "name", c.getNom(),
                "level", c.getNiveau() != null ? c.getNiveau().getNom() : null,
                "studentCount", studentCount
            ));
        }

        List<Project> projects = projectRepository.findWithClassesByCreatedByOrCollaborator(currentUser.getId());
        List<Map<String, Object>> projectList = new java.util.ArrayList<>();
        for (Project p : projects) {
            projectList.add(Map.of(
                "id", p.getId(),
                "name", p.getName(),
                "deadline", p.getDeadline(),
                "classIds", p.getClasses() != null ? p.getClasses().stream().map(cl -> cl.getId()).toList() : List.of()
            ));
        }

        List<Map<String, Object>> repoList = new java.util.ArrayList<>();
        int totalCommits = 0;
        List<tn.esprithub.server.repository.dto.RepositoryDto> repos = repositoryService.getTeacherRepositories(currentUser.getEmail());
        for (var repo : repos) {
            var stats = repositoryService.getRepositoryStats(repo.getFullName(), currentUser.getEmail());
            repoList.add(Map.of(
                "name", repo.getName(),
                "url", repo.getUrl(),
                "commitCount", stats.getTotalCommits(),
                "collaboratorCount", stats.getTotalCollaborators()
            ));
            totalCommits += stats.getTotalCommits();
        }

        List<Map<String, Object>> overdueStudents = List.of();

        TeacherDashboardDto dto = TeacherDashboardDto.builder()
            .classes(classList)
            .projects(projectList)
            .repositories(repoList)
            .totalCommits(totalCommits)
            .overdueStudents(overdueStudents)
            .build();
        return ResponseEntity.ok(dto);
    }
}
