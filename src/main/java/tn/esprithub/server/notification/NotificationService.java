package tn.esprithub.server.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tn.esprithub.server.email.EmailService;
import tn.esprithub.server.project.entity.Task;
import tn.esprithub.server.project.entity.Project;
import tn.esprithub.server.project.repository.TaskRepository;
import tn.esprithub.server.project.repository.ProjectRepository;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.user.repository.UserRepository;
import tn.esprithub.server.project.enums.TaskStatus;
import tn.esprithub.server.notification.entity.Notification;
import tn.esprithub.server.notification.repository.NotificationRepository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final EmailService emailService;
    private final TeamsNotificationService teamsNotificationService;
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    // Configuration des seuils d'alerte (en jours)
    private static final int CRITICAL_DEADLINE_DAYS = 1;
    private static final int WARNING_DEADLINE_DAYS = 3;
    private static final int INFO_DEADLINE_DAYS = 7;

    public List<Notification> getNotificationsForUser(String email, boolean unreadOnly, int limit) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));

        List<Notification> notifications = unreadOnly
            ? notificationRepository.findByStudentAndIsReadFalseOrderByTimestampDesc(user)
            : notificationRepository.findByStudentOrderByTimestampDesc(user);

        int safeLimit = Math.max(1, Math.min(limit, 200));
        return notifications.stream().limit(safeLimit).toList();
    }

    public long getUnreadCountForUser(String email) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));
        return notificationRepository.countByStudentAndIsReadFalse(user);
    }

    public void markNotificationAsRead(String email, Long notificationId) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));

        Notification notification = notificationRepository.findByIdAndStudent(notificationId, user)
            .orElseThrow(() -> new RuntimeException("Notification not found"));

        if (!notification.isRead()) {
            notification.setRead(true);
            notificationRepository.save(notification);
        }
    }

    public int markAllNotificationsAsRead(String email) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));

        List<Notification> unreadNotifications = notificationRepository.findByStudentAndIsReadFalseOrderByTimestampDesc(user);
        unreadNotifications.forEach(notification -> notification.setRead(true));
        notificationRepository.saveAll(unreadNotifications);
        return unreadNotifications.size();
    }

    public void markNotificationAsSeen(String email, Long notificationId) {
        markNotificationAsRead(email, notificationId);
    }

    public int markAllNotificationsAsSeen(String email) {
        return markAllNotificationsAsRead(email);
    }

    public void deleteNotification(String email, Long notificationId) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));

        long deleted = notificationRepository.deleteByIdAndStudent(notificationId, user);
        if (deleted == 0) {
            throw new RuntimeException("Notification not found");
        }
    }

    public int deleteAllNotifications(String email) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));

        return (int) notificationRepository.deleteByStudent(user);
    }

    public int createInAppNotifications(Collection<User> recipients, String title, String message, String type) {
        return createInAppNotifications(recipients, title, message, type, null);
    }

    public int createInAppNotifications(Collection<User> recipients, String title, String message, String type, String targetUrl) {
        if (recipients == null || recipients.isEmpty()) {
            return 0;
        }

        Map<java.util.UUID, User> uniqueRecipients = new LinkedHashMap<>();
        for (User recipient : recipients) {
            if (recipient == null || recipient.getId() == null) {
                continue;
            }
            uniqueRecipients.put(recipient.getId(), recipient);
        }

        if (uniqueRecipients.isEmpty()) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        List<Notification> notifications = uniqueRecipients.values().stream()
            .map(recipient -> {
                Notification notif = new Notification();
                notif.setTitle(title);
                notif.setMessage(message);
                notif.setType(type);
                notif.setTargetUrl(targetUrl);
                notif.setTimestamp(now);
                notif.setRead(false);
                notif.setStudent(recipient);
                return notif;
            })
            .toList();

        notificationRepository.saveAll(notifications);
        return notifications.size();
    }

    /**
     * Envoie une notification pour un événement GitHub (push/pull)
     */
    public void sendGitHubEventNotification(String eventType, String repositoryName, 
                                          String branch, String commitMessage, 
                                          String authorName, List<String> recipientEmails) {
        
        String subject = String.format("GitHub %s - %s", eventType.toUpperCase(), repositoryName);
        
        String emailContent = buildGitHubEventEmailContent(eventType, repositoryName, branch, commitMessage, authorName);
        String teamsContent = buildGitHubEventTeamsContent(eventType, repositoryName, branch, commitMessage, authorName);
        
        // Envoi par email
        for (String email : recipientEmails) {
            try {
                emailService.sendNotificationEmail(email, subject, emailContent);
                log.info("GitHub event notification sent to: {}", email);
                // --- Add: Persist notification for student ---
                userRepository.findByEmail(email).ifPresent(student -> {
                    Notification notif = new Notification();
                    notif.setTitle(subject);
                    notif.setMessage(commitMessage);
                    notif.setType("INFO");
                    notif.setTimestamp(LocalDateTime.now());
                    notif.setRead(false);
                    notif.setStudent(student);
                    notificationRepository.save(notif);
                });
            } catch (Exception e) {
                log.error("Failed to send GitHub event email to: {}", email, e);
            }
        }
        
        // Envoi par Teams
        try {
            teamsNotificationService.sendNotification(teamsContent);
            log.info("GitHub event notification sent to Teams");
        } catch (Exception e) {
            log.error("Failed to send GitHub event notification to Teams", e);
        }
    }

    /**
     * Envoie une alerte de deadline pour une tâche
     */
    public void sendTaskDeadlineAlert(Task task, List<User> recipients, int daysUntilDeadline) {
        String urgency = getUrgencyLevel(daysUntilDeadline);
        String subject = String.format("⚠️ Deadline Alert - %s (%s)", task.getTitle(), urgency);
        
        String emailContent = buildTaskDeadlineEmailContent(task, daysUntilDeadline, urgency);
        String teamsContent = buildTaskDeadlineTeamsContent(task, daysUntilDeadline, urgency);
        
        // Envoi par email
        for (User recipient : recipients) {
            try {
                emailService.sendNotificationEmail(recipient.getEmail(), subject, emailContent);
                log.info("Task deadline alert sent to: {}", recipient.getEmail());

                Notification notif = new Notification();
                notif.setTitle(subject);
                notif.setMessage(task.getTitle() + " deadline in " + daysUntilDeadline + " day(s)");
                notif.setType("WARNING");
                notif.setTimestamp(LocalDateTime.now());
                notif.setRead(false);
                notif.setStudent(recipient);
                notificationRepository.save(notif);
            } catch (Exception e) {
                log.error("Failed to send task deadline email to: {}", recipient.getEmail(), e);
            }
        }
        
        // Envoi par Teams
        try {
            teamsNotificationService.sendNotification(teamsContent);
            log.info("Task deadline alert sent to Teams");
        } catch (Exception e) {
            log.error("Failed to send task deadline notification to Teams", e);
        }
    }

    /**
     * Envoie une alerte de deadline pour un projet
     */
    public void sendProjectDeadlineAlert(Project project, List<User> recipients, int daysUntilDeadline) {
        String urgency = getUrgencyLevel(daysUntilDeadline);
        String subject = String.format("🚨 Project Deadline Alert - %s (%s)", project.getName(), urgency);
        
        String emailContent = buildProjectDeadlineEmailContent(project, daysUntilDeadline, urgency);
        String teamsContent = buildProjectDeadlineTeamsContent(project, daysUntilDeadline, urgency);
        
        // Envoi par email
        for (User recipient : recipients) {
            try {
                emailService.sendNotificationEmail(recipient.getEmail(), subject, emailContent);
                log.info("Project deadline alert sent to: {}", recipient.getEmail());

                Notification notif = new Notification();
                notif.setTitle(subject);
                notif.setMessage(project.getName() + " deadline in " + daysUntilDeadline + " day(s)");
                notif.setType("WARNING");
                notif.setTimestamp(LocalDateTime.now());
                notif.setRead(false);
                notif.setStudent(recipient);
                notificationRepository.save(notif);
            } catch (Exception e) {
                log.error("Failed to send project deadline email to: {}", recipient.getEmail(), e);
            }
        }
        
        // Envoi par Teams
        try {
            teamsNotificationService.sendNotification(teamsContent);
            log.info("Project deadline alert sent to Teams");
        } catch (Exception e) {
            log.error("Failed to send project deadline notification to Teams", e);
        }
    }

    /**
     * Vérifie et envoie les alertes de deadline (exécuté automatiquement)
     */
    @Scheduled(cron = "0 0 9 * * *") // Tous les jours à 9h00
    public void checkAndSendDeadlineAlerts() {
        log.info("Starting daily deadline check...");
        
        LocalDateTime now = LocalDateTime.now();
        
        // Vérifier les tâches
        checkTaskDeadlines(now);
        
        // Vérifier les projets
        checkProjectDeadlines(now);
        
        log.info("Daily deadline check completed");
    }

    private void checkTaskDeadlines(LocalDateTime now) {
        List<Task> activeTasks = taskRepository.findByStatusNotAndDueDateIsNotNull(TaskStatus.COMPLETED);
        
        for (Task task : activeTasks) {
            if (task.getDueDate() == null) continue;
            
            long daysUntilDeadline = ChronoUnit.DAYS.between(now, task.getDueDate());
            
            // Envoyer des alertes selon les seuils
            if (daysUntilDeadline == CRITICAL_DEADLINE_DAYS || 
                daysUntilDeadline == WARNING_DEADLINE_DAYS || 
                daysUntilDeadline == INFO_DEADLINE_DAYS) {
                
                List<User> recipients = getTaskRecipients(task);
                if (!recipients.isEmpty()) {
                    sendTaskDeadlineAlert(task, recipients, (int) daysUntilDeadline);
                }
            }
        }
    }

    private void checkProjectDeadlines(LocalDateTime now) {
        List<Project> activeProjects = projectRepository.findByDeadlineIsNotNull();
        
        for (Project project : activeProjects) {
            if (project.getDeadline() == null) continue;
            
            long daysUntilDeadline = ChronoUnit.DAYS.between(now, project.getDeadline());
            
            // Envoyer des alertes selon les seuils
            if (daysUntilDeadline == CRITICAL_DEADLINE_DAYS || 
                daysUntilDeadline == WARNING_DEADLINE_DAYS || 
                daysUntilDeadline == INFO_DEADLINE_DAYS) {
                
                List<User> recipients = getProjectRecipients(project);
                if (!recipients.isEmpty()) {
                    sendProjectDeadlineAlert(project, recipients, (int) daysUntilDeadline);
                }
            }
        }
    }

    private List<User> getTaskRecipients(Task task) {
        // Récupérer tous les étudiants assignés à cette tâche
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
        
        // Ajouter le créateur du projet
        if (project.getCreatedBy() != null) {
            recipients.add(project.getCreatedBy());
        }
        
        // Ajouter les collaborateurs
        if (project.getCollaborators() != null) {
            recipients.addAll(project.getCollaborators());
        }
        
        // Ajouter les étudiants des groupes du projet
        if (project.getGroups() != null) {
            for (var group : project.getGroups()) {
                if (group.getStudents() != null) {
                    recipients.addAll(group.getStudents());
                }
            }
        }
        
        return recipients.stream().distinct().toList();
    }

    private String getUrgencyLevel(int daysUntilDeadline) {
        if (daysUntilDeadline <= CRITICAL_DEADLINE_DAYS) return "CRITICAL";
        if (daysUntilDeadline <= WARNING_DEADLINE_DAYS) return "WARNING";
        return "INFO";
    }

    // Méthodes de construction du contenu des emails et Teams
    private String buildGitHubEventEmailContent(String eventType, String repositoryName, 
                                              String branch, String commitMessage, String authorName) {
        String now = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        return String.format(
            "<div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;\">" +
            "<h2 style=\"color: #0366d6;\">GitHub Event Notification</h2>" +
            "<div style=\"background-color: #f6f8fa; padding: 15px; border-radius: 6px; margin: 20px 0;\">" +
            "<p><strong>Type d'événement :</strong> %s</p>" +
            "<p><strong>Nom du dépôt :</strong> %s</p>" +
            "<p><strong>Branche concernée :</strong> %s</p>" +
            "<p><strong>Auteur du push :</strong> %s</p>" +
            "<p><strong>Date et heure du push :</strong> %s</p>" +
            "<p><strong>Message du commit :</strong> %s</p>" +
            "</div>" +
            "<div style=\"margin-top: 20px; color: #333;\">" +
            "<p>Un nouvel événement <b>GitHub</b> a été détecté sur le dépôt <b>%s</b>.<br>" +
            "<b>%s</b> a effectué un push sur la branche <b>%s</b> à la date <b>%s</b>.</p>" +
            "<p>Détail du commit : <i>%s</i></p>" +
            "</div>" +
            "<p style=\"color: #888; font-size: 12px;\">Ceci est une notification automatique envoyée par espriHUb.</p>" +
            "</div>",
            eventType, repositoryName, branch, authorName, now, commitMessage, repositoryName, authorName, branch, now, commitMessage
        );
    }

    private String buildGitHubEventTeamsContent(String eventType, String repositoryName, 
                                              String branch, String commitMessage, String authorName) {
        // Format de date/heure
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        // Placeholder pour le nom de la tâche (à remplacer si tu as le mapping commit/tâche)
        String taskName = "[Nom de la tâche]";
        // Message markdown simple pour Teams
        String content = String.format(
            "🔔 Nouveau push GitHub !\n" +
            "- Auteur : %s\n" +
            "- Date : %s\n" +
            "- Tâche : %s\n" +
            "- Repo : %s\n" +
            "- Branche : %s\n" +
            "- Message : \"%s\"",
            authorName, now, taskName, repositoryName, branch, commitMessage
        );
        return content;
    }

    private String buildTaskDeadlineEmailContent(Task task, int daysUntilDeadline, String urgency) {
        String urgencyColor = urgency.equals("CRITICAL") ? "#dc3545" : 
                             urgency.equals("WARNING") ? "#ffc107" : "#17a2b8";
        
        return String.format("""
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                <h2 style="color: %s;">Task Deadline Alert - %s</h2>
                <div style="background-color: #f8f9fa; padding: 15px; border-radius: 6px; margin: 20px 0; border-left: 4px solid %s;">
                    <p><strong>Task:</strong> %s</p>
                    <p><strong>Description:</strong> %s</p>
                    <p><strong>Deadline:</strong> %s</p>
                    <p><strong>Days Remaining:</strong> %d</p>
                    <p><strong>Urgency Level:</strong> %s</p>
                </div>
                <p>Please ensure you complete this task before the deadline.</p>
                <p>This is an automated notification from espriHUb.</p>
            </div>
            """, urgencyColor, urgency, urgencyColor, task.getTitle(), 
                 task.getDescription() != null ? task.getDescription() : "No description",
                 task.getDueDate(), daysUntilDeadline, urgency);
    }

    private String buildTaskDeadlineTeamsContent(Task task, int daysUntilDeadline, String urgency) {
        // Message Teams personnalisé pour la deadline à 24h
        String content = String.format(
            "⏰ Deadline Approaching!\n" +
            "- Tâche : %s\n" +
            "- Description : %s\n" +
            "- Deadline : %s\n" +
            "- Il reste : 24 heures pour terminer cette tâche !",
            task.getTitle(),
            task.getDescription() != null ? task.getDescription() : "(Aucune description)",
            task.getDueDate()
        );
        return content;
    }

    private String buildProjectDeadlineEmailContent(Project project, int daysUntilDeadline, String urgency) {
        String urgencyColor = urgency.equals("CRITICAL") ? "#dc3545" : 
                             urgency.equals("WARNING") ? "#ffc107" : "#17a2b8";
        
        return String.format("""
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                <h2 style="color: %s;">Project Deadline Alert - %s</h2>
                <div style="background-color: #f8f9fa; padding: 15px; border-radius: 6px; margin: 20px 0; border-left: 4px solid %s;">
                    <p><strong>Project:</strong> %s</p>
                    <p><strong>Description:</strong> %s</p>
                    <p><strong>Deadline:</strong> %s</p>
                    <p><strong>Days Remaining:</strong> %d</p>
                    <p><strong>Urgency Level:</strong> %s</p>
                </div>
                <p>Please ensure your project is completed before the deadline.</p>
                <p>This is an automated notification from espriHUb.</p>
            </div>
            """, urgencyColor, urgency, urgencyColor, project.getName(), 
                 project.getDescription() != null ? project.getDescription() : "No description",
                 project.getDeadline(), daysUntilDeadline, urgency);
    }

    private String buildProjectDeadlineTeamsContent(Project project, int daysUntilDeadline, String urgency) {
        String urgencyIcon = urgency.equals("CRITICAL") ? "🚨" : 
                            urgency.equals("WARNING") ? "⚠️" : "ℹ️";
        
        Map<String, Object> card = new HashMap<>();
        card.put("type", "message");
        card.put("attachments", List.of(Map.of(
            "contentType", "application/vnd.microsoft.card.adaptive",
            "content", Map.of(
                "type", "AdaptiveCard",
                "version", "1.0",
                "body", List.of(
                    Map.of("type", "TextBlock", "text", urgencyIcon + " Project Deadline Alert", "weight", "bolder", "size", "large"),
                    Map.of("type", "TextBlock", "text", String.format("**Project:** %s", project.getName())),
                    Map.of("type", "TextBlock", "text", String.format("**Description:** %s", project.getDescription() != null ? project.getDescription() : "No description")),
                    Map.of("type", "TextBlock", "text", String.format("**Deadline:** %s", project.getDeadline())),
                    Map.of("type", "TextBlock", "text", String.format("**Days Remaining:** %d", daysUntilDeadline)),
                    Map.of("type", "TextBlock", "text", String.format("**Urgency:** %s", urgency))
                )
            )
        )));
        
        return card.toString();
    }
} 