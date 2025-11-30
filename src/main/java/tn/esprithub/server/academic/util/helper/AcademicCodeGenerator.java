package tn.esprithub.server.academic.util.helper;

import org.springframework.stereotype.Component;
import tn.esprithub.server.academic.entity.Departement;
import tn.esprithub.server.academic.entity.Niveau;
import tn.esprithub.server.academic.entity.Classe;

/**
 * Helper class for generating codes for academic entities
 */
@Component
public class AcademicCodeGenerator {

    private static final String DEPARTEMENT_PREFIX = "DEPT_";
    private static final String NIVEAU_PREFIX = "NIV_";
    private static final String CLASSE_PREFIX = "CLS_";
    private static final String CODE_PATTERN = "^[A-Z0-9_]+$";

    public String generateDepartementCode(Departement departement) {
        if (departement.getSpecialite() == null || departement.getTypeFormation() == null) {
            return DEPARTEMENT_PREFIX + System.currentTimeMillis();
        }

        String specialiteCode = departement.getSpecialite().name()
                .substring(0, Math.min(3, departement.getSpecialite().name().length()));
        String formationCode = departement.getTypeFormation().name()
                .substring(0, Math.min(2, departement.getTypeFormation().name().length()));
        
        return (specialiteCode + "_" + formationCode).toUpperCase();
    }

    public String generateNiveauCode(Niveau niveau) {
        if (niveau.getDepartement() == null || niveau.getAnnee() == null) {
            return NIVEAU_PREFIX + System.currentTimeMillis();
        }

        String departementCode = niveau.getDepartement().getCode();
        if (departementCode == null || departementCode.isEmpty()) {
            departementCode = generateDepartementCode(niveau.getDepartement());
        }

        return departementCode + "_N" + niveau.getAnnee();
    }

    public String generateClasseCode(Classe classe) {
        if (classe.getNiveau() == null || classe.getNom() == null) {
            return CLASSE_PREFIX + System.currentTimeMillis();
        }

        String niveauCode = classe.getNiveau().getCode();
        if (niveauCode == null || niveauCode.isEmpty()) {
            niveauCode = generateNiveauCode(classe.getNiveau());
        }

        String classeSuffix = extractClasseSuffix(classe.getNom());
        String rawCode = niveauCode + "_" + classeSuffix;
        // Ensure code is at most 15 characters
        if (rawCode.length() > 15) {
            // Try to keep suffix and as much of niveauCode as possible
            int suffixLen = Math.min(classeSuffix.length(), 5); // at least 5 chars for suffix if possible
            int niveauLen = 15 - 1 - suffixLen; // 1 for '_'
            if (niveauLen < 1) niveauLen = 1; // always keep at least 1 char from niveauCode
            String shortNiveau = niveauCode.substring(0, Math.min(niveauCode.length(), niveauLen));
            String shortSuffix = classeSuffix.substring(0, suffixLen);
            rawCode = shortNiveau + "_" + shortSuffix;
            // If still too long, truncate
            if (rawCode.length() > 15) {
                rawCode = rawCode.substring(0, 15);
            }
        }
        return rawCode.toUpperCase();
    }

    private String extractClasseSuffix(String nom) {
        if (nom == null) {
            return "DEFAULT";
        }

        // Extract class identifier from name (e.g., "A", "B" from "GL3-A")
        if (nom.contains("-")) {
            String[] parts = nom.split("-");
            if (parts.length > 1) {
                return parts[parts.length - 1].trim().toUpperCase();
            }
        }

        // If no dash, use the last character or the whole name if short
        if (nom.length() == 1) {
            return nom.toUpperCase();
        }

        // Extract alphanumeric characters only
        String cleaned = nom.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        return cleaned.isEmpty() ? "DEFAULT" : cleaned.substring(Math.max(0, cleaned.length() - 3));
    }

    public boolean isValidCode(String code, String entityType) {
        if (code == null || code.trim().isEmpty()) {
            return false;
        }

        return switch (entityType.toLowerCase()) {
            case "departement" -> code.length() <= 10 && code.matches(CODE_PATTERN);
            case "niveau" -> code.length() <= 10 && code.matches(CODE_PATTERN);
            case "classe" -> code.length() <= 15 && code.matches(CODE_PATTERN);
            default -> false;
        };
    }

    public String sanitizeCode(String code) {
        if (code == null) {
            return null;
        }
        return code.replaceAll("\\W", "").toUpperCase();
    }
}
