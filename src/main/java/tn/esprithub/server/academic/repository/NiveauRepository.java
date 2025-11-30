package tn.esprithub.server.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprithub.server.academic.entity.Niveau;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NiveauRepository extends JpaRepository<Niveau, UUID> {
    
    List<Niveau> findByIsActiveTrue();
    
    List<Niveau> findByDepartementId(UUID departementId);
    
    List<Niveau> findByDepartementIdAndIsActiveTrue(UUID departementId);
    
    List<Niveau> findByAnnee(Integer annee);
    
    Optional<Niveau> findByCode(String code);
    
    List<Niveau> findByNomContainingIgnoreCase(String nom);
    
    boolean existsByCode(String code);
    
    boolean existsByDepartementIdAndAnnee(UUID departementId, Integer annee);
    
    @Query("SELECT n FROM Niveau n LEFT JOIN FETCH n.classes WHERE n.id = :id")
    Optional<Niveau> findByIdWithClasses(@Param("id") UUID id);
    
    @Query("SELECT n FROM Niveau n LEFT JOIN FETCH n.departement WHERE n.departement.id = :departementId AND n.isActive = true")
    List<Niveau> findByDepartementIdWithDepartement(@Param("departementId") UUID departementId);
    
    @Query("SELECT COUNT(c) FROM Niveau n JOIN n.classes c WHERE n.id = :niveauId")
    Long countClassesByNiveauId(@Param("niveauId") UUID niveauId);
    
    // Add count methods for summary DTOs
    @Query("SELECT COUNT(n) FROM Niveau n WHERE n.departement.id = :departementId AND n.isActive = true")
    Long countByDepartementIdAndIsActiveTrue(@Param("departementId") UUID departementId);
}
