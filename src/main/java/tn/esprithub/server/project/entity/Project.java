package tn.esprithub.server.project.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.SuperBuilder;
import tn.esprithub.server.academic.entity.Classe;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.common.entity.BaseEntity;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "projects")
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"groups", "tasks", "collaborators", "classes"})
public class Project extends BaseEntity {
    @NotBlank
    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    // The teacher who created the project
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    // Classes assigned to this project
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "project_classes",
        joinColumns = @JoinColumn(name = "project_id"),
        inverseJoinColumns = @JoinColumn(name = "classe_id")
    )
    private List<Classe> classes;

    // Groups in this project
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Group> groups;

    // Tasks in this project
    @ManyToMany(mappedBy = "projects")
    private List<Task> tasks;

    // Collaborators on this project (including the creator if desired)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "project_collaborators",
        joinColumns = @JoinColumn(name = "project_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private List<User> collaborators;

    @Column(name = "deadline")
    private LocalDateTime deadline;
}
