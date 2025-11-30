package tn.esprithub.server.academic.service;

import tn.esprithub.server.academic.dto.DepartementDto;
import tn.esprithub.server.academic.dto.DepartementSummaryDto;
import tn.esprithub.server.academic.dto.NiveauDto;
import tn.esprithub.server.academic.dto.NiveauSummaryDto;
import tn.esprithub.server.academic.dto.ClasseDto;
import tn.esprithub.server.academic.dto.CreateClasseDto;
import tn.esprithub.server.academic.dto.CourseDto;
import tn.esprithub.server.academic.dto.CourseAssignmentDto;
import tn.esprithub.server.user.dto.UserSummaryDto;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for admin operations on academic entities
 * Only admins can perform all operations across all departments
 */
public interface AdminAcademicService {
    
    // Department operations
    List<DepartementDto> getAllDepartements();
    List<DepartementDto> getActiveDepartements();
    DepartementDto getDepartementById(UUID id);
    DepartementDto createDepartement(DepartementDto departementDto);
    DepartementDto updateDepartement(UUID id, DepartementDto departementDto);
    void deleteDepartement(UUID id);
    void activateDepartement(UUID id);
    void deactivateDepartement(UUID id);
    
    // Department chief assignment
    DepartementDto assignChiefToDepartement(UUID departementId, UUID chiefId);
    DepartementDto removeChiefFromDepartement(UUID departementId);
    
    // Level operations (across all departments)
    List<NiveauDto> getAllNiveaux();
    List<NiveauDto> getNiveauxByDepartement(UUID departementId);
    NiveauDto getNiveauById(UUID id);
    NiveauDto createNiveau(NiveauDto niveauDto);
    NiveauDto updateNiveau(UUID id, NiveauDto niveauDto);
    void deleteNiveau(UUID id);
    
    // Class operations (across all departments)
    List<ClasseDto> getAllClasses();
    List<ClasseDto> getClassesByDepartement(UUID departementId);
    List<ClasseDto> getClassesByNiveau(UUID niveauId);
    ClasseDto getClasseById(UUID id);
    ClasseDto createClasse(ClasseDto classeDto);
    ClasseDto updateClasse(UUID id, ClasseDto classeDto);
    void deleteClasse(UUID id);
    
    // User assignments
    ClasseDto assignStudentsToClasse(UUID classeId, List<UUID> studentIds);
    ClasseDto removeStudentsFromClasse(UUID classeId, List<UUID> studentIds);
    ClasseDto assignTeachersToClasse(UUID classeId, List<UUID> teacherIds);
    ClasseDto removeTeachersFromClasse(UUID classeId, List<UUID> teacherIds);
    
    // Statistics and reporting
    List<DepartementDto> getDepartementsWithStatistics();
    List<UserSummaryDto> getUnassignedUsers();
    List<UserSummaryDto> getUsersByRole(String role);
    
    // Helper methods for class creation workflow
    List<DepartementSummaryDto> getDepartementsSummary();
    List<NiveauSummaryDto> getNiveauxSummaryByDepartement(UUID departementId);
    ClasseDto createClasseForNiveau(UUID departementId, UUID niveauId, CreateClasseDto createClasseDto);
    
    // Course operations
    List<CourseDto> getCoursesByNiveau(UUID niveauId);
    List<CourseAssignmentDto> getCourseAssignmentsByNiveau(UUID niveauId);
    CourseAssignmentDto createCourseAssignment(CourseAssignmentDto dto);
    void deleteCourseAssignment(UUID assignmentId);
    CourseDto createCourse(CourseDto courseDto);
    
    // Ajout : opérations de modification et suppression de cours
    CourseDto updateCourse(UUID id, CourseDto courseDto);
    void deleteCourse(UUID id);
}
