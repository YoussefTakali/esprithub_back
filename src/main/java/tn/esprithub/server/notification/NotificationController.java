package tn.esprithub.server.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import tn.esprithub.server.notification.dto.NotificationDto;
import tn.esprithub.server.notification.entity.Notification;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<NotificationDto>> getNotifications(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "50") int limit,
            Authentication authentication) {

        String email = getUserEmailFromAuthentication(authentication);
        List<NotificationDto> payload = notificationService.getNotificationsForUser(email, unreadOnly, limit)
                .stream()
                .map(this::toDto)
                .toList();

        return ResponseEntity.ok(payload);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(Authentication authentication) {
        String email = getUserEmailFromAuthentication(authentication);
        long unreadCount = notificationService.getUnreadCountForUser(email);
        return ResponseEntity.ok(Map.of("unreadCount", unreadCount));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<Map<String, String>> markAsRead(
            @PathVariable Long notificationId,
            Authentication authentication) {

        String email = getUserEmailFromAuthentication(authentication);
        notificationService.markNotificationAsRead(email, notificationId);
        return ResponseEntity.ok(Map.of("message", "Notification marked as read"));
    }

    @PostMapping("/{notificationId}/seen")
    public ResponseEntity<Map<String, String>> markAsSeen(
            @PathVariable Long notificationId,
            Authentication authentication) {

        String email = getUserEmailFromAuthentication(authentication);
        notificationService.markNotificationAsSeen(email, notificationId);
        return ResponseEntity.ok(Map.of("message", "Notification marked as seen"));
    }

    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Object>> markAllAsRead(Authentication authentication) {
        String email = getUserEmailFromAuthentication(authentication);
        int updated = notificationService.markAllNotificationsAsRead(email);
        return ResponseEntity.ok(Map.of("message", "Notifications marked as read", "updated", updated));
    }

    @PostMapping("/seen-all")
    public ResponseEntity<Map<String, Object>> markAllAsSeen(Authentication authentication) {
        String email = getUserEmailFromAuthentication(authentication);
        int updated = notificationService.markAllNotificationsAsSeen(email);
        return ResponseEntity.ok(Map.of("message", "Notifications marked as seen", "updated", updated));
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Map<String, String>> deleteNotification(
            @PathVariable Long notificationId,
            Authentication authentication) {

        String email = getUserEmailFromAuthentication(authentication);
        notificationService.deleteNotification(email, notificationId);
        return ResponseEntity.ok(Map.of("message", "Notification deleted"));
    }

    @DeleteMapping
    public ResponseEntity<Map<String, Object>> deleteAllNotifications(Authentication authentication) {
        String email = getUserEmailFromAuthentication(authentication);
        int deleted = notificationService.deleteAllNotifications(email);
        return ResponseEntity.ok(Map.of("message", "Notifications deleted", "deleted", deleted));
    }

    private NotificationDto toDto(Notification notification) {
        return NotificationDto.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .type(notification.getType())
            .targetUrl(notification.getTargetUrl())
                .timestamp(notification.getTimestamp())
                .read(notification.isRead())
                .build();
    }

    private String getUserEmailFromAuthentication(Authentication authentication) {
        if (authentication == null) {
            throw new RuntimeException("Authentication required");
        }

        if (authentication.getPrincipal() instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }

        return authentication.getName();
    }
}
