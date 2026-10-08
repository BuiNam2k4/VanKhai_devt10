package com.techbyte.ExamGuardBE.auth;

import com.techbyte.ExamGuardBE.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import static com.techbyte.ExamGuardBE.auth.AuthDtos.*;

@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final String issuer;
    private final Duration ttl;

    public TokenService(JwtEncoder encoder, @Value("${app.security.issuer}") String issuer,
                        @Value("${app.security.access-token-ttl}") Duration ttl) {
        if (ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException("Access token TTL must be positive and at most one day");
        }
        this.encoder = encoder;
        this.issuer = issuer;
        this.ttl = ttl;
    }

    public AuthResponse issue(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        UserResponse profile = UserResponse.from(user);
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(user.getId().toString())
                .issuedAt(now).expiresAt(expiresAt).id(UUID.randomUUID().toString())
                .claim("roles", profile.roles().stream().map(Enum::name).sorted().toList()).build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new AuthResponse(token, "Bearer", expiresAt, profile);
    }
}
