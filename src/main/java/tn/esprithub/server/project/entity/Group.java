package tn.esprithub.server.project.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.SuperBuilder;
import tn.esprithub.server.academic.entity.Classe;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.common.entity.BaseEntity;

import java.util.List;

@Entity
@Table(name = "groups")
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"project", "classe", "students"})
public class Group extends BaseEntity {
    @NotBlank
    @Column(nullable = false, length = 100)
    private String name;

    // The project this group belongs to
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    // The class this group belongs to
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classe_id", nullable = false)
    private Classe classe;

    // Students in this group
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "group_students",
        joinColumns = @JoinColumn(name = "group_id"),
        inverseJoinColumns = @JoinColumn(name = "student_id")
    )
    @Builder.Default
    private List<User> students = new java.util.ArrayList<>();

    @ManyToMany(mappedBy = "assignedToGroups")
    private List<Task> tasks;

    // Repository associated with this group (optional)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", foreignKey = @ForeignKey(name = "fk_group_repository"))
    private tn.esprithub.server.repository.entity.Repository repository;

    @Transient
    private boolean repoCreated;
    @Transient
    private String repoUrl;
    @Transient
    private String repoError;
}
