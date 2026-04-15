package tn.esprithub.server.messenger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessengerParticipantDto {
    private UUID id;
    private String fullName;
    private String email;
    private String role;
}
