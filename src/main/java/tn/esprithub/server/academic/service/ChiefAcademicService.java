package tn.esprithub.server.academic.service;

import tn.esprithub.server.academic.dto.DepartementDto;
import tn.esprithub.server.academic.dto.NiveauDto;
import tn.esprithub.server.academic.dto.ClasseDto;
import tn.esprithub.server.user.dto.UserSummaryDto;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for department chief operations
 * Chiefs can only manage their assigned department
 */
public interface ChiefAcademicService {
    
    // Department operations (restricted to chief's department)
    DepartementDto getMyDepartement(UUID chiefId);
    DepartementDto updateMyDepartement(UUID chiefId, DepartementDto departementDto);
    
    // Level operations (within chief's department)
    List<NiveauDto> getMyDepartementNiveaux(UUID chiefId);
    NiveauDto createNiveauInMyDepartement(UUID chiefId, NiveauDto niveauDto);
    NiveauDto updateNiveauInMyDepartement(UUID chiefId, UUID niveauId, NiveauDto niveauDto);
    void deleteNiveauInMyDepartement(UUID chiefId, UUID niveauId);
    
    // Class operations (within chief's department)
    List<ClasseDto> getMyDepartementClasses(UUID chiefId);
    List<ClasseDto> getClassesByNiveauInMyDepartement(UUID chiefId, UUID niveauId);
    ClasseDto createClasseInMyDepartement(UUID chiefId, ClasseDto classeDto);
    ClasseDto updateClasseInMyDepartement(UUID chiefId, UUID classeId, ClasseDto classeDto);
    void deleteClasseInMyDepartement(UUID chiefId, UUID classeId);
    
    // User assignments (within chief's department)
    ClasseDto assignStudentsToClasseInMyDepartement(UUID chiefId, UUID classeId, List<UUID> studentIds);
    ClasseDto removeStudentsFromClasseInMyDepartement(UUID chiefId, UUID classeId, List<UUID> studentIds);
    ClasseDto assignTeachersToClasseInMyDepartement(UUID chiefId, UUID classeId, List<UUID> teacherIds);
    ClasseDto removeTeachersFromClasseInMyDepartement(UUID chiefId, UUID classeId, List<UUID> teacherIds);
    
    // Statistics for chief's department
    DepartementDto getMyDepartementWithStatistics(UUID chiefId);
    List<UserSummaryDto> getUnassignedUsersInMyDepartement(UUID chiefId);
    List<UserSummaryDto> getTeachersInMyDepartement(UUID chiefId);
    List<UserSummaryDto> getStudentsInMyDepartement(UUID chiefId);

    // Dashboard: notifications dynamiques
    List<tn.esprithub.server.academic.dto.ChiefNotificationDto> getRecentNotificationsForChief(UUID chiefId, int limit);
}
