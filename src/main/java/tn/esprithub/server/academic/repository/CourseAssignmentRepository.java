package tn.esprithub.server.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprithub.server.academic.entity.CourseAssignment;
import tn.esprithub.server.academic.entity.Niveau;

import java.util.List;
import java.util.UUID;

public interface CourseAssignmentRepository extends JpaRepository<CourseAssignment, UUID> {
    List<CourseAssignment> findByNiveau(Niveau niveau);
    List<CourseAssignment> findByNiveauId(UUID niveauId);
    List<CourseAssignment> findByTeacher_Id(UUID teacherId);
}
