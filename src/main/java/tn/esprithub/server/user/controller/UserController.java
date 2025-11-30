package tn.esprithub.server.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.esprithub.server.security.service.AuthenticatedUserService;
import tn.esprithub.server.user.dto.UserDto;
import tn.esprithub.server.user.dto.BatchAssignStudentsRequest;
import tn.esprithub.server.user.dto.CreateUserDto;
import tn.esprithub.server.user.dto.UpdateUserDto;
import tn.esprithub.server.user.dto.UserSummaryDto;
import tn.esprithub.server.user.service.UserService;
import tn.esprithub.server.common.enums.UserRole;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller for user management operations
 * Access restricted to ADMIN and CHIEF (with department limitations)
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class UserController {

    private final UserService userService;
    private final AuthenticatedUserService authenticatedUserService;

    // ========== CRUD OPERATIONS (ADMIN ONLY) ==========

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody CreateUserDto createUserDto) {
        log.info("Admin API: Creating user: {}", createUserDto.getEmail());
        UserDto createdUser = userService.createUser(createUserDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CHIEF')")
    public ResponseEntity<UserDto> getUserById(@PathVariable UUID id, Authentication authentication) {
        log.info("API: Getting user by ID: {}", id);
        
        // Additional authorization check for chiefs
        if (authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_CHIEF"))) {
            UUID managerId = getUserIdFromAuthentication(authentication);
            if (!userService.canUserManageUser(managerId, id)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }
        
        UserDto user = userService.getUserById(id);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDto> updateUser(
            @PathVariable UUID id, 
            @Valid @RequestBody UpdateUserDto updateUserDto) {
        log.info("Admin API: Updating user ID: {}", id);
        UserDto updatedUser = userService.updateUser(id, updateUserDto);
        return ResponseEntity.ok(updatedUser);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable UUID id) {
        log.info("Admin API: Deleting user ID: {}", id);
        userService.deleteUser(id);
        return ResponseEntity.ok(Map.of("message", "Utilisateur supprimé avec succès"));
    }

    // ========== USER LISTING ==========

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserDto>> getAllUsers() {
        log.info("Admin API: Getting all users");
        List<UserDto> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/role/{role}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CHIEF')")
    public ResponseEntity<List<UserDto>> getUsersByRole(@PathVariable UserRole role) {
        log.info("API: Getting users by role: {}", role);
        List<UserDto> users = userService.getUsersByRole(role);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/active")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CHIEF')")
    public ResponseEntity<List<UserDto>> getActiveUsers() {
        log.info("API: Getting active users");
        List<UserDto> users = userService.getActiveUsers();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/inactive")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserDto>> getInactiveUsers() {
        log.info("Admin API: Getting inactive users");
        List<UserDto> users = userService.getInactiveUsers();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CHIEF')")
    public ResponseEntity<List<UserSummaryDto>> searchUsers(@RequestParam String q) {
        log.info("API: Searching users with query: {}", q);
        List<UserSummaryDto> users = userService.searchUsers(q);
        return ResponseEntity.ok(users);
    }

    // ========== DEPARTMENT MANAGEMENT ==========

    @GetMapping("/departement/{departementId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CHIEF')")
    public ResponseEntity<List<UserDto>> getUsersByDepartement(
            @PathVariable UUID departementId, 
            Authentication authentication) {
        log.info("API: Getting users for departement: {}", departementId);
        
        // Additional authorization check for chiefs
        if (authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_CHIEF"))) {
            UUID managerId = getUserIdFromAuthentication(authentication);
            // Chiefs can only see users from their own department
            // This would need additional service method to validate
        }
        
        List<UserDto> users = userService.getUsersByDepartement(departementId);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/departement/{departementId}/teachers")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CHIEF')")
    public ResponseEntity<List<UserDto>> getTeachersByDepartement(@PathVariable UUID departementId) {
        log.info("API: Getting teachers for departement: {}", departementId);
        List<UserDto> teachers = userService.getTeachersByDepartement(departementId);
        return ResponseEntity.ok(teachers);
    }

    @GetMapping("/departement/{departementId}/students")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CHIEF')")
    public ResponseEntity<List<UserDto>> getStudentsByDepartement(@PathVariable UUID departementId) {
        log.info("API: Getting students for departement: {}", departementId);
        List<UserDto> students = userService.getStudentsByDepartement(departementId);
        return ResponseEntity.ok(students);
    }

    // ========== CLASS MANAGEMENT ==========

    @GetMapping("/classe/{classeId}/students")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CHIEF')")
    public ResponseEntity<List<UserDto>> getStudentsByClasse(@PathVariable UUID classeId) {
        log.info("API: Getting students for classe: {}", classeId);
        List<UserDto> students = userService.getStudentsByClasse(classeId);
        return ResponseEntity.ok(students);
    }

    @GetMapping("/classe/{classeId}/teachers")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CHIEF')")
    public ResponseEntity<List<UserDto>> getTeachersByClasse(@PathVariable UUID classeId) {
        log.info("API: Getting teachers for classe: {}", classeId);
        List<UserDto> teachers = userService.getTeachersByClasse(classeId);
        return ResponseEntity.ok(teachers);
    }

    // ========== ASSIGNMENT OPERATIONS (ADMIN ONLY) ==========

    @PutMapping("/{userId}/departement/{departementId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDto> assignUserToDepartement(
            @PathVariable UUID userId, 
            @PathVariable UUID departementId) {
        log.info("Admin API: Assigning user {} to departement {}", userId, departementId);
        UserDto user = userService.assignUserToDepartement(userId, departementId);
        return ResponseEntity.ok(user);
    }

    @DeleteMapping("/{userId}/departement")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDto> removeUserFromDepartement(@PathVariable UUID userId) {
        log.info("Admin API: Removing user {} from departement", userId);
        UserDto user = userService.removeUserFromDepartement(userId);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{studentId}/classe/{classeId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDto> assignStudentToClasse(
            @PathVariable UUID studentId, 
            @PathVariable UUID classeId) {
        log.info("Admin API: Assigning student {} to classe {}", studentId, classeId);
        UserDto student = userService.assignStudentToClasse(studentId, classeId);
        return ResponseEntity.ok(student);
    }

    @DeleteMapping("/{studentId}/classe")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDto> removeStudentFromClasse(@PathVariable UUID studentId) {
        log.info("Admin API: Removing student {} from classe", studentId);
        UserDto student = userService.removeStudentFromClasse(studentId);
        return ResponseEntity.ok(student);
    }

    @PostMapping("/{teacherId}/classe/{classeId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDto> assignTeacherToClasse(
            @PathVariable UUID teacherId, 
            @PathVariable UUID classeId) {
        log.info("Admin API: Assigning teacher {} to classe {}", teacherId, classeId);
        UserDto teacher = userService.assignTeacherToClasse(teacherId, classeId);
        return ResponseEntity.ok(teacher);
    }

    @DeleteMapping("/{teacherId}/classe/{classeId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDto> removeTeacherFromClasse(
            @PathVariable UUID teacherId, 
            @PathVariable UUID classeId) {
        log.info("Admin API: Removing teacher {} from classe {}", teacherId, classeId);
        UserDto teacher = userService.removeTeacherFromClasse(teacherId, classeId);
        return ResponseEntity.ok(teacher);
    }

    // ========== STATUS MANAGEMENT (ADMIN ONLY) ==========

    @PutMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDto> activateUser(@PathVariable UUID id) {
        log.info("Admin API: Activating user: {}", id);
        UserDto user = userService.activateUser(id);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDto> deactivateUser(@PathVariable UUID id) {
        log.info("Admin API: Deactivating user: {}", id);
        UserDto user = userService.deactivateUser(id);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{id}/verify-email")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDto> verifyUserEmail(@PathVariable UUID id) {
        log.info("Admin API: Verifying user email: {}", id);
        UserDto user = userService.verifyUserEmail(id);
        return ResponseEntity.ok(user);
    }

    // ========== UNASSIGNED USERS ==========

    @GetMapping("/unassigned/teachers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserDto>> getUnassignedTeachers() {
        log.info("Admin API: Getting unassigned teachers");
        List<UserDto> teachers = userService.getUnassignedTeachers();
        return ResponseEntity.ok(teachers);
    }

    @GetMapping("/unassigned/students")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CHIEF')")
    public ResponseEntity<List<UserDto>> getUnassignedStudents() {
        log.info("API: Getting unassigned students");
        List<UserDto> students = userService.getUnassignedStudents();
        return ResponseEntity.ok(students);
    }

    @GetMapping("/unassigned/chiefs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserDto>> getUnassignedChiefs() {
        log.info("Admin API: Getting unassigned chiefs");
        List<UserDto> chiefs = userService.getUnassignedChiefs();
        return ResponseEntity.ok(chiefs);
    }

    // ========== STATISTICS ==========

    @GetMapping("/stats/role/{role}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CHIEF')")
    public ResponseEntity<Map<String, Long>> getUserCountByRole(@PathVariable UserRole role) {
        log.info("API: Getting user count for role: {}", role);
        long count = userService.countUsersByRole(role);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @GetMapping("/stats/departement/{departementId}/teachers")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CHIEF')")
    public ResponseEntity<Map<String, Long>> getTeacherCountByDepartement(@PathVariable UUID departementId) {
        log.info("API: Getting teacher count for departement: {}", departementId);
        long count = userService.countTeachersByDepartement(departementId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @GetMapping("/stats/departement/{departementId}/students")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CHIEF')")
    public ResponseEntity<Map<String, Long>> getStudentCountByDepartement(@PathVariable UUID departementId) {
        log.info("API: Getting student count for departement: {}", departementId);
        long count = userService.countStudentsByDepartement(departementId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @GetMapping("/stats/classe/{classeId}/students")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CHIEF')")
    public ResponseEntity<Map<String, Long>> getStudentCountByClasse(@PathVariable UUID classeId) {
        log.info("API: Getting student count for classe: {}", classeId);
        long count = userService.countStudentsByClasse(classeId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    // ========== HELPER METHODS ==========

    private UUID getUserIdFromAuthentication(Authentication authentication) {
        return authenticatedUserService.getUserId(authentication);
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN') or hasRole('CHIEF') or hasRole('TEACHER')")
    public ResponseEntity<List<UserSummaryDto>> getAllUserSummaries() {
        log.info("API: Getting all user summaries (id, name, email)");
        List<UserSummaryDto> users = userService.getAllUserSummaries();
        return ResponseEntity.ok(users);
    }

    // Get students by class (for group creation, etc.)
    @GetMapping("/classes/{classId}/students")
    @PreAuthorize("hasRole('ADMIN') or hasRole('TEACHER') or hasRole('CHIEF')")
    public ResponseEntity<List<UserDto>> getStudentsByClass(@PathVariable UUID classId) {
        return ResponseEntity.ok(userService.getStudentsByClasse(classId));
    }

    // Batch assign students to a class
    @PostMapping("/admin/academic/classes/{classeId}/students")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserDto>> batchAssignStudentsToClasse(
            @PathVariable UUID classeId,
            @Valid @RequestBody BatchAssignStudentsRequest request) {
        log.info("Admin API: Batch assigning students {} to classe {}", request.getStudentIds(), classeId);
        List<UserDto> assigned = userService.batchAssignStudentsToClasse(request.getStudentIds(), classeId);
        return ResponseEntity.ok(assigned);
    }

    // ========== BULK IMPORT USERS (ADMIN ONLY) ===========

    @PostMapping("/import-bulk")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserDto>> importUsersBulk(@Valid @RequestBody tn.esprithub.server.user.dto.BulkCreateUsersRequest request) {
        log.info("Admin API: Bulk importing users, count: {}", request.getUsers() != null ? request.getUsers().size() : 0);
        List<UserDto> created = userService.bulkCreateUsers(request.getUsers());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
