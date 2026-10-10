package com.techbyte.ExamGuardBE.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.techbyte.ExamGuardBE.common.ApiResponse;
import com.techbyte.ExamGuardBE.controller.response.UserResponse;
import com.techbyte.ExamGuardBE.exception.ErrorCode;
import com.techbyte.ExamGuardBE.mapper.ManagementMapper;
import com.techbyte.ExamGuardBE.repository.UserRepository;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/auth/register", "/api/auth/token", "/api/auth/login", "/api/auth/introspect"
    };

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    SecretKey jwtSecretKey(@Value("${jwt.signer-key}") String signerKey) {
        byte[] bytes = signerKey.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 64) throw new IllegalArgumentException("JWT_SIGNER_KEY must contain at least 64 UTF-8 bytes");
        return new SecretKeySpec(bytes, "HmacSHA512");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key, @Value("${jwt.issuer}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS512).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ZERO), new JwtIssuerValidator(issuer)));
        return decoder;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, UserRepository userRepository,
                                    ObjectMapper objectMapper) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(request -> request
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/teacher/**").hasAnyRole("ADMIN", "TEACHER")
                        .requestMatchers("/api/student/**").hasRole("STUDENT")
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((req, res, ex) -> writeError(res, objectMapper, ErrorCode.UNAUTHENTICATED))
                        .accessDeniedHandler((req, res, ex) -> writeError(res, objectMapper, ErrorCode.FORBIDDEN)))
                .oauth2ResourceServer(oauth -> oauth
                        .authenticationEntryPoint((req, res, ex) -> writeError(res, objectMapper, ErrorCode.UNAUTHENTICATED))
                        .accessDeniedHandler((req, res, ex) -> writeError(res, objectMapper, ErrorCode.FORBIDDEN))
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(token -> {
                            long id;
                            try {
                                id = Long.parseLong(token.getSubject());
                            } catch (NumberFormatException exception) {
                                throw new InvalidBearerTokenException("Invalid subject");
                            }
                            var user = userRepository.findWithRolesById(id).filter(item -> item.isEnabled())
                                    .orElseThrow(() -> new InvalidBearerTokenException("Account unavailable"));
                            UserResponse principal = ManagementMapper.toUserResponse(user);
                            var authorities = principal.getRoles().stream()
                                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList();
                            return new UsernamePasswordAuthenticationToken(principal, token, authorities);
                        })));
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") String origins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private static void writeError(HttpServletResponse response, ObjectMapper mapper, ErrorCode code) throws IOException {
        response.setStatus(code.getHttpStatus().value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(mapper.writeValueAsString(ApiResponse.<Void>builder()
                .status(code.getHttpStatus().value()).message(code.getMessage()).build()));
    }
}
