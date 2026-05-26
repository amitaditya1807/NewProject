package com.microservices.auth.service;

import com.microservices.auth.dto.GitHubUserInfo;
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
public class GitHubAuthProvider implements AuthProvider {

    private final GitHubOAuthClient gitHubOAuthClient;
    private final UserAccountRepository userAccountRepository;
    private final UserAuthProviderRepository userAuthProviderRepository;

    @Override
    public AuthProviderType getProviderType() {
        return AuthProviderType.GITHUB;
    }

    @Override
    public AuthenticatedUser authenticate(LoginRequest request) {
        GitHubUserInfo gitHubUserInfo = gitHubOAuthClient.fetchUserInfo(request.getProviderToken());

        UserAuthProvider authProvider = userAuthProviderRepository
                .findByProviderTypeAndProviderUserId(
                        AuthProviderType.GITHUB,
                        gitHubUserInfo.getGithubUserId()
                )
                .orElseGet(() -> createGitHubUser(gitHubUserInfo));

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

    private UserAuthProvider createGitHubUser(GitHubUserInfo gitHubUserInfo) {
        UserAccount userAccount = userAccountRepository.findByEmail(gitHubUserInfo.getEmail())
                .orElseGet(() -> createUserAccountFromGitHub(gitHubUserInfo));

        UserAuthProvider authProvider = UserAuthProvider.builder()
                .userAccount(userAccount)
                .providerType(AuthProviderType.GITHUB)
                .providerUserId(gitHubUserInfo.getGithubUserId())
                .build();

        return userAuthProviderRepository.save(authProvider);
    }

    private UserAccount createUserAccountFromGitHub(GitHubUserInfo gitHubUserInfo) {
        String displayName = gitHubUserInfo.getName();

        if (displayName == null || displayName.isBlank()) {
            displayName = gitHubUserInfo.getLogin();
        }

        UserAccount userAccount = UserAccount.builder()
                .email(gitHubUserInfo.getEmail())
                .displayName(displayName)
                .role(Role.USER)
                .status(AccountStatus.ACTIVE)
                .build();

        return userAccountRepository.save(userAccount);
    }
}