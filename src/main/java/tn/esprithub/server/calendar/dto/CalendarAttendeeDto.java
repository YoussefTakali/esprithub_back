package tn.esprithub.server.calendar.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CalendarAttendeeDto {
    private String userId;
    private String fullName;
    private String email;
    private String role;
}
