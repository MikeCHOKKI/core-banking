package com.corebanking.api.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;

    public JwtTokenProvider(@Value("${JWT_SECRET:change-me-in-production-use-a-rsa-key}") String secret) {
        String paddedSecret = secret;
        while (paddedSecret.length() < 32) {
            paddedSecret += paddedSecret;
        }
        this.secretKey = Keys.hmacShaKeyFor(paddedSecret.substring(0, 32).getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String subject, List<String> roles) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + 86400000); // 24h

        return Jwts.builder()
                .subject(subject)
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiration)
                .issuer("core-banking")
                .signWith(secretKey)
                .compact();
    }
}
