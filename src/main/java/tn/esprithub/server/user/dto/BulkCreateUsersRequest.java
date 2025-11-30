package tn.esprithub.server.user.dto;

import lombok.Data;
import java.util.List;

@Data
public class BulkCreateUsersRequest {
    private List<CreateUserDto> users;
}
