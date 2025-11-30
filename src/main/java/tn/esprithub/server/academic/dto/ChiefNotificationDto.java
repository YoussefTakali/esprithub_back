package tn.esprithub.server.academic.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ChiefNotificationDto {
    private String icon;
    private String text;
    private LocalDateTime date;
} 