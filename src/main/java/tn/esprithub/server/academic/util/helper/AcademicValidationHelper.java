package tn.esprithub.server.academic.util.helper;

import org.springframework.stereotype.Component;
import tn.esprithub.server.academic.enums.Specialites;
import tn.esprithub.server.academic.enums.TypeFormation;
import tn.esprithub.server.common.enums.UserRole;
import tn.esprithub.server.common.exception.BusinessException;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Validation helper for academic operations
 * Provides reusable validation logic for academic entities
 */
@Component
public class AcademicValidationHelper {

    public void validateDepartementCreation(String nom, Specialites specialite, TypeFormation typeFormation) {
        if (nom == null || nom.trim().isEmpty()) {
            throw new BusinessException("Le nom du département est obligatoire");
        }
        
        if (nom.length() > 100) {
            throw new BusinessException("Le nom du département ne peut pas dépasser 100 caractères");
        }
        
        if (specialite == null) {
            throw new BusinessException("La spécialité est obligatoire");
        }
        
        if (typeFormation == null) {
            throw new BusinessException("Le type de formation est obligatoire");
        }
    }

    public void validateNiveauCreation(String nom, Integer annee, UUID departementId) {
        if (nom == null || nom.trim().isEmpty()) {
            throw new BusinessException("Le nom du niveau est obligatoire");
        }
        
        if (nom.length() > 100) {
            throw new BusinessException("Le nom du niveau ne peut pas dépasser 100 caractères");
        }
        
        if (annee == null) {
            throw new BusinessException("L'année est obligatoire");
        }
        
        if (annee < 1 || annee > 6) {
            throw new BusinessException("L'année doit être entre 1 et 6");
        }
        
        if (departementId == null) {
            throw new BusinessException("L'ID du département est obligatoire");
        }
    }

    public void validateClasseCreation(String nom, Integer capacite, UUID niveauId) {
        if (nom == null || nom.trim().isEmpty()) {
            throw new BusinessException("Le nom de la classe est obligatoire");
        }
        
        if (nom.length() > 100) {
            throw new BusinessException("Le nom de la classe ne peut pas dépasser 100 caractères");
        }
        
        if (capacite == null) {
            throw new BusinessException("La capacité est obligatoire");
        }
        
        if (capacite < 1 || capacite > 100) {
            throw new BusinessException("La capacité doit être entre 1 et 100");
        }
        
        if (niveauId == null) {
            throw new BusinessException("L'ID du niveau est obligatoire");
        }
    }

    public void validateUserRole(UserRole role, List<UserRole> allowedRoles) {
        if (role == null) {
            throw new BusinessException("Le rôle de l'utilisateur est obligatoire");
        }
        
        if (!allowedRoles.contains(role)) {
            throw new BusinessException("Rôle d'utilisateur non autorisé: " + role);
        }
    }

    public void validateChiefAssignment(UserRole userRole) {
        if (userRole != UserRole.CHIEF) {
            throw new BusinessException("Seuls les utilisateurs avec le rôle CHIEF peuvent être assignés comme chef de département");
        }
    }

    public void validateTeacherAssignment(UserRole userRole) {
        List<UserRole> allowedRoles = Arrays.asList(UserRole.TEACHER, UserRole.CHIEF, UserRole.ADMIN);
        if (!allowedRoles.contains(userRole)) {
            throw new BusinessException("Seuls les enseignants, chefs de département et administrateurs peuvent être assignés à une classe");
        }
    }

    public void validateStudentAssignment(UserRole userRole) {
        if (userRole != UserRole.STUDENT) {
            throw new BusinessException("Seuls les étudiants peuvent être assignés à une classe");
        }
    }

    public void validateEntityExists(Object entity, String entityName, UUID id) {
        if (entity == null) {
            throw new BusinessException(entityName + " avec l'ID " + id + " n'existe pas");
        }
    }

    public void validateEntityActive(Boolean isActive, String entityName) {
        if (isActive == null || !isActive) {
            throw new BusinessException(entityName + " n'est pas actif");
        }
    }

    public void validateUniqueConstraint(boolean exists, String fieldName, String value) {
        if (exists) {
            throw new BusinessException(fieldName + " '" + value + "' existe déjà");
        }
    }

    public void validateClassCapacity(Integer currentStudents, Integer maxCapacity, Integer additionalStudents) {
        if (currentStudents + additionalStudents > maxCapacity) {
            throw new BusinessException("La capacité maximale de la classe (" + maxCapacity + ") sera dépassée. " +
                    "Étudiants actuels: " + currentStudents + ", Étudiants à ajouter: " + additionalStudents);
        }
    }

    public void validateDepartementCode(String code) {
        if (code != null && code.length() > 10) {
            throw new BusinessException("Le code du département ne peut pas dépasser 10 caractères");
        }
    }

    public void validateNiveauCode(String code) {
        if (code != null && code.length() > 10) {
            throw new BusinessException("Le code du niveau ne peut pas dépasser 10 caractères");
        }
    }

    public void validateClasseCode(String code) {
        if (code != null && code.length() > 15) {
            throw new BusinessException("Le code de la classe ne peut pas dépasser 15 caractères");
        }
    }
}
