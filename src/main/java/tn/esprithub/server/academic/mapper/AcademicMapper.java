package tn.esprithub.server.academic.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tn.esprithub.server.academic.dto.*;
import tn.esprithub.server.academic.entity.*;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.user.dto.UserSummaryDto;
import tn.esprithub.server.user.util.UserMapper;

import java.util.List;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class AcademicMapper {

    private final UserMapper userMapper;

    public DepartementDto toDepartementDto(Departement departement) {
        if (departement == null) return null;

        return DepartementDto.builder()
                .id(departement.getId())
                .nom(departement.getNom())
                .description(departement.getDescription())
                .specialite(departement.getSpecialite())
                .typeFormation(departement.getTypeFormation())
                .code(departement.getCode())
                .isActive(departement.getIsActive())
                .chiefId(departement.getChief() != null ? departement.getChief().getId() : null)
                .chiefName(departement.getChief() != null ? 
                    departement.getChief().getFirstName() + " " + departement.getChief().getLastName() : null)
                .chiefEmail(departement.getChief() != null ? departement.getChief().getEmail() : null)
                .totalNiveaux(departement.getNiveaux() != null ? departement.getNiveaux().size() : 0)
                .build();
    }

    public DepartementDto toDepartementDtoWithDetails(Departement departement) {
        DepartementDto dto = toDepartementDto(departement);
        if (dto != null && departement.getNiveaux() != null) {
            dto.setNiveaux(departement.getNiveaux().stream()
                    .map(this::toNiveauDto)
                    .toList());
        }
        return dto;
    }

    public Departement toDepartementEntity(DepartementDto dto) {
        if (dto == null) return null;

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

    public NiveauDto toNiveauDto(Niveau niveau) {
        if (niveau == null) return null;

        return NiveauDto.builder()
                .id(niveau.getId())
                .nom(niveau.getNom())
                .description(niveau.getDescription())
                .annee(niveau.getAnnee())
                .code(niveau.getCode())
                .isActive(niveau.getIsActive())
                .departementId(niveau.getDepartement() != null ? niveau.getDepartement().getId() : null)
                .departementNom(niveau.getDepartement() != null ? niveau.getDepartement().getNom() : null)
                .totalClasses(niveau.getClasses() != null ? niveau.getClasses().size() : 0)
                .build();
    }

    public NiveauDto toNiveauDtoWithDetails(Niveau niveau) {
        NiveauDto dto = toNiveauDto(niveau);
        if (dto != null && niveau.getClasses() != null) {
            dto.setClasses(niveau.getClasses().stream()
                    .map(this::toClasseDto)
                    .toList());
        }
        return dto;
    }

    public Niveau toNiveauEntity(NiveauDto dto) {
        if (dto == null) return null;

        return Niveau.builder()
                .id(dto.getId())
                .nom(dto.getNom())
                .description(dto.getDescription())
                .annee(dto.getAnnee())
                .code(dto.getCode())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : Boolean.TRUE)
                .build();
    }

    public ClasseDto toClasseDto(Classe classe) {
        if (classe == null) return null;

        return ClasseDto.builder()
                .id(classe.getId())
                .nom(classe.getNom())
                .description(classe.getDescription())
                .capacite(classe.getCapacite())
                .code(classe.getCode())
                .isActive(classe.getIsActive())
                .niveauId(classe.getNiveau() != null ? classe.getNiveau().getId() : null)
                .niveauNom(classe.getNiveau() != null ? classe.getNiveau().getNom() : null)
                .niveauAnnee(classe.getNiveau() != null ? classe.getNiveau().getAnnee() : null)
                .departementId(classe.getNiveau() != null && classe.getNiveau().getDepartement() != null ? 
                    classe.getNiveau().getDepartement().getId() : null)
                .departementNom(classe.getNiveau() != null && classe.getNiveau().getDepartement() != null ? 
                    classe.getNiveau().getDepartement().getNom() : null)
                .totalEtudiants(classe.getStudents() != null ? classe.getStudents().size() : 0)
                .totalEnseignants(classe.getTeachers() != null ? classe.getTeachers().size() : 0)
                .build();
    }

    public ClasseDto toClasseDtoWithDetails(Classe classe) {
        ClasseDto dto = toClasseDto(classe);
        if (dto != null) {
            if (classe.getStudents() != null) {
                dto.setStudents(classe.getStudents().stream()
                        .map(userMapper::toUserSummaryDto)
                        .toList());
            }
            if (classe.getTeachers() != null) {
                dto.setTeachers(classe.getTeachers().stream()
                        .map(userMapper::toUserSummaryDto)
                        .toList());
            }
        }
        return dto;
    }

    public Classe toClasseEntity(ClasseDto dto) {
        if (dto == null) return null;

        return Classe.builder()
                .id(dto.getId())
                .nom(dto.getNom())
                .description(dto.getDescription())
                .capacite(dto.getCapacite())
                .code(dto.getCode())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : Boolean.TRUE)
                .build();
    }

    public List<DepartementDto> toDepartementDtoList(List<Departement> departements) {
        if (departements == null) return Collections.emptyList();
        return departements.stream()
                .map(this::toDepartementDto)
                .toList();
    }

    public List<NiveauDto> toNiveauDtoList(List<Niveau> niveaux) {
        if (niveaux == null) return Collections.emptyList();
        return niveaux.stream()
                .map(this::toNiveauDto)
                .toList();
    }

    public List<ClasseDto> toClasseDtoList(List<Classe> classes) {
        if (classes == null) return Collections.emptyList();
        return classes.stream()
                .map(this::toClasseDto)
                .toList();
    }

    public List<UserSummaryDto> toUserSummaryDtoList(List<User> users) {
        if (users == null) return Collections.emptyList();
        return userMapper.toUserSummaryDtoList(users);
    }
}
