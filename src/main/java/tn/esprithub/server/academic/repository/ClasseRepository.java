package tn.esprithub.server.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprithub.server.academic.entity.Classe;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClasseRepository extends JpaRepository<Classe, UUID> {
    
    List<Classe> findByIsActiveTrue();
    
    List<Classe> findByNiveauId(UUID niveauId);
    
    List<Classe> findByNiveauIdAndIsActiveTrue(UUID niveauId);
    
    Optional<Classe> findByCode(String code);
    
    List<Classe> findByNomContainingIgnoreCase(String nom);
    
    boolean existsByCode(String code);
    
    boolean existsByNiveauIdAndNom(UUID niveauId, String nom);
    
    @Query("SELECT c FROM Classe c LEFT JOIN FETCH c.students WHERE c.id = :id")
    Optional<Classe> findByIdWithStudents(@Param("id") UUID id);
    
    @Query("SELECT c FROM Classe c LEFT JOIN FETCH c.teachers WHERE c.id = :id")
    Optional<Classe> findByIdWithTeachers(@Param("id") UUID id);
    
    @Query("SELECT c FROM Classe c LEFT JOIN FETCH c.niveau n LEFT JOIN FETCH n.departement WHERE c.id = :id")
    Optional<Classe> findByIdWithNiveauAndDepartement(@Param("id") UUID id);
    
    @Query("SELECT c FROM Classe c JOIN c.niveau n WHERE n.departement.id = :departementId AND c.isActive = true")
    List<Classe> findByDepartementId(@Param("departementId") UUID departementId);
    
    @Query("SELECT COUNT(s) FROM Classe c JOIN c.students s WHERE c.id = :classeId")
    Long countStudentsByClasseId(@Param("classeId") UUID classeId);
    
    @Query("SELECT COUNT(t) FROM Classe c JOIN c.teachers t WHERE c.id = :classeId")
    Long countTeachersByClasseId(@Param("classeId") UUID classeId);
    
    // Add count method for niveau summary
    @Query("SELECT COUNT(c) FROM Classe c WHERE c.niveau.id = :niveauId AND c.isActive = true")
    Long countByNiveauIdAndIsActiveTrue(@Param("niveauId") UUID niveauId);
    
    List<Classe> findByTeachers_Id(UUID teacherId);
}
