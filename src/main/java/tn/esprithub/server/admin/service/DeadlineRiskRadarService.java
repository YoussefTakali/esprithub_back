package tn.esprithub.server.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.esprithub.server.common.enums.UserRole;
import tn.esprithub.server.project.entity.Task;
import tn.esprithub.server.project.enums.TaskStatus;
import tn.esprithub.server.project.repository.TaskRepository;
import tn.esprithub.server.repository.entity.Repository;
import tn.esprithub.server.repository.repository.RepositoryEntityRepository;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeadlineRiskRadarService {

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final RepositoryEntityRepository repositoryRepository;

    public Map<String, Object> buildStudentRiskRadar(int daysAhead, int staleDays) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dueSoonLimit = now.plusDays(daysAhead);
        LocalDateTime staleLimit = now.minusDays(staleDays);

        List<User> students = userRepository.findByRoleAndIsActiveTrue(UserRole.STUDENT);
        List<Map<String, Object>> studentRiskItems = new ArrayList<>();

        int highRiskCount = 0;
        int mediumRiskCount = 0;
        int lowRiskCount = 0;

        for (User student : students) {
            List<Task> tasks = taskRepository.findTasksAssignedToUser(student.getId());
            List<Repository> repositories = repositoryRepository.findByOwnerIdAndIsActiveTrue(student.getId());

            long overdueTasks = tasks.stream()
                    .filter(task -> task.getDueDate() != null)
                    .filter(task -> isIncomplete(task.getStatus()))
                    .filter(task -> task.getDueDate().isBefore(now))
                    .count();

            long dueSoonTasks = tasks.stream()
                    .filter(task -> task.getDueDate() != null)
                    .filter(task -> isIncomplete(task.getStatus()))
                    .filter(task -> !task.getDueDate().isBefore(now) && !task.getDueDate().isAfter(dueSoonLimit))
                    .count();

            long staleRepositories = repositories.stream()
                    .filter(repo -> repo.getLastSyncAt() == null || repo.getLastSyncAt().isBefore(staleLimit))
                    .count();

            LocalDateTime lastActivityAt = repositories.stream()
                    .map(this::extractLastActivity)
                    .filter(activity -> activity != null)
                    .max(Comparator.naturalOrder())
                    .orElse(null);

            int score = calculateRiskScore(
                    overdueTasks,
                    dueSoonTasks,
                    repositories.size(),
                    staleRepositories,
                    student.getGithubToken() == null || student.getGithubToken().isBlank(),
                    lastActivityAt,
                    now
            );

            String riskLevel = riskLevelFromScore(score);
            List<String> reasons = buildReasons(overdueTasks, dueSoonTasks, repositories.size(), staleRepositories, lastActivityAt, now);

            if ("HIGH".equals(riskLevel)) {
                highRiskCount++;
            } else if ("MEDIUM".equals(riskLevel)) {
                mediumRiskCount++;
            } else {
                lowRiskCount++;
            }

            Map<String, Object> item = new HashMap<>();
            item.put("studentId", student.getId());
            item.put("email", student.getEmail());
            item.put("fullName", student.getFullName());
            item.put("riskScore", score);
            item.put("riskLevel", riskLevel);
            item.put("totalTasks", tasks.size());
            item.put("overdueTasks", overdueTasks);
            item.put("dueSoonTasks", dueSoonTasks);
            item.put("repositoryCount", repositories.size());
            item.put("staleRepositoryCount", staleRepositories);
            item.put("lastActivityAt", lastActivityAt != null ? lastActivityAt.toString() : null);
            item.put("reasons", reasons);
            studentRiskItems.add(item);
        }

        studentRiskItems.sort((a, b) -> Integer.compare((Integer) b.get("riskScore"), (Integer) a.get("riskScore")));

        Map<String, Object> result = new HashMap<>();
        result.put("generatedAt", now.toString());
        result.put("daysAhead", daysAhead);
        result.put("staleDays", staleDays);
        result.put("totalStudents", students.size());
        result.put("highRiskCount", highRiskCount);
        result.put("mediumRiskCount", mediumRiskCount);
        result.put("lowRiskCount", lowRiskCount);
        result.put("students", studentRiskItems);

        return result;
    }

    private boolean isIncomplete(TaskStatus status) {
        return status != TaskStatus.COMPLETED && status != TaskStatus.CLOSED;
    }

    private LocalDateTime extractLastActivity(Repository repository) {
        if (repository.getPushedAt() == null) {
            return repository.getLastSyncAt();
        }
        if (repository.getLastSyncAt() == null) {
            return repository.getPushedAt();
        }
        return repository.getPushedAt().isAfter(repository.getLastSyncAt())
                ? repository.getPushedAt()
                : repository.getLastSyncAt();
    }

    private int calculateRiskScore(
            long overdueTasks,
            long dueSoonTasks,
            int repositoryCount,
            long staleRepositoryCount,
            boolean missingGithubToken,
            LocalDateTime lastActivityAt,
            LocalDateTime now) {

        int score = 0;

        score += Math.min(50, (int) overdueTasks * 20);
        score += Math.min(24, (int) dueSoonTasks * 8);

        if (missingGithubToken) {
            score += 20;
        }

        if (repositoryCount == 0) {
            score += 10;
        } else {
            double staleRatio = (double) staleRepositoryCount / repositoryCount;
            score += (int) Math.round(staleRatio * 20);
        }

        if (lastActivityAt == null) {
            score += 15;
        } else {
            long inactiveDays = java.time.Duration.between(lastActivityAt, now).toDays();
            if (inactiveDays > 7) {
                score += 15;
            } else if (inactiveDays > 3) {
                score += 8;
            }
        }

        return Math.min(score, 100);
    }

    private String riskLevelFromScore(int score) {
        if (score >= 70) {
            return "HIGH";
        }
        if (score >= 40) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private List<String> buildReasons(
            long overdueTasks,
            long dueSoonTasks,
            int repositoryCount,
            long staleRepositoryCount,
            LocalDateTime lastActivityAt,
            LocalDateTime now) {

        List<String> reasons = new ArrayList<>();

        if (overdueTasks > 0) {
            reasons.add(overdueTasks + " overdue task(s)");
        }
        if (dueSoonTasks > 0) {
            reasons.add(dueSoonTasks + " task(s) due soon");
        }
        if (repositoryCount == 0) {
            reasons.add("no synced repositories");
        } else if (staleRepositoryCount > 0) {
            reasons.add(staleRepositoryCount + " stale repositor(y/ies)");
        }
        if (lastActivityAt == null) {
            reasons.add("no repository activity detected");
        } else {
            long inactiveDays = java.time.Duration.between(lastActivityAt, now).toDays();
            if (inactiveDays > 3) {
                reasons.add("no recent activity for " + inactiveDays + " day(s)");
            }
        }

        if (reasons.isEmpty()) {
            reasons.add("on track");
        }

        return reasons;
    }
}
