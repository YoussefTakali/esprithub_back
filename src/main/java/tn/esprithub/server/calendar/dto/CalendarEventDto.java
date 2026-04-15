package tn.esprithub.server.calendar.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CalendarEventDto {
    private String id;
    private String title;
    private String description;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Boolean allDay;
    private String location;
    private String eventType;
    private String source;
    private String creatorId;
    private String creatorName;
    private boolean editable;
    private String taskId;
    private List<CalendarAttendeeDto> attendees;
}
