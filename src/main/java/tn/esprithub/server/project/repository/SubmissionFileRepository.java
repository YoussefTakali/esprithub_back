package tn.esprithub.server.project.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.esprithub.server.project.entity.SubmissionFile;

import java.util.List;
import java.util.UUID;

@Repository
public interface SubmissionFileRepository extends JpaRepository<SubmissionFile, UUID> {
    
    List<SubmissionFile> findBySubmissionIdAndIsActiveTrue(UUID submissionId);
    
    List<SubmissionFile> findBySubmissionId(UUID submissionId);
    
    void deleteBySubmissionId(UUID submissionId);
}
