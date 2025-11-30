package tn.esprithub.server.academic.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

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
public class NiveauDto {
    private UUID id;
    
    @NotBlank(message = "Le nom du niveau est obligatoire")
    private String nom;
    
    private String description;
    
    @NotNull(message = "L'année est obligatoire")
    @Min(value = 1, message = "L'année doit être au minimum 1")
    @Max(value = 6, message = "L'année doit être au maximum 6")
    private Integer annee;
    
    private String code;
    private Boolean isActive;
    
    // Department information (simplified)
    private UUID departementId;
    private String departementNom;
    
    // Statistics
    private Integer totalClasses;
    private Integer totalEtudiants;
    
    // Related data (for detailed views)
    private List<ClasseDto> classes;
}
