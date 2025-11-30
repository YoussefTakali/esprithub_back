package tn.esprithub.server.academic.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Specialites {
    GL("Génie Logiciel"),
    IA("Intelligence Artificielle"), 
    DS("Data Science"),
    IOT("Internet of Things"),
    TRANC_COMMUN("Tronc Commun"),
    CYBER_SECURITE("Cyber Sécurité"),
    RESEAUX_TELECOM("Réseaux et Télécommunications"),
    DEVELOPPEMENT_WEB("Développement Web"),
    MOBILE_DEV("Développement Mobile"),
    CLOUD_COMPUTING("Cloud Computing");

    private final String displayName;

    Specialites(String displayName) {
        this.displayName = displayName;
    }

    @JsonValue
    public String getDisplayName() {
        return displayName;
    }

    @JsonCreator
    public static Specialites fromString(String text) {
        if (text == null) {
            return TRANC_COMMUN;
        }
        
        // Handle specific legacy cases
        if ("TELECOMMUNICATIONS".equalsIgnoreCase(text)) {
            return RESEAUX_TELECOM;
        }
        
        for (Specialites specialite : Specialites.values()) {
            if (specialite.name().equalsIgnoreCase(text) || 
                specialite.displayName.equalsIgnoreCase(text)) {
                return specialite;
            }
        }
        return TRANC_COMMUN;
    }
}
