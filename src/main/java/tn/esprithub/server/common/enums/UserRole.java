package tn.esprithub.server.common.enums;

public enum UserRole {
    STUDENT("Student"),
    TEACHER("Teacher"), 
    ADMIN("Administrator"),
    CHIEF("Department Chief");

    private final String displayName;

    UserRole(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
