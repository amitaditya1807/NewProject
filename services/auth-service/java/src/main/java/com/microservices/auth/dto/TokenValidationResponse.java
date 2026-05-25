package com.microservices.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TokenValidationResponse {

    private boolean valid;

    private String userId;

    private String email;

    private String role;
}