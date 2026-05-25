package com.microservices.auth.service;

import com.microservices.auth.dto.AuthResponse;
import com.microservices.auth.dto.LoginRequest;
import com.microservices.auth.dto.RegisterRequest;
import com.microservices.auth.enums.AccountStatus;
import com.microservices.auth.enums.AuthProviderType;
import com.microservices.auth.enums.Role;
import com.microservices.auth.factory.AuthProviderFactory;
import com.microservices.auth.interfaces.AuthProvider;
import com.microservices.auth.model.UserAccount;
import com.microservices.auth.model.UserAuthProvider;
import com.microservices.auth.repository.UserAccountRepository;
import com.microservices.auth.repository.UserAuthProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.microservices.auth.security.JwtService;
import com.microservices.auth.dto.TokenValidationResponse;
import com.microservices.auth.dto.RefreshTokenRequest;
import com.microservices.auth.dto.LogoutRequest;
import com.microservices.auth.model.RefreshToken;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final UserAuthProviderRepository userAuthProviderRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProviderFactory authProviderFactory;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthResponse register(RegisterRequest request) {
        if (userAccountRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        UserAccount userAccount = UserAccount.builder()
                .email(request.getEmail())
                .displayName(request.getDisplayName())
                .role(Role.USER)
                .status(AccountStatus.ACTIVE)
                .build();

        UserAccount savedUser = userAccountRepository.save(userAccount);

        UserAuthProvider authProvider = UserAuthProvider.builder()
                .userAccount(savedUser)
                .providerType(AuthProviderType.LOCAL)
                .providerUserId(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();

        userAuthProviderRepository.save(authProvider);

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getRole()
        );

        String accessToken = jwtService.generateToken(authenticatedUser);
        String refreshToken = refreshTokenService.createRefreshToken(savedUser);

        return new AuthResponse(accessToken, refreshToken, "Bearer");
    }

    public AuthResponse login(LoginRequest request) {
        AuthProvider provider = authProviderFactory.getProvider(request.getProviderType());

        AuthenticatedUser authenticatedUser = provider.authenticate(request);
        UserAccount userAccount = userAccountRepository.findById(authenticatedUser.getUserId())
            .orElseThrow(() -> new RuntimeException("User not found"));

        String accessToken = jwtService.generateToken(authenticatedUser);
        String refreshToken = refreshTokenService.createRefreshToken(userAccount);

        return new AuthResponse(accessToken, refreshToken, "Bearer");  
    }

    public TokenValidationResponse validateToken(String token) {
        boolean valid = jwtService.isTokenValid(token);

        return new TokenValidationResponse(
                valid,
                jwtService.extractUserId(token),
                jwtService.extractEmail(token),
                jwtService.extractRole(token)
        );
    }

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenService.validateRefreshToken(request.getRefreshToken());
        UserAccount userAccount = refreshToken.getUserAccount();

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                userAccount.getId(),
                userAccount.getEmail(),
                userAccount.getRole()
        );

        String newAccessToken = jwtService.generateToken(authenticatedUser);

        return new AuthResponse(newAccessToken, request.getRefreshToken(), "Bearer");
    }

    public void logout(LogoutRequest request) {
        refreshTokenService.revokeRefreshToken(request.getRefreshToken());
    }
}