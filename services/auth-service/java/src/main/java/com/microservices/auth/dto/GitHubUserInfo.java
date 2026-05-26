package com.microservices.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GitHubUserInfo {

    private String githubUserId;

    private String email;

    private String name;

    private String login;
}