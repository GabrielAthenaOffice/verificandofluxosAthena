package api.athena.fluxo.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final SharedTokenValidator tokenValidator;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        try {
            // Primeiro tenta extrair do header Authorization (para requisições
            // cross-domain)
            String jwt = extractJwtFromHeader(request);

            // Se não encontrou no header, tenta do cookie
            if (jwt == null) {
                jwt = extractJwtFromCookie(request);
            }

            // Se ainda não encontrou, tenta do parâmetro da URL (útil para iframes)
            if (jwt == null) {
                jwt = request.getParameter("token");
                if (jwt != null) {
                    log.debug("Token JWT encontrado no parâmetro 'token'");
                }
            }

            if (jwt != null && tokenValidator.validateToken(jwt)) {
                Map<String, Object> claims = tokenValidator.extractClaims(jwt);

                String email = (String) claims.get("sub");
                String role = (String) claims.get("role");
                Object userIdObj = claims.get("userId");
                if (userIdObj == null) {
                    log.error("Token JWT inválido: userId não encontrado nos claims");
                    return;
                }
                Long userId = ((Number) userIdObj).longValue();

                // Criar authorities baseado na role
                List<SimpleGrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_" + role));

                // Criar objeto de autenticação personalizado
                UserPrincipal principal = new UserPrincipal(userId, email, role);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(principal,
                        null, authorities);

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("Usuário autenticado: {} ({})", email, role);
            }
        } catch (Exception e) {
            log.error("Erro na autenticação: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String extractJwtFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            log.debug("Nenhum cookie presente na requisição para: {}", request.getRequestURI());
            return null;
        }

        log.debug("Cookies presentes: {}",
                Arrays.stream(request.getCookies())
                        .map(Cookie::getName)
                        .collect(Collectors.joining(", ")));

        String jwt = Arrays.stream(request.getCookies())
                .filter(cookie -> "athenaoffice".equals(cookie.getName()))
                .findFirst()
                .map(Cookie::getValue)
                .orElse(null);

        if (jwt == null) {
            log.debug("Cookie 'athenaoffice' não encontrado");
        }

        return jwt;
    }

    private String extractJwtFromHeader(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            log.debug("Token JWT encontrado no header Authorization");
            return token;
        }

        return null;
    }
}
