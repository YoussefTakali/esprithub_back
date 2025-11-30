package tn.esprithub.server.academic.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.EqualsAndHashCode;
import lombok.Builder;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.common.entity.BaseEntity;

import java.util.List;

/**
 * Represents a class (Classe) within an academic level
 * For example: GL3-A, GL3-B, etc.
 */
@Entity
@Table(name = "classes")
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"students", "teachers", "niveau"})
public class Classe extends BaseEntity {

    @NotBlank(message = "Le nom de la classe est obligatoire")
    @Column(nullable = false, length = 100)
    private String nom;

    @Column(length = 500)
    private String description;

    @NotNull(message = "La capacité est obligatoire")
    @Min(value = 1, message = "La capacité doit être au minimum 1")
    @Max(value = 100, message = "La capacité doit être au maximum 100")
    @Column(nullable = false)
    private Integer capacite;

    @Column(name = "code_classe", length = 50)
    private String code;

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;

    // Many-to-one relationship with level
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "niveau_id", nullable = false, foreignKey = @ForeignKey(name = "fk_classe_niveau"))
    private Niveau niveau;

    // One-to-many relationship with students
    @OneToMany(mappedBy = "classe", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<User> students;

    // Many-to-many relationship with teachers
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "classe_teachers",
        joinColumns = @JoinColumn(name = "classe_id"),
        inverseJoinColumns = @JoinColumn(name = "teacher_id")
    )
    private List<User> teachers;

    @ManyToMany(mappedBy = "assignedToClasses")
    private List<tn.esprithub.server.project.entity.Task> tasks;

    @PrePersist
    @PreUpdate
    private void generateCode() {
        if (this.code == null || this.code.trim().isEmpty()) {
            this.code = generateClasseCode();
        }
    }

    private String generateClasseCode() {
        if (niveau != null && niveau.getCode() != null) {
            // Extract class identifier from name (e.g., "A", "B" from "GL3-A")
            String classeSuffix = extractClasseSuffix(this.nom);
            return niveau.getCode() + "_" + classeSuffix;
        }
        return "CLS_" + this.nom.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    private String extractClasseSuffix(String nom) {
        if (nom != null && nom.contains("-")) {
            String[] parts = nom.split("-");
            if (parts.length > 1) {
                return parts[parts.length - 1].trim().toUpperCase();
            }
        }
        return nom != null ? nom.replaceAll("[^A-Za-z0-9]", "").toUpperCase() : "DEFAULT";
    }
}
