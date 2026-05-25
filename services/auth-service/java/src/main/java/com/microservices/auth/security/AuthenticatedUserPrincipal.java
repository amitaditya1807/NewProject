package com.microservices.auth.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuthenticatedUserPrincipal {

    private String userId;

    private String email;

    private String role;
}