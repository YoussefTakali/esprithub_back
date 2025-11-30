package tn.esprithub.server.academic.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NiveauSummaryDto {
    private UUID id;
    private String nom;
    private String code;
    private Integer annee;
    private Boolean isActive;
    private UUID departementId;
    private String departementNom;
    private Integer totalClasses;
}
