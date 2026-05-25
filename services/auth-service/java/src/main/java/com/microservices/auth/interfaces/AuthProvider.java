package com.microservices.auth.interfaces;

import com.microservices.auth.dto.LoginRequest;
import com.microservices.auth.enums.AuthProviderType;
import com.microservices.auth.service.AuthenticatedUser;

public interface AuthProvider {

    AuthProviderType getProviderType();

    AuthenticatedUser authenticate(LoginRequest request);
}