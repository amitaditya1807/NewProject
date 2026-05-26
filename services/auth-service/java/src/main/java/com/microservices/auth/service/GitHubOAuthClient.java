package com.microservices.auth.service;

import com.microservices.auth.dto.GitHubUserInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class GitHubOAuthClient {

    @Value("${github.client-id}")
    private String githubClientId;

    @Value("${github.client-secret}")
    private String githubClientSecret;

    @Value("${github.redirect-uri}")
    private String githubRedirectUri;

    public GitHubUserInfo fetchUserInfo(String code) {
        if (githubClientId.isBlank() || githubClientSecret.isBlank()) {
            throw new RuntimeException("GitHub OAuth credentials are not configured");
        }

        String accessToken = exchangeCodeForAccessToken(code);

        Map<String, Object> user = fetchGitHubUser(accessToken);
        String email = (String) user.get("email");

        if (email == null || email.isBlank()) {
            email = fetchPrimaryEmail(accessToken);
        }

        return new GitHubUserInfo(
                String.valueOf(user.get("id")),
                email,
                (String) user.get("name"),
                (String) user.get("login")
        );
    }

    private String exchangeCodeForAccessToken(String code) {
        RestClient restClient = RestClient.create();

        Map<String, Object> response = restClient.post()
                .uri("https://github.com/login/oauth/access_token")
                .header("Accept", "application/json")
                .body(Map.of(
                        "client_id", githubClientId,
                        "client_secret", githubClientSecret,
                        "code", code,
                        "redirect_uri", githubRedirectUri
                ))
                .retrieve()
                .body(Map.class);

        if (response == null || response.get("access_token") == null) {
            throw new RuntimeException("Unable to get GitHub access token");
        }

        return (String) response.get("access_token");
    }

    private Map<String, Object> fetchGitHubUser(String accessToken) {
        RestClient restClient = RestClient.create();

        return restClient.get()
                .uri("https://api.github.com/user")
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .body(Map.class);
    }

    private String fetchPrimaryEmail(String accessToken) {
        RestClient restClient = RestClient.create();

        List<Map<String, Object>> emails = restClient.get()
                .uri("https://api.github.com/user/emails")
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .body(List.class);

        if (emails == null) {
            throw new RuntimeException("Unable to fetch GitHub email");
        }

        for (Map<String, Object> emailInfo : emails) {
            Boolean primary = (Boolean) emailInfo.get("primary");
            Boolean verified = (Boolean) emailInfo.get("verified");

            if (Boolean.TRUE.equals(primary) && Boolean.TRUE.equals(verified)) {
                return (String) emailInfo.get("email");
            }
        }

        throw new RuntimeException("No verified primary GitHub email found");
    }
}