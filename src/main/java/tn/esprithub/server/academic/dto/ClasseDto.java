package tn.esprithub.server.academic.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import tn.esprithub.server.user.dto.UserSummaryDto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.util.UUID;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClasseDto {
    private UUID id;
    
    @NotBlank(message = "Le nom de la classe est obligatoire")
    private String nom;
    
    private String description;
    
    @NotNull(message = "La capacité est obligatoire")
    @Min(value = 1, message = "La capacité doit être au minimum 1")
    @Max(value = 100, message = "La capacité doit être au maximum 100")
    private Integer capacite;
    
    private String code;
    private Boolean isActive;
    
    // Level information (simplified)
    private UUID niveauId;
    private String niveauNom;
    private Integer niveauAnnee;
    
    // Department information (simplified)
    private UUID departementId;
    private String departementNom;
    
    // Statistics
    private Integer totalEtudiants;
    private Integer totalEnseignants;
    
    // Related data (simplified for basic info)
    private List<UserSummaryDto> students;
    private List<UserSummaryDto> teachers;
}
