package com.microservices.auth.service;

import org.springframework.stereotype.Component;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.microservices.auth.dto.LoginRequest;
import com.microservices.auth.enums.AccountStatus;
import com.microservices.auth.enums.AuthProviderType;
import com.microservices.auth.interfaces.AuthProvider;
import com.microservices.auth.model.UserAuthProvider;
import com.microservices.auth.repository.UserAuthProviderRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LocalAuthProvider implements AuthProvider {

    private final UserAuthProviderRepository userAuthProviderRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public AuthProviderType getProviderType() {
        return AuthProviderType.LOCAL;
    }

    @Override
    public AuthenticatedUser authenticate(LoginRequest request) {
        UserAuthProvider authProvider = userAuthProviderRepository
                .findByProviderTypeAndProviderUserId(AuthProviderType.LOCAL, request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));
        
        if (!passwordEncoder.matches(request.getPassword(), authProvider.getPasswordHash())) {
            throw new RuntimeException("Invalid email or password");
        }
        if (authProvider.getUserAccount().getStatus() != AccountStatus.ACTIVE) {
            throw new RuntimeException("Account is not active");
        }

        return new AuthenticatedUser(
                authProvider.getUserAccount().getId(),
                authProvider.getUserAccount().getEmail(),
                authProvider.getUserAccount().getRole()
        );
    }
}