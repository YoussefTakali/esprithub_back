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
public class StudentDeadlineDto {
    private UUID id;
    private String title;
    private String type;
    private LocalDateTime deadline;
    private long daysLeft;
    private String priority;
    private String status;
}
