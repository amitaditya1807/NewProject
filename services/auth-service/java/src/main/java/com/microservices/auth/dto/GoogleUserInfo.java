package com.microservices.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GoogleUserInfo {

    private String googleUserId;

    private String email;

    private String name;

    private boolean emailVerified;
}