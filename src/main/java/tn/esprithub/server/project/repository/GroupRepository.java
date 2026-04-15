package tn.esprithub.server.project.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprithub.server.project.entity.Group;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface GroupRepository extends JpaRepository<Group, UUID> {
    List<Group> findByProjectId(UUID projectId);
    List<Group> findByProjectIdAndClasseId(UUID projectId, UUID classeId);

    @Query("SELECT g FROM Group g LEFT JOIN FETCH g.students LEFT JOIN FETCH g.project WHERE g.id = :groupId")
    java.util.Optional<Group> findByIdWithStudentsAndProject(@Param("groupId") UUID groupId);
    
    // Find groups that a student is a member of (with eager loading of repository and project)
    @Query("SELECT DISTINCT g FROM Group g " +
           "LEFT JOIN FETCH g.repository r " +
           "LEFT JOIN FETCH g.project p " +
           "JOIN g.students s WHERE s.id = :studentId")
    List<Group> findGroupsByStudentId(@Param("studentId") UUID studentId);
    
    // Find groups with repositories that a student is a member of
    @Query("SELECT g FROM Group g INNER JOIN FETCH g.repository r INNER JOIN g.students s WHERE s.id = :studentId AND r IS NOT NULL")
    List<Group> findGroupsWithRepositoriesByStudentId(@Param("studentId") UUID studentId);

    // Find all groups of a set of projects with eager student loading.
    @Query("SELECT DISTINCT g FROM Group g LEFT JOIN FETCH g.students WHERE g.project.id IN :projectIds")
    List<Group> findAllByProjectIdsWithStudents(@Param("projectIds") Set<UUID> projectIds);
    
    // Find groups by repository name (for webhook notifications)
    @Query("SELECT g FROM Group g INNER JOIN FETCH g.repository r WHERE r.fullName = :repositoryName")
    List<Group> findByRepositoryName(@Param("repositoryName") String repositoryName);
}
