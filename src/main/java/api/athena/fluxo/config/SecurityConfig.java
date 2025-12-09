package api.athena.fluxo.config;

import api.athena.fluxo.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Endpoints públicos
                        .requestMatchers("/health", "/actuator/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                        .requestMatchers("/api/debug/**").permitAll()

                        // Visualização de fluxos (autenticado)
                        .requestMatchers(HttpMethod.GET, "/api/fluxos/**").authenticated()

                        // Publicação (ADMIN, LIDER_DE_SETOR, FUNCIONARIO)
                        .requestMatchers(HttpMethod.POST, "/api/fluxos/**")
                        .hasAnyRole("ADMIN", "LIDER_DE_SETOR", "FUNCIONARIO")

                        // Atualização e deleção (ADMIN, LIDER_DE_SETOR)
                        .requestMatchers(HttpMethod.PUT, "/api/fluxos/**")
                        .hasAnyRole("ADMIN", "LIDER_DE_SETOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/fluxos/**")
                        .hasAnyRole("ADMIN", "LIDER_DE_SETOR")

                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // IMPORTANTE: URLs de origem NÃO devem ter barra final
        configuration.setAllowedOriginPatterns(List.of(
                "https://frontzapzapinterno.vercel.app",
                "http://localhost:*",
                "http://127.0.0.1:*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
