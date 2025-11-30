package tn.esprithub.server.project.portal.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentNotificationDto {
    private UUID id;
    private String title;
    private String message;
    private String type;
    private LocalDateTime timestamp;
    private boolean read;
    private String actionUrl;
}
