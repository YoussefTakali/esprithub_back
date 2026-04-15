package tn.esprithub.server.calendar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import tn.esprithub.server.calendar.entity.CalendarEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class CalendarCreateEventRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotNull(message = "Start time is required")
    private LocalDateTime startAt;

    @NotNull(message = "End time is required")
    private LocalDateTime endAt;

    private Boolean allDay;

    private String location;

    private CalendarEvent.EventType eventType;

    private List<UUID> attendeeIds;
}
