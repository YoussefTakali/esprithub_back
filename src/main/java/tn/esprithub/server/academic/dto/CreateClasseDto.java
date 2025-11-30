package tn.esprithub.server.academic.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateClasseDto {
    @NotBlank(message = "Le nom de la classe est obligatoire")
    private String nom;
    
    private String description;
    
    @NotNull(message = "La capacité est obligatoire")
    @Min(value = 1, message = "La capacité doit être au minimum 1")
    @Max(value = 100, message = "La capacité doit être au maximum 100")
    private Integer capacite;
    
    private String code;
    
    @Builder.Default
    private Boolean isActive = true;
}
