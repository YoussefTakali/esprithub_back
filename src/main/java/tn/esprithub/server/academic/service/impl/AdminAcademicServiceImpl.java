package tn.esprithub.server.academic.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprithub.server.academic.dto.DepartementDto;
import tn.esprithub.server.academic.dto.DepartementSummaryDto;
import tn.esprithub.server.academic.dto.NiveauDto;
import tn.esprithub.server.academic.dto.NiveauSummaryDto;
import tn.esprithub.server.academic.dto.ClasseDto;
import tn.esprithub.server.academic.dto.CreateClasseDto;
import tn.esprithub.server.academic.dto.CourseDto;
import tn.esprithub.server.academic.dto.CourseAssignmentDto;
import tn.esprithub.server.academic.entity.Departement;
import tn.esprithub.server.academic.entity.Niveau;
import tn.esprithub.server.academic.entity.Classe;
import tn.esprithub.server.academic.entity.Course;
import tn.esprithub.server.academic.entity.CourseAssignment;
import tn.esprithub.server.academic.repository.DepartementRepository;
import tn.esprithub.server.academic.repository.NiveauRepository;
import tn.esprithub.server.academic.repository.ClasseRepository;
import tn.esprithub.server.academic.repository.CourseRepository;
import tn.esprithub.server.academic.repository.CourseAssignmentRepository;
import tn.esprithub.server.academic.service.AdminAcademicService;
import tn.esprithub.server.academic.util.mapper.DepartementMapper;
import tn.esprithub.server.academic.util.mapper.NiveauMapper;
import tn.esprithub.server.academic.util.mapper.ClasseMapper;
import tn.esprithub.server.academic.util.helper.AcademicValidationHelper;
import tn.esprithub.server.academic.util.helper.AcademicCodeGenerator;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.user.repository.UserRepository;
import tn.esprithub.server.user.dto.UserSummaryDto;
import tn.esprithub.server.user.util.UserMapper;
import tn.esprithub.server.common.enums.UserRole;
import tn.esprithub.server.common.exception.BusinessException;

import java.util.List;
import java.util.UUID;

/**
 * Implementation of AdminAcademicService
 * Provides admin-level operations for managing academic entities
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AdminAcademicServiceImpl implements AdminAcademicService {

    private final DepartementRepository departementRepository;
    private final NiveauRepository niveauRepository;
    private final ClasseRepository classeRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final CourseAssignmentRepository courseAssignmentRepository;
    
    private final DepartementMapper departementMapper;
    private final NiveauMapper niveauMapper;
    private final ClasseMapper classeMapper;
    private final UserMapper userMapper;
    
    private final AcademicValidationHelper validationHelper;
    private final AcademicCodeGenerator codeGenerator;

    // Department operations
    @Override
    @Transactional(readOnly = true)
    public List<DepartementDto> getAllDepartements() {
        log.info("Fetching all departments");
        List<Departement> departements = departementRepository.findAll();
        return departementMapper.toDtoList(departements);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepartementDto> getActiveDepartements() {
        log.info("Fetching active departments");
        List<Departement> departements = departementRepository.findByIsActiveTrue();
        return departementMapper.toDtoList(departements);
    }

    @Override
    @Transactional(readOnly = true)
    public DepartementDto getDepartementById(UUID id) {
        log.info("Fetching department with ID: {}", id);
        Departement departement = departementRepository.findByIdWithNiveaux(id)
                .orElseThrow(() -> new BusinessException("Département non trouvé avec l'ID: " + id));
        return departementMapper.toDtoWithStatistics(departement);
    }

    @Override
    public DepartementDto createDepartement(DepartementDto departementDto) {
        log.info("Creating new department: {}", departementDto.getNom());
        
        // Validate input
        validationHelper.validateDepartementCreation(
            departementDto.getNom(), 
            departementDto.getSpecialite(), 
            departementDto.getTypeFormation()
        );
        
        // Check for duplicate name
        List<Departement> existingDepts = departementRepository.findByNomContainingIgnoreCase(departementDto.getNom());
        if (!existingDepts.isEmpty()) {
            throw new BusinessException("Un département avec ce nom existe déjà");
        }
        
        // Create entity
        Departement departement = departementMapper.toEntity(departementDto);
        
        // Generate code if not provided
        if (departement.getCode() == null) {
            departement.setCode(codeGenerator.generateDepartementCode(departement));
        }
        
        // Check for duplicate code
        if (departementRepository.existsByCode(departement.getCode())) {
            throw new BusinessException("Un département avec ce code existe déjà: " + departement.getCode());
        }
        
        // Save and return
        Departement savedDepartement = departementRepository.save(departement);
        log.info("Successfully created department with ID: {}", savedDepartement.getId());
        
        return departementMapper.toDto(savedDepartement);
    }

    @Override
    public DepartementDto updateDepartement(UUID id, DepartementDto departementDto) {
        log.info("Updating department with ID: {}", id);
        
        Departement existingDepartement = departementRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Département non trouvé avec l'ID: " + id));
        
        // Validate input
        validationHelper.validateDepartementCreation(
            departementDto.getNom(), 
            departementDto.getSpecialite(), 
            departementDto.getTypeFormation()
        );
        
        // Update entity
        departementMapper.updateEntityFromDto(existingDepartement, departementDto);
        
        // Regenerate code if specialite or typeFormation changed
        String newCode = codeGenerator.generateDepartementCode(existingDepartement);
        if (!newCode.equals(existingDepartement.getCode())) {
            if (departementRepository.existsByCode(newCode)) {
                throw new BusinessException("Un département avec le code généré existe déjà: " + newCode);
            }
            existingDepartement.setCode(newCode);
        }
        
        Departement updatedDepartement = departementRepository.save(existingDepartement);
        log.info("Successfully updated department with ID: {}", id);
        
        return departementMapper.toDto(updatedDepartement);
    }

    @Override
    public void deleteDepartement(UUID id) {
        log.info("Deleting department with ID: {}", id);
        
        Departement departement = departementRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Département non trouvé avec l'ID: " + id));
        
        // Check if department has associated data
        if (departement.getNiveaux() != null && !departement.getNiveaux().isEmpty()) {
            throw new BusinessException("Impossible de supprimer le département car il contient des niveaux");
        }
        
        if (departement.getTeachers() != null && !departement.getTeachers().isEmpty()) {
            throw new BusinessException("Impossible de supprimer le département car il contient des enseignants");
        }
        
        departementRepository.delete(departement);
        log.info("Successfully deleted department with ID: {}", id);
    }

    @Override
    public void activateDepartement(UUID id) {
        log.info("Activating department with ID: {}", id);
        updateDepartementStatus(id, true);
    }

    @Override
    public void deactivateDepartement(UUID id) {
        log.info("Deactivating department with ID: {}", id);
        updateDepartementStatus(id, false);
    }

    private void updateDepartementStatus(UUID id, boolean isActive) {
        Departement departement = departementRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Département non trouvé avec l'ID: " + id));
        
        departement.setIsActive(isActive);
        departementRepository.save(departement);
        
        log.info("Successfully {} department with ID: {}", 
            isActive ? "activated" : "deactivated", id);
    }

    @Override
    public DepartementDto assignChiefToDepartement(UUID departementId, UUID chiefId) {
        log.info("Assigning chief {} to department {}", chiefId, departementId);
        
        Departement departement = departementRepository.findById(departementId)
                .orElseThrow(() -> new BusinessException("Département non trouvé avec l'ID: " + departementId));
        
        User chief = userRepository.findById(chiefId)
                .orElseThrow(() -> new BusinessException("Utilisateur non trouvé avec l'ID: " + chiefId));
        
        // Validate chief role
        validationHelper.validateChiefAssignment(chief.getRole());
        
        // Check if chief is already assigned to another department
        if (departementRepository.existsByChiefId(chiefId)) {
            throw new BusinessException("Cet utilisateur est déjà chef d'un autre département");
        }
        
        departement.setChief(chief);
        Departement updatedDepartement = departementRepository.save(departement);
        
        log.info("Successfully assigned chief to department");
        return departementMapper.toDto(updatedDepartement);
    }

    @Override
    public DepartementDto removeChiefFromDepartement(UUID departementId) {
        log.info("Removing chief from department {}", departementId);
        
        Departement departement = departementRepository.findById(departementId)
                .orElseThrow(() -> new BusinessException("Département non trouvé avec l'ID: " + departementId));
        
        departement.setChief(null);
        Departement updatedDepartement = departementRepository.save(departement);
        
        log.info("Successfully removed chief from department");
        return departementMapper.toDto(updatedDepartement);
    }

    // Level operations
    @Override
    @Transactional(readOnly = true)
    public List<NiveauDto> getAllNiveaux() {
        log.info("Fetching all levels");
        List<Niveau> niveaux = niveauRepository.findAll();
        List<NiveauDto> dtos = niveauMapper.toDtoList(niveaux);
        // Correction: calculer le vrai total des classes pour chaque niveau
        for (int i = 0; i < niveaux.size(); i++) {
            Niveau niveau = niveaux.get(i);
            NiveauDto dto = dtos.get(i);
            Long total = classeRepository.countByNiveauIdAndIsActiveTrue(niveau.getId());
            dto.setTotalClasses(total != null ? total.intValue() : 0);
        }
        return dtos;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NiveauDto> getNiveauxByDepartement(UUID departementId) {
        log.info("Fetching levels for department: {}", departementId);
        List<Niveau> niveaux = niveauRepository.findByDepartementIdAndIsActiveTrue(departementId);
        return niveauMapper.toDtoListWithStatistics(niveaux);
    }

    @Override
    @Transactional(readOnly = true)
    public NiveauDto getNiveauById(UUID id) {
        log.info("Fetching level with ID: {}", id);
        Niveau niveau = niveauRepository.findByIdWithClasses(id)
                .orElseThrow(() -> new BusinessException("Niveau non trouvé avec l'ID: " + id));
        return niveauMapper.toDtoWithStatistics(niveau);
    }

    @Override
    public NiveauDto createNiveau(NiveauDto niveauDto) {
        log.info("Creating new level: {}", niveauDto.getNom());
        
        // Validate input
        validationHelper.validateNiveauCreation(
            niveauDto.getNom(), 
            niveauDto.getAnnee(), 
            niveauDto.getDepartementId()
        );
        
        // Check if department exists
        Departement departement = departementRepository.findById(niveauDto.getDepartementId())
                .orElseThrow(() -> new BusinessException("Département non trouvé avec l'ID: " + niveauDto.getDepartementId()));
        
        // Check for duplicate level in the same department
        if (niveauRepository.existsByDepartementIdAndAnnee(niveauDto.getDepartementId(), niveauDto.getAnnee())) {
            throw new BusinessException("Un niveau pour cette année existe déjà dans ce département");
        }
        
        // Create entity
        Niveau niveau = niveauMapper.toEntity(niveauDto);
        niveau.setDepartement(departement);
        
        // Generate code if not provided
        if (niveau.getCode() == null) {
            niveau.setCode(codeGenerator.generateNiveauCode(niveau));
        }
        
        // Check for duplicate code
        if (niveauRepository.existsByCode(niveau.getCode())) {
            throw new BusinessException("Un niveau avec ce code existe déjà: " + niveau.getCode());
        }
        
        // Save and return
        Niveau savedNiveau = niveauRepository.save(niveau);
        log.info("Successfully created level with ID: {}", savedNiveau.getId());
        
        return niveauMapper.toDto(savedNiveau);
    }

    @Override
    public NiveauDto updateNiveau(UUID id, NiveauDto niveauDto) {
        log.info("Updating level with ID: {}", id);
        
        Niveau existingNiveau = niveauRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Niveau non trouvé avec l'ID: " + id));
        
        // Validate input
        validationHelper.validateNiveauCreation(
            niveauDto.getNom(), 
            niveauDto.getAnnee(), 
            existingNiveau.getDepartement().getId()
        );
        
        // Update entity
        niveauMapper.updateEntityFromDto(existingNiveau, niveauDto);
        
        // Regenerate code if year changed
        String newCode = codeGenerator.generateNiveauCode(existingNiveau);
        if (!newCode.equals(existingNiveau.getCode())) {
            if (niveauRepository.existsByCode(newCode)) {
                throw new BusinessException("Un niveau avec le code généré existe déjà: " + newCode);
            }
            existingNiveau.setCode(newCode);
        }
        
        Niveau updatedNiveau = niveauRepository.save(existingNiveau);
        log.info("Successfully updated level with ID: {}", id);
        
        return niveauMapper.toDto(updatedNiveau);
    }

    @Override
    public void deleteNiveau(UUID id) {
        log.info("Deleting level with ID: {}", id);
        
        Niveau niveau = niveauRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Niveau non trouvé avec l'ID: " + id));
        
        // Check if level has associated classes
        if (niveau.getClasses() != null && !niveau.getClasses().isEmpty()) {
            throw new BusinessException("Impossible de supprimer le niveau car il contient des classes");
        }
        
        niveauRepository.delete(niveau);
        log.info("Successfully deleted level with ID: {}", id);
    }

    // Class operations
    @Override
    @Transactional(readOnly = true)
    public List<ClasseDto> getAllClasses() {
        log.info("Fetching all classes");
        List<Classe> classes = classeRepository.findAll();
        return classeMapper.toDtoList(classes);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClasseDto> getClassesByDepartement(UUID departementId) {
        log.info("Fetching classes for department: {}", departementId);
        List<Classe> classes = classeRepository.findByDepartementId(departementId);
        return classeMapper.toDtoListWithStatistics(classes);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClasseDto> getClassesByNiveau(UUID niveauId) {
        log.info("Fetching classes for level: {}", niveauId);
        List<Classe> classes = classeRepository.findByNiveauIdAndIsActiveTrue(niveauId);
        return classeMapper.toDtoListWithStatistics(classes);
    }

    @Override
    @Transactional(readOnly = true)
    public ClasseDto getClasseById(UUID id) {
        log.info("Fetching class with ID: {}", id);
        Classe classe = classeRepository.findByIdWithNiveauAndDepartement(id)
                .orElseThrow(() -> new BusinessException("Classe non trouvée avec l'ID: " + id));
        return classeMapper.toDtoWithStatistics(classe);
    }

    @Override
    public ClasseDto createClasse(ClasseDto classeDto) {
        log.info("Creating new class: {}", classeDto.getNom());
        
        // Validate input
        validationHelper.validateClasseCreation(
            classeDto.getNom(), 
            classeDto.getCapacite(), 
            classeDto.getNiveauId()
        );
        
        // Check if level exists
        Niveau niveau = niveauRepository.findById(classeDto.getNiveauId())
                .orElseThrow(() -> new BusinessException("Niveau non trouvé avec l'ID: " + classeDto.getNiveauId()));
        
        // Check for duplicate class name in the same level
        if (classeRepository.existsByNiveauIdAndNom(classeDto.getNiveauId(), classeDto.getNom())) {
            throw new BusinessException("Une classe avec ce nom existe déjà dans ce niveau");
        }
        
        // Create entity
        Classe classe = classeMapper.toEntity(classeDto);
        classe.setNiveau(niveau);
        
        // Generate code if not provided
        if (classe.getCode() == null) {
            classe.setCode(codeGenerator.generateClasseCode(classe));
        }
        // Final safeguard: truncate code to 15 chars
        if (classe.getCode() != null && classe.getCode().length() > 15) {
            classe.setCode(classe.getCode().substring(0, 15));
        }
        // Check for duplicate code
        if (classeRepository.existsByCode(classe.getCode())) {
            throw new BusinessException("Une classe avec ce code existe déjà: " + classe.getCode());
        }
        
        // Save and return
        Classe savedClasse = classeRepository.save(classe);
        log.info("Successfully created class with ID: {}", savedClasse.getId());
        
        return classeMapper.toDto(savedClasse);
    }

    @Override
    public ClasseDto updateClasse(UUID id, ClasseDto classeDto) {
        log.info("Updating class with ID: {}", id);
        
        Classe existingClasse = classeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Classe non trouvée avec l'ID: " + id));
        
        // Validate input
        validationHelper.validateClasseCreation(
            classeDto.getNom(), 
            classeDto.getCapacite(), 
            existingClasse.getNiveau().getId()
        );
        
        // Update entity
        classeMapper.updateEntityFromDto(existingClasse, classeDto);
        
        // Regenerate code if name changed
        String newCode = codeGenerator.generateClasseCode(existingClasse);
        if (!newCode.equals(existingClasse.getCode())) {
            // Final safeguard: truncate code to 15 chars
            if (newCode != null && newCode.length() > 15) {
                newCode = newCode.substring(0, 15);
            }
            if (classeRepository.existsByCode(newCode)) {
                throw new BusinessException("Une classe avec le code généré existe déjà: " + newCode);
            }
            existingClasse.setCode(newCode);
        }
        
        Classe updatedClasse = classeRepository.save(existingClasse);
        log.info("Successfully updated class with ID: {}", id);
        
        return classeMapper.toDto(updatedClasse);
    }

    @Override
    public void deleteClasse(UUID id) {
        log.info("Deleting class with ID: {}", id);
        
        Classe classe = classeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Classe non trouvée avec l'ID: " + id));
        
        // Check if class has associated students or teachers
        if (classe.getStudents() != null && !classe.getStudents().isEmpty()) {
            throw new BusinessException("Impossible de supprimer la classe car elle contient des étudiants");
        }
        
        if (classe.getTeachers() != null && !classe.getTeachers().isEmpty()) {
            throw new BusinessException("Impossible de supprimer la classe car elle a des enseignants assignés");
        }
        
        classeRepository.delete(classe);
        log.info("Successfully deleted class with ID: {}", id);
    }

    // User assignment operations will be continued in next part...
    @Override
    public ClasseDto assignStudentsToClasse(UUID classeId, List<UUID> studentIds) {
        // Implementation will be added
        throw new BusinessException("Not implemented yet");
    }

    @Override
    public ClasseDto removeStudentsFromClasse(UUID classeId, List<UUID> studentIds) {
        // Implementation will be added
        throw new BusinessException("Not implemented yet");
    }

    @Override
    public ClasseDto assignTeachersToClasse(UUID classeId, List<UUID> teacherIds) {
        // Implementation will be added
        throw new BusinessException("Not implemented yet");
    }

    @Override
    public ClasseDto removeTeachersFromClasse(UUID classeId, List<UUID> teacherIds) {
        // Implementation will be added
        throw new BusinessException("Not implemented yet");
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepartementDto> getDepartementsWithStatistics() {
        log.info("Fetching departments with statistics");
        List<Departement> departements = departementRepository.findAllActiveWithChief();
        return departementMapper.toDtoListWithStatistics(departements);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSummaryDto> getUnassignedUsers() {
        log.info("Fetching unassigned users");
        // Users without department, class assignments, etc.
        // Implementation will depend on User entity relationships
        List<User> users = userRepository.findAll(); // Placeholder
        return userMapper.toUserSummaryDtoList(users);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSummaryDto> getUsersByRole(String role) {
        log.info("Fetching users by role: {}", role);
        UserRole userRole = UserRole.valueOf(role.toUpperCase());
        List<User> users = userRepository.findByRole(userRole);
        return userMapper.toUserSummaryDtoList(users);
    }

    // Helper methods for class creation workflow
    
    @Override
    @Transactional(readOnly = true)
    public List<DepartementSummaryDto> getDepartementsSummary() {
        log.info("Fetching departments summary for class creation");
        List<Departement> departements = departementRepository.findByIsActiveTrue();
        return departements.stream()
                .map(this::toDepartementSummaryDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<NiveauSummaryDto> getNiveauxSummaryByDepartement(UUID departementId) {
        log.info("Fetching niveaux summary for department: {}", departementId);
        
        // Validate department exists
        departementRepository.findById(departementId)
                .orElseThrow(() -> new BusinessException("Département non trouvé avec l'ID: " + departementId));
        
        List<Niveau> niveaux = niveauRepository.findByDepartementIdAndIsActiveTrue(departementId);
        return niveaux.stream()
                .map(this::toNiveauSummaryDto)
                .toList();
    }

    @Override
    public ClasseDto createClasseForNiveau(UUID departementId, UUID niveauId, CreateClasseDto createClasseDto) {
        log.info("Creating class for department: {} and niveau: {}", departementId, niveauId);
        
        // Validate department exists  
        departementRepository.findById(departementId)
                .orElseThrow(() -> new BusinessException("Département non trouvé avec l'ID: " + departementId));
        
        // Validate niveau exists and belongs to department
        Niveau niveau = niveauRepository.findById(niveauId)
                .orElseThrow(() -> new BusinessException("Niveau non trouvé avec l'ID: " + niveauId));
        
        if (!niveau.getDepartement().getId().equals(departementId)) {
            throw new BusinessException("Le niveau ne fait pas partie du département spécifié");
        }
        
        // Create class entity
        Classe classe = Classe.builder()
                .nom(createClasseDto.getNom())
                .description(createClasseDto.getDescription())
                .capacite(createClasseDto.getCapacite())
                .code(createClasseDto.getCode() != null ? createClasseDto.getCode() : 
                      niveau.getCode() + "_" + createClasseDto.getNom().replaceAll("\\s+", "").toUpperCase())
                .isActive(createClasseDto.getIsActive())
                .niveau(niveau)
                .build();
        
        // Save and return
        Classe savedClasse = classeRepository.save(classe);
        log.info("Created class: {} with ID: {}", savedClasse.getNom(), savedClasse.getId());
        
        return classeMapper.toDto(savedClasse);
    }

    // Helper mapping methods
    
    private DepartementSummaryDto toDepartementSummaryDto(Departement departement) {
        Long totalNiveaux = niveauRepository.countByDepartementIdAndIsActiveTrue(departement.getId());
        
        return DepartementSummaryDto.builder()
                .id(departement.getId())
                .nom(departement.getNom())
                .code(departement.getCode())
                .specialite(departement.getSpecialite())
                .typeFormation(departement.getTypeFormation())
                .isActive(departement.getIsActive())
                .totalNiveaux(totalNiveaux.intValue())
                .build();
    }
    
    private NiveauSummaryDto toNiveauSummaryDto(Niveau niveau) {
        Long totalClasses = classeRepository.countByNiveauIdAndIsActiveTrue(niveau.getId());
        
        return NiveauSummaryDto.builder()
                .id(niveau.getId())
                .nom(niveau.getNom())
                .code(niveau.getCode())
                .annee(niveau.getAnnee())
                .isActive(niveau.getIsActive())
                .departementId(niveau.getDepartement().getId())
                .departementNom(niveau.getDepartement().getNom())
                .totalClasses(totalClasses.intValue())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseDto> getCoursesByNiveau(UUID niveauId) {
        Niveau niveau = niveauRepository.findById(niveauId)
                .orElseThrow(() -> new BusinessException("Niveau not found"));
        return courseRepository.findByNiveau(niveau)
                .stream()
                .map(course -> CourseDto.builder()
                        .id(course.getId())
                        .name(course.getName())
                        .description(course.getDescription())
                        .niveauId(niveauId)
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseAssignmentDto> getCourseAssignmentsByNiveau(UUID niveauId) {
        Niveau niveau = niveauRepository.findById(niveauId)
                .orElseThrow(() -> new BusinessException("Niveau not found"));
        return courseAssignmentRepository.findByNiveau(niveau)
                .stream()
                .map(assignment -> CourseAssignmentDto.builder()
                        .id(assignment.getId())
                        .courseId(assignment.getCourse().getId())
                        .courseName(assignment.getCourse().getName())
                        .niveauId(niveauId)
                        .teacherId(assignment.getTeacher().getId())
                        .teacherName(assignment.getTeacher().getFullName())
                        .build())
                .toList();
    }

    @Override
    public CourseAssignmentDto createCourseAssignment(CourseAssignmentDto dto) {
        Course course = courseRepository.findById(dto.getCourseId())
                .orElseThrow(() -> new BusinessException("Course not found"));
        Niveau niveau = niveauRepository.findById(dto.getNiveauId())
                .orElseThrow(() -> new BusinessException("Niveau not found"));
        User teacher = userRepository.findById(dto.getTeacherId())
                .orElseThrow(() -> new BusinessException("Teacher not found"));
        var assignment = new tn.esprithub.server.academic.entity.CourseAssignment();
        assignment.setCourse(course);
        assignment.setNiveau(niveau);
        assignment.setTeacher(teacher);
        var saved = courseAssignmentRepository.save(assignment);
        return CourseAssignmentDto.builder()
                .id(saved.getId())
                .courseId(course.getId())
                .courseName(course.getName())
                .niveauId(niveau.getId())
                .teacherId(teacher.getId())
                .teacherName(teacher.getFullName())
                .build();
    }

    @Override
    public void deleteCourseAssignment(UUID assignmentId) {
        courseAssignmentRepository.deleteById(assignmentId);
    }

    @Override
    public CourseDto createCourse(CourseDto courseDto) {
        Niveau niveau = niveauRepository.findById(courseDto.getNiveauId())
                .orElseThrow(() -> new BusinessException("Niveau not found"));
        Course course = Course.builder()
                .name(courseDto.getName())
                .description(courseDto.getDescription())
                .niveau(niveau)
                .build();
        Course saved = courseRepository.save(course);
        return CourseDto.builder()
                .id(saved.getId())
                .name(saved.getName())
                .description(saved.getDescription())
                .niveauId(niveau.getId())
                .build();
    }

    @Override
    public void deleteCourse(UUID id) {
        log.info("Deleting course with ID: {}", id);
        Course course = courseRepository.findById(id)
            .orElseThrow(() -> new BusinessException("Course not found with ID: " + id));
        courseRepository.delete(course);
        log.info("Successfully deleted course with ID: {}", id);
    }

    @Override
    public CourseDto updateCourse(UUID id, CourseDto courseDto) {
        log.info("Updating course with ID: {}", id);
        Course course = courseRepository.findById(id)
            .orElseThrow(() -> new BusinessException("Course not found with ID: " + id));
        course.setName(courseDto.getName());
        course.setDescription(courseDto.getDescription());
        if (courseDto.getNiveauId() != null && (course.getNiveau() == null || !course.getNiveau().getId().equals(courseDto.getNiveauId()))) {
            Niveau niveau = niveauRepository.findById(courseDto.getNiveauId())
                .orElseThrow(() -> new BusinessException("Niveau not found with ID: " + courseDto.getNiveauId()));
            course.setNiveau(niveau);
        }
        Course updated = courseRepository.save(course);
        return CourseDto.builder()
            .id(updated.getId())
            .name(updated.getName())
            .description(updated.getDescription())
            .niveauId(updated.getNiveau().getId())
            .build();
    }
}
