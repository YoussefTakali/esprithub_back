package tn.esprithub.server.academic.util.mapper;

import org.springframework.stereotype.Component;
import tn.esprithub.server.academic.dto.DepartementDto;
import tn.esprithub.server.academic.entity.Departement;
import tn.esprithub.server.user.entity.User;

import java.util.List;

/**
 * Mapper for converting between Departement entities and DTOs
 */
@Component
public class DepartementMapper {

    public DepartementDto toDto(Departement departement) {
        if (departement == null) {
            return null;
        }

        DepartementDto.DepartementDtoBuilder builder = DepartementDto.builder()
                .id(departement.getId())
                .nom(departement.getNom())
                .description(departement.getDescription())
                .specialite(departement.getSpecialite())
                .typeFormation(departement.getTypeFormation())
                .code(departement.getCode())
                .isActive(departement.getIsActive());

        // Map chief information if present
        if (departement.getChief() != null) {
            User chief = departement.getChief();
            builder.chiefId(chief.getId())
                   .chiefName(chief.getFirstName() + " " + chief.getLastName())
                   .chiefEmail(chief.getEmail());
        }

        return builder.build();
    }

    public DepartementDto toDtoWithStatistics(Departement departement) {
        DepartementDto dto = toDto(departement);
        if (dto == null) {
            return null;
        }

        // Calculate statistics
        if (departement.getNiveaux() != null) {
            dto.setTotalNiveaux(departement.getNiveaux().size());
            
            int totalClasses = departement.getNiveaux().stream()
                    .mapToInt(niveau -> niveau.getClasses() != null ? niveau.getClasses().size() : 0)
                    .sum();
            dto.setTotalClasses(totalClasses);

            int totalEtudiants = departement.getNiveaux().stream()
                    .flatMap(niveau -> niveau.getClasses() != null ? niveau.getClasses().stream() : java.util.stream.Stream.empty())
                    .mapToInt(classe -> classe.getStudents() != null ? classe.getStudents().size() : 0)
                    .sum();
            dto.setTotalEtudiants(totalEtudiants);
        }

        if (departement.getTeachers() != null) {
            dto.setTotalEnseignants(departement.getTeachers().size());
        }

        return dto;
    }

    public Departement toEntity(DepartementDto dto) {
        if (dto == null) {
            return null;
        }

        return Departement.builder()
                .id(dto.getId())
                .nom(dto.getNom())
                .description(dto.getDescription())
                .specialite(dto.getSpecialite())
                .typeFormation(dto.getTypeFormation())
                .code(dto.getCode())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : Boolean.TRUE)
                .build();
    }

    public void updateEntityFromDto(Departement entity, DepartementDto dto) {
        if (entity == null || dto == null) {
            return;
        }

        entity.setNom(dto.getNom());
        entity.setDescription(dto.getDescription());
        entity.setSpecialite(dto.getSpecialite());
        entity.setTypeFormation(dto.getTypeFormation());
        
        if (dto.getIsActive() != null) {
            entity.setIsActive(dto.getIsActive());
        }
    }

    public List<DepartementDto> toDtoList(List<Departement> departements) {
        if (departements == null) {
            return List.of();
        }
        return departements.stream()
                .map(this::toDto)
                .toList();
    }

    public List<DepartementDto> toDtoListWithStatistics(List<Departement> departements) {
        if (departements == null) {
            return List.of();
        }
        return departements.stream()
                .map(this::toDtoWithStatistics)
                .toList();
    }
}
