package tn.esprithub.server.academic.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import tn.esprithub.server.academic.enums.Specialites;
import tn.esprithub.server.academic.enums.TypeFormation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpecialiteDto {
    private Long id;
    
    @NotBlank(message = "Le nom de la spécialité est obligatoire")
    private String nom;
    
    @NotNull(message = "Le type de spécialité est obligatoire")
    private Specialites specialites;
    
    @NotNull(message = "Le type de formation est obligatoire")
    private TypeFormation typeFormation;
    
    private Integer totalNiveaux;
    private Integer totalClasses;
    private Integer totalEtudiants;
}
