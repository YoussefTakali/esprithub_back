package tn.esprithub.server.academic.util.mapper;

import org.springframework.stereotype.Component;
import tn.esprithub.server.academic.dto.NiveauDto;
import tn.esprithub.server.academic.entity.Niveau;

import java.util.List;

/**
 * Mapper for converting between Niveau entities and DTOs
 */
@Component
public class NiveauMapper {

    public NiveauDto toDto(Niveau niveau) {
        if (niveau == null) {
            return null;
        }

        NiveauDto.NiveauDtoBuilder builder = NiveauDto.builder()
                .id(niveau.getId())
                .nom(niveau.getNom())
                .description(niveau.getDescription())
                .annee(niveau.getAnnee())
                .code(niveau.getCode())
                .isActive(niveau.getIsActive());

        // Map department information if present
        if (niveau.getDepartement() != null) {
            builder.departementId(niveau.getDepartement().getId())
                   .departementNom(niveau.getDepartement().getNom());
        }

        return builder.build();
    }

    public NiveauDto toDtoWithStatistics(Niveau niveau) {
        NiveauDto dto = toDto(niveau);
        if (dto == null) {
            return null;
        }

        // Calculate statistics
        if (niveau.getClasses() != null) {
            dto.setTotalClasses(niveau.getClasses().size());
            
            int totalEtudiants = niveau.getClasses().stream()
                    .mapToInt(classe -> classe.getStudents() != null ? classe.getStudents().size() : 0)
                    .sum();
            dto.setTotalEtudiants(totalEtudiants);
        }

        return dto;
    }

    public Niveau toEntity(NiveauDto dto) {
        if (dto == null) {
            return null;
        }

        return Niveau.builder()
                .id(dto.getId())
                .nom(dto.getNom())
                .description(dto.getDescription())
                .annee(dto.getAnnee())
                .code(dto.getCode())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : Boolean.TRUE)
                .build();
    }

    public void updateEntityFromDto(Niveau entity, NiveauDto dto) {
        if (entity == null || dto == null) {
            return;
        }

        entity.setNom(dto.getNom());
        entity.setDescription(dto.getDescription());
        entity.setAnnee(dto.getAnnee());
        
        if (dto.getIsActive() != null) {
            entity.setIsActive(dto.getIsActive());
        }
    }

    public List<NiveauDto> toDtoList(List<Niveau> niveaux) {
        if (niveaux == null) {
            return List.of();
        }
        return niveaux.stream()
                .map(this::toDto)
                .toList();
    }

    public List<NiveauDto> toDtoListWithStatistics(List<Niveau> niveaux) {
        if (niveaux == null) {
            return List.of();
        }
        return niveaux.stream()
                .map(this::toDtoWithStatistics)
                .toList();
    }
}
