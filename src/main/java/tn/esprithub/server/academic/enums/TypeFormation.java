package tn.esprithub.server.academic.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TypeFormation {
    LICENCE("Licence"),
    MASTER("Master"),
    INGENIEUR("Ingénieur"),
    PREPA("Préparatoire"),
    CYCLE_PREP("Cycle Préparatoire");

    private final String displayName;

    TypeFormation(String displayName) {
        this.displayName = displayName;
    }

    @JsonValue
    public String getDisplayName() {
        return displayName;
    }

    @JsonCreator
    public static TypeFormation fromString(String text) {
        for (TypeFormation type : TypeFormation.values()) {
            if (type.name().equalsIgnoreCase(text) || 
                type.displayName.equalsIgnoreCase(text)) {
                return type;
            }
        }
        return LICENCE; // Default
    }
}
