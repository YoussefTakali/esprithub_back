package tn.esprithub.server.academic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tn.esprithub.server.academic.entity.Departement;
import tn.esprithub.server.academic.enums.Specialites;
import tn.esprithub.server.academic.enums.TypeFormation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DepartementRepository extends JpaRepository<Departement, UUID> {
    
    List<Departement> findByIsActiveTrue();
    
    List<Departement> findBySpecialite(Specialites specialite);
    
    List<Departement> findByTypeFormation(TypeFormation typeFormation);
    
    Optional<Departement> findByCode(String code);
    
    Optional<Departement> findByChiefId(UUID chiefId);
    
    List<Departement> findByNomContainingIgnoreCase(String nom);
    
    boolean existsByCode(String code);
    
    boolean existsByChiefId(UUID chiefId);
    
    @Query("SELECT d FROM Departement d LEFT JOIN FETCH d.niveaux WHERE d.id = :id")
    Optional<Departement> findByIdWithNiveaux(@Param("id") UUID id);
    
    @Query("SELECT d FROM Departement d LEFT JOIN FETCH d.teachers WHERE d.id = :id")
    Optional<Departement> findByIdWithTeachers(@Param("id") UUID id);
    
    @Query("SELECT d FROM Departement d LEFT JOIN FETCH d.chief WHERE d.isActive = true")
    List<Departement> findAllActiveWithChief();
    
    // Additional methods for chief operations
    @Query("SELECT d FROM Departement d WHERE d.chief.id = :chiefId AND d.isActive = true")
    Optional<Departement> findByChiefIdAndIsActiveTrue(@Param("chiefId") UUID chiefId);
}
