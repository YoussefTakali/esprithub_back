package tn.esprithub.server.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprithub.server.project.entity.Task;
import tn.esprithub.server.project.entity.Project;
import tn.esprithub.server.project.repository.TaskRepository;
import tn.esprithub.server.project.repository.ProjectRepository;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.user.repository.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

@RestController
@RequestMapping("/api/notifications/test")
@RequiredArgsConstructor
@Slf4j
public class NotificationTestController {

    private final NotificationService notificationService;
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    /**
     * Test d'envoi de notification GitHub
     */
    @PostMapping("/github")
    public ResponseEntity<Map<String, String>> testGitHubNotification(
            @RequestBody Map<String, Object> request) {
        
        String eventType = (String) request.get("eventType");
        String repositoryName = (String) request.get("repositoryName");
        String branch = (String) request.get("branch");
        String commitMessage = (String) request.get("commitMessage");
        String authorName = (String) request.get("authorName");
        @SuppressWarnings("unchecked")
        List<String> recipientEmails = (List<String>) request.get("recipientEmails");
        
        try {
            notificationService.sendGitHubEventNotification(
                eventType, repositoryName, branch, commitMessage, authorName, recipientEmails
            );
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "GitHub notification test sent successfully");
            response.put("eventType", eventType);
            response.put("repositoryName", repositoryName);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error sending GitHub notification test", e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to send GitHub notification test: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Test d'alerte de deadline pour une tâche
     */
    @PostMapping("/task-deadline")
    public ResponseEntity<Map<String, String>> testTaskDeadlineAlert(
            @RequestBody Map<String, Object> request) {
        
        UUID taskId = UUID.fromString((String) request.get("taskId"));
        int daysUntilDeadline = (Integer) request.get("daysUntilDeadline");
        
        try {
            Task task = taskRepository.findById(taskId)
                    .orElseThrow(() -> new RuntimeException("Task not found"));
            
            // Récupérer les destinataires
            List<User> recipients = getTaskRecipients(task);
            
            notificationService.sendTaskDeadlineAlert(task, recipients, daysUntilDeadline);
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Task deadline alert test sent successfully");
            response.put("taskId", taskId.toString());
            response.put("taskTitle", task.getTitle());
            response.put("recipientsCount", String.valueOf(recipients.size()));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error sending task deadline alert test", e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to send task deadline alert test: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Test d'alerte de deadline pour un projet
     */
    @PostMapping("/project-deadline")
    public ResponseEntity<Map<String, String>> testProjectDeadlineAlert(
            @RequestBody Map<String, Object> request) {
        
        UUID projectId = UUID.fromString((String) request.get("projectId"));
        int daysUntilDeadline = (Integer) request.get("daysUntilDeadline");
        
        try {
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new RuntimeException("Project not found"));
            
            // Récupérer les destinataires
            List<User> recipients = getProjectRecipients(project);
            
            notificationService.sendProjectDeadlineAlert(project, recipients, daysUntilDeadline);
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Project deadline alert test sent successfully");
            response.put("projectId", projectId.toString());
            response.put("projectName", project.getName());
            response.put("recipientsCount", String.valueOf(recipients.size()));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error sending project deadline alert test", e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to send project deadline alert test: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Déclencher manuellement la vérification des deadlines
     */
    @PostMapping("/check-deadlines")
    public ResponseEntity<Map<String, String>> checkDeadlines() {
        try {
            notificationService.checkAndSendDeadlineAlerts();
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Deadline check completed successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error checking deadlines", e);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to check deadlines: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Obtenir la liste des tâches avec deadlines
     */
    @GetMapping("/tasks-with-deadlines")
    public ResponseEntity<List<Map<String, Object>>> getTasksWithDeadlines() {
        try {
            List<Task> tasks = taskRepository.findByStatusNotAndDueDateIsNotNull(
                tn.esprithub.server.project.enums.TaskStatus.COMPLETED
            );
            
            List<Map<String, Object>> result = tasks.stream()
                    .map(task -> {
                        Map<String, Object> taskMap = new HashMap<>();
                        taskMap.put("id", task.getId().toString());
                        taskMap.put("title", task.getTitle());
                        taskMap.put("dueDate", task.getDueDate());
                        taskMap.put("status", task.getStatus().toString());
                        return taskMap;
                    })
                    .toList();
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error getting tasks with deadlines", e);
            return ResponseEntity.internalServerError().body(List.of());
        }
    }

    /**
     * Obtenir la liste des projets avec deadlines
     */
    @GetMapping("/projects-with-deadlines")
    public ResponseEntity<List<Map<String, Object>>> getProjectsWithDeadlines() {
        try {
            List<Project> projects = projectRepository.findByDeadlineIsNotNull();
            
            List<Map<String, Object>> result = projects.stream()
                    .map(project -> {
                        Map<String, Object> projectMap = new HashMap<>();
                        projectMap.put("id", project.getId().toString());
                        projectMap.put("name", project.getName());
                        projectMap.put("deadline", project.getDeadline());
                        projectMap.put("createdBy", project.getCreatedBy() != null ? project.getCreatedBy().getEmail() : "Unknown");
                        return projectMap;
                    })
                    .toList();
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error getting projects with deadlines", e);
            return ResponseEntity.internalServerError().body(List.of());
        }
    }

    // Méthodes utilitaires pour récupérer les destinataires
    private List<User> getTaskRecipients(Task task) {
        List<User> recipients = new java.util.ArrayList<>();
        
        if (task.getAssignedToStudents() != null) {
            recipients.addAll(task.getAssignedToStudents());
        }
        
        if (task.getAssignedToGroups() != null) {
            for (var group : task.getAssignedToGroups()) {
                if (group.getStudents() != null) {
                    recipients.addAll(group.getStudents());
                }
            }
        }
        
        if (task.getAssignedToClasses() != null) {
            for (var classe : task.getAssignedToClasses()) {
                if (classe.getStudents() != null) {
                    recipients.addAll(classe.getStudents());
                }
            }
        }
        
        return recipients.stream().distinct().toList();
    }

    private List<User> getProjectRecipients(Project project) {
        List<User> recipients = new java.util.ArrayList<>();
        
        if (project.getCreatedBy() != null) {
            recipients.add(project.getCreatedBy());
        }
        
        if (project.getCollaborators() != null) {
            recipients.addAll(project.getCollaborators());
        }
        
        if (project.getGroups() != null) {
            for (var group : project.getGroups()) {
                if (group.getStudents() != null) {
                    recipients.addAll(group.getStudents());
                }
            }
        }
        
        return recipients.stream().distinct().toList();
    }
} 