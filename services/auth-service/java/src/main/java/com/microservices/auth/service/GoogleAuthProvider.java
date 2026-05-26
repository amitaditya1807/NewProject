package com.microservices.auth.service;

import com.microservices.auth.dto.GoogleUserInfo;
import com.microservices.auth.dto.LoginRequest;
import com.microservices.auth.enums.AccountStatus;
import com.microservices.auth.enums.AuthProviderType;
import com.microservices.auth.enums.Role;
import com.microservices.auth.interfaces.AuthProvider;
import com.microservices.auth.model.UserAccount;
import com.microservices.auth.model.UserAuthProvider;
import com.microservices.auth.repository.UserAccountRepository;
import com.microservices.auth.repository.UserAuthProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GoogleAuthProvider implements AuthProvider {

    private final GoogleTokenVerifier googleTokenVerifier;
    private final UserAccountRepository userAccountRepository;
    private final UserAuthProviderRepository userAuthProviderRepository;

    @Override
    public AuthProviderType getProviderType() {
        return AuthProviderType.GOOGLE;
    }

    @Override
    public AuthenticatedUser authenticate(LoginRequest request) {
        GoogleUserInfo googleUserInfo = googleTokenVerifier.verify(request.getProviderToken());

        if (!googleUserInfo.isEmailVerified()) {
            throw new RuntimeException("Google email is not verified");
        }

        UserAuthProvider authProvider = userAuthProviderRepository
                .findByProviderTypeAndProviderUserId(
                        AuthProviderType.GOOGLE,
                        googleUserInfo.getGoogleUserId()
                )
                .orElseGet(() -> createGoogleUser(googleUserInfo));

        UserAccount userAccount = authProvider.getUserAccount();

        if (userAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new RuntimeException("Account is not active");
        }

        return new AuthenticatedUser(
                userAccount.getId(),
                userAccount.getEmail(),
                userAccount.getRole()
        );
    }

    private UserAuthProvider createGoogleUser(GoogleUserInfo googleUserInfo) {
        UserAccount userAccount = userAccountRepository.findByEmail(googleUserInfo.getEmail())
                .orElseGet(() -> createUserAccountFromGoogle(googleUserInfo));

        UserAuthProvider authProvider = UserAuthProvider.builder()
                .userAccount(userAccount)
                .providerType(AuthProviderType.GOOGLE)
                .providerUserId(googleUserInfo.getGoogleUserId())
                .build();

        return userAuthProviderRepository.save(authProvider);
    }

    private UserAccount createUserAccountFromGoogle(GoogleUserInfo googleUserInfo) {
        UserAccount userAccount = UserAccount.builder()
                .email(googleUserInfo.getEmail())
                .displayName(googleUserInfo.getName())
                .role(Role.USER)
                .status(AccountStatus.ACTIVE)
                .build();

        return userAccountRepository.save(userAccount);
    }
}