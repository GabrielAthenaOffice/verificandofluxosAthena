package api.athena.fluxo.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Valida tokens JWT gerados pela API principal (ZapZap)
 * Usa o MESMO SECRET para validação
 */
@Service
@Slf4j
public class SharedTokenValidator {

    @Value("${api.security.token.secret}")
    private String secret;

    public boolean validateToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            JWT.require(algorithm)
                    .withIssuer("auth-api")
                    .build()
                    .verify(token);
            return true;
        } catch (JWTVerificationException e) {
            log.error("Token inválido: {}", e.getMessage());
            return false;
        }
    }

    public Map<String, Object> extractClaims(String token) {
        try {
            DecodedJWT jwt = JWT.decode(token);
            Map<String, Object> claims = new HashMap<>();

            claims.put("sub", jwt.getSubject());
            claims.put("role", jwt.getClaim("role").asString());
            claims.put("userId", jwt.getClaim("userId").asLong());
            claims.put("name", jwt.getClaim("name").asString());

            return claims;
        } catch (Exception e) {
            log.error("Erro ao extrair claims: {}", e.getMessage());
            return new HashMap<>();
        }
    }
}
