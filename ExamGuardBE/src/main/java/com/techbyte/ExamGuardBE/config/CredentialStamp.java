package com.techbyte.ExamGuardBE.config;

import com.techbyte.ExamGuardBE.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class CredentialStamp {
    private final SecretKey jwtSecretKey;

    public String forUser(User user) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(jwtSecretKey.getEncoded(), "HmacSHA256"));
            var value = "examguard.credentials.v1:" + user.getId() + ":" + user.getPasswordHash();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException failure) { throw new IllegalStateException("Cannot compute credential stamp", failure); }
    }

    public boolean matches(Jwt token, User user) {
        Object stamp = token.getClaim("credentialStamp");
        return stamp instanceof String value && MessageDigest.isEqual(value.getBytes(StandardCharsets.UTF_8), forUser(user).getBytes(StandardCharsets.UTF_8));
    }
}
