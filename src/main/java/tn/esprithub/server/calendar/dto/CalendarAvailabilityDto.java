package tn.esprithub.server.calendar.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CalendarAvailabilityDto {
    private String userId;
    private String fullName;
    private String email;
    private String role;
    private String availabilityStatus;
    private int conflictCount;
    private List<String> conflicts;
}
