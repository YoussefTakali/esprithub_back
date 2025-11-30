package tn.esprithub.server.academic.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.EqualsAndHashCode;
import tn.esprithub.server.common.entity.BaseEntity;

import java.util.List;

/**
 * Represents an academic level (Niveau) within a department
 * For example: 1ère année, 2ème année, 3ème année
 */
@Entity
@Table(name = "niveaux")
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Niveau extends BaseEntity {

    @NotBlank(message = "Le nom du niveau est obligatoire")
    @Column(nullable = false, length = 100)
    private String nom;

    @Column(length = 500)
    private String description;

    @NotNull(message = "L'année est obligatoire")
    @Min(value = 1, message = "L'année doit être au minimum 1")
    @Max(value = 6, message = "L'année doit être au maximum 6")
    @Column(nullable = false)
    private Integer annee;

    @Column(name = "code_niveau", length = 10)
    private String code;

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;

    // Many-to-one relationship with department
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "departement_id", nullable = false, foreignKey = @ForeignKey(name = "fk_niveau_departement"))
    private Departement departement;

    // One-to-many relationship with classes
    @OneToMany(mappedBy = "niveau", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Classe> classes;

    // One-to-many relationship with course assignments
    @OneToMany(mappedBy = "niveau", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CourseAssignment> courseAssignments;

    @PrePersist
    @PreUpdate
    private void generateCode() {
        if (this.code == null || this.code.trim().isEmpty()) {
            this.code = generateNiveauCode();
        }
    }

    private String generateNiveauCode() {
        if (departement != null && departement.getCode() != null) {
            return departement.getCode() + "_N" + this.annee;
        }
        return "NIV_" + this.annee;
    }
}
