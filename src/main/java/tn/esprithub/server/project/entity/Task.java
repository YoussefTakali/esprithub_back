package tn.esprithub.server.project.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.SuperBuilder;
import tn.esprithub.server.project.enums.TaskAssignmentType;
import tn.esprithub.server.project.enums.TaskStatus;
import tn.esprithub.server.project.entity.Group;
import tn.esprithub.server.academic.entity.Classe;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.common.entity.BaseEntity;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "tasks")
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Task extends BaseEntity {
    @NotBlank
    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskAssignmentType type;

    // Assignment targets (nullable depending on type)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "task_groups",
        joinColumns = @JoinColumn(name = "task_id"),
        inverseJoinColumns = @JoinColumn(name = "group_id")
    )
    private List<Group> assignedToGroups;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "task_students",
        joinColumns = @JoinColumn(name = "task_id"),
        inverseJoinColumns = @JoinColumn(name = "student_id")
    )
    private List<User> assignedToStudents;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "task_classes",
        joinColumns = @JoinColumn(name = "task_id"),
        inverseJoinColumns = @JoinColumn(name = "classe_id")
    )
    private List<Classe> assignedToClasses;

    @Column(name = "due_date")
    private LocalDateTime dueDate;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.DRAFT;

    @Builder.Default
    @Column(name = "is_graded", nullable = false)
    private boolean graded = false;

    @Builder.Default
    @Column(name = "visible", nullable = false)
    private boolean isVisible = true;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "task_projects",
        joinColumns = @JoinColumn(name = "task_id"),
        inverseJoinColumns = @JoinColumn(name = "project_id")
    )
    private List<Project> projects;

    public List<Project> getProjects() {
        return projects;
    }
    public void setProjects(List<Project> projects) {
        this.projects = projects;
    }
    public boolean isGraded() { return graded; }
    public void setGraded(boolean graded) { this.graded = graded; }
}
