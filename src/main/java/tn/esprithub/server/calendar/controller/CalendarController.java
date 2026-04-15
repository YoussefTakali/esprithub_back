package tn.esprithub.server.calendar.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.esprithub.server.calendar.dto.CalendarAvailabilityDto;
import tn.esprithub.server.calendar.dto.CalendarCreateEventRequest;
import tn.esprithub.server.calendar.dto.CalendarEventDto;
import tn.esprithub.server.calendar.dto.CalendarUserOptionDto;
import tn.esprithub.server.calendar.service.CalendarService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/calendar")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"${app.cors.allowed-origins}"})
@PreAuthorize("hasAnyRole('ADMIN','CHIEF','TEACHER','STUDENT')")
public class CalendarController {

    private final CalendarService calendarService;

    @GetMapping("/events")
    public ResponseEntity<List<CalendarEventDto>> getEvents(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            Authentication authentication) {
        return ResponseEntity.ok(calendarService.getEvents(authentication.getName(), start, end));
    }

    @PostMapping("/events")
    public ResponseEntity<CalendarEventDto> createEvent(
            @Valid @RequestBody CalendarCreateEventRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(calendarService.createEvent(authentication.getName(), request));
    }

    @DeleteMapping("/events/{eventId}")
    public ResponseEntity<Void> deleteEvent(
            @PathVariable UUID eventId,
            Authentication authentication) {
        calendarService.deleteEvent(authentication.getName(), eventId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/availability")
    public ResponseEntity<List<CalendarAvailabilityDto>> getAvailability(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(required = false) List<UUID> attendeeIds) {
        return ResponseEntity.ok(calendarService.getAvailability(start, end, attendeeIds));
    }

    @GetMapping("/users/search")
    public ResponseEntity<List<CalendarUserOptionDto>> searchUsers(
            @RequestParam String q,
            Authentication authentication) {
        return ResponseEntity.ok(calendarService.searchUsers(authentication.getName(), q));
    }
}
