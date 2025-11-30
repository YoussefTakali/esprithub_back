package tn.esprithub.server.academic.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import tn.esprithub.server.academic.enums.Specialites;
import tn.esprithub.server.academic.enums.TypeFormation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartementDto {
    private UUID id;
    
    @NotBlank(message = "Le nom du département est obligatoire")
    private String nom;
    
    private String description;
    
    @NotNull(message = "Le type de spécialité est obligatoire")
    private Specialites specialite;
    
    @NotNull(message = "Le type de formation est obligatoire")
    private TypeFormation typeFormation;
    
    private String code;
    private Boolean isActive;
    
    // Chief information (simplified)
    private UUID chiefId;
    private String chiefName;
    private String chiefEmail;
    
    // Statistics
    private Integer totalNiveaux;
    private Integer totalClasses;
    private Integer totalEtudiants;
    private Integer totalEnseignants;
    
    // Related data (for detailed views)
    private List<NiveauDto> niveaux;
}
