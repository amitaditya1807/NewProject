package com.microservices.auth.repository;

import com.microservices.auth.enums.AuthProviderType;
import com.microservices.auth.model.UserAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAuthProviderRepository extends JpaRepository<UserAuthProvider, String> {

    Optional<UserAuthProvider> findByProviderTypeAndProviderUserId(
            AuthProviderType providerType,
            String providerUserId
    );
}