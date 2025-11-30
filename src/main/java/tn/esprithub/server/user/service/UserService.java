    // Bulk import users
package tn.esprithub.server.user.service;

import tn.esprithub.server.user.dto.UserDto;
import tn.esprithub.server.user.dto.CreateUserDto;
import tn.esprithub.server.user.dto.UpdateUserDto;
import tn.esprithub.server.user.dto.UserSummaryDto;
import tn.esprithub.server.common.enums.UserRole;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for user management operations
 * Accessible by ADMIN and CHIEF (with restrictions)
 */
public interface UserService {
    
    // CRUD operations (ADMIN only)
    UserDto createUser(CreateUserDto createUserDto);
    UserDto getUserById(UUID id);
    List<UserDto> getUsersByIds(List<UUID> ids);
    UserDto updateUser(UUID id, UpdateUserDto updateUserDto);
    void deleteUser(UUID id);
    
    // User listing (ADMIN and CHIEF with restrictions)
    List<UserDto> getAllUsers();
    List<UserDto> getUsersByRole(UserRole role);
    List<UserDto> getActiveUsers();
    List<UserDto> getInactiveUsers();
    List<UserSummaryDto> searchUsers(String searchTerm);
    List<UserSummaryDto> getAllUserSummaries();
    
    // Department management (ADMIN and CHIEF)
    List<UserDto> getUsersByDepartement(UUID departementId);
    List<UserDto> getTeachersByDepartement(UUID departementId);
    List<UserDto> getStudentsByDepartement(UUID departementId);
    
    // Class management (ADMIN and CHIEF)
    List<UserDto> getStudentsByClasse(UUID classeId);
    List<UserDto> getTeachersByClasse(UUID classeId);
    
    // Assignment operations (ADMIN only)
    UserDto assignUserToDepartement(UUID userId, UUID departementId);
    UserDto removeUserFromDepartement(UUID userId);
    UserDto assignStudentToClasse(UUID studentId, UUID classeId);
    UserDto removeStudentFromClasse(UUID studentId);
    UserDto assignTeacherToClasse(UUID teacherId, UUID classeId);
    UserDto removeTeacherFromClasse(UUID teacherId, UUID classeId);
    List<UserDto> batchAssignStudentsToClasse(List<UUID> studentIds, UUID classeId);
    List<UserDto> bulkCreateUsers(List<CreateUserDto> users);

    // Status management (ADMIN only)
    UserDto activateUser(UUID id);
    UserDto deactivateUser(UUID id);
    UserDto verifyUserEmail(UUID id);
    
    // Unassigned users (ADMIN and CHIEF)
    List<UserDto> getUnassignedTeachers();
    List<UserDto> getUnassignedStudents();
    List<UserDto> getUnassignedChiefs();
    
    // Statistics
    long countUsersByRole(UserRole role);
    long countTeachersByDepartement(UUID departementId);
    long countStudentsByDepartement(UUID departementId);
    long countStudentsByClasse(UUID classeId);
    
    // Helper methods for authorization
    boolean canUserManageUser(UUID managerId, UUID targetUserId);
    boolean isUserInManagerDepartement(UUID managerId, UUID targetUserId);
}
