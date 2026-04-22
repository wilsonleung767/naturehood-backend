package com.naturehood.naturehood_backend.config;

import com.naturehood.naturehood_backend.config.jwt.DualJwtDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security configuration for dual JWT support (HS256 and RS256/ES256).
 *
 * Uses DualJwtDecoder which supports:
 *  1. HMAC/HS256 tokens (mobile app with service role key)
 *  2. JWKS/RS256/ES256 tokens (web clients via Supabase Auth)
 *
 * The dual decoder tries HMAC first (optimized for mobile), then falls back to JWKS.
 * Strategy is configurable via jwt.decoder.strategy property.
 *
 * In controllers, userId = ((JwtAuthenticationToken) authentication).getToken().getSubject()
 * or simply authentication.getName()
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final DualJwtDecoder dualJwtDecoder;

    public SecurityConfig(DualJwtDecoder dualJwtDecoder) {
        this.dualJwtDecoder = dualJwtDecoder;
    }

    /**
     * Security filter chain:
     *  - Stateless sessions (no cookies, JWT only)
     *  - CSRF disabled (REST API consumed by mobile clients)
     *  - All /api/** endpoints require authentication
     *  - SSE endpoint also requires authentication (userId extracted from JWT)
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    // Actuator / health – open for load balancers
                    .requestMatchers("/actuator/health").permitAll()
                    // All other API endpoints require a valid JWT
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt
                            .decoder(dualJwtDecoder)
                            .jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );

        return http.build();
    }

    /**
     * Configures how JWT claims are mapped to Spring Security authorities.
     *
     * Supabase puts roles in the 'role' claim (not 'scope'), so we map that.
     * You can adjust the authority prefix and claim name as needed.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        // Supabase uses 'role' claim; adjust if your project uses different claims
        authoritiesConverter.setAuthoritiesClaimName("role");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        // principal name = JWT subject ('sub') = Supabase user UUID
        converter.setPrincipalClaimName("sub");
        return converter;
    }

    /**
     * CORS configuration permitting React Native / web clients.
     * Tighten origins in production.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
