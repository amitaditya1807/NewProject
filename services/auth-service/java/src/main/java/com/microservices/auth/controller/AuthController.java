package com.microservices.auth.controller;

import com.microservices.auth.dto.AuthResponse;
import com.microservices.auth.dto.LoginRequest;
import com.microservices.auth.dto.RegisterRequest;
import com.microservices.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.microservices.auth.dto.TokenValidationResponse;
import com.microservices.auth.dto.CurrentUserResponse;
import com.microservices.auth.security.AuthenticatedUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.microservices.auth.dto.RefreshTokenRequest;
import com.microservices.auth.dto.LogoutRequest;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/validate")
    public TokenValidationResponse validateToken(
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        String token = authorizationHeader.replace("Bearer ", "");
        return authService.validateToken(token);
    }

    @GetMapping("/me")
    public CurrentUserResponse me(
            @AuthenticationPrincipal AuthenticatedUserPrincipal user
    ) {
        return new CurrentUserResponse(
                user.getUserId(),
                user.getEmail(),
                user.getRole()
        );
    }

    @PostMapping("/refresh")
    public AuthResponse refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refreshToken(request);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }
}