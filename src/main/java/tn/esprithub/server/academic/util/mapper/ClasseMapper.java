package tn.esprithub.server.academic.util.mapper;

import org.springframework.stereotype.Component;
import tn.esprithub.server.academic.dto.ClasseDto;
import tn.esprithub.server.academic.entity.Classe;

import java.util.List;

/**
 * Mapper for converting between Classe entities and DTOs
 */
@Component
public class ClasseMapper {

    public ClasseDto toDto(Classe classe) {
        if (classe == null) {
            return null;
        }

        ClasseDto.ClasseDtoBuilder builder = ClasseDto.builder()
                .id(classe.getId())
                .nom(classe.getNom())
                .description(classe.getDescription())
                .capacite(classe.getCapacite())
                .code(classe.getCode())
                .isActive(classe.getIsActive());

        // Map niveau information if present
        if (classe.getNiveau() != null) {
            builder.niveauId(classe.getNiveau().getId())
                   .niveauNom(classe.getNiveau().getNom())
                   .niveauAnnee(classe.getNiveau().getAnnee());

            // Map department information through niveau
            if (classe.getNiveau().getDepartement() != null) {
                builder.departementId(classe.getNiveau().getDepartement().getId())
                       .departementNom(classe.getNiveau().getDepartement().getNom());
            }
        }

        return builder.build();
    }

    public ClasseDto toDtoWithStatistics(Classe classe) {
        ClasseDto dto = toDto(classe);
        if (dto == null) {
            return null;
        }

        // Calculate statistics
        if (classe.getStudents() != null) {
            dto.setTotalEtudiants(classe.getStudents().size());
        }
        
        if (classe.getTeachers() != null) {
            dto.setTotalEnseignants(classe.getTeachers().size());
        }

        return dto;
    }

    public Classe toEntity(ClasseDto dto) {
        if (dto == null) {
            return null;
        }

        return Classe.builder()
                .id(dto.getId())
                .nom(dto.getNom())
                .description(dto.getDescription())
                .capacite(dto.getCapacite())
                .code(dto.getCode())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : Boolean.TRUE)
                .build();
    }

    public void updateEntityFromDto(Classe entity, ClasseDto dto) {
        if (entity == null || dto == null) {
            return;
        }

        entity.setNom(dto.getNom());
        entity.setDescription(dto.getDescription());
        entity.setCapacite(dto.getCapacite());
        
        if (dto.getIsActive() != null) {
            entity.setIsActive(dto.getIsActive());
        }
    }

    public List<ClasseDto> toDtoList(List<Classe> classes) {
        if (classes == null) {
            return List.of();
        }
        return classes.stream()
                .map(this::toDto)
                .toList();
    }

    public List<ClasseDto> toDtoListWithStatistics(List<Classe> classes) {
        if (classes == null) {
            return List.of();
        }
        return classes.stream()
                .map(this::toDtoWithStatistics)
                .toList();
    }
}
