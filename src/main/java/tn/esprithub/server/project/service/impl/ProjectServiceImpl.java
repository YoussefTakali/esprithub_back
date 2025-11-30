package tn.esprithub.server.project.service.impl;

import org.springframework.stereotype.Service;
import tn.esprithub.server.project.entity.Project;
import tn.esprithub.server.project.repository.ProjectRepository;
import tn.esprithub.server.project.service.ProjectService;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.user.repository.UserRepository;
import tn.esprithub.server.project.dto.TeacherClassCourseDto;
import tn.esprithub.server.academic.repository.ClasseRepository;
import tn.esprithub.server.academic.repository.CourseAssignmentRepository;
import tn.esprithub.server.academic.entity.Classe;
import tn.esprithub.server.academic.entity.CourseAssignment;
import tn.esprithub.server.project.dto.ProjectUpdateDto;
import tn.esprithub.server.project.dto.ProjectDto;
import tn.esprithub.server.project.mapper.ProjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProjectServiceImpl implements ProjectService {
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ClasseRepository classeRepository;
    private final CourseAssignmentRepository courseAssignmentRepository;

    public ProjectServiceImpl(ProjectRepository projectRepository, UserRepository userRepository, ClasseRepository classeRepository, CourseAssignmentRepository courseAssignmentRepository) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.classeRepository = classeRepository;
        this.courseAssignmentRepository = courseAssignmentRepository;
    }

    @Override
    public Project createProject(Project project) {
        if (project.getClasses() != null && !project.getClasses().isEmpty()) {
            List<Classe> managedClasses = classeRepository.findAllById(
                project.getClasses().stream().map(Classe::getId).toList()
            );
            project.setClasses(managedClasses);
        }
        return projectRepository.save(project);
    }

    @Override
    public Project updateProject(UUID id, ProjectUpdateDto dto) {
        Project existing = projectRepository.findById(id).orElseThrow();
        if (dto.getName() != null) existing.setName(dto.getName());
        if (dto.getDescription() != null) existing.setDescription(dto.getDescription());
        if (dto.getDeadline() != null) existing.setDeadline(dto.getDeadline());
        if (dto.getClassIds() != null) {
            List<Classe> classes = classeRepository.findAllById(dto.getClassIds());
            existing.setClasses(classes);
        }
        if (dto.getCollaboratorIds() != null) {
            existing.setCollaborators(userRepository.findAllById(dto.getCollaboratorIds()));
        }
        return projectRepository.save(existing);
    }

    @Override
    public Project updateProject(UUID id, Project project) {
        throw new UnsupportedOperationException("Use updateProject(UUID, ProjectUpdateDto) instead.");
    }

    @Override
    public void deleteProject(UUID id) {
        projectRepository.deleteById(id);
    }

    @Override
    public Project getProjectById(UUID id) {
        Optional<Project> project = projectRepository.findById(id);
        return project.orElse(null);
    }

    @Override
    public List<Project> getAllProjects() {
        return projectRepository.findAll();
    }

    @Override
    public Project addCollaborator(UUID projectId, UUID userId) {
        Project project = projectRepository.findById(projectId).orElseThrow();
        User user = userRepository.findById(userId).orElseThrow();
        if (!project.getCollaborators().contains(user)) {
            project.getCollaborators().add(user);
            projectRepository.save(project);
        }
        return project;
    }

    @Override
    public Project removeCollaborator(UUID projectId, UUID userId) {
        Project project = projectRepository.findById(projectId).orElseThrow();
        User user = userRepository.findById(userId).orElseThrow();
        if (project.getCollaborators().contains(user)) {
            project.getCollaborators().remove(user);
            projectRepository.save(project);
        }
        return project;
    }

    @Override
    public List<TeacherClassCourseDto> getMyClassesWithCourses(User teacher) {
        List<CourseAssignment> assignments = courseAssignmentRepository.findByTeacher_Id(teacher.getId());
        List<TeacherClassCourseDto> result = new ArrayList<>();
        // Fetch all projects for this teacher (created or collaborated)
        List<Project> allProjects = projectRepository.findWithClassesByCreatedByOrCollaborator(teacher.getId());
        for (CourseAssignment assignment : assignments) {
            List<Classe> classes = classeRepository.findByNiveauId(assignment.getNiveau().getId());
            for (Classe classe : classes) {
                // Only include projects where this class is the primary (first) class
                List<ProjectDto> projectDtos = allProjects.stream()
                    .filter(p -> p.getClasses() != null && !p.getClasses().isEmpty() && p.getClasses().get(0).getId().equals(classe.getId()))
                    .map(ProjectMapper::toDto)
                    .toList();
                result.add(new TeacherClassCourseDto(classe.getId(), classe.getNom(), assignment.getCourse().getName(), projectDtos));
            }
        }
        return result;
    }
}
