package com.microservices.auth.dto;

import com.microservices.auth.enums.AuthProviderType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    @NotNull
    private AuthProviderType providerType;

    private String email;

    private String password;

    private String providerToken;
}