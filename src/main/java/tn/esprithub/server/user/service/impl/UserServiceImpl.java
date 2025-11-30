package tn.esprithub.server.user.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.user.dto.UserDto;
import tn.esprithub.server.user.dto.CreateUserDto;
import tn.esprithub.server.user.dto.UpdateUserDto;
import tn.esprithub.server.user.dto.UserSummaryDto;
import tn.esprithub.server.user.repository.UserRepository;
import tn.esprithub.server.user.service.UserService;
import tn.esprithub.server.user.util.UserMapper;
import tn.esprithub.server.academic.entity.Departement;
import tn.esprithub.server.academic.entity.Classe;
import tn.esprithub.server.academic.repository.DepartementRepository;
import tn.esprithub.server.academic.repository.ClasseRepository;
import tn.esprithub.server.common.enums.UserRole;
import tn.esprithub.server.common.exception.BusinessException;
import tn.esprithub.server.email.EmailService;

import java.util.List;
import java.util.UUID;

/**
 * Implementation of UserService with proper authorization and validation
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final DepartementRepository departementRepository;
    private final ClasseRepository classeRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    // ========== CRUD OPERATIONS ==========

    @Override
    public UserDto createUser(CreateUserDto createUserDto) {
        log.info("Creating new user: {}", createUserDto.getEmail());
        
        // Validate email uniqueness
        if (userRepository.existsByEmail(createUserDto.getEmail())) {
            throw new BusinessException("Un utilisateur avec cet email existe déjà: " + createUserDto.getEmail());
        }
        
        // Convert to entity and encode password
        User user = userMapper.toUserEntity(createUserDto);
        String pass = user.getPassword();
        user.setPassword(passwordEncoder.encode(createUserDto.getPassword()));
             user.setIsEmailVerified(true);

        // Handle academic assignments
        handleAcademicAssignments(user, createUserDto.getDepartementId(), createUserDto.getClasseId());
        User savedUser = userRepository.save(user);
        emailService.sendCredentialsEmail(user.getEmail(), user.getUsername(), pass);


        log.info("Successfully created user with ID: {}", savedUser.getId());
        
        return userMapper.toUserDto(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto getUserById(UUID id) {
        log.info("Fetching user by ID: {}", id);
        User user = userRepository.findByIdWithDepartement(id)
                .orElseThrow(() -> new BusinessException("Utilisateur non trouvé avec l'ID: " + id));
        return userMapper.toUserDto(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDto> getUsersByIds(List<UUID> ids) {
        log.info("Fetching users by IDs: {}", ids);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        
        List<User> users = userRepository.findAllById(ids);
        return userMapper.toUserDtoList(users);
    }

    @Override
    public UserDto updateUser(UUID id, UpdateUserDto updateUserDto) {
        log.info("Updating user ID: {}", id);
        
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Utilisateur non trouvé avec l'ID: " + id));
        
        // Check email uniqueness if email is being changed
        if (updateUserDto.getEmail() != null && 
            !updateUserDto.getEmail().equals(existingUser.getEmail()) &&
            userRepository.existsByEmail(updateUserDto.getEmail())) {
            throw new BusinessException("Un utilisateur avec cet email existe déjà: " + updateUserDto.getEmail());
        }
        
        // Update user fields
        userMapper.updateUserFromDto(existingUser, updateUserDto);
        
        // Handle academic assignments
        handleAcademicAssignments(existingUser, updateUserDto.getDepartementId(), updateUserDto.getClasseId());
        
        User updatedUser = userRepository.save(existingUser);
        log.info("Successfully updated user ID: {}", id);
        
        return userMapper.toUserDto(updatedUser);
    }

    @Override
    public void deleteUser(UUID id) {
        log.info("Deleting user ID: {}", id);
        
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Utilisateur non trouvé avec l'ID: " + id));
        
        // Check if user is a chief of a department
        if (user.getChiefOfDepartement() != null) {
            throw new BusinessException("Impossible de supprimer un chef de département. Veuillez d'abord réassigner le département.");
        }
        
        // Check if user has teaching assignments
        if (user.getTeachingClasses() != null && !user.getTeachingClasses().isEmpty()) {
            throw new BusinessException("Impossible de supprimer un enseignant assigné à des classes. Veuillez d'abord réassigner les classes.");
        }
        
        userRepository.delete(user);
        log.info("Successfully deleted user ID: {}", id);
    }

    // ========== USER LISTING ==========

    @Override
    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers() {
        log.info("Fetching all users");
        List<User> users = userRepository.findAll();
        return userMapper.toUserDtoList(users);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDto> getUsersByRole(UserRole role) {
        log.info("Fetching users by role: {}", role);
        List<User> users = userRepository.findByRoleAndIsActiveTrue(role);
        return userMapper.toUserDtoList(users);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDto> getActiveUsers() {
        log.info("Fetching active users");
        List<User> users = userRepository.findByIsActiveTrue();
        return userMapper.toUserDtoList(users);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDto> getInactiveUsers() {
        log.info("Fetching inactive users");
        List<User> users = userRepository.findByIsActiveFalse();
        return userMapper.toUserDtoList(users);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSummaryDto> searchUsers(String searchTerm) {
        log.info("Searching users with term: {}", searchTerm);
        List<User> users = userRepository.searchUsers(searchTerm);
        return userMapper.toUserSummaryDtoList(users);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSummaryDto> getAllUserSummaries() {
        log.info("Fetching all user summaries");
        List<User> users = userRepository.findAll();
        return userMapper.toUserSummaryDtoList(users);
    }

    // ========== DEPARTMENT MANAGEMENT ==========

    @Override
    @Transactional(readOnly = true)
    public List<UserDto> getUsersByDepartement(UUID departementId) {
        log.info("Fetching users for departement: {}", departementId);
        List<User> teachers = userRepository.findByDepartementIdAndRole(departementId, UserRole.TEACHER);
        return userMapper.toUserDtoList(teachers);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDto> getTeachersByDepartement(UUID departementId) {
        log.info("Fetching teachers for departement: {}", departementId);
        List<User> teachers = userRepository.findByDepartementIdAndRole(departementId, UserRole.TEACHER);
        return userMapper.toUserDtoList(teachers);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDto> getStudentsByDepartement(UUID departementId) {
        log.info("Fetching students for departement: {}", departementId);
        // Students are associated through classes, need to get all classes in the department
        List<User> students = userRepository.findAll().stream()
                .filter(user -> user.getRole() == UserRole.STUDENT && 
                               user.getClasse() != null && 
                               user.getClasse().getNiveau() != null &&
                               user.getClasse().getNiveau().getDepartement().getId().equals(departementId))
                .toList();
        return userMapper.toUserDtoList(students);
    }

    // ========== CLASS MANAGEMENT ==========

    @Override
    @Transactional(readOnly = true)
    public List<UserDto> getStudentsByClasse(UUID classeId) {
        log.info("Fetching students for classe: {}", classeId);
        List<User> students = userRepository.findByClasseIdAndRole(classeId, UserRole.STUDENT);
        return userMapper.toUserDtoList(students);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDto> getTeachersByClasse(UUID classeId) {
        log.info("Fetching teachers for classe: {}", classeId);
        // Teachers are associated through many-to-many relationship
        Classe classe = classeRepository.findByIdWithTeachers(classeId)
                .orElseThrow(() -> new BusinessException("Classe non trouvée avec l'ID: " + classeId));
        return userMapper.toUserDtoList(classe.getTeachers());
    }

    // ========== ASSIGNMENT OPERATIONS ==========

    @Override
    public UserDto assignUserToDepartement(UUID userId, UUID departementId) {
        log.info("Assigning user {} to departement {}", userId, departementId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Utilisateur non trouvé avec l'ID: " + userId));
        
        Departement departement = departementRepository.findById(departementId)
                .orElseThrow(() -> new BusinessException("Département non trouvé avec l'ID: " + departementId));
        
        // Validate user role (only teachers can be assigned to departments this way)
        if (user.getRole() != UserRole.TEACHER) {
            throw new BusinessException("Seuls les enseignants peuvent être assignés à un département");
        }
        
        user.setDepartement(departement);
        User savedUser = userRepository.save(user);
        
        log.info("Successfully assigned user to departement");
        return userMapper.toUserDto(savedUser);
    }

    @Override
    public UserDto removeUserFromDepartement(UUID userId) {
        log.info("Removing user {} from departement", userId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Utilisateur non trouvé avec l'ID: " + userId));
        
        user.setDepartement(null);
        User savedUser = userRepository.save(user);
        
        log.info("Successfully removed user from departement");
        return userMapper.toUserDto(savedUser);
    }

    @Override
    public UserDto assignStudentToClasse(UUID studentId, UUID classeId) {
        log.info("Assigning student {} to classe {}", studentId, classeId);
        
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new BusinessException("Étudiant non trouvé avec l'ID: " + studentId));
        
        if (student.getRole() != UserRole.STUDENT) {
            throw new BusinessException("L'utilisateur doit être un étudiant");
        }
        
        Classe classe = classeRepository.findById(classeId)
                .orElseThrow(() -> new BusinessException("Classe non trouvée avec l'ID: " + classeId));
        
        // Check class capacity
        long currentStudentCount = userRepository.countStudentsByClasse(classeId);
        if (currentStudentCount >= classe.getCapacite()) {
            throw new BusinessException("La capacité de la classe est atteinte");
        }
        
        student.setClasse(classe);
        User savedStudent = userRepository.save(student);
        
        log.info("Successfully assigned student to classe");
        return userMapper.toUserDto(savedStudent);
    }

    @Override
    public UserDto removeStudentFromClasse(UUID studentId) {
        log.info("Removing student {} from classe", studentId);
        
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new BusinessException("Étudiant non trouvé avec l'ID: " + studentId));
        
        student.setClasse(null);
        User savedStudent = userRepository.save(student);
        
        log.info("Successfully removed student from classe");
        return userMapper.toUserDto(savedStudent);
    }

    @Override
    public UserDto assignTeacherToClasse(UUID teacherId, UUID classeId) {
        log.info("Assigning teacher {} to classe {}", teacherId, classeId);
        
        User teacher = userRepository.findByIdWithTeachingClasses(teacherId)
                .orElseThrow(() -> new BusinessException("Enseignant non trouvé avec l'ID: " + teacherId));
        
        if (teacher.getRole() != UserRole.TEACHER) {
            throw new BusinessException("L'utilisateur doit être un enseignant");
        }
        
        Classe classe = classeRepository.findById(classeId)
                .orElseThrow(() -> new BusinessException("Classe non trouvée avec l'ID: " + classeId));
        
        if (teacher.getTeachingClasses() == null) {
            teacher.setTeachingClasses(List.of());
        }
        
        if (!teacher.getTeachingClasses().contains(classe)) {
            teacher.getTeachingClasses().add(classe);
            User savedTeacher = userRepository.save(teacher);
            
            log.info("Successfully assigned teacher to classe");
            return userMapper.toUserDto(savedTeacher);
        }
        
        return userMapper.toUserDto(teacher);
    }

    @Override
    public UserDto removeTeacherFromClasse(UUID teacherId, UUID classeId) {
        log.info("Removing teacher {} from classe {}", teacherId, classeId);
        
        User teacher = userRepository.findByIdWithTeachingClasses(teacherId)
                .orElseThrow(() -> new BusinessException("Enseignant non trouvé avec l'ID: " + teacherId));
        
        if (teacher.getTeachingClasses() != null) {
            teacher.getTeachingClasses().removeIf(classe -> classe.getId().equals(classeId));
            User savedTeacher = userRepository.save(teacher);
            
            log.info("Successfully removed teacher from classe");
            return userMapper.toUserDto(savedTeacher);
        }
        
        return userMapper.toUserDto(teacher);
    }

    // ========== STATUS MANAGEMENT ==========

    @Override
    public UserDto activateUser(UUID id) {
        log.info("Activating user: {}", id);
        return updateUserStatus(id, true);
    }

    @Override
    public UserDto deactivateUser(UUID id) {
        log.info("Deactivating user: {}", id);
        return updateUserStatus(id, false);
    }

    @Override
    public UserDto verifyUserEmail(UUID id) {
        log.info("Verifying user email: {}", id);
        
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Utilisateur non trouvé avec l'ID: " + id));
        
        user.setIsEmailVerified(true);
        User savedUser = userRepository.save(user);
        
        log.info("Successfully verified user email");
        return userMapper.toUserDto(savedUser);
    }

    private UserDto updateUserStatus(UUID id, boolean isActive) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Utilisateur non trouvé avec l'ID: " + id));
        
        user.setIsActive(isActive);
        User savedUser = userRepository.save(user);
        
        String action = isActive ? "activated" : "deactivated";
        log.info("Successfully {} user", action);
        return userMapper.toUserDto(savedUser);
    }

    // ========== UNASSIGNED USERS ==========

    @Override
    @Transactional(readOnly = true)
    public List<UserDto> getUnassignedTeachers() {
        log.info("Fetching unassigned teachers");
        List<User> teachers = userRepository.findUnassignedUsersByRole(UserRole.TEACHER);
        return userMapper.toUserDtoList(teachers);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDto> getUnassignedStudents() {
        log.info("Fetching unassigned students");
        List<User> students = userRepository.findUnassignedStudents();
        return userMapper.toUserDtoList(students);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDto> getUnassignedChiefs() {
        log.info("Fetching unassigned chiefs");
        List<User> chiefs = userRepository.findUnassignedChiefs();
        return userMapper.toUserDtoList(chiefs);
    }

    // ========== STATISTICS ==========

    @Override
    @Transactional(readOnly = true)
    public long countUsersByRole(UserRole role) {
        return userRepository.countByRole(role);
    }

    @Override
    @Transactional(readOnly = true)
    public long countTeachersByDepartement(UUID departementId) {
        return userRepository.countTeachersByDepartement(departementId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countStudentsByDepartement(UUID departementId) {
        return userRepository.countStudentsByDepartement(departementId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countStudentsByClasse(UUID classeId) {
        return userRepository.countStudentsByClasse(classeId);
    }

    // ========== HELPER METHODS ==========

    @Override
    @Transactional(readOnly = true)
    public boolean canUserManageUser(UUID managerId, UUID targetUserId) {
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> new BusinessException("Manager non trouvé"));
        
        if (manager.isAdmin()) {
            return true; // Admins can manage all users
        }
        
        if (manager.isChief()) {
            return isUserInManagerDepartement(managerId, targetUserId);
        }
        
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isUserInManagerDepartement(UUID managerId, UUID targetUserId) {
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> new BusinessException("Manager non trouvé"));
        
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException("Utilisateur cible non trouvé"));
        
        if (manager.getChiefOfDepartement() == null) {
            return false;
        }
        
        UUID managerDepartementId = manager.getChiefOfDepartement().getId();
        
        // Check if target is in the same department
        if (target.getDepartement() != null && 
            target.getDepartement().getId().equals(managerDepartementId)) {
            return true;
        }
        
        // Check if target is a student in a class within the department
        if (target.getClasse() != null && 
            target.getClasse().getNiveau() != null &&
            target.getClasse().getNiveau().getDepartement().getId().equals(managerDepartementId)) {
            return true;
        }
        
        return false;
    }

    // ========== PRIVATE HELPER METHODS ==========

    private void handleAcademicAssignments(User user, UUID departementId, UUID classeId) {
        // Handle department assignment for teachers
        if (departementId != null && user.getRole() == UserRole.TEACHER) {
            Departement departement = departementRepository.findById(departementId)
                    .orElseThrow(() -> new BusinessException("Département non trouvé avec l'ID: " + departementId));
            user.setDepartement(departement);
        }
        
        // Handle class assignment for students
        if (classeId != null && user.getRole() == UserRole.STUDENT) {
            Classe classe = classeRepository.findById(classeId)
                    .orElseThrow(() -> new BusinessException("Classe non trouvée avec l'ID: " + classeId));
            
            // Check class capacity
            long currentStudentCount = userRepository.countStudentsByClasse(classeId);
            if (currentStudentCount >= classe.getCapacite()) {
                throw new BusinessException("La capacité de la classe est atteinte");
            }
            
            user.setClasse(classe);
        }
    }

    @Override
    public List<UserDto> batchAssignStudentsToClasse(List<UUID> studentIds, UUID classeId) {
        log.info("Batch assigning students {} to classe {}", studentIds, classeId);
        Classe classe = classeRepository.findById(classeId)
                .orElseThrow(() -> new BusinessException("Classe non trouvée avec l'ID: " + classeId));
        long currentStudentCount = userRepository.countStudentsByClasse(classeId);
        if (currentStudentCount + studentIds.size() > classe.getCapacite()) {
            throw new BusinessException("La capacité de la classe serait dépassée");
        }
        List<User> students = userRepository.findAllById(studentIds);
        for (User student : students) {
            if (student.getRole() != UserRole.STUDENT) {
                throw new BusinessException("L'utilisateur " + student.getId() + " n'est pas un étudiant");
            }
            student.setClasse(classe);
        }
        userRepository.saveAll(students);
        log.info("Successfully batch assigned students to classe");
        return students.stream().map(userMapper::toUserDto).toList();
    }

    @Override
    public List<UserDto> bulkCreateUsers(List<CreateUserDto> users) {
        if (users == null || users.isEmpty()) return List.of();
        return users.stream()
            .map(this::createUser)
            .toList();
    }
}
