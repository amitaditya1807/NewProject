package com.microservices.auth.service;

import com.microservices.auth.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuthenticatedUser {

    private String userId;

    private String email;

    private Role role;
}