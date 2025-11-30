package tn.esprithub.server.academic.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.EqualsAndHashCode;
import lombok.Builder;
import tn.esprithub.server.academic.enums.Specialites;
import tn.esprithub.server.academic.enums.TypeFormation;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.common.entity.BaseEntity;

import java.util.List;

/**
 * Represents a department (Département) at ESPRIT
 * Each department has a chief and contains multiple levels
 */
@Entity
@Table(name = "departements")
@Data
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Departement extends BaseEntity {

    @NotBlank(message = "Le nom du département est obligatoire")
    @Column(nullable = false, length = 100)
    private String nom;

    @Column(length = 500)
    private String description;

    @NotNull(message = "Le type de spécialité est obligatoire")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Specialites specialite;

    @NotNull(message = "Le type de formation est obligatoire")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeFormation typeFormation;

    @Column(name = "code_departement", unique = true, length = 10)
    private String code;

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;

    // Department chief (Chef de département)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chief_id", foreignKey = @ForeignKey(name = "fk_departement_chief"))
    private User chief;

    // Bidirectional relationship with levels
    @OneToMany(mappedBy = "departement", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Niveau> niveaux;

    // Bidirectional relationship with teachers
    @OneToMany(mappedBy = "departement", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<User> teachers;

    @PrePersist
    @PreUpdate
    private void generateCode() {
        if (this.code == null || this.code.trim().isEmpty()) {
            this.code = generateDepartmentCode();
        }
    }

    private String generateDepartmentCode() {
        String specialiteCode = this.specialite.name().substring(0, Math.min(3, this.specialite.name().length()));
        String formationCode = this.typeFormation.name().substring(0, Math.min(2, this.typeFormation.name().length()));
        return (specialiteCode + formationCode).toUpperCase();
    }
}
