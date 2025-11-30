package tn.esprithub.server.academic.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprithub.server.academic.dto.DepartementDto;
import tn.esprithub.server.academic.dto.NiveauDto;
import tn.esprithub.server.academic.dto.ClasseDto;
import tn.esprithub.server.academic.entity.Departement;
import tn.esprithub.server.academic.entity.Niveau;
import tn.esprithub.server.academic.entity.Classe;
import tn.esprithub.server.academic.repository.DepartementRepository;
import tn.esprithub.server.academic.repository.NiveauRepository;
import tn.esprithub.server.academic.repository.ClasseRepository;
import tn.esprithub.server.academic.service.ChiefAcademicService;
import tn.esprithub.server.academic.util.mapper.DepartementMapper;
import tn.esprithub.server.academic.util.mapper.NiveauMapper;
import tn.esprithub.server.academic.util.mapper.ClasseMapper;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.user.repository.UserRepository;
import tn.esprithub.server.user.dto.UserSummaryDto;
import tn.esprithub.server.user.util.UserMapper;
import tn.esprithub.server.common.enums.UserRole;
import tn.esprithub.server.common.exception.BusinessException;
import tn.esprithub.server.academic.dto.ChiefNotificationDto;
import tn.esprithub.server.project.entity.Task;
import tn.esprithub.server.project.repository.TaskRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of ChiefAcademicService
 * Provides chief-level operations restricted to their assigned department
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ChiefAcademicServiceImpl implements ChiefAcademicService {

    private final DepartementRepository departementRepository;
    private final NiveauRepository niveauRepository;
    private final ClasseRepository classeRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    
    private final DepartementMapper departementMapper;
    private final NiveauMapper niveauMapper;
    private final ClasseMapper classeMapper;
    private final UserMapper userMapper;

    @Override
    @Transactional(readOnly = true)
    public DepartementDto getMyDepartement(UUID chiefId) {
        log.info("Getting department for chief with ID: {}", chiefId);
        
        User chief = getUserAndValidateRole(chiefId, UserRole.CHIEF);
        Departement department = getDepartementByChief(chief);
        
        return departementMapper.toDto(department);
    }

    @Override
    public DepartementDto updateMyDepartement(UUID chiefId, DepartementDto departementDto) {
        log.info("Updating department for chief with ID: {}", chiefId);
        
        User chief = getUserAndValidateRole(chiefId, UserRole.CHIEF);
        Departement department = getDepartementByChief(chief);
        
        // Update only allowed fields (chief cannot change speciality or code)
        department.setNom(departementDto.getNom());
        department.setDescription(departementDto.getDescription());
        
        Departement savedDepartment = departementRepository.save(department);
        log.info("Department updated successfully: {}", savedDepartment.getNom());
        
        return departementMapper.toDto(savedDepartment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NiveauDto> getMyDepartementNiveaux(UUID chiefId) {
        log.info("Getting niveaux for chief department with chief ID: {}", chiefId);
        
        User chief = getUserAndValidateRole(chiefId, UserRole.CHIEF);
        Departement department = getDepartementByChief(chief);
        
        List<Niveau> niveaux = niveauRepository.findByDepartementIdAndIsActiveTrue(department.getId());
        return niveaux.stream()
                .map(niveauMapper::toDto)
                .toList();
    }

    @Override
    public NiveauDto createNiveauInMyDepartement(UUID chiefId, NiveauDto niveauDto) {
        log.info("Creating niveau in chief department for chief ID: {}", chiefId);
        
        User chief = getUserAndValidateRole(chiefId, UserRole.CHIEF);
        Departement department = getDepartementByChief(chief);
        
        // Ignore any departementId sent by the frontend (chief context)
        niveauDto.setDepartementId(null);
        
        // Validate that niveau with same year doesn't exist in this department
        if (niveauRepository.existsByDepartementIdAndAnnee(department.getId(), niveauDto.getAnnee())) {
            throw new BusinessException("Un niveau avec cette année existe déjà dans votre département");
        }
        
        Niveau niveau = niveauMapper.toEntity(niveauDto);
        niveau.setDepartement(department);
        
        Niveau savedNiveau = niveauRepository.save(niveau);
        log.info("Niveau created successfully: {} for department: {}", savedNiveau.getNom(), department.getNom());
        
        return niveauMapper.toDto(savedNiveau);
    }

    @Override
    public NiveauDto updateNiveauInMyDepartement(UUID chiefId, UUID niveauId, NiveauDto niveauDto) {
        log.info("Updating niveau {} for chief ID: {}", niveauId, chiefId);
        
        User chief = getUserAndValidateRole(chiefId, UserRole.CHIEF);
        Departement department = getDepartementByChief(chief);
        
        Niveau niveau = niveauRepository.findById(niveauId)
                .orElseThrow(() -> new BusinessException("Niveau non trouvé"));
        
        // Validate that niveau belongs to chief's department
        if (!niveau.getDepartement().getId().equals(department.getId())) {
            throw new BusinessException("Vous n'êtes pas autorisé à modifier ce niveau");
        }
        
        niveau.setNom(niveauDto.getNom());
        niveau.setDescription(niveauDto.getDescription());
        niveau.setAnnee(niveauDto.getAnnee());
        
        Niveau savedNiveau = niveauRepository.save(niveau);
        log.info("Niveau updated successfully: {}", savedNiveau.getNom());
        
        return niveauMapper.toDto(savedNiveau);
    }

    @Override
    public void deleteNiveauInMyDepartement(UUID chiefId, UUID niveauId) {
        log.info("Deleting niveau {} for chief ID: {}", niveauId, chiefId);
        
        User chief = getUserAndValidateRole(chiefId, UserRole.CHIEF);
        Departement department = getDepartementByChief(chief);
        
        Niveau niveau = niveauRepository.findById(niveauId)
                .orElseThrow(() -> new BusinessException("Niveau non trouvé"));
        
        // Validate that niveau belongs to chief's department
        if (!niveau.getDepartement().getId().equals(department.getId())) {
            throw new BusinessException("Vous n'êtes pas autorisé à supprimer ce niveau");
        }
        
        // Check if niveau has classes
        if (!niveau.getClasses().isEmpty()) {
            throw new BusinessException("Impossible de supprimer un niveau qui contient des classes");
        }
        
        niveau.setIsActive(false);
        niveauRepository.save(niveau);
        log.info("Niveau deleted successfully: {}", niveau.getNom());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClasseDto> getMyDepartementClasses(UUID chiefId) {
        log.info("Getting all classes for chief department with chief ID: {}", chiefId);
        
        User chief = getUserAndValidateRole(chiefId, UserRole.CHIEF);
        Departement department = getDepartementByChief(chief);
        
        List<Classe> classes = classeRepository.findByDepartementId(department.getId());
        return classes.stream()
                .map(classeMapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClasseDto> getClassesByNiveauInMyDepartement(UUID chiefId, UUID niveauId) {
        log.info("Getting classes for niveau {} in chief department with chief ID: {}", niveauId, chiefId);
        
        User chief = getUserAndValidateRole(chiefId, UserRole.CHIEF);
        Departement department = getDepartementByChief(chief);
        
        // Validate that niveau belongs to chief's department
        Niveau niveau = niveauRepository.findById(niveauId)
                .orElseThrow(() -> new BusinessException("Niveau non trouvé"));
        
        if (!niveau.getDepartement().getId().equals(department.getId())) {
            throw new BusinessException("Ce niveau n'appartient pas à votre département");
        }
        
        List<Classe> classes = classeRepository.findByNiveauIdAndIsActiveTrue(niveauId);
        return classes.stream()
                .map(classeMapper::toDto)
                .toList();
    }

    @Override
    public ClasseDto createClasseInMyDepartement(UUID chiefId, ClasseDto classeDto) {
        log.info("Creating classe in chief department for chief ID: {}", chiefId);
        
        User chief = getUserAndValidateRole(chiefId, UserRole.CHIEF);
        Departement department = getDepartementByChief(chief);
        
        // Ignore any departementId sent by the frontend (chief context)
        classeDto.setDepartementId(null);
        
        // Validate that niveau belongs to chief's department
        Niveau niveau = niveauRepository.findById(classeDto.getNiveauId())
                .orElseThrow(() -> new BusinessException("Niveau non trouvé"));
        
        if (!niveau.getDepartement().getId().equals(department.getId())) {
            throw new BusinessException("Ce niveau n'appartient pas à votre département");
        }
        
        // Validate that classe with same name doesn't exist in this niveau
        if (classeRepository.existsByNiveauIdAndNom(niveau.getId(), classeDto.getNom())) {
            throw new BusinessException("Une classe avec ce nom existe déjà dans ce niveau");
        }
        
        Classe classe = classeMapper.toEntity(classeDto);
        classe.setNiveau(niveau);
        
        Classe savedClasse = classeRepository.save(classe);
        log.info("Classe created successfully: {} for niveau: {}", savedClasse.getNom(), niveau.getNom());
        
        return classeMapper.toDto(savedClasse);
    }

    @Override
    public ClasseDto updateClasseInMyDepartement(UUID chiefId, UUID classeId, ClasseDto classeDto) {
        log.info("Updating classe {} for chief ID: {}", classeId, chiefId);
        
        User chief = getUserAndValidateRole(chiefId, UserRole.CHIEF);
        Departement department = getDepartementByChief(chief);
        
        Classe classe = classeRepository.findById(classeId)
                .orElseThrow(() -> new BusinessException("Classe non trouvée"));
        
        // Validate that classe belongs to chief's department
        if (!classe.getNiveau().getDepartement().getId().equals(department.getId())) {
            throw new BusinessException("Vous n'êtes pas autorisé à modifier cette classe");
        }
        
        classe.setNom(classeDto.getNom());
        classe.setDescription(classeDto.getDescription());
        classe.setCapacite(classeDto.getCapacite());
        
        Classe savedClasse = classeRepository.save(classe);
        log.info("Classe updated successfully: {}", savedClasse.getNom());
        
        return classeMapper.toDto(savedClasse);
    }

    @Override
    public void deleteClasseInMyDepartement(UUID chiefId, UUID classeId) {
        log.info("Deleting classe {} for chief ID: {}", classeId, chiefId);
        
        User chief = getUserAndValidateRole(chiefId, UserRole.CHIEF);
        Departement department = getDepartementByChief(chief);
        
        Classe classe = classeRepository.findById(classeId)
                .orElseThrow(() -> new BusinessException("Classe non trouvée"));
        
        // Validate that classe belongs to chief's department
        if (!classe.getNiveau().getDepartement().getId().equals(department.getId())) {
            throw new BusinessException("Vous n'êtes pas autorisé à supprimer cette classe");
        }
        
        // Check if classe has students
        if (!classe.getStudents().isEmpty()) {
            throw new BusinessException("Impossible de supprimer une classe qui contient des étudiants");
        }
        
        classe.setIsActive(false);
        classeRepository.save(classe);
        log.info("Classe deleted successfully: {}", classe.getNom());
    }

    @Override
    public ClasseDto assignStudentsToClasseInMyDepartement(UUID chiefId, UUID classeId, List<UUID> studentIds) {
        log.info("Assigning {} students to classe {} for chief ID: {}", studentIds.size(), classeId, chiefId);
        
        User chief = getUserAndValidateRole(chiefId, UserRole.CHIEF);
        Departement department = getDepartementByChief(chief);
        
        Classe classe = getClasseInChiefDepartment(classeId, department);
        
        List<User> students = userRepository.findAllById(studentIds);
        if (students.size() != studentIds.size()) {
            throw new BusinessException("Certains étudiants n'ont pas été trouvés");
        }
        
        // Validate all users are students
        students.forEach(student -> {
            if (!student.getRole().equals(UserRole.STUDENT)) {
                throw new BusinessException("L'utilisateur " + student.getEmail() + " n'est pas un étudiant");
            }
        });
        
        classe.getStudents().addAll(students);
        students.forEach(student -> student.setClasse(classe));
        
        Classe savedClasse = classeRepository.save(classe);
        userRepository.saveAll(students);
        
        log.info("Students assigned successfully to classe: {}", savedClasse.getNom());
        return classeMapper.toDto(savedClasse);
    }

    @Override
    public ClasseDto removeStudentsFromClasseInMyDepartement(UUID chiefId, UUID classeId, List<UUID> studentIds) {
        log.info("Removing {} students from classe {} for chief ID: {}", studentIds.size(), classeId, chiefId);
        
        User chief = getUserAndValidateRole(chiefId, UserRole.CHIEF);
        Departement department = getDepartementByChief(chief);
        
        Classe classe = getClasseInChiefDepartment(classeId, department);
        
        List<User> students = userRepository.findAllById(studentIds);
        classe.getStudents().removeAll(students);
        students.forEach(student -> student.setClasse(null));
        
        Classe savedClasse = classeRepository.save(classe);
        userRepository.saveAll(students);
        
        log.info("Students removed successfully from classe: {}", savedClasse.getNom());
        return classeMapper.toDto(savedClasse);
    }

    @Override
    public ClasseDto assignTeachersToClasseInMyDepartement(UUID chiefId, UUID classeId, List<UUID> teacherIds) {
        log.info("Assigning {} teachers to classe {} for chief ID: {}", teacherIds.size(), classeId, chiefId);
        
        User chief = getUserAndValidateRole(chiefId, UserRole.CHIEF);
        Departement department = getDepartementByChief(chief);
        
        Classe classe = getClasseInChiefDepartment(classeId, department);
        
        List<User> teachers = userRepository.findAllById(teacherIds);
        if (teachers.size() != teacherIds.size()) {
            throw new BusinessException("Certains enseignants n'ont pas été trouvés");
        }
        
        // Validate all users are teachers and belong to the same department
        teachers.forEach(teacher -> {
            if (!teacher.getRole().equals(UserRole.TEACHER)) {
                throw new BusinessException("L'utilisateur " + teacher.getEmail() + " n'est pas un enseignant");
            }
            if (!teacher.getDepartement().getId().equals(department.getId())) {
                throw new BusinessException("L'enseignant " + teacher.getEmail() + " n'appartient pas à votre département");
            }
        });
        
        classe.getTeachers().addAll(teachers);
        
        Classe savedClasse = classeRepository.save(classe);
        log.info("Teachers assigned successfully to classe: {}", savedClasse.getNom());
        return classeMapper.toDto(savedClasse);
    }

    @Override
    public ClasseDto removeTeachersFromClasseInMyDepartement(UUID chiefId, UUID classeId, List<UUID> teacherIds) {
        log.info("Removing {} teachers from classe {} for chief ID: {}", teacherIds.size(), classeId, chiefId);
        
        User chief = getUserAndValidateRole(chiefId, UserRole.CHIEF);
        Departement department = getDepartementByChief(chief);
        
        Classe classe = getClasseInChiefDepartment(classeId, department);
        
        List<User> teachers = userRepository.findAllById(teacherIds);
        classe.getTeachers().removeAll(teachers);
        
        Classe savedClasse = classeRepository.save(classe);
        log.info("Teachers removed successfully from classe: {}", savedClasse.getNom());
        return classeMapper.toDto(savedClasse);
    }

    @Override
    @Transactional(readOnly = true)
    public DepartementDto getMyDepartementWithStatistics(UUID chiefId) {
        log.info("Getting department with statistics for chief ID: {}", chiefId);
        
        User chief = getUserAndValidateRole(chiefId, UserRole.CHIEF);
        Departement department = getDepartementByChief(chief);
        
        DepartementDto departmentDto = departementMapper.toDto(department);
        
        // Add statistics
        long totalTeachers = userRepository.countTeachersByDepartement(department.getId());
        long totalStudents = userRepository.countStudentsByDepartement(department.getId());
        int totalNiveaux = niveauRepository.findByDepartementIdAndIsActiveTrue(department.getId()).size();
        int totalClasses = classeRepository.findByDepartementId(department.getId()).size();
        
        // Note: You might want to add these fields to DepartementDto if they don't exist
        log.info("Department statistics - Teachers: {}, Students: {}, Niveaux: {}, Classes: {}", 
                totalTeachers, totalStudents, totalNiveaux, totalClasses);
        
        return departmentDto;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSummaryDto> getUnassignedUsersInMyDepartement(UUID chiefId) {
        log.info("Getting unassigned users for chief department with chief ID: {}", chiefId);
        
        User chief = getUserAndValidateRole(chiefId, UserRole.CHIEF);
        Departement department = getDepartementByChief(chief);
        
        List<User> unassignedStudents = userRepository.findUnassignedStudents();
        List<User> unassignedTeachers = userRepository.findUnassignedUsersByRole(UserRole.TEACHER);
        
        // Filter by department for teachers
        unassignedTeachers = unassignedTeachers.stream()
                .filter(teacher -> teacher.getDepartement() != null && 
                                 teacher.getDepartement().getId().equals(department.getId()))
                .collect(Collectors.toList());
        
        unassignedStudents.addAll(unassignedTeachers);
        
        return unassignedStudents.stream()
                .map(userMapper::toUserSummaryDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSummaryDto> getTeachersInMyDepartement(UUID chiefId) {
        log.info("Getting teachers for chief department with chief ID: {}", chiefId);
        
        User chief = getUserAndValidateRole(chiefId, UserRole.CHIEF);
        Departement department = getDepartementByChief(chief);
        
        List<User> teachers = userRepository.findByDepartementIdAndRole(department.getId(), UserRole.TEACHER);
        return teachers.stream()
                .map(userMapper::toUserSummaryDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSummaryDto> getStudentsInMyDepartement(UUID chiefId) {
        log.info("Getting students for chief department with chief ID: {}", chiefId);
        
        User chief = getUserAndValidateRole(chiefId, UserRole.CHIEF);
        Departement department = getDepartementByChief(chief);
        
        // Get all students in classes that belong to this department's niveaux
        List<User> students = userRepository.findAll().stream()
                .filter(user -> user.getRole().equals(UserRole.STUDENT))
                .filter(user -> user.getClasse() != null)
                .filter(user -> user.getClasse().getNiveau().getDepartement().getId().equals(department.getId()))
                .toList();
        
        return students.stream()
                .map(userMapper::toUserSummaryDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChiefNotificationDto> getRecentNotificationsForChief(UUID chiefId, int limit) {
        Departement dept = getDepartementByChief(getUserAndValidateRole(chiefId, UserRole.CHIEF));
        List<Niveau> niveaux = dept.getNiveaux();
        List<UUID> classeIds = new java.util.ArrayList<>();
        for (Niveau n : niveaux) {
            if (n.getClasses() != null) {
                for (Classe c : n.getClasses()) {
                    classeIds.add(c.getId());
                }
            }
        }
        // Récupérer toutes les tâches assignées à ces classes
        List<Task> allTasks = new java.util.ArrayList<>();
        for (UUID classeId : classeIds) {
            allTasks.addAll(taskRepository.findByAssignedToClasses_Id(classeId));
        }
        // Trier par date de création décroissante
        allTasks.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        // Prendre les N plus récentes
        List<Task> recentTasks = allTasks.stream().limit(limit).toList();

        List<ChiefNotificationDto> notifications = new java.util.ArrayList<>();
        for (Task task : recentTasks) {
            ChiefNotificationDto notif = new ChiefNotificationDto();
            notif.setIcon("fas fa-tasks");
            notif.setText("Task: " + task.getTitle() + " (due: " + task.getDueDate() + ")");
            notif.setDate(task.getCreatedAt());
            notifications.add(notif);
        }
        return notifications;
    }

    // Helper methods
    
    private User getUserAndValidateRole(UUID userId, UserRole expectedRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Utilisateur non trouvé"));
        
        if (!user.getRole().equals(expectedRole)) {
            throw new BusinessException("Accès non autorisé - rôle requis: " + expectedRole);
        }
        
        if (!user.getIsActive()) {
            throw new BusinessException("Compte utilisateur désactivé");
        }
        
        return user;
    }
    
    private Departement getDepartementByChief(User chief) {
        return departementRepository.findByChiefId(chief.getId())
                .orElseThrow(() -> new BusinessException("Aucun département assigné à ce chef"));
    }
    
    private Classe getClasseInChiefDepartment(UUID classeId, Departement department) {
        Classe classe = classeRepository.findById(classeId)
                .orElseThrow(() -> new BusinessException("Classe non trouvée"));
        
        if (!classe.getNiveau().getDepartement().getId().equals(department.getId())) {
            throw new BusinessException("Cette classe n'appartient pas à votre département");
        }
        
        return classe;
    }
}
