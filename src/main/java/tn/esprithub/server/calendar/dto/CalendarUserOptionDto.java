package tn.esprithub.server.calendar.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CalendarUserOptionDto {
    private String id;
    private String fullName;
    private String email;
    private String role;
}
