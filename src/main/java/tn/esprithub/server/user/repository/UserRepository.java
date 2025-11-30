package tn.esprithub.server.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprithub.server.user.entity.User;
import tn.esprithub.server.common.enums.UserRole;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    // Basic queries
    Optional<User> findByEmail(String email);
    Optional<User> findByGithubUsername(String githubUsername);
    boolean existsByEmail(String email);
    boolean existsByGithubUsername(String githubUsername);

    // Role-based queries
    List<User> findByRole(UserRole role);
    List<User> findByRoleAndIsActiveTrue(UserRole role);
    
    @Query("SELECT COUNT(u) FROM User u WHERE u.role = :role")
    long countByRole(@Param("role") UserRole role);

    // Status queries
    List<User> findByIsActiveTrue();
    List<User> findByIsActiveFalse();
    List<User> findByIsEmailVerifiedFalse();

    // Activity queries
    @Query("SELECT u FROM User u WHERE u.lastLogin IS NULL OR u.lastLogin < :cutoffDate")
    List<User> findInactiveUsers(@Param("cutoffDate") LocalDateTime cutoffDate);

    // Search queries
    @Query("SELECT u FROM User u WHERE " +
           "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<User> searchUsers(@Param("searchTerm") String searchTerm);

    // Academic relationship queries
    List<User> findByDepartementIdAndRole(UUID departementId, UserRole role);
    List<User> findByClasseIdAndRole(UUID classeId, UserRole role);
    
    @Query("SELECT u FROM User u WHERE u.departement IS NULL AND u.role = :role")
    List<User> findUnassignedUsersByRole(@Param("role") UserRole role);
    
    @Query("SELECT u FROM User u WHERE u.classe IS NULL AND u.role = 'STUDENT'")
    List<User> findUnassignedStudents();
    
    @Query("SELECT u FROM User u WHERE u.chiefOfDepartement IS NULL AND u.role = 'CHIEF'")
    List<User> findUnassignedChiefs();

    // Department-specific queries for chiefs
    @Query("SELECT u FROM User u WHERE u.chiefOfDepartement.id = :departementId")
    Optional<User> findChiefByDepartementId(@Param("departementId") UUID departementId);
    
    // Fetch with relationships
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.departement WHERE u.id = :id")
    Optional<User> findByIdWithDepartement(@Param("id") UUID id);
    
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.classe WHERE u.id = :id")
    Optional<User> findByIdWithClasse(@Param("id") UUID id);
    
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.teachingClasses WHERE u.id = :id")
    Optional<User> findByIdWithTeachingClasses(@Param("id") UUID id);

    // Statistics queries
    @Query("SELECT COUNT(u) FROM User u WHERE u.departement.id = :departementId AND u.role = 'TEACHER'")
    long countTeachersByDepartement(@Param("departementId") UUID departementId);
    
    @Query("SELECT COUNT(u) FROM User u WHERE u.classe.niveau.departement.id = :departementId AND u.role = 'STUDENT'")
    long countStudentsByDepartement(@Param("departementId") UUID departementId);
    
    @Query("SELECT COUNT(u) FROM User u WHERE u.classe.id = :classeId AND u.role = 'STUDENT'")
    long countStudentsByClasse(@Param("classeId") UUID classeId);

    // GitHub integration queries
    @Query("SELECT u FROM User u WHERE u.githubToken IS NOT NULL AND u.githubToken != '' AND u.isActive = true")
    List<User> findUsersWithGitHubTokens();

    // Pagination support for large datasets
    @Query("SELECT u FROM User u WHERE u.role IN :roles ORDER BY u.lastName, u.firstName")
    List<User> findByRoleInOrderByName(@Param("roles") List<UserRole> roles);
}
