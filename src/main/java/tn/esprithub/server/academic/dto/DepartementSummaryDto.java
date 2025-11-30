package tn.esprithub.server.academic.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import tn.esprithub.server.academic.enums.Specialites;
import tn.esprithub.server.academic.enums.TypeFormation;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartementSummaryDto {
    private UUID id;
    private String nom;
    private String code;
    private Specialites specialite;
    private TypeFormation typeFormation;
    private Boolean isActive;
    private Integer totalNiveaux;
}
