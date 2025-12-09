package api.athena.fluxo.controller;

import api.athena.fluxo.security.SharedTokenValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
public class DebugController {

    private final SharedTokenValidator tokenValidator;

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("Pong - Fluxo Service is running");
    }

    @PostMapping("/validate-token")
    public ResponseEntity<?> validateToken(@RequestBody String token) {
        try {
            // Remove "Bearer " prefix if present
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            // Remove quotes if present (common when pasting from JSON)
            token = token.replace("\"", "");
            token = token.trim();

            boolean isValid = tokenValidator.validateToken(token);
            if (!isValid) {
                return ResponseEntity.badRequest().body(
                        "Token validation failed. Check server logs for 'Token inválido' or 'JWTVerificationException'.");
            }

            Map<String, Object> claims = tokenValidator.extractClaims(token);
            return ResponseEntity.ok(claims);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error processing token: " + e.getMessage());
        }
    }
}
