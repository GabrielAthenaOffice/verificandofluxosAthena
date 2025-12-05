package api.athena.fluxo.security;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserPrincipal {
    private Long id;
    private String email;
    private String role;
}
