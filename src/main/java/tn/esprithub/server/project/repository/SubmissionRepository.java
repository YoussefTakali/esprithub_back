package tn.esprithub.server.project.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprithub.server.project.entity.Submission;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, UUID> {
    
    List<Submission> findByTaskIdOrderBySubmittedAtDesc(UUID taskId);
    
    List<Submission> findByUserIdOrderBySubmittedAtDesc(UUID userId);
    
    Page<Submission> findByUserIdOrderBySubmittedAtDesc(UUID userId, Pageable pageable);
    
    List<Submission> findByGroupIdOrderBySubmittedAtDesc(UUID groupId);
    
    Optional<Submission> findByTaskIdAndUserId(UUID taskId, UUID userId);
    
    Optional<Submission> findByTaskIdAndGroupId(UUID taskId, UUID groupId);
    
    @Query("SELECT s FROM Submission s WHERE s.taskId = :taskId AND s.status = :status ORDER BY s.submittedAt DESC")
    List<Submission> findByTaskIdAndStatus(@Param("taskId") UUID taskId, @Param("status") Submission.SubmissionStatus status);
    
    @Query("SELECT s FROM Submission s WHERE s.userId = :userId AND s.status = :status ORDER BY s.submittedAt DESC")
    List<Submission> findByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") Submission.SubmissionStatus status);
    
    boolean existsByTaskIdAndUserId(UUID taskId, UUID userId);
    
    boolean existsByTaskIdAndGroupId(UUID taskId, UUID groupId);
    
    @Query("SELECT COUNT(s) FROM Submission s WHERE s.taskId = :taskId")
    long countByTaskId(@Param("taskId") UUID taskId);
    
    @Query("SELECT COUNT(s) FROM Submission s WHERE s.taskId = :taskId AND s.status = 'GRADED'")
    long countGradedByTaskId(@Param("taskId") UUID taskId);
    
    Page<Submission> findAllByOrderBySubmittedAtDesc(Pageable pageable);

    long countByUserIdAndSubmittedAtBetween(UUID userId, LocalDateTime start, LocalDateTime end);
}
