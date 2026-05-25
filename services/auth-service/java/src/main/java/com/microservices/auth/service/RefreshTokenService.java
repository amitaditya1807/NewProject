package com.microservices.auth.service;

import com.microservices.auth.model.RefreshToken;
import com.microservices.auth.model.UserAccount;
import com.microservices.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-expiration-days}")
    private long refreshExpirationDays;

    public String createRefreshToken(UserAccount userAccount) {
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = hashToken(rawToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .userAccount(userAccount)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusSeconds(refreshExpirationDays * 24 * 60 * 60))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);

        return rawToken;
    }

    public RefreshToken validateRefreshToken(String rawToken) {
        String tokenHash = hashToken(rawToken);

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        if (refreshToken.isRevoked()) {
            throw new RuntimeException("Refresh token has been revoked");
        }

        if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new RuntimeException("Refresh token has expired");
        }

        return refreshToken;
    }

    public void revokeRefreshToken(String rawToken) {
        RefreshToken refreshToken = validateRefreshToken(rawToken);

        refreshToken.setRevoked(true);
        refreshToken.setRevokedAt(Instant.now());

        refreshTokenRepository.save(refreshToken);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception exception) {
            throw new RuntimeException("Unable to hash refresh token");
        }
    }
}