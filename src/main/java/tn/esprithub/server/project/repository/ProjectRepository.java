package tn.esprithub.server.project.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprithub.server.project.entity.Project;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {
    List<Project> findByCreatedBy_IdOrCollaborators_Id(UUID creatorId, UUID collaboratorId);

    @Query("SELECT DISTINCT p FROM Project p LEFT JOIN FETCH p.classes c LEFT JOIN p.collaborators collab WHERE p.createdBy.id = :userId OR collab.id = :userId")
    List<Project> findWithClassesByCreatedByOrCollaborator(@Param("userId") UUID userId);
    
    // Find projects with deadlines (for deadline notifications)
    @Query("SELECT p FROM Project p WHERE p.deadline IS NOT NULL")
    List<Project> findByDeadlineIsNotNull();
}
