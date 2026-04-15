package tn.esprithub.server.project.service.impl;

import org.springframework.stereotype.Service;
import tn.esprithub.server.project.entity.Task;
import tn.esprithub.server.project.repository.TaskRepository;
import tn.esprithub.server.project.service.TaskService;
import tn.esprithub.server.project.dto.TaskCreateDto;
import tn.esprithub.server.project.dto.TaskUpdateDto;
import tn.esprithub.server.project.dto.TaskDto;
import tn.esprithub.server.project.mapper.TaskMapper;
import tn.esprithub.server.project.entity.Project;
import tn.esprithub.server.project.entity.Group;
import tn.esprithub.server.academic.entity.Classe;
import tn.esprithub.server.project.enums.TaskStatus;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.project.repository.ProjectRepository;
import tn.esprithub.server.project.repository.GroupRepository;
import tn.esprithub.server.academic.repository.ClasseRepository;
import tn.esprithub.server.notification.NotificationService;
import tn.esprithub.server.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class TaskServiceImpl implements TaskService {
    private final TaskRepository taskRepository;
    private final TaskMapper taskMapper;
    private final ProjectRepository projectRepository;
    private final GroupRepository groupRepository;
    private final ClasseRepository classeRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public TaskServiceImpl(TaskRepository taskRepository, TaskMapper taskMapper, ProjectRepository projectRepository, GroupRepository groupRepository, ClasseRepository classeRepository, UserRepository userRepository, NotificationService notificationService) {
        this.taskRepository = taskRepository;
        this.taskMapper = taskMapper;
        this.projectRepository = projectRepository;
        this.groupRepository = groupRepository;
        this.classeRepository = classeRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Override
    public TaskDto updateTask(UUID id, TaskUpdateDto dto) {
        Task task = taskRepository.findById(id).orElseThrow();
        List<User> previousRecipients = collectTaskRecipients(task);
        TaskStatus previousStatus = task.getStatus();
        LocalDateTime previousDueDate = task.getDueDate();
        boolean previousVisibility = task.isVisible();
        boolean previousGraded = task.isGraded();
        String previousTitle = task.getTitle();
        String previousDescription = task.getDescription();

        taskMapper.updateEntity(dto, task);
        // Update graded explicitly (Spring may not map boolean fields correctly)
        if (dto.getGraded() != null) {
            task.setGraded(dto.getGraded());
        }
        // Update projects
        if (dto.getProjectIds() != null) {
            List<Project> projects = projectRepository.findAllById(dto.getProjectIds());
            task.setProjects(new java.util.ArrayList<>(projects));
        }
        // Update groups
        if (dto.getGroupIds() != null) {
            List<Group> groups = groupRepository.findAllById(dto.getGroupIds());
            task.setAssignedToGroups(new java.util.ArrayList<>(groups));
        }
        // Update classes
        if (dto.getClasseIds() != null) {
            List<Classe> classes = classeRepository.findAllById(dto.getClasseIds());
            task.setAssignedToClasses(new java.util.ArrayList<>(classes));
        }
        // Update students
        if (dto.getStudentIds() != null) {
            List<User> students = userRepository.findAllById(dto.getStudentIds());
            task.setAssignedToStudents(new java.util.ArrayList<>(students));
        }

        Task savedTask = taskRepository.save(task);
        notifyTaskRecipientsOnUpdate(savedTask, previousRecipients, previousVisibility, previousStatus,
                previousDueDate, previousGraded, previousTitle, previousDescription);
        return taskMapper.toDto(savedTask);
    }

    @Override
    public void deleteTask(UUID id) {
        taskRepository.deleteById(id);
    }

    @Override
    public TaskDto getTaskById(UUID id) {
        return taskRepository.findById(id).map(taskMapper::toDto).orElse(null);
    }

    @Override
    public List<TaskDto> getAllTasks() {
        return taskRepository.findAll().stream().map(taskMapper::toDto).toList();
    }

    @Override
    public List<TaskDto> getTasksByClasseId(UUID classeId) {
        return taskRepository.findByAssignedToClasses_Id(classeId).stream().map(taskMapper::toDto).toList();
    }

    @Override
    public List<TaskDto> getTasksByProjectId(UUID projectId) {
        return taskRepository.findByProjects_Id(projectId).stream().map(taskMapper::toDto).toList();
    }

    @Override
    public List<TaskDto> createTasks(TaskCreateDto dto) {
        Task task = taskMapper.toEntity(dto);
        // Explicitly set graded if present in DTO
        if (dto.getGraded() != null) {
            task.setGraded(dto.getGraded());
        }
        // Set projects
        if (dto.getProjectIds() != null) {
            List<Project> projects = projectRepository.findAllById(dto.getProjectIds());
            task.setProjects(new java.util.ArrayList<>(projects));
        }
        // Set groups
        if (dto.getGroupIds() != null) {
            List<Group> groups = groupRepository.findAllById(dto.getGroupIds());
            task.setAssignedToGroups(new java.util.ArrayList<>(groups));
        }
        // Set classes
        if (dto.getClasseIds() != null) {
            List<Classe> classes = classeRepository.findAllById(dto.getClasseIds());
            task.setAssignedToClasses(new java.util.ArrayList<>(classes));
        }
        // Set students
        if (dto.getStudentIds() != null) {
            List<User> students = userRepository.findAllById(dto.getStudentIds());
            task.setAssignedToStudents(new java.util.ArrayList<>(students));
        }
        Task saved = taskRepository.save(task);
        if (saved.isVisible()) {
            List<User> recipients = collectTaskRecipients(saved);
            notificationService.createInAppNotifications(
                    recipients,
                    "New task assigned: " + saved.getTitle(),
                    buildTaskAssignmentMessage(saved),
                    "INFO"
            );
        }
        return java.util.List.of(taskMapper.toDto(saved));
    }

    private void notifyTaskRecipientsOnUpdate(
            Task task,
            List<User> previousRecipients,
            boolean previousVisibility,
            TaskStatus previousStatus,
            LocalDateTime previousDueDate,
            boolean previousGraded,
            String previousTitle,
            String previousDescription) {

        List<User> currentRecipients = collectTaskRecipients(task);
        if (currentRecipients.isEmpty() || !task.isVisible()) {
            return;
        }

        if (!previousVisibility) {
            notificationService.createInAppNotifications(
                    currentRecipients,
                    "New task assigned: " + task.getTitle(),
                    buildTaskAssignmentMessage(task),
                    "INFO"
            );
            return;
        }

        Map<UUID, User> previousById = new LinkedHashMap<>();
        for (User user : previousRecipients) {
            if (user != null && user.getId() != null) {
                previousById.put(user.getId(), user);
            }
        }

        List<User> newlyAssigned = currentRecipients.stream()
                .filter(user -> user.getId() != null && !previousById.containsKey(user.getId()))
                .toList();

        if (!newlyAssigned.isEmpty()) {
            notificationService.createInAppNotifications(
                    newlyAssigned,
                    "New task assigned: " + task.getTitle(),
                    buildTaskAssignmentMessage(task),
                    "INFO"
            );
        }

        boolean lifecycleChanged = !Objects.equals(previousStatus, task.getStatus())
                || !Objects.equals(previousDueDate, task.getDueDate())
                || previousGraded != task.isGraded()
                || !Objects.equals(previousTitle, task.getTitle())
                || !Objects.equals(previousDescription, task.getDescription());

        if (lifecycleChanged) {
            notificationService.createInAppNotifications(
                    currentRecipients,
                    "Task updated: " + task.getTitle(),
                    buildTaskUpdateMessage(task),
                    "INFO"
            );
        }
    }

    private List<User> collectTaskRecipients(Task task) {
        Map<UUID, User> recipients = new LinkedHashMap<>();

        if (task.getAssignedToStudents() != null) {
            for (User student : task.getAssignedToStudents()) {
                if (student != null && student.getId() != null) {
                    recipients.put(student.getId(), student);
                }
            }
        }

        if (task.getAssignedToGroups() != null) {
            for (Group group : task.getAssignedToGroups()) {
                if (group == null || group.getStudents() == null) {
                    continue;
                }
                for (User student : group.getStudents()) {
                    if (student != null && student.getId() != null) {
                        recipients.put(student.getId(), student);
                    }
                }
            }
        }

        if (task.getAssignedToClasses() != null) {
            for (Classe classe : task.getAssignedToClasses()) {
                if (classe == null || classe.getStudents() == null) {
                    continue;
                }
                for (User student : classe.getStudents()) {
                    if (student != null && student.getId() != null) {
                        recipients.put(student.getId(), student);
                    }
                }
            }
        }

        return new ArrayList<>(recipients.values());
    }

    private String buildTaskAssignmentMessage(Task task) {
        String dueDate = task.getDueDate() != null
                ? task.getDueDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                : "No deadline";

        return String.format("A new task '%s' has been assigned to you. Due date: %s.", task.getTitle(), dueDate);
    }

    private String buildTaskUpdateMessage(Task task) {
        String dueDate = task.getDueDate() != null
                ? task.getDueDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                : "No deadline";

        return String.format("Task '%s' was updated. Status: %s. Due date: %s.",
                task.getTitle(), task.getStatus(), dueDate);
    }
}
