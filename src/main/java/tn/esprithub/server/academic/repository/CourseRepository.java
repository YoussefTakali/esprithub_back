package tn.esprithub.server.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprithub.server.academic.entity.Course;
import tn.esprithub.server.academic.entity.Niveau;

import java.util.List;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID> {
    List<Course> findByNiveau(Niveau niveau);
    List<Course> findByNiveauId(UUID niveauId);
}
